# ADR-0021: Tax API Consumption and Internal Access Policy

**Status:** ACCEPTED  
**Date:** 2026-02-21  
**Deciders:** Architecture Team, Backend Lead, Security Lead  
**Affected Issues:** [Tax API contract hardening], [Internal-only service exposure policy]

---

## Context

`pos-tax` now exposes a structured international request contract and stricter validation semantics for destination addresses. At the same time, `pos-tax` is an internal platform service and should not be publicly reachable through external gateway routes.

Without an explicit ADR:

- Callers may treat address validation as best-effort and not handle hard validation failures.
- Teams may assume `pos-tax` endpoints can be consumed directly from external clients.
- Internal service exposure can drift over time if route and network controls are not explicit.

We need a clear contract for:

1. How callers should use `pos-tax` APIs.
2. What strict address validation means operationally.
3. How access is restricted to internal callers only.

---

## Decision

✅ **Resolved** — `pos-tax` APIs are internal-only APIs with strict address validation. Callers must submit valid internationalized address fields and must handle `400` validation responses as contract failures, not transient errors.

### 1. API Usage Contract

**Decision:** ✅ **Resolved** — Consumers use:

- `POST /api/v1/tax/calculate`
- `GET /api/v1/tax/mode` (diagnostic/internal)

`POST /api/v1/tax/calculate` requests must provide `destinationAddress` with:

- `countryCode` (ISO 3166-1 alpha-2, valid code)
- `regionCode` (when provided, must belong to country using ISO 3166-2 dataset validation)
- `postalCode` (required)

Additional request metadata (`currencyCode`, `locale`, etc.) should be supplied when available.

### 2. Strict Validation Expectations

**Decision:** ✅ **Resolved** — Validation is fail-fast and strict:

- Invalid country code -> `400 Bad Request`
- Invalid subdivision for country -> `400 Bad Request`
- Missing required address fields -> `400 Bad Request`

Caller behavior requirements:

- Treat `400` as a deterministic data quality or mapping error.
- Do not retry `400` responses automatically.
- Log and surface validation failures with enough context to correct source data.
- Ensure upstream systems normalize country/region codes before calling `pos-tax`.

### 3. Internal-Only Access and Whitelist Enforcement

**Decision:** ✅ **Resolved** — `pos-tax` endpoints are only reachable by internal module/service calls.

Enforcement model:

1. **Gateway route whitelist:** `pos-tax` MUST NOT be mapped as a public route in `pos-api-gateway` (aligns with ADR-0014).
2. **Service caller whitelist:** Only approved internal services/modules may invoke `pos-tax` endpoints (explicit allowlist in security/network policy).
3. **Network boundary controls:** Deploy `pos-tax` in private/internal network zones; no internet ingress.

Recommended implementation pattern:

- Maintain an explicit allowlist of caller identities (service principals / trusted internal network sources).
- Deny-by-default for all non-whitelisted callers.
- Keep `GET /api/v1/tax/mode` internal; do not expose publicly.

---

## Alternatives Considered

1. **Lenient validation with silent normalization**
   - ❌ Rejected: hides data quality issues and creates nondeterministic tax outcomes.

2. **Publicly expose `pos-tax` via gateway with auth**
   - ❌ Rejected: unnecessary expansion of attack surface for a backend utility service.

3. **No caller whitelist, network-only trust**
   - ❌ Rejected: insufficient least-privilege posture; harder to audit who is allowed to call tax APIs.

---

## Consequences

### Positive ✅

- Deterministic tax inputs and predictable validation outcomes.
- Better data quality in upstream services due to strict contract enforcement.
- Reduced security exposure by keeping tax endpoints internal.
- Clear operational guidance for handling validation failures.

### Negative ⚠️

- Integrators must normalize address/country/region data before calling tax service.
- More upfront integration work for teams migrating to strict address semantics.
- Whitelist governance overhead (maintaining approved caller list).

### Neutral

- External clients should continue using domain APIs (gateway/public services), not `pos-tax` directly.

---

## Implementation Notes

- Request/response examples and schema are documented in OpenAPI for `pos-tax`.
- Validation is implemented through Jakarta Bean Validation plus custom ISO validators.
- Subdivision validity is checked against an ISO 3166-2-backed dataset.
- Security and deployment must enforce deny-by-default + explicit internal allowlist.
- Side note (OpenAPI YAML examples): for string-like numeric values (for example, postal codes), set examples with escaped quotes (e.g., `example = "\"90001\""`) so generated YAML preserves them as string literals.

---

## References

- `durion-positivity-backend/pos-tax/src/main/java/com/positivity/tax/internal/controller/TaxController.java`
- `durion-positivity-backend/pos-tax/src/main/java/com/positivity/tax/internal/dto/TaxCalculationRequest.java`
- `durion-positivity-backend/pos-tax/src/main/java/com/positivity/tax/internal/validation/ValidSubdivisionForCountry.java`
- `durion-positivity-backend/pos-tax/src/main/java/com/positivity/tax/internal/validation/SubdivisionForCountryValidator.java`
- `durion/docs/adr/0014-gateway-internal-service-security.adr.md`
