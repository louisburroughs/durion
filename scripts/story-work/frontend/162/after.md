## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Workexec: Enforce Purchase Order (PO) Requirement During Estimate Approval

## Primary Persona
Service Advisor

## Business Value
Reduce billing exceptions and rework by preventing estimate approval when customer billing rules mandate a PO, and ensure the captured PO reference is available downstream (e.g., invoice display).

---

# 2. Story Intent

## As a / I want / So that
- **As a** Service Advisor  
- **I want** the Estimate approval flow to require a PO number (and optionally a PO attachment reference) when the customer’s BillingRule mandates it  
- **So that** non-compliant estimates cannot be approved and downstream billing/invoicing has the PO reference.

## In-scope
- Frontend changes to the **Estimate approval step** to:
  - Determine whether PO is required (based on backend enforcement outcome / rule evaluation).
  - Capture `poNumber` and optional `attachmentId`.
  - Block approval client-side where possible and **always** handle backend validation failures deterministically.
  - Display stored PO reference on approved estimate/work order summary views that are already present in the frontend.
- Error handling for rule-unavailable / rule-not-configured and PO validation failures.

## Out-of-scope
- Implementing or changing CRM BillingRule configuration UI.
- Implementing attachment upload/storage system (only capturing an `attachmentId` reference if provided).
- Any backend changes (assumed available per referenced backend story).
- Invoice rendering changes outside of showing PO reference when invoice data already includes it (see Open Questions).

---

# 3. Actors & Stakeholders
- **Primary actor:** Service Advisor (approves estimates, enters PO)
- **Secondary actors:** 
  - Billing/Accounting consumers (read-only visibility of PO on invoice)
  - Admin/support (troubleshoot configuration/rule failures)
- **Systems:** 
  - Workexec backend (system of record for approval + PO reference)
  - CRM backend (rules provider; indirect via workexec)

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated and authorized to approve estimates.
- An Estimate exists and is in an approval-eligible state (backend controls; frontend should reflect).
- Estimate is associated to a customer/account such that workexec can evaluate BillingRule.

## Dependencies
- Backend endpoints (from authoritative references):
  - `POST /api/v1/estimates/{id}/approve?approvedBy={userId}` (existing approval)
  - Workexec enforcement on approval that may return domain errors:
    - `PurchaseOrderNumberMissing`
    - `PurchaseOrderNumberInvalidFormat`
    - `PurchaseOrderNumberTooLong`
    - `BillingRuleUnavailable` (retryable)
    - `BillingRuleNotConfigured`
    - attachment-related errors: `PurchaseOrderAttachmentInvalidId`, `PurchaseOrderAttachmentNotFound`, `PurchaseOrderAttachmentUnauthorized`
- Estimate read endpoint includes `purchaseOrderReference` when present (backend AC-5).
- Moqui frontend has an existing screen for viewing/approving estimates (exact route/screen name unknown; see Open Questions).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From Estimate detail / approval screen used by Service Advisors:
  - Primary action: “Approve Estimate”

## Screens to create/modify
- **Modify** existing Estimate detail/approval screen (Moqui Screen) to add a **PO Reference** section within the approval area.
- **Modify** existing Estimate/Work Order summary screen(s) to display `purchaseOrderReference` (read-only) after approval when present.

## Navigation context
- User is already in a specific Estimate context (`estimateId` in URL parameters).
- After successful approval, user remains on Estimate detail (now Approved) or is redirected to the standard post-approval destination (existing behavior; do not change without confirmation).

## User workflows

### Happy path (PO required)
1. Service Advisor opens Estimate pending approval.
2. System indicates PO is required (either via pre-check if available, or after first failed attempt).
3. Service Advisor enters PO number; optionally enters attachmentId (UUID).
4. Service Advisor clicks Approve.
5. Approval succeeds; status updates to Approved; PO reference shown on the record.

### Happy path (PO not required)
1. Service Advisor approves without entering PO.
2. Approval succeeds; PO reference remains blank/null.

### Alternate path (rule unavailable / not configured)
1. Service Advisor attempts approval.
2. Backend returns rule unavailable/not configured.
3. UI blocks completion, shows actionable message and retry guidance.

---

# 6. Functional Behavior

## Triggers
- User clicks “Approve Estimate” from Estimate approval screen.

## UI actions
- Provide inputs:
  - `poNumber` (string)
  - `attachmentId` (UUID string, optional)
- Provide an “Approve” action that calls backend approve API with PO payload (see Service Contracts).

## State changes (frontend-visible)
- On success:
  - Refresh estimate record data; update displayed status to `APPROVED`.
  - Display saved `purchaseOrderReference`.
- On failure:
  - Keep estimate in pre-approval state in UI (no optimistic status change).
  - Highlight PO inputs if validation-related.
  - Show blocking banner/dialog for rule/config errors.

## Service interactions
- Approval attempt always calls workexec approval endpoint.
- After approval success, fetch latest estimate (or rely on approve response if it returns updated entity; unknown).

---

# 7. Business Rules (Translated to UI Behavior)

## Validation (client-side, non-authoritative)
Client-side validation is **advisory only**; backend is authoritative.

- If UI currently knows PO is required (based on prior backend error or explicit flag if available):
  - `poNumber` must be present and non-empty after trim.
  - `poNumber` must be <= 64 chars.
  - `poNumber` should match regex: `^[A-Za-z0-9][A-Za-z0-9 _./-]{0,63}$`
- If `attachmentId` is provided:
  - Must parse as UUID format; otherwise show inline error and prevent submit.

## Enable/disable rules
- Disable Approve button while the approve request is in-flight.
- If PO is known-required and `poNumber` is invalid, disable Approve until corrected.

## Visibility rules
- PO section is always visible on approval screen, but:
  - Show “Required” indicator only when requirement is known (see Service Contracts / Open Questions).
- On read-only/approved states, show PO values read-only.

## Error messaging expectations (mapping to backend errors)
- `PurchaseOrderNumberMissing` → inline error on PO number: “PO number is required to approve this estimate.”
- `PurchaseOrderNumberInvalidFormat` → inline error: “PO number contains invalid characters.”
- `PurchaseOrderNumberTooLong` → inline error: “PO number must be 1–64 characters.”
- `BillingRuleUnavailable` (503) → blocking banner: “Cannot verify PO requirement right now. Please try again.”
- `BillingRuleNotConfigured` → blocking banner: “Billing rules are not configured for this customer; approval is blocked. Contact an administrator.”
- Attachment errors:
  - `PurchaseOrderAttachmentInvalidId` → inline error: “Attachment ID must be a valid UUID.”
  - `PurchaseOrderAttachmentNotFound` → inline error: “Attachment not found.”
  - `PurchaseOrderAttachmentUnauthorized` → inline error: “You do not have access to that attachment.”

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- `Estimate` (read + approve)
- `PurchaseOrderReference` (nested on estimate/work order):
  - `poNumber: string`
  - `attachmentId: UUID (string)`

## Fields
### Input fields (editable on approval attempt)
- `poNumber`
  - type: string
  - required: conditionally (only when rule requires)
  - default: empty
- `attachmentId`
  - type: string (UUID)
  - required: no
  - default: empty

### Read-only fields
- Estimate status (`DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED` per workexec approval workflow doc)
- Stored `purchaseOrderReference` after approval (read-only unless estimate is editable again—no policy defined here; assume read-only once approved)

## Derived/calculated
- `isPoRequired` (UI-only flag)
  - Derived from either:
    - explicit backend-provided flag on estimate/config (preferred), OR
    - last backend error indicating PO required (fallback, safe).

---

# 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names are unknown; define integration at HTTP API level and implement via Moqui `service-call` / `rest` as per repo conventions.

## Load/view calls
- `GET /api/estimates/{id}`
  - Expected to return estimate including:
    - `status`
    - `customerId` (if needed for display)
    - `purchaseOrderReference` (nullable)
  - If backend also returns an “applicable billing rule” or `requirePurchaseOrderOnEstimateApproval`, use it to set `isPoRequired`. (Unknown; see Open Questions.)

## Submit/transition calls
### Approve estimate
- `POST /api/v1/estimates/{estimateId}/approve?approvedBy={userId}`
- Request body (frontend must send if supported by backend; format must match backend):
  ```json
  {
    "purchaseOrderReference": {
      "poNumber": "string",
      "attachmentId": "uuid-string (optional)"
    }
  }
  ```
  If backend expects flat fields instead (unknown), adapt once clarified.

- Success:
  - HTTP 200/201 (backend doc implies approve returns Estimate; not specified)
  - UI refreshes entity and shows Approved state.

## Error handling expectations
- 400 → validation errors (PO missing/invalid, attachment invalid format)
- 403 → unauthorized
- 404 → estimate not found
- 409 → conflict/concurrency (if estimate changed state between load and approve)
- 503 → billing rule unavailable (retryable)

Frontend must map backend error codes (string identifiers) to the UI messages listed in Business Rules.

---

# 10. State Model & Transitions

## Allowed states (Estimate)
From `CUSTOMER_APPROVAL_WORKFLOW.md`:
- `DRAFT`
- `APPROVED`
- `DECLINED`
- `EXPIRED`

## Allowed transitions relevant to this story
- `DRAFT` → `APPROVED` via approve (this story adds PO enforcement at this boundary)
- (Other transitions exist but not modified here)

## Role-based transitions
- Service Advisor can initiate approval (authorization enforced server-side; UI should hide/disable approve action if backend indicates not permitted—mechanism unknown).

## UI behavior per state
- `DRAFT` (approval-eligible): show PO inputs + Approve action.
- `APPROVED`: show PO reference read-only; hide/disable approval action.
- `DECLINED` / `EXPIRED`: approval action hidden/disabled; PO reference read-only if present (likely null).

---

# 11. Alternate / Error Flows

## Validation failures
- User clicks Approve with missing/invalid PO when required:
  - If client-side knows required: block submit and show inline errors.
  - If client-side doesn’t know required: submit may fail; on backend error, show inline error and set `isPoRequired=true` for subsequent attempts.

## Concurrency conflicts
- If backend returns 409 because estimate status changed (e.g., already approved/declined elsewhere):
  - UI refreshes estimate data and shows “This estimate was updated by another user; please review the current status.”

## Unauthorized access
- 403 on approve:
  - Show blocking message: “You are not authorized to approve estimates.”
  - Keep user on page; do not change state.

## Empty states
- If estimate load returns no `purchaseOrderReference`, display “None” in read-only contexts.

---

# 12. Acceptance Criteria

## Scenario 1: PO required and provided → approval succeeds
**Given** an estimate is in `DRAFT` and backend BillingRule requires a PO  
**And** the Service Advisor enters a PO number matching the allowed format and ≤ 64 chars  
**When** the Service Advisor submits approval  
**Then** the UI calls the approve endpoint including `purchaseOrderReference.poNumber`  
**And** the estimate status updates to `APPROVED` after refresh  
**And** the PO number is visible on the approved estimate screen.

## Scenario 2: PO required and missing → approval blocked with inline error
**Given** an estimate is in `DRAFT` and backend BillingRule requires a PO  
**When** the Service Advisor attempts approval without a PO number  
**Then** approval does not complete  
**And** the UI displays an inline error mapped to `PurchaseOrderNumberMissing`  
**And** the estimate remains not approved in the UI.

## Scenario 3: PO not required → approval succeeds without PO
**Given** an estimate is in `DRAFT` and backend BillingRule does not require a PO  
**When** the Service Advisor approves without providing a PO number  
**Then** the approval succeeds  
**And** the stored `purchaseOrderReference` remains empty/null in the refreshed estimate display.

## Scenario 4: Billing rule unavailable → fail-safe blocks approval and suggests retry
**Given** an estimate is in `DRAFT`  
**When** the Service Advisor attempts approval and the backend cannot retrieve BillingRule (503 / `BillingRuleUnavailable`)  
**Then** the UI shows a blocking message indicating the rule cannot be verified  
**And** the estimate remains unapproved  
**And** the user can retry approval.

## Scenario 5: AttachmentId invalid UUID → client-side blocks submit
**Given** the PO attachment field is available  
**When** the Service Advisor enters an attachmentId that is not a valid UUID  
**Then** the UI shows an inline validation error on attachmentId  
**And** the Approve action is disabled (or submit blocked) until corrected.

## Scenario 6: Approved estimate displays PO reference for downstream visibility
**Given** an estimate has been approved with a `purchaseOrderReference`  
**When** the Service Advisor views the approved estimate (or associated work order summary view where invoice is accessed)  
**Then** the PO number (and attachmentId if present) is displayed read-only.

---

# 13. Audit & Observability

## User-visible audit data
- On approval failure, show a user-facing error message with a stable reason (mapped from backend error code).
- Do not display sensitive internal details (no stack traces, no raw payload hashes).

## Status history
- No new UI for history is required; rely on existing status display.
- If existing screen shows approval metadata, ensure PO reference does not overwrite/obscure it.

## Traceability expectations
- Frontend must include correlation/request ID headers if the project convention exists (unknown here).
- Log (frontend console/debug logger per conventions) the approve attempt outcome with:
  - `estimateId`
  - success/failure
  - backend error code (if any)
  - do **not** log PO number (treat as potentially sensitive business identifier).

---

# 14. Non-Functional UI Requirements

- **Performance:** Approval submit should show loading state immediately; avoid duplicate submits.
- **Accessibility:** Inputs must have labels; inline errors must be associated to fields; keyboard navigable submit.
- **Responsiveness:** PO inputs usable on tablet-size screens (service counter).
- **i18n/timezone/currency:** No new currency calculations. Error strings should be defined in the project’s translation/message system if used (unknown; default to existing pattern).

---

# 15. Applied Safe Defaults

- Default ID: UI-ERG-EMPTYSTATE-01  
  - What was assumed: When no PO reference exists, display “None” rather than blank.  
  - Why it qualifies as safe: Purely presentational; does not change domain behavior.  
  - Impacted sections: UX Summary, Alternate / Error Flows.

- Default ID: UI-ERG-LOADING-01  
  - What was assumed: Disable Approve while request is in-flight and show a loading indicator.  
  - Why it qualifies as safe: Prevents duplicate submissions without affecting business rules.  
  - Impacted sections: Functional Behavior, Non-Functional UI Requirements.

- Default ID: ERR-MAP-STD-01  
  - What was assumed: Standard mapping of HTTP 400/403/404/409/503 to user messages, with domain error code mapping when provided.  
  - Why it qualifies as safe: Error handling is derived from backend contract and improves determinism.  
  - Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows, Acceptance Criteria.

---

# 16. Open Questions

1. **Where in the Moqui frontend is “Estimate approval” implemented (screen path / route / component name)?** Provide the canonical screen(s) to modify so routing/transition metadata is correct.  
2. **Does the estimate GET endpoint expose a flag indicating PO is required (e.g., `requirePurchaseOrderOnEstimateApproval`) or must the UI infer requirement only after a failed approval?**  
3. **What is the exact approve endpoint contract for sending PO reference from frontend?**  
   - Is it a request body with `purchaseOrderReference`, query params, or a different shape?  
4. **How should the optional attachment reference be captured?**  
   - Is there an existing attachment picker/uploader component that returns `attachmentId`, or is this a raw UUID input only?  
5. **“PO stored and visible on invoice”**: which frontend view is considered “invoice” in this repo, and does it already read PO reference from workexec/billing payload? If not, should this story also add invoice display changes or is that a separate story?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Billing: Enforce PO Requirement During Estimate Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/162  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

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


### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*