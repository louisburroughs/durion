# [FRONTEND] [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines
## Purpose
Enable users to view inventory availability and ledger history in the context of a specific Work Order Line, deep-linked from Work Order detail. The screen should accept SKU and location context (plus optional work order identifiers) and allow users to manually check availability and review inventory movements. The primary user problem is quickly confirming stock availability and tracing inventory history for a given line without re-entering context.

## Components
- Page header with title: “Inventory Availability” (and navigation to “Ledger”/“History”)
- Context banner (Work Order Line context): Work Order / Work Order Line identifiers (display-only when present)
- Form controls:
  - SKU field (read-only when provided via deep-link; editable otherwise)
  - Location picker (required to query availability)
  - Storage location picker (filtered by selected location)
- Primary actions:
  - “Check Availability” button (manual run)
  - “Refresh” button (re-run current query)
- Availability results panel (summary fields; exact metrics TBD)
- Tabs or secondary navigation:
  - “Availability”
  - “History / Ledger”
- Ledger list (cursor-paginated table)
  - Columns: Entry ID, Timestamp, Type, Quantity, From/To Location (optional), From/To Storage Location (optional), Linkage fields (Work Order / Line / other refs when present)
  - Pagination controls: Next/Previous (cursor-based)
- Ledger entry detail view (drawer or separate page)
- Loading, empty, and error states (for availability and ledger)

## Layout
- Top: Header + breadcrumbs/module nav; optional context banner under header
- Main (two sections): Filters form (top) → Results area (below)
- Results area: [Tabs: Availability | History] with Availability summary panel or Ledger table beneath
- Right/inline: Ledger entry detail shown as drawer/panel when a row is clicked (or navigates to detail page)

## Interaction Flow
1. Entry from Work Order detail (deep-link):
   1. User clicks per-line action “Inventory” / “Availability & History”.
   2. Inventory Availability screen opens with query params including SKU and locationId (and optional work order/work order line identifiers).
   3. On initial load, if required params for availability are present (SKU + locationId), the UI auto-runs exactly one availability request.
   4. UI does not auto-run again unless the user changes inputs or clicks “Check Availability” / “Refresh”.
2. Manual availability check:
   1. User selects/changes Location (required).
   2. User selects Storage Location (optional; picker filtered by selected Location).
   3. User clicks “Check Availability”.
   4. UI calls availability endpoint with current SKU + locationId (and storage location if applicable) and renders results; show loading and empty/error states.
3. History / Ledger browsing (line context):
   1. User switches to “History / Ledger” tab.
   2. UI queries ledger list endpoint with cursor pagination and filters:
      - Primary filter: workOrderLineId (required for “line context” history)
      - Optional locationId filter (applies as OR across from/to semantics)
   3. User paginates using Next/Previous; UI uses cursor params (after/before) to fetch subsequent pages.
4. Ledger entry detail:
   1. User clicks a ledger row.
   2. UI opens detail view and calls ledger detail endpoint for the selected entry ID.
   3. User closes detail to return to list without losing current filters/pagination position.
5. Refresh behavior:
   1. User clicks “Refresh”.
   2. UI re-runs the currently active query (Availability or Ledger) using current inputs and current cursor state (or resets cursor to first page if specified by design).
6. Edge cases:
   1. Missing required params on entry (e.g., no SKU or no locationId): do not auto-run; prompt user to select required inputs.
   2. No current shop/site context: user must select Location before querying.
   3. Changing Location clears/invalidates selected Storage Location and refilters the storage picker.

## Notes
- Preconditions: WorkExec must provide stable identifiers and SKU context; locationId required to query availability; workOrderLineId required for “line context” history.
- Inventory Moqui proxy endpoints must be same-origin; UI should treat them as standard API calls with loading/error handling.
- Acceptance criteria: deep-link with required params triggers exactly one auto-run availability request on load; no further automatic re-runs unless user changes inputs or clicks “Check Availability”.
- Ledger list must support cursor pagination (after/before) and display at least: id, timestamp, type, quantity, optional from/to location and storage location, plus linkage fields (work order/line refs) when present.
- Location filter semantics for ledger are OR across from/to; ensure UI copy/tooltip clarifies this if exposed.
- Requirements are incomplete/TBD in places (e.g., exact availability result fields, exact ledger row shape); design should accommodate flexible columns/fields and graceful empty states.
