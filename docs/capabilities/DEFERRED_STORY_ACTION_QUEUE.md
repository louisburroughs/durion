# Deferred Story Action Queue

This queue captures the deferred frontend stories that are no longer blocked by wholly missing backend contracts, along with the exact decisions still needed to reopen them safely.

## Reopen Order

1. `#571` / `CAP-315` - closest to implementation-ready
2. `#241` / `CAP-219`
3. `#97` / `CAP-216`
4. `#242` / `CAP-218`
5. `#243` / `CAP-218`
6. `#92` / `CAP-218`
7. `#89` / `CAP-220`
8. `#87` / `CAP-221`
9. `#244` / `CAP-218` - needs the largest architecture clarification

## Action Queue

### 1. `#571` / `CAP-315` / ASN Receiving

- Priority: `High`
- Who needs to answer:
  - `inventory backend`
  - `frontend integration owner`
- Decisions needed:
  - Primary flow is `ASN -> createReceivingSession -> receiveItemsIntoStaging`.
  - ASN loading lives inside existing receiving screens and should be treated as the normal receiving path.
  - Trucks without an ASN are less likely, but still possible and should remain a supported fallback path.
- Source:
  - [CAP_315.571.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-315/stories/frontend/CAP_315.571.frontend.md#L46)
- Recommended artifact:
  - Update the story and receiving architecture notes to reflect ASN as the default receiving path.

### 2. `#241` / `CAP-219` / Plan Cycle Counts

- Priority: `High`
- Who needs to answer:
  - `inventory backend`
  - `frontend integration owner`
- Decisions needed:
  - Zones-by-location identifies inventory zones by physical location.
  - Plan-list is the list endpoint for cycle count plans.
  - `today` is allowed.
  - `past` is evaluated in the site timezone.
  - `description` should be optional. `planName` may also be optional; the current `pos-inventory` OpenAPI already exposes optional `planName` on `CreateCycleCountPlanRequest`.
- Source:
  - [CAP_219.241.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-219/stories/frontend/CAP_219.241.frontend.md#L457)
- Recommended artifact:
  - Contract addendum in inventory docs plus frontend integration mapping note, including any final path and field-shape reconciliation for `description` vs `planName`.

### 3. `#97` / `CAP-216` / Cross-Dock Receiving

- Priority: `High`
- Who needs to answer:
  - `inventory backend`
  - `workexec/backend owner`
  - `frontend integration owner`
- Decisions needed:
  - Inventory-side receiving detail is available at `GET /v1/inventory/receiving/sessions/{sessionId}`.
  - Inventory-side cross-dock submit is available at `POST /v1/inventory/receiving/sessions/{sessionId}/lines/{lineId}/cross-dock`.
  - Cross-dock request/response field shape is defined in `pos-inventory/openapi.yaml`.
  - `pos-workorder` exposes candidate workorder read endpoints:
    - `GET /v1/workorders`
    - `GET /v1/workorders/{workorderId}`
    - `GET /v1/workorders/{workorderId}/detail`
    - `GET /v1/workexec/wip`
    - `GET /v1/workexec/wip/{workorderId}`
  - The remaining question is which `pos-workorder` endpoint pair should be canonical for workorder search and line selection.
  - Idempotency support is not documented for cross-dock submit.
  - Submit does not currently return notification/publication status in the documented response shape.
- Source:
  - [CAP_216.97.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-216/stories/frontend/CAP_216.97.frontend.md#L475)
- Recommended artifact:
  - Frontend integration contract note with request/response examples and WorkExec read-side mapping.

### 4. `#242` / `CAP-218` / Return to Stock

- Priority: `High`
- Who needs to answer:
  - `inventory backend`
  - `workexec backend`
  - `frontend/moqui integration owner`
- Decisions needed:
  - What endpoint loads returnable items?
  - What endpoint provides return reason codes?
  - Is destination fixed `locationId`, selectable `locationId`, or does `storageLocationId` matter?
  - What is the retry/idempotency behavior?
  - Which permission gates view vs submit?
- Source:
  - [CAP_218.242.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.242.frontend.md#L528)
- Recommended artifact:
  - Read-side contract note plus destination rule decision.

### 5. `#243` / `CAP-218` / Consume Picked Items

- Priority: `Medium`
- Who needs to answer:
  - `workexec backend`
  - `inventory backend`
  - `frontend integration owner`
- Decisions needed:
  - `pos-workorder` is the likely source of truth for workorder-side reads, but there is still no explicit “picked items for workorder” endpoint.
  - Both consume surfaces exist:
    - `POST /v1/inventory/consumption` in `pos-inventory`
    - `POST /v1/workorders/{workorderId}/parts/consume` in `pos-workorder`
  - Success identifiers differ by surface:
    - inventory returns `consumptionId`, `workorderId`, `pickListId`, `totalItemsConsumed`, `createdAt`
    - workorder returns usage-event fields including `id`, `workorderPartId`, `workorderId`, `eventType`, `quantity`, `performedBy`, `performedAt`
  - Quantity shape is not yet harmonized:
    - inventory consume request uses integer item quantities
    - workorder consume request uses decimal quantity
  - Retry/idempotency depends on the chosen surface:
    - workorder consume documents `Idempotency-Key`
    - inventory consume does not currently document idempotency
- Source:
  - [CAP_218.243.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.243.frontend.md#L482)
- Recommended artifact:
  - Ownership note for read model owner vs movement command owner, plus canonical consume-surface selection.

### 6. `#92` / `CAP-218` / Pick List Generation and View

- Priority: `Medium`
- Who needs to answer:
  - `inventory backend`
  - `workexec/backend owner`
  - `frontend integration owner`
- Decisions needed:
  - Parts picking for workorder execution is Workorder Execution-owned.
  - Is workorder retrieval the canonical load path?
  - Are tasks embedded or separate?
  - What permissions gate view and print?
  - Can `Draft` or `NeedsReview` be printed?
- Source:
  - [CAP_218.92.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.92.frontend.md#L402)
- Recommended artifact:
  - View/print policy note and frontend-facing WorkExec contract mapping.

### 7. `#89` / `CAP-220` / Shortage Resolution

- Priority: `Medium`
- Who needs to answer:
  - `architecture`
  - `workexec owner`
  - `inventory backend`
  - `product/positivity owners`
- Decisions needed:
  - Which domain owns the user-facing shortage-resolution workflow?
  - What are the proxy routes for shortage check and shortage submit?
  - Does submit accept `optionId` only or a structured payload?
  - Can quantity be edited during resolution?
- Source:
  - [CAP_220.89.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-220/stories/frontend/CAP_220.89.frontend.md#L407)
- Recommended artifact:
  - Cross-domain orchestration ADR or contract note.

### 8. `#87` / `CAP-221` / Inventory Security Admin

- Priority: `Medium`
- Who needs to answer:
  - `security owner`
  - `inventory owner`
  - `architecture`
- Decisions needed:
  - Is this a Security feature area or an Inventory admin shell?
  - What roles are canonical vs display-only?
  - What are the admin permissions?
  - What is the definitive inventory action-to-permission gating matrix?
  - Is role-assignment audit/history required in this UI?
- Source:
  - [CAP_221.87.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-221/stories/frontend/CAP_221.87.frontend.md#L453)
- Recommended artifact:
  - Ownership ADR plus gating matrix document.

### 9. `#244` / `CAP-218` / Mechanic Picking

- Priority: `Lower until architecture answer`
- Who needs to answer:
  - `workexec owner`
  - `inventory backend`
  - `architecture`
  - `frontend/moqui integration owner`
- Decisions needed:
  - Is the story officially `domain:workexec`?
  - Is the route keyed by `workOrderId` or `pickTaskId`?
  - What are the task and line statuses and transitions?
  - What can be scanned?
  - Are partial picks, over-picks, and serial or lot capture supported?
- Source:
  - [CAP_218.244.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.244.frontend.md#L402)
- Recommended artifact:
  - Short ADR or canonical picking contract note before implementation.

## Working Pattern

1. Run a short contract triage for `#571`, `#241`, `#97`, and `#242`.
2. Capture decisions in the story docs first, not only in chat or issue comments.
3. Escalate `#89`, `#87`, and `#244` as architecture or domain-ownership questions before implementation starts.
