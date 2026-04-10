# Project Context

## Overview

Durion is a multi-repository platform for TIOTF/Durion initiatives, composed of:

- **Positivity POS Backend (Spring Boot)**: a Java 25, Spring Boot microservice suite organized as a monorepo with `pos-*` services, coordinated via an API gateway and service discovery.
- **Supporting tools**: scripts and “workspace agents” used for automation, documentation, and developer workflows.

## Goals

- Provide modular, domain-focused services for POS and automotive/tire industry operations.
- Provide modern, responsive web interfaces for ERP and operational workflows.
- Expose consistent REST APIs via an API gateway.
- Support event-driven workflows via shared events modules and an event receiver.
- Integrate the Moqui frontend with Positivity backend services through a dedicated integration component.

## Tech Stack

### Positivity Backend (durion-positivity-backend)

- Language: Java 25
- Framework: Spring Boot (3.x)
- Build: Maven (wrapper `./mvnw`, root `pom.xml`)
- Container: Docker Compose for local orchestration
- Ingress: API Gateway
- Discovery: Service discovery module

## Repository Structure (high-level)

### Positivity Backend (durion-positivity-backend)

Positivity is organized as a monorepo with Spring Boot microservices supporting:

- `pos-accounting/` — accounting domain
- `pos-agent-framework/` — automation/agents infrastructure
- `pos-api-gateway/` — edge/API aggregation (preferred external ingress)
- `pos-catalog/` — product/catalog services
- `pos-customer/` — customer/party services
- `pos-event-receiver/` — inbound event ingestion
- `pos-events/` — event models and publishing
- `pos-image/` — image/media handling
- `pos-inquiry/` — inquiry/search endpoints
- `pos-inventory/` — stock/inventory services
- `pos-invoice/` — invoicing and billing
- `pos-location/` — store/site/location services
- `pos-order/` — order lifecycle management
- `pos-people/` — people domain (non-auth)
- `pos-price/` — pricing/discounts
- `pos-security-service/` — authN/authZ services
- `pos-service-discovery/` — service registry
- `pos-shop-manager/` — shop/workflow management
- `pos-vehicle-fitment/` — fitment logic
- `pos-vehicle-inventory/` — vehicle-level inventory
- `pos-vehicle-reference-carapi/` — CarAPI integration
- `pos-vehicle-reference-nhtsa/` — NHTSA integration
- `pos-workorder/` — work orders/jobs

Project configuration:

- `docker/` — Docker Compose files
- `.github/` — docs, ADRs, agents, and instructions

## Operational Notes

- Positivity backend build enforces Java 25; ensure local JDK 25 is active. (managed via `.sdkmanrc`)
- Prefer running clients through the API Gateway rather than calling leaf services directly.
- Use Docker Compose for local end-to-end testing across services.

## Security & Compliance

- Secrets must come from environment variables or secret stores; never hardcoded.
- Follow OWASP best practices for input validation, authorization, and logging.
- Use parameterized queries; never concatenate untrusted input into SQL.
- Prefer deny-by-default access control patterns; verify authorization at the appropriate boundary (gateway/service).

## Development Guidance

- Maintain clear domain boundaries and stable DTO-based API contracts.
- For Positivity backend:
  - Keep controllers thin; put business logic in services.
  - Prefer extending existing event models and shared event infrastructure.
  - Write tests for critical paths (auth, pricing, order lifecycle, inventory updates).

## Adjacent Projects

- `durion-positivity-backend` — Java 25 Spring Boot microservices backend

## Context Management Rules

### Primary Rules

- If required inputs are missing from current context:
  SAY: "Context insufficient – re-anchor needed"
- If referring to decisions not found in:
  - current files
  - `.ai/CONTEXT.md`
  - `.ai/GLOSSARY.md`
  STOP

### Temporary Context Store Rules

**Purpose:** Minimize redundant file reads and maintain continuity across multi-step tasks.

1. **Context Store Location:** Maintain `.ai/session.md` as a temporary working document for the current development session.
2. **Session Initialization:** At the start of each task:
   - Check if `.ai/session.md` exists and is recent (updated within current session).
   - If yes: read `.ai/session.md` first before re-reading project files.
   - If no or stale: begin from `.ai/CONTEXT.md` and `.ai/GLOSSARY.md`.
3. **What to Store in Session:**
   - Current task objective and status
   - Key architectural decisions made in this session
   - Recent file paths and structures accessed
   - Active requirements/constraints being addressed
   - Integration points or dependencies discovered
   - Open questions or blockers
4. **Session Updates:** After completing any subtask or making significant progress:
   - Update `.ai/session.md` with findings, decisions, and next steps.
   - Include: timestamp, current file state, discovered patterns, decisions made.
   - Omit: full file contents (link instead).
5. **Session Cleanup:** At task completion or session end:
   - Preserve key decisions and learnings in `.ai/CONTEXT.md` if they're durable.
   - Delete or archive `.ai/session.md` when starting a new unrelated task.
6. **Conflict Resolution:** If session context contradicts project context:
   - Trust the permanent files (`.ai/CONTEXT.md`, `.ai/GLOSSARY.md`, source files).
   - Update session.md to reflect the authoritative state.
7. **Large Tasks:** For multi-file edits or complex deployments:
   - Create an `.ai/session-{task-id}.md` variant if the session spans hours or multiple contexts.
   - Link it in the main `.ai/session.md` for continuity.
