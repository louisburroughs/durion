# [FRONTEND] [STORY] Accounting: Ingest WorkCompleted Event
## Purpose
Provide Accounting Operations users a UI to monitor ingestion status for WorkCompleted accounting events, including validation errors, duplicates, and processing outcomes. Enable safe retry/replay of retry-eligible failed events without engineering intervention, ensuring workorders become invoice-eligible on time. Support permission-gated access to payload visibility and retry actions, with clear conflict/authorization feedback.

## Components
- Page header: “Event Ingestion” / “WorkCompleted Events” with breadcrumb (Accounting → Integrations → Event Ingestion)
- Search/filter bar
  - Service selector (placeholder)
  - Filters: Event ID, Workorder ID, Status, Event Type (default/expected: WorkCompleted), Date range (created/received)
  - Sort control (field + direction)
  - Pagination controls (page size, next/prev)
- Results table (ingestion records list)
  - Columns (subset): Event ID, Event Type, Status, Workorder ID, Created/Received At, Updated/Processed At, Retry Count, Error Summary
  - Row actions: View Details
- Record detail drawer/page
  - Read-only fields: recordId, eventId, eventType, status, workorderId, journalEntryId, createdAt, updatedAt, processedAt, retryCount, errorCode/type, errorMessage (sanitized), retryEligible indicator
  - Tabs/sections: Overview, Processing Log, Payload (permission-gated)
- Retry action area
  - Retry button (enabled only if retry-eligible and user has permission)
  - Optional “Retry reason” text area (max 500 chars)
  - Confirmation modal (Retry / Cancel)
  - In-flight state (button disabled + spinner)
- Notifications/toasts for success/error/conflict/unauthorized
- Empty state and error state components (no results, service error)

## Layout
- Top: Breadcrumb + page title; right side optional refresh button
- Below top: Search/filter bar (single row; collapsible on small screens)
- Main: Results table with pagination footer
- Detail: Right-side drawer or separate detail page (responsive: drawer on desktop, full page on mobile)
  - [Header/Filters]
  - [Table/List] | [Detail Drawer: Tabs + Actions]

## Interaction Flow
1. Navigate via Accounting → Integrations → Event Ingestion to the WorkCompleted events ingestion screen.
2. Use filters (Event ID, Workorder ID, Status, Event Type, date range) and submit search; table updates with paginated results and applied sorting.
3. Click a record row (or “View Details”) to open the detail view showing read-only ingestion fields and current status.
4. View status outcomes and messages:
   1) Validation failure shows: “Event payload failed validation; cannot be processed.”
   2) Not found shows: “Workorder referenced by event was not found.”
   3) Duplicate conflict shows: “Duplicate event ID detected; already processed or conflicting payload.”
   4) WIP reconciliation failure shows: “WIP reconciliation failed; retry may be available.”
5. If user has payload permission, open the Payload tab to view the event payload JSON; otherwise show a permission notice and hide/redact payload content.
6. Retry eligible failure (Scenario 4):
   1) Detail view indicates retry-eligible status (FAILED or SUSPENDED).
   2) User clicks Retry; confirmation modal appears with optional Retry reason input.
   3) On confirm, UI calls retry endpoint with record identifier (and retryReason if provided).
   4) Retry button is disabled while request is in-flight.
   5) On success, show acknowledgment toast and refresh detail (and list row) to reflect updated status.
7. Retry rejected due to conflict (Scenario 5):
   1) User clicks Retry; backend returns HTTP 409.
   2) UI shows conflict message (already processed/queued; no longer retryable).
   3) UI refreshes record detail to display current status and disables/hides Retry if now not eligible.
8. Unauthorized user cannot retry (Scenario 6):
   1) User without retry permission opens a failed record.
   2) Retry action is hidden or disabled per convention.
   3) If a retry attempt is triggered via UI controls, show authorization error message and keep status unchanged.
9. Processing log:
   1) User opens Processing Log tab; UI calls processing-log endpoint.
   2) Display chronological entries (timestamps, step/status, messages) with empty state if none.

## Notes
- Framework/tech: Vue 3 + TypeScript + Quasar components; integrate with Moqui backend; responsive and accessible (keyboard navigation, ARIA for modals/toasts, readable error text).
- Entity basis: AccountingEvent (eventId, eventType, payload, status, journalEntryId, errorMessage); statuses follow enum lifecycle (RECEIVED → PROCESSING → PROCESSED/FAILED/SUSPENDED).
- Retry policy constraints:
  - Retriable: FAILED, SUSPENDED
  - Not retriable: SCHEMA_VALIDATION_FAILED, DUPLICATE_EVENT (ensure Retry hidden/disabled with explanatory hint)
- Permissions:
  - View list/detail requires view token; payload tab requires accounting:workcompleted_events:view_payload
  - Retry button requires accounting:workcompleted_events:retry
- Retry reason: optional, max 500 chars; include in request for audit trail.
- Navigation placement: Accounting → Integrations → Event Ingestion (use Moqui navigation conventions for module/screen root).
- Error handling: sanitize displayed errorMessage; show clear, user-facing messages; handle service/network failures with retryable UI state.
- TODO (requirements risk): confirm exact field names for ingestion record (several fields unspecified/unclear) and finalize search inputs/service selector behavior.
