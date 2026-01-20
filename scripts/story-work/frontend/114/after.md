## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** pricing-strict

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Pricing: View Immutable Pricing Snapshot Drilldown from Estimate/WO Line (Moqui UI)

### Primary Persona
Service Advisor (primary), Accountant/Auditor (secondary, read-only)

### Business Value
Enable users to **inspect and explain historical line pricing** via an immutable snapshot drilldown so that later pricing rule changes do not create confusion, disputes, or audit gaps.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to open a read-only “Pricing Snapshot” drilldown from an Estimate/Work Order line  
- **So that** I can explain how the price was calculated at the time the line was created and trust that the data is immutable.

### In-scope
- Moqui frontend screens to **retrieve and display** a `PricingSnapshot` by `snapshotId`.
- A drilldown entry point from an Estimate line and/or Work Order line UI (link/button) that opens the snapshot view.
- Read-only presentation of snapshot fields: prices at time, cost-at-time, MSRP-at-time, applied rules trace, timestamps, policy identifiers/versioning.
- Error handling UX when snapshot cannot be loaded (not found, unauthorized, service down).
- Basic permission gating in UI (show/hide drilldown action and handle 403).

### Out-of-scope
- Creating snapshots (triggered on quote/booking) and enforcing immutability (backend responsibility).
- Editing/updating/deleting snapshots (explicitly forbidden).
- Workexec line creation flow changes (e.g., “hard-fail add line if snapshot creation fails”) unless already implemented elsewhere.
- Any visualization beyond structured read-only drilldown (charts, exports) unless specified.
- Accounting reporting consumption.

---

## 3. Actors & Stakeholders
- **Service Advisor:** needs to understand and justify line price.
- **Shop Manager:** may review overrides/rules applied.
- **Accountant/Auditor (read-only):** needs traceability for audits.
- **System (Pricing service via Moqui):** serves snapshot data.

---

## 4. Preconditions & Dependencies
- An Estimate or Work Order line exists in Workexec UI and has a non-empty `pricingSnapshotId` (aka `snapshotId`) associated.
- Pricing backend exposes a snapshot retrieval endpoint (reference backend story indicates):  
  `GET /pricing/v1/snapshots/{snapshotId}` returning full breakdown.
- Authentication is configured for Moqui to call the Pricing API.
- Authorization policy exists for who may view snapshots (permission name TBD; see Open Questions).

Dependencies:
- Pricing backend story: `[BACKEND] [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line` (durion-positivity-backend #50) for API semantics and response shape.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Estimate detail** screen: each line item row exposes an action “View pricing snapshot”.
- From **Work Order detail** screen: each line item row exposes an action “View pricing snapshot”.

### Screens to create/modify
1. **New Screen:** `apps/pos/screen/pricing/snapshot/PricingSnapshotView.xml` (or workspace-standard path)  
   - Purpose: read-only drilldown for a single snapshot.
2. **Modify Screen(s):**
   - Estimate detail screen (Workexec) to add drilldown action if `pricingSnapshotId` present.
   - Work Order detail screen (Workexec) to add drilldown action if `pricingSnapshotId` present.

> Note: exact screen locations/paths in this repo are unknown from inputs; implementer should align with existing Durion frontend Moqui screen structure.

### Navigation context
- Drilldown opens either:
  - in a **dialog** (preferred for quick return), or
  - in a dedicated route `/pricing/snapshots/{snapshotId}` with “Back to document” navigation.
- The view must display document context if provided by the API (`sourceContext` JSON) but must not require it.

### User workflows
**Happy path**
1. User views an estimate/WO.
2. User clicks “View pricing snapshot” on a line.
3. System loads snapshot.
4. User sees immutable pricing breakdown and applied rule trace.

**Alternate paths**
- If line has no snapshot ID: action hidden/disabled with tooltip “No snapshot available”.
- If API returns 404: show “Snapshot not found” and guidance to contact support.
- If 403: show “Not authorized to view pricing details”.
- If network/service unavailable: show “Pricing snapshot service unavailable; try again”.

---

## 6. Functional Behavior

### Triggers
- User clicks drilldown action for a document line with `pricingSnapshotId`.

### UI actions
- Fetch snapshot by `snapshotId`.
- Render read-only sections:
  - Snapshot metadata
  - Price components
  - Applied rules trace
  - Policy/version identifiers
  - Raw context (optional collapsible JSON)

### State changes
- No domain state changes (read-only).  
- Local UI state: loading, loaded, error.

### Service interactions
- Moqui calls Pricing API to retrieve snapshot (see Service Contracts).
- No caching policy specified (safe default: allow short-lived client-side caching within the view instance only).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `snapshotId` must be present and non-empty before attempting drilldown call.
- `snapshotId` must be treated as opaque identifier (no parsing).

### Enable/disable rules
- Drilldown action:
  - **Enabled** only when `pricingSnapshotId` exists on the line.
  - **Disabled/hidden** when missing.

### Visibility rules
- If user lacks required permission (TBD), hide the action or show disabled with “Insufficient permissions”.
- On 403 from API, display an authorization error regardless of UI gating (defense in depth).

### Error messaging expectations
- Errors must be actionable and non-technical:
  - 404 → “Pricing snapshot not found.”
  - 403 → “You don’t have permission to view this pricing snapshot.”
  - 5xx/timeout → “Pricing snapshot service is unavailable right now. Please try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Workexec Document Line** (EstimateLine / WorkOrderLine or equivalent)
  - Must contain `pricingSnapshotId` (string/UUID).
- **PricingSnapshot** (from Pricing API)
  - `snapshotId` (UUID/string)
  - `createdAt` (timestamp with timezone)
  - `sourceContext` (JSON object; optional)
  - `itemIdentifier` (string; SKU/part#/labor op)
  - `quantity` (number)
  - `prices` (JSON object; must include at least `finalPrice`; may include `msrp`, `cost`, etc.)
  - `appliedRules` (array of rule trace objects)
  - `policyVersion` (string)
  - Currency fields: **unclear** whether returned inside `prices` or separately (see Open Questions)

### Fields (type, required, defaults)
Because the frontend contract is API-driven and response schema is not fully specified in inputs, the UI must:
- Treat unknown fields as optional.
- Render required minimum:
  - `snapshotId` (required to view)
  - `createdAt` (required; if missing display “Unknown”)
  - `prices.finalPrice` (required; if missing show “Unavailable” and log client error)

### Read-only vs editable
- All snapshot fields are strictly **read-only**.

### Derived/calculated fields
- Extended price display (`unit * quantity`) must **not** be calculated unless explicitly provided by API (pricing formulas are denylisted for safe defaults). If API provides both unit and extended, display both; otherwise display only provided values.

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
- **Operation:** Retrieve snapshot
- **HTTP:** `GET /pricing/v1/snapshots/{snapshotId}`
- **Inputs:**
  - `snapshotId` (path)
- **Success (200):**
  - Response JSON representing snapshot including pricing breakdown, rule outcomes, versioning, timestamps (per backend reference).
- **Not Found (404):**
  - Show snapshot-not-found UX.
- **Unauthorized/Forbidden (401/403):**
  - 401 → prompt re-auth (existing app behavior)
  - 403 → show not-authorized message; do not retry automatically.
- **Transient failure (408/5xx/network):**
  - Show service unavailable message with “Retry” action.

### Create/update calls
- None (out of scope; snapshots immutable).

### Submit/transition calls
- None.

### Error handling expectations
- Must propagate correlation/request IDs if Moqui provides them (log in console/server logs per project convention).
- Do not display raw stack traces or technical payloads to user.

---

## 10. State Model & Transitions

### Allowed states
UI state machine for snapshot view:
- `idle` (no snapshot selected)
- `loading`
- `loaded`
- `error:notFound`
- `error:forbidden`
- `error:unavailable`

### Role-based transitions
- If user lacks permission, transition directly to `error:forbidden` upon API response.

### UI behavior per state
- `loading`: show spinner/skeleton; disable close/back only if modal pattern requires.
- `loaded`: show content.
- `error:*`: show message + actions:
  - notFound: close/back
  - forbidden: close/back
  - unavailable: retry + close/back

---

## 11. Alternate / Error Flows

### Validation failures
- Missing `snapshotId` on line:
  - Drilldown action hidden/disabled.
  - If user somehow navigates directly to view with missing/invalid ID, show “Invalid snapshot reference” and do not call API.

### Concurrency conflicts
- Not applicable (read-only). If snapshot deleted (should not happen), treat as 404.

### Unauthorized access
- 401/403 handled as in Service Contracts; ensure no data renders from prior snapshot after a forbidden response (clear state on error).

### Empty states
- `appliedRules` empty or missing: show “No pricing rules were applied” (wording must not imply discount policies).
- Missing optional fields: display “Not provided” rather than guessing.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Open snapshot drilldown from a Work Order line (happy path)
**Given** I am an authenticated Service Advisor  
**And** a Work Order line displays a non-empty `pricingSnapshotId`  
**When** I select “View pricing snapshot” for that line  
**Then** the system requests `GET /pricing/v1/snapshots/{pricingSnapshotId}`  
**And** I see the snapshot’s `snapshotId` and `createdAt`  
**And** I see the snapshot’s final price value as provided by the API  
**And** I see an applied rules section showing each returned rule trace item (or an empty-state message if none)

### Scenario 2: Drilldown action is unavailable when a line has no snapshot id
**Given** I am viewing an Estimate with line items  
**And** a line item has an empty or null `pricingSnapshotId`  
**Then** the “View pricing snapshot” action for that line is not available (hidden or disabled)  
**And** the UI does not attempt to call the snapshot API for that line

### Scenario 3: Snapshot not found (404)
**Given** I navigate to the snapshot view for snapshotId `S-DOES-NOT-EXIST`  
**When** the Pricing API responds with HTTP 404  
**Then** the UI shows a “Pricing snapshot not found” error state  
**And** no stale snapshot data is displayed

### Scenario 4: Not authorized (403)
**Given** I attempt to view a pricing snapshot  
**When** the Pricing API responds with HTTP 403  
**Then** the UI shows a “Not authorized to view pricing snapshot” message  
**And** the UI does not show any snapshot details

### Scenario 5: Pricing service unavailable (timeout/5xx)
**Given** I attempt to view a pricing snapshot  
**When** the snapshot request times out or returns HTTP 5xx  
**Then** the UI shows “Pricing snapshot service unavailable”  
**And** I can retry the request  
**And** on retry success the snapshot details render

---

## 13. Audit & Observability

### User-visible audit data
- Display `createdAt` and `policyVersion` (and any other version identifiers returned) as audit context.

### Status history
- Not applicable (snapshot is immutable; no lifecycle in UI).

### Traceability expectations
- Log (frontend/Moqui server logs per convention):
  - `snapshotId`
  - calling document context (estimateId/workOrderId, lineId) if available in UI state
  - HTTP status for failures
- Ensure correlation ID propagation if available from Moqui request context.

---

## 14. Non-Functional UI Requirements
- **Performance:** snapshot view should render within 1s after API response; show loading indicator immediately.
- **Accessibility:** drilldown action reachable via keyboard; dialog/screen has accessible title; error states announced to screen readers.
- **Responsiveness:** snapshot view usable on tablet widths typical for POS.
- **i18n/timezone/currency:**  
  - Display timestamps in user’s locale/timezone per app standard.  
  - Currency formatting must use currency code from API; if absent, do not guess currency (see Open Questions).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for missing `appliedRules` and optional fields; safe because it does not alter domain logic. Impacted sections: UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-LOADING-STATE: Standard loading indicator during API call; safe UI ergonomics only. Impacted sections: State Model, UX Summary.
- SD-ERR-HTTP-MAP: Map 401/403/404/5xx to standard user-safe messages; safe because it follows HTTP semantics without inventing business rules. Impacted sections: Service Contracts, Error Flows.

---

## 16. Open Questions
1. **API schema:** What is the exact response payload for `GET /pricing/v1/snapshots/{snapshotId}` (field names and currency representation)? Specifically: are prices `unitPrice` vs `extendedPrice` provided, and what is the currency code field name?
2. **Permissions:** What permission/role gates viewing a snapshot in the UI? Is it a dedicated permission (e.g., `pricing:snapshot:view`) or inherited from document access?
3. **Entry point scope:** Should the drilldown be added to **both** Estimate and Work Order line UIs in this story, or only one (and which is priority)?
4. **Navigation pattern:** Should snapshot drilldown open as a modal dialog or a dedicated route/screen? Is there an existing project convention for drilldowns in this repo?
5. **Source context rendering:** Should `sourceContext` be shown to end users (collapsed) or hidden behind an “Advanced” toggle, or omitted entirely?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/114

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/114  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **System**, I want an immutable pricing snapshot per document line so that later price changes don’t alter history.

## Details  
- Snapshot includes price, cost-at-time, MSRP-at-time, applied rules, timestamp, policy decisions.

## Acceptance Criteria  
- Snapshot written on quote and/or booking.  
- Immutable.  
- Drilldown supported.

## Integrations  
- Workexec stores snapshotId on lines; Accounting may consume for margin reporting (optional).

## Data / Entities  
- PricingSnapshot, DocumentLineRef, PricingRuleTrace

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: Product / Parts Management

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