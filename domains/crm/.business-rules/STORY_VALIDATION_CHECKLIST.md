# CRM Story Validation Checklist

This checklist is intended for engineers and reviewers to validate story implementations within the CRM domain. It covers key areas to ensure correctness, security, observability, and maintainability—updated to include validation needs introduced by the new CRM frontend stories (integration monitoring, promotion redemptions viewer, CRM snapshot viewer, vehicle ingestion log UI, vehicle lookup, vehicle care preferences, contact points/preferences/merge/account/contact roles).

---

## Scope/Ownership
- [ ] Verify the story is correctly labeled `domain:crm` and CRM is the system-of-record for the data being displayed/edited (e.g., snapshot, vehicles, preferences, redemptions, ingestion logs).
- [ ] Confirm whether the UI is **read-only** vs **mutating** (e.g., integration logs and redemptions are read-only; vehicle care preferences are create/update).
- [ ] Confirm the primary persona(s) and intended entry points exist in the POS navigation (e.g., Integration Admin menu, CRM tools, Vehicle profile, Estimate/Work Order screens).
- [ ] Verify out-of-scope operator actions are not accidentally implemented (e.g., “reprocess DLQ”, “acknowledge”, “mark resolved”) unless explicitly approved.
- [ ] Confirm cross-domain dependencies are documented and respected (Workorder Execution event references, Billing snapshot consumers).
- [ ] Verify screen routing/deep-link behavior is defined and consistent (list → detail, query params preserved, direct detail URL handling).
- [ ] Confirm ownership of “contact roles” and “commercial account creation” is resolved if labels conflict with other domains (payment/billing vs CRM).

---

## Data Model & Validation
- [ ] Verify all identifiers accepted from user input are validated and trimmed (e.g., `eventId`, `partyId`, `vehicleId` must be UUID when required; free-text search query trimmed).
- [ ] Verify client-side validation blocks invalid requests where specified:
  - [ ] Date range validation: `end >= start` for list filters (processing logs, suspense, redemptions).
  - [ ] UUID format validation for `eventId` filters and snapshot inputs (`partyId`, `vehicleId`).
  - [ ] “At least one identifier required” validation for CRM snapshot (`partyId` or `vehicleId`).
  - [ ] Vehicle care preferences: `rotationInterval` must be an integer when provided.
- [ ] Verify enum fields are validated against allowed values and rendered safely:
  - [ ] ProcessingLog statuses (e.g., `SUCCESS`, `FAILURE`, `SKIPPED_DUPLICATE`, `ERROR_VALIDATION`, `ERROR_NOT_FOUND`, `PENDING_REVIEW`, `DUPLICATE` depending on screen).
  - [ ] Snapshot preferences enums (e.g., `invoiceDeliveryMethod`).
  - [ ] Vehicle care preference unit enum (once defined).
- [ ] Verify “missing record” behavior is handled deterministically:
  - [ ] Preferences load returns empty state when record does not exist (whether backend returns 404 vs 200 empty must be handled).
  - [ ] Detail views show “not found” state on 404 and provide a safe navigation back to list.
- [ ] Verify UI does not invent fields not present in backend DTOs; fields/columns are conditional on response schema.
- [ ] Verify free-form text fields (notes, details, payload) are treated as text/JSON and never rendered as HTML.
- [ ] Verify vehicle lookup does not assume VIN format rules unless explicitly required; only enforce non-empty query (and minimum length only if backend defines it).
- [ ] Verify identifier display formatting/masking rules are applied consistently if required (VIN, license plate, payload JSON).

---

## API Contract
- [ ] Verify the frontend calls the **actual** Moqui services/endpoints (not placeholders) and the contract is documented (service name, params, response fields).
- [ ] Verify list endpoints support server-side pagination and sorting where required (processing logs, suspense/DLQ, promotion redemptions).
- [ ] Verify filter semantics match backend behavior and are documented (exact vs prefix vs contains for `correlationId`, VIN/unit/plate matching).
- [ ] Verify error handling is mapped consistently across screens:
  - [ ] `400` shows user-correctable validation messaging (inline when possible).
  - [ ] `401` triggers standard login flow.
  - [ ] `403` shows access denied and does not display partial data.
  - [ ] `404` shows not-found state on detail screens.
  - [ ] `409` (if used for optimistic locking) shows conflict guidance (reload vs overwrite).
  - [ ] `5xx`/network shows generic error + retry affordance.
- [ ] Verify CRM snapshot viewer calls `GET /v1/crm-snapshot` with at least one query param and renders `snapshotMetadata` when present.
- [ ] Verify vehicle lookup uses the confirmed endpoints/methods and handles truncation/pagination signals if provided.
- [ ] Verify create/update calls (vehicle care preferences, communication preferences, contact points, contact roles, commercial account creation, merge) send only allowed fields and handle field-level errors from backend.

---

## Events & Idempotency
- [ ] Verify integration monitoring screens correctly display idempotency-related statuses (e.g., `SKIPPED_DUPLICATE`, `DUPLICATE`) without implying remediation actions.
- [ ] Verify event identity is consistent across list/detail navigation (e.g., suspense item addressed by `suspenseId` vs `eventId`).
- [ ] Verify correlation identifiers are displayed for traceability (e.g., `eventId`, `correlationId`, `workorderId`, `invoiceId`) and copy-to-clipboard works where implemented.
- [ ] Verify retry/attempt metadata is displayed only if backend provides it (retry count, last error, next retry timestamp) and is clearly labeled as read-only.
- [ ] Verify UI does not assume event ordering guarantees; it should sort by timestamps provided (processed/received/routed).

---

## Security
- [ ] Verify every screen and backend call enforces authentication and authorization (RBAC) and fails closed.
- [ ] Verify UI entry points are hidden/disabled when user lacks permission (if the app supports client-side permission gating), and backend 403 is still handled safely.
- [ ] Verify sensitive fields are not exposed:
  - [ ] Suspense/DLQ payload JSON is shown only if explicitly allowed and sanitized/redacted.
  - [ ] ProcessingLog `details` is treated as potentially sensitive and masked/redacted per policy.
  - [ ] VIN/license plate display and logging follow the approved masking policy (if required).
  - [ ] Do not log free-form notes or raw payloads in client logs/telemetry.
- [ ] Verify input sanitization prevents injection:
  - [ ] No raw HTML rendering from backend strings (`details`, `notes`, `payload`).
  - [ ] Query/filter values are not interpolated into templates unsafely.
- [ ] Verify merge and role-editing flows (if implemented) require elevated permissions and produce audit trails (backend + UI confirmation).
- [ ] Verify environment-based restrictions are enforced if required (e.g., integration monitoring only in non-prod or only for internal support).

---

## Observability
- [ ] Verify correlation/request IDs are propagated and surfaced for support:
  - [ ] UI displays correlation ID on 5xx when provided (header or body).
  - [ ] Server-side logs include correlation ID for failed calls.
- [ ] Verify operational screens provide sufficient traceability fields (eventId, correlationId/workorder reference, timestamps, status, failure reason).
- [ ] Verify UI telemetry (if present) avoids sensitive data:
  - [ ] Log query length and result count for vehicle lookup rather than full VIN/plate (unless policy allows).
  - [ ] Log screen name, status code, and timing without payload contents.
- [ ] Verify audit history UI (vehicle care preferences, communication preferences, contact roles, merges) displays actor/time and change type; field-level diffs only if backend provides them.

---

## Performance & Failure Modes
- [ ] Verify list screens use server-side pagination and do not load unbounded results.
- [ ] Verify default sorting is deterministic and uses indexed fields (e.g., `processedTimestamp DESC`, `receivedTimestamp DESC`).
- [ ] Verify filter patterns are performance-safe:
  - [ ] `correlationId` contains/prefix searches are only enabled if backend supports indexing/efficient queries.
  - [ ] Vehicle lookup partial matching is constrained (min length, result limits) per backend rules.
- [ ] Verify UI handles backend timeouts/unavailability gracefully (retry button, preserves filters/inputs).
- [ ] Verify “refresh snapshot” does not spam backend (disable during in-flight; optional debounce/throttle if needed).
- [ ] Verify stale-data behavior is explicit:
  - [ ] Snapshot viewer indicates when data is being refreshed and avoids blanking the UI unexpectedly.
  - [ ] If keeping prior snapshot on error, it is clearly marked as stale (or cleared deterministically).

---

## Testing
- [ ] Unit tests cover client-side validation rules:
  - [ ] UUID validation for eventId/partyId/vehicleId.
  - [ ] Date range validation.
  - [ ] “At least one identifier” rule for snapshot.
  - [ ] Integer validation for rotation interval.
- [ ] Component tests verify UI state transitions: `idle/loading/loaded/empty/error` for list/detail screens.
- [ ] Integration tests (or contract tests) validate API/service calls:
  - [ ] Correct query params/body for search/list endpoints.
  - [ ] Pagination parameters and sorting.
  - [ ] Correct handling of 400/401/403/404/409/5xx.
- [ ] Security tests verify unauthorized users cannot see data (403 handling shows no partial results).
- [ ] Tests verify sensitive fields are not rendered/logged:
  - [ ] Payload redaction behavior (when payload is present).
  - [ ] No HTML injection via `details/notes/payload`.
- [ ] E2E tests cover key workflows:
  - [ ] Integration logs list → detail navigation and back with preserved filters.
  - [ ] Suspense list → detail navigation.
  - [ ] Promotion redemptions list → detail.
  - [ ] Snapshot load by partyId and by vehicleId; refresh behavior.
  - [ ] Vehicle lookup search → select → snapshot load → handoff to caller.
  - [ ] Vehicle care preferences create/update, cancel, and audit history view.
- [ ] Concurrency tests (if optimistic locking is used) verify 409 conflict handling for editable preferences.

---

## Documentation
- [ ] Document the final Moqui service names/endpoints used by each screen (ProcessingLog, SuspenseQueue, PromotionRedemption, Vehicle search/snapshot, Vehicle preferences, audit history).
- [ ] Document required permissions/roles for each screen and action (view vs edit vs audit vs merge).
- [ ] Document redaction/masking rules for payload/details/VIN/plate and logging policy.
- [ ] Document filter semantics and any minimum query length/result limits for search screens.
- [ ] Document error payload shapes used for user messaging (field-level errors, `errorCode` for snapshot 404 cases).
- [ ] Document navigation/handoff mechanism for VehicleLookup returning selected context to the calling workflow.
- [ ] Record decisions resolving open questions and link them to the story/ADR/issue.

---

## Open Questions to Resolve
- [ ] What are the exact Moqui services/entity views to query `ProcessingLog` and `SuspenseQueue` (list + detail), including required params and returned fields?
- [ ] Is a suspense/DLQ record addressed by `eventId` or a separate `suspenseId`/messageId?
- [ ] Can the UI display raw `payload` JSON for suspense items? If yes, what redaction rules apply and is there a safe `payloadSummary` field?
- [ ] What permission keys/roles gate access to integration monitoring screens, and are there environment-based restrictions (prod vs non-prod)?
- [ ] What are the filter semantics for `correlationId` (exact/prefix/contains), and what performance/indexing constraints apply?
- [ ] Should the UI expose retry/attempt metadata (retry count, last error, next retry timestamp), and what fields provide it?
- [ ] Are operator actions (reprocess/retry/ack/export/mark resolved) required? If yes, define allowed actions and audit requirements.
- [ ] Promotions redemptions: what is the exact API/service contract for listing/getting `PromotionRedemption` (pagination/sort/filters)?
- [ ] Promotions redemptions: what roles/permissions gate CSR vs Marketing access?
- [ ] Promotions redemptions: which entity is the canonical read model exposed to UI (`PromotionRedemption` only vs also `ProcessingLog`/event metadata)?
- [ ] Promotions redemptions: are `workOrderId` and `invoiceId` UUIDs or external human-readable identifiers?
- [ ] VehicleUpdated ingestion logs: what is the conflict resolution policy and when is `PENDING_REVIEW` used vs last-write-wins?
- [ ] VehicleUpdated ingestion logs: does ProcessingLog include `estimateId` in addition to `workorderId`, and what is the field name?
- [ ] VehicleUpdated ingestion logs: can `details` contain sensitive customer data and what masking rules apply?
- [ ] Vehicle care preferences: what are the exact endpoints/services for load, upsert, and audit history (including error payload shape and field-level errors)?
- [ ] Vehicle care preferences: fixed schema vs dynamic key/value preferences (EAV/JSON)?
- [ ] Vehicle care preferences: is optimistic locking required (version/ETag/lastUpdatedStamp) and what is the expected 409 behavior?
- [ ] Vehicle care preferences: allowed `rotationIntervalUnit` enum values, whether unit is required when interval is provided, and min/max constraints.
- [ ] Vehicle lookup: confirm endpoints/methods/response shapes for search and vehicle+owner snapshot.
- [ ] Vehicle lookup: ranking logic and whether backend returns `rankScore`/`matchType`.
- [ ] Vehicle lookup: minimum query length and matching rules (contains vs startsWith) for VIN/unit/plate.
- [ ] Vehicle lookup: result limiting/pagination and whether truncation is indicated in response.
- [ ] Vehicle lookup: permission model and required behavior when permission is missing (hide entry point vs show blocked screen).
- [ ] Vehicle lookup: PII policy for displaying/logging full VIN and license plate (masking standard).
- [ ] Vehicle lookup: Moqui screen-flow handoff mechanism to return selected vehicle context to estimate creation (return transition vs redirect params vs shared context).
- [ ] CRM snapshot viewer: should the browser call `GET /v1/crm-snapshot` directly or must it be proxied via Moqui server-side service (allowlist + scope)?
- [ ] CRM snapshot viewer: where is correlation ID provided (header name vs JSON field), and is logging partyId/vehicleId allowed or must be masked?
- [ ] Contact points/preferences/contact roles/merge/commercial account creation: confirm canonical identifiers (`partyId` vs `personId` vs `customerId`) used in routes and service calls.
- [ ] Contact points: phone validation policy (E.164 vs digits-only vs extensions) and whether `kind` is mutable after creation.
- [ ] Party merge: search requirements (allow browse vs require criteria), party types allowed, alias/redirect behavior, and conflict resolution policy (“survivor wins”).
- [ ] Contact roles: CRM vs Billing ownership, role list source of truth, and primary auto-demotion vs reject behavior.
- [ ] Commercial account creation: duplicate detection inputs and backend response shape (separate duplicateCheck vs create returns duplicates requiring confirmation), billing terms source, external identifiers validation, and post-create navigation route.

---

# End of Checklist
