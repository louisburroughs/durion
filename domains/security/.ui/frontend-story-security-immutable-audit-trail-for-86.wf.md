# [FRONTEND] [STORY] Security: Immutable Audit Trail for Movements and Workorder Links
## Purpose
Provide a read-only, immutable Audit Log search and detail experience so authorized users can trace movements and work order link events with strong guardrails. Users must be able to filter within a mandatory UTC date range (max 90 days) and at least one indexed identifier, then view paginated results and drill into event detail. The UI must enforce permission-gated visibility (detail, raw payload, proof fields, export, cross-location) while mirroring backend validation and scoping rules.

## Components
- Global page header: “Audit Logs”
- Contextual entry links (where applicable): “View Audit Logs” from Movement, Work Order, Product, User/Mechanic detail screens
- Filter form (AuditLogSearch)
  - Date/time range inputs: From (local), To (local) with validation messaging
  - Indexed filter inputs (at least one required)
    - Work Order ID, Appointment ID, Mechanic ID, Movement ID
    - Product ID, Part Number
    - Actor ID
    - Event Type dropdown (populated from backend capability list)
    - Aggregate ID, Correlation ID
    - Reason Code dropdown (if labels endpoint available) or exact-match text input fallback
  - Location filter (permission-gated)
    - Multi-select Locations (only if cross-location permission enabled; populated from locations endpoint)
    - Otherwise hidden (implicit session location scope)
  - Buttons: Search, Reset/Clear (optional), Export CSV (permission-gated)
- Results table (paginated)
  - Columns (conditionally rendered if present in response): Occurred At, Event Type, Actor (displayName or id), Aggregate Type, Aggregate ID, Location (id/name), Reason (code/display), Correlation ID (if present)
  - Row action: click to “View Detail”
  - Pagination controls (page, page size) and sort control (e.g., occurredAt desc)
- Audit Log Detail screen (read-only)
  - Summary header: Event ID, Occurred At, Event Type, Aggregate Type/ID
  - Read-only field groups: Actor, Location, Reason, Correlation/Trace, Deep-link metadata (if present)
  - Raw payload section (permission-gated; safe-rendered; collapsed by default optional)
  - Proof fields section (permission-gated; “Provided (not verified)” unless verification result present)
- Export status screen/panel
  - Export job list or single job view: status, requestedAt, requestedBy, progress, result link when succeeded
  - Manual refresh + auto-poll indicator; error display

## Layout
- Top: Page title + optional breadcrumb (e.g., from Movement/Work Order context)
- Main (stacked):
  - Filter form card (top)
  - Actions row (Search/Export) aligned right; validation hints inline
  - Results table (middle) with pagination footer
- Detail view: replaces main content or opens as routed page; back link to results preserving last filters
- Export status: separate routed screen or right-side panel linked from Export action

## Interaction Flow
1. Open Audit Logs
   1. User navigates via main nav (if available) or contextual “View Audit Logs” link from Movement/Work Order/Product/User detail.
   2. If opened contextually, pre-fill the corresponding indexed filter (movementId, workOrderId, productId/partNumber, actorId/mechanicId) and keep location scope implicit unless cross-location is enabled.
2. Search audit logs (primary flow)
   1. User selects From/To date-times (local); UI validates required fields and converts to UTC for request.
   2. User provides at least one indexed filter (e.g., movementId).
   3. If cross-location mode is available and selected, user must choose one or more locations; otherwise location selector is hidden.
   4. Search button becomes enabled only when guardrails pass (date range valid, within 90 days, and at least one indexed filter; plus locations if required).
   5. On Search, UI calls GET `/audit/logs/search` with fromUtc, toUtc, filters, pagination, and sorting; handles 400 field errors inline, 403 as access denied, 429 as rate limit message.
   6. UI renders paginated results; columns appear only for fields present in response.
3. View audit log detail
   1. User clicks a result row; UI routes to detail with eventId.
   2. UI calls GET `/audit/logs/detail?eventId=...`.
   3. UI renders all returned fields read-only; no create/edit/delete controls.
   4. If user lacks `audit:payload:view`, rawPayload section is not shown even if present.
   5. Proof fields (e.g., device/user agent/IP equivalents) display only with required permission; label as “Provided (not verified)” unless backend returns verification status.
4. Export CSV
   1. Export button is visible/enabled only if user has export permission and a successful search has been executed with a valid filter set; disabled while an export request is in-flight.
   2. On Export, UI submits export job using the last valid search filters (same guardrails); show job created state and navigate/show Export Status.
   3. Export Status screen polls per UI convention; user can manually refresh; when SUCCEEDED, show downloadable file name/digest and completion time; on failure, show error state and allow retry (re-uses last filters).
5. Edge cases
   1. If user attempts search with >90-day window or missing indexed filter, show inline validation and keep Search disabled.
   2. If backend omits optional fields, hide corresponding columns/sections rather than showing empty placeholders.
   3. If record not found (404) on detail, show “Not found or not in scope” and provide back navigation.

## Notes
- Guardrails mirrored in UI (backend is source of truth): mandatory UTC date range, max 90 days, and at least one indexed filter beyond date range; tenant may lower max window.
- Permissions gate:
  - List requires `audit:log:view`; detail requires `audit:log:view-detail`.
  - Raw payload requires `audit:payload:view` (hide section entirely without it).
  - Proof fields require dedicated permission; display read-only with “Provided (not verified)” unless verification is explicitly returned.
  - Export requires export permission; only enabled after a successful valid search; prevent duplicate export submissions while pending.
  - Cross-location location multi-select only shown/usable with cross-location permission; otherwise implicit session location scoping.
- Event Type dropdown and Reason Code labels depend on backend capability endpoints; if reason labels unavailable, use exact-match free-text input.
- Results list must support pagination and sorting (default occurredAt desc); preserve last search state when navigating to/from detail.
- All audit data is immutable and read-only; UI must not provide edit/delete affordances anywhere.
