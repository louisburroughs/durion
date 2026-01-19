# AUDIT_DOMAIN_NOTES.md — Domain: `audit` (Non-Normative)

## Intended uses

- Architecture discussions
- Auditor explanations
- Future design work
- Governance review

## Forbidden uses

- Direct agent input
- CI validation
- Story execution rules

> This document explains *why* the normative decisions exist, alternatives considered, and how to communicate them to auditors and stakeholders.

---

## Overview (Security Lens)

The audit domain is inherently high-risk because it concentrates cross-domain history, actor identity, operational metadata, and occasionally sensitive payloads (pricing context, customer identifiers, free-text reason notes). The primary security objectives are:

1. Prevent cross-tenant data exposure.
2. Prevent cross-location data exposure (unless explicitly permitted).
3. Minimize sensitive data exposure (raw payload and rule traces).
4. Ensure audit exports are controlled, logged, and non-enumerable.
5. Ensure UI rendering of untrusted content cannot execute code (XSS prevention).
6. Provide operational guardrails against unbounded queries and export denial-of-service.

---

## Decision AUD-SEC-001 — Tenant Isolation and Scoping Rules

### What it means

Every audit read/write path must be tenant-scoped at the storage, query, and API layers. This includes:

- Search/list queries
- Detail queries
- Snapshot/trace retrieval
- Export requests and downloads
- Meta endpoints (event types, reason codes, locations)

### Why

Audit data is cross-domain; a single bug in scoping can expose large slices of a tenant’s operational history. Tenant isolation must be enforced server-side to prevent UI tampering, misconfiguration, or “ID guessing.”

### Alternatives considered

- Client-enforced tenant filtering (rejected; insufficient)
- Multi-tenant shared indexes without strict `tenantId` prefix keys (rejected unless provably safe)

### Auditor explanation

“We enforce tenant separation at every layer; even if someone manipulates the UI or query parameters, the system will not return data for another tenant.”

---

## Decision AUD-SEC-002 — Location Scoping and Cross-Location Permission Model

### What it means

- `locationId` is a required stored attribute for audit logs when the event is location-relevant.
- Default searches are implicitly scoped to the user’s current POS location context.
- Cross-location search is only allowed when BOTH conditions are met:
  1) user has `audit:scope:cross-location`
  2) request includes explicit `locationIds[]`

### Why

Locations are often a business boundary (different shops, different managers, different operational staff). Many real incidents are “right tenant, wrong location.” This model prevents accidental expansion of scope.

### Alternative considered

- Always require explicit `locationId` filter for all users (strong security but burdensome UX)
- Always allow cross-location for auditors without explicit filters (rejected; too easy to exfiltrate)

### Auditor explanation

“Users can only see audit data for their current location unless they have a special cross-location privilege and explicitly choose locations.”

---

## Decision AUD-SEC-003 — Authorization Model (Roles → Permission Strings)

### What it means

Permissions are action-scoped and composable:

- Viewing lists/details is distinct from viewing raw payload.
- Exporting is distinct from viewing.
- Pricing evidence is distinct from audit logs.
- Cross-location search is distinct from standard search.

This prevents “all-or-nothing” roles.

### Why

Audit systems tend to grow into powerful investigation tools. Least privilege is required to avoid turning every manager into a forensic auditor with access to sensitive raw data.

### Operational notes

- Support staff typically need “what happened” without raw payload.
- Auditors need exports but not necessarily raw payload.
- Some investigations require raw payload, but that should be granted explicitly and temporarily.

### Auditor explanation

“We separate permissions so that users can investigate issues without seeing sensitive raw payload data unless explicitly authorized.”

---

## Decision AUD-SEC-004 — Raw Payload Handling, Redaction, and Safe Rendering

### What it means

- Raw payload is treated as **untrusted**.
- UI rendering is always as **escaped text** (no HTML interpretation).
- Raw payload is not shown by default; it is behind a restricted permission (`audit:payload:view`).
- Field-level redaction is performed by the backend (or backend marks restricted fields).

Pricing artifacts require the same treatment:

- `quoteContext`
- trace `inputs/outputs`

### Why

Raw payloads often include PII, credentials, token-like identifiers, and internal system structure. They are also a stored-XSS risk if rendered improperly. Redaction must be backend-driven so it is consistent and auditable.

### Alternatives considered

- UI-side redaction (rejected; inconsistent and bypassable)
- Always show raw payload to auditors (rejected; least-privilege violation)

### Auditor explanation

“Raw data is available only to authorized personnel and is displayed safely. Sensitive fields may be redacted.”

---

## Decision AUD-SEC-005 — Query Guardrails

### What it means

Audit search must be bounded:

- Mandatory date range inputs (`fromUtc`, `toUtc`)
- Maximum range: 90 days (default)
- Require at least one indexed filter beyond date range for large tenants (policy-enforced)
- Backend returns clear 400 field errors for violations

### Why

Audit tables can become large quickly. Unbounded queries cause latency spikes and can be abused for denial-of-service or data scraping.

### Alternative considered

- UI-only guardrails (rejected; trivial to bypass)
- No guardrails, rely on indexes (rejected; insufficient under adversarial load)

### Auditor explanation

“We restrict searches to bounded time windows and require additional filters to prevent overbroad extraction.”

---

## Decision AUD-SEC-006 — Export Security Model

### What it means

- Export is async job-only.
- Export artifacts are:
  - tenant-scoped
  - permissioned (execute/download)
  - audited (who/when/filters/rowCount/digest)
  - time-bound for download (e.g., signed URL expiry or token expiry)
  - non-enumerable across users/tenants

Export includes:

- metadata (filters used, exportedAt, exportedBy, format)
- SHA-256 digest manifest (integrity evidence)

### Why

Exports represent bulk exfiltration risk. Async jobs allow strict controls, auditing, and consistent integrity proofs.

### Alternatives considered

- Synchronous download (rejected; weak auditing + scalability)
- Permanent export links (rejected; high leakage risk)

### Auditor explanation

“Exports are controlled, logged, and integrity-protected with a digest manifest.”

---

## Decision AUD-SEC-007 — Identifier Semantics for Search

### What it means

Support multiple product identifiers because different users think in different keys:

- Internal `productId` (UUIDv7) is canonical
- `sku` and `partNumber` are user-facing search identifiers
- Normalize/resolve on backend and index as needed

For other entities:

- Prefer denormalized stable IDs on `AuditLog` for indexed search (workOrderId, movementId, actorId, locationId).

### Why

Security is improved when queries rely on indexed, normalized fields rather than payload parsing. It also improves performance and reduces the need to expose raw payload.

---

## Decision AUD-SEC-008 — Pricing Evidence Access, Size Limits, Pagination

### What it means

Pricing traces can be extremely large. The API must:

- Permit pagination/truncation
- Provide explicit indicators when truncated
- Support safe rendering and redaction
- Avoid timeouts

Recommended approach:

- Snapshot returns small metadata and `ruleTraceId`
- Trace endpoint returns paged steps with `nextPageToken`

### Why

Large traces can cause:

- UI freezes
- API timeouts
- memory pressure
- accidental data leakage if not redacted

### Auditor explanation

“We preserve pricing evidence but deliver it safely and in a scalable way.”

---

## Decision AUD-SEC-009 — Immutability Proof Fields (Hash Chain / Signature)

### What it means

If `hash/prevHash/signature` exist, treat as:

- display-only fields
- never claim verification unless a server-side verification process is implemented and returns a boolean result

### Why

Many systems include hash-chain fields without actually verifying them. Claiming verification without real verification is an audit risk.

---

## Decision AUD-SEC-010 — Event Type Vocabulary

### What it means

`eventType` is a controlled vocabulary exposed via a meta endpoint.

### Why

Free-text event types degrade search quality and complicate authorization and analytics. Controlled vocabularies allow stable UI dropdowns and predictable indexing.

---

## Decision AUD-SEC-011 — Deep-Link Metadata Policy

### What it means

- Store structured link metadata, not URLs.
- Ensure links don’t leak unauthorized existence.
- Destination screens enforce access control.

### Why

URLs change; authorization rules evolve; hardcoded URLs in audit data create brittle and unsafe coupling.

---

## Decision AUD-SEC-012 — Correlation and Trace Context Standard

### What it means

Use W3C Trace Context (`traceparent`, `tracestate`) consistently.

### Why

Standard tracing reduces debugging time and supports incident response without exposing payloads.

---

# End of AUDIT_DOMAIN_NOTES.md
