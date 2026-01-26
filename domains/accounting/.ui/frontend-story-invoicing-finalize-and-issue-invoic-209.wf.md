# [FRONTEND] [STORY] Invoicing: Finalize and Issue Invoice
## Purpose
Enable a user to convert a reviewed Draft invoice into an official Issued invoice that becomes immutable and auditable. The invoice detail screen must clearly show issuance blockers, allow issuing only when blockers are absent, and refresh to display issuance metadata and identifiers after success. This reduces billing errors and prevents duplicate issuance.

## Components
- Page header with invoice identifier (UUID) and status badge (Draft/Issued)
- Read-only invoice summary panel (customer/account, period, currency, totals)
- Totals section (read-only): subtotal, tax, discounts, total, amount due/paid (as available)
- Optional breakdown sections (read-only lists/tables) for line/tax/discount breakdown arrays (if present)
- Traceability Snapshot panel (read-only, immutable fields)
- Optional Artifacts panel (read-only list from invoice artifacts source)
- Issuance Blockers panel (list; empty state when none)
- Primary action button: “Issue Invoice” (enabled/disabled based on blockers + status)
- Confirmation modal for issuing (confirm/cancel)
- Loading state (skeleton/spinner) and error toast/inline error banner
- Post-issuance metadata fields (read-only): issuedAt, issuedBy, invoiceNumber (or equivalent)

## Layout
- Top: Breadcrumb/back + Title “Invoice” + Status badge + Primary action area (Issue Invoice)
- Main (two-column):
  - Left/main column: Invoice summary → Totals → Breakdown sections (if present) → Traceability Snapshot
  - Right column: Issuance Blockers → Issuance metadata (only after issuance) → Artifacts (optional)
- Footer: none (or minimal spacing)

## Interaction Flow
1. Open invoice detail screen.
2. UI fetches invoice detail from the invoice detail endpoint and renders read-only fields.
3. UI displays Issuance Blockers panel:
   1. If blockers list is non-empty: show each blocker item; disable “Issue Invoice” and provide disabled-state helper text (e.g., “Resolve blockers to issue”).
   2. If blockers list is empty and invoice status is Draft: enable “Issue Invoice”.
4. User clicks “Issue Invoice”:
   1. Show confirmation modal summarizing action (Draft → Issued, immutable) with Confirm/Cancel.
   2. If user cancels: close modal; no API call.
5. User confirms issuance:
   1. UI calls the issue/finalize invoice endpoint for the current invoice.
   2. While pending: disable actions and show loading indicator.
6. On successful issuance:
   1. UI re-fetches invoice detail.
   2. UI updates status to Issued.
   3. UI displays issuance metadata (issuedAt, issuedBy) and invoiceNumber (now present).
   4. “Issue Invoice” action is removed or disabled permanently for this invoice.
7. Error handling:
   1. If issue call fails: show error banner/toast; keep invoice in Draft; re-enable “Issue Invoice” only if blockers are still empty.
   2. If re-fetch fails after success: show non-blocking error and provide a “Retry” affordance (button or link) to reload invoice detail.

## Notes
- Invoice fields in this story are read-only; issuance converts Draft to Issued and must be treated as immutable afterward.
- “Issue Invoice” must only be available when invoice is Draft and issuance blockers are empty; once Issued, the action must not be available.
- After issuance, invoiceNumber is expected to be present (previously absent/null in Draft) and issuance metadata must be shown.
- TraceabilitySnapshot is invoice-embedded and immutable; display canonical field names per decision (BILL-DEC-004) and treat as read-only.
- Artifacts panel is optional and read-only; render only when artifacts exist; include key fields (name/type, createdAt, createdBy, size/pages if provided).
- Totals and breakdown arrays are read-only; render gracefully when optional arrays are missing (hide section or show “No breakdown available”).
- Ensure UI supports auditability cues (e.g., “Issued invoices cannot be edited”) in confirmation modal and/or Issued state header.
