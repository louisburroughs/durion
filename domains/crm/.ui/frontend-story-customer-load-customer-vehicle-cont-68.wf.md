# [FRONTEND] [STORY] Customer: Load Customer + Vehicle Context and Billing Rules
## Purpose
Provide a read-only Customer Snapshot panel in the order flow so Service Advisors can quickly view the latest CRM customer context for the selected party. The panel loads party metadata, contacts, vehicles, and CRM-provided billing configuration fields, along with snapshot metadata (retrieved time, source, stale status). It supports explicit refresh and handles merged-party and failure scenarios with recoverable actions.

## Components
- Customer Snapshot panel container (read-only)
- Panel header: “Customer Snapshot” title
- Snapshot metadata row: Retrieved at, Source (CACHE/CRM_API), Stale indicator, Stale since (optional)
- Action button: “Refresh snapshot”
- Party/Account section
  - Fields: partyId (UUID), display name, party type (PERSON/ORGANIZATION), status (enum/string)
- Contacts section
  - Contacts list (rows/cards)
  - Contact attributes: kind/type, value (masked where needed), primary indicator per kind, preferred contact method indicator (if provided)
  - Empty-state text: “No contacts on file.”
- Vehicles section
  - Lightweight vehicles list (rows)
  - Vehicle attributes: year/make/model/trim (if present), VIN (sensitive), license plate (sensitive), other identifiers as provided
  - Empty-state text: “No vehicles associated.”
- Billing Configuration section (read-only)
  - Display-only fields (only when present): invoice delivery rule (enum), booleans/flags, payment terms string, currency (ISO-4217), other CRM-provided billing fields
  - Empty-state text: “Billing configuration unavailable.”
- Non-blocking warnings area (if snapshot warnings array provided)
- Merged-party message banner (on 409 PARTY_MERGED)
  - Text includes mergedToPartyId
  - Action button: “Load merged customer”
- Error state banner (network/5xx without snapshot)
  - Recoverable message + “Retry/Refresh” action
  - Correlation info display (e.g., X-Correlation-Id) when available

## Layout
- Top of panel: Header (title left) + “Refresh snapshot” button (right)
- Below header: Snapshot metadata row + stale warning banner (when stale)
- Main stacked sections:
  - Party/Account (top)
  - Contacts (middle)
  - Vehicles (middle)
  - Billing Configuration (bottom)
- Inline banners appear above sections: merged-party banner or error banner; warnings area near top of content

## Interaction Flow
1. Load on customer selection (success)
   1. When an order draft has a selected customer with valid partyId, request Customer Snapshot via Moqui-proxied endpoint using partyId.
   2. Show loading state in the panel while fetching.
   3. Render party metadata, contacts list (or “No contacts on file.”), vehicles list (or “No vehicles associated.”), and billing configuration fields when present (otherwise “Billing configuration unavailable.”).
   4. Render snapshot metadata: retrievedAt, source (CACHE/CRM_API), stale indicator; show warnings list if provided (non-blocking).
2. Refresh snapshot (explicit)
   1. User clicks “Refresh snapshot.”
   2. Re-request snapshot for the currently selected partyId; show loading state without navigating away.
   3. Update all sections and metadata to match the latest response.
3. Party merged (409 PARTY_MERGED)
   1. If snapshot request returns 409 with errorCode PARTY_MERGED and mergedToPartyId, show merged-party banner with mergedToPartyId.
   2. User clicks “Load merged customer.”
   3. Request snapshot for mergedToPartyId and render it if permitted; otherwise show access/error state.
4. Stale snapshot returned
   1. If response metadata.stale is true, show a clear stale warning.
   2. Display staleSince when provided.
   3. Still render all available snapshot sections (do not block usage).
5. Network/5xx failure without snapshot payload
   1. If request fails and no snapshot is returned, show recoverable error banner.
   2. Provide retry/refresh action to re-attempt the request.
   3. Display correlation identifier if available (e.g., X-Correlation-Id).
   4. Ensure UI does not log or display PII values in error details (contacts, VIN, license plate).

## Notes
- Read-only panel: UI displays CRM snapshot fields as provided; no policy enforcement or inferred defaults for billing rules.
- Empty states must match specified copy for Contacts, Vehicles, and missing Billing Configuration.
- Sensitive data handling: VIN and license plate are treated as sensitive for telemetry; avoid logging/displaying PII in errors and diagnostics.
- Snapshot metadata requirements: retrievedAt (ISO timestamp), source (CACHE/CRM_API), stale boolean, staleSince optional; warnings array (optional) should render as non-blocking warnings.
- Merged-party handling: 409 PARTY_MERGED must present mergedToPartyId and allow user-driven load of merged customer snapshot.
- Access control: snapshot access is permission-gated; UI may optionally hide entry/panel when permission missing, but backend enforcement is required (permission key TBD).
- Loading states: show clear in-panel loading on initial load and refresh; avoid clearing existing data until new data arrives unless required by design.
