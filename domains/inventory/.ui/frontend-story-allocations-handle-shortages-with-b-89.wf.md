# [FRONTEND] [STORY] Allocations: Handle Shortages with Backorder or Substitution Suggestion
## Purpose
Enable Service Advisors to complete part allocations even when requested quantity exceeds available-to-promise (ATP) by intercepting shortage responses and guiding users through resolution choices. The Work Order Allocation screen should clearly flag shortage-pending lines and provide a deterministic, backend-ordered set of resolution options (substitute, external, backorder). The goal is to prevent silent failures and provide a clear, recoverable path to resolve shortages.

## Components
- Work Order Allocation screen (existing)
  - Allocation line items list/table
  - Per-line shortage indicator/badge (e.g., “Shortage pending”)
  - Per-line action: “Resolve shortage”
  - Allocation submit action (button)
- Shortage Resolution dialog (new modal/popup or sub-screen fragment)
  - Dialog header/title: “Resolve shortage”
  - Shortage summary panel (Requested, ATP, Shortfall; plus part identifier)
  - Options list grouped by type (Substitute / External / Backorder)
  - Option cards/rows (rendered from backend display-safe fields)
  - Primary action per option (e.g., “Select” / “Apply”)
  - Secondary action: “Cancel” / “Close”
  - Non-blocking warning banner: “Some options could not be displayed due to missing data.”
  - Blocking empty state: “No resolution options available.”
- Data refresh/reopen support
  - “Reload line” behavior to fetch allocation line including shortage status and optional last shortage context snapshot

## Layout
- Work Order Allocation screen
  - Top: Work order context header (existing)
  - Main: Allocation lines list/table
    - Each line: Part identifier, requested qty, status area (includes shortage badge when applicable), actions (includes “Resolve shortage” when shortage pending)
  - Footer/side (existing): Allocation submit controls
- Shortage Resolution dialog (modal)
  - Top: Title + close icon
  - Body: Shortage summary panel above grouped options
  - Body: Group sections in backend-provided order; within each group, options in backend-provided order
  - Bottom: Cancel/Close button(s)

## Interaction Flow
1. User edits/selects a part allocation line and submits allocation.
2. UI sends allocation request to backend.
3. If backend returns success (no shortage), UI updates allocation line normally.
4. If backend returns a shortage response:
   1. UI intercepts the submit result and marks the allocation line as “shortage pending” (visible indicator on the line item).
   2. UI opens the Shortage Resolution dialog in the Work Order context.
   3. Dialog displays shortage summary exactly as provided (Requested, ATP, Shortfall; plus preferred part display identifier).
   4. UI renders resolution options grouped by type and displayed in the exact order provided by backend (no re-sorting across types).
5. Option rendering guards:
   1. For any option missing required fields to render/submit its type, hide that option.
   2. If at least one option is hidden due to missing data, show a non-blocking warning banner: “Some options could not be displayed due to missing data.”
   3. If all options are hidden/invalid, show blocking empty state: “No resolution options available.” (no selectable options).
6. User selects a resolution option:
   1. UI submits the selection using the option’s opaque stable identifier (do not resend full option payload).
   2. On success, UI closes dialog and updates the allocation line to reflect resolved state (and removes shortage pending indicator).
   3. On failure, keep dialog open and surface an inline error (implementation-specific), allowing retry or cancel.
7. User cancels/closes the dialog:
   1. Dialog closes without applying a resolution.
   2. Allocation line remains flagged as shortage pending.
8. Reopen flow:
   1. User clicks “Resolve shortage” on the flagged allocation line.
   2. UI loads the allocation line via the provided route to retrieve shortage status and (optionally) last shortage context snapshot.
   3. UI reopens the Shortage Resolution dialog using the latest available shortage context/options.

## Notes
- Deterministic ordering is required: options must appear in the exact backend-provided order and be grouped by type without re-sorting across types.
- Shortage context fields are backend-provided; display the preferred part identifier (trimmed) and the numeric summary (requested, ATP, shortfall). Treat optional identifiers/flags as optional and do not block rendering if absent.
- Resolution options contain display-safe fields; render only what is provided and safe. If an option includes sensitive fields, render safely and do not log.
- Rendering guardrails are mandatory:
  - Hide invalid options (missing required fields for that option type).
  - Show non-blocking warning banner if any options are hidden.
  - Show blocking empty state if no valid options remain.
- Work Order Allocation screen must visibly indicate shortage pending on the affected line and provide a clear “Resolve shortage” action to reopen the dialog.
- Risk note: requirements may be incomplete; keep dialog structure flexible to accommodate additional option types/fields without redesign.
