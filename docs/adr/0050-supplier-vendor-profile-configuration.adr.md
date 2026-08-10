# ADR-0050: Supplier Vendor Profile Configuration Model

**Status:** PROPOSED **Date:** 2026-08-10 **Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain **Affected Issues:** durion#372 (CAP-317),
durion-positivity-backend#1222

---

## Context

- **Current State**: Every deployment of the platform is expected to wire a custom set of vendors, capabilities, endpoints, norm versions, credentials, and account numbers.
  The same vendor differs per deployment (accounts, enabled capabilities, sandbox vs production).
- **The Problem**: Vendor wiring must be data, not code — otherwise each deployment forks the integration layer.
- **Drivers**: Deployment-level configurability (supplier architecture goal 3); platform secrets policy (no hardcoded credentials); the EDIWheel party model (per-message
  buyer/consignee identification); the Michelin account model (one credential set + two account numbers per transaction).
- **Scope**: The persisted configuration model in `pos-supplier` and its rules. Not covered: adapter/codec selection mechanics (ADR-0051).

---

## Decision

### 1. Vendor profile as the unit of configuration

**Decision:** ✅ **Resolved** — A **vendor profile** represents one supplier account in one deployment: profile key (`SupplierRef`), display name, protocol defaults (timeouts,
retry), sandbox overlay, and three child collections — auth configs, commercial accounts, and endpoint bindings. One vendor may legitimately have multiple profiles (regions,
legal entities). Profiles are DB-persisted with a permission-gated admin API; YAML bootstrap seeds first deployments by idempotent upsert.

### 2. Capability bindings

**Decision:** ✅ **Resolved** — A binding maps one capability to `(protocolFamily, version, baseUrl, path, authRef, optional cron schedule, enabled)`. **Absent binding =
capability disabled** for that vendor in that deployment, surfaced as a typed `CAPABILITY_NOT_CONFIGURED` status, never an error leak. Batch capabilities carry their schedule
on the binding; environment promotion changes only profile data (sandbox overlay), never code.

### 3. Credentials are references; account numbers are data

**Decision:** ✅ **Resolved** — Auth configs (`BASIC_PLUS_APIKEY`, `OAUTH2_CLIENT_CREDENTIALS`, `BEARER`) store **secret references only** (`env:` / secret-store keys),
resolved at call time; plaintext credentials never persist, never serialize into API responses (write-only fields), and never appear in logs or the exchange audit (redacted
headers). Commercial **account numbers** are ordinary profile data, distinct from credentials: one credential set authenticates the connection while account numbers identify
the commercial parties inside each message.

### 4. Canonical account roles: billing and delivery

**Decision:** ✅ **Resolved** — The profile models two generic account roles:

- **billing** — the invoicing/settlement account number (+ agency code), typically one per profile at legal-entity level, shareable across locations.
- **delivery** — account numbers per receiving location, stored as a mapping `pos-location UUID → account number (+ agency code)`.

Vendor vocabulary (`billTo`/`shipTo` in Michelin APIs, `BuyerParty`/`Consignee` in EDIWheel XML) exists **only inside adapters**; canonical code, entities, DTOs, events, and
UI use billing/delivery. Callers pass a `PartyContext` (billing account + delivery location); a missing delivery mapping for the requested location is a configuration error
raised **before any network call**.

### 5. Auditability

**Decision:** ✅ **Resolved** — Profile changes carry standard audit fields (ADR-0018/0024) and are permission-gated deny-by-default (ADR-0040, permissions registered per
ADR-0025). Raw exchange-payload access uses a separate, tighter permission than profile administration.

---

## Consequences

**Positive:** vendor onboarding and environment promotion are configuration acts; secrets stay in the secret store; location-aware ordering is validated before money moves.
**Negative / accepted:** profile schema becomes a contract that adapters depend on; delivery mappings must be maintained as locations change (surfaced in the admin UI as a
warning). **Open:** retention period for raw exchange payloads (operations/compliance decision, CAP-317 open question).

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §7
- ADR-0049, ADR-0051; platform secrets rule (CLAUDE.md/AGENTS.md)
