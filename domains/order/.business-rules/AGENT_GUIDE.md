# AGENT_GUIDE.md

## Summary

This guide defines the Order domain’s **normative** rules for order cancellation orchestration in the Durion POS platform. It resolves the prior “open questions” into safe defaults so frontend (Moqui/Quasar) work can proceed without inventing policy. Decisions are indexed and cross-referenced to `DOMAIN_NOTES.md` for non-normative rationale.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-ORDER-001 | Order domain is cancellation orchestrator |
| DECISION-ORDER-002 | Work status blocking rules |
| DECISION-ORDER-003 | Payment settlement handling (void vs refund required) |
| DECISION-ORDER-004 | Cancellation audit record immutability |
| DECISION-ORDER-005 | Idempotent cancellation semantics |
| DECISION-ORDER-006 | Cancellation reason taxonomy |
| DECISION-ORDER-007 | Orchestration timeout and failure handling |
| DECISION-ORDER-008 | Cancellation comments maximum length |
| DECISION-ORDER-009 | Concurrency control and 409 response |
| DECISION-ORDER-010 | Authorization and permission model |
| DECISION-ORDER-011 | Canonical domain label and ownership for cancellation UI |
| DECISION-ORDER-012 | UI → Moqui service contract conventions (safe defaults) |
| DECISION-ORDER-013 | Canonical order cancellation status enum contract |
| DECISION-ORDER-014 | Frontend permission exposure pattern (safe default) |
| DECISION-ORDER-015 | Correlation IDs and admin-only details visibility |

## Domain Boundaries

### What Order owns (system of record)

- Order lifecycle state transitions (including cancellation variants)
- Cancellation policy (cancellable vs blocked) and orchestration outcomes
- Downstream coordination and final order status selection
- Immutable audit records for cancellation attempts

### What Order does *not* own

- Payment execution (authorization/capture/settlement/void/refund execution)
- Work execution state machine and physical rollback procedures
- Accounting/GL postings and refund processing workflows

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| Order | Order record and authoritative status values, including cancellation-related states. |
| CancellationRecord | Immutable audit record per cancellation attempt (success or failure). |
| WorkOrder (external) | Work execution object linked to the order; used to evaluate cancellability and rollback status. |
| PaymentTransaction (external) | Payment object linked to the order; used to decide void vs refund-required and void status. |
| CancelOrderRequest | Inputs: `orderId`, `reason` (required), `comments` (optional, max 2000). |
| CancelOrderResponse | Outputs: `orderId`, `status`, `message`, `cancellationId`, `paymentVoidStatus`, `workRollbackStatus`, optional support identifiers. |

## Invariants / Business Rules

- Order domain is the orchestrator and authority for cancellation policy and resulting order status. (Decision ID: DECISION-ORDER-001)
- Cancellation is blocked if work is in a non-reversible state set. (Decision ID: DECISION-ORDER-002)
- Cancellation proceeds regardless of payment settlement state; settled payments imply refund-required status, not a UI refund flow. (Decision ID: DECISION-ORDER-003)
- Every cancellation attempt is audited as an immutable record (including blocked attempts and failures). (Decision ID: DECISION-ORDER-004)
- Cancellation is idempotent for already-cancelled orders and conflict-protected for in-flight cancellation. (Decision ID: DECISION-ORDER-005, DECISION-ORDER-009)
- `reason` is required; `comments` is optional and limited to 2000 characters (server-enforced). (Decision ID: DECISION-ORDER-008)

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-ORDER-001 | Order domain orchestrates cancellation | [DOMAIN_NOTES.md](#decision-order-001---order-domain-as-cancellation-orchestrator) |
| DECISION-ORDER-002 | Work blocks cancellation in irreversible states | [DOMAIN_NOTES.md](#decision-order-002---work-status-blocking-rules-for-cancellation) |
| DECISION-ORDER-003 | Void if unsettled; refund-required if settled | [DOMAIN_NOTES.md](#decision-order-003---payment-settlement-handling-in-cancellation) |
| DECISION-ORDER-004 | Cancellation audit is immutable | [DOMAIN_NOTES.md](#decision-order-004---cancellation-audit-record-immutability) |
| DECISION-ORDER-005 | Idempotency for repeat cancels | [DOMAIN_NOTES.md](#decision-order-005---idempotent-cancellation-semantics) |
| DECISION-ORDER-006 | Canonical reason list and governance | [DOMAIN_NOTES.md](#decision-order-006---cancellation-reason-taxonomy) |
| DECISION-ORDER-007 | Timeout/failure handling policy | [DOMAIN_NOTES.md](#decision-order-007---cancellation-orchestration-timeout-and-failure-handling) |
| DECISION-ORDER-008 | Comments max length (2000) | [DOMAIN_NOTES.md](#decision-order-008---cancellation-comments-maximum-length) |
| DECISION-ORDER-009 | Concurrency returns 409 | [DOMAIN_NOTES.md](#decision-order-009---concurrency-control-with-409-conflict-response) |
| DECISION-ORDER-010 | Permission model for cancellation | [DOMAIN_NOTES.md](#decision-order-010---authorization-and-permission-model-for-cancellation) |
| DECISION-ORDER-011 | Canonical story label is domain:order | [DOMAIN_NOTES.md](#decision-order-011---canonical-domain-label-and-ownership-for-cancellation-ui) |
| DECISION-ORDER-012 | Safe-default Moqui contract conventions | [DOMAIN_NOTES.md](#decision-order-012---ui-to-moqui-service-contract-conventions-safe-defaults) |
| DECISION-ORDER-013 | Canonical cancellation status enum | [DOMAIN_NOTES.md](#decision-order-013---canonical-order-cancellation-status-enum-contract) |
| DECISION-ORDER-014 | Permission exposure pattern to UI | [DOMAIN_NOTES.md](#decision-order-014---frontend-permission-exposure-pattern-safe-default) |
| DECISION-ORDER-015 | Correlation IDs/admin details policy | [DOMAIN_NOTES.md](#decision-order-015---correlation-ids-and-admin-only-details-visibility) |

## Open Questions (from source)

### Q: What is the canonical frontend domain label for this work: `domain:order` vs `domain:positivity` vs `domain:payment` / “Point of Sale”?

- Answer: Use `domain:order` as the canonical label for cancellation UI and orchestration work; tag cross-domain dependencies separately (e.g., Payment/Work Execution) but do not re-home ownership.
- Assumptions:
  - The cancellation operation’s system-of-record is the Order domain.
  - Other domains remain authoritative for their own statuses but do not expose cancellation entrypoints directly to UI.
- Rationale:
  - Ownership aligns with order lifecycle and the state machine.
- Impact:
  - Story routing, code ownership, and review routing should be normalized under Order domain.
- Decision ID: DECISION-ORDER-011

### Q: What is the exact Moqui screen route/screen name for Order Detail in this repo?

- Answer: Safe default is a single Order Detail screen that accepts `orderId` as a parameter and is the only launch point for cancellation UI; the exact route must match repo conventions and should be treated as a configuration/implementation detail.
- Assumptions:
  - Order Detail exists as a stable navigation target.
  - Order Detail can load cancellation summary fields or can trigger a dedicated “latest cancellation” fetch.
- Rationale:
  - Keeps cancellation UX in the order context and avoids fragmenting navigation.
- Impact:
  - Requires confirming the screen name/URL in Moqui screens before implementation.
- Decision ID: DECISION-ORDER-012

### Q: What are the Moqui service names/endpoints and parameter mappings for loading order detail, submitting cancellation, and retrieving latest cancellation record?

- Answer: Use a single Order-owned cancel service and a single order-detail service; if cancellation records are not embedded in order detail, add a dedicated “latest cancellation record” read service owned by Order.
- Assumptions:
  - Moqui service names will follow repository naming conventions; until confirmed, treat them as placeholders.
  - Cancellation submit is a single command-like service with validations and orchestration server-side.
- Rationale:
  - Minimizes UI coupling to downstream systems.
- Impact:
  - Backend/Moqui service contracts must be documented alongside the story.
- Decision ID: DECISION-ORDER-012

### Q: What is the definitive error response schema (stable `errorCode` vs message-only)?

- Answer: Require a stable `errorCode` plus a user-safe `message`, with optional `details` and optional `fieldErrors` for validation.
- Assumptions:
  - UI needs deterministic mapping for 400/403/409/500.
- Rationale:
  - Prevents UI from parsing free-form strings and reduces leakage risk.
- Impact:
  - Backend contracts and tests should validate `errorCode` is always present on non-2xx responses.
- Decision ID: DECISION-ORDER-012

### Q: What is the definitive order status enum set the frontend will receive (in-flight + failure variants)?

- Answer: Canonical cancellation-relevant statuses are `CANCELLING`, `CANCELLED`, `CANCELLED_REQUIRES_REFUND`, and `CANCELLATION_FAILED`; legacy variants should be mapped server-side to these canonical values for UI.
- Assumptions:
  - Some legacy status values may exist; UI must not need to understand all historical variants.
- Rationale:
  - Prevents enum drift and broken UI rendering.
- Impact:
  - Backend should provide a stable status contract and optionally a `statusDisplay` field.
- Decision ID: DECISION-ORDER-013

### Q: How does the frontend determine `ORDER_CANCEL` capability in this repo?

- Answer: Safe default is: backend returns an explicit boolean `canCancel` in the order detail response (and/or a capability set), and UI gates the button based on that; backend still enforces permission.
- Assumptions:
  - A standard permission exposure mechanism exists but may not be uniform across screens.
- Rationale:
  - Avoids duplicating security policy in the UI.
- Impact:
  - Order detail contract should include `canCancel` or a capabilities array.
- Decision ID: DECISION-ORDER-014

### Q: Which roles besides Store Manager can cancel orders?

- Answer: Cancellation eligibility is permission-based, not role-name-based; at minimum Store Manager and Service Advisor are expected to have `ORDER_CANCEL` where appropriate.
- Assumptions:
  - Role names vary by tenant; permissions are stable.
- Rationale:
  - Keeps RBAC flexible across deployments.
- Impact:
  - Security team/config must grant `ORDER_CANCEL` to intended roles.
- Decision ID: DECISION-ORDER-010

### Q: Should the frontend call any advisory pre-check endpoints before submit?

- Answer: No separate pre-check endpoint is required for v1; the submit response is the source of truth. UI may render advisory warnings based on already-loaded order/work/payment summaries, but must not treat them as authoritative.
- Assumptions:
  - Submit is fast enough and returns structured reasons for blocking.
- Rationale:
  - Avoids double-calling and stale pre-check results.
- Impact:
  - Ensure cancel submit returns user-safe blocking codes like `WORK_NOT_CANCELLABLE`.
- Decision ID: DECISION-ORDER-012

### Q: For `CANCELLATION_FAILED`, should the UI offer a “Retry cancellation” action?

- Answer: No retry button in the standard Order Detail UI for v1; UI should display failure status and guidance for manual intervention. Retry (if supported) is an admin-only capability and should be delivered as a separate story.
- Assumptions:
  - Retrying can create repeated side effects in downstream systems.
- Rationale:
  - Conservative UX prevents accidental repeated void/rollback attempts.
- Impact:
  - UI includes support text and surfaces identifiers for support.
- Decision ID: DECISION-ORDER-015

### Q: Is `reason` a fixed enum list (hardcoded) or loaded from backend/config?

- Answer: `reason` is a canonical enum owned by Order domain and versioned; UI should prefer loading the allowed list from backend/config, with a safe fallback to a locally-configured list.
- Assumptions:
  - New reason codes may be added over time.
- Rationale:
  - Prevents UI deployments from blocking new backend reason codes.
- Impact:
  - Backend provides allowed reasons endpoint or embeds it in metadata.
- Decision ID: DECISION-ORDER-006

### Q: Should correlation IDs and downstream subsystem details be displayed in the UI?

- Answer: Show `cancellationId` to all authorized users; show correlation IDs and subsystem details only to admin/support roles via explicit permission.
- Assumptions:
  - Correlation IDs can be used to query logs/traces and should be treated as operational data.
- Rationale:
  - Balances supportability with minimization of internal leakage.
- Impact:
  - Backend redacts restricted fields unless permission is present.
- Decision ID: DECISION-ORDER-015

## Todos Reconciled

- Original todo: "Confirm actual Moqui entity names/fields and whether cancellation summary is embedded in Order detail response or fetched separately." → Resolution: Replace with task: `TASK-ORDER-001` (confirm and document contracts in Moqui screens/services).
- Original todo: "Replace placeholder endpoints with actual Moqui service names and/or REST endpoints once confirmed." → Resolution: Replace with task: `TASK-ORDER-002` (document service names and schemas).
- Original todo: "Add explicit fixtures for status enum variants once the definitive enum set is confirmed." → Resolution: Resolved (define canonical UI enum contract) + Replace with task: `TASK-ORDER-003` (add fixtures for any legacy mappings that remain).

## End

End of document.
