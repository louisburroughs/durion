# STORY_VALIDATION_CHECKLIST.md

## Summary

Validation checklist for inventory-domain stories. Use this alongside `AGENT_GUIDE.md` and the decision set `DECISION-INVENTORY-###`.

## Completed items

- [x] Converted prior open questions into acceptance criteria

## Scope/Ownership

- [ ] Verify each story is correctly labeled `domain:inventory` and resolve conflicts where issue text suggests a different domain but inventory owns the capability.
- [ ] Verify each screen/flow has a clear primary persona and entry point (Inventory menu vs contextual links).
- [ ] Verify out-of-scope items are explicitly not implemented (e.g., no inventory edits on Availability screen; no ledger mutation; no implicit UOM conversions).
- [ ] Verify integration pattern: UI calls Moqui proxy only (no direct Vue → inventory backend). (DECISION-INVENTORY-002)
- [ ] Verify admin/ops screens (Topology, SyncLog, feed monitoring) are placed under correct navigation namespace and are not exposed to general users by default.

## Data Model & Validation

- [ ] Verify required identifiers are validated client-side before calling backend (trimmed non-empty `productSku`, required `locationId` where applicable).
- [ ] Verify Availability query validation:
  - [ ] `productSku` is trimmed and non-empty; whitespace-only is rejected.
  - [ ] `locationId` is required.
  - [ ] `storageLocationId` is optional; if provided it must belong to the selected `locationId`.
- [ ] Verify date range filters enforce `from <= to` and block submit when invalid.
- [ ] Verify quantity inputs enforce domain rules where applicable:
  - [ ] Availability display is read-only (no quantity edits). (DECISION-INVENTORY-004)
  - [ ] Adjustments: `quantityChange` required and cannot be zero. (DECISION-INVENTORY-006)
- [ ] Verify “no inventory history” is treated as success for Availability with zeros and a clear empty-state message. (DECISION-INVENTORY-004)
- [ ] Verify storage location create/update validation:
  - [ ] `locationId`/site, `name`, `barcode`, `storageType` required on create. (DECISION-INVENTORY-007)
  - [ ] `barcode` is trimmed and non-empty; duplicates are surfaced as field errors. (DECISION-INVENTORY-003)
  - [ ] Parent selection prevents self-parenting and cycles.
- [ ] Verify deactivation workflow validation:
  - [ ] Deactivate action is disabled/hidden when already inactive.
  - [ ] If backend indicates destination is required for non-empty location, UI requires `destinationLocationId` and blocks submit without it. (DECISION-INVENTORY-007)
- [ ] Verify inactive/pending location selection rules are enforced consistently across any “new movement” flow (block selection; historical display read-only). (DECISION-INVENTORY-009)
- [ ] Verify JSON fields render safely:
  - [ ] `tags`, `capacity`, `temperature`, SyncLog payloads, and feed payloads render via a safe JSON viewer (escape + truncate + explicit expand/copy). (DECISION-INVENTORY-015)

## API Contract

- [ ] Verify API contracts use plain JSON responses and deterministic error schema. (DECISION-INVENTORY-003)
- [ ] Verify all list screens use cursor pagination (`pageSize`, `pageToken`, `nextPageToken`) and do not rely on total counts unless explicitly provided. (DECISION-INVENTORY-003)
- [ ] Verify error payload parsing is robust:
  - [ ] Field-level validation errors map to the correct form fields when possible.
  - [ ] Unknown error shapes fall back to a generic message without crashing.

## Events & Idempotency

- [ ] Verify read-only screens do not emit mutation side effects (Availability, Ledger view, Topology views).
- [ ] Verify mutation actions are protected against double-submit (disable while in-flight).
- [ ] Verify UI does not auto-retry mutations.

## Security

- [ ] Verify all inventory screens enforce authentication (401 routes to login/session refresh per app convention). (DECISION-INVENTORY-012)
- [ ] Verify authorization is enforced in UI and backend:
  - [ ] UI hides/disables gated actions when permission data is available. (DECISION-INVENTORY-010)
  - [ ] Backend 403 is handled gracefully with a forbidden state and no data leakage. (DECISION-INVENTORY-012)
- [ ] Verify sensitive data handling:
  - [ ] Availability quantities are not logged to console/telemetry. (DECISION-INVENTORY-011)
  - [ ] Raw payload blobs (SyncLog, feed payloads) are not logged and are permission-gated. (DECISION-INVENTORY-011)

## Observability

- [ ] Verify `X-Correlation-Id` is propagated on requests and shown in error UI technical details. (DECISION-INVENTORY-012)

## Performance & Failure Modes

- [ ] Verify list screens use server-side pagination; do not fetch unbounded datasets.
- [ ] Verify large JSON payloads are not rendered eagerly in tables (preview + on-demand expand).
- [ ] Verify timeout UX is bounded (spinner appears quickly; timeouts show retry).

## Testing

- [ ] Unit tests cover client-side validation rules (required fields, date ranges, non-zero adjustments).
- [ ] Component tests cover `idle → loading → success/error` and correct UI rendering.
- [ ] Contract tests/mocks cover success and failure (401/403/404/422/5xx) with deterministic error schema.

## Documentation

- [ ] Each new/modified screen documents route, backend services called, and required permissions.
- [ ] Error mapping table documented for each flow (400/401/403/404/409/422/5xx) with user-facing messages.

## Acceptance Criteria (Resolved Open Questions)

### Availability endpoint, auth mechanism, and envelope

- [ ] Availability uses the contract described in the inventory domain docs and is called via Moqui proxy. (DECISION-INVENTORY-002, DECISION-INVENTORY-004)
- [ ] Response is plain JSON (no `{data: ...}` envelope) and errors follow deterministic schema. (DECISION-INVENTORY-003)

### Pickers for `locationId` and `storageLocationId`

- [ ] UI uses pickers backed by inventory read models; no free-text UUID entry in normal user flows. (DECISION-INVENTORY-001, DECISION-INVENTORY-008)
- [ ] UI prevents choosing a storage location outside the selected site when possible; backend errors are surfaced when not preventable. (DECISION-INVENTORY-001, DECISION-INVENTORY-003)

### Availability deep-linking

- [ ] Availability supports canonical deep-link parameters and does not loop on auto-run. (DECISION-INVENTORY-004, DECISION-INVENTORY-014)

### Availability quantity sensitivity

- [ ] Client logs/telemetry never emit numeric quantities from Availability results. (DECISION-INVENTORY-011)

### Ledger endpoints and pagination shape

- [ ] Ledger uses cursor pagination and immutable entries; UI never attempts to mutate ledger records. (DECISION-INVENTORY-003, DECISION-INVENTORY-005)

### Ledger location filter semantics

- [ ] Filtering by location matches `fromLocationId OR toLocationId` semantics and UI copy reflects this. (DECISION-INVENTORY-005)

### Workexec integration identifier

- [ ] Movement history deep-links use identifiers that exist on ledger entries (e.g., `sourceTransactionId`), without assuming Workexec-only IDs. (DECISION-INVENTORY-005)

### Storage location field schemas (`capacity`, `temperature`)

- [ ] `capacity` and `temperature` are treated as opaque JSON; UI validates JSON syntax only and renders safely. (DECISION-INVENTORY-015)

### Inactive storage location editability

- [ ] Inactive locations are blocked for new movement flows; historical display is read-only. (DECISION-INVENTORY-009)
- [ ] If backend allows metadata fixes on inactive bins, UI supports it without enabling operational use. (DECISION-INVENTORY-007, DECISION-INVENTORY-009)

### Storage type mutability

- [ ] `storageType` is create-only unless backend explicitly documents mutation support. (DECISION-INVENTORY-007)

### Parent selection rules (inactive parents)

- [ ] Parent selection defaults to active-only for operational flows; historical organization may include inactive parents if explicitly enabled. (DECISION-INVENTORY-009)

### Topology permissions (LocationRef / SyncLog / payload)

- [ ] Topology screens are permission-gated and payload visibility is separately gated. (DECISION-INVENTORY-010, DECISION-INVENTORY-011)

### Manual “Sync now” trigger

- [ ] If manual sync exists, it is permission-gated and guarded against double-trigger. (DECISION-INVENTORY-008)

### “New movement” flows that block inactive locations

- [ ] Any flow that creates movement/adjustment blocks `INACTIVE` and `PENDING` sites/bins; PENDING is treated like INACTIVE. (DECISION-INVENTORY-009)

### Tags rendering expectations

- [ ] Tags are treated as opaque JSON and are rendered safely (escape + truncate + expand/copy). (DECISION-INVENTORY-015)

### Fitment hints scope (if applicable)

- [ ] Fitment hints remain out-of-scope unless an inventory-owned contract exists; if implemented, they use deterministic errors and inventory permission naming. (DECISION-INVENTORY-003, DECISION-INVENTORY-010)

### Feed normalization / ingestion monitoring

- [ ] Feed ops screens and allowed updates match inventory-owned contracts and permissions. (DECISION-INVENTORY-013, DECISION-INVENTORY-010)

## End

End of document.
