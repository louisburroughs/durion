## GitHub Copilot Instructions – Durion Platform (Frontend + POS Backend)

These instructions guide AI coding agents working across the Durion platform repositories in this workspace.

## Common Context

### Big Picture

- The Durion platform is split into:
  - **Frontend Platform (Moqui)**: Moqui Framework application with business components under `runtime/component/` and framework core under `framework/`. Project and repository name is `durion-moqui-frontend`.
  - **POS Backend (Spring Boot)**: Java 21 / Spring Boot microservice suite under `pos-*` directories. Project and repository name is `durion-positivity-backend`.
- The two codebases interact via REST APIs; the Moqui frontend consumes services exposed by the POS backend.
- Both codebases follow **durion/domains/{domain}/.business-rules/*.md** for shared business logic and rules expressed in natural language.:
  - Keep changes scoped to the owning component/service.
  - Mirror patterns already present in the target module or component.
- Prefer existing architecture decisions and guidance:
  - See `durion/docs/` for architecture/governance ADRs, design and governance docs and overall project docs.
  - See `durion/domains/{domain}/.business-rules/` for domain specific business and configuration rules.
  - POS backend: `durion-positivity-backend/docs/` and module level `docs/`.
  - Moqui frontend: `durion-moqui-frontend/docs/` and component level `docs/`.

### Security (Applies Everywhere)

- Never hardcode secrets (tokens, passwords, API keys). Use environment variables or secret stores.
- Enforce least privilege and deny-by-default patterns.
- Keep authentication/authorization consistent with existing project patterns:
  - POS backend: typically enforced at API gateway/service boundaries; align with existing security modules.
  - Moqui frontend: leverage Moqui security patterns and avoid ad-hoc bypasses.

### Documentation & Consistency

- When adding/changing externally visible behavior (API routes, events, service names, configs), update the closest relevant README/doc.
- Keep naming and behavior consistent with the domain vocabulary and existing contracts.

### **⚠️ Naming Conventions – MANDATORY**

- **`workorder` MUST be written as ONE WORD** (not "work order" or "Work Order").
  - Use in all contexts: code identifiers, comments, documentation, API descriptions, logs.
  - Examples: `workorder`, `Workorder`, `WORKORDER`, `workorderId`, `workorderStatus`.
  - Fix existing instances of "work order" / "Work Order" / "WorkOrder" / "workOrder" (two words or camel case) whenever you encounter them in code or comments.
  - This is a **strongly enforced convention** across all projects in this workspace.

## POS Backend – durion-positivity-backend

### Repository Summary

- The backend is a **Java 21 / Spring Boot microservice suite** under `pos-*` directories (for example `pos-accounting/`, `pos-agent-framework/`).
- Each `pos-*` module is an independently deployable service with its own database and REST API.

### Module Conventions

Treat each `pos-*` directory as a standard Spring Boot service using existing module patterns:

- `controller/` – REST endpoints (keep controllers thin)
- `service/` – business logic orchestration
- `repository/` – Spring Data JPA data access
- `entity/` – JPA entities and domain types
- `config/` – Spring configuration (security, DB, messaging)

### Builds, Tests, and Running

- Use Maven wrapper from repo root:
  - Build a module: `./mvnw clean compile -pl pos-accounting -am`
  - Run a module: `./mvnw spring-boot:run -pl pos-accounting`
- Prefer Actuator health checks when diagnosing runtime issues.
- `pos-agent-framework` tests may require `GITHUB_TOKEN` via Maven settings; never hardcode tokens.

### Events & Cross-Cutting Concerns

- Prefer extending existing event models (e.g., accounting/audit trail events) rather than inventing ad-hoc messaging.
- Before changing cross-cutting behavior (security, event schemas, accounting rules, inventory calculations), check relevant ADRs and docs.

## Moqui Frontend – durion-moqui-frontend

### Repository Summary

- The frontend platform is a **Moqui Framework application** with business components under `runtime/component/`.
- UI stack: **Vue 3 + Quasar + TypeScript 5**, layered on Moqui screens and REST APIs.
- The system follows **DDD-style bounded contexts**: each `durion-*` component owns its UI, services, and data model.

### Component & Coding Conventions

- Treat each directory under `runtime/component/` as a Moqui component; mirror its existing layout (entities, services, screens, assets).
- Service naming pattern: `{domain}.{service-type}#Action`.
- REST endpoint pattern: `/rest/api/v{version}/{resource}/{id}/{action}`.
- For integrations with the Spring Boot backend, use the existing bridge in `runtime/component/durion-positivity/` rather than adding ad-hoc HTTP calls.

### Builds, Running, and Tests

- Backend/Moqui build (Gradle wrapper):
  - Fast dev build: `./gradlew build -x test`
  - Full build: `./gradlew build`
- Docker Compose recommended for Moqui + DB: use compose files under `docker/`.
- Run without Docker (after build): `java -jar runtime/build/libs/moqui.war`.
- Frontend tooling (from repo root): `npm install`, `npm run dev`, `npm run build`, plus `npm run lint` / `npm run type-check`.
- Testing:
  - Server-side: Spock/Groovy via Gradle
  - Frontend: Jest for Vue/TypeScript (see `.github/agents/test.agent.md`)

### Governance / ADRs / Shared Context

- Prefer existing guidance under `.github/docs/` (architecture/governance) and domain terminology in `.ai/context.md` + `.ai/glossary.md`.
- When adding docs/components, link them from the closest relevant README so humans and agents can discover them.
