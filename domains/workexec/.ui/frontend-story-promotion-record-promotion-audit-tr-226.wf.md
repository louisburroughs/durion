# [FRONTEND] [STORY] Promotion: Record Promotion Audit Trail
## Purpose
Provide a Promotion Audit UI that lets authorized users review promotion events associated with a Work Order or Estimate, including the most recent promotion summary and access to full event details. Enable auditors and operators to trace who promoted what, when, and what artifacts (Work Order, Estimate, Snapshot, Approval) were produced. Ensure consistent navigation and robust error/empty-state handling across entry points and deep links.

## Components
- Promotion Audit section header (within Work Order and Estimate detail screens)
- “Most recent promotion” summary card/row
- “View details” action (link/button)
- Expandable events list/table (for multiple events)
- Event row items: promoting user identifier, event type, timestamp, estimateId, workOrderId, snapshotRef, approvalRef
- Event details view (inline panel or dedicated screen)
- Key-value field list for event details
- Promotion summary subsection (totals/counts) when present
- Optional “Summary details available” indicator for unknown summary keys
- Optional collapsible raw JSON viewer (read-only; permission-gated)
- Navigation actions (enabled/disabled based on references):
  - Open Work Order
  - Open Estimate
  - View Snapshot (if supported)
  - View Approval (if supported)
- States:
  - Loading skeleton/spinner
  - Empty state (no events)
  - Transient error state with Retry
  - Access denied state
  - Not found state

## Layout
- Top: Screen title/context (Work Order detail / Estimate detail / Promotion Audit Event detail)
- Main (Work Order / Estimate): Promotion Audit section placed below primary entity summary, above secondary sections
- Promotion Audit section:
  - Most recent promotion summary (top)
  - Right-aligned “View details” link/button
  - Below: expandable “All promotion events” list/table (collapsed by default if only one event)
- Event details view:
  - Left/main: key-value details + promotion summary totals/counts
  - Right/inline actions: Open Work Order / Open Estimate / View Snapshot / View Approval (disabled with helper text when unavailable)

## Interaction Flow
1. Work Order → Promotion Audit (primary)
   1. User opens Work Order detail screen.
   2. System loads Promotion Audit events for the Work Order.
   3. If events exist, render “Promotion Audit” section with “Most recent promotion” summary showing: promoting user identifier, event timestamp (user timezone), estimateId, workOrderId, plus snapshot/approval references when present.
   4. If promotion summary totals/item counts are provided, render them in the summary (and in details).
   5. User selects “View details” or expands the events list to view all events.
   6. User selects an event to open the event details view (inline panel or dedicated screen).
   7. In details, user can navigate via enabled actions (Open Work Order / Open Estimate / View Snapshot / View Approval).

2. Estimate → Promotion Audit (secondary)
   1. User opens Estimate detail screen.
   2. Promotion Audit section renders most recent promotion (if records exist).
   3. User selects link to the resulting Work Order (when workOrderId exists) and/or opens event details.

3. Deep link: Promotion Audit Event
   1. User opens a Promotion Audit Event detail route/screen.
   2. System loads the event and renders key-value details.
   3. User follows links/actions to Work Order / Estimate / Snapshot / Approval where references exist and screens are supported.

4. Empty and missing-reference behaviors
   1. If API returns empty list or no records, show empty state (“No promotion audit events available”).
   2. If snapshotRef is absent, show “Snapshot reference unavailable” and disable View Snapshot.
   3. If approvalRef is absent, show “Approval reference unavailable” and disable View Approval.

5. Error handling (standard envelope)
   1. If unauthorized (401), follow app auth flow (redirect/login).
   2. If forbidden (403), show access denied state; do not render partial data.
   3. If not found (404), show not found state.
   4. If network/timeout/5xx, show transient error state with Retry.

## Notes
- Display minimum viable event fields (render known fields if present; do not require all):
  - Event identifier (required)
  - Event type (required; expected value like “PROMOTED”)
  - Event timestamp (required; store raw ISO-8601, display in user timezone)
  - Promoting user identifier (required)
  - estimateId (required)
  - workOrderId (required)
  - snapshotRef (optional but expected; show “Snapshot reference unavailable” if missing)
  - approvalRef (optional but expected; show “Approval reference unavailable” if missing)
  - summary/details object (optional JSON): render known keys (totals/counts) and tolerate unknown keys
- Summary rendering rules:
  - Show known numeric totals/counts when present (including optional subfields such as item counts).
  - If unknown keys exist and no known fields can be rendered, show “Summary details available”; optionally allow a collapsible raw JSON view for auditors (read-only), subject to permission.
- “Most recent promotion” summary should always render when records exist; “All events” list/table appears when multiple events exist (expandable/collapsible).
- Navigation actions must be enabled only when the corresponding reference exists; otherwise disabled with explanatory helper text.
- Acceptance criteria focus: Work Order scenario must show most recent event’s promoting user identifier, timestamp, estimateId, workOrderId, snapshot/approval references when present, and promotion summary totals/item counts when provided.
