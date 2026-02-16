---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Audit Trail: Append-only request/plan/execution ledger + query API"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:security
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:security
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As the NLTI platform, I want an append-only audit ledger for requests, plans, confirmations, executions, and tool calls so that NLTI-driven automation is explainable and audit-ready.

## Actors & Stakeholders
- **Primary actor:** NLTI service writing audit entries.
- **Secondary actor:** Admin/support user querying audit trails.
- **Stakeholders:** Security/compliance, SRE, domain owners.

## Preconditions
- CorrelationId is produced for NLTI requests (Capability 01).
- Plan and execution concepts exist (Capabilities 04–06).

## Functional Behavior
1. **Audit Ledger Model**
   - Create versioned audit event types:
     - `NLTI_REQUEST_RECEIVED`
     - `NLTI_INTENT_PARSED`
     - `NLTI_PLAN_CREATED`
     - `NLTI_PLAN_CONFIRMED` / `NLTI_PLAN_CANCELLED`
     - `NLTI_EXECUTION_STARTED`
     - `NLTI_STEP_STARTED`
     - `NLTI_STEP_COMPLETED`
     - `NLTI_STEP_FAILED`
     - `NLTI_EXECUTION_COMPLETED`
   - All events MUST include:
     - `auditId`
     - `timestamp`
     - `correlationId`
     - `sessionId`
     - `userId`
     - `tenantId` (if applicable)
     - `eventType`
     - `payload` (structured JSON)
2. **Append-only Writes**
   - Audit entries are immutable once written.
3. **Query API**
   - Provide a query endpoint (e.g., `GET /nlt/v1/audit`) supporting filters:
     - correlationId
     - userId
     - time range
     - entity references (if present in payload)
4. **PII/Secrets Safety**
   - Do not persist raw secrets.
   - Prompt/body retention is allowed only if safe defaults are met; otherwise store a redacted form and/or hashes.

## Alternate / Error Flows
- Audit storage unavailable → NLTI must fail “closed” for execution (no silent execution without audit), but may allow read-only requests if configured (explicit setting).
- Oversized payload → store trimmed payload + reference to external storage (if available), or reject with clear error.

## Business Rules
- All mutating executions MUST be auditable end-to-end.
- Audit records MUST be queryable by correlationId.
- Audit chain MUST link request → plan → confirmation → execution → step outcomes.

## Data Requirements
- Persistent store for audit events (append-only semantics).
- Index on correlationId and timestamp.
- Optional index on entity reference fields extracted into top-level columns.

## Acceptance Criteria
- **Given** an NLTI request, **when** it is processed through plan and execution, **then** the audit ledger contains a complete chain of events.
- **Given** a correlationId, **when** audit query is invoked, **then** all related events are returned in timestamp order.
- **Given** a step fails, **when** execution stops, **then** the audit ledger includes step failure and execution completion events.
- **Given** prompts include sensitive strings, **when** auditing, **then** stored audit data is redacted per policy.

## Audit & Observability
- Audit itself is the deliverable; additionally:
  - metric: audit_write_failures
  - metric: audit_query_latency_ms
  - logs include correlationId

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Audit & Traceability (Request → Plan → Execution Ledger)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user and auditor, I want NLTI actions and outcomes to be fully traceable so that automated work can be reviewed, explained, and audited.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Security/Compliance, Support/SRE, Domain service owners

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
- Given a correlation ID, when searched, then all related NLTI records are retrievable.