# [FRONTEND] [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes
## Purpose
Provide a Moqui UI screen to look up inventory availability using a Product SKU and Location, with an optional Storage Location filter. Support deep links via query parameters that can auto-run the lookup once per page load when valid. Ensure deterministic validation, error handling, and read-only rendering of backend-provided availability results (including success-with-zeros).

## Components
- Page header: “Availability” (Inventory → Availability)
- Availability lookup form
  - Product SKU text input (required; trimmed)
  - Location picker (required; no free-text UUID entry)
  - Storage Location picker (optional; filtered by selected Location)
  - “Include inactive” toggle for Location picker (if supported)
- Primary button: “Check availability” (disabled until valid; disabled while loading)
- Secondary buttons: “Clear”, “Copy link”
- Inline field validation messages (SKU required; Location required; Storage Location mismatch)
- Loading indicator (spinner shown quickly; within form/results area)
- Results section (read-only fields returned by backend)
- Empty-state success message (“No inventory history found for this selection”)
- Error panel (401/403/422/5xx) with “Retry” button
- “Technical details” accordion
  - Correlation ID (if present)
  - HTTP status / deterministic error code (if provided)

## Layout
- Top: Page header + brief helper text (“Check availability by SKU and location.”)
- Main (stacked):
  - Form card: SKU + Location + Storage Location + Include inactive toggle
  - Actions row: [Check availability] [Clear] [Copy link]
  - Below form:
    - Loading state area (spinner) OR
    - Results card (read-only) OR
    - Error panel + Technical details accordion
- Inline: Field-level errors directly beneath each input

## Interaction Flow
1. Screen load: initialize in Idle state (form visible; results hidden).
2. Parse query params into form state: productSku, locationId, storageLocationId (optional).
3. If locationId changes (user selection):
   1) Clear storageLocationId selection.
   2) Clear any existing results and errors.
4. Deep-link auto-run:
   1) If productSku and locationId are present and valid, auto-run lookup once per load.
   2) If storageLocationId is present but does not belong to selected locationId, show inline error and do not auto-run until corrected.
5. Manual lookup (primary flow):
   1) User enters Product SKU (trim whitespace) and selects Location via picker.
   2) Optionally selects Storage Location via picker filtered by Location.
   3) “Check availability” becomes enabled only when required inputs are valid.
   4) On click, call availability endpoint via Moqui proxy (no direct Vue → inventory backend).
   5) While request in-flight: show spinner within 100ms; disable submit; keep form visible.
   6) On success: render returned availability fields read-only; do not compute/modify quantities client-side.
6. Success-with-zeros:
   1) If backend returns success with zero quantities, still render results.
   2) Show message: “No inventory history found for this selection.”
7. Clear action:
   1) Resets form fields, results, and errors to Idle state.
8. Copy link action:
   1) Copies current URL with canonical query params only (productSku, locationId, storageLocationId if set).
9. Error handling (deterministic):
   1) For 401/403/422/5xx: show error panel with retry.
   2) Technical details accordion shows correlation ID (if available) and HTTP status/error code.
   3) Retry re-submits the same request using current form state.

## Notes
- Client-side validation:
  - Product SKU: required; trim; reject empty/whitespace-only with “Product SKU is required.”
  - Location: required; must be selected via picker (no free-text UUID entry in v1).
  - Storage Location: optional; if selected must belong to selected Location; enforce via filtered picker; deep-link mismatch blocks auto-run and shows inline error.
- Results visibility rules:
  - Hidden in Idle state.
  - Visible in Success state (including zeros).
  - Error state shows error panel + retry; correlation ID displayed when present.
- Location picker behavior:
  - Default to ACTIVE locations; provide “Include inactive” toggle for historical lookup if supported.
- Integration constraint: all calls go through Moqui proxy endpoint only.
- UX constraints: loading indicator appears quickly; submit disabled while loading; form remains visible during loading.
- Empty-state is not an error: “no inventory history” is treated as success-with-zeros.
- Technical details must avoid sensitive payload display; show only correlation ID and status/error code when available.
