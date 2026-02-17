repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Audit Trail: Append-only request/plan/execution ledger + query API"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:backend
  - agent:security
  - agent:story-authoring
---

## Story Intent (strengthened)
Provide an append-only audit ledger for NLTI request → plan → confirmation → execution events with an efficient query API. Ensure PII and secrets are handled per policy (redaction or hashing) and that audit entries are immutable and queryable by `correlationId`.

## Core Requirements
- Append-only event model with `auditEventId`, `correlationId`, `eventType`, `timestamp`, `userId`, `payload` (redacted as required).
- Query API: `GET /nlt/v1/audit?correlationId=...&from=&to=&eventType=` supporting pagination and ordering.
- Writes must be durable and idempotent from the writer perspective.

## Acceptance Criteria
- All executions produce a chain of audit events linking request→plan→confirmation→execution.
- Query by correlationId returns full event chain in timestamp order.
- Sensitive data not stored in plaintext; oversized payloads are referenced by secure blob store and only small metadata stored in ledger.

## Observability & SRE
- Metric: `nlt.audit.write_failures` and `nlt.audit.query_latency_ms`.
- Alert on audit write failures above threshold; failing audit write disallows destructive execution.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Audit & Traceability (Request → Plan → Execution Ledger)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

## Story Intent
As a Positivity user and auditor, I want NLTI actions and outcomes to be fully traceable so that automated work can be reviewed, explained, and audited.

## Functional Behavior
- Record an audit trail for:
  - NLTI requests
  - Parsed intent
  - Generated plans
  - Confirmations
  - Tool calls and outcomes
- Allow searching by correlation ID and primary business identifiers

## Acceptance Criteria
- Given any NLTI request, when it completes, then there is an audit record chain linking request → plan → execution and step outcomes.
- Given a correlation ID, when searched, then all related NLTI records are retrievable in timestamp order.

