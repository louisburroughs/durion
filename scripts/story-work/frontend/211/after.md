## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:draft

### Recommended
- agent:billing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] Invoicing: Display & Enforce Invoice Traceability Links (Work Order / Estimate / Approvals)

### Primary Persona
Billing Specialist (authorized POS user) / Billing Administrator

### Business Value
Ensure every invoice is defensible and auditable by (a) surfacing immutable source links to executed/approved work and (b) preventing issuance when traceability is incomplete per billing policy, reducing disputes and compliance risk.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Billing Specialist  
- **I want** the invoice UI to show traceability links (work order, estimate version, approval artifacts) captured on the invoice and enforce issuance gating when required links are missing  
- **So that** I can verify the invoice is tied to approved scope and executed work before issuing/collecting payment.

### In-scope
- Add **Traceability** section to invoice view/edit (Draft) screens showing immutable source references captured by Billing.
- Enforce **Draft → Issued** transition gating in UI when traceability policy requirements are not satisfied (based on backend validation + UI precheck).
- Authorized access to view/download approval artifacts from invoice context (via backend-provided links/IDs).
- Error/empty-state handling when traceability is missing or artifacts cannot be fetched.

### Out-of-scope
- Generating the traceability snapshot (backend responsibility).
- Customer-facing invoice document formatting / “include identifiers on invoice PDF/email” configuration and rendering (explicitly separate story).
- Creating/editing estimates, approvals, or work orders.
- Any reconciliation/accounting posting behavior.

---

## 3. Actors & Stakeholders
- **Billing Specialist (primary user):** Reviews invoice traceability and issues invoice.
- **Billing Administrator (privileged user):** Can access more details/artifacts; resolves traceability exceptions.
- **Auditor (stakeholder):** Requires immutable lineage and history.
- **System (Moqui UI + Billing services):** Loads invoice, checks validations, performs transitions.
- **Upstream Work Execution system (stakeholder dependency):** Source of work order/estimate/approval identifiers (already snapshotted onto invoice).

---

## 4. Preconditions & Dependencies
- User is authenticated and authorized to access invoice screens.
- Backend Billing provides invoice detail payload including immutable traceability fields (see **Data Requirements**).
- Backend provides a service endpoint to attempt invoice issuance (Draft → Issued) that:
  - rejects issuance if policy-required traceability is missing
  - returns structured errors (422/409) with reason codes/messages
- Backend provides a service endpoint to fetch approval artifacts (or artifact metadata + secure download URLs) subject to permissions.
- Invoice exists in `DRAFT` or `ISSUED` state; traceability fields are immutable once persisted.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From invoice list: select an invoice → Invoice Detail screen.
- From work order context (if present in app): open linked invoice → Invoice Detail.

### Screens to create/modify
- **Modify**: `InvoiceDetail` screen (draft + issued views) to add **Traceability** panel.
- **Modify**: `InvoiceIssueConfirm` (or existing issue action area/modal) to enforce gating and show blocking reasons.
- **Optional (if pattern exists)**: `ArtifactViewer` screen/dialog for viewing artifact metadata + download/open actions.

> Exact screen names/paths must match repo conventions (Open Question if unknown).

### Navigation context
- Invoice Detail includes:
  - existing invoice summary
  - new Traceability panel with identifiers and links
  - issuance action area (only when in Draft and authorized)

### User workflows

**Happy path: view traceability + issue**
1. User opens a Draft invoice.
2. User reviews Traceability panel:
   - Work order link
   - Estimate version link (if present)
   - Approval record(s) / artifact(s) list (if present)
3. User clicks “Issue Invoice”.
4. UI calls backend issue service; on success navigates/refreshes to `ISSUED` state and shows immutable traceability still visible.

**Alternate: missing optional traceability**
- Estimate/approval data is absent but not required by policy → Traceability panel shows “Not provided” with reason text; issuance allowed.

**Alternate: missing required traceability**
- Required approval/estimate is missing (policy) → UI disables “Issue Invoice” and shows blocking reason; if user attempts via direct action, backend returns validation error and UI displays it.

**Alternate: artifact retrieval failure**
- Artifact list fails to load due to permission/404/timeout → show partial traceability data; issuance behavior depends on policy + backend response.

---

## 6. Functional Behavior

### Triggers
- Invoice Detail screen loads.
- User expands Traceability section.
- User clicks an artifact to view/download.
- User clicks “Issue Invoice”.

### UI actions
- Load invoice details (including traceability snapshot).
- Render traceability identifiers with copy-to-clipboard.
- Provide navigation links (internal route) to related objects **only if** the app has those screens and user is authorized.
- Retrieve artifact metadata and allow open/download if authorized.
- Issue invoice via transition call; handle success/failure.

### State changes
- Invoice status changes from `DRAFT` → `ISSUED` only on successful backend transition.
- No UI-side mutation of traceability fields (read-only always).

### Service interactions
- `InvoiceDetail` load service.
- `InvoiceArtifactList` / `ArtifactGet` service(s) (if separate).
- `InvoiceIssue` transition/service.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Traceability fields are **display-only**; UI must not present inputs to edit:
  - `sourceWorkorderId`
  - `sourceEstimateId` / `sourceEstimateVersionId`
  - `sourceApprovalIds`
  - `sourceSchemaVersion`
- Issuance gating:
  - UI must treat backend as authoritative.
  - UI should pre-check and disable Issue action when invoice detail includes backend-provided “issuance blockers” (if available) **or** when mandatory traceability fields are absent and policy says they’re required (policy source unclear → Open Question).
  - On issuing attempt, must handle backend `422` (validation), `409` (state conflict), `403` (not authorized).

### Enable/disable rules
- “Issue Invoice” button visible only when:
  - invoice status == `DRAFT`
  - user has permission `invoice:issue` (or equivalent)
- “View/Download Artifact” actions visible only when:
  - artifact exists and user has permission `approvalArtifact:view` (name TBD)
  - backend indicates artifact is accessible (e.g., signed URL returned)

### Visibility rules
- Traceability panel visible to authorized roles; if user lacks permission, panel either hidden or shows “Insufficient permissions” (needs product decision → Open Question).

### Error messaging expectations
- If issuance blocked due to missing traceability: show a deterministic message including which link(s) are missing (e.g., “Approval record required but missing”).
- If artifacts missing due to corruption: show blocking banner “Traceability incomplete—contact admin” and prevent issuance (policy-driven; backend should enforce).

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Invoice` (Billing SoR)
- References (read-only identifiers only):
  - Work Order (WorkExec SoR)
  - Estimate / Estimate Version (WorkExec/CRM depending on architecture; treated as reference only)
  - Approval Record / Document Artifact (approval system; treated as reference only)

### Fields (type, required, defaults)

**Invoice (loaded for display)**
- `invoiceId` (string/UUID, required)
- `status` (`DRAFT|ISSUED|PAID|VOID`, required)
- `sourceWorkorderId` (string/UUID, required, read-only)
- `sourceEstimateId` (string/UUID, optional, read-only)
- `sourceEstimateVersionId` (string/UUID, optional, read-only) *(choose one naming; backend shows both patterns → Open Question)*
- `sourceApprovalIds` (array<string/UUID>, optional, read-only)
- `sourceSchemaVersion` (string, required, read-only)
- `traceabilitySummary` (string, optional, read-only) *(if backend provides)*
- `issuanceBlockers` (array of `{code, message, field?}`, optional) *(if backend provides; recommended to avoid UI guessing)*

**Artifact metadata (if supported)**
- `artifactId` (string/UUID)
- `artifactType` (string)
- `title` (string)
- `createdAt` (datetime)
- `createdBy` (string/userRef)
- `downloadUrl` (string, time-limited) OR `contentRef` (string) with separate download call

### Read-only vs editable by state/role
- All traceability fields: read-only always.
- Artifact access: read-only, permission-gated.

### Derived/calculated fields (frontend)
- `traceabilityStatus` (derived display state):
  - `COMPLETE` when required items present
  - `INCOMPLETE` when required items missing
  - `UNKNOWN` when policy info not provided
> This is display-only; must not be used as sole enforcement.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui service names are placeholders; implementer must map to actual services/endpoints used by this repo.

### Load/view calls
1. **Load Invoice Detail**
   - Input: `invoiceId`
   - Output: Invoice fields + traceability snapshot fields listed above
   - Errors:
     - `404` invoice not found → show not found state
     - `403` unauthorized → show access denied

2. **Load Artifact List for Invoice**
   - Input: `invoiceId` (or `sourceApprovalIds[]`)
   - Output: list of artifact metadata; optionally includes secure `downloadUrl`
   - Errors:
     - `403` unauthorized → hide artifact actions; show “Not authorized”
     - `404` artifact missing → mark as missing; if policy requires it, issuance will fail downstream
     - `503` unavailable → show retry option; do not assume artifacts absent

### Submit/transition calls
3. **Issue Invoice**
   - Input: `invoiceId`, optional idempotency token (if frontend participates)
   - Success: updated invoice status `ISSUED` (or redirect to refreshed invoice)
   - Errors:
     - `422` validation (missing required traceability, missing customer billing data, etc.) → render error list; keep in Draft
     - `409` conflict (already issued/voided/changed) → force refresh; show conflict message
     - `503` downstream failure → show non-destructive error and allow retry

### Error handling expectations
- Errors must be displayed in a banner + field-level cues where applicable.
- All service calls should log correlation/trace id if available in response headers (or Moqui context).

---

## 10. State Model & Transitions

### Allowed states (relevant to this story)
- `DRAFT` (editable invoice fields maybe; traceability read-only)
- `ISSUED` (invoice immutable; traceability read-only)
- `PAID`, `VOID` (read-only for this story)

### Role-based transitions
- `DRAFT` → `ISSUED`: requires permission `invoice:issue` and traceability completeness per policy.
- No other transitions in scope.

### UI behavior per state
- **DRAFT**
  - Show Traceability panel
  - Show “Issue Invoice” if permitted and not blocked
- **ISSUED/PAID/VOID**
  - Show Traceability panel
  - Hide issuance action
  - Artifacts remain viewable only if permitted

---

## 11. Alternate / Error Flows

### Validation failures (issuance)
- Missing approval/estimate required by policy:
  - Backend returns `422` with reason code(s)
  - UI displays blocking banner and keeps invoice in Draft

### Concurrency conflicts
- Invoice status changed by another user/session:
  - Backend returns `409`
  - UI prompts “Invoice updated elsewhere. Refreshing…” and reloads detail

### Unauthorized access
- User without permission:
  - Invoice load returns `403` OR panel-level permission denies
  - UI shows access denied (screen) or hides panel/actions (needs decision)

### Empty states
- No estimate/approval IDs present:
  - Show “No estimate linked” / “No approvals linked” with neutral styling
- Artifact list empty but approval IDs exist:
  - Show “Artifacts unavailable” and encourage retry/contact admin

---

## 12. Acceptance Criteria

### Scenario 1: Traceability panel shows immutable links on Draft invoice
**Given** I am an authorized billing user  
**And** an invoice exists in `DRAFT` with `sourceWorkorderId` populated  
**When** I open the Invoice Detail screen  
**Then** I see a Traceability section showing `sourceWorkorderId` and `sourceSchemaVersion` as read-only values  
**And** I see estimate and approval identifiers if present  
**And** there are no editable inputs for traceability fields.

### Scenario 2: Issue action is blocked when backend policy rejects missing traceability
**Given** I am authorized to issue invoices  
**And** an invoice is in `DRAFT`  
**And** required traceability identifiers are missing per billing policy  
**When** I click “Issue Invoice”  
**Then** the UI calls the Issue Invoice service  
**And** the service responds with a validation error (e.g., HTTP 422) indicating missing traceability  
**And** the invoice remains in `DRAFT`  
**And** the UI displays an error banner listing the missing traceability requirement(s).

### Scenario 3: Successful issuance preserves visible traceability
**Given** I am authorized to issue invoices  
**And** an invoice is in `DRAFT` with all policy-required traceability present  
**When** I issue the invoice  
**Then** the invoice transitions to `ISSUED`  
**And** the Traceability section still shows the same immutable identifiers  
**And** the “Issue Invoice” action is no longer available.

### Scenario 4: Artifact access is permission-gated
**Given** an invoice shows one or more approval/artifact references  
**When** I do not have permission to view artifacts  
**Then** artifact download/view actions are not shown (or are disabled)  
**And** I see an “Insufficient permissions” message for artifact access.

### Scenario 5: Artifact retrieval failure does not misrepresent completeness
**Given** an invoice includes approval IDs  
**When** artifact list retrieval fails with a transient error (e.g., 503)  
**Then** the UI shows a retryable error state for artifacts  
**And** it does not claim “no artifacts exist”  
**And** issuance behavior remains determined by the backend issue attempt response.

### Scenario 6: Concurrency conflict on issuance
**Given** I have Invoice Detail open for an invoice in `DRAFT`  
**And** another user issues the invoice  
**When** I attempt to issue the invoice  
**Then** I receive a conflict response (409)  
**And** the UI refreshes invoice data  
**And** I see the invoice in `ISSUED` state with issuance action removed.

---

## 13. Audit & Observability

### User-visible audit data
- Invoice detail should show (if already available in app):
  - `issuedAt`, `issuedBy` (read-only) once issued
  - traceability identifiers as immutable references

### Status history
- If the app has a status history component, ensure issuance adds/refreshes the timeline entry (no new backend assumptions).

### Traceability expectations
- UI must display `sourceSchemaVersion` (workexec contract version) for audit defensibility.
- When issuance is blocked, UI should display backend-provided reason code(s) to support support/audit workflows.

---

## 14. Non-Functional UI Requirements
- **Performance:** Invoice detail load should render skeleton/loading state; artifact list loads asynchronously to avoid blocking main invoice render.
- **Accessibility:** Traceability section must be keyboard navigable; links/buttons have accessible names; error banners announced (ARIA live region).
- **Responsiveness:** Traceability panel readable on tablet-sized POS devices; identifiers should wrap and provide copy action.
- **i18n/timezone/currency:** Not applicable beyond standard timestamp formatting already used by app; do not introduce new currency behavior.

---

## 15. Applied Safe Defaults
- Default ID: UI-EMPTY-STATE-01  
  - Assumed: Standard empty states for missing optional estimate/approval identifiers (“Not provided”).  
  - Why safe: Purely presentational; does not change business policy or state.  
  - Impacted sections: UX Summary, Alternate / Error Flows.
- Default ID: UI-ERROR-HANDLING-01  
  - Assumed: Map HTTP 422/409/403/404/503 to banner messaging + retry where applicable.  
  - Why safe: Standard error mapping; enforcement remains backend-authoritative.  
  - Impacted sections: Service Contracts, Alternate / Error Flows, Acceptance Criteria.
- Default ID: UI-ASYNC-LOAD-01  
  - Assumed: Load artifact list separately after invoice detail to improve perceived performance.  
  - Why safe: Does not alter data or policy; only sequencing of reads.  
  - Impacted sections: Non-Functional UI Requirements, UX Summary.

---

## 16. Open Questions

1. **Backend contract (required):** What are the exact field names returned for estimate reference: `sourceEstimateId` vs `sourceEstimateVersionId` vs both? Provide the canonical response schema used by the frontend.  
2. **Issuance gating UX:** Does the invoice detail payload include an explicit `issuanceBlockers` (or similar) array we should render/interpret, or must the frontend only learn blockers from the issuance attempt (422 response)?  
3. **Permissions:** What are the exact permission IDs/roles for:
   - viewing traceability panel
   - issuing invoice
   - viewing/downloading approval artifacts
4. **Artifact retrieval API:** What service/endpoint returns approval artifacts from invoice context?
   - Input: invoiceId vs approvalIds
   - Output: signed URL vs separate download endpoint
5. **Policy specificity:** Which traceability identifiers are *policy-required* to issue (approval always? estimate version always? configurable per customer/account)? Frontend must not guess.
6. **Navigation links:** Do screens/routes exist for Work Order, Estimate, Approval record in this frontend? If not, should identifiers be copy-only (no links) until those screens exist?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/211


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/211
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder)

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
Invoice draft is generated.

## Main Flow
1. System stores references from invoice to workorder.
2. System stores references from invoice to originating estimate version.
3. System stores references from invoice to approval artifacts/records.
4. System exposes traceability in UI for authorized roles.
5. System includes reference identifiers in customer-facing invoice where configured.

## Alternate / Error Flows
- Origin artifacts missing due to data corruption → block issuance and alert admin.

## Business Rules
- Invoices must be traceable to the approved scope and executed work.

## Data Requirements
- Entities: Invoice, Workorder, Estimate, ApprovalRecord, DocumentArtifact
- Fields: workorderId, estimateId, estimateVersion, approvalId, artifactRef, traceabilitySummary

## Acceptance Criteria
- [ ] Invoice contains links to workorder and estimate/approval trail.
- [ ] Authorized users can retrieve approval artifacts from invoice context.
- [ ] Issuance is blocked if traceability is incomplete (policy).

## Notes for Agents
Traceability is your defense in disputes; enforce it.


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

====================================================================================================