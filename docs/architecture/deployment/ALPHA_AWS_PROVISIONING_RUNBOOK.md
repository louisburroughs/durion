# Alpha AWS Provisioning Runbook

Status: Authoritative operator and agent guide
Environment: `alpha` — single-tenant prototype cell
Last updated: 2026-04-02
Depends on:

- [Foundation-First Tenant Cell Deployment Architecture](./FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md)
- [Phased CI/CD and Runtime Plan](./PHASED_CICD_AND_RUNTIME_PLAN.md)

---

## Overview

This runbook provisions a single Durion alpha tenant cell on AWS. It is intended to be executed once
by a human operator or an authorized AWS agent. Every step references a concrete AWS CLI command or
SSH command. All decisions recorded in the architecture document are reflected here.

The cell runs 22 Docker containers on a single `t3.2xlarge` EC2 host in `us-east-1`:

| Category | Services |
|---|---|
| Frontend | `pos-frontend` (Node 22 / Angular SSR) |
| API Gateway | `pos-api-gateway` (Spring Cloud Gateway) |
| Service Discovery | `eureka-server` (Spring Cloud Eureka) |
| Backend services (14) | `pos-accounting`, `pos-catalog`, `pos-customer`, `pos-event-receiver`, `pos-image`, `pos-inventory`, `pos-invoice`, `pos-location`, `pos-people`, `pos-price`, `pos-security-service`, `pos-shop-manager`, `pos-vehicle-inventory`, `pos-workorder` |
| Infrastructure | `postgres`, `otel-collector`, `jaeger`, `prometheus`, `grafana` |

Images for all custom services are pulled from AWS ECR. Public images (`postgres`, `jaeger`,
`prometheus`, `grafana`, `otel-collector`) are pulled from Docker Hub / official registries.

**CI/CD prerequisite:** Before executing Step 11 (Deploy), all custom service images must have been
built and pushed to ECR by the GitHub Actions `build-push-ecr.yml` workflows. See the
[CI/CD Workflow section](#github-actions-cicd-workflows-reference) at the end of this document.

---

## Prerequisites

The following must be satisfied before starting:

- AWS CLI v2 configured with credentials that have the following IAM permissions:
  - `ecr:CreateRepository`, `ecr:PutLifecyclePolicy`
  - `iam:CreateRole`, `iam:AttachRolePolicy`, `iam:CreateInstanceProfile`, `iam:AddRoleToInstanceProfile`
  - `ec2:RunInstances`, `ec2:AllocateAddress`, `ec2:AssociateAddress`, `ec2:CreateSecurityGroup`, `ec2:AuthorizeSecurityGroupIngress`
  - `s3:CreateBucket`, `s3:PutBucketLifecycleConfiguration`, `s3:PutBucketPolicy`
  - `route53:ChangeResourceRecordSets`, `route53:ListHostedZones`
- A registered domain hosted in Route 53. This runbook uses `positivity.durion.com`.
- SSH key pair already created in AWS in `us-east-1`. Replace `durion-alpha-key` with your key pair name throughout.
- Docker Engine and Git installed on the local operator machine (for manual image builds if CI has not run yet).
- GitHub repository secrets configured (see [Secrets Configuration](#secrets-configuration)).

---

## Section 1 — Bootstrap: Shell Variables

Run these in your AWS CLI shell session. All later commands reference these variables.

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity \
  --query Account --output text --region "$AWS_REGION")
export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
export CELL_NAME=alpha
export DOMAIN=positivity.durion.com
export KEY_PAIR_NAME=durion-alpha-key
export S3_BACKUP_BUCKET=durion-positivity-backups

echo "Account:  $AWS_ACCOUNT_ID"
echo "Registry: $ECR_REGISTRY"
```

---

## Section 2 — ECR Repositories

Create one repository per custom service image. Public infrastructure images do not need ECR repos.

```bash
ECR_SERVICES=(
  durion/pos-frontend
  durion/pos-api-gateway
  durion/eureka-server
  durion/pos-accounting
  durion/pos-catalog
  durion/pos-customer
  durion/pos-event-receiver
  durion/pos-image
  durion/pos-inventory
  durion/pos-invoice
  durion/pos-location
  durion/pos-people
  durion/pos-price
  durion/pos-security-service
  durion/pos-shop-manager
  durion/pos-vehicle-inventory
  durion/pos-workorder
)

for REPO in "${ECR_SERVICES[@]}"; do
  echo "Creating ECR repo: $REPO"
  aws ecr create-repository \
    --repository-name "$REPO" \
    --region "$AWS_REGION" \
    --image-scanning-configuration scanOnPush=true \
    --encryption-configuration encryptionType=AES256 \
    --query 'repository.repositoryUri' \
    --output text 2>/dev/null \
    || echo "  Already exists, skipping."
done
```

Apply a lifecycle policy to each repo to keep only the 20 most recent images and remove untagged
images after 7 days:

```bash
LIFECYCLE_POLICY='{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Remove untagged images after 7 days",
      "selection": {"tagStatus": "untagged", "countType": "sinceImagePushed", "countUnit": "days", "countNumber": 7},
      "action": {"type": "expire"}
    },
    {
      "rulePriority": 2,
      "description": "Keep only 20 most recent tagged images",
      "selection": {"tagStatus": "tagged", "tagPrefixList": ["sha-"], "countType": "imageCountMoreThan", "countNumber": 20},
      "action": {"type": "expire"}
    }
  ]
}'

for REPO in "${ECR_SERVICES[@]}"; do
  aws ecr put-lifecycle-policy \
    --repository-name "$REPO" \
    --lifecycle-policy-text "$LIFECYCLE_POLICY" \
    --region "$AWS_REGION" > /dev/null
done
```

---

## Section 3 — IAM Configuration

### 3.1 EC2 Instance Role (pull ECR, write S3 backups)

```bash
# Trust policy — allows EC2 to assume this role
cat > /tmp/ec2-trust.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "ec2.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role \
  --role-name durion-alpha-ec2-role \
  --assume-role-policy-document file:///tmp/ec2-trust.json \
  --description "Durion alpha EC2 host: ECR pull + S3 backup write"

# ECR read access (pull images)
aws iam attach-role-policy \
  --role-name durion-alpha-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

# Scoped S3 backup write policy
cat > /tmp/s3-backup-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:ListBucket"],
    "Resource": [
      "arn:aws:s3:::${S3_BACKUP_BUCKET}",
      "arn:aws:s3:::${S3_BACKUP_BUCKET}/alpha/*"
    ]
  }]
}
EOF

aws iam put-role-policy \
  --role-name durion-alpha-ec2-role \
  --policy-name durion-alpha-s3-backup \
  --policy-document file:///tmp/s3-backup-policy.json

# Create instance profile and attach role
aws iam create-instance-profile \
  --instance-profile-name durion-alpha-instance-profile

aws iam add-role-to-instance-profile \
  --instance-profile-name durion-alpha-instance-profile \
  --role-name durion-alpha-ec2-role

echo "Waiting for instance profile propagation..."
sleep 15
```

### 3.2 GitHub Actions OIDC Role (push ECR images)

This allows GitHub Actions to push images without storing AWS access keys in GitHub secrets.

```bash
# Create OIDC provider for GitHub Actions (run once per AWS account)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
  2>/dev/null || echo "OIDC provider already exists."

GITHUB_ORG=louisburroughs   # Replace with your GitHub org/username

# Trust policy — scoped to your GitHub org's repos
cat > /tmp/gha-trust.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringLike": {
        "token.actions.githubusercontent.com:sub": "repo:${GITHUB_ORG}/*:*"
      },
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
      }
    }
  }]
}
EOF

aws iam create-role \
  --role-name durion-gha-ecr-push \
  --assume-role-policy-document file:///tmp/gha-trust.json \
  --description "GitHub Actions: push images to Durion ECR repos"

# ECR push permissions
cat > /tmp/ecr-push-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "ecr:GetAuthorizationToken",
    "Resource": "*"
  }, {
    "Effect": "Allow",
    "Action": [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage"
    ],
    "Resource": "arn:aws:iam::*:root"
  }]
}
EOF

# Attach the AWS managed policy for ECR power user (easier than inline for push)
aws iam attach-role-policy \
  --role-name durion-gha-ecr-push \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

GHA_ROLE_ARN=$(aws iam get-role \
  --role-name durion-gha-ecr-push \
  --query 'Role.Arn' --output text)

echo "GitHub Actions role ARN: $GHA_ROLE_ARN"
echo ""
echo "ACTION REQUIRED: Add this as a GitHub Actions secret named AWS_ROLE_ARN"
echo "  in both durion-positivity-frontend and durion-positivity-backend repositories."
```

---

## Section 4 — S3 Backup Bucket

```bash
# Create the bucket (us-east-1 does not use LocationConstraint)
aws s3api create-bucket \
  --bucket "$S3_BACKUP_BUCKET" \
  --region "$AWS_REGION"

# Block public access
aws s3api put-public-access-block \
  --bucket "$S3_BACKUP_BUCKET" \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket "$S3_BACKUP_BUCKET" \
  --versioning-configuration Status=Enabled

# Lifecycle rule: expire alpha backups after 14 days
cat > /tmp/s3-lifecycle.json << 'EOF'
{
  "Rules": [{
    "ID": "alpha-backup-expiry",
    "Status": "Enabled",
    "Filter": {"Prefix": "alpha/"},
    "Expiration": {"Days": 14},
    "NoncurrentVersionExpiration": {"NoncurrentDays": 7}
  }]
}
EOF

aws s3api put-bucket-lifecycle-configuration \
  --bucket "$S3_BACKUP_BUCKET" \
  --lifecycle-configuration file:///tmp/s3-lifecycle.json

echo "S3 backup bucket: s3://${S3_BACKUP_BUCKET}"
```

---

## Section 5 — Security Group

```bash
# Create the security group
SG_ID=$(aws ec2 create-security-group \
  --group-name durion-alpha-sg \
  --description "Durion alpha cell: HTTPS + HTTP + SSH" \
  --region "$AWS_REGION" \
  --query 'GroupId' --output text)

echo "Security Group ID: $SG_ID"

# HTTPS — public
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --region "$AWS_REGION" \
  --protocol tcp --port 443 --cidr 0.0.0.0/0

# HTTP — public (Let's Encrypt ACME challenge redirect)
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --region "$AWS_REGION" \
  --protocol tcp --port 80 --cidr 0.0.0.0/0

# SSH — OPERATOR IP ONLY. Replace with your actual IP.
OPERATOR_IP=$(curl -s https://checkip.amazonaws.com)/32
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --region "$AWS_REGION" \
  --protocol tcp --port 22 --cidr "$OPERATOR_IP"

echo "SSH access restricted to: $OPERATOR_IP"
```

---

## Section 6 — EC2 Instance

### 6.1 Resolve the Latest Amazon Linux 2023 AMI

```bash
AMI_ID=$(aws ssm get-parameter \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --region "$AWS_REGION" \
  --query 'Parameter.Value' --output text)

echo "AMI: $AMI_ID"
```

### 6.2 Launch the Instance

```bash
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t3.2xlarge \
  --key-name "$KEY_PAIR_NAME" \
  --security-group-ids "$SG_ID" \
  --iam-instance-profile Name=durion-alpha-instance-profile \
  --block-device-mappings '[{
    "DeviceName": "/dev/xvda",
    "Ebs": {
      "VolumeSize": 80,
      "VolumeType": "gp3",
      "Iops": 3000,
      "Throughput": 125,
      "DeleteOnTermination": true
    }
  }]' \
  --tag-specifications 'ResourceType=instance,Tags=[
    {Key=Name,Value=durion-alpha},
    {Key=Environment,Value=alpha},
    {Key=Project,Value=durion}
  ]' \
  --region "$AWS_REGION" \
  --query 'Instances[0].InstanceId' --output text)

echo "Instance ID: $INSTANCE_ID"

# Wait for the instance to reach running state
aws ec2 wait instance-running \
  --instance-ids "$INSTANCE_ID" \
  --region "$AWS_REGION"

echo "Instance is running."
```

### 6.3 Allocate and Associate an Elastic IP

```bash
ALLOC_ID=$(aws ec2 allocate-address \
  --domain vpc \
  --region "$AWS_REGION" \
  --query 'AllocationId' --output text)

ELASTIC_IP=$(aws ec2 describe-addresses \
  --allocation-ids "$ALLOC_ID" \
  --region "$AWS_REGION" \
  --query 'Addresses[0].PublicIp' --output text)

aws ec2 associate-address \
  --instance-id "$INSTANCE_ID" \
  --allocation-id "$ALLOC_ID" \
  --region "$AWS_REGION" > /dev/null

echo "Elastic IP: $ELASTIC_IP"
echo "Instance public IP: $ELASTIC_IP"
```

---

## Section 7 — Route 53 DNS

Retrieve the hosted zone ID for your domain and create an A record.

```bash
HOSTED_ZONE_ID=$(aws route53 list-hosted-zones \
  --query "HostedZones[?Name=='durion.com.'].Id" \
  --output text | cut -d/ -f3)

echo "Hosted Zone ID: $HOSTED_ZONE_ID"

cat > /tmp/route53-change.json << EOF
{
  "Comment": "Durion alpha cell",
  "Changes": [{
    "Action": "UPSERT",
    "ResourceRecordSet": {
      "Name": "${DOMAIN}",
      "Type": "A",
      "TTL": 300,
      "ResourceRecords": [{"Value": "${ELASTIC_IP}"}]
    }
  }]
}
EOF

aws route53 change-resource-record-sets \
  --hosted-zone-id "$HOSTED_ZONE_ID" \
  --change-batch file:///tmp/route53-change.json \
  --query 'ChangeInfo.Status' --output text

echo "DNS record created: ${DOMAIN} -> ${ELASTIC_IP}"
echo "Allow up to 5 minutes for DNS propagation."
```

---

## Section 8 — Host Configuration (SSH in)

SSH into the instance and run all commands in this section on the host.

```bash
ssh -i ~/.ssh/${KEY_PAIR_NAME}.pem ec2-user@${ELASTIC_IP}
```

All commands below are run **on the EC2 host** unless otherwise noted.

### 8.1 Install Docker Engine

```bash
sudo dnf update -y
sudo dnf install -y docker git

# Start and enable Docker
sudo systemctl enable --now docker

# Allow ec2-user to run docker without sudo
sudo usermod -aG docker ec2-user
newgrp docker

# Install Docker Compose plugin
COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest \
  | grep '"tag_name"' | cut -d'"' -f4)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL \
  "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

docker compose version
```

### 8.2 Install nginx and certbot

```bash
sudo dnf install -y nginx python3-certbot-nginx

sudo systemctl enable nginx
```

---

## Section 9 — nginx Configuration and TLS

### 9.1 Initial HTTP Configuration

Create the nginx server block. All traffic is proxied to the frontend Node server on port 4200.
The frontend's Express server handles `/api` proxying to the API gateway through the Docker network.

```bash
sudo tee /etc/nginx/conf.d/durion-alpha.conf << 'EOF'
server {
    listen 80;
    server_name positivity.durion.com;

    # Increase timeouts for SSR responses
    proxy_read_timeout 60s;
    proxy_connect_timeout 10s;

    # Forward real client IP
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    location / {
        proxy_pass http://127.0.0.1:4200;
    }
}
EOF

sudo nginx -t && sudo systemctl reload nginx
```

### 9.2 Obtain a Let's Encrypt Certificate

Wait for DNS propagation before running this step. Verify DNS resolves first:

```bash
host positivity.durion.com
# Should return the Elastic IP before proceeding
```

Then issue the certificate:

```bash
sudo certbot --nginx \
  --non-interactive \
  --agree-tos \
  --email ops@durion.com \
  -d positivity.durion.com

sudo systemctl reload nginx
```

certbot modifies the nginx config to add the SSL server block and HTTP → HTTPS redirect automatically.

### 9.3 Verify nginx TLS

```bash
curl -I https://positivity.durion.com
# Expected: HTTP/2 502 (gateway is not running yet — this confirms TLS works)
```

---

## Section 10 — Deploy Directory and Secrets

### 10.1 Create the Deploy Directory and Clone the Backend Repo

```bash
sudo mkdir -p /opt/durion/alpha
sudo chown ec2-user:ec2-user /opt/durion/alpha

cd /opt/durion/alpha
git clone https://github.com/louisburroughs/durion-positivity-backend.git backend
```

### 10.2 Create the Production Docker Compose Override

The default `docker-compose.yml` uses `build:` directives. For EC2 deployment, this override
replaces every custom service's `build:` with the corresponding ECR `image:` reference.

Set the image tags from your most recent CI run. The CI workflow tags images as `sha-<short-sha>`.

```bash
# Set these to the exact image tags pushed by your CI run
BACKEND_TAG=sha-$(cd /opt/durion/alpha/backend && git rev-parse --short HEAD)
FRONTEND_TAG=sha-<commit-sha-from-frontend-ci-run>   # Replace with actual tag
ECR_REGISTRY=<AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com  # Replace

cat > /opt/durion/alpha/docker-compose.prod.yml << EOF
# Production image override — replaces build: directives with ECR image: references.
# Usage: docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
services:
  pos-frontend:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-frontend:${FRONTEND_TAG}

  eureka-server:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/eureka-server:${BACKEND_TAG}

  pos-accounting:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-accounting:${BACKEND_TAG}

  pos-api-gateway:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-api-gateway:${BACKEND_TAG}

  pos-catalog:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-catalog:${BACKEND_TAG}

  pos-customer:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-customer:${BACKEND_TAG}

  pos-event-receiver:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-event-receiver:${BACKEND_TAG}

  pos-image:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-image:${BACKEND_TAG}

  pos-inventory:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-inventory:${BACKEND_TAG}

  pos-invoice:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-invoice:${BACKEND_TAG}

  pos-location:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-location:${BACKEND_TAG}

  pos-people:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-people:${BACKEND_TAG}

  pos-price:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-price:${BACKEND_TAG}

  pos-security-service:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-security-service:${BACKEND_TAG}

  pos-shop-manager:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-shop-manager:${BACKEND_TAG}

  pos-vehicle-inventory:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-vehicle-inventory:${BACKEND_TAG}

  pos-workorder:
    build: !reset null
    image: ${ECR_REGISTRY}/durion/pos-workorder:${BACKEND_TAG}
EOF
```

### 10.3 Create the Secrets File

Create `/opt/durion/alpha/.env` with strict permissions. This file is **never committed to source
control**.

```bash
touch /opt/durion/alpha/.env
chmod 600 /opt/durion/alpha/.env
```

Populate `/opt/durion/alpha/.env` with the following variables. Fill in all values before
proceeding to deployment.

```bash
# /opt/durion/alpha/.env
# Permissions: 600 — operator-owned — never committed to source control

# Postgres
POSTGRES_USER=pos_user
POSTGRES_PASSWORD=<STRONG_RANDOM_PASSWORD>
POSTGRES_DB=positivity

# Spring DataSource (same credentials)
SPRING_DATASOURCE_USERNAME=pos_user
SPRING_DATASOURCE_PASSWORD=<SAME_AS_POSTGRES_PASSWORD>

# Internal service event signing key (32+ character random string)
POS_EVENTS_API_SECRET=<RANDOM_SECRET>

# Grafana Cloud OTLP (leave blank to disable telemetry export)
OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp-gateway-prod-us-east-3.grafana.net/otlp
OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic <base64-encoded-instance-id:api-key>
```

Generate strong passwords on the host:

```bash
# Example password generation — use these values in the .env file
openssl rand -base64 32   # for POSTGRES_PASSWORD
openssl rand -base64 32   # for POS_EVENTS_API_SECRET
```

---

## Section 11 — Pull Images and Deploy

### 11.1 Authenticate Docker to ECR

The EC2 instance profile provides the credentials — no static keys needed.

```bash
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS \
    --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com
```

Where `AWS_ACCOUNT_ID` is your 12-digit AWS account ID. Replace the placeholder above.

### 11.2 Pre-pull Custom Images

Pre-pulling ensures all images are cached before starting the stack, preventing partial startup
failures due to network interruptions.

```bash
cd /opt/durion/alpha/backend

docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  pull
```

### 11.3 Start the Stack

```bash
docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  up -d

echo "Stack started. Wait ~3 minutes for all services to reach healthy state."
```

---

## Section 12 — Health Verification

### 12.1 Check Container Health

```bash
cd /opt/durion/alpha/backend

docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  ps
```

All services should show `healthy` or `running`. Services with healthchecks (Spring Boot services,
postgres, eureka, gateway, frontend) must be `healthy` before smoke runs.

### 12.2 Check Spring Boot Actuator Health

Run this for each backend service. Replace the port numbers as needed.

```bash
# Key service health checks
SERVICE_PORTS=(
  "pos-api-gateway:8080"
  "pos-accounting:9001"
  "pos-people:9011"
  "pos-security-service:9008"
  "pos-workorder:8090"
  "pos-shop-manager:9010"
  "eureka-server:8761"
)

for SP in "${SERVICE_PORTS[@]}"; do
  NAME=$(echo "$SP" | cut -d: -f1)
  PORT=$(echo "$SP" | cut -d: -f2)
  STATUS=$(curl -sf "http://localhost:${PORT}/actuator/health" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','UNKNOWN'))" \
    2>/dev/null || echo "UNREACHABLE")
  echo "${NAME}: ${STATUS}"
done
```

### 12.3 Check the Frontend

```bash
curl -I http://localhost:4200
# Expected: HTTP/1.1 200 OK

curl -I https://positivity.durion.com
# Expected: HTTP/2 200
```

---

## Section 13 — Reference Data Seed

**This step is required before running the smoke suite.** Several smoke routes address specific
entities by ID (e.g., `WO-123`, `EMP-123`). Without seeded records, those pages render in error
state and smoke results are meaningless.

For alpha, load reference data using the manual SQL script stored alongside the compose file.
A seed runner is a Phase 3 deliverable. Until then:

```bash
# Copy the seed SQL file to the host (run from local machine)
scp -i ~/.ssh/${KEY_PAIR_NAME}.pem \
  ./scripts/alpha-seed.sql \
  ec2-user@${ELASTIC_IP}:/opt/durion/alpha/

# Execute the seed SQL against the running Postgres container
docker compose \
  -f /opt/durion/alpha/backend/docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  exec -T postgres psql \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -f /dev/stdin < /opt/durion/alpha/alpha-seed.sql

echo "Reference data seed complete."
```

Document the seed version used in the deployment log:

```bash
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) seed=alpha-seed.sql commit=$(git -C /opt/durion/alpha/backend rev-parse --short HEAD)" \
  >> /opt/durion/alpha/deployment.log
```

---

## Section 14 — Daily Backup Cron

### 14.1 Create the Backup Script

```bash
sudo mkdir -p /opt/durion/scripts
sudo tee /opt/durion/scripts/backup.sh << 'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE=/opt/durion/alpha/backend/docker-compose.yml
OVERRIDE_FILE=/opt/durion/alpha/docker-compose.prod.yml
ENV_FILE=/opt/durion/alpha/.env
S3_BUCKET=durion-positivity-backups
DATE=$(date +%Y-%m-%d)

# Read POSTGRES_USER and POSTGRES_DB from .env
POSTGRES_USER=$(grep '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2)
POSTGRES_DB=$(grep '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2)

docker compose \
  -f "$COMPOSE_FILE" \
  -f "$OVERRIDE_FILE" \
  exec -T postgres \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" \
  | gzip \
  | aws s3 cp - "s3://${S3_BUCKET}/alpha/${DATE}.sql.gz" \
    --region us-east-1

echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) Backup completed: s3://${S3_BUCKET}/alpha/${DATE}.sql.gz"
SCRIPT

sudo chmod 750 /opt/durion/scripts/backup.sh
sudo chown root:ec2-user /opt/durion/scripts/backup.sh
```

### 14.2 Schedule via Cron

```bash
# Runs at 02:00 UTC daily
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/durion/scripts/backup.sh >> /var/log/durion-backup.log 2>&1") \
  | crontab -

# Create log file
sudo touch /var/log/durion-backup.log
sudo chown ec2-user:ec2-user /var/log/durion-backup.log

crontab -l
```

### 14.3 Verify the First Backup

Run a manual test immediately to confirm the script works correctly before depending on the cron.

```bash
/opt/durion/scripts/backup.sh && echo "Backup test: OK"
aws s3 ls "s3://${S3_BACKUP_BUCKET}/alpha/" --region us-east-1
```

---

## Section 15 — Smoke Suite

After reference data is loaded and all services are healthy, run the frontend smoke suite against
the live cell.

**From your local machine (or a CI runner with access to the frontend repo):**

```bash
cd ~/IdeaProjects/durion-positivity-frontend

A11Y_USE_EXISTING_SERVER=1 \
A11Y_BASE_URL=https://positivity.durion.com \
A11Y_FAIL_ON_IMPACT=critical \
npm run a11y:smoke
```

A deployment is **not considered promoted** until:

1. All `/actuator/health` endpoints return `UP`
2. All 8 smoke routes return HTTP 200 with no `critical` accessibility violations
3. The smoke result is logged alongside the deployment record

---

## Section 16 — Post-Deployment Checklist

| Check | Command | Expected |
|---|---|---|
| All containers healthy | `docker compose ps` | All `healthy` or `running` |
| Postgres accepting connections | `docker compose exec postgres pg_isready` | `accepting connections` |
| Eureka dashboard reachable | `curl -s http://localhost:8761/` (internal only) | HTTP 200 |
| Frontend responding | `curl -I https://positivity.durion.com` | HTTP 2xx |
| TLS certificate valid | `curl -vI https://positivity.durion.com 2>&1 \| grep 'expire date'` | Date > 60 days |
| S3 backup ran | `aws s3 ls s3://durion-positivity-backups/alpha/` | Object present |
| Cron scheduled | `crontab -l` | Backup entry visible |
| Smoke suite passed | Smoke output | 0 critical violations |
| Seed version logged | `cat /opt/durion/alpha/deployment.log` | Seed entry present |

---

## Secrets Configuration

The following GitHub Actions secrets must be set in each repository before the CI/CD workflows
can push images to ECR.

### Required in `durion-positivity-frontend`

| Secret | Value |
|---|---|
| `AWS_ROLE_ARN` | ARN of `durion-gha-ecr-push` role (printed in Section 3.2) |
| `AWS_ACCOUNT_ID` | Your 12-digit AWS account ID |

### Required in `durion-positivity-backend`

| Secret | Value |
|---|---|
| `AWS_ROLE_ARN` | ARN of `durion-gha-ecr-push` role |
| `AWS_ACCOUNT_ID` | Your 12-digit AWS account ID |

Navigate to each GitHub repository → Settings → Secrets and variables → Actions → New repository
secret.

---

## GitHub Actions CI/CD Workflows Reference

The following workflow files handle image builds and ECR pushes. These workflows must run
successfully at least once before executing Section 11 (Deploy).

| File | Repository | Trigger |
|---|---|---|
| `.github/workflows/build-push-ecr.yml` | `durion-positivity-frontend` | Push to `master`, `workflow_dispatch` |
| `.github/workflows/build-push-ecr.yml` | `durion-positivity-backend` | Push to `master`, `workflow_dispatch` |

Image tags use the format `sha-<7-char-git-sha>` (e.g., `sha-a1b2c3d`). Retrieve the tag for
each repo from the GitHub Actions run summary, then use those tags when creating
`docker-compose.prod.yml` in Section 10.2.

---

## Rollback Procedure

If a deployment must be rolled back:

1. Identify the previous working image tags from the GitHub Actions run history.
2. Update `/opt/durion/alpha/docker-compose.prod.yml` with the previous `BACKEND_TAG` and `FRONTEND_TAG`.
3. Re-pull and restart:

```bash
cd /opt/durion/alpha/backend

docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  pull

docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  up -d --force-recreate
```

1. Re-run the smoke suite to confirm the rollback cell is healthy.
2. Log the rollback action in `/opt/durion/alpha/deployment.log`.
