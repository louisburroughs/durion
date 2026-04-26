---
title: Durion Positivity — Podman on EC2 Architecture
status: reference
environment: alpha / prototype
last-updated: 2026-04-14
scope: Rootless Podman + Podman Compose deployment on AWS EC2
---

# Durion Positivity — Podman on EC2 Architecture

## Overview

This document describes the architecture for running the Durion Positivity POS platform using rootless Podman and Podman Compose on a single AWS EC2 instance. The topology is
identical to the Docker on EC2 model but replaces the Docker daemon with Podman's daemonless, rootless container runtime — improving the security posture without changing the
application contract.

---

## Why Podman Instead of Docker

| Concern             | Docker                                       | Podman                                     |
| ------------------- | -------------------------------------------- | ------------------------------------------ |
| Daemon              | Requires root-owned `dockerd`                | Daemonless — no persistent root process    |
| Rootless containers | Supported but non-default                    | Native default                             |
| CVE surface         | Docker daemon is a privileged attack surface | No daemon to exploit                       |
| Systemd integration | Requires workarounds                         | Native `podman generate systemd`           |
| OCI compliance      | OCI-compatible                               | OCI-native                                 |
| Compose support     | `docker compose` plugin                      | `podman-compose` or `podman compose` (v4+) |
| ECR authentication  | `docker login`                               | `podman login` (identical flow)            |

For a single-host alpha deployment, Podman reduces the blast radius of a container escape by eliminating the root daemon. The application images, environment variables, and
compose file structure are unchanged.

---

## Architecture Diagram

```
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
Podman rootless network: pos-network (pasta/slirp4netns)
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

## Host Setup Differences from Docker

### Install Podman on Amazon Linux 2023

```bash
sudo dnf install -y podman podman-compose
sudo dnf install -y nginx certbot python3-certbot-nginx

# Enable lingering so rootless containers survive logout
sudo loginctl enable-linger ec2-user
```

### ECR Authentication

```bash
aws ecr get-login-password --region us-east-1 \
  | podman login --username AWS --password-stdin \
    288757602241.dkr.ecr.us-east-1.amazonaws.com
```

### Running the Stack

`podman-compose` reads the same `docker-compose.yml` and `docker-compose.prod.yml` files without modification:

```bash
podman-compose \
  -f /opt/durion/alpha/backend/docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  up -d
```

### Systemd Auto-Restart (Rootless)

Generate systemd units for automatic restart on reboot:

```bash
podman generate systemd --new --name --files \
  --pod-prefix "" --separator "-" \
  pos-api-gateway

mkdir -p ~/.config/systemd/user/
mv *.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable container-pos-api-gateway.service
```

Alternatively, use a single systemd unit that calls `podman-compose up`:

```ini
# ~/.config/systemd/user/durion-stack.service
[Unit]
Description=Durion Positivity Stack
After=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/bin/podman-compose \
  -f /opt/durion/alpha/backend/docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  up -d
ExecStop=/usr/bin/podman-compose \
  -f /opt/durion/alpha/backend/docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  down

[Install]
WantedBy=default.target
```

---

## Rootless Networking Considerations

Podman rootless containers use `pasta` (default on AL2023 with Podman 4+) or `slirp4netns` for network namespacing. Key differences from Docker bridge networking:

- Container-to-container communication within the same Compose project works identically via service name DNS
- Port binding below 1024 requires `net.ipv4.ip_unprivileged_port_start=80` sysctl or use of ports ≥ 1024 with Nginx handling 80/443 on the host (already the case in this
  architecture)
- The `pos-network` bridge is created in the user network namespace — no root required

```bash
# Allow rootless binding to ports 80+ if needed
echo "net.ipv4.ip_unprivileged_port_start=80" \
  | sudo tee /etc/sysctl.d/99-podman-ports.conf
sudo sysctl --system
```

---

## CI/CD Deploy Script Changes

The `deploy-backend.sh` on the instance replaces `docker` with `podman` and `docker compose` with `podman-compose`:

```bash
# ECR login
aws ecr get-login-password --region us-east-1 \
  | podman login --username AWS --password-stdin "${ECR_REGISTRY}"

# Pull images
podman-compose "${COMPOSE_ARGS[@]}" pull --quiet "${BACKEND_SERVICES[@]}"

# Restart services
podman-compose "${COMPOSE_ARGS[@]}" up -d --force-recreate "${BACKEND_SERVICES[@]}"
```

The GitHub Actions SSM `send-command` step in `build-push-ecr.yml` is unchanged — it still calls `deploy-backend.sh` on the instance.

---

## Observability

Identical to the Docker on EC2 model. All services emit OTLP telemetry to the OTEL Collector. Prometheus scrapes metrics. Grafana provides dashboards. Jaeger stores traces. No
changes to `otel-collector-config.yml` or `prometheus.yml`.

---

## Backup and Recovery

Identical to the Docker on EC2 model. Daily `pg_dump` cron uploads to S3. 14-day retention. Manual restore procedure.

The `pg_dump` cron runs as `ec2-user` using `podman exec`:

```bash
0 2 * * * podman exec postgres-positivity \
  pg_dumpall -U positivity | gzip \
  | aws s3 cp - s3://durion-alpha-deploy/backups/$(date +\%Y-\%m-\%d).sql.gz
```

---

## Comparison with Docker on EC2

| Aspect           | Docker on EC2                        | Podman on EC2                                   |
| ---------------- | ------------------------------------ | ----------------------------------------------- |
| Runtime          | `dockerd` (root daemon)              | Daemonless (rootless)                           |
| Compose file     | Unchanged                            | Unchanged                                       |
| Image format     | OCI / Docker                         | OCI (identical)                                 |
| ECR auth         | `docker login`                       | `podman login`                                  |
| Auto-restart     | `restart: unless-stopped` in compose | systemd user units                              |
| Port binding     | Docker handles                       | Requires `ip_unprivileged_port_start` for <1024 |
| Security posture | Root daemon present                  | No root daemon                                  |
| AWS SSM deploy   | `docker compose` commands            | `podman-compose` commands                       |

---

## Limitations and Migration Path

The same limitations as Docker on EC2 apply — single host, no HA, manual scaling. The migration path to ECS/Fargate is identical: application images are OCI-compliant and
portable. Podman-built images push to ECR with `podman push` and are consumed by ECS tasks without modification.

---

## Extension — Managed Cloud Services Variant

This extension mirrors the Docker on EC2 managed services variant exactly, with Podman-specific command substitutions noted where they differ. Ollama, Kafka, Grafana, and
PostgreSQL move off the application host to dedicated managed or cloud-hosted services.

### Updated Architecture Diagram

```
Internet
    │
    ▼
Route 53 (durionpos.org)
    │
    ▼
Elastic IP → EC2 t3.xlarge (rootless Podman — application containers only)
    │
    ▼
Nginx (TLS termination — Let's Encrypt)
  ├── :443 /          → localhost:4200
  └── :443 /api       → localhost:8080
    │
    ▼
Podman rootless network: pos-network
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
External Managed Services (VPC private subnet or VPN)
  ├── Dedicated PostgreSQL EC2 (TimescaleDB, private IP)
  ├── Amazon MSK (Managed Kafka)
  ├── Grafana Cloud (metrics, traces, logs via OTLP)
  └── Ollama Cloud or dedicated GPU EC2 (g4dn.xlarge)
```

### Managed Service Configuration

All four managed service replacements are identical to the Docker on EC2 extension. Refer to that document for full configuration details. The only Podman-specific differences
are:

#### Backup — pg_dump from Dedicated DB Host

With Postgres on a separate host, the `pg_dump` cron moves to the DB host itself rather than using `podman exec`:

```bash
# On the dedicated DB EC2 host
0 2 * * * pg_dumpall -U positivity | gzip \
  | aws s3 cp - s3://durion-alpha-deploy/backups/$(date +\%Y-\%m-\%d).sql.gz
```

#### Deploy Script — Podman Commands

The `deploy-backend.sh` on the application host uses `podman login` and `podman-compose` as described in the main Podman section. The managed service endpoints are injected
via `.env` — no script logic changes.

```bash
# ECR login (Podman)
aws ecr get-login-password --region us-east-1 \
  | podman login --username AWS --password-stdin "${ECR_REGISTRY}"

podman-compose "${COMPOSE_ARGS[@]}" pull --quiet "${BACKEND_SERVICES[@]}"
podman-compose "${COMPOSE_ARGS[@]}" up -d --force-recreate "${BACKEND_SERVICES[@]}"
```

#### Systemd Unit — No Postgres Dependency

With Postgres off-host, remove the `postgres` health check dependency from the systemd `After=` directive. The application services connect to the external DB host directly on
startup.

### Revised Application Host Sizing

Identical to the Docker variant — downsizing from `t3.2xlarge` to `t3.xlarge` is appropriate once Postgres, Ollama, Grafana, and Kafka are removed.

### Services Removed from Compose File

```yaml
# Remove in docker-compose.prod.yml override (same as Docker variant):
# - postgres
# - ollama
# - ollama-init
# - prometheus
# - grafana
# - jaeger
# Keep:
# - otel-collector (exports to Grafana Cloud)
```

### Podman vs Docker — Managed Services Variant Comparison

| Aspect           | Docker + Managed Services | Podman + Managed Services      |
| ---------------- | ------------------------- | ------------------------------ |
| Runtime          | `dockerd` (root)          | Daemonless (rootless)          |
| Compose changes  | Remove 6 services         | Identical — same compose files |
| ECR auth         | `docker login`            | `podman login`                 |
| pg_dump          | Runs on DB host           | Runs on DB host                |
| Deploy script    | `docker compose`          | `podman-compose`               |
| Security posture | Root daemon present       | No root daemon                 |
