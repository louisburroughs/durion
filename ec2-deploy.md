# EC2 SSM Deployment Setup

This document covers everything required to enable the `deploy-alpha` job in
`build-push-ecr.yml` to deploy backend services to the alpha EC2 instance via
AWS Systems Manager (SSM) instead of SSH.

## Overview

The deployment flow is:

1. GitHub Actions assumes the OIDC role (`AWS_ROLE_ARN`)
2. Compose files are uploaded to S3 (`ALPHA_DEPLOY_BUCKET`)
3. SSM `send-command` pulls the compose files from S3 onto the instance
4. SSM `send-command` runs `/opt/durion/alpha/scripts/deploy-backend.sh` on the instance
5. The script updates the `.env` file and restarts Docker Compose services

---

## Prerequisites

- EC2 instance is visible in **Systems Manager → Fleet Manager** as Online
- EC2 instance role has `AmazonSSMManagedInstanceCore` attached
- GitHub OIDC role (`AWS_ROLE_ARN`) has the required IAM permissions (see below)
- An S3 bucket exists for staging compose files (`ALPHA_DEPLOY_BUCKET`)
- The deploy script exists on the instance at `/opt/durion/alpha/scripts/deploy-backend.sh`

---

## 1. S3 Bucket

Create this first — the IAM policies in the next steps reference its ARN.

```bash
aws s3api create-bucket \
  --bucket durion-alpha-deploy \
  --region us-east-1
```

Block all public access:

```bash
aws s3api put-public-access-block \
  --bucket durion-alpha-deploy \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

---

## 2. IAM — GitHub OIDC Role Permissions

The role assumed by GitHub Actions (`AWS_ROLE_ARN`) must include:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:SendCommand",
        "ssm:GetCommandInvocation",
        "ssm:ListCommandInvocations"
      ],
      "Resource": [
        "arn:aws:ec2:us-east-1:288757602241:instance/i-06d434c7593e70f5c",
        "arn:aws:ssm:us-east-1::document/AWS-RunShellScript"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject"],
      "Resource": "arn:aws:s3:::durion-alpha-deploy/alpha/*"
    }
  ]
}
```

Replace `<account-id>`, `<instance-id>`, and `<bucket-name>` with your values.

To add this in the AWS Console:

1. Go to **IAM → Roles**, find the role used by `AWS_ROLE_ARN`
2. **Permissions → Add permissions → Create inline policy**
3. Paste the JSON above and save

---

## 3. IAM — EC2 Instance Role Permissions

The instance role must have `AmazonSSMManagedInstanceCore` attached **and** must
be able to pull from ECR and read from the S3 deploy bucket.

Attach the following managed policies to the instance role:

| Policy                               | Purpose                                           |
| ------------------------------------ | ------------------------------------------------- |
| `AmazonSSMManagedInstanceCore`       | Allows SSM agent to register and receive commands |
| `AmazonEC2ContainerRegistryReadOnly` | Allows `docker pull` from ECR                     |

Add an inline policy for S3 bucket access:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": "arn:aws:s3:::durion-alpha-deploy/alpha/*"
    }
  ]
}
```

To attach in the AWS Console:

1. Go to **EC2 → Instances**, select the alpha instance
2. **Security tab → IAM Role → click the role link**
3. **Add permissions → Attach policies** for the managed policies
4. **Add permissions → Create inline policy** for the S3 statement above

---

## 4. GitHub Secrets & Variables

Add the following in **GitHub → Repository → Settings → Secrets and variables → Actions**:

| Name                                | Type     | Value                                                |
| ----------------------------------- | -------- | ---------------------------------------------------- |
| `AWS_ROLE_ARN`                      | Secret   | ARN of the GitHub OIDC IAM role                      |
| `ALPHA_EC2_INSTANCE_ID`             | Secret   | EC2 instance ID, e.g. `i-0abc123def456`              |
| `ALPHA_DEPLOY_BUCKET`               | Secret   | S3 bucket name (no `s3://` prefix)                   |
| `SECURITY_SEED_ADMIN_PASSWORD_HASH` | Secret   | Bcrypt hash for seeded admin password                |
| `AUTO_DEPLOY_ALPHA`                 | Variable | Set to `true` to auto-deploy on every push to `main` |

The old SSH secrets (`ALPHA_EC2_HOST`, `ALPHA_EC2_USER`, `ALPHA_EC2_SSH_KEY`) are
no longer required and can be removed.

---

## 5. On-Instance Deploy Script

The SSM command calls `/opt/durion/alpha/scripts/deploy-backend.sh` on the instance.
Create this file on the instance once (SSH in while you still have access, or use
SSM Session Manager):

```bash
sudo mkdir -p /opt/durion/alpha/scripts
sudo tee /opt/durion/alpha/scripts/deploy-backend.sh > /dev/null << 'EOF'
#!/usr/bin/env bash
set -euo pipefail

GITHUB_SHA="${1:?GITHUB_SHA argument required}"
IMAGE_TAG="sha-${GITHUB_SHA::7}"

ALPHA_ROOT="/opt/durion/alpha"
BACKEND_DIR="${ALPHA_ROOT}/backend"
ENV_FILE="${ALPHA_ROOT}/.env"
PROD_OVERRIDE="${ALPHA_ROOT}/docker-compose.prod.yml"

BACKEND_SERVICES=(
  eureka-server
  pos-accounting
  pos-api-gateway
  pos-catalog
  pos-customer
  pos-event-receiver
  pos-image
  pos-inventory
  pos-invoice
  pos-location
  pos-mcp-server
  pos-people
  pos-price
  pos-security-service
  pos-shop-manager
  pos-vehicle-inventory
  pos-workorder
)

# Update BACKEND_TAG in .env
if grep -q '^BACKEND_TAG=' "${ENV_FILE}"; then
  sed -i "s/^BACKEND_TAG=.*/BACKEND_TAG=${IMAGE_TAG}/" "${ENV_FILE}"
else
  printf '\nBACKEND_TAG=%s\n' "${IMAGE_TAG}" >> "${ENV_FILE}"
fi

# Update SECURITY_SEED_ADMIN_PASSWORD_HASH in .env
if [[ -z "${SECURITY_SEED_ADMIN_PASSWORD_HASH:-}" ]]; then
  echo "SECURITY_SEED_ADMIN_PASSWORD_HASH is required."
  exit 1
fi
if grep -q '^SECURITY_SEED_ADMIN_PASSWORD_HASH=' "${ENV_FILE}"; then
  sed -i "s|^SECURITY_SEED_ADMIN_PASSWORD_HASH=.*|SECURITY_SEED_ADMIN_PASSWORD_HASH=${SECURITY_SEED_ADMIN_PASSWORD_HASH}|" "${ENV_FILE}"
else
  printf 'SECURITY_SEED_ADMIN_PASSWORD_HASH=%s\n' "${SECURITY_SEED_ADMIN_PASSWORD_HASH}" >> "${ENV_FILE}"
fi

# Derive and update ECR_REGISTRY from instance role
ECR_REGISTRY="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-east-1.amazonaws.com"
if grep -q '^ECR_REGISTRY=' "${ENV_FILE}"; then
  sed -i "s|^ECR_REGISTRY=.*|ECR_REGISTRY=${ECR_REGISTRY}|" "${ENV_FILE}"
else
  printf 'ECR_REGISTRY=%s\n' "${ECR_REGISTRY}" >> "${ENV_FILE}"
fi

# Log in to ECR
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

cd "${BACKEND_DIR}"

COMPOSE_ARGS=(
  -f docker-compose.yml
  -f "${PROD_OVERRIDE}"
  --env-file "${ENV_FILE}"
)

# Reconcile postgres image if changed
DESIRED_POSTGRES_IMAGE="$(
  docker compose "${COMPOSE_ARGS[@]}" config \
    | sed -n '/^  postgres:/,/^[^ ]/p' \
    | awk '/image:/ {print $2; exit}'
)"
CURRENT_POSTGRES_CONTAINER_ID="$(docker compose "${COMPOSE_ARGS[@]}" ps -q postgres 2>/dev/null || true)"
CURRENT_POSTGRES_IMAGE=""
if [[ -n "${CURRENT_POSTGRES_CONTAINER_ID}" ]]; then
  CURRENT_POSTGRES_IMAGE="$(docker inspect -f '{{.Config.Image}}' "${CURRENT_POSTGRES_CONTAINER_ID}" 2>/dev/null || true)"
fi
if [[ -n "${DESIRED_POSTGRES_IMAGE}" && "${CURRENT_POSTGRES_IMAGE}" != "${DESIRED_POSTGRES_IMAGE}" ]]; then
  echo "Reconciling postgres image: current='${CURRENT_POSTGRES_IMAGE:-<none>}' desired='${DESIRED_POSTGRES_IMAGE}'"
  docker compose "${COMPOSE_ARGS[@]}" pull postgres
  docker compose "${COMPOSE_ARGS[@]}" up -d --force-recreate postgres
fi

docker compose "${COMPOSE_ARGS[@]}" pull "${BACKEND_SERVICES[@]}"
docker compose "${COMPOSE_ARGS[@]}" up -d --force-recreate "${BACKEND_SERVICES[@]}"
docker compose "${COMPOSE_ARGS[@]}" ps
EOF
sudo chmod +x /opt/durion/alpha/scripts/deploy-backend.sh
```

---

## 6. Verify SSM Access

Before triggering the workflow, confirm SSM can reach the instance:

```bash
aws ssm send-command \
  --instance-ids "i-06d434c7593e70f5c" \
  --document-name "AWS-RunShellScript" \
  --parameters '{"commands":["echo hello from SSM"]}' \
  --region us-east-1 \
  --query "Command.CommandId" --output text
```

Then check the result (replace `<command-id>`):

```bash
aws ssm get-command-invocation \
  --command-id "f4d66728-109f-443f-8783-1987dc914aaa" \
  --instance-id "i-06d434c7593e70f5c" \
  --region us-east-1 \
  --query "[StandardOutputContent,StandardErrorContent]" \
  --output text
```

Expected output: `hello from SSM`

---

## 7. Triggering a Deployment

**Manual (workflow_dispatch):**

1. Go to **GitHub → Actions → Build and Push to ECR**
2. Click **Run workflow**
3. Check **Deploy to alpha EC2 after pushing images**
4. Click **Run workflow**

**Automatic on every push to `main`:**

Set the repository variable `AUTO_DEPLOY_ALPHA` to `true` in
**Settings → Secrets and variables → Actions → Variables**.
