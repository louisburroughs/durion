# ADR-0034: Frontend Server-Generated Field Omission Policy

**Status:** ACCEPTED
**Date:** 2026-03-29
**Deciders:** Frontend Architecture Team, Backend Contract Team
**Affected Issues:** PR #12 review finding — thread r3006417708; CAP-166 (Location Price Overrides), ADR-0024 (entity createdAt/updatedAt population)

---

## Context

The Durion backend populates certain timestamp and audit fields automatically at write time (e.g., `createdAt`, `updatedAt`, `requestedAt`, `approvedAt`, `issuedAt`). These fields must not be sent by the client — doing so causes backend validation failures, stores incorrect data, or silently overrides server-generated values.

In PR #12, `location-overrides.component.html` sent `requestedAt: ''` (empty string from a hidden input) as part of the `POST /pricing/v1/overrides` request body. The backend contract specifies that `requestedAt` is server-set at record creation. The empty string would either fail backend validation or store `null`/empty instead of the actual creation timestamp.

ADR-0024 already governs backend policy for `createdAt`/`updatedAt`. This ADR extends that policy to the frontend type system and API payload construction.

---

## Decision

### 1. Model Interface Typing

**Decision:** ✅ **Resolved** — Fields that are server-generated must be typed as `readonly` and optional in TypeScript frontend interfaces.

```typescript
export interface LocationPriceOverride {
  id: string;
  locationId: string;
  productSku: string;
  overridePrice: number;
  currency: string;
  status: LocationPriceOverrideStatus;
  reason: string;
  readonly requestedAt?: string;   // server-generated — optional, readonly
  readonly approvedAt?: string;    // server-generated — optional, readonly
}
```

The `readonly` modifier communicates intent and prevents accidental mutation in component code. The `?` (optional) ensures the type is valid both for new objects (before the server has set the field) and for read objects (where the field is present).

### 2. Request Payload Construction

**Decision:** ✅ **Resolved** — Server-generated fields must be excluded from create/update request payloads. Never include them as empty strings, `null`, or computed client-side values.

```typescript
// CORRECT — omit server-generated fields entirely
this.api.post<LocationPriceOverride>('/pricing/v1/overrides', {
  locationId,
  productSku,
  overridePrice,
  currency,
  reason,
  status: 'PENDING_APPROVAL',
  // requestedAt intentionally absent — server sets it
});

// WRONG — empty string sent for server-generated field
this.api.post<LocationPriceOverride>('/pricing/v1/overrides', {
  ...,
  requestedAt: '',
});

// WRONG — client-computed timestamp for server-generated field
this.api.post<LocationPriceOverride>('/pricing/v1/overrides', {
  ...,
  requestedAt: new Date().toISOString(),  // server should set this, not client
});
```

### 3. Identification of Server-Generated Fields

**Decision:** ✅ **Resolved** — The canonical list of server-generated fields per domain model must be documented in the domain's model file via a TSDoc `@serverGenerated` block comment on the property.

```typescript
/**
 * ISO 8601 timestamp set by the server when the override request is created.
 * @serverGenerated — do not include in create/update request payloads.
 */
readonly requestedAt?: string;
```

When in doubt, treat any timestamp field not filled in by the user via a form input as server-generated.

### 4. Angular Template Restriction

**Decision:** ✅ **Resolved** — Angular templates must not use `new` keyword expressions (e.g., `new Date()`) directly in template bindings or event handlers. Angular's template compiler does not support arbitrary JavaScript expressions including `new`.

If a timestamp is required in a submit handler and must be client-generated (e.g., an explicit user-action timestamp), compute it in the component method, not in the template.

```typescript
// CORRECT — compute in component method
submitWithTimestamp(): void {
  const submittedAt = new Date().toISOString();
  this.service.create({ ..., submittedAt });
}
```

```html
<!-- WRONG — new Date() is invalid in Angular templates -->
<form (submit)="create({ submittedAt: (new Date()).toISOString() })">
```

---

## Alternatives Considered

1. **Omit field entirely from the frontend interface**: Don't declare server-generated fields in the TypeScript model at all. Rejected: frontend reads these fields back from API responses (e.g., display `requestedAt` on the override detail view), so the field must exist in the interface for read operations.

2. **Use a separate `CreateRequest` type that omits server-generated fields**: Create `type CreateLocationPriceOverrideRequest = Omit<LocationPriceOverride, 'requestedAt' | 'approvedAt'>`. Accepted as a complementary pattern; useful when the create payload is significantly different from the read shape. Not mandated by this ADR (adds type surface area for simple cases) but recommended when the omitted field count is three or more.

3. **Backend validation rejects server-generated fields if present**: Rely on backend to reject invalid payloads. Rejected as the primary guard: frontend should not send incorrect payloads in the first place; backend rejection is a safety net, not a policy substitute.

---

## Consequences

### Positive ✅

- Prevents data corruption from client-overriding server-generated timestamps.
- `readonly` and `?` on model fields communicate intent clearly to future developers.
- `@serverGenerated` TSDoc provides per-field documentation at the type declaration.
- Eliminates a class of backend request validation failures.

### Negative ⚠️

- Requires audit of existing domain models to identify undocumented server-generated fields.
- `Omit<>` create-request types add surface area if used extensively.

### Neutral

- Cross-references ADR-0024 (backend) and extends its intent to the frontend type contract.

---

## Implementation Notes

- Include in `durion-positivity-frontend/AGENTS.md` PR checklist: "Server-generated fields (`requestedAt`, `approvedAt`, `createdAt`, `updatedAt`) are `readonly?` in models and absent from create/update payloads."
- Angular template restriction on `new` keyword is enforced at compile time (NG5002 error). This ADR documents the correct resolution pattern.
- Common server-generated fields across Durion domain models: `createdAt`, `updatedAt`, `requestedAt`, `approvedAt`, `issuedAt`, `completedAt`, `cancelledAt`.
