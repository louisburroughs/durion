STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Promotions: View Promotion Redemption Records (from Invoice Finalization)

**Primary Persona:** Customer Service Representative (CSR) / Marketing Manager (read-only)

**Business Value:** Provide UI visibility into promotion redemption activity recorded by CRM so staff can support customers, audit usage, and detect potential abuse without querying databases or logs.

---

## 2. Story Intent

### As a / I want / So that
- **As a** CSR or Marketing Manager  
- **I want** to view promotion redemption records associated to a promotion and filter them by work order/invoice/customer/time  
- **So that** I can confirm whether a promotion was redeemed, by whom, and on which invoice/work order, and spot duplicates/abuse patterns.

### In-scope
- Moqui UI screens to **search/list** promotion redemptions.
- A **detail view** of a single redemption record.
- Filters for common identifiers: `promotionId`, `workOrderId`, `invoiceId`, `customerId`, date range.
- Read-only display of relevant redemption fields and any processing/idempotency indicators that are persisted.
- Authorization gating (frontend enforcement + backend error handling display).

### Out-of-scope
- Consuming the `PromotionRedeemed` event (backend responsibility).
- Editing/redacting redemption records.
- Enforcing usage limits at invoice finalization (policy/ownership is unclear; see Open Questions).
- Creating promotions or configuring limits.

---

## 3. Actors & Stakeholders
- **CSR (primary user):** Verifies redemption presence for customer support.
- **Marketing Manager (stakeholder):** Reviews usage/efficacy and identifies suspicious patterns.
- **System (CRM backend):** Source of truth for redemption records.
- **Workorder Execution domain (external):** Emits redemption events; referenced identifiers appear in UI for cross-system support.

---

## 4. Preconditions & Dependencies
- User is authenticated in the frontend.
- User has required permission to view promotion/redemption data (exact permission name TBD; see Open Questions).
- CRM backend exposes **read APIs** (or Moqui services) to list and retrieve redemption records. If not available, this story is blocked.
- Redemption records exist (or empty-state UI is shown).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Promotions area (TBD route): “Promotion Redemptions” list.
- From a Promotion detail screen (if exists): “View redemptions for this promotion”.

### Screens to create/modify
1. **Screen:** `apps/pos/screen/crm/promotions/PromotionRedemptions.xml` (list/search)
2. **Screen:** `apps/pos/screen/crm/promotions/PromotionRedemptionDetail.xml` (detail)
3. **Optional integration point:** add navigation link from existing Promotions screen (if present in repo).

### Navigation context
- Breadcrumb: CRM → Promotions → Redemptions
- Support direct deep-links:
  - `/crm/promotions/redemptions` (list)
  - `/crm/promotions/redemptions/:promotionRedemptionId` (detail)
  - `/crm/promotions/:promotionId/redemptions` (filtered list shortcut)

### User workflows
- **Happy path (list):**
  1. User opens Redemptions list.
  2. Applies filters (e.g., promotionId + date range).
  3. Views paginated results; selects a row.
  4. Navigates to Detail screen.
- **Alternate paths:**
  - Deep-link with `workOrderId` or `invoiceId` query param pre-fills filters.
  - No results → show empty state with guidance to adjust filters.
  - Backend returns 403 → show “Not authorized” state.
  - Backend unavailable → show retry.

---

## 6. Functional Behavior

### Triggers
- Enter list screen: load initial list (either empty until filter applied, or last-used filters; see Safe Defaults).
- Change filter / submit search: reload list.
- Enter detail screen: load redemption record by ID.

### UI actions
- Search form submit.
- Clear filters.
- Click row → open detail.
- Copy-to-clipboard for IDs (optional but helpful; if not supported by existing component patterns, omit).

### State changes
- Frontend-only view state: loading, loaded, empty, error.
- No domain state mutations (read-only).

### Service interactions
- Call backend to:
  - List redemptions with filters + pagination + sort.
  - Fetch a single redemption by `promotionRedemptionId`.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filter inputs:
  - `promotionId`, `workOrderId`, `invoiceId`, `customerId`, `promotionRedemptionId`: non-empty string; trim whitespace.
  - Date range: `from <= to`; show inline error if invalid.
- Do **not** validate or infer idempotency logic in UI; only display what backend persists.

### Enable/disable rules
- Search button disabled while request in-flight.
- Clear filters enabled when any filter has a value.

### Visibility rules
- Columns/fields shown only if present in response (to avoid implying missing backend fields).
- If backend provides a “duplicate/processing status” indicator, render it; otherwise omit.

### Error messaging expectations
- 400 validation error: show message “Invalid filter” plus backend-provided detail.
- 403: “You don’t have permission to view promotion redemptions.”
- 404 on detail: “Redemption not found.”
- 500/network: generic failure with retry.

---

## 8. Data Requirements

> Note: Backend story references multiple entities (`PromotionUsage`, `RedemptionEvent`, `ProcessingLog`) but the backend reference content primarily defines `PromotionRedemption`. Frontend must not invent fields; it should bind to the API schema once confirmed.

### Entities involved (frontend read models)
- `PromotionRedemption` (primary)
- `Promotion` (optional: for context display if list is filtered by promotionId; only if API returns it)
- Optional processing/idempotency read model if available:
  - `ProcessingLog` or a `dedupeKey/eventId` field

### Fields to display (minimum viable)
For each redemption record:
- `promotionRedemptionId` (string/UUID, required, read-only)
- `promotionId` (string/UUID, required, read-only)
- `customerId` (string/UUID, optional/nullable per backend; read-only)
- `workOrderId` (string/UUID or external ID, required, read-only)
- `invoiceId` (string/UUID or external ID, optional/nullable; read-only)
- `redemptionTimestamp` (datetime, optional/nullable; read-only)
- `createdAt` (datetime, optional/nullable; read-only)

### Derived/calculated fields
- None (do not calculate counters/limits on frontend).

---

## 9. Service Contracts (Frontend Perspective)

> Moqui frontend typically interacts via Moqui screens/actions/services. Exact service names/endpoints are not provided in inputs, so these are **placeholders** that must be confirmed.

### Load/view calls
1. **List redemptions**
   - **Service (proposed):** `crm.promotionRedemption.search`
   - **Inputs:**
     - `promotionId?`, `customerId?`, `workOrderId?`, `invoiceId?`
     - `fromDate?`, `toDate?` (ISO-8601)
     - `pageIndex` (0-based) / `pageSize`
     - `orderBy` default `-redemptionTimestamp` or `-createdAt` (TBD)
   - **Outputs:**
     - `items[]` (PromotionRedemption)
     - `totalCount`

2. **Get redemption detail**
   - **Service (proposed):** `crm.promotionRedemption.get`
   - **Inputs:** `promotionRedemptionId` (required)
   - **Outputs:** `item` (PromotionRedemption)

### Create/update calls
- None (read-only story).

### Submit/transition calls
- None.

### Error handling expectations
- Standard mapping:
  - 400 → field-level error when possible, else banner.
  - 403/401 → route to auth/forbidden screen per app conventions.
  - 404 → not found state on detail.
  - 409 → display conflict (unlikely for read; handle generically).

---

## 10. State Model & Transitions

### Allowed states
- UI view states: `idle`, `loading`, `loaded`, `empty`, `error`.

### Role-based transitions
- If unauthorized:
  - list/detail screens transition to `error(forbidden)` and must not display any partial data.

### UI behavior per state
- `loading`: show skeleton/spinner, disable search controls that would create duplicate requests.
- `empty`: show “No redemptions found” + suggestion to broaden filters.
- `error`: show retry; preserve filter values.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- User selects `fromDate > toDate`:
  - Prevent search; show inline error at date range controls.

### Backend validation failures (server-side 400)
- Backend rejects filter combination:
  - Show banner with backend message; keep form state.

### Concurrency conflicts
- Not applicable for read-only; still handle stale detail:
  - If detail record deleted between list and click → detail returns 404; show not-found state.

### Unauthorized access
- 401: redirect to login (per app convention).
- 403: show forbidden message; do not retry automatically.

### Empty states
- No matching redemptions: empty table/list state, not an error.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View list of redemptions filtered by promotionId
**Given** I am authenticated and authorized to view promotion redemptions  
**When** I navigate to the Promotion Redemptions list screen  
**And** I enter a valid `promotionId` filter and submit search  
**Then** the system requests redemption records from the backend using the provided filter  
**And** I see a paginated list of matching redemption records including `promotionRedemptionId`, `promotionId`, `workOrderId`, and `invoiceId` (if present)

### Scenario 2: No results returns empty state
**Given** I am authenticated and authorized to view promotion redemptions  
**When** I search with filters that match no redemption records  
**Then** I see an empty-state message indicating no results  
**And** no error banner is shown

### Scenario 3: View redemption detail
**Given** I am viewing the Promotion Redemptions list with at least one result  
**When** I select a redemption record  
**Then** I navigate to the redemption detail screen  
**And** the system loads the redemption by `promotionRedemptionId`  
**And** I see read-only fields for `promotionId`, `workOrderId`, `invoiceId` (if present), and timestamps (if present)

### Scenario 4: Unauthorized access is blocked
**Given** I am authenticated but not authorized to view promotion redemptions  
**When** I navigate to the Promotion Redemptions list screen  
**Then** the backend returns 403 Forbidden  
**And** the UI displays a not-authorized message  
**And** no redemption data is displayed

### Scenario 5: Invalid date range prevented client-side
**Given** I am on the Promotion Redemptions list screen  
**When** I set `fromDate` later than `toDate`  
**And** I attempt to submit search  
**Then** the UI blocks the request  
**And** I see an inline validation message indicating the date range is invalid

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided by backend): `createdAt` and `redemptionTimestamp`.
- Display identifiers needed for traceability: `promotionId`, `workOrderId`, `invoiceId`, `customerId`.

### Status history
- Not applicable unless backend returns processing status/attempts; if such fields exist, render them as read-only in detail.

### Traceability expectations
- Each backend call should include correlation ID headers if that’s a project convention (TBD). Frontend should log (non-PII) failures with request context (screen, filters excluding PII).

---

## 14. Non-Functional UI Requirements
- **Performance:** List screen should support pagination; avoid loading unbounded results.
- **Accessibility:** All form controls labeled; table supports keyboard navigation; error messages announced (Quasar accessibility patterns).
- **Responsiveness:** Works on tablet width; filters collapse/stack vertically.
- **i18n/timezone:** Render timestamps in user locale/timezone if app supports it; otherwise ISO display (needs confirmation). Currency not applicable.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-results state with guidance; safe as UI-only ergonomics; impacts UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-PAGINATION: Paginate list results with configurable page size; safe as it does not alter domain logic; impacts UX Summary, Service Contracts, Non-Functional.
- SD-ERR-STD-MAPPING: Map HTTP 400/401/403/404/500 to standard banners/states; safe because it follows backend signals; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Frontend scope confirmation:** This story is primarily backend/event-driven. What exact frontend user experience is expected—(A) a read-only redemption viewer (as specified above), (B) a promotion detail enhancement showing redemption count/history, or (C) no UI at all (purely backend)?  
2. **API/service contract:** What are the actual Moqui service names or REST endpoints for listing and retrieving `PromotionRedemption` records (including pagination, sorting, and filter parameters)?  
3. **Authorization model:** What permission/role(s) are required to view promotion redemptions in the POS frontend (e.g., `crm.promotions.read`, `crm.redemptions.read`), and should Marketing vs CSR have different access?  
4. **Data model alignment:** Which entity is the canonical read model exposed to the frontend: `PromotionRedemption` only, or also `PromotionUsage`, `RedemptionEvent`, `ProcessingLog`? What fields from these are available/required in UI?  
5. **Usage limits enforcement visibility:** Acceptance criteria mention “Usage limits enforced when configured.” Should the frontend display any “limit reached” indicators for a promotion, and if yes, what is the backend-provided field/source (CRM vs Workorder Execution)?  
6. **Identifier formats:** Are `workOrderId` and `invoiceId` UUIDs generated within this system, or external human-readable references? This affects validation and display formatting.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotions: Record Promotion Redemption from Invoicing  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/158  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Record Promotion Redemption from Invoicing

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **to record when a promotion is redeemed against a finalized invoice/workorder** so that **we can track usage and prevent abuse**.

## Details
- On invoice finalization, Workorder Execution emits PromotionRedeemed.
- CRM records redemption once (idempotent) and updates counters.

## Acceptance Criteria
- Redemption recorded once.
- Usage limits enforced when configured.
- Redemption links to Workorder/Invoice reference.

## Integration Points (Workorder Execution)
- Workorder Execution emits PromotionRedeemed; CRM consumes and updates usage.

## Data / Entities
- PromotionUsage
- RedemptionEvent
- ProcessingLog

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