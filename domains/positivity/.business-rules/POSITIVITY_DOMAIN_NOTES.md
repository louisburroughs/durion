# POSITIVITY_DOMAIN_NOTES.md

## Summary

This document is a non-normative rationale and decision log for the `positivity`
domain. It captures intent and trade-offs behind the orchestration and
composition patterns referenced by the Decision Index.

Normative rules and the authoritative Decision IDs live in `AGENT_GUIDE.md`.

## Completed items

- [x] Decision sections align to Decision IDs
- [x] No unresolved Open Questions or TODOs remain
- [x] Document ends with the required footer

## Decision details

### DECISION-POSITIVITY-001 - Order cancellation uses persisted saga

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-001)
- Decision: Orchestrate Order cancellation as a persisted saga/state machine.
- Rationale: Enables deterministic retries and crash-safe progress tracking.

### DECISION-POSITIVITY-002 - Work cancellation precedes payment reversal

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-002)
- Decision: Cancel work first, then reverse payment.
- Rationale: Avoids revenue-loss and reconciliation risk when work cancel fails.

### DECISION-POSITIVITY-003 - Cancellation idempotency and deduplication

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-003)
- Decision: Require a client idempotency key; server deduplicates repeats.
- Rationale: Retries must not repeat downstream side effects.

### DECISION-POSITIVITY-004 - Product Detail aggregation with graceful degradation

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-004)
- Decision: Serve Product Detail via an aggregated endpoint; degrade with 200 +
  per-component statuses when Pricing/Inventory fail.
- Rationale: Keeps UI responsive without misrepresenting unknown values.

### DECISION-POSITIVITY-005 - `location_id` is required for Product Detail

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-005)
- Decision: `location_id` is required; missing/invalid returns 400.
- Rationale: Pricing and availability are location-scoped.

### DECISION-POSITIVITY-006 - Null numeric fields when component status is non-OK

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-006)
- Decision: When a component status is non-OK, numeric fields must be null.
- Rationale: Prevents the UI from presenting unknown values as real numbers.

### DECISION-POSITIVITY-007 - `generatedAt`, `asOf`, and staleness semantics

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-007)
- Decision: Use `generatedAt` for composition time and per-component `asOf` for
  freshness; allow staleness thresholds.
- Rationale: Separates response timing from component data freshness.

### DECISION-POSITIVITY-008 - Short TTL caching and cache key requirements

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-008)
- Decision: Allow short TTL caching; cache key includes `productId` +
  `locationId`.
- Rationale: Prevents cross-location leakage while reducing dependency load.

### DECISION-POSITIVITY-009 - Fail fast only on Catalog 404

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-009)
- Decision: Catalog not-found is the only fail-fast condition; others degrade.
- Rationale: Catalog is the identity/exists check; others are enrichments.

### DECISION-POSITIVITY-010 - Dynamic lead time overrides static lead time

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-010)
- Decision: Inventory dynamic lead time overrides Catalog static hints.
- Rationale: Avoids inconsistent precedence logic across clients.

### DECISION-POSITIVITY-011 - Status fields are treated as opaque strings

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-011)
- Decision: Clients special-case only `OK`; other statuses are opaque.
- Rationale: Forward-compatible evolution of status taxonomies.

### DECISION-POSITIVITY-012 - Currency field rules for pricing

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-012)
- Decision: When `pricing.status == OK`, `pricing.currency` is required;
  otherwise currency is null.
- Rationale: Prevents formatting ambiguity and stale-value display.

### DECISION-POSITIVITY-013 - Permission gating model for Product Detail

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-013)
- Decision: Enforce `positivity:product_detail:read` and location authorization.
- Rationale: Deny-by-default and avoids partial leakage for unauthorized users.

### DECISION-POSITIVITY-014 - Trace and correlation propagation

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-014)
- Decision: Prefer W3C trace context; ensure a `correlationId` exists and return
  it via `X-Correlation-Id`.
- Rationale: Standard cross-service diagnostics.

### DECISION-POSITIVITY-015 - No per-request events for read degradation

- Normative source: `AGENT_GUIDE.md` (DECISION-POSITIVITY-015)
- Decision: Track read degradation via metrics/structured logs, not events.
- Rationale: Avoids high-cardinality event streams.

## End

End of document.
