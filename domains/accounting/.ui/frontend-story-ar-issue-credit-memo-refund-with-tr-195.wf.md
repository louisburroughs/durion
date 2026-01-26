# [FRONTEND] [STORY] AR: Issue Credit Memo / Refund with Traceability
## Purpose
Enable authorized users to issue and post a credit memo (refund) against a specific invoice with full traceability back to the invoice. The screen must collect credit amount, reason code, and a justification while displaying read-only invoice context to prevent over-crediting and support audit needs. The UI must enforce basic validation and clearly surface backend state/period restrictions.

## Components
- Page header: “Issue Credit Memo” + breadcrumb/back to Invoice Detail
- Read-only Invoice Context panel
  - Invoice ID (UUIDv7)
  - Invoice number (if available)
  - Invoice status (display-only enum/string)
  - Customer name + customer identifier (display-only)
  - Currency code (required for formatting)
  - Invoice total (optional)
  - Remaining refundable/creditable amount (required for “cannot exceed” UX)
  - Optional eligibility helper: eligible flag (boolean) and eligibility message (string)
- Issue Credit Memo form
  - Credit amount input (decimal; currency formatted; > 0)
  - Reason code select (opaque enum values from backend list)
  - Justification textarea/input (min 10 chars; max per backend; UI conservative max if unknown)
- Inline field validation messages (per field)
- Submit button: “Issue Credit Memo” (creates + posts)
- Secondary button: “Cancel” (returns to Invoice Detail)
- Global/banner alert area for backend errors (e.g., accounting period closed)
- Loading state on submit (button spinner/disabled)
- Error summary (optional) for multiple validation errors returned by backend

## Layout
- Top: Header (title + back/breadcrumb)
- Main (two sections stacked or two-column on wide screens):
  - Left/Top: Invoice Context panel (read-only key/value list)
  - Right/Bottom: “Issue Credit Memo” form (amount, reason, justification)
- Bottom: Action row aligned right: [Cancel] [Issue Credit Memo]
- Banner area near top of main content for blocking errors

## Interaction Flow
1. User navigates from Invoice Detail to “Issue Credit Memo” for a specific invoice (invoiceId from route).
2. UI loads and displays read-only invoice context (including currency and remaining refundable/creditable amount).
3. User enters Credit Amount:
   1) UI formats as currency using invoice currency.
   2) UI validates: required, numeric, > 0.
   3) UI validates: cannot exceed remaining refundable/creditable amount (client-side guard; backend authoritative).
4. User selects Reason Code from dropdown (required).
5. User enters Justification:
   1) UI validates: required, min length 10.
   2) UI enforces a conservative max length if backend max is not provided.
6. User clicks “Issue Credit Memo”:
   1) If Reason Code missing, UI blocks submit, shows inline error, and does not call the service.
   2) If any field invalid, UI blocks submit and shows inline errors.
   3) If valid, UI calls Issue Credit Memo (create + post) with idempotency key and form values.
7. On success response (creditMemoId + posted status/timestamps):
   1) UI navigates to Credit Memo Detail for returned creditMemoId.
   2) Credit Memo Detail (out of scope here) must show link/reference to invoice, reason code, justification, amount, and posted timestamp/status.
8. On backend validation error (400 with field errors):
   1) UI maps errors to fields when possible (amount/reasonCode/justification).
   2) UI shows an error summary/banner for non-field errors.
9. On backend accounting period closed (AD-012):
   1) UI shows blocking banner: “Cannot post credit memo because the accounting period is closed.”
   2) UI shows support hint: “Contact Accounting Manager to reopen period or use approved adjustment process.”
   3) No UI action is provided to reopen the period.
10. On state/conflict errors (409 with error codes):
   1) UI shows banner indicating the credit memo cannot be posted due to current invoice/credit memo state.
   2) User remains on form; submit re-enabled after error.

## Notes
- Inputs required: invoiceId (from route), creditAmount (> 0), reasonCode (required), justification (>= 10 chars).
- Read-only context must include currency (for formatting) and remaining refundable/creditable amount (for “cannot exceed” UX); backend remains authoritative.
- Reason codes come from backend list; treat values as opaque enums (display label may be provided separately if available).
- Submit action is “create + post” in one step; include an idempotency key in the request to prevent duplicates on retries.
- Error handling:
  - Field-level validation errors should be shown inline and prevent service call when caught client-side.
  - Backend AD-012 must render the specified banner + support hint; no remediation action in UI.
  - Conflict/state errors should be surfaced clearly in a banner; avoid silent failures.
- Traceability requirement: after success, user must land on Credit Memo Detail that links back to the originating invoice and displays reason/justification and posting metadata.
- TODO (implementation): determine conservative max justification length if backend does not provide one; ensure UI does not truncate silently (show remaining character count or hard limit behavior as decided).
