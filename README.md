# Durion Platform Workspace

![Workspace](https://img.shields.io/badge/workspace-durion-0366d6)
![Docs](https://img.shields.io/badge/docs-governance%20%2B%20adr-0a7ea4)
![Default Branch](https://img.shields.io/badge/branch-master-brightgreen)

## Overview

Durion is a multi-repository platform for the Positivity POS system. This
repository is the workspace hub for governance, architecture decision records,
cross-repo standards, and shared domain documentation.

## Tech Stack

- Markdown documentation and architecture artifacts
- Workspace automation scripts
- Agent and instruction configuration under `.github/`

## Prerequisites

- Java 25 (SDKMAN recommended)
- Node.js 22 LTS and npm 11+
- Docker with Compose v2
- GitHub CLI (`gh`) for issue and PR workflows

## Quick Start

```bash
# 1) Start shared local infrastructure
cd ../durion-positivity-backend
docker compose up -d

# 2) Build backend modules
./mvnw clean package

# 3) Generate and build Angular SDK
cd ../durion-positivity-sdk-angular
npm install
npm run generate
npm run build

# 4) Start frontend
cd ../durion-positivity-frontend
npm install
npm start
```

## Common Commands

```bash
# Backend validation
cd ../durion-positivity-backend
./mvnw clean test

# Frontend validation
cd ../durion-positivity-frontend
npm run lint
npx ng test --no-watch
npm run a11y:smoke

# Angular SDK validation
cd ../durion-positivity-sdk-angular
npm test
npm run lint
npm run build
```

## Repository Layout

```text
~/IdeaProjects/
├── durion/                           # this repo (docs, governance, ADRs)
├── durion-positivity-backend/        # Java microservices (Spring Boot)
├── durion-positivity-frontend/       # Angular SPA + SSR
├── durion-positivity-sdk-angular/    # Angular SDK generated from OpenAPI
└── durion-positivity-sdk-java/       # Java SDK modules
```

Primary content in this repository:

- `docs/adr/`: architecture decision records
- `docs/architecture/`: architecture blueprints and observability docs
- `docs/capabilities/`: capability documentation and planning artifacts
- `domains/`: shared domain language and boundaries
- `.github/instructions/`: coding and workflow instruction files
- `.github/agents/`: agent definitions and specialization metadata
- `AGENTS.md`: cross-repo implementation and policy hierarchy

## Standards and Workflow

Documentation and implementation hierarchy:

1. `AGENTS.md` in this repository
2. Project-level `AGENTS.md` in the target repository
3. Nearest local `README.md`
4. Applicable ADRs in `docs/adr/`

For API or contract changes, use this order:

1. Update backend code and OpenAPI specification.
2. Regenerate SDK clients.
3. Update frontend integration.
4. Run validations in each affected repository.
5. Update nearby README and ADR references as needed.

## References

- `docs/ONBOARDING.md`
- `docs/adr/`
- `docs/architecture/observability/OBSERVABILITY.md`
- `AGENTS.md`
