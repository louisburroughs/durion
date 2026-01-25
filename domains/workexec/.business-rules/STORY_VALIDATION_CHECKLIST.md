# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates WorkExec-domain story implementations for correctness, security, operational visibility, and contract fidelity. It mirrors patterns from accounting and CRM validation checklists, encoding safe defaults and contract requirements for estimate, workorder, and approval workflows.

## Completed items

- [x] Phase 2.2 complete: Created comprehensive validation checklist mirroring accounting/CRM patterns

## Scope/Ownership

- [ ] Confirm story labeled `domain:workexec`
- [ ] Verify primary actor(s) and permissions (Service Advisor, Shop Manager, Customer)
- [ ] Confirm WorkExec is SoR for mutated entities (Estimate, WorkOrder, ApprovalRecord, ApprovalConfiguration)
- [ ] Ensure cross-domain data (Customer, Vehicle, Parts, Services, Tax) is treated as read-only unless explicit contract exists
- [ ] Verify UI does not infer workexec policy (approval requirements, tax calculations, pricing rules) beyond what backend returns
- [ ] Confirm navigation routes/screen paths follow repo conventions and are placed under correct WorkExec menu area
- [ ] Verify new configuration/admin capabilities (ApprovalConfiguration) are explicitly confirmed as WorkExec-owned

## Data Model & Validation

- [ ] Validate all required request inputs are present and correctly typed before submit (dates, UUIDs, enums, amounts, signatures)
- [ ] Verify date range validation is enforced client-side: `endDate >= startDate` (inclusive) for filters
- [ ] Verify UUID validation behavior is consistent per screen:
  - [ ] If IDs are guaranteed UUIDs, block invalid UUID input with inline errors
  - [ ] If IDs are not guaranteed UUIDs (e.g., estimateNumber, workOrderNumber), allow free-text and rely on backend validation
- [ ] Verify currency formatting uses `currencyUomId` from backend and does not assume a default currency
- [ ] Verify money input validation:
  - [ ] `unitPrice > 0`, `quantity > 0`, `laborUnits > 0` for item additions
  - [ ] Amount inputs enforce currency scale/rounding rules consistent with backend (decimal only, no floating point)
  - [ ] Totals display uses backend-calculated values; UI does not recalculate unless contract explicitly allows
- [ ] Verify estimate status eligibility rules are enforced using backend-provided status:
  - [ ] Actions (approve, decline, reopen, submit, revise) disabled when status not eligible
  - [ ] Do not hardcode status strings; use backend enum values (DRAFT, APPROVED, DECLINED, EXPIRED)
- [ ] Verify workorder status transitions follow backend state machine (DRAFT → APPROVED → ASSIGNED → WORK_IN_PROGRESS → AWAITING_PARTS/AWAITING_APPROVAL → READY_FOR_PICKUP → COMPLETED/CANCELLED)
- [ ] Verify workorder item status handling (PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED)
- [ ] Verify approval method validation:
  - [ ] `approvalMethod` constrained to backend enum (CLICK_CONFIRM, SIGNATURE, ELECTRONIC_SIGNATURE, VERBAL_CONFIRMATION)
  - [ ] Signature data required when method is SIGNATURE; base64 PNG validation
  - [ ] Signer name required when capturing signature
- [ ] Verify audit fields displayed in UI are read-only and sourced from backend (`createdAt/By`, `updatedAt`, `approvedAt`, `declinedAt`, `expiresAt`)
- [ ] Verify signature data is treated as untrusted content:
  - [ ] Render image safely (no HTML injection via data URI)
  - [ ] Validate MIME type before display (image/png, image/jpeg only)
- [ ] Verify version field handling:
  - [ ] Estimate `version` is Integer field (not JPA @Version); backend increments on financial changes
  - [ ] UI displays version for audit visibility but does not manage version math
  - [ ] Concurrency conflicts handled via backend response (no If-Match header pattern)

## API Contract

- [ ] Verify each screen has a concrete backend service/endpoint contract documented and implemented
- [ ] Verify list endpoints support server-side pagination and sorting (pageIndex/pageSize/orderBy) and UI uses them
- [ ] Verify error response handling is consistent and actionable:
  - [ ] 400/422 validation errors map to inline field errors where possible
  - [ ] 401/403 show access denied without leaking record existence
  - [ ] 404 shows "not found" with safe messaging
  - [ ] 409 shows conflict/reload guidance (status changed, version mismatch, estimate already promoted)
  - [ ] 5xx/timeout shows retry affordance and preserves user inputs
- [ ] Verify per-item error shapes are supported (highlight affected item row when backend returns item-specific errors)
- [ ] Verify estimate endpoints match backend contract:
  - [ ] `GET /v1/workorders/estimates/{estimateId}` returns EstimateDTO with status, version, items, totals, approval config
  - [ ] `POST /v1/workorders/estimates` creates draft estimate
  - [ ] `POST /v1/workorders/estimates/{estimateId}/approval` approves with signature
  - [ ] `POST /v1/workorders/estimates/{estimateId}/decline` declines with reason
  - [ ] `POST /v1/workorders/estimates/{estimateId}/reopen` reopens within expiry window
- [ ] Verify workorder endpoints (when implemented) match backend contract
- [ ] Verify approval configuration endpoints (when implemented) match backend contract
- [ ] Verify canonical error shape `{ errorCode, message, correlationId?, fieldErrors[]? }` is supported:
  - [ ] UI renders `errorCode` and `message` without exposing sensitive internals
  - [ ] If `fieldErrors` includes field keys, UI maps them to correct form fields

## Events & Idempotency

- [ ] Verify mutations use idempotency key pattern when backend supports it:
  - [ ] Frontend generates idempotency key once per submit attempt
  - [ ] UI prevents double-submit while request is in flight
  - [ ] On retry after timeout, behavior is deterministic per agreed policy
- [ ] Verify estimate approve/decline/reopen handle idempotency outcomes without reinterpretation
- [ ] Verify estimate revision creates new version and preserves approval history audit trail
- [ ] Verify approval records are immutable audit records (no edits allowed)

## Security

- [ ] Verify screen-level authorization is enforced for each WorkExec screen (Moqui artifact authz), not only via hidden buttons
- [ ] Verify action-level authorization is enforced for mutations:
  - [ ] Approve/decline/reopen estimate requires correct permission token
  - [ ] Submit for approval requires correct permission token
  - [ ] Revise estimate requires correct permission token
  - [ ] Add/update items requires correct permission token
  - [ ] Override price (if supported) requires correct permission token and is disabled/hidden otherwise
- [ ] Verify sensitive signature data visibility is gated:
  - [ ] Signature images shown only to authorized roles
  - [ ] Base64 signature data not logged in client-side telemetry or console
- [ ] Verify no secrets/credentials are ever displayed or logged
- [ ] Verify PII minimization:
  - [ ] Customer name, vehicle details, signature not included in client telemetry
  - [ ] Redact PII from error logs and network traces
- [ ] Verify unauthorized users cannot infer existence of estimates/workorders/approvals by probing IDs (consistent 403/404 per policy)
- [ ] Verify approval configuration screens (when implemented) enforce least privilege:
  - [ ] View vs manage permissions are distinct and enforced at screen + action level
  - [ ] Read-only roles cannot access create/edit/delete transitions

## Observability

- [ ] Verify UI surfaces support-friendly identifiers on success/failure:
  - [ ] Estimate operations: `estimateId`, `estimateNumber`, `version`, `status`, timestamps
  - [ ] Approval operations: `approvalRecordId`, `resolutionStatus`, `resolvedAt`, `resolvedBy`
  - [ ] WorkOrder operations: `workOrderId`, `workOrderNumber`, `status`, timestamps
- [ ] Verify Moqui server logs include key identifiers (estimateId, workOrderId, customerId, vehicleId, approvalRecordId) and do not include full signature data
- [ ] Verify correlation/trace header propagation is implemented per project standard
- [ ] Verify W3C Trace Context propagation on all outbound calls:
  - [ ] `traceparent` propagated unchanged when present
  - [ ] `traceparent` generated only if absent
  - [ ] `tracestate` propagated when present
- [ ] Verify approval signature capture displays copyable support identifiers:
  - [ ] `estimateId`, `approvalRecordId`, `approvedAt`, `approvedBy`, `approvalMethod`
- [ ] Verify client-side telemetry excludes raw signature data, customer PII, and includes only identifiers + error codes

## Performance & Failure Modes

- [ ] Verify list screens use server-side pagination and do not fetch full payloads
- [ ] Verify default list load performance targets are met (first page within ~2s) and UI shows loading states
- [ ] Verify large signature image rendering is performant:
  - [ ] Use lazy loading for signature images in list views
  - [ ] Apply size limits/compression with download option if needed
- [ ] Verify concurrency/conflict handling:
  - [ ] On 409 status changed, UI prompts reload and clears stale form state
  - [ ] On 409 version mismatch (if implemented), UI prompts reload
  - [ ] On 409 estimate already promoted, UI shows existing workOrderId
  - [ ] UI does not show success state unless backend confirms success
- [ ] Verify approval flow handles async/timeout gracefully:
  - [ ] Loading states during signature capture submit
  - [ ] Timeout messaging with retry affordance
  - [ ] Preserve captured signature on timeout for retry
- [ ] Verify estimate/workorder detail screens handle missing optional fields gracefully:
  - [ ] Display "Not available" or equivalent for null fields (expiresAt, approvedAt, declineReason)
  - [ ] Do not break rendering when optional audit fields are absent

## Acceptance Criteria (per resolved question)

See [workexec-questions.md](../workexec-questions.md) for per-issue acceptance criteria with example API request/response pairs (to be added in Phase 2.3).
