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

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Estimate: Present Customer-Facing Estimate Summary for Review (Snapshot-Based)

## Primary Persona
Service Advisor

## Business Value
Enable Service Advisors to generate a deterministic, customer-facing estimate summary (view/print/share) that matches an immutable snapshot for review prior to seeking customer approval, while enforcing visibility policies and legal/compliance requirements.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Service Advisor  
- **I want** to generate and view a customer-facing estimate summary for a Draft estimate  
- **So that** I can review scope, pricing, taxes/fees, terms, and expiration with the customer before submitting the estimate for approval.

## In-scope
- UI action to generate a customer-facing summary for an estimate in **DRAFT** status.
- Rendering a read-only summary from an **immutable snapshot** created at generation time.
- Enforcing role/policy-based visibility (hide internal-only fields like cost/margin).
- Displaying configured terms/disclaimers and expiration date when applicable.
- Optional next step CTA to proceed to “Submit for Approval” (navigation only; actual approval capture is out of scope unless endpoint exists).

## Out-of-scope
- Editing estimate line items/pricing (only Draft editing elsewhere).
- Creating/changing legal terms configuration or policy configuration UI.
- Implementing approval capture flows (signature, electronic signature, etc.).
- PDF generation implementation details if backend doesn’t provide it (frontend will consume provided format).

---

# 3. Actors & Stakeholders

- **Primary Actor:** Service Advisor
- **Secondary Actors:** Customer (recipient of summary), System (Moqui + backend services)
- **Stakeholders:** Compliance/Legal (terms/disclaimers), Finance (totals correctness), Audit/Security (visibility enforcement and immutable records)

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in POS frontend.
- Estimate exists and is accessible to the current user (authorization enforced server-side).
- Estimate is in status **DRAFT** (per workexec approval workflow).

## Dependencies
- Backend capability to:
  - Validate estimate status and permissions.
  - Generate and persist an **Estimate Summary Snapshot** (immutable), and return snapshot identifier and renderable content or data model.
  - Provide visibility policy outcomes (or already-filtered payload).
  - Provide legal terms/disclaimer text and missing-terms policy behavior (`FAIL` vs `USE_DEFAULTS`), captured in snapshot.
- Moqui screen route(s) to reach estimate detail and summary.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Estimate Detail** screen (Draft estimate): action button/link **“Customer Summary”**.

## Screens to create/modify
1. **Modify**: `EstimateDetail` screen (existing)  
   - Add action to generate/open summary.
2. **Create**: `EstimateSummaryReview` screen  
   - Displays snapshot-based summary (read-only).
   - Provides actions: Print, Download/Export (if supported), Back to Estimate, Proceed to Submit for Approval (if available route).

## Navigation context
- Route pattern (proposed):  
  - `/estimates/:estimateId` → detail  
  - `/estimates/:estimateId/summary` → summary generation + display  
  - Optionally `/estimate-summaries/:snapshotId` if snapshot is first-class.

## User workflows
### Happy path
1. SA opens Draft estimate.
2. Clicks **Customer Summary**.
3. System generates snapshot and loads summary view.
4. SA reviews totals, terms, expiration.
5. SA prints/downloads and discusses with customer.
6. SA optionally clicks **Proceed to Approval** → navigates to approval flow entry.

### Alternate paths
- Estimate not Draft → show error and stay on detail.
- Missing legal terms:
  - Policy FAIL → block generation with clear message.
  - Policy USE_DEFAULTS → generate summary and show banner “Default terms used” (if backend indicates).
- Visibility policy hides internal fields → summary excludes them (no placeholders).

---

# 6. Functional Behavior

## Triggers
- User action: click/tap **Customer Summary** on a Draft estimate.

## UI actions
- Disable the action while request is in-flight.
- On success, navigate to summary screen with `snapshotId` (preferred) or render returned document.

## State changes
- **No estimate state change** (remains DRAFT).
- A new immutable **EstimateSummarySnapshot** is created (backend responsibility), recorded for audit.

## Service interactions
- Call “Generate Estimate Summary Snapshot” service/endpoint (see Service Contracts).
- Then call “Fetch Summary Snapshot/Rendered Summary” if generation endpoint returns only an ID.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Only allow summary generation when estimate status is **DRAFT**.
  - UI may optimistically hide/disable action if estimate status != DRAFT (but server is authoritative).
- Totals displayed (line totals, subtotal, taxTotal, fees, grandTotal) must match snapshot values exactly.

## Enable/disable rules
- “Customer Summary” action:
  - Enabled when estimate status == DRAFT and user has view permission.
  - Disabled otherwise; if clicked via deep link, show appropriate error.

## Visibility rules
- Do not display internal-only fields (e.g., `itemCost`, `profitMargin`, margin %) for unauthorized roles.
- Prefer backend-filtered payload; if backend provides visibility flags, frontend must enforce hiding (defense in depth).

## Error messaging expectations
- Invalid status: “Summary can only be generated for Draft estimates.”
- Missing legal terms with FAIL policy: “Cannot generate estimate summary: Legal terms and conditions not configured.”
- Forbidden: “You don’t have access to this estimate.”
- Not found: “Estimate not found.”
- Generic: “Unable to generate summary. Please try again or contact support.”

---

# 8. Data Requirements

## Entities involved (conceptual, as used by UI)
- `Estimate`
- `EstimateItem` (services/parts/labor lines; exact types TBD)
- `EstimateSummarySnapshot` (immutable)
- `LegalTermsConfiguration` / `MissingLegalTermsPolicy` (as referenced through snapshot metadata)
- `VisibilityPolicy` / role-based field visibility (as applied to snapshot payload)

## Fields (type, required, defaults)
### Estimate (read-only in this story)
- `estimateId` (string/uuid or long; **required**)
- `status` (enum: `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`; **required**)
- `subtotal` (money; required)
- `taxTotal` (money; required)
- `feesTotal` (money; optional if supported; else 0)
- `grandTotal` (money; required)
- `expirationDate` (datetime/date; optional)

### EstimateItem (read-only)
- `description` (string; required)
- `quantity` (decimal; required)
- `unitPrice` (money; required)
- `lineTotal` (money; required)
- Internal-only (must be excluded unless authorized):
  - `itemCost` (money; optional)
  - `profitMargin` (money/percent; optional)

### EstimateSummarySnapshot (read-only)
- `snapshotId` (string/uuid; required)
- `estimateId` (required)
- `snapshotTimestamp` (datetime UTC; required)
- `snapshotData` (json; required — but UI should consume a typed subset)
- `legalTermsSource` (`CONFIGURED` | `DEFAULT` | null; required in snapshot, nullable by schema)
- `legalTermsVersion` (string; optional)
- `legalTermsText` (text; optional depending on policy)
- `policyMode` (`FAIL` | `USE_DEFAULTS`; required)
- `createdBy` (user id; required)
- `auditMetadata` (json; required)

## Read-only vs editable by state/role
- All fields in this story are **read-only**.
- No edits to estimate items, terms, or totals from the summary view.

## Derived/calculated fields
- Per-line display totals and overall totals are **not recalculated by frontend**; displayed values must come from snapshot payload.

---

# 9. Service Contracts (Frontend Perspective)

> Note: Backend endpoints are partially defined in provided references; exact endpoint(s) for summary snapshot are not specified for Moqui. This section defines required frontend-facing contracts; if backend differs, adapt but keep behaviors.

## Load/view calls
1. **Get Estimate (for header/status guard)**
   - `GET /api/estimates/{estimateId}`
   - Used to confirm status and basic info, or rely on existing estimate detail context.

2. **Fetch Summary Snapshot**
   - `GET /api/estimates/{estimateId}/summary-snapshots/{snapshotId}` **OR**
   - `GET /api/estimate-summary-snapshots/{snapshotId}`
   - Returns snapshot metadata + customer-facing display model (preferred) and optionally a rendered HTML/PDF URL.

## Create/update calls
1. **Generate Summary Snapshot**
   - `POST /api/estimates/{estimateId}/summary`
   - Request: `{}` or `{ requestedBy: <userId?> }` (prefer server derives user from auth)
   - Response (201):
     - `snapshotId`
     - `renderFormat` (`HTML` | `PDF` | `DATA`)
     - `summaryUrl` (optional)
     - `snapshot` (optional inline payload)

## Submit/transition calls
- None required for summary generation.
- Optional navigation to approval submission screen; no service call unless that screen requires it.

## Error handling expectations
- `400` validation errors (missing required inputs) → show message.
- `403` forbidden → show access error.
- `404` estimate/snapshot not found → show not found.
- `409` invalid status or policy block (e.g., missing terms + FAIL) → show specific message.
- `5xx` → show generic failure with retry option.

---

# 10. State Model & Transitions

## Allowed states (Estimate)
- `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED` (per workexec approval workflow doc)

## Role-based transitions
- This story performs **no estimate status transition**.
- Generation of summary snapshot is allowed only when estimate is in `DRAFT` (per frontend requirement and backend reference; backend may also allow for `APPROVED` but that would contradict provided frontend trigger—must clarify).

## UI behavior per state
- `DRAFT`: Customer Summary action visible/enabled.
- `APPROVED`/`DECLINED`/`EXPIRED`: action hidden or disabled; deep link shows 409 error message.

---

# 11. Alternate / Error Flows

## Validation failures
- Attempt generate summary when estimate not `DRAFT`:
  - UI shows blocking toast/dialog and does not navigate to summary.
- Missing legal terms:
  - If policy `FAIL`: generation returns error; UI shows compliance-block message.
  - If `USE_DEFAULTS`: generation succeeds; UI shows non-blocking banner “Default terms applied” (only if backend indicates this condition via response metadata).

## Concurrency conflicts
- If estimate updated between detail view and summary generation:
  - Snapshot should capture at generation time; UI displays snapshot timestamp.
  - If backend returns `409` due to concurrent modification/version mismatch (if implemented): show “Estimate changed; please retry.”

## Unauthorized access
- `403`: show access denied and provide navigation back to list.

## Empty states
- If estimate has zero items:
  - If backend blocks generation: show error from backend.
  - If backend allows: summary displays “No line items” and totals (likely zero). (Requires clarification; do not assume.)

---

# 12. Acceptance Criteria

## Scenario 1: Generate summary from Draft estimate (success)
Given I am authenticated as a Service Advisor  
And an Estimate exists with status `DRAFT` and at least one line item  
When I click “Customer Summary” on the estimate  
Then the system creates an immutable estimate summary snapshot  
And I am shown a read-only customer-facing summary based on that snapshot  
And the displayed `grandTotal` equals the snapshot’s `grandTotal`  
And the summary shows the snapshot timestamp.

## Scenario 2: Restricted internal fields are not shown
Given I am authenticated as a Service Advisor  
And an Estimate line item has internal cost/margin fields populated  
When I generate and view the customer-facing summary  
Then the summary does not display internal-only fields (e.g., itemCost, margin)  
And there is no UI control that reveals those fields.

## Scenario 3: Invalid estimate status blocks generation
Given I am authenticated  
And an Estimate exists with status `APPROVED`  
When I attempt to open “Customer Summary” for that estimate  
Then the system responds with a `409` (or equivalent invalid-state error)  
And the UI displays “Summary can only be generated for Draft estimates.”  
And no snapshot is created/persisted by the system.

## Scenario 4: Missing legal terms with FAIL policy blocks generation
Given I am authenticated as a Service Advisor  
And an Estimate exists with status `DRAFT`  
And the active missing-legal-terms policy is `FAIL`  
And legal terms are not configured for the applicable shop/location  
When I generate the customer-facing summary  
Then the system blocks generation and returns an error  
And the UI displays “Cannot generate estimate summary: Legal terms and conditions not configured”  
And the UI does not navigate to a summary view.

## Scenario 5: Missing legal terms with USE_DEFAULTS allows generation (with indication)
Given I am authenticated as a Service Advisor  
And an Estimate exists with status `DRAFT`  
And the active missing-legal-terms policy is `USE_DEFAULTS`  
And legal terms are not configured for the applicable shop/location  
When I generate the customer-facing summary  
Then the system generates the summary using default legal terms  
And the UI indicates that default terms were used (via banner or metadata)  
And the snapshot records `legalTermsSource = DEFAULT` (as visible in response metadata or audit view, if available).

---

# 13. Audit & Observability

## User-visible audit data
- Summary view shows:
  - `snapshotId`
  - `snapshotTimestamp`
  - (If provided) legal terms source/version and policy mode (read-only “Summary generated with configured/default terms”).

## Status history
- No estimate status history changes.
- Snapshot creation is an auditable event and should be viewable in an admin/audit context (not implemented here unless existing component).

## Traceability expectations
- Frontend passes/propagates correlation id if framework supports it (e.g., request header) and logs client-side errors with:
  - `estimateId`, `snapshotId` (if known), HTTP status, error code/message.

---

# 14. Non-Functional UI Requirements

- **Performance:** Summary generation should show loading state; if >2s, show spinner with “Generating summary…”; allow retry on failure.
- **Accessibility:** All actions keyboard accessible; summary content readable with screen readers; print/download buttons have accessible labels.
- **Responsiveness:** Summary view usable on tablet (customer review) and desktop; printable layout should not clip totals/terms.
- **i18n/timezone/currency:** Display money using store currency formatting provided by backend or frontend locale settings; display expiration date and snapshot timestamp in shop-local timezone if provided, otherwise indicate timezone (requires clarification).

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a standard empty-state message on summary screen when no line items are present in returned payload; qualifies as safe because it does not change domain behavior, only presentation. Impacted sections: UX Summary, Alternate/Empty States.
- SD-UX-LOADING-STATE: Standard in-flight disabling and spinner for generate action; qualifies as safe because it’s UI ergonomics only. Impacted sections: Functional Behavior, Non-Functional.
- SD-ERR-HTTP-MAP: Map 403/404/409/5xx to standard error UI patterns; qualifies as safe because it’s generic error handling aligned to HTTP semantics. Impacted sections: Service Contracts, Error Flows.

---

# 16. Open Questions

1. **Backend endpoint contract:** What are the exact Moqui/backend endpoints for (a) generating the estimate summary snapshot and (b) retrieving the snapshot/rendered summary (HTML/PDF/data)?  
2. **Render format:** Should the frontend render from structured JSON (preferred for consistent UI), from server-rendered HTML, or via a PDF URL/download? Can multiple formats be supported?  
3. **Estimate status eligibility:** Is summary generation strictly limited to `DRAFT` only, or also allowed for `APPROVED` (backend reference includes invalid status for approved, but approval workflow allows approved estimates)? Confirm authoritative rule.  
4. **Missing legal terms policy source:** How does frontend determine applicable policy mode (`FAIL` vs `USE_DEFAULTS`)—is it embedded in the generation response only, or is there a readable configuration endpoint?  
5. **Visibility policy enforcement:** Will backend return already-filtered customer-facing payload, or must frontend apply a visibility policy definition? If frontend must apply it, what is the policy schema and source?  
6. **Taxes/fees breakdown:** Are fees a separate field (`feesTotal`) and are tax/fee line breakdowns required in the customer summary? The prompt says “taxes, fees” but the data list omits fee fields.  
7. **Expiration date semantics:** Is `expirationDate` stored on Estimate, derived from configuration, or both? What timezone should be used for display?  
8. **Zero-item estimates:** Should the system allow generating a summary when an estimate has zero line items, or should it block with validation?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Estimate: Present Estimate Summary for Review  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/234  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Present Estimate Summary for Review

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
Service Advisor

## Trigger
A Draft estimate is ready to be reviewed with the customer before approval submission.

## Main Flow
1. User requests a customer-facing summary view/printout.
2. System generates a summary that includes scope, quantities, pricing, taxes, fees, and totals.
3. System excludes restricted internal fields (cost, margin) based on role/policy.
4. System includes configured terms, disclaimers, and expiration date.
5. User shares summary with customer and optionally proceeds to submit for approval.

## Alternate / Error Flows
- Terms/disclaimers not configured → use defaults or block submission depending on compliance settings.

## Business Rules
- Customer summary must be consistent with the estimate snapshot.
- Visibility rules must be enforced for internal-only fields.
- Expiration must be clearly shown if configured.

## Data Requirements
- Entities: Estimate, EstimateItem, DocumentTemplate, VisibilityPolicy
- Fields: displayPrice, taxTotal, grandTotal, termsText, expirationDate, hiddenCostFields

## Acceptance Criteria
- [ ] Customer-facing summary is generated and matches estimate totals.
- [ ] Restricted fields are not displayed to unauthorized roles.
- [ ] Summary includes terms and expiration where configured.

## Notes for Agents
This output becomes the basis for consent text during approval—keep it deterministic.

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