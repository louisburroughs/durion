## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

# 1. Story Header

## Title
[FRONTEND] Receiving: Create Receiving Session from PO/ASN

## Primary Persona
Receiver (warehouse/store receiving clerk)

## Business Value
Enables fast, accurate check-in by creating an auditable receiving session pre-populated from an existing PO or ASN, reducing manual entry and setup time.

---

# 2. Story Intent

## As a / I want / So that
**As a** Receiver,  
**I want** to create a receiving session by entering or scanning a PO/ASN identifier,  
**so that** expected items and quantities are pre-loaded and I can begin check-in.

## In-scope
- UI flow to create a new Receiving Session using **exact-match** PO/ASN identifier.
- Support two entry methods:
  - Manual text entry
  - Barcode scan into the same input (scanner acts as keyboard input)
- Validation feedback for:
  - Not found
  - Not receivable (closed/fully received)
  - Missing identifier (blind receiving blocked)
- Creation of session shell + pre-populated lines from source
- Navigate to session detail view after creation
- Display auditable metadata available from create response (session id, source doc, entry method, timestamps/creator when returned)

## Out-of-scope (explicit)
- Counting/confirming received quantities and line-by-line matching workflow
- Capturing variances (over/under/wrong item) and variance approval
- Lot/serial capture (not implemented in v1 here; only tolerate fields if returned)
- Search/browse for POs/ASNs (identifier must be entered/scanned)
- Blind receiving (creating session without PO/ASN)

---

# 3. Actors & Stakeholders

- **Receiver (primary user):** creates sessions during physical receiving.
- **Inventory Manager:** cares about traceability/auditability and operational correctness.
- **Purchasing Manager:** cares about PO receiving progression accuracy.
- **External system (Positivity):** may be source of ASN data (frontend consumes via Moqui/backend APIs; no direct integration UI here).

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in the frontend and has permission to perform receiving session creation (permission name not specified in provided inputs; enforced by backend; frontend must handle 403).

## Dependencies
- Backend capability to:
  - Validate a PO/ASN identifier for receivability (exact match)
  - Create a ReceivingSession tied to exactly one source document (PO or ASN)
  - Return created session and its expected lines
- Product master mapping exists for items on PO/ASN (backend responsibility; frontend renders lines returned)

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Primary navigation: Receiving module landing screen (e.g., `/receiving`)
- Action: “Create New Receiving Session”

## Screens to create/modify
1. **Screen:** `apps/pos/screen/receiving/CreateReceivingSession.xml` (new)
   - Purpose: enter/scan identifier and submit session creation
2. **Screen:** `apps/pos/screen/receiving/ReceivingSessionDetail.xml` (new or existing if already present)
   - Purpose: show created session header + expected lines (read-only for this story)
3. **Menu/Navigation update:** add route/link from receiving landing screen to create screen

## Navigation context
- Breadcrumb: Receiving → Create Session
- After successful creation: redirect to Receiving → Session → Detail (with `receivingSessionId` parameter)

## User workflows
### Happy path (manual)
1. Receiver opens Create Session screen
2. Enters identifier (e.g., `PO-123`)
3. Clicks “Create Session”
4. Sees Session Detail with header (supplier, shipment ref if any, source doc) and expected lines

### Happy path (scan)
1. Receiver focuses identifier input
2. Scans barcode; value populates input
3. Auto-submit behavior is **not assumed**; user confirms by clicking Create (unless explicitly supported; see Open Questions)
4. Redirect to Session Detail

### Alternate paths
- Not found → inline error; stay on create screen
- Not receivable → inline error; stay on create screen
- Missing identifier → blocking message; stay on create screen
- Cancel → back to receiving landing screen; no changes persisted

---

# 6. Functional Behavior

## Triggers
- User presses “Create Session” (primary trigger)
- (Optional) User presses Enter while in identifier field (treated as Create if non-empty; safe UI default)

## UI actions
- Input field for `sourceDocumentIdentifier` (string)
- Buttons:
  - **Create Session** (primary)
  - **Cancel** (secondary)
- Loading state while calling service
- Disable Create while request in-flight

## State changes (frontend)
- No local state persistence required beyond in-screen form state
- On success: route transition to detail screen with returned `receivingSessionId`

## Service interactions (Moqui)
- Call a backend/Moqui service (or REST endpoint via Moqui) to:
  1. Validate identifier
  2. Create session and lines
- Capture method of entry:
  - `entryMethod=MANUAL` when user typed (or pasted) value and clicked Create
  - `entryMethod=SCAN` when input originated from scan (see Open Questions for detection approach)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Identifier is required:
  - If empty/blank on submit → show blocking error: **“Receiving requires a valid PO or ASN. Blind receiving is not supported.”**
- Exact match required:
  - Backend returns not found → show: **“Source document <id> not found.”**
- Must be receivable:
  - If PO/ASN is Closed/Fully Received → show: **“<id> has already been fully received.”** (PO vs ASN wording may vary; frontend displays backend-provided message when available)

## Enable/disable rules
- Create button disabled when identifier is blank or request is in-flight
- Identifier field disabled during in-flight request (to prevent concurrent submits)

## Visibility rules
- Error banner/inline message visible only when last submit failed
- Session detail screen displays:
  - Header fields (supplier/distributor, shipment reference, source doc id/type, status)
  - Expected lines list (read-only here)

## Error messaging expectations
- Prefer backend error messages when safe and user-actionable
- Fallback to standard mapped messages by HTTP status:
  - 400: validation error (show message)
  - 403: “You do not have permission to create receiving sessions.”
  - 404: not found (if used by backend)
  - 409: conflict (e.g., already fully received) show message
  - 500: “Something went wrong creating the receiving session. Try again or contact support.”

---

# 8. Data Requirements

## Entities involved (conceptual; frontend reads/writes via services)
- `ReceivingSession`
- `ReceivingLine`
- `SupplierRef` (read-only display via session header)
- `VarianceRecord` (mentioned in original, but **out-of-scope** in this story)

## Fields (type, required, defaults)

### Create request (frontend → service)
- `sourceDocumentId` (String, **required**) — value user entered/scanned
- `sourceDocumentType` (Enum `PO|ASN`, **optional/derived**) — if backend can infer from identifier, frontend may omit; otherwise must be provided (Open Question)
- `entryMethod` (Enum `MANUAL|SCAN`, **required**)

### Create response (service → frontend)
- `receivingSessionId` (String/UUID, required)
- `sourceDocumentId` (String, required)
- `sourceDocumentType` (Enum `PO|ASN`, required)
- `supplierId` (String, required)
- `supplierName` (String, recommended for UI display)
- `shipmentReference` (String, nullable)
- `status` (Enum; expected initial `Open`, required)
- `createdAt` (Timestamp, optional if returned)
- `createdByUserId` (String, optional if returned)
- `lines[]` (required)
  - `receivingLineId` (String/UUID, required)
  - `productId` (String, required)
  - `productName`/`sku` (String, optional if returned)
  - `expectedQuantity` (Decimal, required)
  - `receivedQuantity` (Decimal, required; default 0)

## Read-only vs editable
- Create screen: only `sourceDocumentId` editable
- Detail screen (this story): all fields read-only (no editing of quantities/lines)

## Derived/calculated fields
- Display-only: total expected lines count, total expected quantity (optional UI convenience; safe default)

---

# 9. Service Contracts (Frontend Perspective)

> Note: Concrete endpoint/service names are not provided in inputs; define Moqui service interface names below for frontend integration. If actual backend uses REST, Moqui can wrap via service-call-out; implementer should map accordingly.

## Load/view calls
- `receiving.getReceivingSession`  
  **Input:** `receivingSessionId`  
  **Output:** session header + lines (fields listed above)  
  **Use:** Session Detail screen load/refresh

## Create/update calls
- `receiving.createReceivingSessionFromSource`  
  **Input:**
  - `sourceDocumentId` (required)
  - `entryMethod` (required)
  - `sourceDocumentType` (optional if inferable)
  **Output:**
  - `receivingSessionId` + populated session object + lines

## Submit/transition calls
- None in this story (no state transitions beyond creation)

## Error handling expectations
- Service returns structured errors with:
  - `errorCode` (recommended)
  - `message` (required)
  - `fieldErrors` (optional array: `field`, `message`)
- Frontend maps:
  - fieldErrors for identifier to input helper text
  - message to page-level alert if general

---

# 10. State Model & Transitions

## Allowed states (as displayed)
- `Open` (expected initial)
- `InProgress`, `Completed`, `Cancelled` (may exist; displayed if returned)

## Role-based transitions
- None implemented in UI for this story

## UI behavior per state
- Detail screen:
  - Always read-only in this story regardless of state
  - If session is not found or access denied, show appropriate error state

---

# 11. Alternate / Error Flows

## Validation failures
- Empty identifier → show blind receiving blocked message; no request sent (client-side)
- Backend 400 with field error → show inline; remain on create screen

## Concurrency conflicts
- If backend returns 409 because a session was created concurrently or PO became fully received between validation and create:
  - Show backend message; remain on create screen
  - Allow retry with a different identifier

## Unauthorized access
- Backend 403:
  - Show permission error
  - Do not retry automatically
  - Provide navigation back to Receiving landing

## Empty states
- Detail screen loads but lines array empty:
  - Show message: “No expected lines were provided for this source document.”
  - Still display session header for auditability

---

# 12. Acceptance Criteria

## Scenario 1: Create session from PO via manual entry
**Given** the Receiver is authenticated  
**And** a Purchase Order with identifier `PO-123` exists and is receivable (Open or Partially Received)  
**When** the Receiver navigates to Receiving → Create Session  
**And** enters `PO-123` in the identifier field  
**And** clicks “Create Session”  
**Then** the system creates a receiving session with status `Open`  
**And** the created session is tied to source document `PO-123`  
**And** the session detail screen is shown for the returned `receivingSessionId`  
**And** expected lines are displayed with `receivedQuantity = 0` for each line  
**And** the recorded entry method is `MANUAL` (as shown in response data or audit section if displayed)

## Scenario 2: Create session from ASN via scan
**Given** the Receiver is authenticated  
**And** an ASN with identifier `ASN-ABC-789` exists and is receivable  
**When** the Receiver navigates to the Create Session screen  
**And** scans a barcode that inputs `ASN-ABC-789` into the identifier field  
**And** clicks “Create Session”  
**Then** a receiving session is created and displayed  
**And** the session is tied to `ASN-ABC-789`  
**And** the session lines are pre-populated from ASN expected lines  
**And** the recorded entry method is `SCAN`

## Scenario 3: Identifier not found
**Given** the Receiver is on the Create Session screen  
**When** the Receiver enters `PO-999`  
**And** clicks “Create Session”  
**Then** the system shows an error message “Source document PO-999 not found.” (or backend-provided equivalent)  
**And** no navigation to session detail occurs  
**And** no receiving session is created

## Scenario 4: Source document already fully received/closed
**Given** the Receiver is on the Create Session screen  
**And** a Purchase Order `PO-456` exists but is Closed/Fully Received  
**When** the Receiver enters `PO-456` and submits  
**Then** the system shows an error message indicating it has already been fully received  
**And** no session is created

## Scenario 5: Blind receiving blocked
**Given** the Receiver is on the Create Session screen  
**When** the Receiver leaves the identifier blank  
**And** clicks “Create Session”  
**Then** the system shows “Receiving requires a valid PO or ASN. Blind receiving is not supported.”  
**And** no request is sent to create a session

## Scenario 6: Unauthorized user
**Given** the user lacks permission to create receiving sessions  
**When** they attempt to create a session for a valid identifier  
**Then** the system shows a permission error message  
**And** no session detail is shown

---

# 13. Audit & Observability

## User-visible audit data
- On Session Detail screen, display (if returned by service):
  - `createdAt`
  - `createdBy` (user id or name)
  - `sourceDocumentType` + `sourceDocumentId`
  - `entryMethod`

## Status history
- Out-of-scope to display full history unless backend already exposes it; do not implement new timeline here.

## Traceability expectations
- Frontend must include correlation/request id if Moqui provides it (e.g., via headers) in console logs only (no PII).
- Ensure session id is visible/copyable on detail screen for support/troubleshooting.

---

# 14. Non-Functional UI Requirements

- **Performance:** Create request should show loading state immediately; session detail should load within acceptable POS UX (<2s on typical network; if slower show skeleton/loading).
- **Accessibility:** Identifier input has label; errors announced via aria-live; buttons keyboard accessible; Enter submits when focus in input.
- **Responsiveness:** Works on tablet form factor; no reliance on hover interactions.
- **i18n/timezone/currency:** Timestamps displayed in user locale/timezone if available; no currency handling required.

---

# 15. Applied Safe Defaults

- SD-UX-ENTER-SUBMIT: Allow Enter key in identifier field to trigger Create when non-empty; qualifies as safe ergonomic default; impacts UX Summary, Functional Behavior, Acceptance Criteria (implicit).
- SD-UX-LOADING-DISABLE: Disable inputs/buttons during in-flight request and show loading indicator; safe to prevent duplicate submissions; impacts Functional Behavior, Error Flows.
- SD-ERR-HTTP-MAP: Standard mapping of 400/403/404/409/500 to user-actionable messages when backend message not provided; safe as generic error handling; impacts Business Rules, Service Contracts, Error Flows.

---

# 16. Open Questions

1. **Backend contract details (Moqui service vs REST):** What are the exact Moqui service names/paths and request/response schemas for:
   - create receiving session from source
   - load receiving session detail  
   (Needed to implement screen actions and data bindings precisely.)

2. **Source document type inference:** Should the frontend require the user to specify PO vs ASN, or will the backend infer `sourceDocumentType` from the identifier? If frontend must supply it, what is the UI rule (prefix-based, toggle, etc.)?

3. **Scan detection rule:** How should the frontend distinguish `entryMethod=SCAN` vs `MANUAL`?
   - Is there an existing scanner-input utility/pattern in this repo (e.g., timing-based keystroke heuristic, dedicated scan event)?
   - If none, should we record `MANUAL` always unless a dedicated scan event is wired?

4. **Session detail screen existence:** Is there already a receiving session detail screen/route in `durion-moqui-frontend` that should be extended instead of created anew? If yes, provide its path and parameter naming.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Receiving: Create Receiving Session from PO/ASN — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/99


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Receiving: Create Receiving Session from PO/ASN
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/99
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Receiving: Create Receiving Session from PO/ASN

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Receiver**, I want to create a receiving session from a PO/ASN so that inbound items can be checked in.

## Details
- Session includes supplier/distributor, shipment ref, expected lines.
- Support scanning barcodes and capturing lot/serial (optional v1).

## Acceptance Criteria
- Receiving session created.
- Lines can be matched and variances captured.
- Session auditable.

## Integrations
- Positivity may provide ASN; product master maps items.

## Data / Entities
- ReceivingSession, ReceivingLine, SupplierRef, VarianceRecord

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