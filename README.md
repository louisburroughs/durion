# Durion Workspace

This workspace contains the shared Durion platform documentation, the Positivity backend, and the TypeScript SDK used by Durion Angular frontend applications. It provides development guidance, agent documentation, and observability references for working across the platform.

## Repositories in This Workspace

- `durion` — workspace coordination, agent docs, ADRs, and governance
- `durion-positivity-backend` — POS microservices (Java 25, Spring Boot 4.0.x)
- `durion-positivity-sdk` — TypeScript SDK generated from backend OpenAPI contracts
- `durion-positivity-frontend` — Angular frontend application for POS user workflows

The frontend consumes backend APIs and SDK packages. When the frontend repository is checked out alongside this workspace, use its README as the source of truth for frontend setup and architecture.

## Quick Links

- Root agent guide: `AGENTS.md`
- Architecture Decision Records: `docs/adr/README.md`
- Observability architecture: `docs/architecture/observability/OBSERVABILITY.md`
- Agent docs: `.github/agents/`
- Frontend README: `../durion-positivity-frontend/README.md`

## Prerequisites

- Java 25+
- Node 18+ and npm
- Maven (or use `./mvnw`) for backend builds
- Docker & Docker Compose for local stacks where needed

## Quick Start

1. Clone the workspace:

```bash
git clone git@github.com:louisburroughs/durion.git
cd durion
```

1. Read the root agent guide for repo-specific instructions:

```bash
less AGENTS.md
```

1. Build the backend:

```bash
cd ../durion-positivity-backend
./mvnw -pl pos-api-gateway -am clean package
```

1. Build the TypeScript SDK:

```bash
cd ../durion-positivity-sdk
npm install
npm run build
```

1. For Angular frontend applications, use the frontend repository README and Angular CLI workflow:

```bash
cd ../durion-positivity-frontend
less README.md
```

## Testing

- Backend tests:

```bash
cd ../durion-positivity-backend
./mvnw -DskipTests=false clean test
```

- SDK tests:

```bash
cd ../durion-positivity-sdk
npm test
```

- Frontend tests:

```bash
cd ../durion-positivity-frontend
npm test
```

## Local Development

- Backend local stack and service-specific setup live in `../durion-positivity-backend/AGENTS.md`.
- Frontend local setup, routing, and architecture guidance live in `../durion-positivity-frontend/README.md`.
- The observability architecture and collector configuration live at `docs/architecture/observability/OBSERVABILITY.md`.

## Contributing

- Follow the guidance in `AGENTS.md` and each repository's local instructions.
- Preface PR titles with the affected area, for example `[frontend]`, `[sdk]`, `[pos-order]`, or `[infra]`.
- Ensure relevant linting and tests pass before opening a PR.

## Further Reading

- Workspace agent guide: `AGENTS.md`
- Backend agent guide: `../durion-positivity-backend/AGENTS.md`
- SDK overview: `../durion-positivity-sdk/README.md`
- Frontend overview: `../durion-positivity-frontend/README.md`
- ADR index: `docs/adr/README.md`
