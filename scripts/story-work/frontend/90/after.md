STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Counts: Approve and Post Adjustments from Cycle Count

### Primary Persona
Inventory Manager (and Tier-2 Approver: Inventory Controller/Director)

### Business Value
Ensure cycle-count variances are posted to inventory on-hand only with appropriate policy-driven approvals, producing immutable ledger entries and a complete audit trail.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Inventory Manager (and authorized approver),
- **I want** to review, approve, or reject cycle-count-generated inventory adjustments before they are posted when they exceed policy thresholds,
- **So that** shrink/corrections are controlled, auditable, and posted accurately to on-hand inventory.

### In-scope
- Frontend screens to:
  - List adjustments requiring approval (approval queue)
  - View adjustment details (including computed variances and required tier)
  - Approve (post) or reject (with reason) adjustments when authorized
  - View posted outcome (ledger reference) and audit/status history
- Permission-aware UI gating (hide/disable actions when unauthorized)
- Error handling and user feedback for validation/authorization/concurrency failures
- In-app pending approvals indicator entry point (minimal v1)

### Out-of-scope
- Creating cycle counts, computing variances, and generating adjustments (handled by prior flow/backend)
- Defining/configuring threshold policies (admin/config screens)
- Email digests/escalations (optional per backend reference; not implemented unless contract exists)
- Accounting/reporting downstream processing beyond showing status (no financial policy decisions in UI)

---

## 3. Actors & Stakeholders
- **Inventory Manager (Tier 1 Approver)**: Reviews and approves/rejects adjustments requiring Tier 1.
- **Inventory Controller/Director (Tier 2 Approver)**: Approves higher-risk adjustments requiring Tier 2.
- **Inventory Clerk (Read-only)**: May view adjustments but cannot approve/reject (exact permissions TBD).
- **System (Inventory Domain/Moqui services)**: Enforces thresholds, required tier, state transitions, ledger posting, audit.
- **Accounting (Downstream)**: May consume outbox events; frontend only surfaces that posting occurred (no accounting logic).

---

## 4. Preconditions & Dependencies
- A cycle count has completed and produced one or more **InventoryAdjustment** records.
- Backend provides:
  - Adjustment list and detail read endpoints/services
  - Approve (post) action endpoint/service
  - Reject action endpoint/service (requires rejection reason)
  - Status/history/audit fields in responses
- User is authenticated and authorization/permissions are enforceable by backend; frontend can query effective permissions or infer via 403.
- Reason codes for rejection (or free-text rejection reason) availability is defined by backend contract (currently unclear).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Inventory → Counts → Adjustment Approvals**
- Optional secondary entry: notification badge/link “Pending approvals (N)” (if notification data is available)

### Screens to create/modify (Moqui)
1. **`Inventory/Count/AdjustmentApprovalQueue.xml`** (new)
   - List/table of adjustments with status `PENDING_APPROVAL`
   - Filters (safe defaults): location/site, SKU/stockItem, required tier, aging, variance magnitude
2. **`Inventory/Count/AdjustmentApprovalDetail.xml`** (new)
   - Read-only adjustment details + computed variance fields
   - Action panel: Approve/Post, Reject (with rejectionReason), Back
   - Status/audit history panel (read-only)
3. Optional: add link/badge in an existing Inventory dashboard screen (if one exists in repo conventions)

### Navigation context
- From queue screen, selecting a row navigates to detail screen for that `adjustmentId`.
- After action:
  - Approve/Post: remain on detail, refresh and show new status `POSTED` and ledger entry reference (if returned), and disable further actions.
  - Reject: remain on detail, refresh and show status `REJECTED`, display rejection reason, disable further actions.
  - If auto-approved by policy, queue should not show it; detail view should show `AUTO_APPROVED`/`POSTED` state if navigated directly.

### User workflows
- **Happy path (Tier 1)**: Open queue → open adjustment → review variances → Approve → see posted confirmation and ledger reference.
- **Happy path (Tier 2)**: Same, but only Tier 2 can approve when required tier is Tier 2.
- **Alternate**: Reject with reason → see rejected status.
- **Alternate**: Attempt action without permission → UI blocked and/or backend returns 403 with message.
- **Alternate**: Posting fails backend-side (stock inactive/not found) → show FAILED with error details if provided.

---

## 6. Functional Behavior

### Triggers
- User navigates to approval queue.
- User selects an adjustment to view.
- User clicks Approve/Post or Reject.

### UI actions
**Queue**
- Load pending adjustments list.
- Allow filtering/sorting.
- Row click → navigate to detail.

**Detail**
- Load adjustment by ID.
- Show:
  - Stock item identifiers (SKU/name as available)
  - Location/site as available
  - varianceQty, onHandQtyAtProposal, unitCost
  - derived: unitVariance, valueVariance, percentVariance
  - requiredApprovalTier
  - policyVersion
  - status and timestamps; createdBy; approvedBy if any
- Actions:
  - **Approve/Post** button
  - **Reject** button + required input (rejectionReason)
  - Buttons enabled/disabled based on status and authorization

### State changes (UI perspective)
- `PENDING_APPROVAL` → (Approve) → `POSTED` (or `FAILED` on error)
- `PENDING_APPROVAL` → (Reject) → `REJECTED`
- `AUTO_APPROVED` and `POSTED` and `REJECTED` are treated as immutable read-only states.

### Service interactions
- Read queue and detail via Moqui service calls (or REST via Moqui screens if that’s repo convention).
- Call approve/reject services; on success, refresh detail and update queue (remove item if no longer pending).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Reject requires **rejectionReason** (non-empty). Enforce client-side required validation and backend validation.
- Approve requires:
  - adjustment status is `PENDING_APPROVAL`
  - current user has permission for required tier (frontend should pre-check if possible; backend is source of truth)

### Enable/disable rules
- Approve/Post button enabled only when:
  - status = `PENDING_APPROVAL`
  - user authorized for requiredApprovalTier
- Reject button enabled only when:
  - status = `PENDING_APPROVAL`
  - user authorized for requiredApprovalTier (or separate reject permission if backend differentiates—unclear)
- If status in (`POSTED`, `REJECTED`, `FAILED`, `AUTO_APPROVED`), all mutation actions disabled.

### Visibility rules
- Queue screen:
  - Only show adjustments in `PENDING_APPROVAL` (default), with optional filter for other statuses (safe default: allow toggling to “All statuses” if endpoint supports it; otherwise pending only).
- Detail screen:
  - Always show read-only fields; show action panel only if user has any approval capability and status is `PENDING_APPROVAL`.

### Error messaging expectations
- 403: “You do not have permission to approve/reject this adjustment.”
- 400 validation: show specific field errors (e.g., “Rejection reason is required.”)
- 409 concurrency: “This adjustment was updated by another user. Refreshing…” and auto-refresh detail.
- 500/unknown: generic error + correlation/trace id if available.

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `InventoryAdjustment` (primary)
- `InventoryLedgerEntry` (read-only reference after posting)
- `ReasonCode` (only if rejection uses a coded list rather than free text; unclear)
- `ApprovalRecord` / audit history (if backend exposes explicit approval records vs embedded audit fields; unclear)

### Fields (type, required, defaults)
**InventoryAdjustment (display)**
- `adjustmentId` (UUID, required, read-only)
- `stockItemId` (UUID, required, read-only)
- `reasonCodeId` (UUID, required?, read-only) — cycle count shrink/gain; display label if available
- `varianceQty` (decimal, required, read-only)
- `unitCost` (decimal money, required? read-only, 4+ decimals)
- `onHandQtyAtProposal` (decimal, required, read-only)
- Derived (read-only):
  - `unitVariance` (decimal)
  - `valueVariance` (decimal money)
  - `percentVariance` (decimal)
- Workflow:
  - `status` enum (required)
  - `requiredApprovalTier` enum (nullable when not pending)
  - `policyVersion` (string)
- Audit:
  - `createdByUserId`, `approvedByUserId` (UUID)
  - `createdAt`, `updatedAt` (timestamp)
  - `rejectionReason` (string, required only when rejected)
  - `failureMessage` or `errorDetails` (string, if status FAILED; backend contract unclear)

**InventoryLedgerEntry (display)**
- `ledgerEntryId` (UUID)
- `type` = `ADJUST_CYCLE_COUNT`
- `changeInQuantity`, `quantityAfter`, `postedAt`

### Read-only vs editable
- All adjustment fields read-only except:
  - `rejectionReason` input (editable) when rejecting a `PENDING_APPROVAL` item.

### Derived/calculated fields
- UI should treat derived fields as read-only and sourced from backend (do not recompute thresholds/tiers client-side).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not provided in this issue; below are required capabilities, not exact endpoints.

### Load/view calls
- **List pending approvals**
  - Input: optional filters (site/location, SKU, tier, date range)
  - Output: list of `InventoryAdjustment` summary fields including `adjustmentId`, identifiers, variances, tier, status, timestamps
- **Get adjustment detail**
  - Input: `adjustmentId`
  - Output: full `InventoryAdjustment` + (optional) related ledger entry + audit/status history

### Create/update calls
- None (frontend does not create adjustments in this story)

### Submit/transition calls
- **Approve/Post adjustment**
  - Input: `adjustmentId`
  - Output: updated `InventoryAdjustment` (status POSTED or FAILED) and ledger reference if posted
  - Errors:
    - 403 unauthorized tier/permission
    - 409 concurrency
    - 422/400 invalid state (not pending)
- **Reject adjustment**
  - Input: `adjustmentId`, `rejectionReason` (string) OR `rejectionReasonCodeId` (if coded)
  - Output: updated `InventoryAdjustment` status REJECTED
  - Errors: 400 missing reason, 403, 409, 422/400 invalid state

### Error handling expectations
- Moqui screen should surface service error messages in a standard error banner and field-level errors for rejectionReason.
- On 409, prompt refresh and reload.

---

## 10. State Model & Transitions

### Allowed states (as displayed)
- `PENDING_APPROVAL`
- `AUTO_APPROVED`
- `POSTED`
- `REJECTED`
- `FAILED`

### Role-based transitions (UI)
- From `PENDING_APPROVAL`:
  - Approve/Post: allowed only for users authorized for `requiredApprovalTier`
  - Reject: allowed only for users authorized for `requiredApprovalTier` (unless backend distinguishes; TBD)
- No transitions allowed from `AUTO_APPROVED`, `POSTED`, `REJECTED`, `FAILED` in UI.

### UI behavior per state
- `PENDING_APPROVAL`: show action panel (permission gated)
- `AUTO_APPROVED`: show as informational; no actions
- `POSTED`: show ledger entry reference; no actions
- `REJECTED`: show rejectionReason; no actions
- `FAILED`: show failure message and guidance (“Contact support or retry if allowed”)—retry behavior is **not** assumed; only show if backend exposes retry capability.

---

## 11. Alternate / Error Flows

### Validation failures
- Reject without rejectionReason:
  - Client blocks submit; if server returns 400, display field error.

### Concurrency conflicts
- If another user posts/rejects while viewing:
  - Approve/Reject returns 409 or “invalid state”
  - UI reloads detail and informs user the item is no longer pending.

### Unauthorized access
- User can view queue/detail but cannot act:
  - Action buttons hidden/disabled; if direct submit attempted, show 403 message.

### Empty states
- Queue has zero items:
  - Display “No adjustments pending approval” and show last refreshed time.

### Backend posting failure
- Approve returns status `FAILED`:
  - Display backend-provided error details if present; keep item out of queue if it is no longer pending.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View pending approval queue
**Given** I am an authenticated user with access to Inventory Counts  
**When** I navigate to “Adjustment Approvals”  
**Then** I see a list of adjustments in status `PENDING_APPROVAL`  
**And** each row shows stock identifier, variance quantities/values, required approval tier, and created time  
**And** if there are none, I see an empty-state message.

### Scenario 2: Tier 1 approver can approve Tier 1 adjustment
**Given** an adjustment exists with status `PENDING_APPROVAL` and `requiredApprovalTier = TIER_1_MANAGER`  
**And** I have Tier 1 approval permission  
**When** I open the adjustment detail and click “Approve/Post”  
**Then** the system posts the adjustment and the UI refreshes to status `POSTED`  
**And** the UI displays a ledger entry reference (if provided by backend)  
**And** approve/reject actions are disabled.

### Scenario 3: Tier 1 approver cannot approve Tier 2 adjustment
**Given** an adjustment exists with status `PENDING_APPROVAL` and `requiredApprovalTier = TIER_2_DIRECTOR`  
**And** I do not have Tier 2 approval permission  
**When** I open the adjustment detail  
**Then** the “Approve/Post” and “Reject” actions are not available (hidden or disabled)  
**And** if I attempt to approve via direct request, I receive a 403 and the UI shows an authorization error.

### Scenario 4: Reject requires a reason
**Given** an adjustment exists with status `PENDING_APPROVAL`  
**And** I am authorized to reject it  
**When** I click “Reject” without entering a rejection reason  
**Then** the UI prevents submission and shows “Rejection reason is required”  
**And** no state change occurs.

### Scenario 5: Authorized reject transitions to REJECTED
**Given** an adjustment exists with status `PENDING_APPROVAL`  
**And** I am authorized to reject it  
**When** I enter a rejection reason and submit reject  
**Then** the UI refreshes to status `REJECTED`  
**And** the rejection reason is displayed read-only  
**And** the adjustment no longer appears in the pending approval queue.

### Scenario 6: Concurrency conflict on approve
**Given** I am viewing a `PENDING_APPROVAL` adjustment  
**And** another approver posts it before I do  
**When** I click “Approve/Post”  
**Then** the backend responds with a concurrency/invalid-state error  
**And** the UI informs me it was updated by someone else  
**And** the UI reloads detail showing the current status (e.g., `POSTED`).

---

## 13. Audit & Observability

### User-visible audit data
- Detail screen shows:
  - createdBy, createdAt
  - approvedBy, postedAt (or updatedAt) when posted
  - rejection reason + who rejected (if available)
  - policyVersion applied
  - status history list (if backend exposes events/history); otherwise show timestamps and status only.

### Status history
- If backend provides an audit/history endpoint, show chronological transitions (Created → Pending/Auto-approved → Posted/Rejected/Failed) with actor and timestamp.

### Traceability expectations
- Display correlation/request id on error banner if returned by backend (safe observability default).
- Ledger entry reference shown when posted to tie UI action to immutable ledger.

---

## 14. Non-Functional UI Requirements
- **Performance:** Queue loads within 2 seconds for up to 200 pending items (pagination if more).
- **Accessibility:** Keyboard navigable table, proper labels for inputs, focus management after errors.
- **Responsiveness:** Works on tablet viewport used in warehouse/stockroom.
- **i18n/timezone:** Display timestamps in user locale/timezone; store/submit in UTC as provided by backend.
- **Currency:** Display monetary values using system currency formatting; do not assume currency code unless backend provides.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for zero pending approvals; safe because it doesn’t affect domain logic; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: Paginate approval queue (default page size 25) if list supports it; safe ergonomics only; impacts UX Summary, Performance.
- SD-ERR-MAP-STD: Map 400/403/409/500 to standard Moqui/Quasar notifications + field errors; safe because it’s presentation-layer handling; impacts Service Contracts, Error Flows.
- SD-OBS-CORRELATION-ID: Display correlation/request id when present in error response headers/body; safe observability boilerplate; impacts Audit & Observability, Error Flows.

---

## 16. Open Questions
1. **Backend service contract:** What are the exact Moqui service names (or REST endpoints) for:
   - listing pending approval adjustments
   - fetching adjustment detail (including ledger entry reference)
   - approve/post action
   - reject action?
2. **Rejection input:** Is rejection reason **free-text** (`rejectionReason`) or a **ReasonCode** selection (or both)? If codes: what is the entity/service to load valid reason codes for cycle-count rejection?
3. **Authorization model:** What permissions/roles govern:
   - viewing the queue
   - approving Tier 1 vs Tier 2
   - rejecting (same as approve or separate permission)?
4. **Queue scope filters:** Is approval queue scoped by **site/location** (multi-site user)? If yes, how is the user’s active site determined in frontend (session context vs explicit filter)?
5. **Audit/history availability:** Does backend expose explicit approval/audit records (e.g., `ApprovalRecord` list) or only current fields (`approvedByUserId`, timestamps)? What fields should be displayed for “full audit trail”?
6. **FAILED handling:** When posting fails and status becomes `FAILED`, does backend allow retry (transition back to pending) or is correction always via a new adjustment? UI must not assume a retry path.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Counts: Approve and Post Adjustments from Cycle Count — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/90


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Counts: Approve and Post Adjustments from Cycle Count
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/90
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Counts: Approve and Post Adjustments from Cycle Count

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want approvals before posting adjustments so that shrink and corrections are controlled.

## Details
- Approval required above thresholds.
- Posting creates Adjust ledger entries and updates on-hand.

## Acceptance Criteria
- Adjustments require permission.
- Ledger entries posted.
- Thresholds enforced.
- Full audit trail.

## Integrations
- Optional accounting events for shrink/adjustment.

## Data / Entities
- InventoryLedgerEntry(Adjust), ApprovalRecord, ReasonCode, EventOutbox

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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