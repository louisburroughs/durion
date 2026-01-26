# [FRONTEND] [STORY] Payment: Print/Email Receipt and Store Reference
## Purpose
Enable users to print and/or email receipts immediately after a successful payment capture and from invoice/receipt detail views. The UI must fetch and display backend-authoritative receipt references, delivery status/history, and policy-driven reprint/delivery rules. Support “No Receipt” to suppress delivery while keeping the receipt record, and enforce permission gating plus policy-required reason codes and step-up authentication for reprints/duplicate receipts.

## Components
- Payment Success header with capture confirmation and invoice summary
- Receipt summary card (per receipt) with receiptReference, status, createdAt, remaining balance (if present)
- Actions toolbar: Print, Email, No Receipt, View Details, Reprint Receipt (when allowed)
- Receipt list (if multiple receipts per invoice)
- Receipt Detail page header (receiptReference, invoiceId, receiptId)
- Receipt content preview placeholder (read-only; indicates backend-provided printer-ready content)
- Delivery status/history timeline (PRINT/EMAIL events, timestamps, outcomes)
- Policy panel (remaining reprints, window, allowed actions, required inputs)
- Email delivery form (email address input; CRM-suggested email read-only hint)
- Reason code selector (dropdown) when required by policy
- Step-up authentication modal (obtain elevation token) when required by policy
- Permission/blocked state messaging (disabled buttons with tooltip/help text)
- Loading/empty/error states (fetch receipts, deliver/reprint failures)
- Printer selection/status indicator (POS printer subsystem integration)

## Layout
- Top: Page title + invoice/payment context (invoiceId, amount, capturedAt)
- Main:
  - Receipt Actions Inline (Payment Success) or Receipt Summary (Invoice Detail)
  - Receipt List (stacked cards)
  - Each card: left = key fields; right = action buttons
- Receipt Detail:
  - Top: identifiers + status chips
  - Middle: Policy panel (right) + History timeline (main/left)
  - Bottom: Actions section (Print/Reprint/Email/No Receipt) with conditional inputs

## Interaction Flow
1. Payment success → load receipts
   1. On Payment Success screen load, UI calls fetch receipts by invoiceId.
   2. Display receiptReference and receiptStatus for each returned receipt; show remaining balance if provided.
   3. Show action buttons only if user permissions allow and receipt.allowedActions includes the action.
2. Happy path: Print receipt after capture
   1. Cashier clicks Print on a receipt card.
   2. UI calls backend print/reprint content endpoint (printer-ready payload or print job descriptor).
   3. Frontend sends payload to POS printer subsystem.
   4. UI refreshes receipt state and updates delivery history/status from backend.
3. Email receipt (optional delivery)
   1. Cashier clicks Email.
   2. UI shows email form with CRM-suggested email as a read-only hint (user can type a different email if allowed by UX; suggestion is not authoritative).
   3. If policy requires reason code and/or elevation token, prompt for required inputs before submit.
   4. Submit deliver request (type=EMAIL, email, optional reasonCode/elevationToken).
   5. Refresh receipt to show updated delivery status/history.
4. No Receipt (suppress delivery)
   1. User selects No Receipt.
   2. UI submits deliver request (type=NONE/NO_RECEIPT per backend enum) to suppress delivery while keeping receipt record.
   3. Refresh receipt to show updated status/history (backend authoritative).
5. Navigate to Receipt Detail
   1. User clicks View Details from Payment Success or Invoice Detail receipt summary.
   2. Navigate to Receipt Detail by receiptId; load receipt + policy + history from backend.
6. Reprint flow with policy + permissions
   1. User clicks Reprint Receipt (only visible/enabled when permitted and allowedActions includes REPRINT).
   2. UI evaluates backend policy output:
      1. If reason code required, show reason code dropdown and require selection before continuing.
      2. If elevation required, open step-up auth modal to obtain elevation token; block submit until token acquired.
   3. Submit reprint request with required reasonCode and/or elevationToken.
   4. Print backend-provided reprint content; UI must display “REPRINT/DUPLICATE” indication only if included in backend content/policy (no frontend-invented watermarking).
   5. Refresh receipt state to reflect updated history and remaining reprints/window.
7. Edge cases and states
   1. No receipts returned: show empty state with guidance (receipt may still be generating) and retry.
   2. Backend denies action (policy/permissions): show non-destructive error and keep UI state consistent with refreshed receipt.
   3. Printer failure: show print error, allow retry if policy allows; refresh receipt to confirm whether backend recorded a print event.
   4. Partial payments: if remaining balance present, display prominently on receipt summary/detail.

## Notes
- Receipt is read-only in UI; Billing is the system of record for receipt data, policy evaluation, delivery/reprint commands, and audit trail.
- UI must not infer or invent reprint/duplicate markings; only render indications provided by backend content/policy.
- Action gating requires both user permissions and receipt.allowedActions (backend-provided) to be satisfied.
- Reprint/delivery may require policy-driven reason codes and/or step-up authentication; elevation token must be obtained via the specified step-up flow and included in requests when required.
- Delivery status/history must be displayed and refreshed after actions; backend remains authoritative for final state.
- Support multiple roles: Cashier (print/email), Supervisor/Manager (step-up auth/approvals), Customer Service (reprint with permission + reason), Customer (recipient).
- Ensure navigation paths: Payment Success → inline actions; Invoice Detail → receipt summary; Receipt Detail → actions + history.
- TODO (design): define disabled-state messaging, reason code list presentation, and step-up auth modal UX; confirm exact enum values for delivery types and receipt status from backend contract.
