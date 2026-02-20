## GitHub Copilot Instructions – Durion Platform (Frontend + POS Backend)

These instructions guide AI coding agents working across the Durion platform repositories in this workspace.

## Common Context

### Big Picture

- The Durion platform is:
  - **POS Backend (Spring Boot)**: Java 21 / Spring Boot microservice suite under `pos-*` directories. Project and repository name is `durion-positivity-backend`.
- The application interacts via REST APIs; Only special cases are allowed direct connections all other services are connected by API or event handling.
- The application follows **durion/domains/{domain}/.business-rules/*.md** for shared business logic and rules expressed in natural language.:
  - Keep changes scoped to the owning component/service.
  - Mirror patterns already present in the target module or component.
- Prefer existing architecture decisions and guidance:
  - See `durion/docs/` for architecture/governance ADRs, design and governance docs and overall project docs.
  - See `durion/domains/{domain}/.business-rules/` for domain specific business and configuration rules.
  - POS backend: `durion-positivity-backend/docs/` and module level `docs/`.

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
- Some git calls may require `GITHUB_TOKEN` via Maven settings; never hardcode tokens.

### Events & Cross-Cutting Concerns
- For cross-cutting concerns (security, events, accounting rules, inventory calculations), check relevant ADRs and docs before changing behavior.
- Before changing cross-cutting behavior (security, event schemas, accounting rules, inventory calculations), check relevant ADRs and docs.


### Governance / ADRs / Shared Context

- Prefer existing guidance under `docs/` (architecture/governance) and domain terminology in `.ai/context.md` + `.ai/glossary.md`.
- When adding docs/components, link them from the closest relevant README so humans and agents can discover them. If README is missing, create one.
