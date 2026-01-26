# [FRONTEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)
## Purpose
Provide a POS-facing screen to load and view a CRM Snapshot (account, contacts, vehicles, and rules/preferences) using either a Party ID or Vehicle ID. The UI validates identifiers client-side (required + UUID format) before calling a Moqui REST proxy endpoint. Users can refresh the same query, clear the screen back to idle, and see clear error states for authorization, validation, not found, and generic failures.

## Components
- Page header/title: “CRM Snapshot Viewer” (or equivalent within Billing)
- Identifier input form
  - Party ID (UUID) text field with inline validation
  - Vehicle ID (UUID) text field with inline validation
  - Helper/empty-state text: “Enter a Party ID or Vehicle ID to load a snapshot.”
- Primary actions
  - “Load Snapshot” button
  - “Refresh” button (enabled when identifiers exist / after a successful load)
  - “Clear” button
- Status/feedback
  - Inline form error area (missing identifiers, invalid UUID)
  - Loading indicator (request in progress; blocks submit)
  - Error banner/panel for failed requests (403/404/other) with optional correlation ID display
- Snapshot metadata panel (render only if present)
  - Snapshot ID (UUID)
  - Version/timestamp (ISO-8601)
  - Source/system string (if present)
- Snapshot content sections
  - Account summary card (required account object)
  - Contacts list/table (optional array; treat missing as empty)
  - Vehicles list/table (optional array; treat missing as empty)
  - Rules/Preferences section (account-level object; optional fields)
  - “Additional attributes” key/value renderer for any provided object fields (render keys as provided; no inference)

## Layout
- Top: Page title + brief instruction text
- Main: [Party ID field] [Vehicle ID field] [Load Snapshot] [Refresh] [Clear]
- Below form: Status area (inline validation / loading / error banner)
- Below status: Snapshot Metadata (compact row/card) then stacked sections: Account Summary → Contacts → Vehicles → Rules/Preferences

## Interaction Flow
1. Idle state (no request yet)
   1. Show empty form with helper text: “Enter a Party ID or Vehicle ID to load a snapshot.”
   2. “Load Snapshot” disabled until at least one identifier is non-empty and valid UUID.
2. Client-side validation (before any request)
   1. If both Party ID and Vehicle ID are empty: show inline message; do not call service.
   2. If any non-empty identifier is not UUID format: show inline field error; block submit.
3. Load Snapshot (success via partyId)
   1. User enters valid Party ID UUID and clicks “Load Snapshot”.
   2. UI enters request-in-progress state; blocks submit and shows loading indicator.
   3. UI calls Moqui REST proxy endpoint/service with provided identifiers.
   4. On success response: render snapshot sections (Account, Contacts, Vehicles, Rules/Preferences) and show metadata fields when present (id/version/timestamp/source).
4. Load Snapshot (success via vehicleId only)
   1. User enters valid Vehicle ID UUID (Party ID empty) and clicks “Load Snapshot”.
   2. UI calls Moqui proxy; backend resolves primary party.
   3. UI renders returned snapshot without requiring user to select a party.
5. Refresh
   1. User clicks “Refresh”.
   2. UI repeats the last load using the same identifiers.
   3. UI updates displayed snapshot and metadata version/timestamp when present.
6. Clear
   1. User clicks “Clear”.
   2. UI clears both inputs, removes snapshot display, clears errors, and returns to idle state.
7. Backend validation error (400)
   1. If Moqui proxy returns 400: show validation error state (missing identifiers or invalid UUID).
   2. Do not render partial snapshot data from that failed response.
8. Unauthorized (403)
   1. If Moqui proxy returns 403: show not-authorized error state.
   2. Do not retry automatically; do not display partial snapshot data from that failed response.
9. Not found (404)
   1. If Moqui proxy returns 404: show not-found message.
   2. If response indicates “vehicle not found” (specialized case): show specialized message for vehicle lookup failure.
10. Generic failure (other non-2xx)
   1. Show generic failure message.
   2. If correlation ID is present in response header: display it in the error panel for support/debugging.
   3. If a previous snapshot exists, allow it to remain visible as stale while indicating last request failed.

## Notes
- Allowed UI states: idle (no request yet), loading (request in progress), snapshot present, last request failed (may still show stale snapshot if one exists).
- Security constraints:
  - Snapshot retrieval must be via Moqui proxy only (no direct CRM calls from UI).
  - Treat all backend strings as plain text; no HTML rendering.
- Rendering rules:
  - Do not assume optional fields exist; render only what is present per response.
  - Missing arrays (contacts/vehicles) must be treated as empty and rendered as empty-state rows/messages.
  - For any “additional attributes” object: render keys/values exactly as provided; do not infer schema.
- UX constraints:
  - While loading, block submit and show the instruction/feedback appropriately.
  - Inline UUID validation must trigger when a field is non-empty and invalid; block “Load Snapshot”.
- Error handling acceptance:
  - 200: render snapshot.
  - 400: validation error.
  - 403: unauthorized error; no retries; no partial data.
  - 404: not found; specialized message when vehicle not found.
  - Other: generic failure + correlation ID if available.
