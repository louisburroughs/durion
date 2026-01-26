# [FRONTEND] [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts
## Purpose
Ensure Estimate and Work Order artifacts emitted by the frontend always include required CRM reference IDs (party, vehicle, contacts) on create and preserve them on update/submit flows. Provide service advisors with a read-only customer sidebar that displays CRM-backed customer/contact details, vehicle/VIN, service history, and payment history. Improve reliability and performance via SWR caching with graceful fallback when CRM is unavailable.

## Components
- Work Order / Estimate header (title, status/state, identifiers)
- Primary action buttons (Create, Update/Save, Submit/Transition, Convert Estimate → Work Order)
- Read-only CRM Reference section (crmPartyId, crmVehicleId, crmContactIds)
- Customer sidebar (read-only)
  - Customer name + contact info
  - Primary vehicle + VIN
  - Service history summary (last 5 work orders)
  - Payment history (last 3 transactions)
  - Link/button: “Open in CRM portal” (external)
- Loading states (skeleton/placeholder for sidebar data)
- Error/fallback banner or inline message for CRM unavailable
- Non-blocking “Using cached data” indicator (optional)
- Confirmation/validation messaging for missing required CRM refs on create

## Layout
- Top: Page header with artifact type (Estimate/Work Order), status, and primary actions (Save/Submit/Convert)
- Main (left/center): Core Estimate/Work Order details form/view (existing fields), plus a read-only “CRM References” panel near top
- Right: Customer sidebar (CRM-backed) with stacked sections (Customer, Vehicle, Service History, Payment History)
- Inline ASCII hint: Top Header/Actions; Main Content (left) | Customer Sidebar (right)

## Interaction Flow
1. Load Estimate or Work Order detail page.
2. Frontend requests CRM-backed sidebar data using SWR:
   1) Serve cached data immediately if present (<100ms target).
   2) Revalidate in background; update sidebar when fresh data arrives.
3. If cache miss, call CRM API; populate sidebar and update cache.
4. If CRM API unavailable:
   1) Use stale cache if available and show fallback notice.
   2) If no cache, display “(Unknown Customer)” and minimal placeholders for other sections.
5. Create Estimate:
   1) User initiates create and submits.
   2) Validate required CRM refs are present: crmPartyId (non-empty), crmVehicleId (non-empty), crmContactIds (non-null array, may be empty).
   3) Send create request body including crmPartyId, crmVehicleId, crmContactIds.
   4) Include required REST header on create (per DECISION-INVENTORY-012).
6. Update Estimate (when allowed by state/permissions):
   1) User edits allowed fields and saves.
   2) Ensure CRM reference fields are not null/omitted; preserve previously stored values (send back or use patch semantics that cannot clear them).
7. Create Work Order:
   1) User initiates create (or from estimate conversion flow) and submits.
   2) Validate required CRM refs as in create estimate.
   3) Send create request body including CRM refs; include required REST header on create (DECISION-INVENTORY-012).
8. Update Work Order (when allowed by state/permissions):
   1) User saves changes.
   2) Preserve CRM reference fields (must not clear via null/omission).
9. Convert Estimate → Work Order:
   1) User triggers conversion (if exposed in UI).
   2) Frontend ensures CRM references are present in conversion request or verifies backend copies them from estimate.
   3) On success, navigate to new Work Order and load sidebar via SWR.

## Notes
- CRM reference fields (Estimate + Work Order):
  - crmPartyId: required on create; read-only; non-empty string.
  - crmVehicleId: required on create; read-only; non-empty string.
  - crmContactIds: required as non-null array on create; read-only; may be empty.
- On update flows, UI must not null/omit previously stored CRM reference fields; contract must be confirmed for update calls.
- Create calls must include the specified REST header on create (DECISION-INVENTORY-012).
- Read-only UX: no editing of CRM-backed customer/contact/payment data; provide link to CRM portal for updates.
- Sidebar content requirements:
  - Customer name + contact info (service advisor reference)
  - Primary vehicle + VIN (if linked)
  - Service history summary (last 5 work orders from WorkExec)
  - Payment history (last 3 transactions from CRM, read-only)
- Test/acceptance fixtures:
  - Load work order → CRM customer details populated.
  - Cache hit → CRM data served from cache (<100ms).
  - Cache miss → CRM API called; cache updated.
  - CRM unavailable → use stale cache; display fallback UI (“(Unknown Customer)” if needed).
- TODO (implementation): confirm whether Estimate → Work Order conversion is a backend action and whether backend copies CRM refs automatically; otherwise include refs explicitly in conversion request.
