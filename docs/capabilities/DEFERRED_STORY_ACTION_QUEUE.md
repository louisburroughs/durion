# Deferred Story Action Queue

This queue captures the deferred frontend stories that are no longer blocked by wholly missing backend contracts, along with the exact decisions still needed to reopen them safely.

> **Backend implementation complete** (2026-04-01): All backend scaffolding for the queued stories is implemented. `openapi.yaml` and the SDK packages are up to date. All four stories below are unblocked and ready for frontend implementation.

## Reopen Order

1. `#92` / `CAP-218` — **UNBLOCKED**
2. `#89` / `CAP-220` — **UNBLOCKED**
3. `#87` / `CAP-221` — **UNBLOCKED**
4. `#244` / `CAP-218` — **UNBLOCKED** (backend pick scaffold from `#92` is in place)

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
- Status: **UNBLOCKED** — backend implementation complete; `openapi.yaml` and SDK up to date (2026-04-01)
- Planning artifact:
  - [PRD-cap218-backend-fulfillment-completion.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/PRD-cap218-backend-fulfillment-completion.md)
- Resolved decisions:
  - Parts picking for workorder execution is Workorder Execution-owned.
  - Frontend uses the WorkExec/workorder-owned facade (not inventory directly).
  - Task embedding, display enrichment, permission gates, and print eligibility are defined in the backend contract — consult `openapi.yaml` and SDK for authoritative shapes.
- Source:
  - [CAP_218.92.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.92.frontend.md#L402)
- Next action:
  - Frontend implementation. Use `openapi.yaml` and SDK as the contract source of truth.

### 2. `#89` / `CAP-220` / Shortage Resolution

- Priority: `Medium`
- Status: **UNBLOCKED** — backend implementation complete; `openapi.yaml` and SDK up to date (2026-04-01)
- Resolved decisions:
  - `resolveShortage` and `queryLeadTime` contracts are implemented and published in `openapi.yaml`.
  - Backend layer ownership, proxy routes, submit payload shape, and quantity-edit policy are defined in the contract — consult `openapi.yaml` and SDK.
- Source:
  - [CAP_220.89.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-220/stories/frontend/CAP_220.89.frontend.md#L407)
- Next action:
  - Frontend implementation. Use `openapi.yaml` and SDK as the contract source of truth.

### 3. `#87` / `CAP-221` / Inventory Security Admin

- Priority: `Medium`
- Status: **UNBLOCKED** — backend implementation complete; `openapi.yaml` and SDK up to date (2026-04-01)
- Implementation constraints (unchanged):
  - Keep `#87` scoped to permission catalog visibility and permission-gated inventory actions only.
  - Use the normalized `pos-inventory` permission catalog and the published canonical inventory action-to-permission gating matrix from the story doc.
  - Use JWT token `authorities` claims for the current-user effective permission set when gating routes and actions.
- Source:
  - [CAP_221.87.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-221/stories/frontend/CAP_221.87.frontend.md#L453)
- Next action:
  - Frontend implementation of the Inventory Security permission catalog page and route/action gating using the documented canonical matrix and JWT `authorities` claims.

### 4. `#244` / `CAP-218` / Mechanic Picking

- Priority: `Medium` (backend pick scaffold from `#92` is now in place)
- Status: **UNBLOCKED** — backend implementation complete; `openapi.yaml` and SDK up to date (2026-04-01)
- Planning artifact:
  - [PRD-cap218-backend-fulfillment-completion.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/PRD-cap218-backend-fulfillment-completion.md)
- Resolved decisions:
  - WorkExec/workorder-facing pick facade is implemented (`durion-positivity-backend#179`).
  - Route keying, task/line statuses and transitions, scan handling, multi-match, completion-with-remainder, and serial/lot capture are defined in the backend contract — consult `openapi.yaml` and SDK.
- Source:
  - [CAP_218.244.frontend.md](/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-218/stories/frontend/CAP_218.244.frontend.md#L402)
- Next action:
  - Frontend implementation. Use `openapi.yaml` and SDK as the contract source of truth. Implement after or in parallel with `#92`.

## Working Pattern

1. All four stories are unblocked. Implement in reopen order: `#92` → `#89` → `#87` → `#244` (or `#244` in parallel with `#92`).
2. Use `openapi.yaml` and the SDK as the authoritative contract source for all four stories — backend answers for all previously open questions are encoded there.
3. Capture any frontend-side implementation decisions in the story docs, not only in chat or issue comments.
