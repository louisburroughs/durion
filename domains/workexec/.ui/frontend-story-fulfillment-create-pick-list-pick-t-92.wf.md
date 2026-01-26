# [FRONTEND] [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder
## Purpose
Enable Dispatchers to locate and view the pick list associated with a work order and review its pick tasks in a deterministic backend-provided order. Provide clear status visibility (pick list status and per-task status), plus print-friendly and mobile-friendly rendering. Handle “not generated yet” and timeout/retry states while ensuring all data loads via same-origin Moqui proxy services (no direct frontend-to-backend calls).

## Components
- Page header: “Pick List” title + Work Order identifier context (if available)
- Pick list summary panel (header card)
  - Pick List ID
  - Work Order ID
  - Status badge (Draft / ReadyToPick / …)
  - Created timestamp (display)
  - Optional context fields (if provided): facility/site name, task count, due window/timestamps
- Primary actions
  - Refresh button (retries load)
  - Print button (opens print-friendly view / triggers browser print)
- Pick tasks table/list
  - Columns/fields per row: Product identifier (preferred), Product display name, Quantity + UOM, Suggested location (site/bin/code), Priority, Due time, Task status
  - “Needs review” indicator for tasks without actionable locations (e.g., missing bin)
- Loading state (skeleton/spinner)
- Empty state: “Pick list not available yet” + Refresh action
- Timeout state (after 8 seconds): message + Retry action
- Error state panel (for 401/403/404/409/422/5xx): deterministic error message + correlation/request ID echo (if provided) + Retry where applicable
- Pagination controls (only if backend contract specifies cursor pagination): Next/Previous, loading indicator

## Layout
- Top: Page title + breadcrumbs/back link to Work Order (if available) | Right: Refresh, Print
- Main (stacked): Pick List Summary Card → Pick Tasks List/Table → (optional) Pagination
- Inline ASCII hint: [Header: Title | Refresh | Print] → [Pick List Summary] → [Tasks Table/List] → [Pagination/Status]

## Interaction Flow
1. Navigate to Work Order pick list screen (e.g., from a Work Order detail page or direct URL containing workOrderId).
2. System calls Moqui proxy endpoint to load pick list by workOrderId (same-origin JSON).
3. While waiting, show loading state for header and tasks area.
4. On success:
   1. Render pick list header with Pick List ID, Work Order ID, Status, Created timestamp (and optional context if present).
   2. Render pick tasks in the exact deterministic order provided by backend (either pre-ordered array or by explicit sortKey/order field).
   3. For each task row, display product identifier and display name (no cross-domain product lookup), quantity (and UOM if present), suggested location (site/bin/code), priority, due time, and status.
   4. If a task has no actionable location (e.g., bin is null), mark it as “Needs review” and visually de-emphasize location fields (e.g., “—”).
5. User selects Print:
   1. Switch to print-friendly rendering (or open print view) optimized for paper and mobile; invoke browser print.
6. Alternate path: pick list not generated yet:
   1. If backend indicates not found/not generated (404 or equivalent contract), show empty state: “Pick list not available yet.”
   2. User clicks Refresh → system retries the same Moqui proxy load call.
7. Timeout path:
   1. If the pick list load call does not return within 8 seconds, show timeout state with “Retry.”
   2. User clicks Retry → system retries; on success, render header and tasks as in step 4.
8. Error handling path:
   1. For 401/403: show access/authorization error state (no task data), include request/correlation ID if provided.
   2. For 409/422: show validation/conflict message per deterministic error schema; allow Retry if meaningful.
   3. For 5xx: show server error state with Retry.
9. If tasks are not embedded in pick list response:
   1. After pick list header loads, call Moqui proxy endpoint to fetch tasks by pickListId.
   2. Render tasks when returned; if paginated, use cursor controls to fetch next/prev pages while preserving backend order.

## Notes
- Must use Moqui proxy services only (same-origin); no direct Vue → backend calls.
- Deterministic ordering is backend-authoritative; frontend must not compute route optimization or re-sort beyond honoring provided order/orderKey.
- Required to render header: pickListId, workOrderId, status enum, created timestamp.
- Required per task row: pickTaskId, pickListId, product identifier (preferred for display/deep-link patterns), product display name (strongly preferred), quantity, optional UOM, suggested location fields (site required; bin nullable), optional priority and due time, task status, and either explicit order field or pre-sorted response.
- “Needs review” handling: tasks with missing/nullable actionable location (e.g., bin null) must be clearly indicated and still listed.
- Print-friendly and mobile-friendly rendering are in-scope; picking execution (mark picked/partial/substitutions) is out-of-scope unless backend explicitly provides.
- Observability: include/echo requestId/correlationId from responses in error/timeout UI where available.
- TODO (blocking): finalize Moqui proxy endpoint paths, params, and response shapes for:
  - Get pick list by workOrderId (embedded tasks vs tasksRef)
  - Get pick tasks by pickListId (ordering field vs pre-ordered; pagination cursor contract if large)
  - Deterministic error schema mapping for 401/403/404/409/422/5xx
