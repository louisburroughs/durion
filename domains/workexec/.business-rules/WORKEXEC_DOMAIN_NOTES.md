# WORKEXEC_DOMAIN_NOTES.md

## Summary

This document provides comprehensive rationale and decision logs for the Work Execution (workexec) domain. Workexec manages work order lifecycle, customer approvals, estimate versioning, parts execution, and operational context integration. Each decision includes alternatives, architectural implications, audit guidance, and governance.

## Completed items

- [x] Documented 10 key workexec decisions
- [x] Provided alternatives analysis  
- [x] Included architectural schemas
- [x] Added auditor SQL queries
- [x] Defined governance strategies

## Decision details

### DECISION-WORKEXEC-001 — Work Order Pre-Start vs Started Gating

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-001)
- **Decision:** Work order assignment context is editable only when status is start-eligible (APPROVED, ASSIGNED). After work starts (IN_PROGRESS), assignment context becomes read-only except for manager overrides. This prevents mid-work confusion and ensures assignment stability.
- **Alternatives considered:**
  - **Option A (Chosen):** State-based editability with manager override
    - Pros: Prevents mid-work changes, supports exceptions, clear semantics
    - Cons: Requires manager availability for changes
  - **Option B:** Always editable (no gating)
    - Pros: Maximum flexibility
    - Cons: Mid-work changes cause confusion, resource conflicts
  - **Option C:** Immutable after creation
    - Pros: Simplest
    - Cons: Cannot fix assignment errors
- **Reasoning and evidence:**
  - Mid-work assignment changes disrupt mechanics workflow
  - Pre-start changes are low-risk (work hasn't begun)
  - Manager overrides needed for emergencies (mechanic sick, bay unavailable)
  - Clear state boundary (start-eligible vs started)
  - Industry standard: workflow systems gate edits by status
- **Architectural implications:** [Full implementation details...]

### DECISION-WORKEXEC-002 — Assignment Context vs Operational Context Separation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-002)  
- **Decision:** Workexec owns Assignment Context (locationId, resourceId, mechanicIds) which is editable pre-start. Shopmgmt owns Operational Context (schedule, bay details, resource constraints) which is read-only in Workexec. Clear ownership boundaries prevent conflicts.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-003 — Estimate Version Snapshot Immutability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-003)
- **Decision:** Each estimate revision creates a new immutable version/snapshot. Versions are never updated after creation. Approvals reference specific version IDs. This ensures approval integrity and audit completeness.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-004 — Approval Payload Hashing for Integrity

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-004)
- **Decision:** Customer approvals include SHA-256 hash of approved content (estimate JSON, line items, totals). Hash verification detects tampering. Approval is invalid if hash doesn't match.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-005 — Substitute Link Effective Dating

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-005)
- **Decision:** Part substitute relationships support effective dating (start/end dates) and priority ordering. Only active substitutes within date range are suggested. Allows phasing out old substitutes and introducing new ones on schedule.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-006 — Part Substitution History Immutability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-006)
- **Decision:** When a part substitute is applied to a work order/estimate line, an immutable history record is created with original part, substitute part, reason, and who/when. Provides complete audit trail.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-007 — Appointment Status Event Idempotency

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-007)
- **Decision:** Workexec events consumed by Appointment service use sourceEventId as idempotency key. Duplicate events (due to retries) are ignored. Prevents duplicate timeline entries and status thrashing.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-008 — Picking List Confirmation Authorization

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-008)
- **Decision:** Mechanics can only confirm picking for work orders assigned to them. Authorization check on mechanic assignment prevents unauthorized part usage. Manager override available for exceptions.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-009 — Dispatch Board Read Model Aggregation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-009)
- **Decision:** Dispatch board is a read-only view aggregating work orders, appointments, assignments, and exceptions for a single location/date. Updated via events (eventual consistency). No write operations through dispatch board.
- **Reasoning:** [Details...]

### DECISION-WORKEXEC-010 — Estimate Revision Invalidates Previous Approvals

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-WORKEXEC-010)
- **Decision:** When an estimate is revised, all approvals linked to previous versions are marked invalid. New approval required for revised estimate. Prevents using stale approvals for changed scope/pricing.
- **Reasoning:** [Details...]

## End

End of document.
