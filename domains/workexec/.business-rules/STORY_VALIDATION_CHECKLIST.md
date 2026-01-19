# workexec Story Validation Checklist

This checklist is intended for engineers and reviewers to validate story implementations within the `workexec` domain. It is updated to incorporate validation needs from new frontend stories (admin substitute rules, priced substitution picker, operational/assignment context, dispatch board dashboard, and event-driven appointment status timeline).

---

## Scope/Ownership
- [ ] Confirm the story is correctly labeled `domain:workexec` and does not belong to another domain (e.g., inventory/product admin, shop management, people/timekeeping).
- [ ] Verify the owning system-of-record for each feature is explicit (e.g., Shopmgr for operational context; Workexec for work order state; People service for availability).
- [ ] Verify Moqui screen placement and routing follow repo conventions (screen paths, menu taxonomy, URL parameters).
- [ ] Confirm cross-domain dependencies are documented and coordinated (Shopmgr endpoints, People availability, Workexec events).
- [ ] Verify out-of-scope behaviors are not accidentally implemented (e.g., no policy authoring in substitution picker; no dispatch mutations on dispatch board; no event emission from frontend unless specified).

## Data Model & Validation
- [ ] Verify all UI forms enforce required fields before submit (client-side) and still handle backend validation errors (server authoritative).
- [ ] Verify enum fields are validated against the canonical backend values (no frontend-invented enums/casing).
- [ ] Verify ID fields are validated for presence and basic format (non-empty; no whitespace) without assuming UUID vs numeric unless contract states it.
- [ ] Verify date/time inputs validate ordering and format (e.g., `effectiveFrom <= effectiveTo`; dispatch board `date` is `YYYY-MM-DD`).
- [ ] Verify substitute link creation/update validation:
  - [ ] `partId` required.
  - [ ] `substitutePartId` required.
  - [ ] `partId != substitutePartId`.
  - [ ] `substituteType` required and limited to allowed values.
  - [ ] `isAutoSuggest` treated as boolean; default behavior matches backend (do not assume defaults if unknown).
  - [ ] `priority` is integer and within any backend-defined bounds (if provided).
- [ ] Verify optimistic concurrency fields are present where required:
  - [ ] SubstituteLink update includes `version`.
  - [ ] Operational context override includes required concurrency token/version if backend requires it.
- [ ] Verify “pre-start editability” gating is based on authoritative work order status values returned by backend (not hardcoded guesses).
- [ ] Verify substitution picker gating:
  - [ ] Apply is disabled unless a candidate is selected.
  - [ ] Apply is disabled when price is unavailable unless backend indicates manual price is permitted.
  - [ ] “Find substitutes” is disabled only when backend provides an explicit eligibility flag; otherwise allow and handle backend rejection.
- [ ] Verify event-driven appointment timeline storage (if implemented in Moqui entities):
  - [ ] Timeline entries are append-only (no UI edit/delete).
  - [ ] `reopenFlag` is monotonic (once true, never cleared).
  - [ ] Timeline entry uniqueness is enforced by idempotency key (e.g., `sourceEventId` unique per appointment).

## API Contract
- [ ] Verify all frontend calls use the canonical endpoint paths and methods (no guessing; update code/docs once contracts confirmed).
- [ ] Verify request payloads match backend schema exactly (field names, nesting, casing).
- [ ] Verify response handling supports both “return updated resource” and “ack + re-fetch” patterns where backend is ambiguous.
- [ ] Verify list/search screens use pagination and filtering only if backend supports it; otherwise implement “query by partId” UX as designed.
- [ ] Verify substitute link CRUD behaviors:
  - [ ] Create uses `POST` and handles `201` with returned `id` and `version`.
  - [ ] Update uses `PUT` and handles `200` with updated `version`.
  - [ ] Deactivate uses `DELETE` and treats `200` or `204` as success.
  - [ ] Query substitutes uses `GET /parts/{partId}/substitutes` with supported query params only.
- [ ] Verify substitution picker contracts:
  - [ ] Candidate fetch endpoint supports identifying the target line (work order vs estimate) without leaking internal IDs.
  - [ ] Apply endpoint returns enough data to refresh UI deterministically (updated line or requires re-fetch).
- [ ] Verify dispatch board contracts:
  - [ ] Dispatch board endpoint accepts `locationId` and `date` and returns an `asOf`/timestamp if required for stale detection.
  - [ ] People availability endpoint contract is correct and merged safely (no assumptions about optional fields).
- [ ] Verify error handling uses the standard error envelope (field errors, stable error codes, correlationId) once confirmed.
- [ ] Verify HTTP status handling is consistent and user-visible messaging is mapped for: `400`, `401`, `403`, `404`, `409`, `5xx`.

## Events & Idempotency
- [ ] Verify create/submit actions that can be double-submitted are protected:
  - [ ] If supported, frontend sends `Idempotency-Key` on create operations (e.g., SubstituteLink create, promote/submit actions if present).
  - [ ] UI disables submit buttons while request is in-flight to reduce duplicates.
- [ ] Verify optimistic concurrency conflicts (`409`) are handled with a clear “refresh and retry” UX and do not silently overwrite.
- [ ] Verify event ingestion (appointment status updates) is idempotent:
  - [ ] Duplicate event deliveries do not create duplicate timeline entries.
  - [ ] Out-of-order events apply precedence rules (e.g., INVOICED supersedes COMPLETED; CANCELLED terminal except reopen).
  - [ ] Orphaned events (no mapping) are recorded for ops review and do not create phantom appointments.
- [ ] Verify event processing is atomic (appointment status update + timeline insert succeed/fail together).
- [ ] Verify event identifiers used for idempotency are globally unique or a documented composite key is used.

## Security
- [ ] Verify all screens/actions require authentication and follow existing session handling patterns.
- [ ] Verify authorization is enforced for all mutation actions:
  - [ ] SubstituteLink create/update/deactivate requires appropriate permission/role.
  - [ ] Operational context override requires privileged permission.
  - [ ] Assignment context edit requires permission and is also state-gated.
  - [ ] Substitution apply requires permission; manual price override requires `ENTER_MANUAL_PRICE` (or canonical equivalent).
  - [ ] Dispatch board view is restricted by role and/or location membership.
  - [ ] Ops/event failure screens are restricted to admin/ops roles.
- [ ] Verify the UI does not rely solely on “hide button” for security; backend 403 must be handled safely.
- [ ] Verify sensitive data is not logged client-side (no raw event payloads, no tokens/links, no signature strokes if present elsewhere).
- [ ] Verify error messages shown to users do not expose internal stack traces, SQL errors, or upstream service details.
- [ ] Verify any webhook/consumer endpoint for event ingestion is protected with an approved mechanism (mTLS/JWT/shared secret) and rejects unauthenticated calls.
- [ ] Verify PII display policy is followed on dashboards (dispatch board, appointment detail): only show allowed customer/person fields per repo policy.

## Observability
- [ ] Verify frontend propagates correlation/request IDs if the repo has a standard header (and surfaces correlationId from error payloads when present).
- [ ] Verify structured logging/telemetry exists for key user actions and failures (without PII):
  - [ ] SubstituteLink CRUD attempts and outcomes.
  - [ ] Substitution candidate fetch/apply outcomes.
  - [ ] Operational context load/override outcomes.
  - [ ] Dispatch board fetch durations and failure rates.
- [ ] Verify dispatch board includes “Last updated” (client) and “As of” (server) timestamps when available.
- [ ] Verify event ingestion processing logs include `eventId`, `eventType`, `workOrderId`, `appointmentId` (if resolved), and failure reason.
- [ ] Verify metrics/timers exist to validate dispatch board SLA targets (P50/P95/P99) if required by story.

## Performance & Failure Modes
- [ ] Verify list/detail screens do not block primary content on secondary calls (e.g., work order renders even if operational context fails).
- [ ] Verify dispatch board polling:
  - [ ] Poll interval is 30s while screen is active/visible.
  - [ ] Polling stops on navigation away/unmount to prevent leaks.
  - [ ] Backoff is applied on repeated failures to avoid request storms.
- [ ] Verify stale/offline behavior:
  - [ ] Cached last-success payload remains visible when appropriate.
  - [ ] “Stale” indicator triggers after defined threshold (e.g., >2 minutes since last success).
- [ ] Verify substitution picker performance:
  - [ ] Candidate list renders efficiently for expected sizes (<50).
  - [ ] Apply action prevents double-click duplicates and shows progress state.
- [ ] Verify upstream dependency failures degrade gracefully:
  - [ ] People availability failure does not block dispatch board work order rendering.
  - [ ] Shopmgr operational context failure does not block work order detail.
- [ ] Verify concurrency/race handling:
  - [ ] Assignment/operational context edits fail safely if work starts mid-edit (backend 409/400 handled; UI refreshes).
  - [ ] Substitution apply fails safely if line changed mid-dialog (backend 409 handled; UI refreshes).

## Testing
- [ ] Unit tests cover UI validation rules (required fields, self-substitute prevention, date range validation).
- [ ] Integration tests (or contract tests) validate API request/response shapes for each endpoint used.
- [ ] Tests cover HTTP error mapping and UX outcomes for `400/401/403/404/409/5xx`.
- [ ] Tests cover optimistic concurrency flows (stale `version` → 409 → refresh).
- [ ] Tests cover idempotency behaviors:
  - [ ] UI prevents double-submit while in-flight.
  - [ ] Event ingestion duplicate event does not duplicate timeline.
- [ ] Tests cover dispatch board polling lifecycle (start/stop on mount/unmount; backoff on failures).
- [ ] Tests cover timezone/currency formatting using backend-provided currency codes and shop/user timezone policy (once defined).
- [ ] Security tests verify unauthorized users cannot access mutation controls and that 403 is handled without state corruption.
- [ ] Accessibility checks for dialogs and forms (keyboard navigation, focus trap for substitution picker dialog, ARIA labels for errors).

## Documentation
- [ ] Document canonical Moqui screen routes/paths for:
  - [ ] Work Order detail
  - [ ] Estimate detail
  - [ ] Appointment detail
  - [ ] Reporting → Dispatch Board
  - [ ] Admin Rules → Substitutes
- [ ] Document API endpoints used by each screen, including query params, request/response examples, and error envelope.
- [ ] Document permission/role requirements and how the frontend determines capabilities (session claims vs endpoint).
- [ ] Document status enum mappings used in UI gating (work order “started” statuses; appointment status mapping table for events).
- [ ] Document idempotency strategy:
  - [ ] When frontend sends `Idempotency-Key`
  - [ ] What keys are used for event ingestion dedupe
- [ ] Document failure-mode UX (stale data behavior, offline read-only behavior, partial data rendering).
- [ ] Document any audit fields displayed (created/updated metadata, version, correlationId) and their source.

---

## Open Questions to Resolve
- [ ] Is `domain:workexec` the correct ownership for substitute relationship admin UI, or should it be inventory/product domain?
- [ ] What endpoint/screen should be used for part lookup (search by SKU/name) instead of manual ID entry, and what is the response shape?
- [ ] Is there a `GET /api/v1/substitutes` list/search endpoint with filters/pagination, or must the UI be “query by partId” only?
- [ ] What is the standard backend error payload schema for 400/409 (field errors, stable error codes, correlationId, existingResourceId on duplicates)?
- [ ] Should the frontend generate and send `Idempotency-Key` by default for create calls (e.g., SubstituteLink create)?
- [ ] On SubstituteLink update, are `partId` and `substitutePartId` immutable? If editable, how are uniqueness conflicts handled?
- [ ] Do backend defaults apply when omitted (`priority=100`, `isAutoSuggest=false`), or must UI always send explicit values?
- [ ] Is there an API to fetch SubstituteLink audit trail entries for display, or only created/updated metadata?
- [ ] For priced substitution picker: what are the exact endpoints/payloads for (a) fetching candidates for WO part line, (b) fetching candidates for estimate part line, and (c) applying a substitute?
- [ ] Is substitution picker in scope for both Estimates and Work Orders, or Work Orders only?
- [ ] How does the frontend determine “original part is unavailable” (explicit line field vs backend-enforced on apply)?
- [ ] How does backend signal permission `ENTER_MANUAL_PRICE` (capability flag in payload vs 403 vs separate endpoint)?
- [ ] Should candidate responses include unavailable candidates with statuses, or only available ones?
- [ ] For operational context: what are the canonical work order statuses that count as “work started” for edit/override gating?
- [ ] Are overrides allowed after start for managers, disallowed entirely, or handled via versioned future context (lock-at-start semantics)?
- [ ] Does operational context override require `actorId` and/or `version` in the request? If yes, what are the exact field names and how does frontend obtain actor identity?
- [ ] What is the override success response shape (returns updated context vs ack requiring GET) and success status code (200 vs 201)?
- [ ] What is the definition of “team” (assignedMechanics only vs separate team entity)?
- [ ] Dispatch board: what is the exact endpoint path and schema for `DispatchBoardView` and `ExceptionIndicator` (including enums and IDs)?
- [ ] Does dispatch board endpoint already aggregate mechanic availability and bay occupancy, or must frontend call People availability separately and merge?
- [ ] Are “appointments” separate entities from work orders in the dispatch board payload? If separate, what endpoint supplies them?
- [ ] What roles/permissions may view Dispatch Board (location membership only vs explicit permission like `DISPATCH_VIEW`)?
- [ ] Are exception indicators computed entirely by backend, or does frontend compute any (e.g., overdue starts)? If frontend computes, what are the exact rules?
- [ ] Event ingestion: what is the delivery mechanism in this repo (webhook endpoint, broker consumer, polling job/inbox table)?
- [ ] Are Appointment, WorkOrderAppointmentMapping, and timeline entities defined in this repo (exact Moqui entity names/fields), or provided by another service?
- [ ] What auth mechanism protects event ingestion, and which roles can access the ops failure screen?
- [ ] Should orphaned/invalid events be stored in Moqui DB for review, an external DLQ, or both?
- [ ] Is `eventId` globally unique and stable across retries? If not, what composite idempotency key should be used?
- [ ] Does `InvoiceIssued` arrive as a distinct event type or as a status within `WorkorderStatusChanged`, and what fields are present?
