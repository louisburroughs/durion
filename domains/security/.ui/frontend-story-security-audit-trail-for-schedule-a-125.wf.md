# [FRONTEND] [STORY] Security: Audit Trail for Schedule and Assignment Changes
## Purpose
Provide a secure Audit Logs search and detail experience for schedule and assignment changes, accessible from multiple contextual entry points (Work Order, Appointment, Mechanic, and optionally scheduling surfaces). Users can filter and review audit events, then drill into a single event’s full details when authorized. The UI must handle pagination, truncated result warnings, and a clear “not found” state for missing audit log IDs.

## Components
- Page header with title “Audit History” / “Audit Logs”
- Context chips/summary (prefilled identifiers from entry point: Work Order, Appointment, Mechanic, optional schedule/assignment IDs)
- Search/filter form
  - Required: Location selector (required)
  - Required: Time range (from/to) (required)
  - Indexed filters (at least one required): e.g., Work Order ID, Appointment ID, Mechanic/User ID, Actor ID/name, Event type/action, Entity type, Assignment/Schedule identifiers, etc.
  - Cross-location toggle/selector (only if permitted; requires additional permission)
  - Apply/Search button
  - Clear/Reset button
- Results list (AuditLog summary rows)
  - Row fields (minimum render set): auditLogId, occurredAt (UTC), event/action, entity type, entity id, location, actor display (name/id), optional summary fields (e.g., reason/source)
- Pagination controls
  - Cursor-based: Next/Previous (using cursor + limit) or offset-based page controls
  - “Results truncated; refine filters” banner when backend indicates truncation
- Audit Log detail view (drawer or separate page)
  - Key fields: id, occurredAt, action/event, entity, location, actor
  - Additional fields: version, effectiveAt (optional), before/after JSON (optional), notes/description (escaped text), sensitive payload (permission-gated), correlation/request id (optional)
  - Deep-link metadata section (optional)
  - Proof fields section (permission-gated): hash/signature/proof strings
  - Back to results button/link
- States
  - Loading skeleton/spinner (list + detail)
  - Empty state (no results)
  - Error state (generic)
  - Not found state (detail 404)

## Layout
- Top: Header (Audit Logs) + Back link (if navigated from a contextual entry point)
- Main (stacked):
  - Context/prefill summary chips
  - Filter form (2–3 columns on desktop; single column on mobile)
  - Results header row (count + truncation banner area)
  - Results list (clickable rows) + pagination at bottom
- Detail: Opens as right-side panel on desktop or full-screen page on mobile; includes Back control at top

## Interaction Flow
1. User opens Audit Logs from an entry point (Work Order / Appointment / Mechanic / optional schedule UI) via “View Audit History”; destination receives identifiers only and enforces authorization.
2. Audit Logs screen loads with prefilled filters (e.g., relevant entity IDs) and prompts user to ensure required filters are set (Location + Time range + at least one indexed filter).
3. User adjusts filters (adds/removes indexed filters, changes time range, selects location; optionally cross-location if permitted) and clicks Search.
4. UI calls search/list endpoint with AND semantics across provided filters; shows loading state, then renders results list.
5. If backend returns truncation indicator, UI displays “Results truncated; refine filters” banner above results.
6. User paginates through results using cursor (Next/Previous) or offset controls depending on backend behavior.
7. User selects a result row to view Audit Log detail; UI navigates to detail view (panel/page) and fetches full record.
8. Detail view renders additional fields when present; permission-gated fields (sensitive payload, proof fields) are shown only when returned/authorized, otherwise hidden.
9. User clicks Back to return to the Audit Logs search screen with filters/results preserved.
10. Edge case (Scenario 10): User navigates directly to an Audit Log detail URL with a specific auditLogId; backend returns 404; UI shows a “Not found” state with a single action to go back to Audit Logs search.

## Notes
- Authorization must be enforced on the destination screens; entry points pass identifiers only (AUD-SEC-011).
- Search requirements: Location and Time range are required; at least one indexed filter is required; filters combine with AND semantics.
- Cross-location searches require an additional permission and a location parameter only when applicable; UI should hide/disable cross-location controls when not permitted.
- Render occurredAt as UTC timestamp; consider user-friendly formatting with explicit UTC label.
- “notes/description” field must be rendered as escaped text (no HTML interpretation) and may be length-limited by backend.
- JSON fields (before/after, optional payload) should be displayed in a readable, collapsible format; handle large payloads gracefully.
- Preserve filter state and pagination position when navigating between list and detail.
- Provide clear empty, loading, error, and 404-not-found states; 404 must include navigation back to search.
