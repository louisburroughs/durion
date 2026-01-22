# ACCOUNTING_DOMAIN_NOTES.md

*(Non-Normative — Exploratory & Rationale)*

---

## Document Status

**Status:** Living design and rationale document  
**Normative Authority:** ❌ None  
**Binding Rules:** See `AGENT_GUIDE.md`  
**Audience:** Architects, governance reviewers, senior engineers, auditors  
**Change Frequency:** Moderate

This document captures **why** accounting decisions were made, what alternatives were considered, and what risks remain.  
It preserves institutional memory without contaminating executable policy.

---

## Architectural Goals

1. **Financial correctness over convenience**
2. **Auditability over UI simplicity**
3. **Clear system-of-record boundaries**
4. **Agent-safe determinism**
5. **Operational visibility without policy inference**

These goals bias decisions toward:

* Explicit user actions
* Immutable records
* Conservative defaults
* Backend-authoritative enums and states

---

## Accounting Domain Philosophy

Accounting in Durion is treated as:

* A **financial interpreter**, not an operational controller
* A **consumer of events**, not a producer of truth for upstream domains
* A **custodian of audit trails**, not a workflow engine

This leads to several recurring patterns:

* Read models for foreign-domain actions (payments, refunds)
* Explicit commands for financial state changes
* Strong separation between ingestion and application

---

Below is a **clean, deterministic assignment of Decision IDs in `AGENT_GUIDE.md`**, followed by an **updated cross-reference section for `ACCOUNTING_DOMAIN_NOTES.md`**.

This preserves the **normative / non-normative split** while making governance traceable and auditable.

---

# Part 1 — Decision IDs for `AGENT_GUIDE.md` (Normative)

These IDs are **authoritative**.  
They must be treated as stable references for CI, agents, and audits.

---

## Decision Index (Authoritative)

| Decision ID | Title                                    |
| ----------- | ---------------------------------------- |
| **AD-001**  | Refund System of Record                  |
| **AD-002**  | Payment Receipt vs AR Reduction          |
| **AD-003**  | Overpayment Handling via Customer Credit |
| **AD-004**  | Manual Customer Assignment on Payments   |
| **AD-005**  | Timekeeping Export Mode                  |
| **AD-006**  | Identifier Strategy (UUIDv7)             |
| **AD-007**  | Ingestion Monitoring Read Model          |
| **AD-008**  | Correlation & Trace Standard             |
| **AD-009**  | Raw Payload Visibility Policy            |
| **AD-010**  | Apply Payment Idempotency Model          |
| **AD-011**  | Posting Reference Canonical Identifier   |
| **AD-012**  | Accounting Period Enforcement            |
| **AD-013**  | Permission Gating Model                  |
| **AD-014**  | Asynchronous Retry Semantics             |
| **AD-015**  | Timezone Semantics for Exports           |

---

## How These Appear in `AGENT_GUIDE.md`

In the **normative guide**, decisions should be tagged inline like this:

```md
### Refunds (System of Record)
**Decision: AD-001**

Payment domain is the system of record for refund execution.
Accounting maintains a read-only RefundTransaction derived from RefundIssued events.
```

or for rules:

```md
### Receivables Application Rules
**Decision: AD-002, AD-003, AD-010**

Payment receipt does not reduce AR.
Only PaymentApplication records reduce AR.
Overpayments create CustomerCredit and set unappliedAmount to zero.
Apply operations require frontend-generated UUIDv7 idempotency keys.
```

> No rationale text appears in AGENT_GUIDE — only the Decision ID.

---

# Part 2 — Cross-Referenced `ACCOUNTING_DOMAIN_NOTES.md`

Below is the **updated Decision Log section**, rewritten to **explicitly reference AGENT_GUIDE Decision IDs**.

This section **replaces** the existing “Decision Log (Resolved)” in `ACCOUNTING_DOMAIN_NOTES.md`.

---

## Decision Log (Resolved, Cross-Referenced)

---

### **AD-001 — Refund System of Record**

**Normative Source:** `AGENT_GUIDE.md` (AD-001)

**Decision:**  
The **Payment domain** is the system of record for refund execution.  
Accounting maintains a **read-only RefundTransaction read model** derived from events.

**Rationale:**  
Refund execution is processor-dependent and operational. Accounting must reflect refunds without mutating them.

**Rejected Alternatives:**

* Accounting owning refund lifecycle
* Dual-write refund state across domains

---

### **AD-002 — Payment Receipt vs AR Reduction**

**Normative Source:** `AGENT_GUIDE.md` (AD-002)

**Decision:**  
Payment receipt does **not** reduce AR.  
AR reduction occurs only through explicit PaymentApplication records.

**Rationale:**  
Cash receipt and application are distinct accounting events. Conflation causes audit ambiguity.

---

### **AD-003 — Overpayment Handling via Customer Credit**

**Normative Source:** `AGENT_GUIDE.md` (AD-003)

**Decision:**  
Overpayments create **CustomerCredit**.  
Negative invoice balances are forbidden.

**Rationale:**  
Credits preserve reporting integrity and align with GAAP-style AR systems.

---

### **AD-004 — Manual Customer Assignment on Payments**

**Normative Source:** `AGENT_GUIDE.md` (AD-004)

**Decision:**  
Manual customer assignment:

* Allowed once
* Requires justification
* Immutable afterward

**Rationale:**  
Prevents silent reassignment of cash and enforces accountability.

---

### **AD-005 — Timekeeping Export Mode**

**Normative Source:** `AGENT_GUIDE.md` (AD-005)

**Decision:**  
Timekeeping exports are **asynchronous only**.

**Rationale:**  
Large datasets, auditability, and retry visibility outweigh UX convenience.

---

### **AD-006 — Identifier Strategy (UUIDv7)**

**Normative Source:** `AGENT_GUIDE.md` (AD-006)

**Decision:**  
All primary identifiers are UUIDv7.

**Rationale:**  
Time-sortable, collision-safe, uniform validation across layers.

---

### **AD-007 — Ingestion Monitoring Read Model**

**Normative Source:** `AGENT_GUIDE.md` (AD-007)

**Decision:**  
Accounting exposes a first-class ingestion monitoring read model.

**Rationale:**  
Operational visibility is required even when no domain entity is created.

---

### **AD-008 — Correlation & Trace Standard**

**Normative Source:** `AGENT_GUIDE.md` (AD-008)

**Decision:**  
System uses W3C Trace Context (`traceparent`, `tracestate`).

**Rationale:**  
Industry standard, tool-agnostic, compatible with distributed tracing.

---

### **AD-009 — Raw Payload Visibility Policy**

**Normative Source:** `AGENT_GUIDE.md` (AD-009)

**Decision:**  
Raw payload JSON is restricted by permission; default view is redacted summary.

**Rationale:**  
Prevents PII leakage and log contamination.

---

### **AD-010 — Apply Payment Idempotency Model**

**Normative Source:** `AGENT_GUIDE.md` (AD-010)

**Decision:**  
Frontend generates UUIDv7 `applicationRequestId` and reuses on retry.

**Rationale:**  
Prevents double application under retries or network failure.

---

### **AD-011 — Posting Reference Canonical Identifier**

**Normative Source:** `AGENT_GUIDE.md` (AD-011)

**Decision:**  
`journalEntryId` is the canonical posting reference.  
Ledger IDs are secondary.

**Rationale:**  
Journal entries represent the auditable accounting action.

---

### **AD-012 — Accounting Period Enforcement**

**Normative Source:** `AGENT_GUIDE.md` (AD-012)

**Decision:**  
All postings and applications are blocked in closed periods.

**Rationale:**  
Preserves period integrity and prevents retroactive mutation.

---

### **AD-013 — Permission Gating Model**

**Normative Source:** `AGENT_GUIDE.md` (AD-013)

**Decision:**  
Permissions are action-scoped and least-privilege.  
Viewing ≠ executing.

**Rationale:**  
Reduces blast radius of compromised roles.

---

### **AD-014 — Asynchronous Retry Semantics**

**Normative Source:** `AGENT_GUIDE.md` (AD-014)

**Decision:**  
All retries execute as async jobs.

**Rationale:**  
Avoids long-running HTTP requests and supports audit tracking.

---

### **AD-015 — Timezone Semantics for Exports**

**Normative Source:** `AGENT_GUIDE.md` (AD-015)

**Decision:**  
Exports interpret dates in **location timezone**.

**Rationale:**  
Operational data is location-anchored, not user-anchored.

---

## Governance Rule (Restated)

> **If a statement in `ACCOUNTING_DOMAIN_NOTES.md` conflicts with a Decision ID in `AGENT_GUIDE.md`, the Decision ID wins.**

---

## Tradeoffs & Rejected Alternatives

### Auto-Allocation of Payments

**Rejected because:**

* Masks user intent
* Complicates reversals
* Creates hidden policy in backend

Explicit allocation is slower but safer.

---

### Editable Application Dates

**Rejected because:**

* Violates accounting period controls
* Introduces backdating risk
* Complicates audit narratives

---

### Displaying Raw Payloads by Default

**Rejected because:**

* PII leakage risk
* Log contamination risk
* Overwhelms most users

Payload summaries are the default; raw access is exceptional.

---

## Cross-Domain Tensions

### Accounting vs Billing

* Billing owns invoice issuance
* Accounting owns posting and AR effects

**Risk:**  
UI confusion if issuance and posting are assumed to be atomic.

---

### Accounting vs Payment

* Payment owns transaction lifecycle
* Accounting owns financial interpretation

**Risk:**  
Users assume refunds or reversals “happen” in accounting.

---

### Accounting vs Timekeeping

* Timekeeping owns approval
* Accounting owns export

**Risk:**  
Users attempt to export unapproved data.

---

## UI/UX Implications (Non-Binding)

* Users must be shown **why** actions are unavailable (status-driven UI)
* Overpayment outcomes must be explicit (“Credit created”)
* Ingestion screens must emphasize *status*, not *blame*

---

## Audit & Compliance Commentary

The design intentionally supports:

* SOX-style traceability
* Clear before/after state narratives
* Minimal implied intent

Every financial mutation:

* Has a user
* Has a timestamp
* Has a justification or source event
* Is immutable after creation

---

## Future Capabilities (Explicitly Non-Normative)

These are **not commitments**:

* Payment application reversal workflows
* Credit reapplication flows
* Multi-currency AR
* Export preview / dry-run mode
* Automated suspense resolution

They are listed here to **avoid re-litigating past decisions** later.

---

## Risk Register

| Risk                                  | Mitigation               |
| ------------------------------------- | ------------------------ |
| User frustration from strict rules    | Clear UI messaging       |
| Over-reliance on ingestion monitoring | Training & runbooks      |
| Payload visibility misuse             | Strong permissions       |
| Domain boundary erosion               | Enforced via AGENT_GUIDE |

---

## How This Document Should Be Used

✅ Acceptable uses:

* Architecture discussions
* Auditor explanations
* Future design work
* Governance review

❌ Forbidden uses:

* Direct agent input
* CI validation
* Story execution rules

---

## Authority Reminder

> **If this document contradicts `AGENT_GUIDE.md`, it is wrong.**

That is intentional.

---

## End

End of document.

---

# Addendum — New Consolidated Stories: Design Rationale & Institutional Memory (Non-Normative)

This addendum captures **new architectural pressure** introduced by consolidated frontend stories (CoA, posting configuration, posting rule sets, journal entry review, AP vendor bills, AP workflow actions, and an ingestion submit tool). It does **not** create new binding rules; it records tradeoffs, risks, and why we are intentionally blocked on certain items.

## Why these stories matter to the architecture

Across the stories, a consistent theme emerges:

1. **Accounting is becoming a configuration-heavy domain** (CoA, posting categories/keys/mappings, posting rule sets).
2. **Accounting is also becoming an operational observability surface** (ingestion monitoring, ingestion submit tool, JE review, AP bill traceability).
3. **The frontend must not invent contracts** (endpoints, permission tokens, enums, event types, dimension schemas). This is a deliberate “safe defaults” posture aligned with AD-013 and the broader governance model.

This combination creates a predictable tension:

* Finance users want “just let me edit it” workflows.
* Audit and determinism require **append-only/versioned** configuration and **explicit commands**.
* Operators want powerful tools (submit events, view payloads), but security requires **deny-by-default** and **redaction-first**.

## Pattern: “Configuration as Code” vs “Configuration as Data”

The posting configuration stories (CoA, posting categories/mappings, posting rule sets) implicitly push toward two models:

* **Configuration as Data**: editable records in the system with audit fields.
* **Configuration as Code**: versioned JSON definitions (rule sets) with publish/validate semantics.

We are intentionally allowing both patterns, but with guardrails:

* “Data” configuration (CoA, categories, mappings) should still behave like controlled configuration:
  * immutable identifiers
  * limited mutable fields
  * effective dating rather than deletion
* “Code-like” configuration (posting rule sets) should behave like release artifacts:
  * DRAFT → PUBLISHED → ARCHIVED
  * publish-time validation
  * traceability from journal entry to exact version

**Institutional memory:** We have repeatedly been burned in other systems by “editable in place” accounting configuration. It creates audit ambiguity (“what rules were in effect when this posted?”). The stories are correctly pushing us toward versioning and effective dating, but the backend contract must make this explicit.

## Pattern: “Read-only review UIs” as the default for financial artifacts

Several stories are explicitly read-only:

* Draft Journal Entry review UI
* Vendor Bill created-from-event review UI

This is consistent with the philosophy: accounting is a **custodian of audit trails**, not a workflow engine.

**Tradeoff:** Read-only UIs frustrate ops users who want to “fix it now.”  
**Mitigation:** Provide:

* traceability identifiers (eventId, ingestionId, journalEntryId, rule version)
* clear status and error codes
* links to the *correct* system-of-record or configuration screens

This reduces “shadow corrections” and keeps the audit narrative coherent.

## Pattern: “Operator tools” must be treated as privileged surfaces

Issue #207 introduces an ingestion submit tool: a UI that can POST a canonical event envelope to a sync ingestion endpoint and show acknowledgements.

This is operationally valuable, but it is also a high-risk capability:

* It can be used to inject events (even if only into lower environments).
* It can accidentally expose payloads.
* It can become a de facto “replay engine” if not constrained.

**Why we still want it:** It provides deterministic end-to-end validation of:

* auth failures
* schema validation
* idempotency behavior
* conflict/quarantine behavior
* trace propagation

**Key tradeoffs and mitigations (non-normative):**

* Prefer environment scoping (e.g., enabled only in non-prod) *if governance allows*.
* Require explicit permissions for:
  * viewing the tool
  * submitting events
  * viewing raw payload echoes (AD-009)
* Never persist payloads in Moqui; keep in-memory only (as the story proposes).
* Ensure the backend is authoritative for idempotency/conflict semantics; UI only normalizes display.

## Pattern: “Traceability-first UI” is becoming a product requirement

Across stories, users need copyable identifiers:

* `traceparent` (AD-008)
* `eventId` (AD-006)
* `ingestionId` (AD-007)
* `journalEntryId` (AD-011)
* rule set id + version (posting rule sets)
* billId/vendor refs (AP)

This is not just observability; it is **audit workflow enablement**. It reduces time-to-triage and prevents “screen-scrape debugging.”

**Risk:** Overexposing identifiers can leak existence across permission boundaries.  
**Mitigation:** Follow AD-013: do not reveal existence on 403/404; only show identifiers that the user already supplied or that were returned in an authorized response.

## Pattern: “Dimensions” are a major unresolved complexity

Multiple stories reference “dimensions” on GL mappings and JE lines (business unit, location, department, cost center, etc.). This is a known hard problem:

* Dimensions vary by organization and reporting needs.
* Some are required for certain posting categories but not others.
* Some are references; others are freeform codes.
* Validation rules can be complex and time-dependent.

**Institutional memory:** If we guess dimensions in the frontend, we will hardcode policy and break determinism. Dimensions must be backend-described (schema or metadata endpoint) or strictly opaque.

**Pragmatic approach (non-normative):**

* Treat dimensions as an opaque map for display until a schema is defined.
* Prefer backend-provided “dimension display names” and “required/optional” metadata per posting category.

## Pattern: “System-of-record boundaries” are under pressure in AP

The AP stories introduce workflow transitions (approve, schedule payment) and vendor bill creation from events.

This raises a boundary question:

* Is AP bill workflow owned by Accounting, Billing, or a dedicated AP module?
* Payment execution is explicitly owned by Payment domain, but scheduling is ambiguous.

**Why we are blocked:** Without a clear SoR, we risk:

* dual-write status transitions
* inconsistent audit trails
* unclear event ownership (e.g., `Accounting.PaymentScheduled.v1`)

**Mitigation:** Require explicit backend ownership and event contracts before implementing UI transitions.

---

## Addendum — Decision Log (New Patterns Identified)

These are **not new normative decisions**; they are recorded here because the stories reveal recurring patterns that likely deserve formalization in `AGENT_GUIDE.md` later.

### Candidate Decision (Not Yet Assigned): Versioned Configuration Artifacts

**Observed in:** Posting Rule Sets story (#202), Posting Categories/Mappings story (#203), CoA story (#204)

**Proposed principle:**  
Accounting configuration that affects posting should be either:

* effective-dated and non-overlapping (mappings), or
* versioned with publish/validate semantics (rule sets), and
* always traceable from resulting journal entries.

**Why:** Prevents “configuration drift” and supports audit narratives.

**Rejected alternative (implicit):** Editable-in-place configuration without version history.  
**Why rejected:** Cannot reliably answer “what rules were in effect when this posted?”

**Risk:** Increased UX complexity (more screens, more “create new version” flows).  
**Mitigation:** Provide clear UI affordances and history views; default to “clone latest published.”

### Candidate Decision (Not Yet Assigned): Privileged Operator Surfaces Must Be Explicitly Scoped

**Observed in:** Ingestion submit tool story (#207)

**Proposed principle:**  
Operator tools that can inject or replay events must be:

* permission gated (view vs submit vs payload view),
* non-persistent for payloads,
* safe in error messaging (no existence leaks),
* trace-propagating.

**Why:** These tools are powerful and can become an attack surface.

**Rejected alternative:** “Just expose the endpoint and let operators use curl.”  
**Why rejected:** Loses auditability, increases error rate, and encourages ad hoc sharing of payloads.

### Candidate Decision (Not Yet Assigned): Read-only First for System-Generated Financial Artifacts

**Observed in:** Draft JE review (#201), Vendor Bill review (#194)

**Proposed principle:**  
System-generated artifacts are reviewed, not edited, unless a dedicated “adjustment” workflow exists.

**Why:** Editing generated artifacts breaks traceability and creates hidden policy.

**Rejected alternative:** Allow inline edits to “fix” a JE or bill.  
**Why rejected:** Creates unbounded audit risk and unclear responsibility.

---

## Addendum — Known Issues / Open Questions (Consolidated)

This section consolidates open questions from the stories into a domain-level backlog. Many are **blocking** because they touch contracts, permissions, or SoR boundaries (all denylisted for invention).

### A. Ingestion Submit Tool (Issue #207)

1. **Sync ingestion submit endpoint contract (blocking):** exact path and success response schema (ack fields, idempotency outcome fields, conflict semantics).  
2. **Permissions (blocking, AD-013):** explicit tokens for:
   * viewing the submit screen
   * submitting events
   * viewing raw payload echoes (must align with AD-009)  
3. **Payload shape constraint (blocking):** does backend accept any JSON value or object-only? If object-only, confirm errorCode.

### B. Chart of Accounts / GLAccount (Issue #204)

1. **Backend endpoints/services (blocking):** list/detail/create/update/deactivate contracts, field names, pagination/sort.  
2. **Permissions (blocking, AD-013):** explicit tokens for view vs manage.  
3. **Deactivation policy (blocking):** what blocks deactivation and stable error codes.  
4. **Editing inactive accounts (blocking):** allowed or immutable?  
5. **Status filtering support (blocking):** server-side derived status filtering or UI-only badges?  
6. **Optimistic locking (blocking):** ETag/version semantics and conflict response codes.

### C. Posting Categories / Mapping Keys / Effective-Dated GL Mappings (Issue #203)

1. **Backend contracts (blocking):** endpoints/services and error shapes, especially overlap conflict details.  
2. **Permissions (blocking, AD-013):** view vs manage vs auditor read-only (if distinct).  
3. **Dimensions schema (blocking):** authoritative list/types/requiredness and lookup semantics.  
4. **Immutability/versioning policy (blocking):** editable-in-place vs append-only/versioned for categories/keys/mappings.  
5. **Overlap handling policy (clarification):** always reject overlaps vs auto-end-date prior mapping.  
6. **Resolution test endpoint (optional):** mappingKey + date resolution endpoint and permissions.

### D. Posting Rule Sets (Issue #202)

1. **Backend endpoint family (blocking):** list/detail/versionDetail/create/createVersion/publish/archive contracts.  
2. **Permissions (blocking, AD-013):** view/create/publish/archive tokens.  
3. **EventType source (blocking):** authoritative catalog endpoint/service for recognized EventTypes.  
4. **RulesDefinition schema/editor (blocking):** JSON schema or structured editor contract.  
5. **Version creation semantics (blocking):** baseVersion required? concurrency behavior and error shapes.  
6. **Archiving policy (blocking):** can archived be base? unarchive? restrictions.  
7. **List optimization (non-blocking):** “latest version summary” availability.  
8. **Validation detail payload shape (blocking):** structure for unbalanced/invalid references.

### E. Draft Journal Entry Review (Issue #201)

1. **Backend services/contracts (blocking):** search/list and detail (header + lines) and optional audit history.  
2. **Permissions (blocking, AD-013):** explicit token(s) for viewing journal entries.  
3. **Field naming (blocking):** `sourceEventId` vs `eventId`, `mappingRuleVersionId` naming, dimension fields.  
4. **Rounding/scale (blocking):** how UI should compare debits/credits per currency (exact vs rounded; scale source).  
5. **Failure visibility (clarification):** should UI expose quarantined/failed events or is that separate ingestion tooling?

### F. AP Vendor Bills Created from Events (Issue #194)

1. **Backend contracts (blocking):** list/detail endpoints/services, traceability fields, ingestion visibility embedding vs linkage, posting refs.  
2. **Permissions (blocking, AD-013):** view vendor bills, view linked journal entry, payload summary vs raw payload policy.  
3. **Origin event taxonomy (blocking):** canonical upstream event types that create vendor bills.  
4. **Vendor bill status enum (blocking):** canonical statuses for filtering and review.  
5. **JE navigation (optional):** route and permission to link to JE detail.

### G. AP Approve & Schedule Payments (Issue #193)

1. **System-of-record ownership (blocking):** which domain/service owns AP bill workflow transitions.  
2. **Moqui routing conventions (blocking):** canonical screen paths/menu placement.  
3. **Backend contracts (blocking):** load/approve/schedule endpoints/services, error codes, permission tokens.  
4. **Event contract (clarification):** whether `Accounting.PaymentScheduled.v1` exists and its ownership (avoid inventing).

### H. Additional open-question clusters (from truncated consolidated list)

The consolidated “Open Questions” section includes additional clusters (manual JE, reconciliation, ingestion retry tooling) that appear to be separate stories not fully included in the truncated input. They are recorded here as **known gaps** because they touch core accounting controls:

* **Manual JE creation/posting:** reason codes, idempotency, period closed errors, unbalanced errors, permissions, currency rules.  
* **Reconciliation:** permissions, endpoint family, import formats, matching cardinality, adjustment types, report contract, currency handling.  
* **Ingestion retry tooling:** status model, retry eligibility, operator reason/audit requirements, payload masking, navigation conventions.

**Institutional memory note:** These areas are historically where “policy inference” creeps into UI. They should be treated as backend-owned policy surfaces with explicit contracts and stable error codes.

---

## Addendum — Risk Assessment Updates (Story-Driven)

This extends (does not replace) the existing Risk Register.

| Risk | Why it surfaced now | Mitigation (non-normative) |
|---|---|---|
| Configuration drift breaks auditability | Posting rules/mappings/CoA are being added as UI-managed config | Prefer versioning/effective dating; require traceability from JE to config version; avoid in-place edits without history |
| Privileged operator tools become an injection surface | Ingestion submit UI can generate/submit events | Strict permission gating (view vs submit vs payload); environment scoping; no payload persistence; safe error messaging |
| Dimension schema ambiguity causes hardcoded policy | Multiple stories reference “dimensions” | Require backend schema/metadata; treat as opaque map until defined; avoid frontend-requiredness rules |
| Existence leaks via 403/404 differences | Many screens deep-link by ID | Standardize “not found or not authorized” messaging where required; do not show record existence hints |
| UI balance checks disagree with backend | JE review requires per-currency balancing | Define rounding/scale contract; display backend-provided totals if available; otherwise compute with explicit scale rules |
| Domain boundary erosion (AP workflow) | Approve/schedule introduces workflow ownership ambiguity | Require explicit SoR and event ownership; avoid dual-write; keep UI blocked until contracts exist |

---

### Authority Reminder (Repeated)

> **If this document contradicts `AGENT_GUIDE.md`, it is wrong.**

That is intentional.
