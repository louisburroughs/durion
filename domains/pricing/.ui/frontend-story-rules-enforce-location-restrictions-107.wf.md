# [FRONTEND] [STORY] Rules: Enforce Location Restrictions and Service Rules for Products
## Purpose
Enable the POS UI to evaluate product restriction rules whenever a Service Advisor adds items or attempts to finalize a transaction, ensuring restricted products are blocked or require a manager override. Provide clear, traceable outcomes per line item (allowed/blocked/requires override/unknown) and enforce that all items have an authoritative evaluation before finalization. Include an admin UI to create/edit/deactivate restriction rules and optionally view audit/history when supported.

## Components
- Transaction screen
  - Line item list/table (SKU/name, qty, price, restriction status badge, reasons)
  - Per-line actions: Remove item, Retry evaluation, Override (if permitted), View details
  - Inline restriction message area (backend message or generic)
  - Finalize/Complete Transaction button (with blocking banner/toast)
- Override modal (manager override)
  - Summary: product, qty, context (location/service), current decision + reasons
  - Rule selection dropdown/list (when required or multiple candidates)
  - Manager credential input (as required by catalog: e.g., user/pass or PIN)
  - Submit override, Cancel
  - Error banner (403/409/5xx) and retry affordance
- Restriction rule admin
  - Rule list (search/filter, empty state message, Create rule CTA)
  - Create/Edit rule form
    - Condition type selector (enum or string input), condition value input
    - Product selector (inventory product search + selected chips/list)
    - Effective start date-time picker, optional end date-time picker
    - Save, Cancel, Deactivate (sets effectiveEnd)
  - Audit/history panel/section (if endpoint exists)
- Global messaging
  - Toasts/banners for service unavailable, policy changed, access denied, validation errors

## Layout
- Top: Transaction header (location/service context, transactionId status) + global alerts area
- Main: Line item table/list with right-aligned per-line restriction badge + actions
- Bottom: Finalize button area with blocking message banner when disabled
- Admin: Left list of rules; Right detail pane (create/edit form + audit/history below)

## Interaction Flow
1. Add product to transaction line items.
2. UI triggers restriction evaluation for the line item (non-commit path) using required context fields; show status as “Unknown/Evaluating” until response.
3. On evaluation response per item:
   1) If decision = Allowed: mark line item normal/finalizable; hide restriction actions.
   2) If decision = Blocked: highlight line item as blocked; show denial message (backend-provided if available, else generic); disable Finalize; show Remove item + Retry evaluation.
   3) If decision = RequiresOverride: show warning state; disable Finalize; show Override action (only if permitted and transactionId exists); display reasons and candidate rule info if provided.
   4) If decision = Unknown (e.g., 5xx/timeout): mark as unknown; allow editing/removal; disable Finalize; show Retry evaluation.
4. Attempt to finalize transaction:
   1) If any item is Unknown: block finalize with “Restrictions must be evaluated before finalizing.”
   2) If any item is Blocked or RequiresOverride: block finalize and focus/scroll to the first blocked item.
   3) If commit-path restriction service unavailable (5xx/timeout): block finalize with exact message “Restriction service unavailable; cannot complete transaction.”
5. Override flow (for RequiresOverride items):
   1) User clicks Override; UI loads override requirements on-demand (may prefetch for authorized users).
   2) Modal shows decision, reasons, and rule selection if required (multiple candidates or response flag indicates selection required).
   3) User enters manager credentials and submits override request.
   4) On override success where overrideId and policyVersion are present: store overrideId on the line item and include in document payload; mark line item finalizable; keep decision displayed as “Overridden” while retaining original reasons.
   5) On override denied: keep line item in RequiresOverride state without overrideId; show denial message (backend-provided if available; otherwise generic).
   6) On 409 conflict (policyVersion mismatch): show “Restriction policy changed; re-evaluate required.”; trigger re-evaluation for that line item; require a new override attempt.
   7) On 403 forbidden: show “Access denied”; keep item restricted.
6. Admin: view/create/edit/deactivate rules (if feature available):
   1) Navigate to Restriction Rules; if 404 from admin endpoints, show “Restriction admin is not available” and hide menu entry if possible.
   2) Empty list: show “No restriction rules found” and “Create rule” CTA (only with manage permission).
   3) Create rule: select condition type/value; select product(s) via inventory search; set effectiveStart and optional effectiveEnd; Save.
   4) Edit rule: update allowed fields; deactivate by setting effectiveEnd (preferred).
   5) Audit/history: display entries if endpoint exists; otherwise omit section.

## Notes
- Line item states and UI behavior:
  - Allowed: normal.
  - Blocked: highlighted; finalize disabled; show remove + retry.
  - RequiresOverride: show override action if permitted and transactionId exists; finalize disabled until approved.
  - Unknown: warning; finalize disabled; show retry.
- Client-side validation (UX-only; backend authoritative) for rule create/edit:
  - effectiveStart required; effectiveEnd optional but must be > effectiveStart when present (half-open semantics; store-local date-time).
  - Condition type/value required; if backend provides enumerations, no free-form; otherwise allow non-empty strings and surface backend errors.
  - At least one product selected (unless backend supports broader scopes; follow backend schema if present).
- Error handling:
  - 400: show field-level errors when provided; keep form open / keep item restricted.
  - 403: show “Access denied”; avoid sensitive details.
  - 404: evaluate/override show generic error and mark Unknown (non-commit) or block finalize (commit); admin 404 treated as feature not available.
  - 409: treat as policyVersion mismatch; require re-evaluation before override.
  - 5xx/timeout: non-commit → Unknown; commit → block finalize with exact unavailability message.
- Messaging must include product identifier (SKU/name) when showing blocked/override-required messages; prefer backend-provided message when available.
- Ensure a successful, authoritative evaluation exists for all line items before enabling finalize; overridden items must remain traceable (show “Overridden” + original reasons) and include overrideId in submission payload.
