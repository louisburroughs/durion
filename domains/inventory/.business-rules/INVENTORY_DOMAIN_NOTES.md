
# INVENTORY_CONTROL_DOMAIN_NOTES.md — Inventory Control Domain (Non-Normative)

## Intended uses

- Architecture discussions
- Auditor explanations
- Future design work
- Governance review

## Forbidden uses

- Direct agent input
- CI validation
- Story execution rules

This document explains the rationale and tradeoffs behind the normative Inventory Control decisions in `AGENT_GUIDE.md`.

---

## INV-DEC-001 — Canonical Location Model: `LocationRef` as Site; `StorageLocation` as Bin

### Why this matters

Inventory systems fail when “site” and “bin” get mixed. The frontend needs stable mental models:

- Site = “business location boundary” (HR-owned lifecycle, staffing, compliance)
- Bin = “operational storage topology” inside a site (inventory-owned)

### Tradeoff

- Treating `siteId` as a synonym of `locationId` simplifies UI and contracts, but requires migration discipline and story hygiene (avoid reintroducing `siteId`).

### Auditor explanation

“We can show where stock was located at the business location level and within internal bin hierarchy; both have stable identifiers and lifecycle rules.”

---

## INV-DEC-002 — Integration Pattern: Moqui Service Proxy

### Rationale

Moqui proxy services reduce fragmentation:

- centralized auth/session handling
- consistent error mapping and correlation propagation
- easier to swap inventory backend hostnames/versions without redeploying Vue logic

### Tradeoffs

- Slightly increased latency (one more hop)
- Requires disciplined Moqui service definitions and stable internal naming

### Auditor explanation

“Access control and logging for inventory data access can be centralized and uniformly enforced.”

---

## INV-DEC-003 — API Envelopes & Error Schema

### Rationale

A plain JSON approach avoids “envelope drift” across screens and prevents duplicate parsing logic.
A deterministic error schema helps:

- map field errors to forms
- present actionable messages
- attach correlation IDs for support without exposing stack traces

### Future design

If a global envelope is later adopted, it should be implemented in a single adapter and not proliferate into component code.

---

## INV-DEC-004 — Availability Contract (deep-linking, success-with-zeros)

### Why “success with zeros” matters

Most parts will have no local history. Treating “no ledger history” as an error causes:

- false alarms
- wasted time
- user distrust

### Deep-linking rationale

Service advisors and managers often need “bookmarkable” availability views. Deep-linking improves speed during phone calls and counter workflows.

### SLA rationale

The UX must feel responsive even under poor connectivity:

- quick spinner
- deterministic timeout
- no infinite loops

---

## INV-DEC-005 — Ledger Contract (filters, location semantics, pagination, immutability)

### Why OR semantics for location

Users think “show me movements involving this location,” not “only outbound from this location.”
OR semantics match investigator intent and reduce user error.

### Why cursor pagination

Ledger tables can be large. Cursor paging:

- scales better
- avoids inconsistent results under concurrent writes
- reduces total-count pressure on backend

### Auditor explanation

“Ledger records are immutable and append-only; corrections are separate entries, preserving provenance.”

---

## INV-DEC-006 — Adjustment Workflow Scope (create-only in v1)

### Rationale

Adjustment approvals are governance-heavy:

- separation of duties
- manager review
- audit narratives and exception handling

Create-only v1 enables operational continuity while deferring approval workflows to a dedicated story set.

### Future design

Add:

- staged adjustment request
- approval step
- posting job
- immutable audit trail of who approved and why

---

## INV-DEC-007 — StorageLocation CRUD + Deactivation

### Why destination-required deactivation exists

Deactivating a non-empty bin without transferring stock creates “ghost inventory.”
Destination-required semantics preserve physical integrity.

### Why storageType is immutable

Changing bin semantics after usage breaks analytics and SOPs (“this used to be a hazardous cage, now it’s a tire rack”).

### Inactive editability rationale

Allowing metadata fixes supports operations (barcode misprint) without reopening for movements.

---

## INV-DEC-008 — HR Sync Contracts + Manual Sync

### Rationale

HR is upstream truth, but operations need:

- visibility into sync outcomes
- ability to reconcile when HR updates lag or events fail

Manual “sync now” is admin-only to prevent abuse.

### Auditor explanation

“We can demonstrate when a location roster change was ingested and how it affected movement eligibility.”

---

## INV-DEC-009 — Inactive/Pending Selection Rules (movement blocking)

### Why treat PENDING as inactive

PENDING often indicates incomplete onboarding:

- not fully staffed
- not fully configured
- risk of misrouting stock

### Why define “movement screens”

Selection-blocking is easy to miss when new flows appear. An explicit list reduces regression risk.

---

## INV-DEC-010 — Permission Naming Convention

### Rationale

Stable, action-scoped permissions allow:

- least privilege
- clear role design
- straightforward UI gating

Future roles can map to these permissions without refactoring the UI.

---

## INV-DEC-011 — Sensitive Data & Logging Policy

### Why quantities are sensitive

Inventory positions can be commercially sensitive (fleet contracts, parts shortages).
Logs persist and are hard to purge; treat quantities as sensitive-by-default.

### Practical guidance

Log request metadata and correlation IDs; never log payload blobs or quantities.

---

## INV-DEC-012 — Correlation/Request ID Convention

### Rationale

A single correlation header makes cross-service debugging feasible without exposing internals.
401/403 behaviors prevent cached-content leakage between sessions.

---

## INV-DEC-013 — Availability Feed Ops Ownership & Contracts

### Rationale

Even if ingestion is orchestrated elsewhere, operational teams need one place to:

- see run outcomes
- triage unmapped parts
- resolve exceptions

Inventory is the natural consumer and interpreter of availability feeds.

### Auditor explanation

“We retain evidence of feed runs and exception resolution actions.”

---

## INV-DEC-014 — Deep-Link Parameter Names & Routing Conventions

### Why canonical names matter

Once links are shared (email, tickets, SOP documents), changing param names creates operational churn and support cost.

---

## INV-DEC-015 — JSON Field Handling & Safe Rendering

### Rationale

JSON fields often carry:

- vendor metadata
- operator notes
- upstream payload snippets

Safe rendering prevents XSS and accidental disclosure. Truncation prevents UI lockups.

---

# End of INVENTORY_CONTROL_DOMAIN_NOTES.md
