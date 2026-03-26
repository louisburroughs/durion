## Completion Status: COMPLETE — Wave B Initial Slice Done

---

# Wave B Continuation — CAP-004 + CAP-005

## Completion Status: IN PROGRESS

Orchestration run started: 2026-03-26T23:11:51Z
Execution slice: Workexec Wave B Continuation — CAP-004 (stories 231, 230, 229, 228, 227, 226), CAP-005 (stories 225, 224, 223, 222, 221, 220, 219)
PR target: durion-positivity-frontend
Branch: cap/workexec-wave-b-cont (from master @ 441203c)

## Prior State

Wave B initial slice (CAP-002/CAP-003) is COMPLETE. PR #3 merged to master at 441203c. Workexec feature scaffold is live with all estimate and customer approval pages, `WorkexecService` (estimate + approval operations), and `workexec.models.ts`. Existing pages: estimate-create, estimate-detail, estimate-parts, estimate-labor, estimate-revise, estimate-summary, approval-submit, approval-digital, approval-in-person, approval-partial, approval-detail. All approval routes under `/app/workexec/estimates/:estimateId/approval/...`. No workorder-facing pages exist yet. The `/app/workexec` lazy-load is already registered. Design pack: `design/Shop-Workorder/`.

---

Summary: Implement the workorder promotion and execution slice — promotion preconditions + workorder creation (CAP-004, 6 stories) and workorder execution lifecycle (CAP-005, 7 stories) — as PR #4 in durion-positivity-frontend. Establishes `WorkorderDetailPageComponent` as the central hub. ALL operation_ids require contract normalization via OpenAPI inspection. CAP-006 (completion) and CAP-007 (invoicing) deferred to next run.

## Implementation Steps

- [x] Step 1: Read and analyze source materials — PRD-multistage-capability-frontend-build.md, PRD-agent-capability-frontend-execution.md, CAP-004/AGENT_WORKSET.yaml, CAP-005/AGENT_WORKSET.yaml, all 13 frontend story markdown files (CAP_004.231–226, CAP_005.225–219), domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md, domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md, design/DESIGN.md, design/source/theme-tokens.md, design/Shop-Workorder/ design pack (BayDetails.png, ShopFloor.png, WorkorderDashboard.png, DESIGN.md), all 13 wireframes under domains/workexec/.ui/, existing workexec service + routes + models

- [x] Step 2: Designer first-pass intake — Design Brief issued. Architectural Ledger system applied: blueprint-blue gradient header, Electric Teal for primary CTAs only, tonal separation with no dividers, underlined ledger inputs, glassmorphism modals, ledger-style alternating row tables, Public Sans for display/secondary Inter for data, status chips with rounded-sm, role-based guards via @if. Tokens confirmed against design/Shop-Workorder/DESIGN.md and theme-tokens.md. — design brief for workexec workorder hub covering CAP-004 (promotion flow, workorder scaffold, audit trail) and CAP-005 (assign, start, labor, parts, substitutions, change requests, role visibility). Brief must cover: workorder dashboard surface hierarchy using Shop-Workorder design pack (ShopFloor.png / WorkorderDashboard.png as primary), WIP bay card layout, tabbed execution workspace (labor / parts / change-requests tabs), "Promote to Work Order" CTA treatment on estimate-detail, ledger field style for labor and parts entry, role-based conditional rendering approach, tonal separation using blueprint-blue gradients, "Architectural Ledger" style compliance.

- [x] Step 3: Create execution branch cap/workexec-wave-b-cont from master (441203c) in durion-positivity-frontend via durion/.github/hooks/create-branch-hook.sh — PASS branch=cap/workexec-wave-b-cont

- [ ] Step 4: Story 231 (CAP-004) — Validate Promotion Preconditions (action gating on /app/workexec/estimates/:estimateId): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Add "Promote to Work Order" CTA to EstimateDetailPageComponent. Pre-flight validation before calling promotion endpoint: check estimate is in APPROVED state, approved snapshot exists, no duplicate workorder. Display specific precondition failure messages (expired approval, missing approved scope, duplicate promotion). Disable CTA when preconditions fail. Wire `getEstimateById` (status check), `promoteEstimateToWorkorder` (with validation preflight). Contract normalization: inspect OpenAPI for `promoteEstimateToWorkorder` request/response shape and record in runs/latest.md.

- [ ] Step 5: Story 230 (CAP-004) — Create Workorder from Approved Estimate — NEW PAGE WorkorderDetailPageComponent (/app/workexec/workorders/:workorderId): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. CORE STORY — creates `WorkorderDetailPageComponent` as the workorder hub. Scaffold workexec workorder sub-structure: workorders/ pages. Wire `promoteEstimateToWorkorder` (POST, with Idempotency-Key) → navigate to /app/workexec/workorders/:workorderId on success. Implement WorkorderDetailPageComponent: header (workorder ID, status badge, customer/vehicle, assigned tech), tabs placeholder (Labor | Parts | Change Requests), audit summary section. Wire `getWorkorderById`, `getWorkorderDetail`. Register workorders/:workorderId route in workexec.routes.ts. Add WorkorderDto, WorkorderDetailDto to workexec.models.ts.

- [ ] Step 6: Story 229 (CAP-004) — Generate Workorder Items from Estimate Scope (workorder detail page): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement workorder items/scope display in WorkorderDetailPageComponent. On load, display line items promoted from estimate (parts + labor). Items are read-only ledger rows showing quantity, description, price. Show original estimate reference. Wire `getWorkorderDetail` (items embedded in response) or separate items fetch from workorder context. Display empty state if no items promoted.

- [ ] Step 7: Story 228 (CAP-004) — Enforce Idempotent Promotion (UI behavior on estimate-detail): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Enhance promotion action: disable "Promote" button after first click, show progress indicator. On response: success → navigate to workorder. 409 conflict (duplicate) → display existing workorder link ("Work order already exists for this estimate: [link]"). Timeout/5xx → show safe-retry guidance ("Action may have succeeded — check your work orders before retrying"). Corrupted link case → admin escalation message. `Idempotency-Key` header on `promoteEstimateToWorkorder`. Wire `getWorkorderById` to resolve existing WO on 409.

- [ ] Step 8: Story 227 (CAP-004) — Handle Partial Approval Promotion (UI behavior on estimate-detail): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Handle partial approval case: display approved scope summary before promotion, show which line items are approved vs declined. Allow promotion with approved-only scope (partial). Display declined items as excluded from workorder. Wire `getEstimateSummary` (approved scope), `promoteEstimateToWorkorder` with partialApproval flag per OpenAPI. Show post-promotion summary of what was and was not included.

- [ ] Step 9: Story 226 (CAP-004) — Record Promotion Audit Trail (inline section in WorkorderDetailPageComponent): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Add read-only promotion audit section to WorkorderDetailPageComponent: promoted-from estimate link, promoting user, timestamp, snapshot reference, any partial exclusions. Wire `getTransitionHistory` (workorder transitions log). Display as collapsible ledger section. No separate page — inline in detail.

- [ ] Step 10: Story 225 (CAP-005) — Assign Technician to Workorder (/app/workexec/workorders/:workorderId/assign): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement WorkorderAssignPageComponent. Technician picker with search/filter. Current assignment display. Reassignment flow with reason capture. Navigate back to workorder detail on success. Wire `assignTechnician`, `reassignTechnician`, `getTechnicianAssignment`. Contract normalization: confirm assign endpoint shape in OpenAPI. Register route workorders/:workorderId/assign.

- [ ] Step 11: Story 224 (CAP-005) — Start Workorder and Track Status (WorkorderDetailPageComponent status transitions): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Add status transition CTAs to WorkorderDetailPageComponent header: "Start Work" (PENDING→IN_PROGRESS), status badge updates. Disable transitions for unauthorized states. Show in-progress indicator while status call is pending. Wire `startWork`, `startWorkSession`. Display current status badge prominently with color treatment per design tokens. Contract normalization: confirm startWork/startWorkSession paths in OpenAPI.

- [ ] Step 12: Story 223 (CAP-005) — Record Labor Performed (/app/workexec/workorders/:workorderId/labor): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement WorkorderLaborPageComponent. Labor session: start/stop session timer (startLaborSession/stopLaborSession), record completed labor entries (createLaborPerformed). Display labor history ledger (getLaborHistory). Manual time entry with service code and description. Flat-rate vs time-based mode. Wire `startLaborSession`, `stopLaborSession`, `createLaborPerformed`, `getLaborHistory`, `adjustLaborHours`. Register route workorders/:workorderId/labor. Contract normalization: confirm labor paths.

- [ ] Step 13: Story 222 (CAP-005) — Issue and Consume Parts (/app/workexec/workorders/:workorderId/parts): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement WorkorderPartsPageComponent. Display workorder parts list from workorder detail. Issue parts from stock (issueParts), consume parts (consumeParts), return unused (returnParts/returnUnusedQuantity). Quantity validation. Usage history (getUsageHistory). Wire `issueParts`, `consumeParts`, `returnParts`, `returnUnusedQuantity`, `correctPartQuantity`, `getUsageHistory`. Register route workorders/:workorderId/parts. Contract normalization: confirm parts paths.

- [ ] Step 14: Story 221 (CAP-005) — Handle Part Substitutions and Returns (within WorkorderPartsPageComponent): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Add substitution flow to WorkorderPartsPageComponent: "Suggest Substitutes" button (suggestSubstitutes) → substitution picker → confirm substitution (substitutePart) with reason capture. Display original vs substituted part. Wire `substitutePart`, `suggestSubstitutes`. No separate page — integrated as modal/panel in parts page.

- [ ] Step 15: Story 220 (CAP-005) — Request Additional Work and Flag for Approval (/app/workexec/workorders/:workorderId/change-requests): RED → anvil → HTML Specialist → TypeScript Specialist → integrate → Designer sign-off → Code Review. Implement WorkorderChangeRequestsPageComponent. Create change request form (description, new items, labor). Change request list with status badges. Approve/decline actions for authorized roles. Wire `createChangeRequest`, `approveChangeRequest`, `declineChangeRequest`, `getChangeRequestsByWorkorder`, `getChangeRequestById`. Register routes workorders/:workorderId/change-requests. Contract normalization: confirm change-request paths.

- [ ] Step 16: Story 219 (CAP-005) — Apply Role-Based Visibility (cross-cutting on WorkorderDetailPageComponent and sub-pages): RED → anvil → TypeScript Specialist → integrate → Designer sign-off → Code Review. NOTE: No separate page — cross-cutting implementation. Add `UserRoleService` or leverage existing auth service to gate sections. Hide cost/margin fields from non-authorized roles. Hide "Assign Technician" from non-managers. Hide "Approve Change Request" from technicians. Use `*ngIf`/`@if` guards based on role claims from auth context. Wire `getOperationalContext` to resolve role-based UI flags from backend if present. Document role mapping in component comments.

- [ ] Step 17: Designer final sign-off on full Wave B continuation integration. Designer must review all 13 implemented stories against design/DESIGN.md, design/Shop-Workorder/ pack, and theme-tokens.md. Must return Design Verdict: PASS before proceeding to verification.

- [ ] Step 18: Verification gates:
    - `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
    - `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`
    - Both must pass. Any failures must be documented in run artifacts with remediation notes before PR creation.

- [ ] Step 19: Update capability run artifacts — create or overwrite:
    - `/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-004/runs/latest.md`
    - `/home/louis-burroughs/IdeaProjects/durion/docs/capabilities/CAP-005/runs/latest.md`
    Each artifact must include: capability id, domain, stories processed, files changed, operation_ids implemented, validation commands executed, status (done/partial/blocked), blockers, assumptions, and contract normalization findings for empty operation_ids.

- [ ] Final Step: Create PR in durion-positivity-frontend by invoking durion/.github/hooks/pull-request-hook.sh with completed stories 231, 230, 229, 228, 227, 226, 225, 224, 223, 222, 221, 220, 219 and validation evidence from Step 18 and run artifacts from Step 19.

## Domain Ownership Map

| Angular Domain | Capability | Story | Title | Route / Placement |
|---|---|---|---|---|
| `workexec` | CAP-004 | 231 | Validate Promotion Preconditions | /app/workexec/estimates/:estimateId (action gate) |
| `workexec` | CAP-004 | 230 | Create Workorder from Approved Estimate | /app/workexec/workorders/:workorderId (NEW WorkorderDetailPageComponent) |
| `workexec` | CAP-004 | 229 | Generate Workorder Items from Estimate | /app/workexec/workorders/:workorderId (items section) |
| `workexec` | CAP-004 | 228 | Enforce Idempotent Promotion | /app/workexec/estimates/:estimateId (UI behavior) |
| `workexec` | CAP-004 | 227 | Handle Partial Approval Promotion | /app/workexec/estimates/:estimateId (scope display) |
| `workexec` | CAP-004 | 226 | Record Promotion Audit Trail | /app/workexec/workorders/:workorderId (audit section) |
| `workexec` | CAP-005 | 225 | Assign Technician to Workorder | /app/workexec/workorders/:workorderId/assign |
| `workexec` | CAP-005 | 224 | Start Workorder and Track Status | /app/workexec/workorders/:workorderId (status CTAs) |
| `workexec` | CAP-005 | 223 | Record Labor Performed | /app/workexec/workorders/:workorderId/labor |
| `workexec` | CAP-005 | 222 | Issue and Consume Parts | /app/workexec/workorders/:workorderId/parts |
| `workexec` | CAP-005 | 221 | Handle Part Substitutions | /app/workexec/workorders/:workorderId/parts (modal) |
| `workexec` | CAP-005 | 220 | Request Additional Work | /app/workexec/workorders/:workorderId/change-requests |
| `workexec` | CAP-005 | 219 | Apply Role-Based Visibility | cross-cutting on workorder-detail + sub-pages |

## Verification Commands

- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`

## Edge Cases

- ALL 13 stories have empty or minimal operation_ids. Contract normalization (OpenAPI inspection) is mandatory for Steps 4–16 and must be recorded in run artifacts.
- Story 231 (precondition validation) and story 228 (idempotency) and story 227 (partial approval) all modify EstimateDetailPageComponent — do not conflict with existing CAP-002 approval flow logic already in estimate-detail.
- Story 230 creates the WorkorderDetailPageComponent which is the scaffold for stories 229, 226, 224, 219 — must be implemented first in story order.
- Story 219 (role-based visibility) has no page component of its own — must be implemented as conditional rendering guards across workorder-detail and sub-pages. Requires a consistent role-checking pattern.
- Story 221 (substitutions) is co-located in WorkorderPartsPageComponent — no separate route. Implement as modal/slide-out panel.
- `npm test -- --watch=false` flag required; omitting `--watch=false` will hang CI.
- Workorder routes use `/app/workexec/workorders/:workorderId/...` — distinct path segment from existing `/app/workexec/estimates/:estimateId/...` routes. No route conflicts expected.

## Open Questions

- `promoteEstimateToWorkorder`: does it return the new workorderId directly or require a follow-up `getWorkorderById` poll? Inspect OpenAPI response shape during Step 4.
- Story 224 `startWork` vs `startWorkSession`: are these different operations or the same? Inspect OpenAPI during Step 11.
- Story 219 role-based visibility: does the backend return role/permission flags via `getOperationalContext` or should frontend use JWT claims only? Inspect during Step 16.
- Story 220 change requests: is approve/decline action on the change request endpoint or via `patchEstimateStatus`-equivalent for workorders? Inspect OpenAPI during Step 15.

## Deferred

- CAP-006: Workorder Completion (stories 218, 217, 216, 215, 214) — deferred to next run
- CAP-007: Workorder Invoicing (stories 213, 212, 211, 210, 209; 3 missing wireframes) — deferred to next run

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

- [x] Final Step: Create PR in durion-positivity-frontend by invoking durion/.github/hooks/pull-request-hook.sh with completed stories 239, 238, 237, 236, 235, 234, 233, 271, 270, 269, 268 and validation evidence from Step 16 and run artifacts from Step 17. → PR #3 created and merged to master (441203c). https://github.com/louisburroughs/durion-positivity-frontend/pull/3

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
