# Durion Platform Workspace

Durion is a multi-repository platform for the Positivity POS system. This repository is the workspace hub for governance, ADRs, architecture docs, domain language, and agent/workflow guidance.

## Repositories In Scope

Keep these repositories as sibling folders:

```text
~/IdeaProjects/
├── durion/                           # this repo (docs, governance, ADRs)
├── durion-positivity-backend/        # Java microservices (Spring Boot)
├── durion-positivity-frontend/       # Angular SPA + SSR
├── durion-positivity-sdk-angular/    # Angular SDK generated from OpenAPI
└── durion-positivity-sdk-java/       # Java SDK modules
```

## What This Repository Contains

- `docs/adr/`: accepted and proposed architecture decisions
- `docs/architecture/`: platform architecture documentation
- `docs/capabilities/`: capability definitions and planning artifacts
- `domains/`: shared domain vocabulary and boundaries
- `.github/instructions/`: Copilot/agent coding and documentation rules
- `.github/agents/`: agent configuration and specialization metadata
- `scripts/`: workspace automation scripts
- `AGENTS.md`: cross-repo implementation rules and decision hierarchy

## Documentation Hierarchy

Use this order when implementing changes:

1. `durion/AGENTS.md`
2. Project-level `AGENTS.md` in the target repository
3. Nearest local `README.md`
4. Relevant ADRs in `durion/docs/adr/`

If guidance conflicts, use the closest-scope document.

## Prerequisites

- Java 25 (SDKMAN recommended)
- Node.js 22 LTS and npm 11+
- Docker with Compose v2
- GitHub CLI (`gh`) for issue/PR workflows

## Quick Start

1. Start shared backend infrastructure:

```bash
cd ../durion-positivity-backend
docker compose up -d
```

2. Build backend modules:

```bash
./mvnw clean package
```

3. Generate/build Angular SDK:

```bash
cd ../durion-positivity-sdk-angular
npm install
npm run generate
npm run build
```

4. Start frontend:

```bash
cd ../durion-positivity-frontend
npm install
npm start
```

## Recommended Change Flow

For API or contract changes:

1. Update backend code and OpenAPI spec in the relevant backend module.
2. Regenerate Angular SDK (`durion-positivity-sdk-angular`).
3. Update frontend usage.
4. Run tests in each affected repo.
5. Update the nearest README and ADR references when behavior changes.

## Validation Commands

Backend:

```bash
cd ../durion-positivity-backend
./mvnw clean test
```

Frontend:

```bash
cd ../durion-positivity-frontend
npm run lint
npx ng test --no-watch
npm run a11y:smoke
```

Angular SDK:

```bash
cd ../durion-positivity-sdk-angular
npm test
npm run lint
npm run build
```

## Further Reading

- Onboarding: `docs/ONBOARDING.md`
- ADRs: `docs/adr/`
- Observability architecture: `docs/architecture/observability/OBSERVABILITY.md`
- Platform policies: `AGENTS.md`
