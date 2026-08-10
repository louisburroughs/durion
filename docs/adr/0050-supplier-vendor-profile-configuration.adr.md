# ADR-0050: Supplier Vendor Profile Configuration Model

**Status:** PROPOSED — revised 2026-08-10 (PRCR-005/006/007)  
**Date:** 2026-08-10  
**Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain  
**Affected Issues:** durion#372 (CAP-317), durion-positivity-backend#1222

---

## Context

- **Current State**: Every deployment of the platform is expected to wire a custom set of vendors, capabilities, endpoints, norm versions, credentials, and account numbers.
  The same vendor differs per deployment (accounts, enabled capabilities, sandbox vs production).
- **The Problem**: Vendor wiring must be data, not code — otherwise each deployment forks the integration layer. Raw exchange payloads may contain account, vehicle,
  commercial, and customer data, so their capture and retention need explicit governance.
- **Drivers**: Deployment-level configurability (supplier architecture goal 3); platform secrets policy (no hardcoded credentials); ADR-0027 UUID-typed identifiers; the
  EDIWheel party model (per-message buyer/consignee identification); the Michelin account model (one credential set + two account numbers per transaction).
- **Scope**: The persisted configuration model in `pos-supplier` and its rules, including exchange-payload governance. Not covered: adapter/codec selection mechanics
  (ADR-0051).

---

## Decision

### 1. Profile identity: UUID primary, supplierRef alias

**Decision:** ✅ **Resolved** — A vendor profile's platform identity is **`vendorProfileId` (UUIDv7)** per ADR-0013/0027: it is the primary key, the value carried in foreign
keys, events, and exchange-audit rows. **`supplierRef`** is retained as a unique, human-readable configuration alias (used in YAML, logs, and admin screens) — it is an
attribute, never an identifier crossing a contract boundary.

### 2. Vendor profile as the unit of configuration

**Decision:** ✅ **Resolved** — A **vendor profile** represents one supplier account in one deployment: identity (§1), display name, protocol defaults (timeouts, retry),
sandbox overlay, `sourceOfTruth` (§6), and three child collections — auth configs, commercial accounts, and endpoint bindings. One vendor may legitimately have multiple
profiles (regions, legal entities).

### 3. Capability bindings

**Decision:** ✅ **Resolved** — A binding maps one capability to `(protocolFamily, version, baseUrl, path, authRef, optional cron schedule, enabled)`. **Absent binding =
capability disabled** for that vendor in that deployment, surfaced as a typed `CAPABILITY_NOT_CONFIGURED` status, never an error leak. Batch capabilities carry their schedule
on the binding; environment promotion changes only profile data (sandbox overlay), never code.

### 4. Credentials are references; account numbers are data

**Decision:** ✅ **Resolved** — Auth configs (`BASIC_PLUS_APIKEY`, `OAUTH2_CLIENT_CREDENTIALS`, `BEARER`) store **secret references only** (`env:` / secret-store keys),
resolved at call time; plaintext credentials never persist, never serialize into API responses (write-only fields), and never appear in logs or the exchange audit (redacted
headers). Commercial **account numbers** are ordinary profile data, distinct from credentials: one credential set authenticates the connection while account numbers identify
the commercial parties inside each message.

### 5. Canonical account roles: billing and delivery

**Decision:** ✅ **Resolved** — The profile models two generic account roles:

- **billing** — the invoicing/settlement account number (+ agency code), typically one per profile at legal-entity level, shareable across locations.
- **delivery** — account numbers per receiving location, stored as a mapping `pos-location UUID → account number (+ agency code)`.

Vendor vocabulary (`billTo`/`shipTo` in Michelin APIs, `BuyerParty`/`Consignee` in EDIWheel XML) exists **only inside adapters**; canonical code, entities, DTOs, events, and
UI use billing/delivery. Callers pass a `PartyContext` (billing account + delivery location); a missing delivery mapping for the requested location is a configuration error
raised **before any network call**.

### 6. YAML is authoritative on every startup

**Decision:** ✅ **Resolved** — Each profile carries a `sourceOfTruth` marker: `YAML` or `ADMIN`.

- For `YAML`-managed profiles, the YAML configuration is **authoritative on every startup**, not merely a first-boot seed: startup reconciles the database to the YAML —
  creating, updating, and overwriting as needed. A profile previously YAML-sourced but absent from the current YAML is **disabled, not deleted** (history and exchange audit
  are preserved); its bindings stop resolving.
- The admin API **rejects** create/update/delete on `YAML`-managed profiles with an error naming the configuration source — admin changes to them are not "temporary," they
  are not possible. Read access is unaffected.
- Profiles created through the admin API are `ADMIN`-managed and untouched by YAML reconciliation. A deployment may freely mix both kinds.
- YAML reconciliation writes standard audit fields with actor `system:yaml-bootstrap`, so profile history distinguishes operator edits from configuration rollouts.
- Secrets remain references in both modes (§4); YAML never contains plaintext credentials.

### 7. Exchange-payload governance

**Decision:** ✅ **Resolved** — Raw request/response payloads in the exchange audit are commercial records and are governed as follows:

- **Retention:** default **400 days** (13 months — covers an annual dispute/audit cycle with margin; accepted 2026-08-10), configurable per deployment; a scheduled job
  hard-deletes payloads past retention while retaining the exchange metadata row (timings, outcome, correlation ID) for operational history.
- **Encryption:** payload columns are encrypted at rest.
- **Minimization:** redaction extends beyond credential headers to configured body fields, driven by data classification (e.g. customer identifiers in fleet workorder
  authorization payloads); redaction configuration lives with the binding.
- **Capture level per binding:** `FULL` | `REDACTED` | `METADATA_ONLY` — deployments can disable payload capture per capability or per data classification without losing the
  exchange metadata trail.
- **Access:** payload reads require the dedicated `supplier:audit:read` permission (tighter than profile admin) and are themselves audited (who read which exchange, when).

### 8. Auditability

**Decision:** ✅ **Resolved** — Profile changes carry standard audit fields (ADR-0018/0024) and are permission-gated deny-by-default (ADR-0040, permissions registered per
ADR-0025).

---

## Consequences

**Positive:** vendor onboarding and environment promotion are configuration acts with a single authoritative source per profile; secrets stay in the secret store;
location-aware ordering is validated before money moves; payload capture has an explicit lifecycle instead of growing forever.
**Negative / accepted:** profile schema becomes a contract that adapters depend on; delivery mappings must be maintained as locations change (surfaced in the admin UI as a
warning); YAML-managed deployments must route profile changes through their configuration pipeline rather than the UI.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §7
- ADR-0049, ADR-0051, ADR-0013/0027 (identifiers); platform secrets rule (CLAUDE.md/AGENTS.md)
