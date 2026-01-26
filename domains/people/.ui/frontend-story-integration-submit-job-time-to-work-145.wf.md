# [FRONTEND] [STORY] Integration: Submit Job Time to workexec as Labor Performed (Idempotent)
## Purpose
Enable a user to submit an eligible TimeEntry to Workexec as “Labor Performed” using an idempotent POST request. Provide a review-and-confirm experience that validates billable time against session duration minus breaks, supports manager approval when required, and clearly communicates submission success/failure. Ensure retries reuse the same idempotency key/correlation id for the same submit attempt.

## Components
- Page header: “Time Entry Review” / “Submit to Workexec”
- Time entry summary panel (read-only fields): timeEntryId, jobId, workerId, start timestamp, hours (decimal), status/eligibility
- Task breakdown list (per task: name/code, minutes/hours, subtotal)
- Breaks list (per break: start/end or duration, deducted time)
- Totals section: clock-in/out duration, total breaks, total billable minutes/hours, validation indicator
- Approval section: manager approval required toggle/indicator (policy-driven), approval history list, approval timestamp
- Primary CTA button: “Submit to Workexec”
- Secondary actions: “Retry submission” (only on failure), “Back” / “Cancel”
- Inline alerts/toasts: validation errors, submission errors, success confirmation
- Submission status area: current state (Not submitted / Submitting / Submitted), submitted timestamp
- Confirmation modal (if required): “Confirm submission” with summary + required approval acknowledgement
- Submission result details: returned workexec laborPerformedId (if present) and/or correlation id used

## Layout
- Top: Page header + breadcrumb/back
- Main (two-column):
  - Left: Time entry summary + Approval section (incl. history)
  - Right: Task breakdown + Breaks + Totals/validation
- Bottom sticky footer: Primary “Submit to Workexec” CTA + secondary actions; status/alerts near CTA

## Interaction Flow
1. Load review screen for a TimeEntry.
2. Display read-only identifiers (timeEntryId, jobId, workerId), start timestamp, hours, and eligibility status.
3. Render task breakdown, breaks, and computed totals (session duration, breaks deducted, total billable).
4. Validate totals:
   1. If billable minutes > actual session time minus breaks, show error “INVALID_BILLABLE_MINUTES” and disable Submit.
   2. If negative minutes/hours, show error “INVALID_BILLABLE_MINUTES” and disable Submit.
5. If location policy requires manager approval:
   1. Show approval required indicator and approval history.
   2. Disable Submit until approval is present; otherwise show modal requiring acknowledgement before proceeding.
6. Primary flow (first-time submit):
   1. User clicks “Submit to Workexec”.
   2. UI generates an idempotency key/correlation id for this submit attempt and stores it for retries.
   3. UI sends POST to configured Workexec endpoint with `Idempotency-Key` header (or equivalent) set to the stored key.
   4. Request body maps TimeEntry to labor-performed schema:
      1. quantity unit = hours
      2. source.system set (configured constant)
      3. sourceReferenceId = timeEntryId
   5. While awaiting response, show “Submitting…” state and disable actions.
   6. On 2xx response: show status “Submitted”, display returned laborPerformedId (if present) or the correlation id used, and record submitted timestamp.
7. Retry flow (same attempt):
   1. If submission fails (non-2xx/network), show error message and keep “Retry submission” available.
   2. On retry, reuse the same stored idempotency key/correlation id and resend the POST.
8. Fixture-driven edge cases:
   1. Submit session with accurate billable minutes → success and “Submitted” state.
   2. Submit with breaks recorded → totals reflect deductions; submission uses final hours.
   3. Submit multiple tasks → tasks aggregated to session totals; submission uses aggregated hours.

## Notes
- TimeEntry fields are read-only inputs: timeEntryId, jobId, workerId (opaque strings), start timestamp (ISO-8601), hours (decimal, must be positive), status (must indicate eligibility).
- Endpoint path must be confirmed (two candidate paths referenced); UI should rely on configured endpoint.
- Idempotency requirement: generate and persist an idempotency key per submit attempt; retries must reuse the same key.
- Acceptance criteria:
  - Only eligible TimeEntries can be submitted; ineligible status disables Submit with explanation.
  - Successful 2xx response updates UI to “Submitted” and shows laborPerformedId or correlation id.
  - Validation blocks submission when billable minutes exceed allowable session time or are negative (INVALID_BILLABLE_MINUTES).
- Audit/traceability: display approval history and submitted timestamp; display correlation id used for the submit attempt.
- TODO (implementation): confirm exact header name for idempotency and exact Workexec endpoint path; confirm labor-performed request schema field names and response field containing laborPerformedId.
