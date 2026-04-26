# ADR-0041: Frontend Angular SDK API Transport Policy

**Status:** ACCEPTED **Date:** 2026-04-25 **Deciders:** Frontend Architecture Team, SDK Maintainers, Platform Lead **Affected Repos:** `durion`, `durion-positivity-frontend`,
`durion-positivity-sdk-angular`

---

## Context

The active frontend is an Angular 21 application and the platform now has an Angular-native SDK in `durion-positivity-sdk-angular`.

That SDK provides:

- OpenAPI-generated Angular service classes for backend domains
- shared contract types generated from backend specs
- Angular DI integration around `HttpClient`
- a consistent place to regenerate frontend contracts when backend APIs change

Existing platform guidance is inconsistent:

- workspace documentation still references the framework-agnostic `durion-positivity-sdk` as the frontend integration layer
- ADR-0010 still describes `ApiBaseService` as the canonical frontend transport wrapper
- some frontend code paths use generated Angular SDK clients, while others still construct raw backend URLs

This creates avoidable drift:

- endpoint paths, headers, and request/response shapes are duplicated in the frontend
- backend OpenAPI changes can require manual frontend rewrites instead of SDK regeneration
- domain pages and services can mix transport concerns with feature logic
- migration work remains ambiguous because there is no explicit policy defining the approved client boundary

ADR-0010 remains correct for route ownership, feature boundaries, and page/service structure. This ADR refines ADR-0010 specifically for frontend-to-backend transport.

---

## Decision

### 1. Canonical frontend-to-backend client

**Decision:** ✅ **Resolved** - All API calls from `durion-positivity-frontend` to `durion-positivity-backend` must use `durion-positivity-sdk-angular`.

Required pattern:

- frontend code injects generated Angular SDK services or SDK workflow helpers
- backend contract changes are consumed by regenerating and rebuilding `durion-positivity-sdk-angular`
- frontend code treats the Angular SDK as the contract boundary for backend APIs

The framework-agnostic `durion-positivity-sdk` remains valid for non-Angular consumers, but it is not the canonical frontend transport for the Angular SPA.

### 2. Prohibited transport patterns for backend APIs

**Decision:** ✅ **Resolved** - Direct backend HTTP construction in the frontend is prohibited for new and modified work.

Prohibited patterns for backend calls include:

- feature services composing raw backend URLs
- page components calling `HttpClient` directly against backend endpoints
- page components calling `ApiBaseService` directly against backend endpoints
- feature logic relying on stringly-typed request/response contracts when an Angular SDK package exists for that domain

`ApiBaseService` is therefore legacy migration infrastructure, not the approved long-term boundary for frontend-to-backend domain calls.

### 3. Layering inside the frontend

**Decision:** ✅ **Resolved** - The Angular SDK does not remove the feature-layer architecture defined in ADR-0010.

Within `durion-positivity-frontend`:

- `services/` remain the primary place for feature-specific orchestration, response mapping, and UI-facing abstractions
- `pages/` and `components/` should not assemble backend URLs or own transport details
- generated SDK services may be injected into feature services directly
- page-level direct injection of generated SDK clients should be treated as a temporary shortcut, not the preferred steady-state pattern

This keeps backend contract usage centralized per feature while still enforcing SDK-based transport.

### 4. Core-layer scope

**Decision:** ✅ **Resolved** - Core frontend code may continue to own cross-cutting concerns such as auth state, token persistence, and HTTP interceptors, but backend
transport still flows through the Angular SDK.

Allowed core responsibilities:

- bearer-token attachment
- refresh-and-retry interception
- SDK configuration provisioning in `app.config.ts`
- non-backend HTTP such as translation asset loading or external integrations

Backend API transport should not bypass the Angular SDK simply because the call originates from `core/`.

### 5. Migration and enforcement

**Decision:** ✅ **Resolved** - Existing direct backend calls are technical debt and must be migrated toward Angular SDK usage opportunistically and by touched scope.

Required enforcement for new or modified frontend API work:

1. add or regenerate the needed contract in `durion-positivity-sdk-angular`
2. wire the SDK package/configuration in the frontend
3. consume the generated Angular SDK service from a feature service or approved workflow wrapper
4. test the frontend behavior against the SDK-facing abstraction rather than raw URL construction

If a backend endpoint is not yet present in `durion-positivity-sdk-angular`, the missing contract should be added there first unless a time-critical exception is explicitly
documented.

---

## Alternatives Considered

1. **Keep `ApiBaseService` as the canonical frontend transport**: Rejected. It duplicates path and payload knowledge in the frontend and weakens the value of the generated
   contract layer.
2. **Allow both Angular SDK and direct HTTP indefinitely**: Rejected. Mixed transport patterns create review ambiguity and slow migration because no single boundary is
   enforceable.
3. **Use the framework-agnostic `durion-positivity-sdk` in Angular**: Rejected. `durion-positivity-sdk-angular` already matches Angular DI, `HttpClient`, and `Observable`
   usage directly.

---

## Consequences

### Positive ✅

- Frontend/backend API contracts are centralized in one generated Angular client layer.
- Backend OpenAPI changes are consumed through SDK regeneration instead of repeated manual path rewrites.
- Feature code becomes more consistent because transport details are pushed to SDK packages and feature services.
- Reviewers get a clear architectural standard for migration work and new API integrations.

### Negative ⚠️

- Existing frontend code that still depends on `ApiBaseService` must be migrated over time.
- Backend endpoint additions may require coordinated work across backend, Angular SDK, and frontend repos before a feature can be completed.
- Generated SDK package maintenance becomes a hard dependency for frontend delivery.

### Neutral

- ADR-0010 still governs frontend feature ownership, routing, and page/service boundaries; this ADR only narrows the transport rule.
- `ApiBaseService` may continue to exist during migration, but it is not the target architecture for backend domain calls.

---

## Implementation Notes

- Update workspace and frontend documentation to state that `durion-positivity-sdk-angular` is the required frontend API layer.
- Treat ADR-0010's `ApiBaseService` wording as amended by this ADR.
- Prefer SDK client injection inside feature services, with pages/components consuming those services.
- Keep auth interceptors and SDK configuration in `src/app/core` and `src/app/app.config.ts`.
- Frontend tests should primarily mock SDK-facing feature abstractions; raw URL assertions belong in Angular SDK tests or temporary migration shims only.

---

## References

- [ADR-0010: Frontend Domain Responsibilities Guide](./0010-frontend-domain-responsibilities-guide.adr.md)
- [ADR-0035: Frontend Service Method Minimum Test Coverage](./0035-frontend-service-method-minimum-test-coverage.adr.md)
- [Workspace README](../../README.md)
- [durion-positivity-sdk-angular README](../../../durion-positivity-sdk-angular/README.md)
- [Frontend app.config.ts](/home/louis-burroughs/IdeaProjects/durion-positivity-frontend/src/app/app.config.ts)
