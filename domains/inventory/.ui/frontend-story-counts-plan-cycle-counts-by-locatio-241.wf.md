# [FRONTEND] [STORY] Counts: Plan Cycle Counts by Location/Zone
## Purpose
Enable users to create a new Cycle Count Plan by selecting a Location, choosing one or more Zones within that Location, and scheduling a future (or today, per TBD rule) date. Provide clear client-side validation and deterministic backend error display, then confirm successful creation and navigate to a plan view or list. Ensure the UI uses Moqui proxy/service interactions only and surfaces correlation/technical details when available.

## Components
- Page header: “Cycle Count Plans” / “Create Cycle Count Plan”
- Plan List view
  - Plans table/list (server-paged preferred; cursor pagination)
  - “Create Plan” primary button
  - Empty state (no plans)
  - Loading state / skeleton
  - Error banner (page-level)
- Plan Create view (form)
  - Location select (required; populated from Moqui proxy)
  - Zones multi-select / checkbox list (required; populated by selected location)
  - Scheduled date picker (required; cannot be in the past)
  - Optional plan name/description text input (label/name TBD)
  - Inline field error messages
  - Page-level error banner with “Technical details” (correlation id when present)
  - Submit button (“Create plan”)
  - Cancel/back button
  - Loading indicators (locations load, zones load, submit in progress)
- Plan Detail view (optional, if supported)
  - Read-only metadata section: created info returned by API (e.g., created timestamp/created by/id)
  - Summary of selected location, zones, scheduled date, optional name/description
  - Error/forbidden states

## Layout
- Top: Breadcrumbs (Plans > Create) + page title; right-aligned primary action on list page (“Create Plan”)
- Main (List): Filters (optional/TBD) above plans table; pagination controls at bottom
- Main (Create): Single-column form with sections: Location → Zones → Scheduled date → Optional name/description; actions row at bottom (Cancel left, Create right)
- Main (Detail): Summary card at top; metadata block beneath; back link to list

## Interaction Flow
1. User visits Plan List screen.
   1. Call list plans endpoint (TBD) via Moqui; use cursor pagination if server-paged.
   2. Show loading state; on success render list; on empty show empty state with “Create Plan”.
   3. On failure: show page-level error; if 401 redirect to login/session refresh; if 403 show forbidden state.
2. User clicks “Create Plan” (or visits Plan Create screen directly).
   1. Render create form with disabled Zones control until Location selected.
   2. Fetch Locations via Moqui proxy; show loading state for Location select.
   3. If Locations fetch fails: show page-level error; handle 401/403 as above.
3. User selects a Location.
   1. Clear any previously selected zones and any zone-related field errors.
   2. Fetch Zones for the selected location via Moqui proxy (endpoint TBD).
   3. While loading zones: disable zones selector and show inline loading indicator.
   4. If zones fetch fails (including 404/422 if returned): show page-level error and keep zones unselectable until resolved or location changed.
4. User fills Scheduled date and optional name/description.
   1. Client-side validate scheduled date is not in the past (timezone/today rule TBD); show inline error and block submit if invalid.
5. User submits create form.
   1. Client-side validation:
      1. If Location missing: block submit; show “Location is required.”
      2. If no Zones selected: block submit; show “Select at least one zone.”
      3. If scheduled date in past: block submit; show inline error (do not submit).
   2. Call create cycle count plan endpoint via Moqui transition/service (endpoint TBD).
   3. On success:
      1. Show success toast/banner.
      2. Navigate to Plan Detail if supported (fetch by id if needed), else return to Plan List and refresh list.
      3. Display read-only “created” metadata as returned by API (no client-side fabrication).
   4. On failure:
      1. Parse deterministic error schema; map field errors to Location, Zones, Scheduled date, and optional name/description when provided by backend.
      2. Do not override backend wording for server-side validation errors.
      3. If error shape unknown: show generic message “Could not create cycle count plan.”
      4. Include correlation id in “Technical details” when available.
      5. 401: redirect to login/session refresh; 403: render forbidden state without leaking data.

## Notes
- Must use Moqui proxy/services only; Vue must not call inventory backend directly.
- Out of scope: executing counts, editing plan scope after creation (unless backend supports), recurring plans, SKU selection logic beyond displaying backend results, location/zone administration, inventory ledger mutations.
- API shapes/endpoints are partially TBD: zones list, create plan, plan list/detail; UI should be resilient to evolving response shapes while adhering to deterministic error schema conventions.
- Optional name/description field: label and backend constraints TBD; do not enforce max length client-side until confirmed; rely on backend validation and surface field errors deterministically.
- Scheduled date “today” and timezone rule is TBD; implement client-side check with a clear TODO and ensure server-side errors are displayed verbatim.
- Ensure Moqui screen messages area is used consistently for errors; provide a dedicated “Technical details” area for correlation id when present.
- Forbidden/unauthorized handling must follow app convention: 401 triggers login/session refresh; 403 shows forbidden state without exposing plan/location/zone data.
