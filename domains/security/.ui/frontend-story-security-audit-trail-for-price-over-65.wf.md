# [FRONTEND] [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations
## Purpose
Provide a read-only Audit Trail UI for financial exceptions (price overrides, refunds, cancellations) so authorized users can search, filter, and review who did what and when, with reasons and record references. Enable drilldowns from Order and/or Invoice detail screens into a pre-filtered audit list. Support CSV export (preferably asynchronous) while enforcing append-only behavior and deny-by-default permissions with canonical Security error handling.

## Components
- Global navigation entry: Security → Audit Trail (Financial Exceptions)
- Audit List page
  - Page header/title + brief description (“Read-only audit trail for financial exceptions”)
  - Filter panel
    - Date range: From date, To date
    - Event type dropdown (enum)
    - Actor search input (user id / display name if supported)
    - Order ID input
    - Invoice ID input
    - Location/Terminal input (only if supported)
    - Actions: Apply filters, Clear filters
  - Results table (read-only)
    - Columns: Event type, Timestamp, Actor, Reason summary, References (Order/Invoice/Payment)
    - Default sort indicator: Timestamp desc
    - Row action: View details (or row click)
  - Pagination controls (page, page size if supported)
  - Export action: Export CSV (disabled/hidden without permission)
  - Empty state message: “No audit entries match your filters”
- Audit Detail page (read-only)
  - Summary header: Event type + timestamp
  - Fields: Actor, Timestamp, Event type, Reason (full), Reference identifiers
  - Reference links (Order/Invoice/Payment) shown only if identifier exists and user has access
  - Optional sensitive/raw payload section (only with explicit permission; otherwise hidden or replaced with redaction message if backend supports)
  - Back to results
- Inline error/alert component using Security error envelope (standardized message + optional details)
- “Not authorized” access state (no data leakage)

## Layout
- Top: App header + breadcrumbs (e.g., Security / Audit Trail / Financial Exceptions)
- Left: Main navigation (includes Security section)
- Main (Audit List): [Header] → [Filters panel] → [Export button aligned right] → [Results table] → [Pagination + status]
- Main (Audit Detail): [Header + Back] → [Read-only field grid] → [Reference links] → [Optional sensitive section]
- Footer area: reserved for global toasts/errors (if applicable)

## Interaction Flow
1. Navigate to Security → Audit Trail (Financial Exceptions).
2. System checks view permission (deny-by-default).
3. If unauthorized: show “Not authorized” and do not reveal whether entries exist.
4. If authorized: request audit list from audit service with pagination; render table sorted by most recent first.
5. User adjusts filters (date range, event type, actor, order/invoice, location/terminal) and clicks Apply; list refreshes with the selected criteria.
6. If no results: show “No audit entries match your filters” and keep filters visible for adjustment.
7. User selects a row (View details / row click); UI requests audit entry detail by id and navigates to detail view.
8. Detail view shows curated read-only fields (actor, timestamp, event type, reason, references); no edit/delete actions anywhere.
9. Reference links (Order/Invoice/Payment) appear only when identifiers exist and user has access to the target screen; otherwise show plain text or omit.
10. From Order Detail: user clicks “View Financial Exception Audit”; navigate to Audit List with Order ID pre-filled and applied; list query includes the order filter.
11. From Invoice Detail (if implemented similarly): user clicks “View Financial Exception Audit”; navigate to Audit List with Invoice ID pre-filled and applied.
12. Export: user clicks Export CSV (only with export permission); UI triggers export endpoint (async preferred) and shows progress/confirmation; on completion, provide download link or initiate download.
13. Error handling: for any list/detail/export failure, display canonical Security error envelope messaging; avoid leaking data on 403/404.

## Notes
- Append-only enforcement: UI must not expose edit/delete/modify actions for audit entries (read-only everywhere).
- Permissions (deny-by-default):
  - View audit trail permission required to access list/detail screens.
  - Export permission required to see/enable Export action.
  - View sensitive/raw payload permission required to show any raw/redacted payload content; otherwise hide or show “Redacted: insufficient permissions” if backend provides a redaction indicator.
- Canonical error handling must follow the Security error envelope per DECISION-INVENTORY-015; ensure 403/404 messaging is fail-secure (no disclosure).
- Drilldown requirement: deep link from Order Detail → Audit Trail pre-filtered to that order (and similarly for Invoice if supported).
- Backend gaps impacting UI (must be handled defensively):
  - Pagination may be missing; UI should still present pagination controls but be prepared for large result sets and consider client-side safeguards (e.g., warn if too many results).
  - No enforced result count/date range/export size limits; recommend UI constraints (e.g., default date range, max 90 days) and clear messaging if backend later adds 413/422.
  - Permission checks and redaction may not be implemented server-side; UI must still gate actions/visibility, but true protection requires backend enforcement.
- Export pattern: asynchronous export preferred (DECISION-INVENTORY-014); if only synchronous CSV is available, show loading state and handle timeouts gracefully.
- Domain ownership: audit entries come from the audit domain read model (not Security); UI should treat data as read-only and avoid implying write-side ownership.
