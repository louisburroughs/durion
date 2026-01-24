# Durion Workspace

This workspace contains the Durion platform: a Moqui-based frontend/runtime and a Spring Boot POS backend. It provides agent-run documentation, development tooling, and observability guidance to help engineers and automation agents work across repositories.

Repositories in this workspace

- `durion-moqui-frontend` — Moqui runtime + UI (Vue 3, Quasar, TypeScript 5)
- `durion-positivity-backend` — POS microservices (Java 21, Spring Boot 3.x)
- `durion` — workspace coordination, agent docs, and governance (this repo)

Quick links

- Root agent guide: `AGENTS.md`
- Architecture Decision Records: `docs/adr/README.md`
- Observability architecture: `docs/architecture/observability/OBSERVABILITY.md`
- SRE runbook: `.github/agents/sre.agent.md`

Prerequisites

- Java 21+
- Node 18+ and npm
- Maven (or use `./mvnw`) for backend
- Gradle (or use `./gradlew`) for Moqui runtime builds
- Docker & Docker Compose (for local stacks)

Quick start

1. Clone the workspace:

```bash
git clone git@github.com:louisburroughs/durion.git
cd durion
```

2. Read the root agent guide for repo-specific instructions:

```bash
less AGENTS.md
```

3. Frontend quick build (Moqui):

```bash
cd durion-moqui-frontend
npm install
./gradlew build -x test
```

4. Backend quick build (Positivity):

```bash
cd durion-positivity-backend
./mvnw -pl pos-api-gateway -am clean package
```

Local compose examples

```bash
# Start Moqui + Postgres local stack
cd durion-moqui-frontend
docker-compose -f docker/moqui-postgres-compose.yml up -d

# (If present) start observability demo
docker-compose -f docs/observability-compose.yml up -d
```

Testing

- Backend tests:

```bash
cd durion-positivity-backend
./mvnw -DskipTests=false clean test
```

- Frontend tests:

```bash
cd durion-moqui-frontend
npm test
```

Observability

- The canonical observability architecture and collector configuration live at `docs/architecture/observability/OBSERVABILITY.md`.
- See `.github/agents/sre.agent.md` for telemetry contract, SLIs, and runbooks.

Contributing

- Follow the `AGENTS.md` guidance and the per-repo `AGENTS.md` files.
- Preface PR titles with the affected area, e.g. `[moqui]`, `[pos-order]`, `[infra]`.
- Ensure lint and tests pass before opening a PR.

Further reading & tools

- Workspace AGENTS.md: `AGENTS.md`
- Per-repo AGENTS.md: `durion-moqui-frontend/AGENTS.md`, `durion-positivity-backend/AGENTS.md`
- Agent docs: `.github/agents/`
- CRM permissions and Moqui service wrappers: see [Durion-CRM-PermissionImplementation.md](Durion-CRM-PermissionImplementation.md) and [CrmRestServices.xml](durion-moqui-frontend/runtime/component/durion-positivity/service/CrmRestServices.xml) for required permissions on CRM endpoints.

If you'd like, I can run the main build/test commands to validate the instructions or create CI snippets for common workflows.
<parameter name="filePath">/home/n541342/IdeaProjects/durion/README.md