# AGENTS.md — Durion Platform

## Project Overview

Durion is a multi-repo platform that includes:

- `durion-moqui-frontend`: Moqui Framework runtime + UI (Vue 3, Quasar, TypeScript 5)
- `durion-positivity-backend`: POS Spring Boot microservices (Java 21, Spring Boot 4.0.2)
- `durion`: workspace-level coordination, governance, project ,and agent docs

This file is a concise, agent-focused guide containing the commands and context agents need to work across frontend and backend.

---

## Quick Prerequisites

- Java 21+ (for Spring Boot services)
- Maven (or use `./mvnw`) for backend
- Java 11 (for Moqui runtime)
- Gradle (or `./gradlew`) for Moqui frontend runtime builds
- Node 18+ / npm (for frontend assets and tooling)
- Docker + Docker Compose (for local stacks and collector demos)

---

## Setup Commands

Clone and prepare repositories (from workspace root):

```bash
git clone git@github.com:louisburroughs/durion.git
cd durion
```

Frontend (Moqui) quick setup:

```bash
cd durion-moqui-frontend
npm install            # install UI/tooling deps
./gradlew build -x test # build runtime and assets
```

Backend (Positivity) quick setup:

```bash
cd durion-positivity-backend
./mvnw -pl pos-api-gateway -am clean package  # build gateway + deps
# Build a specific service:
./mvnw -pl pos-order -am clean package
```

Local stack (compose examples):

```bash
# From durion-moqui-frontend (see docker/ for supplied compose files)
docker-compose -f docker/moqui-postgres-compose.yml up -d
# Start observability demo (if present in docs/compose)
# docker-compose -f docs/observability-compose.yml up -d
```

---

## Development Workflow

- Frontend (Moqui): use `./gradlew` to build the runtime. The runtime `webroot/` serves UI bundles (check `runtime/build/libs/moqui.war`).
- UI assets and dev scripts: use `npm run dev` / `npm run build` where available in `durion-moqui-frontend`.
- Backend: use `./mvnw -pl <module> -am spring-boot:run` or run the packaged JAR with `java -jar` for a single service.

Examples:

```bash
# Run single Spring Boot service locally
cd durion-positivity-backend/pos-order
./mvnw spring-boot:run
# or
java -jar target/pos-order-*.jar
```

---

## Testing

- Backend (Positivity):

```bash
# Run all tests in the workspace-level backend
cd durion-positivity-backend
./mvnw -DskipTests=false clean test
# Module-only tests
./mvnw -pl pos-order -am test
```

- Frontend:

```bash
cd durion-moqui-frontend
npm test      # runs Jest or configured test runner
./gradlew test # Moqui/server-side tests if configured
```

---

## Build & Release

- Backend artifacts: `./mvnw -pl <module> -am clean package`
- Frontend/runtime: `./gradlew clean build`
- Ensure `SERVICE_VERSION`/`service.version` is set in CI builds for observability and release tagging.

---

## Observability (instrumentation & runbooks)

Primary, canonical architecture doc: `docs/architecture/observability/OBSERVABILITY.md`
SRE agent runbook: `.github/agents/sre.agent.md`

Guidance for agents:

- Use the OpenTelemetry Collector as the gateway for OTLP from services (gRPC: `otel-collector:4317`, HTTP: `:4318`).
- Frontend: capture Web Vitals, JS errors, network spans, attach `release`/`service.version` and upload sourcemaps for traceability.
- Backend: prefer OpenTelemetry Java agent for baseline, manual SDK instrumentation for business metrics. Document metrics in `METRICS.md` near each `pos-*` module.

Quick commands/examples:

```bash
# Example env for local OTLP endpoint
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
# Start a simple collector (if a local compose file exists)
docker-compose -f docs/observability-compose.yml up -d
```

---

## Code Style & Quality

- Moqui: follow existing Moqui conventions. Keep controllers thin; place logic in service classes.
- Spring Boot: use Micrometer/OpenTelemetry integration and standard Actuator endpoints (`/actuator/health`).
- TypeScript/Vue: ESLint + Prettier; follow Composition API or repo conventions.
- Run linters before committing:

```bash
# Frontend
cd durion-moqui-frontend
npm run lint
# Backend: rely on mvn formatter/lint steps if configured
cd durion-positivity-backend
./mvnw verify
```

---

## Documentation Hierarchy & README.md Files

**MANDATORY: Always check for and consult README.md files in your working directory before making changes.**

Documentation follows a strict hierarchy:

1. **Workspace-level**: `durion/AGENTS.md` (this file) — cross-cutting guidance
2. **Project-level**: `durion-positivity-backend/AGENTS.md`, `durion-moqui-frontend/AGENTS.md` — project-specific setup and patterns
3. **Module/Component-level**: `README.md` files in each module directory — module-specific documentation

### README.md Requirements

**Before working in any directory:**
- Check if a `README.md` exists in that directory
- Read and follow the guidance in that README.md
- Consult parent README.md files for context

**When making changes:**
- Update the local `README.md` when adding new features, changing APIs, or modifying module structure
- Document new configuration options, environment variables, or setup steps
- Add examples for new functionality
- Keep setup instructions current

**README.md locations to check:**
- Module root: `pos-accounting/README.md`, `pos-order/README.md`, etc.
- Component root: `runtime/component/durion-*/README.md`
- Feature directories: Any top-level directory may have a README.md

**If no README.md exists:**
- Create one when adding significant new functionality
- Include: purpose, setup, key files, common commands, testing instructions

---

## Agents & Where to Find Their Docs

Agent docs live under `.github/agents/` in this repo. Key agent docs and runbooks:

- `.github/agents/sre.agent.md` — SRE/Observability guidance (instrumentation, telemetry contract)
- `.github/agents/primary-software-engineer.agent.md` — Primary software engineer guidance
- `.github/agents/dev-deploy.agent.md` — Deployment/CI/CD guidance
- `.github/agents/api.agent.md` — REST API guidance
- `.github/agents/moqui-developer.agent.md` — Moqui-specific developer guidance
- Backend repo test agent: `durion-positivity-backend/.github/agents/test.agent.md`
- Frontend repo test agent: `durion-moqui-frontend/.github/agents/test.agent.md`

**Always consult local README.md files first**, then agent docs for cross-cutting concerns. When in doubt about observability, consult `.github/agents/sre.agent.md` and `docs/architecture/observability/OBSERVABILITY.md`.

---

## Pull Request & Commit Guidance

- Preface PR titles with the capability issue number: `cap/<cap-id>`
- Required checks: lint, unit tests, and any CI integration tests configured for the module.
- Document changes to observability (metrics/traces) in `METRICS.md` co-located with the component.

---

## Debugging & Troubleshooting Tips

- Frontend blank-screen: confirm `/webroot/` serves bundles, check browser console errors and CSP.
- Backend health: hit `http://<host>:<port>/actuator/health` and check logs for DB/connectivity errors.
- Observability gaps: ensure `SERVICE_VERSION` is set, check OTLP endpoint env vars, and confirm collector is reachable.

Logs & traces correlation:

- Ensure trace context (W3C) is propagated from browser → gateway → backend services.
- Upload sourcemaps and set frontend `release` tag to make JS stack traces actionable.

---

## Security Considerations

- Never commit secrets to repo. Use environment variables or secret stores for credentials/tokens.
- Avoid logging PII in telemetry attributes or logs. Follow `docs/` security policies and OWASP guidance.

---

## Where to Extend

- **Module/Component documentation**: Always create or update `README.md` in the module directory when adding new modules or features
- For monorepo-like per-component agent context, add `AGENTS.md` into subproject roots (e.g., `durion-positivity-backend/AGENTS.md`)
- **Keep README.md files current**: Update them when changing module structure, APIs, or configuration
- Update this file when new developer workflows or CI steps are added that affect multiple projects

**Documentation Priority Order:**
1. Check local `README.md` in your working directory
2. Check project-level `AGENTS.md` for project-specific patterns
3. Check workspace-level `AGENTS.md` (this file) for cross-cutting guidance
4. Check `.github/agents/*.agent.md` for specialized guidance

---
