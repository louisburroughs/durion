# Durion Workspace

This workspace contains the shared Durion platform documentation, the Positivity backend, and the TypeScript SDK used by Durion Angular frontend applications. It provides development guidance, agent documentation, and observability references for working across the platform.

## Repositories in This Workspace

- `durion` — workspace coordination, agent docs, ADRs, and governance
- `durion-positivity-backend` — POS microservices (Java 21, Spring Boot 4.0.x)
- `durion-positivity-sdk` — TypeScript SDK generated from backend OpenAPI contracts

Angular frontend applications consume the backend APIs and SDK, but are not part of this workspace checkout.

## Quick Links

- Root agent guide: `AGENTS.md`
- Architecture Decision Records: `docs/adr/README.md`
- Observability architecture: `docs/architecture/observability/OBSERVABILITY.md`
- Agent docs: `.github/agents/`

## Prerequisites

- Java 21+
- Node 18+ and npm
- Maven (or use `./mvnw`) for backend builds
- Docker & Docker Compose for local stacks where needed

## Quick Start

1. Clone the workspace:

```bash
git clone git@github.com:louisburroughs/durion.git
cd durion
```

2. Read the root agent guide for repo-specific instructions:

```bash
less AGENTS.md
```

3. Build the backend:

```bash
cd ../durion-positivity-backend
./mvnw -pl pos-api-gateway -am clean package
```

4. Build the TypeScript SDK:

```bash
cd ../durion-positivity-sdk
npm install
npm run build
```

5. For Angular frontend applications, use the frontend repository's own README and Angular CLI workflow.

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

## Local Development

- Backend local stack and service-specific setup live in `../durion-positivity-backend/AGENTS.md`.
- The observability architecture and collector configuration live at `docs/architecture/observability/OBSERVABILITY.md`.

## Contributing

- Follow the guidance in `AGENTS.md` and each repository's local instructions.
- Preface PR titles with the affected area, for example `[frontend]`, `[sdk]`, `[pos-order]`, or `[infra]`.
- Ensure relevant linting and tests pass before opening a PR.

## Further Reading

- Workspace agent guide: `AGENTS.md`
- Backend agent guide: `../durion-positivity-backend/AGENTS.md`
- SDK overview: `../durion-positivity-sdk/README.md`
- ADR index: `docs/adr/README.md`
