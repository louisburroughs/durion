# Durion Positivity Platform

Durion Positivity is a Point-of-Sale platform built on a Java 25 microservices backend, an Angular-native TypeScript SDK generated from OpenAPI contracts, a framework-agnostic TypeScript SDK for non-Angular consumers, and an Angular 21 frontend SPA. This repository is the workspace root: it holds cross-repo governance, Architecture Decision Records, agent specifications, domain models, and observability configuration.

---

## Table of Contents

- [Workspace Layout](#workspace-layout)
- [Architecture Overview](#architecture-overview)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [What Lives in This Repo](#what-lives-in-this-repo)
- [Testing](#testing)
- [Development Workflow](#development-workflow)
- [Contributing](#contributing)
- [Further Reading](#further-reading)

---

## Workspace Layout

All relevant repositories should be cloned into the same parent directory and treated as siblings.

| Repository | Role | Stack |
|---|---|---|
| `durion` *(this repo)* | Governance, ADRs, agent specs, domain models, observability | Markdown, Python scripts |
| `durion-positivity-backend` | POS microservices, API Gateway, OpenAPI specs | Java 25, Spring Boot 4.0.5, Maven, PostgreSQL |
| `durion-positivity-sdk-angular` | Angular-native SDK used by the frontend for all backend API calls | TypeScript 5.4+, Angular 21, OpenAPI Generator |
| `durion-positivity-sdk` | Framework-agnostic typed client SDK for non-Angular consumers | TypeScript 5.4+, OpenAPI Generator, npm workspaces |
| `durion-positivity-frontend` | POS single-page application with SSR | Angular 21.1.0, TypeScript 5.9.2, Express 5 |

```
~/IdeaProjects/
├── durion/                     ← you are here
├── durion-positivity-backend/
├── durion-positivity-sdk-angular/
├── durion-positivity-sdk/
└── durion-positivity-frontend/
```

---

## Architecture Overview

```
Angular 21 SPA (SSR)
        │
        │  injects @durion-sdk/{domain} Angular services
        ▼
Angular SDK (OpenAPI-generated packages)
        │
        │  generated from OpenAPI specs via OpenAPI Generator v7.5.0
        ▼
Spring Boot 4 Microservices
  ├── pos-api-gateway          ← single entry point for all HTTP traffic
  ├── pos-security-service     ← auth, JWT, roles, permissions
  ├── pos-order                ← sales orders, price overrides
  ├── pos-inventory            ← stock movements, reservations
  ├── pos-workorder            ← work orders, estimates
  ├── pos-accounting           ← GL, journal entries, Stripe payments
  ├── pos-customer             ← CRM: accounts, contacts, vehicles
  ├── pos-catalog              ← product catalog
  └── ... (20+ services total)
        │
        │  structured logs + traces + metrics
        ▼
Observability Stack (Docker Compose)
  ├── OpenTelemetry Collector  ← centralises all telemetry
  ├── Jaeger                   ← distributed tracing
  ├── Prometheus               ← metrics
  └── Grafana                  ← dashboards
```

`durion-positivity-sdk-angular` sits between the Angular frontend and the backend. All frontend-to-backend API calls must use the Angular SDK packages rather than direct URL construction in frontend feature code. When a backend OpenAPI spec changes, regenerate and rebuild `durion-positivity-sdk-angular` first so the frontend consumes the updated contract through generated clients.

---

## Prerequisites

| Tool | Minimum version | Notes |
|---|---|---|
| Java | 25 | Use `.sdkmanrc` with SDKMAN — `sdk env install` |
| Maven | bundled | Use `./mvnw` wrapper from the backend repo |
| Node.js | 18 | Built-in Fetch API; polyfill required on 14–17 |
| npm | 8+ | Workspaces support required |
| Docker & Docker Compose | any recent | Required for observability stack and local Kafka |

---

## Getting Started

### 1. Clone all repositories

```bash
cd ~/IdeaProjects
git clone git@github.com:louisburroughs/durion.git
git clone git@github.com:louisburroughs/durion-positivity-backend.git
git clone git@github.com:louisburroughs/durion-positivity-sdk-angular.git
git clone git@github.com:louisburroughs/durion-positivity-sdk.git
git clone git@github.com:louisburroughs/durion-positivity-frontend.git
```

### 2. Start the observability stack

The Docker Compose file in the backend repo brings up PostgreSQL, OTEL Collector, Jaeger, Prometheus, and Grafana:

```bash
cd durion-positivity-backend
docker compose up -d
```

### 3. Build and run the backend

```bash
# Build all modules and run tests
./mvnw clean package

# Build only the API Gateway and its upstream dependencies (faster)
./mvnw -pl pos-api-gateway -am clean package

# Start the gateway
./mvnw -pl pos-api-gateway spring-boot:run
```

### 4. Build the Angular SDK used by the frontend

The Angular SDK reads OpenAPI specs from the sibling `durion-positivity-backend/` directory:

```bash
cd ../durion-positivity-sdk-angular
npm install
npm run generate     # regenerate clients from current OpenAPI specs
npm run build        # compile CJS + ESM + type declarations
```

For non-Angular consumers, the fetch-based `durion-positivity-sdk` can be built separately.

### 5. Run the frontend

```bash
cd ../durion-positivity-frontend
npm install
npm start            # dev server at http://localhost:4200
```

See [durion-positivity-frontend/README.md](../durion-positivity-frontend/README.md) for SSR, environment configuration, and feature module details.

---

## What Lives in This Repo

```
durion/
├── docs/
│   ├── adr/                    ← Architecture Decision Records (numbered 0001-NNNN)
│   ├── architecture/
│   │   └── observability/      ← OpenTelemetry architecture and collector config
│   ├── capabilities/           ← Capability specifications
│   ├── design/                 ← Design documentation
│   ├── journeys/               ← User journey documentation
│   ├── stories/                ← Story specifications and epics
│   ├── ONBOARDING.md           ← Start here for a full platform introduction
│   └── EXEMPLARS.md            ← Reference implementations and patterns
├── domains/                    ← Domain-driven design bounded contexts
│   ├── accounting/
│   ├── crm/
│   ├── inventory/
│   ├── order/
│   ├── security/
│   └── ... (15 domains total)
├── .github/
│   ├── agents/                 ← Agent specifications (20+ types)
│   ├── instructions/           ← Agent instructions and prompts
│   └── pull_request_template.md
├── scripts/                    ← Python utilities for story/capability management,
│                                  issue labelling, contract guide generation
├── agents/                     ← Agent definitions and configurations
├── AGENTS.md                   ← Cross-repo agent guidance — read before using AI workflows
└── README.md                   ← this file
```

### Architecture Decision Records

ADRs live in `docs/adr/` and are numbered sequentially. They document enforceable architectural choices across all repositories — check them before introducing new patterns or dependencies.

### Domain Models

`domains/` contains the bounded-context definitions for all 15 business domains. These are the canonical source of truth for domain terminology used across the backend, SDK, and frontend.

### Agent Specifications

`.github/agents/` defines the AI agent roles used across development workflows (lead coder, PR reviewer, API planner, test coverage, TypeScript specialist, and more). `AGENTS.md` at the root is mandatory reading before running any agent-assisted workflow.

### Observability

`docs/architecture/observability/OBSERVABILITY.md` describes the full telemetry pipeline: how services emit traces, metrics, and logs; how the OTEL Collector routes them; and how to read them in Jaeger and Grafana.

---

## Testing

### Backend

```bash
cd ../durion-positivity-backend

# Full test suite
./mvnw clean test

# Single module
./mvnw -pl pos-order clean test

# With coverage report
./mvnw clean verify
```

### SDK

```bash
cd ../durion-positivity-sdk

# Full suite — 392 tests across 15 files
npm test

# Single test file
npx jest src/__tests__/sdk-003-transport.test.ts

# With coverage (80% threshold enforced)
npm test -- --coverage
```

### Frontend

```bash
cd ../durion-positivity-frontend

# Unit tests (Vitest + jsdom)
npm test

# Accessibility audit (axe-core)
npm run test:a11y
```

---

## Development Workflow

### Backend → SDK → Frontend change

When a backend API changes:

1. Update or add the endpoint in the relevant Spring Boot service.
2. Confirm the OpenAPI spec (`openapi.yaml` in that service module) reflects the change.
3. From `durion-positivity-sdk`: run `npm run generate` to regenerate the affected `@durion-sdk/*` package.
4. Compile the SDK (`npm run build`) and resolve any type errors in the frontend.
5. Open PRs in both `durion-positivity-backend` and `durion-positivity-sdk` — link them.

### SDK-only change (workflow helpers or transport)

Hand-written code lives in `packages/sdk-{domain}/src/workflows/` and `packages/sdk-transport/src/`. Generated files (`src/apis/`, `src/models/`, `src/runtime.ts`) must never be edited directly.

### ADR compliance

New architectural patterns — database choices, cross-service communication, auth mechanisms, dependency upgrades — require an ADR in `docs/adr/` before implementation.

---

## Contributing

- **PR title prefix** — Preface with the affected area: `[backend]`, `[sdk]`, `[frontend]`, `[infra]`, `[docs]`.
- **Agent guidance** — Read `AGENTS.md` before using any AI-assisted workflow.
- **Linting**
  - Backend: `./mvnw spotless:check` (Palantir Java Format) and `./mvnw checkstyle:check`
  - SDK: `npm run lint` (ESLint + TypeScript strict)
  - Frontend: `npm run lint` (ESLint + Angular ESLint)
- **Tests required** — All changed code must maintain existing coverage thresholds. SDK enforces 80% across branches, functions, lines, and statements.
- **Generated code** — Never commit hand-edits to `src/apis/`, `src/models/`, or `runtime.ts` in the SDK. Regenerate instead.

---

## Further Reading

| Resource | Location |
|---|---|
| Platform onboarding | `docs/ONBOARDING.md` |
| ADR index | `docs/adr/README.md` |
| Observability architecture | `docs/architecture/observability/OBSERVABILITY.md` |
| Agent guide (cross-repo) | `AGENTS.md` |
| Backend setup and agent guide | `../durion-positivity-backend/AGENTS.md` |
| Backend README | `../durion-positivity-backend/README.md` |
| SDK README | `../durion-positivity-sdk/README.md` |
| Frontend README | `../durion-positivity-frontend/README.md` |
