# [FRONTEND] [STORY] Workexec: Handle Substitution Pricing for Part Substitutions
## Purpose
Enable users to find and apply policy-eligible part substitutes from a specific part line item on both Work Orders and Estimates. The UI must display candidate availability, backend-provided policy flags, and pricing (or “Price unavailable”) without inferring eligibility. Users can apply a selected substitute via a backend call with idempotency, while the UI enforces “cannot apply without price unless manual price permission” using backend-provided capability and pricing fields. The flow must support cancel, empty states, and error handling (including concurrency conflicts) without changing the line unless apply succeeds.

## Components
- Work Order screen: Parts line items table/list (modified)
  - Per-line action button/link: “Find substitutes”
  - Inline disabled state + tooltip/reason text (when action not allowed)
- Estimate screen: Parts line items table/list (modified)
  - Per-line action button/link: “Find substitutes”
  - Inline disabled state + tooltip/reason text
- Substitution Picker Dialog (reusable Moqui include)
  - Dialog header: “Find substitutes” + target part summary (part number/description)
  - Loading indicator/skeleton state
  - Optional toggle: “Show unavailable” (on/off)
  - Error banner area (permission, conflict, generic)
  - Empty state panel/messages
  - Candidate list (single-select)
    - Candidate row: part number/SKU, description
    - Availability status + optional message
    - Price display (currency) or “Price unavailable”
    - Policy flags badges (as returned)
    - Disabled row state + reason (non-eligible/unavailable/pricing gate)
  - Footer buttons: “Cancel”, “Apply” (primary), optional “Refresh candidates” (shown on 409)

## Layout
- Parent screens (Work Order / Estimate):
  - Main: Parts line items list/table
    - Each row: part info | qty/price | actions (… / “Find substitutes”)
- Dialog:
  - Top: Title + target part summary; right: close (X)
  - Main: toggle “Show unavailable” (top), then list/error/empty content
  - Bottom (footer): left error hint (if any), right: Cancel | Apply

## Interaction Flow
1. Open picker (Work Order or Estimate)
   1. User clicks “Find substitutes” on a specific part line item.
   2. If action is disabled (document not editable or backend-provided disable reason/capability), show disabled state and reason; do not open dialog.
   3. Dialog opens and immediately shows loading state.
   4. UI calls candidates endpoint with target identifiers (document type/id, line id) and current “Show unavailable” setting.
   5. On success, render candidate list rows with availability, optional message, pricing (or “Price unavailable”), and policy flags badges exactly as returned.

2. Select candidate and apply
   1. User selects a single candidate (radio/select row).
   2. If candidate is marked non-eligible/disabled by backend fields, it is non-selectable and shows reason.
   3. Apply button remains disabled until a selectable candidate is chosen.
   4. Pricing gate: if selected candidate has missing/unavailable price and user lacks manual price permission (capability flag or equivalent backend-provided signal), disable Apply and show inline reason (e.g., “Price required to apply”).
   5. User clicks Apply:
      - UI sends apply request with selected candidate id and required target identifiers.
      - Include Idempotency-Key header.
   6. On apply success:
      - Close dialog.
      - Update only the affected part line using apply response if provided; otherwise trigger a refresh of the document/parts list.

3. Cancel flow
   1. User clicks Cancel or closes dialog (X).
   2. Dialog closes; no apply call is made; parent line remains unchanged.

4. Toggle “Show unavailable”
   1. User toggles “Show unavailable”.
   2. UI re-fetches candidates with the updated toggle value.
   3. Unavailable candidates (when shown) render disabled with reason/message if provided.

5. Empty states
   1. If candidates response returns zero items: show “No authorized substitutes found.”
   2. If candidates exist but none are eligible/available (per backend flags): show “No substitutes are currently available.”
   3. If “Show unavailable” is enabled, show unavailable candidates disabled with reasons instead of a fully empty list when applicable.

6. Error states and permissions
   1. Candidates fetch returns 403: show permission error state in dialog; do not render list; Apply disabled.
   2. Apply returns 403: show permission error banner; keep dialog open; do not change parent line.
   3. Generic fetch/apply errors (standard error envelope): show error banner with message; keep dialog open; no parent updates.

7. Concurrency conflict (409 on apply)
   1. Apply returns 409 with standard error envelope indicating the line changed since opening.
   2. Keep dialog open and show banner: “This line changed since you opened substitutes.”
   3. Provide “Refresh candidates” action that re-calls candidates endpoint using current “Show unavailable” setting.
   4. Only refresh parent screen/line after a successful apply or explicit user refresh action (if implemented).

## Notes
- Must support both Work Orders and Estimates with the same picker UX; backend enforces eligibility (UI must not be authoritative).
- Candidate display must use backend-provided fields only (availability, messages, policy flags, eligibility/disabled indicators); no inference or client-side policy logic.
- Pricing display rules: show formatted currency when price is present; otherwise show “Price unavailable.”
- Apply gating: UI must prevent apply when price is missing unless manual price permission is present (capability signaling if available; otherwise rely on backend enforcement and surface 403/validation errors).
- Apply must include Idempotency-Key header; apply behavior should be safe to retry without duplicating substitution history.
- Apply creates immutable substitution history server-side; UI does not edit policies, master data, or implement manual price entry (only gates apply).
- Refresh strategy: prefer updating the single line from apply response; fallback to re-fetching the document/parts list.
- Align all error/empty states to backend outcomes and standard error envelope; ensure dialog remains stable (no parent line changes) until apply succeeds.
