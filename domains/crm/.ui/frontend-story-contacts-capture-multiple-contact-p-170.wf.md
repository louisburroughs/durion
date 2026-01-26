# [FRONTEND] [STORY] Contacts: Capture Multiple Contact Points

## Purpose
Enable users to view and manage multiple contact points (Email and Phone) for a Party, including optional labels and a primary indicator per kind. Authorized users can add new contact points and update existing ones, including promoting a contact point to primary while ensuring only one primary per kind. The UI must reflect backend rules such as immutable kind on update and automatic demotion of the prior primary when a new primary is set.

## Components
- Page header: “Contact Points” + Party identifier/context (from route)
- Section tabs or subheader (optional): Party > Contact Points
- Two grouped lists:
  - Email contact points list
  - Phone contact points list
- Contact point list item:
  - Value (email address or phone number)
  - Optional label tag (HOME/WORK/MOBILE/OTHER)
  - Primary badge/indicator
  - Actions menu/buttons (Edit, Make Primary)
- “Add Contact Point” button (global or per-kind)
- Add Contact Point modal/drawer:
  - Kind selector (EMAIL/PHONE) (create-only)
  - Value input (required)
  - Label dropdown (optional)
  - Extension input (phone only; optional)
  - Primary checkbox/toggle (default false)
  - Save / Cancel buttons
- Edit Contact Point modal/drawer:
  - Kind display (read-only)
  - Value input (required)
  - Label dropdown (optional)
  - Extension input (phone only; optional)
  - Primary checkbox/toggle
  - Save / Cancel buttons
- Confirmation modal for “Make Primary”
- Inline validation messages (required fields, format hints)
- Error banner/toast for API errors (400 validation, 403 forbidden, 409 conflict duplicates)
- Loading/empty states for each list

## Layout
- Top: Page title “Contact Points” + Party context
- Main:
  - Left/Top: EMAIL section header + “Add” action; below: email list
  - Left/Below: PHONE section header + “Add” action; below: phone list
- Modals/drawers overlay main content for Add/Edit/Confirm

## Interaction Flow
1. View contact points
   1. User opens Party “Contact Points” screen (PartyId from route/context).
   2. System loads and displays two grouped lists: EMAIL and PHONE.
   3. Each item shows value, optional label tag, and primary indicator.
   4. System ensures UI displays at most one primary per kind (one for EMAIL, one for PHONE).

2. Add a new contact point (non-primary example)
   1. Authorized user clicks “Add Contact Point” (or “Add” within PHONE section).
   2. Add modal opens with fields: Kind, Value, optional Label, optional Extension (phone only), Primary toggle (default off).
   3. User selects Kind=PHONE, Label=MOBILE, Value="555-123-4567", Primary=false; clicks Save.
   4. On success, modal closes and the new contact point appears in the PHONE list.
   5. Existing primary PHONE (if any) remains primary.

3. Edit an existing contact point
   1. User clicks “Edit” on a contact point.
   2. Edit modal opens with Kind shown read-only; user updates Value/Label/Extension/Primary as needed.
   3. User clicks Save; system submits update without Kind.
   4. On success, list item updates in place (including primary indicator if changed).

4. Make Primary action (demotes prior primary)
   1. User clicks “Make Primary” on a non-primary contact point (e.g., Phone B).
   2. Confirmation modal appears (e.g., “Set this as primary PHONE? Current primary will be replaced.”).
   3. User confirms; system updates the contact point with isPrimary=true.
   4. On success, UI shows selected contact point as primary and previously primary contact point as not primary.
   5. UI continues to show only one primary for that kind.

5. Error/edge handling
   1. If API returns 400 validation errors, show field-level messages and keep modal open.
   2. If API returns 403, show an error banner/toast and disable manage actions if appropriate.
   3. If API returns 409 conflict (duplicate), show a clear message and keep modal open for correction.

## Notes
- ContactPoint fields for UI: id (read-only), partyId (context), kind (EMAIL/PHONE; create-only; immutable on update), label (HOME/WORK/MOBILE/OTHER optional), value (required), extension (phone only; ignored/empty for email), isPrimary (default false), createdAt/updatedAt (optional display-only if provided).
- Primary constraint: at most one primary EMAIL and one primary PHONE per party; when setting isPrimary=true, backend atomically demotes prior primary of the same kind—UI should refresh/reconcile both affected items.
- Scenario acceptance: viewing shows both kinds with labels and primary indicators; adding non-primary phone does not change existing primary; “Make Primary” results in exactly one primary for that kind.
- Audit history is not required; createdAt/updatedAt may be displayed if available but no audit trail UI is needed.
- Permissions: viewing requires view permission; add/edit/make-primary requires manage permission; UI should hide/disable manage controls when unauthorized.
- Backend may enforce duplicate constraints; UI must handle conflict responses gracefully.
