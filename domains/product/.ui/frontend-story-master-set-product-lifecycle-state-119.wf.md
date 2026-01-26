# [FRONTEND] [STORY] Master: Set Product Lifecycle State (Active/Discontinued) with Effective Dates

## Purpose
Enable Product Admins to view and update a product’s lifecycle state (Active/Inactive/Discontinued) with an effective date/time and optional override reason, ensuring lifecycle changes are auditable and enforceable. Provide a way to manage replacement product suggestions for discontinued items so downstream quoting/selling workflows can avoid unsourceable products. Surface backend-provided metadata (effective timestamp, last changed by/at, override reason) clearly and handle legacy products where effective date may be null.

## Components
- Page header: Product name/ID + breadcrumb/back link
- Product summary strip (read-only): key identifiers (e.g., SKU), current lifecycle badge
- Section: “Product Lifecycle”
  - Read-only display fields: Current lifecycle state, Effective at (UTC), Last changed by, Last changed at, Last override reason (if present)
  - Edit form (permission-gated)
    - Lifecycle state dropdown (Active / Inactive / Discontinued)
    - Effective date input (date-only) and/or datetime input (if supported); helper text about UTC storage
    - Override reason text input/textarea (conditional; shown/required when Discontinued or when backend rules apply)
    - Actions: Save/Submit, Cancel/Reset
- Discontinued banner/alert (shown when lifecycleState = Discontinued)
- Section: “Replacement Suggestions”
  - Add replacement row/button (permission-gated)
  - Replacement product picker (search/select modal or inline autocomplete)
  - Priority order numeric input (default 1; suggest next available)
  - Notes textarea (optional)
  - Effective at input (optional; date-only or datetime if supported)
  - Replacements list/table (sorted by priority ascending)
    - Columns: Priority, Replacement product (name/ID), Notes, Effective at, (optional) Actions
- Toast/inline confirmation messages (success/error)
- Loading/empty states for lifecycle and replacements

## Layout
- Top: Header (Product title + lifecycle badge)
- Main column:
  - Product summary strip
  - Product Lifecycle section (display panel above; edit form below or in an “Edit” expandable area)
  - Discontinued banner directly under lifecycle state when applicable
  - Replacement Suggestions section (Add controls above list/table)
- Footer area within sections: Save/Cancel actions for lifecycle form; Add/Save actions for replacement creation

## Interaction Flow
1. View lifecycle state on Product Detail
   1. User opens Product Detail and scrolls to “Product Lifecycle”.
   2. Frontend loads product detail including lifecycle fields.
   3. UI displays lifecycleState and lifecycleStateEffectiveAt; if effectiveAt is null, show “Not set”.
   4. If provided, display lastStateChangedBy and lastStateChangedAt; display lifecycleOverrideReason if present.
2. Set lifecycle state to INACTIVE with a future effective date (date-only)
   1. User with manage permission opens lifecycle edit form.
   2. Selects lifecycle state = Inactive.
   3. Enters an effective date (date-only); UI indicates it will be stored/returned as UTC timestamp.
   4. Clicks Save/Submit.
   5. Frontend sends update request including selected state and entered effective date.
   6. On success, refresh/reload product detail; show updated lifecycleState and backend-returned UTC effective timestamp; show success confirmation.
   7. On error, keep form values, show inline error message.
3. Discontinue a product with override reason + irreversible state messaging
   1. User selects lifecycle state = Discontinued.
   2. Override reason field appears (and is required if backend rules indicate).
   3. User submits change.
   4. After refresh, UI shows lifecycleState = Discontinued and displays a prominent banner: “Discontinued — cannot be reactivated”.
   5. If backend returns override reason, display it in the lifecycle read-only fields.
4. Add a replacement product suggestion (for discontinued product)
   1. User opens “Replacement Suggestions” and clicks “Add replacement”.
   2. Opens product picker; searches and selects replacement product.
   3. Sets priority order (default 1; UI suggests next available if list already has items).
   4. Optionally enters notes and effectiveAt.
   5. Clicks Save/Add; frontend creates replacement record via backend.
   6. On success, replacement appears in list sorted by priority ascending; show success confirmation.
   7. Edge cases: empty list shows “No replacements yet”; duplicate priority or missing required fields shows validation errors.
5. Permission-gated behavior
   1. If user lacks manage permission, lifecycle edit controls and replacement add controls are hidden/disabled; read-only fields remain visible.

## Notes
- Lifecycle fields are read-only except via form submit; UI should not allow inline edits outside the controlled form.
- lifecycleStateEffectiveAt may be null for legacy products; must display “Not set” without errors.
- Effective date input should support date-only entry; backend stores/returns UTC timestamp—display returned value after refresh to avoid client-side assumptions.
- Discontinued state requires special UX: show banner and messaging that it cannot be reactivated (enforce visually even if backend enforces).
- Override reason: display last override reason if returned; treat as conditional/required when Discontinued (and align with backend validation).
- Replacement list must be sorted by priorityOrder ascending; UI should suggest next available priority to reduce conflicts.
- Product picker is required for replacementProductId; ensure it supports search/select and returns product identifier.
- Include clear loading, empty, and error states for both product lifecycle data and replacements list.
- Domain ownership conflict noted in requirements; ensure UI language and placement align with Product Master context (Product/Catalog lifecycle + replacements).
