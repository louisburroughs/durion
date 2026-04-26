---
title: Durion Positivity — Docker on EC2 Architecture
status: reference
environment: alpha / prototype
last-updated: 2026-04-14
scope: Single-host Docker Compose deployment on AWS EC2
---

# Durion Positivity — Docker on EC2 Architecture

## Overview

This document describes the architecture for running the Durion Positivity POS platform as a Docker Compose stack on a single AWS EC2 instance. This is the current alpha
deployment model and the lowest-cost path to a fully operational tenant cell.

---

## Architecture Diagram

```ascii
Internet
    │
    ▼
Route 53 (durionpos.org)
    │
    ▼
Elastic IP → EC2 t3.2xlarge (Amazon Linux 2023)
    │
    ▼
Nginx (TLS termination — Let's Encrypt)
  ├── :443 /          → localhost:4200  (Angular SSR frontend)
  └── :443 /api       → localhost:8080  (API Gateway)
       /security-service
       /mcp-server
    │
    ▼
Docker bridge network: pos-network
  ├── pos-frontend          :4200 → :4000
  ├── pos-api-gateway       :8080 → :8080
  ├── eureka-server         :8761 → :8761
  ├── pos-security-service  :8086 → :8080
  ├── pos-accounting              → :8080
  ├── pos-catalog                 → :8080
  ├── pos-customer                → :8080
  ├── pos-event-receiver          → :8080  (internal only)
  ├── pos-image                   → :8080
  ├── pos-inventory               → :8080
  ├── pos-invoice                 → :8080
  ├── pos-location                → :8080
  ├── pos-mcp-server        :8094 → :8086
  ├── pos-people                  → :8080
  ├── pos-price                   → :8080
  ├── pos-shop-manager            → :8080
  ├── pos-vehicle-inventory       → :8080
  ├── pos-workorder               → :8080
  ├── postgres-positivity   127.0.0.1:5432
  ├── ollama                :11434
  ├── otel-collector        :4317/:4318
  ├── prometheus            :9090
  ├── grafana               :3000
  └── jaeger                :16686
```

---

## AWS Infrastructure

| Resource           | Value                                     |
| ------------------ | ----------------------------------------- |
| Instance type      | `t3.2xlarge` (8 vCPU, 32 GB RAM)          |
| OS                 | Amazon Linux 2023                         |
| Storage            | 80 GB `gp3` EBS root volume               |
| Networking         | Elastic IP, Security Group                |
| DNS                | Route 53 A record → Elastic IP            |
| TLS                | Let's Encrypt via `certbot --nginx`       |
| Container registry | Amazon ECR (`durion/*`)                   |
| Secrets            | `/opt/durion/alpha/.env` (mode 600)       |
| Backups            | Daily `pg_dump` → S3 via instance profile |

### Security Group Rules

| Direction | Port | Source           | Purpose                      |
| --------- | ---- | ---------------- | ---------------------------- |
| Inbound   | 443  | `0.0.0.0/0`      | HTTPS traffic                |
| Inbound   | 80   | `0.0.0.0/0`      | Let's Encrypt HTTP challenge |
| Inbound   | 22   | Operator IP only | SSH access                   |
| Outbound  | All  | `0.0.0.0/0`      | ECR pulls, S3, AWS APIs      |

---

## Service Topology

### Startup Dependency Order

```
postgres ──────────────────────────────────────────────┐
eureka-server ─────────────────────────────────────────┤
pos-event-receiver (depends: postgres, eureka) ────────┤
pos-security-service (depends: postgres, eureka,       │
                      pos-event-receiver) ─────────────┤
                                                        ▼
                              All domain services (depend: pos-security-service)
                                                        │
                                                        ▼
                              pos-api-gateway (depends: eureka-server)
                                                        │
                                                        ▼
                              pos-frontend (depends: pos-api-gateway)
```

### Service Discovery

Eureka (`pos-service-discovery`) is retained in the alpha cell. All services register using their Docker Compose service name. The API gateway routes via `lb://SERVICE-NAME`
resolved through Eureka. This is revisited at ECS migration where AWS Cloud Map replaces Eureka.

### Database

A single `timescale/timescaledb:2.17.2-pg16` container serves all microservices. Each service connects to its own dedicated database (e.g. `pos_accounting_db`,
`pos_customer_db`). The `postgres/init-databases.sql` script creates all databases on first run. Port binding is restricted to `127.0.0.1:5432` — no external exposure.

### Secrets Management

All secrets are injected via `/opt/durion/alpha/.env` at `docker compose` invocation time. The file is never committed to source control. The EC2 instance profile grants ECR
pull and S3 access — no long-lived AWS access keys are stored on the host.

---

## Observability

| Signal  | Component                                 | Port                    |
| ------- | ----------------------------------------- | ----------------------- |
| Traces  | OTEL Collector → Jaeger                   | 4317/4318 → 16686       |
| Metrics | OTEL Collector → Prometheus → Grafana     | 8888/8889 → 9090 → 3000 |
| Logs    | Logback JSON → stdout → Docker log driver | —                       |

All services emit telemetry to the OTEL Collector via `OTEL_EXPORTER_OTLP_ENDPOINT`. The Grafana OTEL Java agent is bundled in every service image.

---

## CI/CD

Images are built by the `build-push-ecr.yml` GitHub Actions workflow and pushed to ECR. The `deploy-alpha` job uses AWS SSM `send-command` to pull updated images and restart
services on the EC2 host — no SSH keys are required. Deployment is triggered manually via `workflow_dispatch` or automatically when `AUTO_DEPLOY_ALPHA=true`.

```
GitHub Actions (OIDC role)
    │
    ├── Maven build → ECR push (parallel matrix per service)
    │
    └── SSM send-command → EC2
            ├── aws s3 cp compose files from S3
            └── deploy-backend.sh
                    ├── Update BACKEND_TAG in .env
                    ├── docker compose pull --quiet
                    └── docker compose up -d --force-recreate
```

---

## Backup and Recovery

- Daily `pg_dump` cron job uploads compressed dump to S3 using the instance profile
- 14-day retention via S3 lifecycle rule
- Restore is a manual operator step: stop stack → restore dump → restart stack
- Recovery objective: best-effort (up to 24 hours data loss acceptable for prototype)

---

## Limitations and Migration Path

| Limitation                | Resolution at ECS migration                                               |
| ------------------------- | ------------------------------------------------------------------------- |
| Single host — no HA       | ECS tasks distributed across AZs                                          |
| Manual scaling            | ECS Application Auto Scaling                                              |
| Eureka service discovery  | AWS Cloud Map or ALB target groups                                        |
| Single Postgres container | Amazon RDS for PostgreSQL (config-only change to `SPRING_DATASOURCE_URL`) |
| Nginx on host             | AWS ALB with ACM certificate                                              |
| SSM deploy script         | ECS `update-service --force-new-deployment`                               |

The application images, environment variable contracts, and OpenAPI specs are unchanged between this model and ECS/Fargate — the host is the only thing that changes.

---

## Extension — Managed Cloud Services Variant

This extension describes a hybrid upgrade to the Docker on EC2 model that offloads Ollama, Kafka, Grafana, and PostgreSQL to dedicated managed or cloud-hosted services. The
application EC2 host shrinks significantly — it runs only the Spring Boot microservices, the Angular frontend, Eureka, and the OTEL Collector. Everything stateful or
compute-intensive moves off-host.

### Updated Architecture Diagram

```
Internet
    │
    ▼
Route 53 (durionpos.org)
    │
    ▼
Elastic IP → EC2 t3.xlarge (application containers only)
    │
    ▼
Nginx (TLS termination — Let's Encrypt)
  ├── :443 /          → localhost:4200  (Angular SSR frontend)
  └── :443 /api       → localhost:8080  (API Gateway)
    │
    ▼
Docker bridge network: pos-network
  ├── pos-frontend
  ├── pos-api-gateway
  ├── eureka-server
  ├── pos-security-service
  ├── pos-accounting
  ├── pos-catalog
  ├── pos-customer
  ├── pos-event-receiver
  ├── pos-image
  ├── pos-inventory
  ├── pos-invoice
  ├── pos-location
  ├── pos-mcp-server          ──────────────────────────────────────┐
  ├── pos-people                                                     │
  ├── pos-price                                                      │
  ├── pos-shop-manager                                               │
  ├── pos-vehicle-inventory   ──────────────────────────────────┐   │
  ├── pos-workorder           ──────────────────────────────┐   │   │
  └── otel-collector          ──────────────────────────┐   │   │   │
                                                         │   │   │   │
                                                         ▼   ▼   ▼   ▼
External Managed Services (private network / VPC peering or VPN)
  ├── Dedicated PostgreSQL EC2 (db.t3.2xlarge, TimescaleDB)
  │     └── All per-service databases (pos_accounting_db, pos_customer_db, ...)
  ├── Amazon MSK (Managed Kafka)
  │     └── Topics: workorder-events, vehicle-inventory-events
  ├── Grafana Cloud
  │     └── Receives OTLP metrics + traces from otel-collector
  └── Ollama Cloud (ollama.com hosted) or EC2 GPU instance
        └── OLLAMA_BASE_URL → pos-mcp-server
```

### Managed Service Replacements

#### PostgreSQL — Dedicated EC2 Database Host

PostgreSQL moves from a container on the application host to a dedicated EC2 instance running TimescaleDB. This provides:

- Independent scaling of compute and storage from the application tier
- Persistent storage on a separate EBS volume — unaffected by application host replacement
- Easier backup management (`pg_dump` cron runs on the DB host itself)
- Straightforward migration path to RDS (config-only `SPRING_DATASOURCE_URL` change)

Recommended spec: `t3.large` (2 vCPU, 8 GB RAM), 200 GB `gp3` EBS, Amazon Linux 2023.

```bash
# Install TimescaleDB on the dedicated DB host
sudo dnf install -y postgresql16-server
# Follow TimescaleDB installation for AL2023
# Bind to private IP only in postgresql.conf:
# listen_addresses = '10.0.x.x'
```

Update `.env` on the application host:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://10.0.x.x:5432/pos_accounting_db
# Remove the postgres service from docker-compose.yml overrides
```

Security Group on the DB host: allow inbound `5432` from the application host's private IP only.

#### Kafka — Amazon MSK

The local Kafka container (used by `pos-workorder` and `pos-vehicle-inventory`) is replaced by Amazon MSK:

| Setting        | Value                                          |
| -------------- | ---------------------------------------------- |
| Broker type    | `kafka.t3.small` (2 brokers, Multi-AZ)         |
| Kafka version  | 3.5.x                                          |
| Authentication | SASL/SCRAM or IAM                              |
| Topics         | `workorder-events`, `vehicle-inventory-events` |

Update `.env`:

```bash
SPRING_KAFKA_BOOTSTRAP_SERVERS=b-1.durion.xxxxx.kafka.us-east-1.amazonaws.com:9092,b-2....
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true
MANAGEMENT_HEALTH_KAFKA_ENABLED=true
```

Remove the `kafka` and `zookeeper` services from the compose file. No application code changes.

#### Grafana — Grafana Cloud

The self-hosted Grafana + Prometheus containers are replaced by Grafana Cloud (free tier supports up to 10k series):

| Signal  | Pipeline                                                 |
| ------- | -------------------------------------------------------- |
| Metrics | OTEL Collector → Grafana Cloud Prometheus (remote_write) |
| Traces  | OTEL Collector → Grafana Cloud Tempo                     |
| Logs    | OTEL Collector → Grafana Cloud Loki                      |

Update `otel-collector-config.yml` exporters:

```yaml
exporters:
  prometheusremotewrite:
    endpoint: https://prometheus-prod-xx.grafana.net/api/prom/push
    auth:
      authenticator: basicauth/grafana
  otlp/tempo:
    endpoint: tempo-prod-xx.grafana.net:443
    auth:
      authenticator: basicauth/grafana
  loki:
    endpoint: https://logs-prod-xx.grafana.net/loki/api/v1/push
    auth:
      authenticator: basicauth/grafana

extensions:
  basicauth/grafana:
    client_auth:
      username: "${GRAFANA_INSTANCE_ID}"
      password: "${GRAFANA_API_KEY}"
```

Remove `prometheus`, `grafana`, and `jaeger` containers from the compose file. Add `GRAFANA_INSTANCE_ID` and `GRAFANA_API_KEY` to `.env`.

#### Ollama — Cloud or Dedicated GPU Host

`pos-mcp-server` requires an LLM inference endpoint. Two options:

**Option A — Ollama Cloud (ollama.com hosted)**

Ollama's hosted API is OpenAI-compatible. Update `pos-mcp-server` environment:

```bash
OLLAMA_BASE_URL=https://api.ollama.com
OLLAMA_API_KEY=${OLLAMA_API_KEY}
```

This requires a LangChain4j adapter change if the current integration uses the native Ollama HTTP API rather than the OpenAI-compatible endpoint.

**Option B — Dedicated EC2 GPU instance**

Run Ollama on a `g4dn.xlarge` (1× T4 GPU, 16 GB VRAM) in the same VPC:

```bash
# On the GPU host
curl -fsSL https://ollama.com/install.sh | sh
systemctl enable --now ollama
ollama pull llama3.1:8b
ollama pull nomic-embed-text
```

Bind Ollama to the private IP and update `.env` on the application host:

```bash
OLLAMA_BASE_URL=http://10.0.x.x:11434
```

Remove `ollama` and `ollama-init` from the compose file.

### Revised Application Host Sizing

With Postgres, Kafka, Grafana, and Ollama removed from the application host, the RAM footprint drops from ~28 GB to ~10 GB. The instance can be downsized:

| Before                       | After                       |
| ---------------------------- | --------------------------- |
| `t3.2xlarge` (8 vCPU, 32 GB) | `t3.xlarge` (4 vCPU, 16 GB) |
| ~$0.33/hr                    | ~$0.17/hr                   |

### Revised `.env` Additions

```bash
# Database (dedicated host)
SPRING_DATASOURCE_URL=jdbc:postgresql://10.0.x.x:5432/{db_name}

# Kafka (MSK)
SPRING_KAFKA_BOOTSTRAP_SERVERS=b-1.durion.xxxxx.kafka.us-east-1.amazonaws.com:9092

# Grafana Cloud
GRAFANA_INSTANCE_ID=123456
GRAFANA_API_KEY=glc_xxxx

# Ollama
OLLAMA_BASE_URL=http://10.0.x.x:11434
# or for Ollama Cloud:
# OLLAMA_BASE_URL=https://api.ollama.com
# OLLAMA_API_KEY=ollama_xxxx
```

### Services Removed from docker-compose.yml

```yaml
# Remove these services in docker-compose.prod.yml override:
# - postgres
# - ollama
# - ollama-init
# - prometheus
# - grafana
# - jaeger
# Keep:
# - otel-collector (now exports to Grafana Cloud instead of local Prometheus/Jaeger)
```

### Security Group Changes

| Host             | New inbound rule | Source                     |
| ---------------- | ---------------- | -------------------------- |
| DB EC2           | TCP 5432         | Application EC2 private IP |
| GPU EC2 (Ollama) | TCP 11434        | Application EC2 private IP |
| Application EC2  | No change        | —                          |

All inter-host traffic stays within the VPC private subnet — no internet exposure.
