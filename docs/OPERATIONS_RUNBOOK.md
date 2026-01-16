# Durion Platform Operations Runbook

## Overview

This runbook documents operational procedures that span the Durion platform:

- **durion-moqui-frontend** (Moqui Framework, Java 11, Groovy, Vue.js 3, Quasar, TypeScript)
- **durion-positivity-backend** (Spring Boot 3.x microservices, Java 21)

This runbook is **platform-level**. For repo-local details that may evolve faster, also consult:

- Workspace-level coordination: `durion/workspace-agents/docs/OperationsRunbook.md`
- Frontend-local: `durion-moqui-frontend/docs/OperationsRunbook.md`
- Backend-local: `durion-positivity-backend/docs/OperationsRunbook.md`

**Operational targets (backend services, typical):**

- Availability: 99.9% during business hours
- Typical response time: < 500 ms for core APIs under normal load
- RTO: 4 hours, RPO: 1 hour (aligned with workspace-level targets)

---

## How to Use This Runbook

Use this runbook when operating, deploying, or debugging issues that involve the frontend and backend together.

Suggested triage flow:

1. Confirm the frontend is serving traffic (`/webroot/`) and is not error-looping.
2. Confirm the backend gateway and dependent services are healthy (`/actuator/health`).
3. If the issue crosses repos (auth, networking, environment config, incident response), switch to the workspace-level runbook.

---

## 1. Build and Test

### 1.1 Frontend platform build (Moqui)

```bash
cd durion-moqui-frontend

# Fast development build (skip tests)
./gradlew build -x test

# Full build with tests
./gradlew build

# Run tests (as configured)
./gradlew test
```

### 1.2 Backend build (Positivity)

```bash
cd durion-positivity-backend

# Clean build with tests
./mvnw -e -U -DskipTests=false -DfailIfNoTests=false clean test

# Full package build
./mvnw -e -U -DskipTests=false -DfailIfNoTests=false clean package
```

### 1.3 Backend module-focused build

```bash
cd durion-positivity-backend

# Build only the agent framework
./mvnw -pl pos-agent-framework -am clean test

# Build and test a specific POS module (example: pos-order)
./mvnw -pl pos-order -am clean test
```

---

## 2. Run Locally

### 2.1 Frontend platform (Moqui) with Docker Compose

```bash
cd durion-moqui-frontend

# (Optional) Create a local env file (do NOT commit secrets)
cat > .env.local << EOF
DB_USER=moqui_user
DB_PASSWORD=dev_password
ADMIN_PASSWORD=admin_password
EOF
chmod 600 .env.local

# Start PostgreSQL + Moqui stack
docker-compose -f docker/moqui-postgres-compose.yml up -d

# Follow logs
docker-compose -f docker/moqui-postgres-compose.yml logs -f moqui

# Access application
# http://localhost:8080/webroot/
```

Stop services:

```bash
cd durion-moqui-frontend

docker-compose -f docker/moqui-postgres-compose.yml down
# or clean state
docker-compose -f docker/moqui-postgres-compose.yml down -v
```

### 2.2 Frontend platform (Moqui) without Docker

```bash
cd durion-moqui-frontend

# Build and create runtime
./gradlew build

# Start Moqui (embedded H2 for development)
java -jar runtime/build/libs/moqui.war

# Access at
# http://localhost:8080/webroot/
```

### 2.3 Backend services (Positivity)

In most workflows, run services behind the API gateway and verify health via Actuator.

Examples:

```bash
cd durion-positivity-backend

# Build artifacts
./mvnw -pl pos-api-gateway -am clean package

# Run a single service locally (example JAR run)
java -jar pos-api-gateway/target/pos-api-gateway-*.jar
```

---

## 3. Deployment

Deployment details (Kubernetes, ECS, etc.) are environment-specific. These are the generic steps.

### 3.1 Backend deployment artifacts

```bash
cd durion-positivity-backend

./mvnw -e -U -DskipTests=false -DfailIfNoTests=false clean package
```

Deploy using your environment’s manifests/pipelines (GitHub Actions, ArgoCD, Helm, ECS task definitions, etc.).

### 3.2 Frontend platform artifacts

```bash
cd durion-moqui-frontend

# Ensure a clean build
./gradlew clean build

# Resulting artifacts include the Moqui runtime and any packaged web assets.
```

### 3.3 Post-deploy health checks

Backend (per service):

```bash
curl -f http://<host>:<port>/actuator/health || echo "health check failed"

# Example (if routed by a gateway)
# curl -f https://api.example.com/pos-order/actuator/health
```

Frontend (Moqui):

```bash
curl -f http://<host>:<port>/webroot/ || echo "frontend health check failed"
```

---

## 4. Monitoring and Troubleshooting

### 4.1 Backend key signals

- HTTP error rate (4xx/5xx)
- Request latency (p95, p99)
- JVM metrics (heap, GC, threads)
- Database connection pool usage

Illustrative Prometheus-style alerts:

```yaml
# High error rate
alert: PosBackendHighErrorRate
expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
for: 5m
labels:
  severity: critical
annotations:
  summary: "High 5xx rate in durion-positivity-backend"

# High latency
alert: PosBackendHighLatency
expr: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) > 1
for: 5m
labels:
  severity: warning
annotations:
  summary: "High p95 latency in durion-positivity-backend"
```

### 4.2 Backend build failures

```bash
cd durion-positivity-backend
./mvnw -e -U clean test

# Narrow scope if only one module is failing
./mvnw -pl pos-order -am clean test
```

### 4.3 Backend service fails to start

Common causes:

- Missing environment variables (DB URLs, credentials, external service endpoints)
- Port conflicts
- Database connectivity issues

Checks:

```bash
# Containerized (example)
kubectl logs deployment/pos-order -c pos-order --tail=200

# Local
java -jar pos-order/target/pos-order-*.jar
```

### 4.4 Backend health endpoint failing

```bash
curl -v http://<host>:<port>/actuator/health
```

### 4.5 Frontend (Moqui) fails to start

Common causes:

- Database configuration issues (host, port, user, password)
- Missing required DB schemas

```bash
cd durion-moqui-frontend

docker-compose -f docker/moqui-postgres-compose.yml logs moqui
```

### 4.6 Frontend cannot reach backend APIs

- Confirm backend services are healthy and reachable (prefer gateway URLs)
- Verify the `durion-positivity` integration component configuration under `durion-moqui-frontend/runtime/component/durion-positivity/`
- Check CORS, gateway routing, or proxy configuration

For incidents that require cross-repo diagnosis, use the workspace-level runbook.

---

## 5. Agent Structure and Kiro Automation

Each repo has a Kiro task plan under `.kiro/specs/agent-structure/tasks.md`. The helper scripts apply exactly one unchecked task at a time.

Frontend:

```bash
cd durion-moqui-frontend
MAX_STEPS=1 ./kiro-run-agent-structure.zsh
```

Backend:

```bash
cd durion-positivity-backend
MAX_STEPS=1 ./kiro-run-agent-structure.zsh
```

---

## 6. When to Escalate to Workspace-Level Runbook

Use `durion/workspace-agents/docs/OperationsRunbook.md` when:

- Issues involve both frontend (Moqui/Vue) and backend services (gateway, auth, networking)
- Story orchestration outputs diverge from actual behavior
- You need coordinated SRE/DR actions across multiple repositories

Keep this runbook focused on platform-level procedures and treat the workspace-level runbook as the source of truth for incident coordination.
