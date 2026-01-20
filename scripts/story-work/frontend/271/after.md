STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Approval: Capture Digital Customer Approval (Estimate + Work Order)

### Primary Persona
Service Advisor (POS user)

### Business Value
Capture a non-repudiable, auditable digital approval (signature) for an Estimate or Work Order so the business can proceed with authorized work and retain legally defensible proof of consent.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** a POS UI flow to collect a customer’s digital signature and submit it as a digital approval for an Estimate or Work Order,  
**so that** the entity transitions to Approved and we retain an immutable approval record for audit and dispute resolution.

### In-scope
- UI entry points from Estimate and Work Order views when an approval is required.
- Signature capture UX (strokes + rendered image) and submission to backend approval endpoints.
- Validation + error handling for invalid state, missing fields, authorization failures.
- Display of approval result (approvalId, timestamp, approver metadata if returned).
- Basic audit visibility: show “Approved” status and link/section for Approval record summary if API provides it.

### Out-of-scope
- Backend implementation (assumed provided by backend story).
- PDF signing / PDF rendering in frontend (only display reference if returned).
- Configuring approval methods (location/customer policy screens).
- Notifications (SMS/email) for electronic signature workflows.
- Editing estimate line items / versioning flows (covered elsewhere).

---

## 3. Actors & Stakeholders
- **Service Advisor (primary actor):** initiates approval capture in POS, guides customer.
- **Customer (external actor):** signs on device.
- **Audit/Compliance stakeholder:** requires immutable traceability and metadata.
- **Billing stakeholder (downstream):** relies on Approved state to proceed.

---

## 4. Preconditions & Dependencies
- User is authenticated in POS.
- A target **Estimate** or **Work Order** exists and is retrievable in the frontend.
- Target entity is in a backend-defined “approval required” state (backend story references `Pending Customer Approval`).
- Backend APIs available (see Service Contracts):
  - `POST /api/v1/estimates/{estimateId}/approvals`
  - `POST /api/v1/work-orders/{workOrderId}/approvals`
- Frontend has (or will add) a signature capture component capable of:
  - capturing stroke vectors (x/y/timestamp)
  - producing a PNG image encoded as Base64
- Routing exists to view Estimate and Work Order details.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Estimate Detail** screen: action “Capture Digital Approval” visible only when estimate status indicates approval is required.
- From **Work Order Detail** screen: action “Capture Digital Approval” visible only when work order status indicates approval is required.

### Screens to create/modify
1. **Modify** `EstimateDetail` screen
   - Add action to open approval capture screen/modal.
2. **Modify** `WorkOrderDetail` screen
   - Add action to open approval capture screen/modal.
3. **Create** a reusable approval capture screen:
   - `component://.../screen/workexec/approval/DigitalApprovalCapture.xml` (name illustrative)
   - Can be used for both entity types via parameters: `entityType`, `entityId`.
4. (Optional) **Create** a lightweight dialog wrapper screen for embedding in detail pages.

### Navigation context
- The approval capture screen must preserve context:
  - “Back to Estimate” or “Back to Work Order”
  - On success, return to originating detail screen and refresh entity data.

### User workflows
**Happy path (Estimate):**
1. Advisor opens Estimate detail in “Pending Customer Approval”.
2. Clicks “Capture Digital Approval”.
3. Customer signs (draws) on device; advisor confirms signer details if required.
4. Advisor submits approval.
5. UI shows success + updates estimate status to Approved.

**Happy path (Work Order):**
Same flow via Work Order detail.

**Alternate paths:**
- Clear signature and re-sign.
- Cancel approval capture and return without changes.
- Submission rejected due to state change (409) → show message and refresh entity.

---

## 6. Functional Behavior

### Triggers
- User clicks “Capture Digital Approval” from Estimate/WorkOrder detail.

### UI actions
- Render signature canvas area and controls:
  - “Clear”
  - “Submit Approval”
  - “Cancel”
- On submit:
  - Validate signature is not empty.
  - Build request payload expected by backend:
    - `customerSignatureData.signatureImage` (Base64 PNG)
    - `customerSignatureData.signatureStrokes` (array) (if required/available)
    - `approvalPayload` (object)
    - (Optional) `intentId` if backend requires anti-replay token (unclear; see Open Questions)
  - Call correct endpoint based on entityType.

### State changes (frontend)
- While submitting: show loading state; disable inputs to prevent double submit.
- On success (201):
  - Show confirmation (toast + inline summary).
  - Navigate back to detail screen and refresh entity.
- On failure:
  - Map HTTP code to user-friendly message and keep user on capture screen (unless unrecoverable like 404).

### Service interactions
- GET (existing) to load entity detail (Estimate/WorkOrder).
- POST approval capture to backend endpoints listed above.
- Optional: GET approval detail endpoint if available (not defined in provided inputs; do not assume).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Signature required**: “Submit Approval” is disabled until signature canvas contains strokes (or a non-empty image can be generated).
- **Entity must be approval-eligible**:
  - If detail screen indicates not in approval-required state, hide action.
  - If user deep-links to capture screen and entity is not eligible, show blocking error and provide navigation back.
- **Immutable approval**: after success, UI must treat approval as final (do not allow “edit approval”).
- **One approval per entity version**: if backend rejects due to already approved/version mismatch, show conflict message (409) and refresh detail.

### Enable/disable rules
- Disable “Submit Approval” while:
  - signature empty
  - submit in progress
- Disable “Clear” while submit in progress.

### Visibility rules
- Capture action visible only when entity status matches “Pending Customer Approval” (exact status string/enums may differ; needs mapping—see Open Questions).

### Error messaging expectations
- 400: “Approval could not be submitted. Please complete required fields and try again.”
- 403: “You don’t have permission to record approvals.”
- 404: “This estimate/work order could not be found.”
- 409: “This estimate/work order is no longer awaiting approval (it may have been approved or changed). Refreshing status.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- **Estimate** (read)
  - `id`
  - `status`
  - (If available) `currency`, `totalAmount`, `lineItemsSummary`, `version`/`documentDigest`
- **WorkOrder** (read)
  - `id`
  - `status`
  - (If available) `currency`, `totalAmount`, `lineItemsSummary`, `version`/`documentDigest`
- **Approval** (write via API; read optional if returned)
  - `approvalId` (UUID)
  - `entityType`
  - `entityId`
  - `approvalTimestamp`
  - `payloadHash`
  - `approverIpAddress` (likely server-derived; display not required)
  - `approverUserAgent` (server-derived; display not required)
  - `pdfSignatureReference` (optional)

### Fields captured in UI (with types)
- `entityType`: enum `{Estimate, WorkOrder}` (required; passed via route params)
- `entityId`: string/UUID (required; passed via route params)
- `customerSignatureData.signatureImage`: string Base64 PNG (required)
- `customerSignatureData.signatureStrokes`: array of `{x:number, y:number, t:number}` or similar (optional unless backend requires)
- `approvalPayload`: JSON object (required; exact schema unclear—see Open Questions)
- `intentId`: string (optional; unclear—see Open Questions)
- `signerUserId`: string/number (unclear whether customer vs advisor identity; see Open Questions)

### Read-only vs editable
- Entity identifiers and computed approval payload fields are read-only.
- Signature is editable until submitted.
- Post-submit, all fields are read-only and screen navigates away or locks.

### Derived/calculated fields
- Rendered PNG image derived from strokes/canvas.
- If UI constructs `approvalPayload` from entity details (amount, currency, digest), these are derived and should be displayed in a “You are approving:” summary.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend story uses `/api/v1/...` paths; frontend must align exactly. If current frontend uses a different base path/proxy, adapt via existing API client config.

### Load/view calls
- `GET /api/v1/estimates/{estimateId}` (assumed exists because listing/get is in customer approval workflow doc)
- `GET /api/v1/work-orders/{workOrderId}` (not explicitly listed in provided docs; dependency)

### Create/update calls (approval capture)
- `POST /api/v1/estimates/{estimateId}/approvals`
  - **Request body** (minimum):
    ```json
    {
      "customerSignatureData": {
        "signatureImage": "<base64-png>",
        "signatureStrokes": [ /* optional */ ]
      },
      "approvalPayload": { /* required */ }
    }
    ```
  - **Response**: `201 Created` with `approvalId` (exact response shape unspecified)

- `POST /api/v1/work-orders/{workOrderId}/approvals`
  - Same shape as above.

### Submit/transition calls
- The POST implicitly transitions entity status to `Approved` on success.

### Error handling expectations
- 400 Bad Request: missing required fields, integrity mismatch
- 403 Forbidden: authorization failure
- 404 Not Found: entity missing
- 409 Conflict: invalid state (already approved, in progress, etc.)
- 5xx: show generic error + allow retry

---

## 10. State Model & Transitions

### Relevant states (from provided backend story text)
For approval target entity (Estimate/WorkOrder), the UI must handle at least:
- `Pending Customer Approval` → `Approved` (via digital approval)
- Rejection cases where entity is in:
  - `Approved`
  - `In Progress`
  - `Denied`
  - `On Hold`
  - `Transferred`

### Role-based transitions
- Service Advisor initiates approval capture.
- Backend enforces permissions; frontend should not attempt to replicate RBAC logic beyond hiding controls when possible.

### UI behavior per state
- **Pending Customer Approval**: show “Capture Digital Approval”
- **Approved**: hide capture action; show approval summary if available
- **Other non-eligible states**: hide capture action; if user navigates directly, show “Not eligible for approval” message

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Empty signature → inline error “Signature is required.”
- Unable to generate PNG from canvas → block submit, show “Unable to capture signature, please try again.”

### Backend validation failures
- 400 missing fields → show backend validation message if provided; otherwise generic.
- 400 integrity error (payload hash mismatch / tampering) → show “Approval payload validation failed. Please reload and try again.”

### Concurrency conflicts
- Another user approves while this user is signing → POST returns 409; UI must:
  1) show conflict message
  2) provide “Refresh” action that reloads entity and exits capture screen if no longer eligible

### Unauthorized access
- 403 → show permission error; provide navigation back to detail.

### Empty states
- Entity detail load fails (404) → show not found; disable approval capture and offer return to list.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Capture digital approval for Work Order (success)
Given a Work Order with id "WO-123" is in status "Pending Customer Approval"  
And the Service Advisor is on the Work Order detail screen  
When the user selects "Capture Digital Approval"  
And the customer provides a non-empty signature  
And the user submits the approval  
Then the system sends a POST request to `/api/v1/work-orders/WO-123/approvals` containing `customerSignatureData.signatureImage` and `approvalPayload`  
And the UI displays a success confirmation  
And the Work Order detail is refreshed and shows status "Approved"

### Scenario 2: Capture digital approval for Estimate (success)
Given an Estimate with id "EST-456" is in status "Pending Customer Approval"  
When the user captures a non-empty signature and submits approval  
Then the system POSTs to `/api/v1/estimates/EST-456/approvals`  
And the UI refreshes the Estimate detail to show status "Approved"

### Scenario 3: Prevent submit when signature is empty
Given the user is on the digital approval capture screen for a pending entity  
When the signature area is empty  
Then the "Submit Approval" action is disabled  
And attempting to submit shows "Signature is required."

### Scenario 4: Backend rejects due to invalid state (409)
Given a Work Order "WO-456" is already in status "Approved"  
When the user submits a digital approval to `/api/v1/work-orders/WO-456/approvals`  
Then the UI shows a conflict message indicating the work order is no longer awaiting approval  
And the UI offers to refresh and returns to the Work Order detail screen

### Scenario 5: Backend rejects due to incomplete payload (400)
Given a Work Order "WO-123" is in status "Pending Customer Approval"  
When the user submits approval without required `customerSignatureData` fields  
Then the UI displays a validation error  
And the Work Order status remains unchanged in the UI after refresh

### Scenario 6: Unauthorized approval attempt (403)
Given the current user lacks permission to record approvals  
When they submit a digital approval  
Then the UI shows a permission error  
And no local state is marked as approved

---

## 13. Audit & Observability

### User-visible audit data
- After successful approval, show at minimum:
  - “Approved” status
  - approval timestamp (if returned or from refreshed entity)
  - approvalId (if returned)
  - approval method = “Digital Signature” (label in UI)

### Status history
- If Work Order / Estimate detail includes a “history” section, it should reflect the status change after refresh.
- No new history UI is required unless existing pattern exists.

### Traceability expectations
- Frontend should include correlation/request id headers if the project standard supports it (do not invent header names; use existing API client conventions).

---

## 14. Non-Functional UI Requirements

- **Performance:** signature capture must remain responsive; avoid heavy re-renders while drawing.
- **Accessibility (WCAG 2.1):**
  - Provide keyboard-accessible controls for Clear/Submit/Cancel.
  - Provide text alternative explaining how to sign.
  - Ensure sufficient contrast and focus indicators.
- **Responsiveness:** works on tablet resolution and desktop; signature area scales appropriately.
- **i18n/timezone/currency:** display amounts/currency in entity summary using existing formatting utilities (no new currency rules invented).

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions

1. **Status mapping:** What are the exact backend status values for “Pending Customer Approval” and “Approved” for **Work Orders** and **Estimates** in the POS UI payloads? (Needed to correctly show/hide entry points and handle deep links.)
2. **ApprovalPayload schema:** What is the exact required shape of `approvalPayload` for the POST endpoints (required keys: `originType`, `originId`, `documentDigest`/`originVersion`, `amount`, `currency`, `lineItemsSummary`, `signerUserId`, `timestamp`)? Provide example request/response bodies.
3. **Intent/anti-replay:** Does the frontend need to request or include an `intentId` (single-use token) before submitting approval? If yes, what endpoint issues it and what is its TTL?
4. **Work Order endpoints:** The workexec docs list `/api/v1/work-orders/{workOrderId}/approvals` but other docs use `/api/workorders/{id}/start`. What is the canonical base path and naming for work order APIs in this environment?
5. **Signature strokes requirement:** Are stroke vectors required by backend or optional? If required, what coordinate system and timestamp format should be used?
6. **Signer identity:** Is `signerUserId` the **customer** identifier, the **advisor**, or omitted/derived? If customer, how is the customer identity represented/authenticated for in-store signing?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Approval: Capture Digital Customer Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/271  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #20 - Approval: Capture Digital Customer Approval  
**URL**: https://github.com/louisburroughs/durion/issues/20  
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:37:51.888477534*