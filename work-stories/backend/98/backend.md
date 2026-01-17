Title: [BACKEND] [STORY] Billing: Enforce PO Requirement During Estimate Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/98
Labels: type:story, domain:workexec, status:ready-for-dev, agent:story-authoring, agent:crm, agent:workexec, agent:billing

## Story Intent
As a Service Advisor, I want Estimate approval to enforce a Purchase Order (PO) requirement when the customer’s CRM BillingRule mandates it, so non-compliant estimates cannot be approved and downstream billing has the PO reference available.

## Actors & Stakeholders
- **Primary actor:** Service Advisor
- **Workflow owner / SoR for Estimate approval + PO reference:** Workorder Execution (`domain:workexec`)
- **Rules/config provider:** CRM (`domain:crm`) provides BillingRule as a versioned read contract
- **Downstream consumer:** Billing (`domain:billing`) consumes PO reference from Workexec (read-only)

## Preconditions
- A Work Order / Estimate exists in `Pending Approval`.
- Work Order is linked to a customer/account identifier usable to query CRM BillingRule.
- Caller is authorized to approve estimates.

## Functional Behavior
### 1) Approve Estimate (Workexec-owned)
- Trigger: Service Advisor invokes `ApproveEstimate` for a Work Order/Estimate.
- Workexec retrieves the effective CRM BillingRule for that customer using a versioned CRM API.
- Workexec evaluates `requirePurchaseOrderOnEstimateApproval`.

### 2) Enforcement when PO is required
- If BillingRule indicates PO is required:
  - Workexec validates `purchaseOrderReference.poNumber` per validation rules below.
  - If `attachmentId` is provided, Workexec validates it as a reference to the attachment/document system (existence + authorization).
  - If any validation fails, approval is blocked and the Estimate remains `Pending Approval`.

### 3) Successful approval
- If PO is not required, or all required PO validation passes:
  - Workexec persists `purchaseOrderReference` on the Estimate/Work Order.
  - Workexec transitions Estimate state to `Approved` atomically with persistence.
  - Workexec publishes `EstimateApproved` including the full `purchaseOrderReference` (if present).

## Alternate / Error Flows
- Missing PO when required → block approval; return error code `PurchaseOrderNumberMissing`.
- Invalid PO number format → block approval; return `PurchaseOrderNumberInvalidFormat`.
- PO number too long → block approval; return `PurchaseOrderNumberTooLong`.
- CRM BillingRule unavailable (timeout/5xx/network) → **fail-safe**: block approval; return `503` (or domain error `BillingRuleUnavailable`).
- CRM returns 404 (no BillingRule configured) → block approval as configuration error; return `BillingRuleNotConfigured`.
- AttachmentId invalid / not found / unauthorized → block approval with `PurchaseOrderAttachmentInvalidId` / `PurchaseOrderAttachmentNotFound` / `PurchaseOrderAttachmentUnauthorized`.

## Business Rules
- **Primary domain ownership (Decision):** Workexec owns the Estimate approval state transition and the enforcement logic that blocks/permits approval.
- **System of record (Decision):** Workexec is the system of record for `PurchaseOrderReference` attached to an Estimate/Work Order.
- **CRM contract (Decision):** Workexec queries CRM via a **versioned HTTP API** for BillingRule.
- **Failure mode (Decision):** fail-safe (block approval) if BillingRule cannot be determined.

## Integration Contract (CRM BillingRule)
- Versioned endpoint example: `GET /api/v1/billing-rules/{customerId}`
- Response includes:
  - `customerId`
  - `requirePurchaseOrderOnEstimateApproval` (boolean)
  - `effectiveFrom` (timestamp)
  - `version` (integer)

## Data Requirements
### Workexec entity
- `purchaseOrderReference` (nullable complex type)
  - `poNumber` (string)
  - `attachmentId` (UUID, optional)

### PO Number validation (Decision)
- Required only when BillingRule requires it.
- Trim whitespace; reject if empty after trim.
- Length: 1..64 characters.
- Allowed characters: A–Z, a–z, 0–9, space, and `- _ / .`.
  - Regex example: `^[A-Za-z0-9][A-Za-z0-9 _./-]{0,63}$`
- No uniqueness constraints assumed.

### AttachmentId validation (Decision)
- `attachmentId` is a foreign key/reference to the attachment/document system (not free text, not a URI).
- If provided, must be valid UUID format and must exist and be authorized.

## Acceptance Criteria
- **AC-1: PO required and provided → approval succeeds**
  - Given BillingRule requires PO
  - When Service Advisor provides a valid `poNumber` and approves
  - Then Estimate transitions to `Approved` and Workexec persists `purchaseOrderReference`.

- **AC-2: PO required and missing → approval blocked**
  - Given BillingRule requires PO
  - When approval is attempted without `poNumber`
  - Then approval is rejected, Estimate remains `Pending Approval`, and error `PurchaseOrderNumberMissing` is returned.

- **AC-3: PO not required → approval succeeds without PO**
  - Given BillingRule does not require PO
  - When approval is attempted without `poNumber`
  - Then approval succeeds and `purchaseOrderReference` remains null.

- **AC-4: CRM unavailable → fail-safe blocks approval**
  - Given approval is attempted
  - When CRM BillingRule cannot be retrieved due to timeout/5xx/network
  - Then approval is blocked and a retryable error is returned.

- **AC-5: Downstream visibility via Workexec**
  - Given an Estimate is approved with `purchaseOrderReference`
  - When a downstream consumer reads the approved Work Order (API/event)
  - Then `purchaseOrderReference` is included in the payload.

## Audit & Observability
- Audit every approval attempt with `workOrderId`, result, and failure reason (e.g., `PO_REQUIRED_BUT_MISSING`, `BILLING_RULE_UNAVAILABLE`).
- Metrics counters for approve attempts and PO validation failures (tagged by reason).
- Publish `EstimateApproved` on success; include `workOrderId`, `customerId`, and `purchaseOrderReference` if present.

## Open Questions
- None. Decisions were supplied in the issue comments (Decision Record generated by `clarification-resolver.sh` on 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #98 — [BACKEND] [STORY] Billing: Enforce PO Requirement During Estimate Approval

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Billing: Enforce PO Requirement During Estimate Approval

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **the system to require a PO when account rules mandate it** so that **billing exceptions are reduced**.

## Details
- PO required triggers validation in Estimate approval step.
- Capture PO number and optional attachment reference.

## Acceptance Criteria
- Approval blocked without PO when required.
- PO stored and visible on invoice.

## Integration Points (Workorder Execution)
- Workorder Execution checks CRM billing rules and enforces PO before approval.

## Data / Entities
- PurchaseOrderRef (WO domain)
- CRM BillingRule reference

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*