# [FRONTEND] [STORY] Customer: Enforce PO Requirement and Billing Rules During Checkout
## Purpose
Enforce Billing-owned checkout policy during POS checkout by requiring Purchase Order (PO) capture (write-once) and applying credit/terms gating before allowing finalization. Provide deterministic, auditable blocking reasons and a controlled override path that requires an elevation token and reason when policy demands it. Prevent non-compliant commercial checkouts from being finalized while giving clerks clear guidance on what action is needed to proceed.

## Components
- Checkout header (customer/account context, checkout status)
- Billing Requirements panel (policy summary + blockers)
- PO section
  - PO number input (write-once)
  - “Save PO” button
  - Read-only PO audit display (PO value, captured at, captured by)
- Override section
  - Override eligibility/status indicator (allowed/required/recorded)
  - Override reason input (required when override attempted)
  - Elevation token input (required when policy requires elevation)
  - Optional approver/metadata field (only if backend requires)
  - “Request Override” / “Apply Override” button
  - Read-only override audit display (approved at, approved by, token/approval id if present)
- Finalize area
  - “Finalize” primary button
  - Inline blocking message area (why finalize is blocked)
- Field-level error messages (PO input, override reason/token)
- Loading/disabled states for Billing API calls

## Layout
- Top: Checkout header + status
- Main (left/center): Order/checkout details + Finalize area (Finalize button + inline blocker text)
- Right sidebar: Billing Requirements panel
  - PO section (input + save; then read-only audit)
  - Override section (inputs + apply; then read-only audit)
- Footer (optional): Secondary actions (e.g., Back/Cancel)

## Interaction Flow
1. On checkout load (or when customer/payment terms change), UI fetches BillingRuleEvaluation and renders Billing Requirements panel with:
   1) PO policy (required? reason/message), 2) override policy (allowed? requires elevation?), 3) credit/terms gating (blocked? reasons list).
2. Primary flow: PO required blocks finalization until captured
   1) Clerk clicks “Finalize”.
   2) UI calls Billing evaluation (or reuses fresh evaluation if already current) to confirm current blockers.
   3) If PO is required and no PO recorded and no override recorded, UI blocks finalization:
      - Do not call POS finalize.
      - Highlight PO section and show blocker message in Billing Requirements panel and near Finalize.
   4) Clerk enters PO number and clicks “Save PO”.
   5) UI calls PO capture endpoint (write-once); on success:
      - Replace PO input with read-only PO audit display (value, timestamp, user/id).
      - Re-run/refresh Billing evaluation and update blockers.
   6) Clerk clicks “Finalize” again; if no remaining blockers, proceed to POS finalize.
3. Override flow (when allowed/required by policy)
   1) If PO requirement can be overridden, clerk opens Override section.
   2) Clerk enters required override reason; if policy requires elevation, clerk also enters elevation token.
   3) Clerk clicks “Request Override/Apply Override”; UI calls override endpoint (write-once).
   4) On success, show read-only override audit display and refresh Billing evaluation; if blockers cleared, allow Finalize.
4. Credit/terms gating flow
   1) If Billing evaluation indicates credit/terms gating blocks checkout, show clear blocker reason(s) in Billing Requirements panel.
   2) Disable or block Finalize with an explicit message; do not attempt POS finalize until gating is resolved or an allowed override is successfully recorded (if applicable).
5. Error handling
   1) If PO capture returns validation errors, show field-level error on PO input and keep Finalize blocked.
   2) If override endpoint returns validation errors (missing/invalid reason or elevation token), show field-level errors on corresponding fields and keep Finalize blocked.
   3) If Billing endpoints return blocker messages, surface them in Billing Requirements panel and near Finalize.

## Notes
- PO capture and PO override are write-once: once recorded, inputs become read-only and only audit fields are displayed.
- Finalize must be deterministically blocked when Billing evaluation indicates unmet requirements; UI must not call POS finalize until PO is captured or override succeeds (and credit/terms gating is satisfied).
- BillingRuleEvaluation is read-only and drives UI state: required flags, optional message, and any reasons array should be rendered as user-facing blockers.
- Override requires an elevation token when policy demands it (per BILL-DEC-010); ensure the UI enforces required fields before calling the override endpoint.
- Display audit fields returned by backend (timestamps, user/UUIDs, approval ids) to support compliance and reconciliation.
- Ensure loading/disabled states prevent double-submission for PO capture/override and prevent Finalize while requests are in flight.
- TODO: confirm exact backend field name for elevation token (open question) and whether optional approver/metadata is required.
