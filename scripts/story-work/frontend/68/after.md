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

# 1. Story Header

## Title
[FRONTEND] [STORY] Customer: Load Customer + Vehicle Context and Billing Rules (Customer Snapshot Panel)

## Primary Persona
Service Advisor (POS user creating/managing an order)

## Business Value
Ensure order creation has fast, consistent access to customer account details, contacts, vehicles, and billing rule configuration so advisors can make correct billing/communication choices and downstream flows can reference billing rules without repeated lookups.

---

# 2. Story Intent

## As a / I want / So that
As a **Service Advisor**, I want to **load and view a Customer Snapshot** (account, contacts, vehicles, billing rules) for the selected customer so that **order creation can respect billing rules and preferred contacts** and I can validate the context quickly.

## In-scope
- Frontend screen/component(s) to fetch and display a **CustomerSnapshot** for a selected `accountId`
- Display: account metadata, contacts (including preferred contact method), vehicles (lightweight), billing rules (read-only)
- Explicit “Refresh” behavior and visible “data source”/stale indicators (cache vs API, stale if applicable)
- Error and empty states for missing contacts/vehicles/billing rules and for 403/404/5xx
- Moqui screen routing + transitions + service calls (frontend perspective)
- Hooks/structure for downstream “billing rule enforcement” (UI-level exposure of rule values to order flow), without implementing billing enforcement policies

## Out-of-scope
- Editing customer/contact/vehicle/billing rules (CRM is SoR; read-only here)
- Implementing business enforcement of billing rules (e.g., blocking checkout if PO missing) beyond exposing the rule values to the order draft/session
- Customer search/selection UI (assumed to exist elsewhere; this story starts from a known/selected `accountId`)
- Vehicle service history, tire specs, attachments, warranty, etc. (explicitly out of snapshot scope)
- Event-driven cache invalidation implementation (frontend will respond to metadata and manual refresh; backend handles invalidation)

---

# 3. Actors & Stakeholders

## Actors
- **Service Advisor**: Views snapshot while creating/managing an order
- **POS Frontend (Moqui screens)**: Initiates snapshot retrieval and renders it

## Stakeholders / Integrations
- **CRM backend**: Provides snapshot API (authoritative payload)
- **Workexec / Order creation flow**: Consumer of snapshot reference and billingRules values
- **Billing/Pricing domains**: Enforcement/meaning of rules (not implemented here)

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in POS
- User has permission to view the selected customer account (backend enforces; frontend must handle 403)
- A customer account has been selected, providing an `accountId` (UUID)

## Dependencies
- A reachable backend endpoint/service for “Get Customer Snapshot by accountId” that returns the schema described in backend story reference (CustomerSnapshot with `metadata.source`, `metadata.stale`, etc.)
- POS has an order draft/session concept to store a snapshot reference or the snapshot itself for reuse in order flow (exact storage location TBD → Open Questions)

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From an **Order Draft / Order Create** screen when a customer is selected
- From a **Customer context panel** within the order workflow (side panel / section; layout not prescribed)

## Screens to create/modify
1. **Modify** existing Order Create / Order Draft screen to include a “Customer Snapshot” section that:
   - Accepts `accountId` (from selection) as input parameter
   - Loads snapshot on entry or when account changes
2. **Create (if needed)** a dedicated screen for customer context viewing:
   - Example: `apps/pos/screen/order/CustomerSnapshot.xml` (name TBD)
   - Used as an includable screenlet in order create

## Navigation context
- Screen parameters: `accountId` (required for load), optional `orderId`/`orderDraftId` (if available) for storing snapshot reference
- Add a “Refresh snapshot” user action within the snapshot section

## User workflows

### Happy path
1. Advisor selects a customer (account)
2. Snapshot section loads and shows:
   - Account name/type/status
   - Contacts list with primary/preferred indicators
   - Vehicles list (VIN/description/unit/license plate)
   - Billing rules summary (PO required, tax exempt, payment terms, credit hold, etc.)
3. Advisor optionally clicks “Refresh” to fetch a fresh snapshot
4. Snapshot values are available to the order draft/session as a read-only context/hook

### Alternate paths
- Account has no contacts → show empty contacts state
- Account has no vehicles → show empty vehicles state
- Billing rules missing/partial → show defaults + warning indicator (based on payload content/metadata; do not invent policy)
- CRM unavailable → show stale snapshot indicator if returned as stale; otherwise show error with retry/refresh option
- Unauthorized (403) → show permission error and hide details

---

# 6. Functional Behavior

## Triggers
- Screen enter with `accountId`
- `accountId` changes (user selects a different customer)
- User clicks “Refresh snapshot”

## UI actions
- Load state: show loading indicator for snapshot panel
- Render snapshot sections when loaded
- Refresh action re-fetches snapshot (bypassing any client-side cache if present)
- Provide a “Copy identifiers” action (accountId, vehicleId) if standard in project (safe UX default only if already a pattern; otherwise omit)

## State changes (frontend)
- `snapshotLoadState`: `idle | loading | loaded | error`
- `snapshot`: populated with last successful payload
- `lastError`: structured error info for display/logging
- Optional: `snapshotRef` stored into order draft/session (see Open Questions)

## Service interactions (frontend ↔ backend)
- Call backend service/endpoint to fetch snapshot by `accountId`
- Pass auth context automatically via session
- Handle:
  - 200: render payload
  - 404: show “Customer account not found”
  - 403: show “No permission”
  - 5xx/timeout: show error; if payload includes stale snapshot use it and show warning

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- `accountId` must be present and must look like a UUID before attempting load
  - If missing/invalid: do not call backend; show inline error “Customer account is required”
- Do not attempt to “fix” or default domain data values on the client (billing rule defaults are backend responsibility per reference)

## Enable/disable rules
- “Refresh” enabled only when:
  - `accountId` present AND not currently loading
- If 403 unauthorized:
  - Disable refresh and hide all customer details; show only the permission message

## Visibility rules
- Contacts section visible even if empty, but shows “No contacts on file”
- Vehicles section visible even if empty, but shows “No vehicles associated”
- Billing rules section visible even if missing; if missing in payload, show “Billing rules not configured (defaults applied)” ONLY if backend indicates defaults were applied (see Open Questions on how signaled)

## Error messaging expectations
- 404: “Customer account not found. Verify the selected customer.”
- 403: “You do not have permission to view this customer.”
- Network/5xx: “Unable to load customer snapshot. Try again.” plus “Show details” expandable for a correlationId if available (do not show stack traces)

---

# 8. Data Requirements

## Entities involved (frontend read models)
- `CustomerSnapshot` (read-only view model)
- Nested: `contacts[]`, `vehicles[]`, `billingRules`, `metadata`

## Fields (type, required, defaults)
From backend reference schema (frontend must tolerate nullables):

### CustomerSnapshot
- `accountId`: UUID (required)
- `accountName`: string (required)
- `accountType`: enum `INDIVIDUAL|COMMERCIAL|FLEET` (required)
- `accountStatus`: enum `ACTIVE|INACTIVE|SUSPENDED` (required)
- `contacts`: array (required; may be empty)
- `vehicles`: array (required; may be empty)
- `billingRules`: object (required if backend always returns after defaulting; otherwise optional)
- `metadata`: object (required)
  - `retrievedAt`: ISO timestamp (required)
  - `source`: enum `CACHE|CRM_API` (required)
  - `stale`: boolean (required)
  - `staleSince`: ISO timestamp (optional)

### Contact
- `contactId`: UUID
- `name`: string
- `role`: string
- `phone`: string (optional)
- `email`: string (optional)
- `preferredContactMethod`: enum `PHONE|EMAIL|SMS|PORTAL|MAIL` (optional)
- `preferredFor`: list enum (optional)
- `doNotContact`: boolean (optional)
- `isPrimary`: boolean (required/expected)

### Vehicle
- `vehicleId`: UUID
- `vin`: string (expected 17 chars; display as-is)
- `make`: string
- `model`: string
- `year`: integer
- `mileage`: integer (optional)
- `description`: string (optional)
- `unitNumber`: string (optional)
- `licensePlate`: string (optional)

### BillingRules
- `poRequired`: boolean
- `taxExempt`: boolean
- `paymentTerms`: string
- `creditLimit`: decimal (optional)
- `creditHold`: boolean
- `invoiceDeliveryMethod`: enum `EMAIL|MAIL|PORTAL|EDI`
- `billingAddressId`: UUID (optional)
- `autoPayEnabled`: boolean
- `discountPolicyRef`: string (optional)
- `currency`: string ISO-4217 (optional)
- `extensions`: map (optional)

## Read-only vs editable by state/role
- All snapshot fields are **read-only** in this story (no edits)

## Derived/calculated fields (frontend only)
- “Preferred contact label” derived for display using backend-provided `isPrimary` / `preferredContactMethod` (do not re-run domain priority logic beyond simple highlighting; backend should provide stable ordering)
- “Stale badge” derived from `metadata.stale == true`

---

# 9. Service Contracts (Frontend Perspective)

> Note: exact endpoint paths are not provided in frontend issue; must be confirmed.

## Load/view calls
- **Operation**: Get customer snapshot
- **Input**: `accountId` (UUID)
- **Output**: `CustomerSnapshot` JSON as defined above
- **Headers** (if supported): accept JSON; include auth session; correlation id response header should be captured if present

## Create/update calls
- none (read-only)

## Submit/transition calls
- Optional: store snapshot reference into order draft/session (Moqui service) (TBD → Open Questions)

## Error handling expectations
- Map HTTP statuses:
  - 200 → render
  - 400 → show validation error (likely only if backend rejects accountId format)
  - 403 → permission error state
  - 404 → not found state
  - 409 → concurrency/conflict (if backend uses ETag) show “Updated elsewhere; refresh”
  - 5xx/timeout → error; if response includes usable snapshot marked stale, render with warning

---

# 10. State Model & Transitions

## Allowed states (UI component)
- `idle`: no account selected yet
- `loading`: fetching snapshot
- `loaded`: snapshot displayed
- `error`: load failed (with message)

## Role-based transitions
- Service Advisor:
  - `idle → loading` on selection
  - `loaded → loading` on refresh
- Unauthorized user (403):
  - `loading → error(unauthorized)` and remains blocked from viewing details

## UI behavior per state
- `idle`: show “Select a customer to view details”
- `loading`: disable refresh, show spinner
- `loaded`: show sections + badges (source/stale)
- `error`: show error banner + retry (unless unauthorized)

---

# 11. Alternate / Error Flows

## Validation failures
- Missing/invalid `accountId`:
  - Do not call backend
  - Show inline message and keep snapshot panel in `idle` or `error` (implementation choice; must be consistent)

## Concurrency conflicts
- If backend supports ETag/snapshot version and returns 409:
  - Show message “Customer data changed; refresh snapshot”
  - Provide refresh action

## Unauthorized access
- On 403:
  - Clear any previously loaded snapshot from UI (avoid data leakage when switching accounts)
  - Show permission message only

## Empty states
- Contacts empty: show empty state; no placeholder PII
- Vehicles empty: show empty state
- Billing rules missing: show “Not configured” only if backend indicates; otherwise show generic “Unavailable”

---

# 12. Acceptance Criteria

## Scenario 1: Load snapshot on customer selection (success)
Given I am a Service Advisor with permission to view customer account A  
And I navigate to an order draft where account A is selected  
When the Customer Snapshot section loads  
Then the UI requests the customer snapshot for account A  
And displays account metadata, contacts (may be empty), vehicles (may be empty), and billing rules  
And displays snapshot metadata including retrieved timestamp and source (CACHE or CRM_API)

## Scenario 2: Refresh snapshot
Given a customer snapshot for account A is displayed  
When I click “Refresh snapshot”  
Then the UI re-requests the snapshot for account A  
And updates the displayed data and metadata to match the latest response  
And shows a loading state while the refresh is in progress

## Scenario 3: Customer not found (404)
Given I attempt to load a snapshot for accountId X that does not exist  
When the backend responds with 404  
Then the UI shows “Customer account not found”  
And does not display any prior customer’s snapshot data

## Scenario 4: Unauthorized (403)
Given I do not have permission to view account A  
When the UI requests the snapshot for account A  
And the backend responds with 403  
Then the UI shows “You do not have permission to view this customer”  
And no customer details (contacts/vehicles/billing rules) are rendered

## Scenario 5: CRM/backend unavailable with stale snapshot returned
Given account A has a previously cached snapshot  
And the backend returns a snapshot response where metadata.stale is true  
When the UI renders the snapshot  
Then the UI shows a clear warning that the data is stale  
And displays staleSince when provided  
And still renders available snapshot sections

## Scenario 6: Network/5xx failure without snapshot
Given the backend is unavailable and no snapshot payload is returned  
When the snapshot request fails  
Then the UI shows a recoverable error message and a retry/refresh action  
And logs the failure with correlation information if available

---

# 13. Audit & Observability

## User-visible audit data
- Display `metadata.retrievedAt`, `metadata.source`, and stale indicator in the UI (non-PII)
- Do not display internal cache keys unless explicitly approved

## Status history
- Track last load time and last error in the UI state (not persisted unless order draft storage is defined)

## Traceability expectations
- Frontend logs/telemetry should include:
  - `accountId` (UUID)
  - result: success/error + HTTP status
  - latency (ms)
  - correlationId (if provided by backend)
- Avoid logging contact details (PII) in client logs

---

# 14. Non-Functional UI Requirements

- **Performance**: Snapshot panel should render within 2 seconds after response; show loading immediately on request start
- **Accessibility**: Loading and error states announced to screen readers; buttons have accessible labels
- **Responsiveness**: Works on tablet/desktop typical for POS
- **i18n/timezone**: Display timestamps in store/user timezone using standard app formatting (do not invent new formatting rules)
- **Security**: Do not cache PII in localStorage unless project convention explicitly allows (unknown → Open Question)

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATES: Provide explicit empty states for contacts/vehicles sections; safe because it affects only UI ergonomics and does not change domain policy. Impacted sections: UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-LOADING-RETRY: Standard loading indicator + retry action on transient failures; safe because it is generic error handling behavior. Impacted sections: Functional Behavior, Alternate/Error Flows, Acceptance Criteria.
- SD-OBS-CLIENT-LOG-SANITIZATION: Log only identifiers/status/correlationId and avoid PII; safe because it reduces risk and aligns with secure logging guidance. Impacted sections: Audit & Observability, Non-Functional UI Requirements.

---

# 16. Open Questions

1. What is the **exact Moqui service name and/or REST path** the frontend should call to retrieve `CustomerSnapshot` by `accountId` (and expected request/response envelope if not raw JSON)?
2. Where should the frontend store the snapshot for order creation: **order draft entity**, **server-side session**, or **client-only state**? If persisted, what is the entity/service contract for `orderDraft.customerSnapshotRef`?
3. How does the backend signal that **billing rule defaults were applied** (e.g., a flag in `metadata`, a warnings array, or always-returned fully-defaulted `billingRules`) so the UI can message accurately without guessing?
4. Is there an established convention for handling **stale snapshot** UI in this project (badge text, blocking vs non-blocking warning), and should any actions be disabled when `metadata.stale=true`?
5. Are there **role/permission scopes** the frontend should check client-side (in addition to backend 403 handling), or is backend-only authorization the standard?
6. Is client-side persistence (e.g., **localStorage**) allowed for snapshot caching, or must snapshot data remain in-memory only?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Customer: Load Customer + Vehicle Context and Billing Rules — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/68

Labels: frontend, story-implementation, payment

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want a customer snapshot so that order creation respects billing rules and contacts.

## Details
- Show account, individuals, contacts, preferred contact method.
- Show vehicles and VIN/description.
- Show billing rules (PO required, tax exemption, terms).

## Acceptance Criteria
- Snapshot loads for selected customer.
- Billing rule enforcement hooks present.
- Cached/updated appropriately.

## Integrations
- CRM provides snapshot API; workexec can use same refs.

## Data / Entities
- CustomerSnapshot, VehicleRef, BillingRuleRef

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale