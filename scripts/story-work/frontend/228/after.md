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

## 1. Story Header

### Title
[FRONTEND] [STORY] Promotion: Enforce Idempotent Promotion (Estimate Snapshot → Work Order)

### Primary Persona
Service Advisor (primary UI initiator); System (idempotency enforcer)

### Business Value
Prevents duplicate Work Orders when promotion is retried (double-clicks, refresh, network retries), preserving operational integrity, auditability, and a single canonical Work Order per estimate snapshot.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** promoting an approved estimate snapshot to a work order to be safe to retry and always return the same canonical work order,  
**so that** accidental double actions or transient failures do not create duplicate work orders or inconsistent execution data.

### In-scope
- Frontend “Promote Estimate to Work Order” action is **idempotent from the user’s perspective**
- UI handles:
  - first-time promotion success (creates WO)
  - retry returning existing WO
  - partial promotion completion (backend completes missing pieces; UI reflects final canonical WO)
  - corrupted link case (promotion record exists but WO missing) with clear admin escalation message
- Explicit error handling, concurrency-safe UI (disable button, show progress, allow safe retry)
- Display canonical Work Order reference/link after promotion

### Out-of-scope
- Implementing backend idempotency logic, DB constraints, or actual PromotionRecord entity persistence
- Work order execution lifecycle transitions beyond “navigate to created/retrieved work order”
- Admin repair tools (only surface “requires admin intervention” guidance)

---

## 3. Actors & Stakeholders
- **Service Advisor**: initiates promotion, expects single WO outcome
- **System**: performs promotion and enforces idempotency key `(estimateId, snapshotVersion)`
- **Auditor/Manager**: expects traceability of promotion attempts (success/retry/failure)
- **Support/Admin**: handles corrupted data link scenarios

---

## 4. Preconditions & Dependencies
- Estimate exists and is in a promotable condition (typically **APPROVED**; exact rule enforced by backend)
- A specific immutable **snapshotVersion** is available for the estimate being promoted
- Frontend can call backend promotion endpoint(s) and receives a response that includes a Work Order identifier and/or URL
- User is authenticated and authorized to promote estimates to work orders (authorization errors handled)

**Dependencies (backend/API)**
- A promotion endpoint that behaves idempotently for `(estimateId, snapshotVersion)` and returns canonical WO reference.
- A way to load an estimate including snapshot/version info (or a UI-provided snapshotVersion).
- A work order view route exists in frontend to navigate to a Work Order by ID/number.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Estimate Detail** screen: action button “Promote to Work Order”
- Optional: from **Estimate List** row action (if existing in app) (only if estimate is eligible)

### Screens to create/modify
- **Modify**: `EstimateDetail` screen
  - Add a “Promote to Work Order” action area
  - Add a promotion result panel / banner showing canonical Work Order link
- **Optional new**: `PromotionResultDialog` reusable component (Vue/Quasar) invoked by Estimate screens

### Navigation context
- After successful promotion (created or retrieved), user can click:
  - “Open Work Order” → navigate to WorkOrder detail screen/route
  - “Copy Work Order #/Link” (optional)

### User workflows
**Happy path (first attempt)**
1. Service Advisor opens Estimate Detail.
2. Clicks “Promote to Work Order”.
3. UI shows blocking progress state.
4. Success response returns Work Order reference.
5. UI shows “Work Order created” and provides link.

**Retry path (double click / refresh / retry after transient error)**
1. Service Advisor repeats promotion.
2. UI receives “existing work order” response.
3. UI shows “Work Order already exists” and same canonical link.

**Corrupted link path**
1. Promotion attempt returns error indicating promotion record exists but linked work order missing/corrupt.
2. UI shows non-retryable error with escalation instructions and surfaces correlation id if provided.

---

## 6. Functional Behavior

### Triggers
- User clicks “Promote to Work Order” on an estimate snapshot/version.
- System/network retries cause duplicate requests (double-click, page reload, timeout retry).

### UI actions
- On click:
  - Validate required identifiers exist in UI state: `estimateId`, `snapshotVersion`
  - Disable the promote button immediately (prevent local double-submit)
  - Show loading indicator and “Promoting…” message
- On success:
  - Persist the returned canonical Work Order reference in component state
  - Render a success banner/panel with Work Order number/id and link
  - Enable “Open Work Order” navigation action
- On failure:
  - Re-enable promote button **only if** retry is allowed (see error mapping)
  - Show error banner with actionable message

### State changes (frontend)
- `promotionStatus`: `idle | inProgress | succeeded | failed | blockedCorrupted`
- `promotionResult`: `{ workOrderId, workOrderNumber?, workOrderUrl? }` (as provided)
- `lastAttempt`: timestamp and (if available) `correlationId`

### Service interactions (high-level)
- Call backend “promote” service with `(estimateId, snapshotVersion)`
- On response, optionally call Work Order “get” endpoint to confirm existence if backend returns only ID (only if required by UI routing)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- UI must not allow promotion if:
  - `estimateId` missing → show “Estimate not loaded; refresh and try again.”
  - `snapshotVersion` missing → show “Estimate snapshot version is required to promote.”
- UI must treat the operation as idempotent:
  - Any successful response that contains a canonical Work Order reference is considered final, regardless of whether it was created or retrieved.

### Enable/disable rules
- “Promote to Work Order” button:
  - Enabled only when estimate is in an eligible status per loaded estimate data (if status available in UI); otherwise enabled and backend enforces (do not guess rules).
  - Disabled while `promotionStatus === inProgress`

### Visibility rules
- Show promotion result panel only when `promotionStatus === succeeded` and work order reference present.
- Show “Admin intervention required” panel when corrupted link error occurs.

### Error messaging expectations
- Must distinguish:
  - **Transient** (timeouts/5xx): “Promotion may have succeeded. You can retry safely; the system will return the same Work Order.”
  - **Validation** (4xx like 400/422): show backend-provided field/summary errors.
  - **Unauthorized** (401/403): “You don’t have permission to promote estimates.”
  - **Corrupted link** (409/500 with explicit code): “Promotion record exists but linked Work Order is missing. Do not retry. Contact admin/support.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Estimate` (read)
- `PromotionRecord` (not necessarily directly read by frontend; implied by promotion API behavior)
- `WorkOrder` (read/navigate)
- `AuditEvent` (not directly read; expect backend to log)

### Fields
**Estimate (read)**
- `estimateId` (string/number, required)
- `snapshotVersion` (integer, required for promotion)
- `status` (enum/string, optional but used for button enablement if present)

**Promotion request (write)**
- `estimateId` (required)
- `snapshotVersion` (required)

**Promotion response (read)**
- `workOrderId` (required on success)
- `workOrderNumber` (optional)
- `workOrderUrl` (optional; if absent, frontend builds route from `workOrderId`)
- `outcome` (optional: `CREATED | RETRIEVED | COMPLETED_PARTIAL` if backend supports; otherwise infer messaging generically)
- `correlationId` (optional but strongly preferred for support)

### Read-only vs editable
- All fields are read-only in UI except the action trigger.

### Derived/calculated fields
- `promotionKey` should **not** be computed client-side as authoritative; client may log a derived string for UI debug only (non-authoritative).

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names are not provided in inputs; the contracts below define required behavior and payloads. If the repo already has established endpoints/services, implement using existing conventions.

### Load/view calls
- Load Estimate Detail (existing): must provide `estimateId`, `snapshotVersion`, `status` (or enough to display promote action)

### Create/update calls
- **Promote Estimate Snapshot**
  - **Method**: `POST`
  - **Path (proposed)**: `/api/estimates/{estimateId}/promote`
  - **Body**: `{ "snapshotVersion": <int> }`
  - **Success (200 or 201)**:
    ```json
    { "workOrderId": 456, "workOrderNumber": "WO-456", "outcome": "CREATED|RETRIEVED|COMPLETED_PARTIAL", "correlationId": "..." }
    ```
  - **Idempotency behavior**: Same `(estimateId, snapshotVersion)` always returns the same `workOrderId`.

### Submit/transition calls
- None beyond promotion.

### Error handling expectations
- `400/422`: input invalid (missing snapshotVersion, estimate not promotable)
- `401/403`: not authorized
- `404`: estimate not found (or snapshot not found)
- `409` (preferred) or `500` with explicit error code: corrupted promotion link (promotion record points to missing WO)
- `5xx/timeout`: treat as unknown outcome; allow safe retry and message accordingly

Frontend must:
- Display backend `correlationId` if present in error payload/headers.
- Avoid re-submitting automatically in a tight loop; user-initiated retry only.

---

## 10. State Model & Transitions

### Allowed states (frontend promotion UI state)
- `idle` → `inProgress` → (`succeeded` | `failed` | `blockedCorrupted`)

### Role-based transitions
- If backend returns 403, UI remains/returns to `idle` with permission error and does not attempt further transitions.

### UI behavior per state
- `idle`: show enabled Promote button (if eligible), no banners
- `inProgress`: disable Promote button, show spinner and warning “Do not navigate away”
- `succeeded`: show canonical Work Order link and “Open Work Order”
- `failed`: show retryable error UI; keep Promote enabled
- `blockedCorrupted`: show non-retryable error UI; keep Promote disabled and show “Contact admin/support”

---

## 11. Alternate / Error Flows

### Validation failures
- Missing snapshotVersion in UI → block call, show inline error
- Backend rejects estimate state (e.g., not approved) → show error; do not change estimate locally

### Concurrency conflicts
- Two tabs promoting same estimate snapshot:
  - One creates, other retrieves; both must end with same canonical WO reference.

### Unauthorized access
- 401: redirect to login (if app pattern), then return to estimate
- 403: show “Not permitted” and do not retry automatically

### Empty states
- Estimate loaded but no snapshotVersion available:
  - Show explanation and hide/disable promote action.

---

## 12. Acceptance Criteria

### Scenario 1: First-time promotion creates a new work order
**Given** a Service Advisor is viewing an Estimate Detail with `estimateId=E-123` and `snapshotVersion=1`  
**And** the Promote button is enabled  
**When** the user clicks “Promote to Work Order”  
**Then** the UI disables the Promote button and shows an in-progress indicator  
**And** the frontend sends a promotion request containing `estimateId=E-123` and `snapshotVersion=1`  
**And** on a successful response containing `workOrderId`  
**Then** the UI shows a success state with a canonical “Open Work Order” link for that `workOrderId`.

### Scenario 2: Retried promotion returns the same canonical work order (no duplicates)
**Given** an Estimate snapshot `estimateId=E-123` and `snapshotVersion=1` has already been promoted  
**When** the user triggers “Promote to Work Order” again (including after refresh)  
**Then** the UI receives a success response with the same `workOrderId` as previously shown  
**And** the UI displays that same canonical Work Order link  
**And** the UI does not display any indication that a second Work Order was created.

### Scenario 3: Timeout/5xx allows safe user retry and still resolves to single canonical work order
**Given** the user clicks “Promote to Work Order” for `estimateId=E-123` and `snapshotVersion=1`  
**When** the request times out or returns a 5xx error  
**Then** the UI shows an error message stating the operation may have succeeded and retry is safe  
**And** the Promote button becomes enabled again for user-initiated retry  
**When** the user retries  
**Then** the UI eventually shows exactly one canonical Work Order link for the snapshot.

### Scenario 4: Corrupted promotion record blocks and requires admin intervention
**Given** the backend determines a PromotionRecord exists for `(estimateId=E-123, snapshotVersion=1)`  
**And** the linked Work Order cannot be loaded (corrupted/missing)  
**When** the user clicks “Promote to Work Order”  
**Then** the UI shows a non-retryable error state instructing the user to contact admin/support  
**And** the Promote button is disabled to prevent repeated attempts  
**And** the UI displays any provided `correlationId` for support diagnostics.

### Scenario 5: Missing required identifiers prevents promotion call
**Given** the Estimate Detail is missing `snapshotVersion` in the loaded data  
**When** the user attempts to promote  
**Then** the frontend does not call the backend  
**And** shows “Estimate snapshot version is required to promote.”

---

## 13. Audit & Observability

### User-visible audit data
- After a successful promotion, UI should display:
  - Work Order reference
  - Timestamp of completion (client-side) (optional)
  - Correlation ID if returned

### Status history
- Not a separate UI requirement, but UI must not hide repeated attempts; it may show “retrieved existing” if `outcome` provided.

### Traceability expectations
- Frontend should include a request correlation header if the project already uses one (e.g., `X-Correlation-Id`) and log it client-side in dev tools logs (non-PII).
- On errors, surface correlationId from response payload/headers when present.

---

## 14. Non-Functional UI Requirements
- **Performance**: promotion action should show feedback within 250ms (spinner/message), regardless of network latency.
- **Accessibility**: loading state announced to screen readers; disabled button has accessible reason text.
- **Responsiveness**: works on tablet resolutions typical for POS.
- **i18n**: all user-facing messages routed through translation mechanism if project uses one; otherwise centralized constants (do not hardcode scattered strings).

---

## 15. Applied Safe Defaults
- SD-UX-01: Disable submit/action button while request in-flight; qualifies as safe UX ergonomics to prevent accidental double submits; impacts UX Summary, Functional Behavior, Error Flows.
- SD-ERR-01: Treat network timeout/5xx as “unknown outcome” and allow user-initiated retry with messaging that idempotency makes retry safe; qualifies as safe error-handling mapping; impacts Business Rules, Alternate/Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. What is the **exact backend promotion endpoint** (path, method, request/response schema) used by the Moqui frontend for promoting an estimate snapshot to a work order?
2. Does the promotion response include an explicit `outcome` field (CREATED vs RETRIEVED vs COMPLETED_PARTIAL) and a `correlationId`, or must the frontend infer messaging purely from HTTP status?
3. What is the canonical frontend route for Work Order detail navigation (by `workOrderId` vs `workOrderNumber`)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotion: Enforce Idempotent Promotion  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/228  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Enforce Idempotent Promotion

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
Promotion is executed multiple times due to retries or double actions.

## Main Flow
1. System checks for an existing promotion record for (estimateId, snapshotVersion).
2. If a workorder exists, system returns the existing workorder reference instead of creating a duplicate.
3. If promotion was partially completed, system completes missing pieces safely.
4. System records retry event for diagnostics/audit (optional).
5. User sees a single canonical workorder link.

## Alternate / Error Flows
- Promotion record exists but workorder deleted/corrupted → require admin intervention and block.

## Business Rules
- Promotion must be idempotent under retries.
- Promotion record is the authoritative link between estimate snapshot and workorder.

## Data Requirements
- Entities: PromotionRecord, Workorder, Estimate, AuditEvent
- Fields: promotionKey, estimateId, snapshotVersion, workorderId, status, retryCount

## Acceptance Criteria
- [ ] Repeated promotion attempts do not create duplicate workorders.
- [ ] System returns the same workorder URL/number for the same snapshot.
- [ ] Partial promotion can be safely completed.

## Notes for Agents
Idempotency prevents data integrity nightmares—treat it as non-negotiable.


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