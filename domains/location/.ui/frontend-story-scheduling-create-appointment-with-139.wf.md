# [FRONTEND] [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle
## Purpose
Enable a Service Advisor to create a new scheduled appointment by selecting an existing CRM customer and one of their associated vehicles, then entering requested services and a start/end time window. The form should validate required fields and ensure the selected vehicle belongs to the selected customer. On successful creation, the user is taken to the canonical appointment detail screen showing the newly created appointment and its status.

## Components
- Page header: “Create Appointment”
- Breadcrumb/back link to Scheduling/Appointments list
- Customer lookup/select control (CRM search)
- Vehicle lookup/select control (filtered by selected customer)
- Scheduled time window inputs
  - Start datetime picker/input
  - End datetime picker/input
- Requested services section
  - List of service request line items
  - “Add service” button
  - Per-line service description text input
  - Per-line remove action
- Notes textarea (optional)
- Inline validation/error summary area
- Primary action button: “Create Appointment”
- Secondary action button: “Cancel”
- Loading indicators for CRM lookups and submit
- Confirmation/navigation behavior on success (redirect)

## Layout
- Top: Header + breadcrumb/back
- Main (single column form):
  - Customer (top)
  - Vehicle (below customer)
  - Time window (start/end side-by-side)
  - Requested services (list + add/remove)
  - Notes (bottom)
- Footer/action row (sticky or bottom): Cancel (left) | Create Appointment (right)

## Interaction Flow
1. User opens “Create Appointment” screen.
2. User searches/selects a CRM Customer.
3. System loads/enables Vehicle selector scoped to the chosen customer.
4. User selects a CRM Vehicle from the filtered list.
5. User adds at least one requested service line item and enters a service description for each.
6. User selects/enters Start datetime and End datetime.
7. User optionally enters Notes.
8. User clicks “Create Appointment”.
9. Client validates:
   1. Customer is selected (required).
   2. Vehicle is selected (required).
   3. At least one service request exists and is non-empty.
   4. Start and End are present and End is after Start.
10. System submits create request including crmCustomerId, crmVehicleId, startAt, endAt, notes (if any), and serviceRequests; includes Authorization header.
11. On success (201 Created with appointment id), system navigates to the canonical Appointment Detail screen for that id and displays the appointment status.
12. Edge cases:
   1. CRM unavailable or lookup fails: show inline error and allow retry; disable dependent selectors as needed.
   2. Vehicle not associated with customer (server-validated): show submission error and prompt user to reselect vehicle/customer.
   3. Invalid time window (end <= start): block submit and highlight fields.
   4. Missing required fields: show field-level errors and keep user on form.
   5. Submit fails (non-201): show error summary; keep entered values for correction/resubmission.
   6. Cancel: return to previous screen or appointments list without creating.

## Notes
- Required fields: crmCustomerId, crmVehicleId, startAt, endAt, serviceRequests (must contain at least 1 item).
- Validation constraints:
  - Selected vehicle must belong to selected customer (server-validated; client should also filter vehicles by customer).
  - endAt must be after startAt.
- Datetime format: send ISO-8601; include offset or UTC per API contract (open question/TODO).
- CRM Customer/Vehicle are read-only lookup DTOs; appointment creation should store stable identifiers to preserve context even if CRM data changes later.
- Success criteria: create request includes required fields + Authorization header; response is 201 with id; redirect to appointment detail; status displayed matches created appointment’s status (value TBD by backend contract).
- Requested services entity/shape is TBD (embedded vs separate); UI should support multiple line items and map to serviceRequests array.
- Requirements are incomplete/under review; expect contract discovery updates (e.g., exact endpoint, response payload, status values, error formats).
