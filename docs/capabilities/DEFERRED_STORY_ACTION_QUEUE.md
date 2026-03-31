# Deferred Story Action Queue

This queue captures the deferred frontend stories that are no longer blocked by wholly missing backend contracts, along with the exact decisions still needed to reopen them safely.

## Reopen Order

1. `#92` / `CAP-218`
2. `#89` / `CAP-220`
3. `#87` / `CAP-221`
4. `#244` / `CAP-218` - depends on the backend pick scaffold from `#92`

## Recently Completed Artifact Follow-Through

### `#571` / `CAP-315` / ASN Receiving

- Status: `Documented`
- Completed artifacts:
  - Story updated with ASN-as-default receiving path and receiving-session reuse
  - Receiving architecture note updated to reflect `createAsn -> createReceivingSession -> receiveItemsIntoStaging`

### `#241` / `CAP-219` / Plan Cycle Counts

- Status: `Documented`
- Completed artifacts:
  - Story updated with zones-by-location and plan-list contract guidance
  - Field-shape reconciliation documented: `planName` is the canonical optional command field; `description` remains read-model/proxy-owned unless backend adds it

### `#97` / `CAP-216` / Cross-Dock Receiving

- Status: `Documented`
- Completed artifacts:
  - Story updated with canonical WorkExec read-side mapping
  - Retry/idempotency guidance documented as non-idempotent unless contract changes

### `#242` / `CAP-218` / Return to Stock

- Status: `Documented`
- Completed artifacts:
  - Story updated with returnable-items source, reason-code lookup, and command mapping
  - Destination rule documented as eligible `locationId` with optional `storageLocationId`

### `#243` / `CAP-218` / Consume Picked Items

- Status: `Documented`
- Completed artifacts:
  - Story updated with backend ownership note: frontend should target a workorder-owned facade, with server-to-server orchestration into inventory consumption
  - Raw backend contract evidence documented: workorder detail owns part totals; inventory owns pick lists, pick tasks, and `consumePickedItems`

## Action Queue

### 1. `#92` / `CAP-218` / Pick List Generation and View

- Priority: `Medium`
- Planning artifact:
  - [PRD-cap218-backend-fulfillment-completion.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/PRD-cap218-backend-fulfillment-completion.md)
- Who needs to answer:
  - `inventory backend`
  - `workexec/backend owner`
  - `frontend integration owner`
- Decisions needed:
  - Parts picking for workorder execution is Workorder Execution-owned.
  - Frontend should use a WorkExec/workorder-owned facade rather than call inventory directly.
  - Are tasks embedded or separate?
  - What display enrichment is guaranteed beyond raw inventory task fields?
  - What permissions gate view and print?
  - Can `Draft` or `NeedsReview` be printed?
- Source:
  - [CAP_218.92.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.92.frontend.md#L402)
- Recommended artifact:
  - View/print policy note and frontend-facing WorkExec contract mapping.
- Action to open:
  - Implement the backend pick-list scaffold for `#92`: add a WorkExec/workorder-facing facade endpoint that loads inventory pick-list state (`getPickListsForWorkorder` plus pick-task retrieval), normalizes the response shape for frontend view/print use, and makes the final embedded-vs-separate task contract explicit.

### 2. `#89` / `CAP-220` / Shortage Resolution

- Priority: `Medium`
- Who needs to answer:
  - `architecture`
  - `workexec owner`
  - `inventory backend`
  - `product/positivity owners`
- Decisions needed:
  - Inventory recommendation and lead-time contracts now exist:
    - `resolveShortage`
    - `queryLeadTime`
  - Which backend layer owns the user-facing shortage-decision submit workflow?
  - What are the proxy routes for shortage check and shortage submit?
  - Does submit accept `optionId` only or a structured payload?
  - Can quantity be edited during resolution?
- Source:
  - [CAP_220.89.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-220/stories/frontend/CAP_220.89.frontend.md#L407)
- Recommended artifact:
  - Cross-domain orchestration note plus backend action to add the submit-decision facade/endpoint.

### 3. `#87` / `CAP-221` / Inventory Security Admin

- Priority: `Medium`
- What must be done:
  - Keep `#87` scoped to permission catalog visibility and permission-gated inventory actions only.
  - Use the normalized `pos-inventory` permission catalog and the published canonical inventory action-to-permission gating matrix from the story doc.
  - Use JWT token `authorities` claims for the current-user effective permission set when gating routes and actions.
- Source:
  - [CAP_221.87.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-221/stories/frontend/CAP_221.87.frontend.md#L453)
- Recommended artifact:
  - Frontend implementation checklist using the published gating matrix and token-claim permission source.
- Action to open:
  - Implement the Inventory Security permission catalog page and route/action gating using the documented canonical matrix and JWT `authorities` claims.

### 4. `#244` / `CAP-218` / Mechanic Picking

- Priority: `Lower until #92 scaffold lands`
- Planning artifact:
  - [PRD-cap218-backend-fulfillment-completion.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/PRD-cap218-backend-fulfillment-completion.md)
- Backend tracker:
  - `durion-positivity-backend#179` should own the WorkExec/workorder-facing pick facade
  - `durion-positivity-backend#28` remains the inventory-side pick-list/task system of record
- Who needs to answer:
  - `workexec owner`
  - `inventory backend`
  - `frontend integration owner`
- Decisions needed:
  - Implement the workorder-facing pick scaffold that `#244` depends on.
  - Is the route keyed by `workOrderId` or `pickTaskId`?
  - What are the task and line statuses and transitions?
  - What can be scanned?
  - How are multi-match, completion-with-remainder, and serial or lot capture handled?
- Source:
  - [CAP_218.244.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.244.frontend.md#L402)
- Recommended artifact:
  - Backend contract note plus workorder-facing pick scaffold shared with `#92`.
- Action to open:
  - Implement the workorder-facing pick-task scaffold in `pos-workorder` for `#92/#244`: load pick list for a workorder, resolve scans, confirm line picks, complete the pick task, and publish the canonical route/payload/error contract the frontend should use.

## Working Pattern

1. Start contract triage with `#92`, then `#89`.
2. Capture decisions in the story docs first, not only in chat or issue comments.
3. Escalate `#89` as a backend contract question before implementation starts, and treat `#92` as the prerequisite backend scaffold for `#244`.
