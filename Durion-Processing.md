## Completion Status: COMPLETE

Orchestration run started: 2026-03-27T00:00:00Z
Execution slice: Workexec Domain Wave B — CAP-002 (stories 239, 238, 237, 236, 235, 234), CAP-003 (stories 233, 271, 270, 269, 268)
PR target: durion-positivity-frontend
Branch: cap/workexec-wave-b (from master @ bf1ce1a)

## Prior State

Wave A (CRM domain) is COMPLETE. PR #2 (cap/crm-wave-a-completion) was created and merged to master at bf1ce1a. All four Wave A completion stories (CAP-089: 174, 173; CAP-092: 163, 164) are done. The workexec domain currently exists as a shell stub only: `src/app/features/workexec/workexec.component.ts` (RouterOutlet passthrough) and `src/app/features/workexec/workexec.routes.ts` (empty children array). The `/app/workexec` lazy-load is already registered in `app.routes.ts`. No pages, services, or models exist yet in the workexec feature. Design pack `design/Shop-Workorder/` contains BayDetails, ShopFloor, WorkorderDashboard references. CAP-003 stories 271, 269, and 268 have empty `operation_ids` and require contract review normalization before or during implementation.

---

Summary: Implement the foundational workexec domain frontend slice — estimate creation and management (CAP-002, 6 stories) and customer approval workflow (CAP-003, 5 stories) — as PR #3 in durion-positivity-frontend. This establishes the workexec feature scaffold: routes, service adapters, models, and page components for the complete estimate-to-approval pipeline. CAP-004 through CAP-007 and remaining workexec capabilities are deferred to Wave B continuation.

## Implementation Steps

- [x] Step 1: Read and analyze source materials — PRD-multistage-capability-frontend-build.md, PRD-agent-capability-frontend-execution.md, CAP-002/AGENT_WORKSET.yaml, CAP-003/AGENT_WORKSET.yaml, all 11 frontend story markdown files (CAP_002.239–234, CAP_003.233/271/270/269/268), domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md, design/DESIGN.md, design/source/theme-tokens.md, design/Shop-Workorder/ design pack (BayDetails.png, ShopFloor.png, WorkorderDashboard.png, DESIGN.md), all workexec wireframes under domains/workexec/.ui/, workexec route/component stubs (workexec.routes.ts, workexec.component.ts), app.routes.ts

- [x] Step 2: Designer first-pass intake — design brief for workexec domain covering CAP-002 (estimate create/edit/parts/labor/totals/summary) and CAP-003 (submit/digital-approval/in-person/partial/expiration) stories. Brief must cover: surface hierarchy for estimate workspace using Shop-Workorder design pack, line-item table layout using "No-Divider" rule, approval flow modal/panel treatment, tonal separation and blueprint-blue gradient CTAs, "Architectural Ledger" style compliance, underlined ledger input fields for all estimate form fields, and token mapping from theme-tokens.md.

- [x] Step 3: Create execution branch cap/workexec-wave-b from master (bf1ce1a) in durion-positivity-frontend via durion/.github/hooks/create-branch-hook.sh

- [x] Step 4: Story 239 (CAP-002) — Create Draft Estimate (/app/workexec/estimates/new): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Scaffold workexec feature structure: pages/, components/, services/, models/. Implement EstimateNewPageComponent. Wire `createEstimate` + `getEstimateById` operations via EstimateService. Route on success to estimate workspace (/app/workexec/estimates/:estimateId). Include idempotency-key header, loading/error/confirmation states, and audit display (created/updated metadata from response). Register route in workexec.routes.ts.

- [x] Step 5: Story 238 (CAP-002) — Add Parts to Estimate — Catalog + Non-Catalog + Price Override (/app/workexec/estimates/:estimateId/parts): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement EstimatePartsPageComponent with catalog part search picker (SKU/description), add part line item with quantity validation, optional price override flow (permission-gated, reason code), non-catalog entry path (policy-gated). Wire `addEstimateItem`, `calculateEstimateTotals`, `createEstimate`, `getEstimateById`, `updateEstimateItem`. Immediate totals refresh after add/edit. Idempotency-Key on mutations.

- [x] Step 6: Story 237 (CAP-002) — Add Labor/Service Line to Draft Estimate — Catalog + Optional Custom (/app/workexec/estimates/:estimateId/labor): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement EstimateLaborPageComponent with service catalog picker (code/description), labor-unit capture (time-based) or flat-rate handling, custom labor entry (policy-gated via backend signal). Wire `addEstimateItem`, `calculateEstimateTotals`, `getEstimateById`, `updateEstimateItem`. Display backend-derived pricing and recalculated totals after add.

- [x] Step 7: Story 236 (CAP-002) — Calculate Taxes and Totals on Estimate (inline on estimate workspace, /app/workexec/estimates/:estimateId): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. NOTE: wireframe resides in domains/pricing/.ui/ (cross-domain reference; no domain change, workexec owns the UI behavior). Implement totals/tax display section in EstimateDetailPageComponent. Trigger `calculateEstimateTotals` on line-item changes. Display subtotal, taxes, fees, discounts, grand total from backend response. Show calculation snapshot read-only link. Block "Submit for Approval" CTA when backend signals missing tax config. Wire `calculateEstimateTotals`, `getEstimateById`. Idempotency-Key on mutations.

- [x] Step 8: Story 235 (CAP-002) — Revise Estimate Prior to Approval — Versioned + Approval Invalidation (/app/workexec/estimates/:estimateId/revise): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement EstimateRevisePageComponent. Expose Revise action on allowed estimate states. Create new version linked to prior (immutable prior). Edit new version line items + terms/notes with save + recalc. Invalidate prior approvals on revision from approval-pending context. Show read-only revision/version history. Wire `createEstimate`, `getEstimateById`, `patchEstimateStatus`, `reopenEstimate`.

- [x] Step 9: Story 234 (CAP-002) — Present Customer-Facing Estimate Summary for Review — Snapshot-Based (/app/workexec/estimates/:estimateId/summary): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement EstimateSummaryPageComponent as read-only snapshot view. Role/capability-based visibility (hide cost/margin fields). Display terms/disclaimers and expiration date when present. CTA to proceed to Submit for Approval (navigates to /app/workexec/estimates/:estimateId/approval/submit). Wire `createEstimateSnapshot`, `getEstimateById`, `getEstimateSummary`.

- [x] Step 10: Story 233 (CAP-003) — Submit Estimate for Customer Approval (/app/workexec/estimates/:estimateId/approval/submit): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement ApprovalSubmitPageComponent. Frontend-driven validation display (field/section errors from backend response). Confirm submission step → execute `createEstimate` (submit transition operation per workset) → display resulting status + approvalRequestId. Show audit metadata (who/when) as read-only if returned by backend. Idempotency-Key on submit.

- [x] Step 11: Story 271 (CAP-003) — Capture Digital Customer Approval (/app/workexec/estimates/:estimateId/approval/digital): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. NOTE: operation_ids is EMPTY in workset — perform contract normalization: inspect durion-positivity-backend/pos-workorder/openapi.yaml for digital approval operation (expected: `approveEstimate` or equivalent) and record in runs/latest.md. Implement ApprovalDigitalPageComponent: signature capture UX (strokes + rendered image), submission to approval endpoint, approval result display (approvalId, timestamp, approver metadata). Validation + error handling for invalid state, missing fields, auth failures.

- [x] Step 12: Story 270 (CAP-003) — Capture In-Person Customer Approval (/app/workexec/estimates/:estimateId/approval/in-person): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement ApprovalInPersonPageComponent. Estimate + approval configuration context display. In-person approval capture using configured method (CLICK_CONFIRM at minimum). Handle invalid state, authorization failures, concurrency conflicts (409 + reload). Show updated estimate status and audit metadata post-approval. Wire `approveEstimate`, `createEstimate` (per workset).

- [x] Step 13: Story 269 (CAP-003) — Record Partial Approval — Work Order (/app/workexec/estimates/:estimateId/approval/partial): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. NOTE: operation_ids is EMPTY — perform contract normalization: inspect OpenAPI for partial approval operation and record finding. Implement ApprovalPartialPageComponent: load work order + line items requiring approval, per-line approve/decline decisions, approval method/proof capture, single atomic idempotent confirmation submit, display resulting Work Order status + approved total. 409 concurrency handling + reload. Read-only audit metadata post-submit.

- [x] Step 14: Story 268 (CAP-003) — Handle Approval Expiration (/app/workexec/estimates/:estimateId/approval/:approvalId): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. NOTE: operation_ids is EMPTY — perform contract normalization: inspect OpenAPI for expiration response codes/error shapes and record finding. Implement expiration detection and handling in ApprovalDetailComponent: disable approve/deny actions on expired state, display expiration status alert + expiration metadata, error handling for backend EXPIRED response on submit, guide user to next step (re-submission or revision). On-load + on-submit expiration checks.

- [x] Step 15: Designer final sign-off on full Wave B initial slice integration. Designer must review all 11 implemented stories against design/DESIGN.md, design/Shop-Workorder/ pack, and theme-tokens.md. Must return Design Verdict: PASS before proceeding to verification.

- [x] Step 16: Verification gates:
    - `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
    - `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`
    - Both must pass. Any failures must be documented in run artifacts with remediation notes before PR creation.

- [x] Step 17: Update capability run artifacts — create or overwrite:
    - `/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-002/runs/latest.md`
    - `/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-003/runs/latest.md`
    Each artifact must include: capability id, domain, stories processed, files changed, operation_ids implemented, validation commands executed, status (done/partial/blocked), blockers, assumptions, and follow-ups (especially contract normalization findings for stories 271/269/268).

- [ ] Final Step: Create PR in durion-positivity-frontend by invoking durion/.github/hooks/pull-request-hook.sh with completed stories 239, 238, 237, 236, 235, 234, 233, 271, 270, 269, 268 and validation evidence from Step 16 and run artifacts from Step 17.

## Domain Ownership Map

| Angular Domain | Capability | Story | Title | Route |
|---|---|---|---|---|
| `workexec` | CAP-002 | 239 | Create Draft Estimate | /app/workexec/estimates/new |
| `workexec` | CAP-002 | 238 | Add Parts to Estimate | /app/workexec/estimates/:estimateId/parts |
| `workexec` | CAP-002 | 237 | Add Labor to Estimate | /app/workexec/estimates/:estimateId/labor |
| `workexec` | CAP-002 | 236 | Calculate Taxes and Totals | /app/workexec/estimates/:estimateId (inline section) |
| `workexec` | CAP-002 | 235 | Revise Estimate Prior to Approval | /app/workexec/estimates/:estimateId/revise |
| `workexec` | CAP-002 | 234 | Present Estimate Summary | /app/workexec/estimates/:estimateId/summary |
| `workexec` | CAP-003 | 233 | Submit Estimate for Customer Approval | /app/workexec/estimates/:estimateId/approval/submit |
| `workexec` | CAP-003 | 271 | Capture Digital Customer Approval | /app/workexec/estimates/:estimateId/approval/digital |
| `workexec` | CAP-003 | 270 | Capture In-Person Customer Approval | /app/workexec/estimates/:estimateId/approval/in-person |
| `workexec` | CAP-003 | 269 | Record Partial Approval | /app/workexec/estimates/:estimateId/approval/partial |
| `workexec` | CAP-003 | 268 | Handle Approval Expiration | /app/workexec/estimates/:estimateId/approval/:approvalId |

## Verification Commands

- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`

## Edge Cases

- Story 236 wireframe lives in `domains/pricing/.ui/` — cross-domain wireframe reference only; domain ownership remains `workexec`. Verify wireframe file exists before starting Step 7.
- Stories 271, 269, 268 have empty `operation_ids` in AGENT_WORKSET.yaml. Contract normalization (OpenAPI inspection) is required during Steps 11–14 and must be recorded in run artifacts.
- WorkexecComponent is a stub with an empty route children array. Step 4 must expand routes before subsequent story steps can register child routes.
- CAP-003 story 233 workset lists `createEstimate` as its only operation_id — cross-reference with contract guide for the actual submit-for-approval endpoint (`patchEstimateStatus` or equivalent) and document assumption in run artifact.
- Approval routes for stories 270/271 share the base route `/app/workexec/estimates/:estimateId/approval/...` — ensure route registration does not conflict.
- `npm test -- --watch=false` flag is required; omitting `--watch=false` will hang CI.

## Open Questions

- Story 233 operation_ids list only `createEstimate` — is the submit-for-approval action backed by `patchEstimateStatus` or a distinct `submitEstimateForApproval` endpoint? Inspect OpenAPI before Step 10.
- Stories 271/269/268 have empty operation_ids — which approval endpoints cover digital capture, partial approval, and expiration handling? Requires OpenAPI inspection during Steps 11–14.
- Story 269 targets "Work Order" approval while the route is anchored to `:estimateId` — confirm whether this story's partial approval applies to the estimate or a promoted work order, and adjust route accordingly.
- Does the digital signature capture in Story 271 require a third-party library or native canvas element? Requires investigation during Step 11 to avoid adding unapproved dependencies.

## Deferred

- CAP-004: Promotion to Workorder (stories 231, 230, 229, 228, 227, 226) — deferred to Wave B continuation
- CAP-005: Workorder Execution (stories 225, 224, 223, 222, 221, 220) — deferred to Wave B continuation
- CAP-006: Workorder Completion (stories 218, 217, 216, 215, 214) — deferred to Wave B continuation
- CAP-007: Invoicing (stories 213, 212, 211) — deferred to Wave B continuation
- CAP-137, CAP-139, CAP-142, CAP-249: Additional workexec capabilities — deferred to Wave B continuation
- CAP-092 Story 162 (PO Requirement Enforcement) — carried forward from Wave A deferral, assign to Wave B continuation
- accounting domain (Wave B) — deferred to separate Wave B PR
- billing domain (Wave B) — deferred to separate Wave B PR
