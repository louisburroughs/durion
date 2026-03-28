# ADR-0010: Frontend Domain Responsibilities Guide

**Status:** ACCEPTED  
**Date:** 2026-03-28  
**Deciders:** Frontend Architecture Team, Platform Lead  
**Affected Repos:** `durion-positivity-frontend`, `durion`

---

## Context

The previous version of this ADR described a Moqui + Vue/Quasar multi-component frontend model.
That no longer reflects the active implementation.

The current production frontend is the Angular application in `durion-positivity-frontend`:

- Angular 21 standalone architecture (`@angular/core` 21.x)
- Route-based lazy loading by domain under `src/app/features/*`
- Shared auth/HTTP concerns in `src/app/core/*`
- Capability orchestration and execution tracking in `durion/docs/capabilities/CAPABILITY_STATUS_BOARD.md`

Without an updated ADR, architecture guidance is misleading (wrong framework, wrong structure, wrong responsibilities).

---

## Decision

### 1) Canonical Frontend Architecture

The canonical UI implementation for Durion is Angular, not Moqui/Vue component assets.

Architecture baseline:

- App shell + lazy feature routes via `src/app/app.routes.ts`
- Public routes: `/login`, `/forbidden`, `/not-found`
- Protected app routes under `/app` using `authGuard` + `rolesChildGuard`
- Optional role constraints via route `data.roles` (for example `security`, `admin`)
- Standalone components and route-level lazy loading (`loadComponent`, `loadChildren`)

### 2) Core Layer Responsibilities (`src/app/core`)

Core is the only layer that owns cross-cutting transport/auth behavior.

- `AuthService`
  - Login/logout/session validation
  - Access/refresh token lifecycle
  - Role extraction and role checks
- `authInterceptor`
  - Attaches bearer token
  - Handles 401 -> refresh -> retry once
- Guards
  - `authGuard`: authentication gate
  - `rolesGuard` / `rolesChildGuard`: RBAC gate
- `ApiBaseService`
  - Single base URL wrapper for feature services
  - Shared HTTP verbs and optional headers

Rule:

- Feature services must use `ApiBaseService` for backend calls.
- Direct `HttpClient` usage in features is not allowed (exception: core auth/interceptor internals).

### 3) Feature Domain Ownership (`src/app/features`)

Each Angular feature folder owns its pages, route table, domain services, and domain UI models.
Cross-domain coupling must happen through backend APIs/contracts, not direct feature-to-feature imports.

| Angular Feature | Primary Domain Responsibility | Current Maturity |
| --- | --- | --- |
| `workexec` | Estimates, approvals, workorder execution/completion | Implemented (Wave B/C baseline) |
| `crm` | Party/contact/vehicle flows, snapshot, billing rules | Implemented (Wave A baseline) |
| `accounting` | Event monitoring, posting rules, payments, credit memos, vendor payments | Implemented (Wave D baseline) |
| `billing` | Invoice detail and billing views tied to workorder flow | Implemented |
| `security` | Roles, permissions, audit UI | Implemented (Wave E) |
| `auth` | Login/session UX | Implemented |
| `shopmgmt` | Scheduling/dispatch/appointment operations | In flight (Wave F) |
| `location` | Locations, bays, mobile units | In flight (Wave F) |
| `people` | Timekeeping approval/export/work session | Wave G queue |
| `inventory` | Inventory domain entry point | Stub |
| `product` | Product domain entry point | Stub |
| `order` | Order domain entry point | Stub |
| `admin` | Global admin entry point | Minimal |
| `shell` | App shell, dashboard, shared chat panel wiring | Implemented |
| `system` | Access denied / not found system pages | Implemented |

Execution status for capabilities is tracked in `docs/capabilities/CAPABILITY_STATUS_BOARD.md`.
This ADR defines architecture and ownership; the status board defines delivery state.

### 4) Routing and Navigation Conventions

- All business routes live under `/app/<feature>`.
- Feature route files are the source of truth for domain navigation contracts.
- New capabilities must add routes in the owning feature route file, not in `app.routes.ts` directly.
- Catch-all behavior must remain feature-local (`{ path: '**', redirectTo: ... }`) when needed.

### 5) Data/Service Layer Conventions

Within a feature:

- `services/` wraps backend calls and maps to frontend models.
- `models/` defines DTOs/UI types for the feature boundary.
- `pages/` contains route-level containers/pages.
- `components/` contains reusable presentational domain widgets.

Rules:

- Keep transformation and endpoint specifics in feature services.
- Keep route params/query parsing in page components.
- Avoid domain state in global singletons unless it is session-level concern.

### 6) Security and RBAC Conventions

- Authentication gate is mandatory for `/app/**`.
- Route-level RBAC must be declared via `data.roles`.
- UI role checks use `AuthService.hasRole/hasAnyRole`; backend remains source of truth for authorization.
- Session expiry behavior must redirect to `/login` with a return URL when possible.

### 7) Testing and Quality Baseline

- Unit/component tests use Angular TestBed.
- HTTP-dependent tests use `HttpClientTesting` utilities.
- Service tests should assert contract mapping behavior and error handling.
- Route guards/interceptor behavior must remain covered by targeted tests.

---

## Consequences

### Positive

- Architecture guidance now matches the actual Angular codebase.
- Domain ownership is explicit at the feature-folder and route level.
- Core cross-cutting concerns (auth, base API transport, RBAC) stay centralized and auditable.
- Capability execution planning can reference a stable frontend structure.

### Tradeoffs

- Feature maturity is mixed (implemented + in-flight + stubs), so patterns must tolerate partial domains.
- Route sprawl risk increases as capabilities expand; this requires disciplined feature route maintenance.

---

## Implementation Notes

- This ADR supersedes framework assumptions from the earlier Moqui/Vue-oriented version of ADR-0010.
- Use this ADR together with:
  - `durion/docs/capabilities/CAPABILITY_STATUS_BOARD.md` for execution status
  - `durion-positivity-frontend/src/app/app.routes.ts` for active route topology
  - `durion-positivity-frontend/src/app/core/*` for transport/auth/RBAC enforcement patterns

---

## References

- Angular docs: <https://angular.dev/>
- [CAPABILITY_STATUS_BOARD.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAPABILITY_STATUS_BOARD.md)
- [app.routes.ts](/home/louis-burroughs/IdeaProjects/durion-positivity-frontend/src/app/app.routes.ts)
- [app.config.ts](/home/louis-burroughs/IdeaProjects/durion-positivity-frontend/src/app/app.config.ts)
- [api-base.service.ts](/home/louis-burroughs/IdeaProjects/durion-positivity-frontend/src/app/core/services/api-base.service.ts)
- [auth.service.ts](/home/louis-burroughs/IdeaProjects/durion-positivity-frontend/src/app/core/services/auth.service.ts)
- [auth.interceptor.ts](/home/louis-burroughs/IdeaProjects/durion-positivity-frontend/src/app/core/interceptors/auth.interceptor.ts)
