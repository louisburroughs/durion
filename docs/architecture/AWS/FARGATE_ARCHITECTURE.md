---
title: Durion Positivity — AWS Fargate Architecture
status: reference
environment: production / scaled tenant cells
last-updated: 2026-04-14
scope: ECS Fargate multi-AZ deployment on AWS
---

# Durion Positivity — AWS Fargate Architecture

## Overview

This document describes the target production architecture for the Durion Positivity POS platform running on AWS ECS with the Fargate launch type. Fargate eliminates EC2 instance management, provides per-task compute isolation, and enables fine-grained auto-scaling per service — making it the natural evolution from the single-host Docker/Podman alpha model.

The application images, environment variable contracts, and OpenAPI specs are unchanged from the alpha model. Only the hosting substrate changes.

---

## Architecture Diagram

```
Internet
    │
    ▼
Route 53 (durionpos.org)
    │
    ▼
AWS WAF
    │
    ▼
Application Load Balancer (ACM TLS certificate)
  ├── / → Target Group: pos-frontend (port 4000)
  ├── /api/* → Target Group: pos-api-gateway (port 8080)
  ├── /security-service/* → Target Group: pos-api-gateway
  └── /mcp-server/* → Target Group: pos-api-gateway
    │
    ▼
VPC (us-east-1)
  ├── Public Subnets (AZ-a, AZ-b)  ← ALB only
  └── Private Subnets (AZ-a, AZ-b) ← All ECS tasks
        │
        ▼
ECS Cluster (Fargate)
  ├── pos-frontend              (Angular SSR, Node 22)
  ├── pos-api-gateway           (Spring Boot, :8080)
  ├── pos-security-service      (Spring Boot, :8080)
  ├── pos-event-receiver        (Spring Boot, :8080 — internal only)
  ├── pos-accounting            (Spring Boot, :8080)
  ├── pos-catalog               (Spring Boot, :8080)
  ├── pos-customer              (Spring Boot, :8080)
  ├── pos-image                 (Spring Boot, :8080)
  ├── pos-inventory             (Spring Boot, :8080)
  ├── pos-invoice               (Spring Boot, :8080)
  ├── pos-location              (Spring Boot, :8080)
  ├── pos-mcp-server            (Spring Boot, :8086)
  ├── pos-people                (Spring Boot, :8080)
  ├── pos-price                 (Spring Boot, :8080)
  ├── pos-shop-manager          (Spring Boot, :8080)
  ├── pos-vehicle-inventory     (Spring Boot, :8080)
  └── pos-workorder             (Spring Boot, :8080)
        │
        ▼
AWS Managed Services (Private Subnets)
  ├── Amazon RDS for PostgreSQL (Multi-AZ)
  │     └── One database per service (pos_accounting_db, pos_customer_db, ...)
  ├── Amazon MSK (Managed Kafka) ← pos-workorder, pos-vehicle-inventory
  ├── Amazon ElastiCache (Redis) ← pos-security-service session cache
  └── Amazon S3                  ← pos-image, pos-documents, backups
        │
        ▼
Observability (Private Subnets or Managed)
  ├── AWS Distro for OpenTelemetry (ADOT) Collector — ECS sidecar
  ├── AWS X-Ray ← traces
  ├── Amazon Managed Prometheus (AMP) ← metrics
  └── Amazon Managed Grafana (AMG) ← dashboards
```

---

## AWS Infrastructure

### Networking

| Resource | Configuration |
|---|---|
| VPC | Single VPC, CIDR `10.0.0.0/16` |
| Public subnets | 2 × AZ (`10.0.1.0/24`, `10.0.2.0/24`) — ALB only |
| Private subnets | 2 × AZ (`10.0.10.0/24`, `10.0.20.0/24`) — ECS tasks, RDS |
| NAT Gateway | 1 per AZ for outbound internet from private subnets |
| VPC Endpoints | `ecr.api`, `ecr.dkr`, `s3`, `secretsmanager`, `ssm`, `logs` |

VPC endpoints eliminate NAT Gateway data transfer costs for ECR pulls and Secrets Manager calls, which are frequent in a microservices deployment.

### Load Balancing and TLS

| Resource | Configuration |
|---|---|
| ALB | Internet-facing, public subnets |
| ACM Certificate | `*.durionpos.org` — auto-renewed |
| WAF | AWS Managed Rules (Core Rule Set + Known Bad Inputs) |
| Listener rules | Path-based routing to target groups |
| Health checks | `GET /actuator/health` (backend), `GET /` (frontend) |

### Compute — ECS Fargate

Each microservice runs as an independent ECS Service with its own Task Definition. Recommended starting sizes:

| Service | CPU | Memory | Min tasks | Max tasks |
|---|---|---|---|---|
| pos-api-gateway | 1 vCPU | 2 GB | 2 | 10 |
| pos-security-service | 0.5 vCPU | 1 GB | 2 | 6 |
| pos-frontend | 0.5 vCPU | 1 GB | 2 | 6 |
| pos-accounting | 0.5 vCPU | 1 GB | 1 | 4 |
| pos-customer | 0.5 vCPU | 1 GB | 1 | 4 |
| pos-mcp-server | 1 vCPU | 2 GB | 1 | 2 |
| All other services | 0.5 vCPU | 1 GB | 1 | 4 |

All tasks run in private subnets with no public IP. Outbound internet access is via NAT Gateway.

### Database — Amazon RDS for PostgreSQL

| Resource | Configuration |
|---|---|
| Engine | PostgreSQL 16 with TimescaleDB extension |
| Deployment | Multi-AZ (primary + standby replica) |
| Instance class | `db.t4g.medium` (starting) |
| Storage | 100 GB `gp3`, auto-scaling enabled |
| Databases | One per service (same schema as Docker model) |
| Credentials | AWS Secrets Manager — rotated automatically |
| Backups | Automated daily snapshots, 14-day retention |
| Migrations | Flyway runs on service startup (unchanged) |

The `SPRING_DATASOURCE_URL` changes from `jdbc:postgresql://postgres:5432/pos_accounting_db` to the RDS endpoint — no application code changes required.

### Secrets Management

All secrets are stored in AWS Secrets Manager under the `durion/alpha/` prefix and injected into ECS task definitions as environment variables at launch time. No `.env` files on hosts.

| Secret | Path |
|---|---|
| Database credentials | `durion/alpha/postgres` |
| JWT secret | `durion/alpha/security/jwt-secret` |
| Stripe API key | `durion/alpha/stripe/api-key` |
| POS Events API secret | `durion/alpha/events/api-secret` |
| POS Security API secret | `durion/alpha/security/api-secret` |

---

## Service Discovery

Eureka is replaced by **AWS Cloud Map** at the Fargate tier. Each ECS service registers with a Cloud Map private DNS namespace (`pos.local`). The API gateway routes to `http://pos-accounting.pos.local:8080` instead of `lb://POS-ACCOUNTING`.

This requires updating `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` → `SPRING_CLOUD_DISCOVERY_ENABLED=false` and replacing gateway route URIs in `pos-api-gateway/application.yml`. All other service code is unchanged.

Alternatively, the ALB can serve as the service mesh with internal target groups per service — no code changes required to the gateway.

---

## Auto-Scaling

Each ECS service is configured with Application Auto Scaling:

```
Scale out: CPU > 70% for 2 consecutive minutes → add 1 task
Scale in:  CPU < 30% for 5 consecutive minutes → remove 1 task
```

The API gateway and security service scale more aggressively as they handle all inbound traffic. Domain services scale independently based on their own CPU/memory metrics.

---

## Messaging — Amazon MSK (Kafka)

`pos-workorder` and `pos-vehicle-inventory` use Kafka. In the Fargate model, the local Kafka container is replaced by Amazon MSK:

| Resource | Configuration |
|---|---|
| Broker type | `kafka.t3.small` (2 brokers, Multi-AZ) |
| Kafka version | 3.5.x |
| Authentication | IAM (no stored credentials) |
| Topics | `workorder.events.v1`, `vehicle-inventory-events` |

`SPRING_KAFKA_BOOTSTRAP_SERVERS` is updated to the MSK broker endpoints. No application code changes.

---

## AI / MCP Server — Ollama

`pos-mcp-server` depends on Ollama for LLM inference. Options in Fargate:

| Option | Trade-off |
|---|---|
| Ollama on EC2 GPU instance (`g4dn.xlarge`) | Best performance, separate from Fargate |
| Amazon Bedrock (Claude / Titan) | Fully managed, no Ollama, requires LangChain4j Bedrock adapter |
| Ollama as ECS task on Fargate (CPU only) | Simple but slow for large models |

Recommended: Ollama on a dedicated `g4dn.xlarge` EC2 instance in the private subnet. `pos-mcp-server` connects via `OLLAMA_BASE_URL=http://ollama.pos.local:11434` through Cloud Map DNS.

---

## Observability

The Grafana OTEL Java agent bundled in each service image continues to emit telemetry. In Fargate, the OTEL Collector runs as a sidecar container in each task definition rather than as a standalone service.

| Signal | Pipeline |
|---|---|
| Traces | ADOT sidecar → AWS X-Ray |
| Metrics | ADOT sidecar → Amazon Managed Prometheus (AMP) |
| Logs | CloudWatch Logs (awslogs driver, one log group per service) |
| Dashboards | Amazon Managed Grafana (AMG) — datasources: AMP + X-Ray + CloudWatch |

```yaml
# Task definition sidecar (excerpt)
- name: aws-otel-collector
  image: public.ecr.aws/aws-observability/aws-otel-collector:latest
  environment:
    - name: AWS_REGION
      value: us-east-1
  command:
    - --config=/etc/ecs/ecs-default-config.yaml
```

---

## CI/CD

The `build-push-ecr.yml` workflow pushes images to ECR unchanged. The `deploy-alpha` job is replaced by an ECS deployment step:

```yaml
- name: Deploy to ECS
  run: |
    for SERVICE in "${BACKEND_SERVICES[@]}"; do
      aws ecs update-service \
        --cluster durion-alpha \
        --service "$SERVICE" \
        --force-new-deployment \
        --region us-east-1
    done
```

ECS performs a rolling update: new tasks start, pass health checks, then old tasks are drained. No downtime for stateless services.

---

## IAM — Task Execution Roles

Each ECS task requires two IAM roles:

**Task Execution Role** (used by ECS agent to launch the task):
- `ecr:GetAuthorizationToken`, `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer`
- `secretsmanager:GetSecretValue` (scoped to `durion/alpha/*`)
- `logs:CreateLogStream`, `logs:PutLogEvents`

**Task Role** (used by the application at runtime):
- `s3:GetObject`, `s3:PutObject` (scoped to `durion-alpha-deploy/*`) — pos-image, pos-documents
- `xray:PutTraceSegments`, `xray:PutTelemetryRecords` — all services
- `aps:RemoteWrite` — ADOT sidecar

---

## Tenant Cell Model

Each paying customer receives a dedicated ECS cluster (or dedicated ECS services within a shared cluster with separate task definitions and RDS databases). The tenant isolation model from `FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md` is preserved:

- Dedicated RDS instance per tenant
- Dedicated Secrets Manager paths per tenant (`durion/<tenant-id>/`)
- Dedicated CloudWatch log groups per tenant
- Dedicated ECS services per tenant (or separate cluster for full isolation)
- Separate Route 53 subdomain per tenant (`<tenant>.durionpos.org`)

---

## Cost Estimate (Single Tenant Cell)

| Resource | Approx. monthly cost |
|---|---|
| ECS Fargate (17 services × 0.5 vCPU × 1 GB, 1 task each) | ~$180 |
| ALB | ~$25 |
| RDS PostgreSQL Multi-AZ `db.t4g.medium` | ~$100 |
| MSK `kafka.t3.small` (2 brokers) | ~$100 |
| NAT Gateway (2 AZs) | ~$65 |
| CloudWatch Logs | ~$20 |
| Secrets Manager | ~$5 |
| ECR storage | ~$5 |
| **Total estimate** | **~$500/month** |

Costs reduce significantly with Savings Plans (up to 50% on Fargate compute) and Graviton task definitions (`ARM64`).

---

## Migration Path from Docker/Podman on EC2

| Step | Action |
|---|---|
| 1 | Create VPC, subnets, NAT Gateways, VPC endpoints |
| 2 | Provision RDS — update `SPRING_DATASOURCE_URL` in task definitions |
| 3 | Provision MSK — update `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| 4 | Create ECS cluster and task definitions (one per service) |
| 5 | Replace Eureka with Cloud Map or ALB internal routing |
| 6 | Create ALB, target groups, listener rules |
| 7 | Attach WAF to ALB |
| 8 | Migrate secrets from `.env` file to Secrets Manager |
| 9 | Update CI/CD deploy step from SSM to `ecs update-service` |
| 10 | Cut over Route 53 record from EC2 Elastic IP to ALB DNS name |

Steps 2–4 can be run in parallel. The EC2 host remains live until Route 53 cutover, enabling a zero-downtime migration.
