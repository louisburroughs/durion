# ADR-0017: API Controller HTTP Response Codes Standard

**Status:** ACCEPTED **Date:** 2026-02-17 **Deciders:** Architecture, Backend Lead, API Lead **Affected Issues:** N/A

---

## Context

Backend domain contract guides define overlapping HTTP response conventions, but there are inconsistencies that create implementation drift across controllers and tests:

- Most domains converge on `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `422`, and `500`.
- Some guides use `409` for business rule violations while others use `422` for similar domain outcomes.
- Error envelope shape is mostly aligned, but field-level validation details appear as either `fieldErrors[]` or `details[]`.

Without a platform-level standard, teams can implement semantically similar failures with different status codes, reducing API predictability and increasing contract-testing
friction.

---

## Decision

### 1. Canonical HTTP Response Matrix

**Decision:** ✅ **Resolved** - Standardize controller responses using the following matrix unless a domain ADR explicitly overrides a case.

- `200 OK` for successful reads and successful mutations that return a response body.
- `201 Created` for successful resource creation.
- `202 Accepted` for mutations whose effect is enqueuing an asynchronous command event ([ADR-0044](0044-platform-event-only-domain-walls.adr.md) R4). The response body carries a tracking/idempotency reference and a link to a pending-state resource; asynchronous rejection (the owner's result event reports "rejected") is surfaced through that status resource, never as a late HTTP error on the original request. *(Added 2026-07-08 by ADR-0044.)*
- `204 No Content` for successful operations with no response body (for example delete/revoke).
- `400 Bad Request` for malformed requests and request-shape/field validation errors.
- `401 Unauthorized` for missing or invalid authentication credentials.
- `403 Forbidden` for authenticated callers lacking required permissions.
- `404 Not Found` when the requested resource does not exist.
- `409 Conflict` for resource state/version/idempotency conflicts and business state collisions.
- `422 Unprocessable Entity` for semantically valid requests that violate domain policy and are not representable as a conflict.
- `500 Internal Server Error` for unhandled server-side failures.

`501 Not Implemented` may only be used for explicitly documented stub endpoints.

### 2. Conflict vs Unprocessable Boundary

**Decision:** ✅ **Resolved** - Prefer `409` for stateful collisions and use `422` sparingly for semantic domain-policy violations.

Use `409` when the request cannot be applied because of current resource/system state, including optimistic-lock/version mismatch, duplicate-unique constraints, invalid
lifecycle transition, and idempotency-key payload mismatch.

Use `422` when payload shape is valid and resource state is not the primary issue, but the requested operation violates domain policy rules that are explicitly documented in
the endpoint contract.

### 3. Error Envelope Contract

**Decision:** ✅ **Resolved** - Standardize non-2xx response bodies to a common envelope with correlation and field-level diagnostics.

Minimum required fields:

- `code`
- `message`
- `status`
- `timestamp`
- `correlationId`

Validation and domain error details:

- Use `fieldErrors[]` as the canonical field-level collection.
- Use `details[]` only when explicitly required by a documented external contract; otherwise use `fieldErrors[]`.

### 4. Correlation and Observability

**Decision:** ✅ **Resolved** - Error responses must include and propagate `X-Correlation-Id`.

- If the header is provided by the client, echo it in the response and map it to `correlationId`.
- If absent, generate one and include it in both response headers and error body.

---

## Alternatives Considered

1. **Per-domain status code interpretation only**: Rejected due to cross-service inconsistency and higher contract-maintenance cost.
2. **Collapse `409` and `422` into a single code**: Rejected because state conflicts and semantic policy violations carry different remediation patterns.
3. **No standard error envelope**: Rejected due to degraded debuggability and weaker observability across services.

---

## Consequences

### Positive ✅

- ✅ Predictable controller behavior across backend services
- ✅ Reduced ambiguity for client developers and contract tests
- ✅ Improved traceability through consistent correlation handling
- ✅ Clear guidance for implementing domain exceptions and global error handlers

### Negative ⚠️

- ⚠️ Teams must consistently classify domain failures as `409` or `422` to avoid semantic drift
- ⚠️ New feature delivery must include status-code and error-envelope contract tests

### Neutral

- Neutral impact on endpoint paths and resource models

---

## Implementation Notes

- Apply this standard in controller advice / exception mapping layers for each `pos-*` service.
- Keep controllers thin and centralize status-code mapping in service exceptions + global handlers.
- Document any domain-specific override in the relevant domain `BACKEND_CONTRACT_GUIDE.md` and reference this ADR.
- Add or update integration tests for representative `400`, `401`, `403`, `404`, `409`, and `422` cases.

---

## References

- [Accounting Backend Contract Guide](../../domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Billing Backend Contract Guide](../../domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [CRM Backend Contract Guide](../../domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Inventory Backend Contract Guide](../../domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Location Backend Contract Guide](../../domains/location/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Order Backend Contract Guide](../../domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [People Backend Contract Guide](../../domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Pricing Backend Contract Guide](../../domains/pricing/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Security Backend Contract Guide](../../domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Shop Management Backend Contract Guide](../../domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Work Execution Backend Contract Guide](../../domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [ADR-0009: Backend Domain responsibilities](0009-backend-domain-responsibilities-guide.adr.md)
- [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md)

---

## Sign-Off

| Role         | Name | Date       | Notes |
| ------------ | ---- | ---------- | ----- |
| Architecture | LMB  | 2026-02-17 |       |
| Backend Lead | LMB  | 2026-02-17 |       |
| API Lead     | LMB  | 2026-02-17 |       |

---

## Timeline

- **Proposed**: 2026-02-17
- **Accepted**: 2026-02-17

---

## Changelog

- **2026-02-17**: Initial draft
- **2026-02-17**: Marked ACCEPTED and clarified greenfield implementation guidance
