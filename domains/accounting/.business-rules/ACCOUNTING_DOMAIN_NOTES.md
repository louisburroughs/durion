
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
