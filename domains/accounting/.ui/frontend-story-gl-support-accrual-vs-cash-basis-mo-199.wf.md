# [FRONTEND] [STORY] GL: Support Accrual vs Cash Basis Modes
## Purpose
Provide a Moqui UI for Finance users to view and manage a business unit’s accounting basis (Accrual vs Cash) using effective-dated policy records. Users must be able to see the current basis as-of now, review basis history with audit fields, and propose a validated basis change aligned to fiscal period start. Access must be permission-gated so auditors can review history read-only while authorized users can submit changes.

## Components
- Page header: “Accounting Basis”
- Breadcrumb / navigation context: Accounting → Policies → Accounting Basis
- Business Unit selector (dropdown/search)
- Current basis summary card (as-of now)
  - Business Unit name/ID
  - Current basis value (Accrual/Cash)
  - Effective-from timestamp for current basis (if available)
- Actions toolbar
  - “Change Basis” button (permission + state gated)
  - Inline status/notice area for backend messages (e.g., scheduled change exists)
- Basis history table (effective-dated list)
  - Columns: Basis, Effective From, Created At, Created By, Policy ID
  - Optional columns: Reason, Audit/Correlation ID (if returned)
  - Empty state message (no history)
- Change Basis modal/dialog
  - Read-only: current basis
  - Required: new basis (enum select)
  - Required: effectiveFrom (datetime input)
  - Optional: reason (text)
  - Error summary area (top of modal)
  - Field-level validation messages
  - Buttons: Cancel, Save/Submit
- Toast/inline confirmation message on success
- Authorization error banner/toast (for 403)

## Layout
- Top: Page title + breadcrumb
- Main (stacked):
  - Row 1: Business Unit selector (left) | Actions toolbar (right, includes Change Basis)
  - Row 2: Current basis summary card (full width)
  - Row 3: Basis history table (full width)
- Modal overlay: Change Basis dialog centered over page

## Interaction Flow
1. Screen load:
   1. Render page shell with Business Unit selector.
   2. If no Business Unit selected, show empty current/history states and disable “Change Basis”.
2. Business Unit selection change:
   1. Call backend load service to fetch current basis (as-of now) and effective-dated history list.
   2. Populate current basis card and history table.
   3. If backend indicates a scheduled future change exists and stacking is disallowed, display backend-provided message in the notice area and block further changes (disable “Change Basis”).
3. Authorized user opens Change Basis:
   1. User clicks “Change Basis”.
   2. Open modal showing current basis read-only.
   3. User selects new basis (required) and enters effectiveFrom datetime (required; expected to align to fiscal period start).
   4. Optional: user enters reason (if supported/required by backend).
4. Submit basis change (happy path):
   1. Client-side validate required fields are present (do not hard-block on UUID format unless explicitly guaranteed by backend).
   2. If supported, generate a command/audit correlation ID (UUIDv7) for idempotency; otherwise omit.
   3. Call backend change service with businessUnitId, new basis, effectiveFrom, optional reason, optional commandId.
   4. On success: close modal, show confirmation including new basis + effectiveFrom, refresh current basis + history list, and display audit fields (createdAt/createdBy) if provided.
5. Submit basis change (validation errors):
   1. Keep modal open.
   2. Map backend field errors to corresponding inputs; show non-field errors in modal error summary.
6. Submit basis change (authorization error):
   1. If backend returns 403, show authorization error without leaking record existence; keep modal closed or close it if open.
7. Read-only user (auditor) experience:
   1. Load and view current basis + history.
   2. “Change Basis” is hidden or disabled with clear read-only affordance; no edit controls enabled.

## Notes
- Permission gating:
  - Read-only users can view current basis and history but cannot initiate or submit changes.
  - Disable “Change Basis” when no Business Unit is selected or user lacks edit permission.
- EffectiveFrom constraints:
  - Backend is source of truth for validating alignment to fiscal period start; UI should guide but rely on server-side validation.
- Scheduled future change constraint:
  - If stacking is disallowed and a future change exists, UI must block additional changes and display the backend-provided message (requires backend error code/flag/message).
- Audit/trace visibility:
  - History should display who/when/old/new/effectiveFrom via table columns; include createdAt/createdBy and policy ID if returned.
  - Optional correlation/command ID is read-only and only shown if echoed by backend.
- Entry points:
  - Primary: Accounting → Policies → Accounting Basis.
  - Secondary: Business Unit detail → Accounting tab → Accounting Basis (if present).
- Error handling:
  - Support canonical backend validation error shape with field-level mapping plus top-level errors.
  - Do not enforce UUID formatting client-side unless backend contract guarantees it.
