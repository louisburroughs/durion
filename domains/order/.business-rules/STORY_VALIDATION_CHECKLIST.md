
# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates Order-domain cancellation UI and service work for correctness, safety, and determinism. It incorporates resolved open questions into testable acceptance criteria, with a bias toward conservative defaults and backend-authoritative policy.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:order`
- [ ] Verify primary actor(s) and permissions (`ORDER_CANCEL`)

## Data Model & Validation

- [ ] Validate required inputs and types (`orderId`, `reason`, optional `comments`)
- [ ] Verify date/time and timezone semantics for audit timestamps (rendered in user locale; stored server-side)
- [ ] Quantity and rounding rules: N/A (no monetary calculations in UI beyond display)

## API Contract

- [ ] Verify endpoints/services, request/response schema, and deterministic errors (`errorCode`, `message`, optional `details`, optional `fieldErrors`)
- [ ] Verify `409` conflict handling and post-submit refresh

## Events & Idempotency

- [ ] UI prevents duplicate submissions (disable submit while in-flight)
- [ ] Backend treats repeat cancellation requests idempotently for already-cancelled orders

## Security

- [ ] Permission gating for sensitive payloads and raw payload redaction (do not show payment internals)

## Observability

- [ ] Ensure trace identifiers and audit fields surface in UI and logs (admin-only for correlation IDs)

## Acceptance Criteria (per resolved question)

### Q: What is the canonical frontend domain label for this work: `domain:order` vs `domain:positivity` vs `domain:payment` / “Point of Sale”?

- Acceptance: Work is labeled `domain:order`; cancellation UI is implemented as Order-owned orchestration (no direct UI calls to payment/work domains).
- Test Fixtures: An issue/story with labels and code ownership review.
- Example API request/response (code block)
  ```json
  {
    "storyLabels": ["domain:order"],
    "dependsOn": ["payment", "workexec"]
  }
  ```

### Q: What are the Moqui service names/endpoints and parameter mappings for loading order detail, submitting cancellation, and retrieving latest cancellation record?

- Acceptance: The UI calls exactly one Order-owned service to cancel, passing `orderId`, `reason`, `comments`; order detail loads cancellation summary either embedded or via a single Order-owned “latest cancellation” read service.
- Test Fixtures: Mock contracts for `orderDetail` and `cancelOrder` services.
- Example API request/response (code block)
  ```json
  {
    "request": {"orderId": "ORD-123", "reason": "CUSTOMER_REQUEST", "comments": ""},
    "response": {"orderId": "ORD-123", "status": "CANCELLED", "message": "Order cancelled", "cancellationId": "CAN-001"}
  }
  ```

### Q: What is the definitive order status enum set the frontend will receive?

- Acceptance: UI renders `CANCELLING`, `CANCELLED`, `CANCELLED_REQUIRES_REFUND`, `CANCELLATION_FAILED`; unknown statuses do not break the screen and render a safe fallback.
- Test Fixtures: A list of order detail responses for each status.
- Example API request/response (code block)
  ```json
  {
    "orderId": "ORD-123",
    "status": "CANCELLING",
    "cancellationSummary": {"cancellationId": "CAN-001"}
  }
  ```

### Q: How does the frontend determine `ORDER_CANCEL` capability?

- Acceptance: Cancel action is shown/enabled only when backend indicates capability (e.g., `canCancel=true`) and user has permission; forced attempts without permission result in `403` and safe UI handling.
- Test Fixtures: Order detail responses with `canCancel=true/false` and users with/without permission.
- Example API request/response (code block)
  ```json
  {
    "orderId": "ORD-123",
    "canCancel": false,
    "status": "OPEN"
  }
  ```

### Q: Should the frontend call any advisory pre-check endpoint(s) before submit?

- Acceptance: UI does not call a separate pre-check endpoint; submit response drives blocking messages and final state rendering.
- Test Fixtures: Cancel submit returns `400` with `errorCode=WORK_NOT_CANCELLABLE`.
- Example API request/response (code block)
  ```json
  {
    "errorCode": "WORK_NOT_CANCELLABLE",
    "message": "Order cannot be cancelled because work has started",
    "details": {"workStatus": "LABOR_STARTED"}
  }
  ```

### Q: For `CANCELLATION_FAILED`, should the UI offer a “Retry cancellation” action?

- Acceptance: UI shows status + guidance only; no retry action is present in standard Order Detail.
- Test Fixtures: Order detail with `status=CANCELLATION_FAILED`.
- Example API request/response (code block)
  ```json
  {
    "orderId": "ORD-123",
    "status": "CANCELLATION_FAILED",
    "message": "Cancellation failed; manual intervention required"
  }
  ```

### Q: Is `reason` a fixed enum list (hardcoded) or loaded from backend/config?

- Acceptance: UI uses backend/config as source of truth when available; if not available, UI uses a local fallback list that includes `OTHER`.
- Test Fixtures: Backend returns allowed reasons list including `CUSTOMER_REQUEST` and `OTHER`.
- Example API request/response (code block)
  ```json
  {
    "allowedCancellationReasons": ["CUSTOMER_REQUEST", "DUPLICATE_ORDER", "OTHER"]
  }
  ```

### Q: Should correlation IDs and downstream subsystem details be displayed in the UI, or restricted to logs/admin views?

- Acceptance: `cancellationId` is visible to authorized users; correlation IDs and subsystem details require an explicit admin/support permission and are otherwise omitted/redacted.
- Test Fixtures: Two users (standard vs support) viewing the same cancelled order.
- Example API request/response (code block)
  ```json
  {
    "cancellationId": "CAN-001",
    "support": {"correlationId": ""}
  }
  ```

## End

End of document.
