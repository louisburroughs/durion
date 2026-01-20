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

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)

### Primary Persona
Receiver (warehouse receiving clerk)

### Business Value
Accurately increases on-hand inventory in the default Staging location at time of receipt, records variances (over/short), and creates immutable inventory ledger entries so downstream put-away and availability workflows can proceed with auditable data.

---

## 2. Story Intent

### As a / I want / So that
**As a** Receiver,  
**I want** to receive items against a Purchase Order (PO) or ASN into the site’s default Staging location by entering actual quantities and UOM,  
**so that** on-hand increases immediately in staging, variances are recorded, and put-away can follow using accurate inventory state.

### In-scope
- Moqui screen flow to:
  - search/select a PO/ASN to receive
  - load expected lines (items, expected qty, expected UOM)
  - enter actual received qty (and UOM if selectable) per line
  - finalize/confirm receipt
- Display outcome:
  - lines marked as received states (received / received-short / received-over)
  - confirmation that items are now “in staging”
  - show recorded variance summary for the receipt session
- Error handling UX for not found, validation failures, and transaction failure rollback messaging.

### Out-of-scope
- Receiving items **not** on PO/ASN (“unplanned receipt”)
- Put-away process/UI (moving from staging to final bins)
- Inventory valuation/cost display changes (costing is inventory-owned but not part of this UI story)
- Backorder/substitution policies
- Printing labels, barcode receiving scan flows (unless explicitly confirmed)

---

## 3. Actors & Stakeholders
- **Receiver (Primary):** performs receiving and confirms counts.
- **Warehouse Manager (Stakeholder):** expects auditability and inventory accuracy.
- **System (Moqui app):** enforces validation, calls services, renders states.
- **Downstream Work Execution (Stakeholder/System):** may consume availability/receipt completion signals (frontend only displays results; emission is backend).

---

## 4. Preconditions & Dependencies
- User is authenticated.
- User has permission(s) to perform receiving (exact permission string(s) TBD → Open Question).
- A PO or ASN exists and is in an allowed status (backend reference: Open / In-Transit).
- A default **Staging StorageLocation** is configured for the receiving site/facility.
- Backend endpoints/services exist (or will exist per backend story) to:
  - find PO/ASN and load expected receiving lines
  - submit receipt confirmation and return resulting states/ids

Dependencies:
- Backend story: `[BACKEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)` (durion-positivity-backend #34)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: Inventory → Receiving → “Receive into Staging”
- Deep link: `/inventory/receiving/receive` (proposed; final route must match repo conventions)

### Screens to create/modify (Moqui)
1. **Screen:** `apps/pos/screen/inventory/receiving/ReceiveStaging.xml` (new)
   - Subscreens:
     - `FindDocument` (enter PO/ASN identifier)
     - `ReceiveLines` (edit actual quantities)
     - `ReviewConfirm` (summary + confirm)

2. **Screen:** `apps/pos/screen/inventory/receiving/ReceiptResult.xml` (new or embedded section)
   - shows receipt session summary, variances, and staging confirmation

> If the project uses a single-screen pattern with transitions and conditional sections, implement as one screen with transitions; otherwise split as above.

### Navigation context
- Breadcrumb: Inventory / Receiving / Receive into Staging
- Back behavior:
  - From ReceiveLines → back to FindDocument (warn if unsaved edits)
  - After Confirm → go to Result; from Result allow “Receive another” (returns to FindDocument)

### User workflows
**Happy path**
1. Receiver opens “Receive into Staging”
2. Enters PO/ASN number → Search
3. System loads expected lines
4. Receiver enters actual received quantities per line (UOM shown; editable only if allowed)
5. Receiver clicks Review → sees variances preview
6. Receiver clicks Confirm Receipt
7. System shows success + resulting states; indicates items are “In Staging”

**Alternate paths**
- PO/ASN not found → show error and remain on FindDocument
- Under/Over receipt → allowed; show variance preview and confirm still enabled
- Backend rejects due to status/permission → show error; remain on editing screen
- Transaction failure → show failure and no local state changes; allow retry

---

## 6. Functional Behavior

### Triggers
- User submits PO/ASN identifier (search)
- User edits actual quantities
- User confirms receipt

### UI actions
- **Search action:** validate identifier non-empty; call load service; render expected lines.
- **Edit action:** per line:
  - input actualQty (numeric, >= 0; decimals allowed only if UOM supports—unknown → Open Question)
  - optionally select actualUom (only if backend allows)
- **Review action:** compute client-side variance preview (expected - actual) for display only; authoritative variance is backend.
- **Confirm action:** submit receipt payload; disable confirm button while in-flight; show spinner.

### State changes (frontend)
- Local screen state: Draft edits → Reviewed → Submitted
- After success:
  - display receipt session/correlation id
  - display line states from backend response
  - allow navigation to result

### Service interactions (frontend to Moqui backend)
- Load expected lines for PO/ASN
- Submit receipt confirmation (atomic operation backend-side):
  - ledger entries created
  - on-hand updated in staging
  - variances created
  - receiving line states updated

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- PO/ASN identifier is required to load lines.
- For each line being received:
  - `actualQty` is required (if line is presented for receiving)
  - `actualQty` must be a number and **>= 0**
- Do not allow manual edits to system-managed outcomes:
  - ledger entries, on-hand, and variance records are not editable in UI (read-only results)

### Enable/disable rules
- **Confirm Receipt** enabled only when:
  - document is loaded
  - all visible lines have valid `actualQty`
  - no in-flight submission
- **Review** enabled when lines are valid; review does not persist.

### Visibility rules
- Default Staging location is displayed (read-only) as the receipt destination.
- Variance preview visible after any line has actual != expected.
- If no lines returned (empty PO/ASN or fully received), show empty-state message and disable confirm.

### Error messaging expectations
- Not found: “PO/ASN not found or not eligible for receiving.”
- Validation: field-level messages, e.g. “Quantity must be 0 or greater.”
- Permission: “You do not have permission to receive inventory.”
- Status conflict: “This document cannot be received in its current status.”
- Transaction failure: “Receipt failed; no inventory was updated. Please retry.”

---

## 8. Data Requirements

### Entities involved (conceptual; frontend reads/writes via services)
- `InventoryLedgerEntry` (created by backend; displayed summary only)
- `InventoryVariance` (created by backend; displayed summary)
- `ReceivingLine` (loaded and updated state by backend)
- `StorageLocation` (staging destination; displayed)
- PO/ASN document + lines (source of expected qty/uom)

### Fields (type, required, defaults)
**Document search**
- `documentType` (enum: PO | ASN) — *unclear if required* (Open Question)
- `documentId` (string) — required
- `siteId` (string) — *may be implicit from user context* (Open Question)

**Receiving line (loaded)**
- `receivingLineId` (string) — required
- `productId` (string) — required
- `productName` (string) — required (display)
- `expectedQty` (decimal) — required
- `expectedUomId` (string) — required
- `state` (string enum) — required (display)
- `stagingLocationId` (string) — required (display; same for all lines)

**Receiving input (editable)**
- `actualQty` (decimal) — required, default = expectedQty (safe UI default)
- `actualUomId` (string) — default = expectedUomId; editable only if backend supports

**Receipt result (read-only)**
- `receiptCorrelationId` (string) — required
- `receivedAt` (datetime UTC) — required
- `receivedByUserId` (string) — required
- Per line:
  - `quantityChange` (decimal) — required
  - `varianceType` (enum SHORTAGE|OVERAGE|NONE) — derived/display
  - `varianceQty` (decimal) — derived/display

### Read-only vs editable by state/role
- Before confirm: only `actualQty` (and possibly `actualUomId`) editable.
- After confirm: everything read-only.
- Users without receiving permission: screen loads may be allowed (view) but confirm must be blocked (exact policy unclear → Open Question).

### Derived/calculated fields (frontend)
- Variance preview:
  - `varianceQtyPreview = actualQty - expectedQty`
  - `varianceTypePreview = OVERAGE if >0 else SHORTAGE if <0 else NONE`

Authoritative variance comes from backend response.

---

## 9. Service Contracts (Frontend Perspective)

> Exact Moqui service names are not provided in inputs; to remain buildable, define expected request/response shape and map to Moqui services once confirmed.

### Load/view calls
1. **Service:** `inventory.receiving.findDocument` (proposed)
   - **Input:** `documentId`, optional `documentType`, optional `siteId`
   - **Output:**
     - `documentId`, `documentType`, `status`
     - `stagingLocationId`, `stagingLocationName`
     - `lines[]` with fields listed in Data Requirements

### Create/update calls
- None (draft edits are client-side until confirm)

### Submit/transition calls
2. **Service:** `inventory.receiving.confirmReceipt` (proposed)
   - **Input:**
     - `documentId`, `documentType`
     - `stagingLocationId` (should match default; backend validates)
     - `lines[]`: `{ receivingLineId, productId, actualQty, actualUomId }`
   - **Output:**
     - `receiptCorrelationId`, `receivedAt`, `receivedByUserId`
     - updated `lines[]` including new `state`
     - `variances[]` summary (or per-line variance fields)
     - optional `ledgerEntryIds[]` or counts

### Error handling expectations
- 400 validation errors → map to field errors when possible; otherwise show banner.
- 403 → show permission banner; disable confirm.
- 404 → show not found message on FindDocument.
- 409 conflict (status changed / already received) → show conflict banner and offer “Reload document”.
- 500/timeout → show retryable error, keep user edits intact.

---

## 10. State Model & Transitions

### Allowed states (ReceivingLineState)
Backend reference suggests:
- `EXPECTED`
- `RECEIVED`
- `RECEIVED_SHORT`
- `RECEIVED_OVER`

(Exact enum values must match backend → Open Question)

### Role-based transitions
- Receiver can transition lines from EXPECTED → one of RECEIVED / RECEIVED_SHORT / RECEIVED_OVER via confirm receipt.
- Other roles not defined (Open Question).

### UI behavior per state
- If line is already in a terminal received state when loaded:
  - show as read-only and exclude from editable inputs
  - show message “Already received”
- Only EXPECTED (or eligible) lines are editable and included in confirm payload.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing/invalid actualQty:
  - block confirm
  - show inline error per line
- Negative qty:
  - inline error; prevent confirm

### Concurrency conflicts
- If backend returns 409 due to document status/lines changed:
  - show banner “Document changed since loaded”
  - provide actions: Reload (re-calls findDocument), Cancel

### Unauthorized access
- If 403 on load: show access denied screen section; no data displayed.
- If 403 on confirm: keep edits, show banner, disable confirm.

### Empty states
- Document found but no receivable lines:
  - show “Nothing to receive” with reason if provided (e.g., fully received)
  - disable confirm

---

## 12. Acceptance Criteria

### Scenario 1: Load receivable document and default staging location
**Given** I am logged in as a Receiver with permission to receive inventory  
**When** I enter a valid PO/ASN identifier and search  
**Then** the system displays the document status and its expected receiving lines  
**And** the destination location is shown as the site’s default Staging location (read-only)

### Scenario 2: Receive exact expected quantity (no variance)
**Given** a document line expects 10 EACH for ITEM-A  
**When** I enter an actual quantity of 10 and confirm receipt  
**Then** the receipt succeeds and a receipt correlation id is shown  
**And** the line state is displayed as Received  
**And** the UI indicates the items are now in Staging  
**And** no variance is shown for that line

### Scenario 3: Short receipt creates variance (UI shows variance result)
**Given** a document line expects 10 EACH for ITEM-A  
**When** I enter an actual quantity of 8 and confirm receipt  
**Then** the receipt succeeds  
**And** the line state is displayed as Received-Short (or equivalent)  
**And** the result includes a SHORTAGE variance of 2 EACH for ITEM-A

### Scenario 4: Over receipt creates variance (UI shows variance result)
**Given** a document line expects 10 EACH for ITEM-A  
**When** I enter an actual quantity of 12 and confirm receipt  
**Then** the receipt succeeds  
**And** the line state is displayed as Received-Over (or equivalent)  
**And** the result includes an OVERAGE variance of 2 EACH for ITEM-A

### Scenario 5: Document not found
**Given** I am on the Receive into Staging screen  
**When** I search for an identifier that does not match an eligible PO/ASN  
**Then** I see a not-found message  
**And** no receiving lines are displayed  
**And** I cannot confirm receipt

### Scenario 6: Validation prevents confirm
**Given** I have loaded a document with at least one receivable line  
**When** I enter a negative quantity for a line  
**Then** I see an inline validation error on that line  
**And** the Confirm Receipt action is disabled

### Scenario 7: Backend conflict on confirm
**Given** I have loaded a document and entered actual quantities  
**When** I confirm receipt and the backend responds with a conflict (409)  
**Then** the UI shows a conflict banner  
**And** I can reload the document  
**And** the UI does not show a success result

---

## 13. Audit & Observability

### User-visible audit data
- On success result, show:
  - `receiptCorrelationId`
  - `receivedAt` (render in user locale; sourced from UTC)
  - `receivedBy` (username/display name if available)
- If backend provides audit trail link/id, display as “View receipt details” (route TBD).

### Status history
- If backend returns line status history, show in expandable section per line; otherwise out-of-scope.

### Traceability expectations
- All submit requests include a client-generated idempotency key or correlation id header if the system supports it (unknown → Open Question).
- Frontend logs (console/network) must not expose sensitive data; show correlation id in success/error banners for support.

---

## 14. Non-Functional UI Requirements
- **Performance:** Load document and render lines within 2s for up to 200 lines (pagination/virtual scroll allowed).
- **Accessibility:** All inputs labeled; errors announced; keyboard navigable table/form.
- **Responsiveness:** Works on tablet receiving station widths; line editing usable without horizontal scrolling where possible.
- **i18n/timezone:** Display `receivedAt` in local timezone; store/receive UTC from backend.
- **Currency:** Not applicable (no cost display).

---

## 15. Applied Safe Defaults
- SD-UX-01 (Empty State): Show a standard empty-state panel when no receivable lines are returned; safe because it does not change domain logic; impacts UX Summary, Error Flows.
- SD-UX-02 (Default Input Value): Default `actualQty` to `expectedQty` when lines load; safe because user can edit and backend remains authoritative; impacts Data Requirements, UX Summary.
- SD-ERR-01 (HTTP→UI Mapping): Map 400/403/404/409/500 to field errors/banners as described; safe because it only affects presentation and does not invent business policy; impacts Service Contracts, Error Flows.

---

## 16. Open Questions
1. **Permissions:** What exact permission(s)/roles gate receiving into staging (load vs confirm)? (Needed for UI gating and error expectations.)
2. **Document identification:** Do users select **PO vs ASN** explicitly, or is the identifier unique enough to auto-detect? (Affects FindDocument UI and service inputs.)
3. **Eligible statuses:** What exact PO/ASN statuses are allowed for receiving (and are partially received documents allowed)? (Needed for consistent UX errors and test cases.)
4. **UOM behavior:** Is `actualUomId` selectable, and if so what conversions are allowed/handled (decimal quantities, pack sizes)? (Cannot assume conversion policy.)
5. **ReceivingLineState enum:** What are the exact state values returned by backend (strings) and which should be treated as terminal/non-editable? (Needed to implement state-based UI.)
6. **Idempotency:** Does the confirm receipt endpoint support idempotency keys to prevent duplicate ledger entries on retry? If yes, what header/field should frontend send? (Critical for safe retry UX.)
7. **Staging location source:** Is staging location determined by **site**, **facility**, **warehouse**, or **user location** context, and how is that context provided in frontend? (Needed to display correct staging destination.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/98


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/98
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Receiver**, I want to receive items into staging so that on-hand increases and put-away can follow.

## Details
- Default to staging location.
- Record qty and UOM; handle over/short.

## Acceptance Criteria
- Ledger entries created for Receive.
- Items visible as 'in staging'.
- Variances recorded.

## Integrations
- Availability updates; workexec may see expected receipts.

## Data / Entities
- InventoryLedgerEntry(Receive), StagingLocation, ReceivingLineState

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