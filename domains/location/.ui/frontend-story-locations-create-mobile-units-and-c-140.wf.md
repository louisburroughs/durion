# [FRONTEND] [STORY] Locations: Create Mobile Units and Coverage Rules
## Purpose
Enable admins to create, view, and update Mobile Units, including assigning capability IDs and selecting required related entities (base location, travel buffer policy). Provide a Coverage Rules sub-screen to link Mobile Units to Service Areas with priority and effective windows. Support creation and management of Travel Buffer Policies with domain-aligned validation and basic audit visibility.

## Components
- Global admin header with module title and user/session controls
- Left admin navigation: Admin → Location Management
- Breadcrumbs (context-aware)
- Mobile Units List screen
  - Search/filter input (optional)
  - Table/list: Name, Status, Base Location, Travel Buffer Policy, Max Daily Jobs, Updated At
  - Actions: View, Create
- Mobile Unit Create/Edit form
  - Read-only: MobileUnit ID (detail only), created/updated timestamps, version, optional audit events link/section
  - Fields: Name (required), Status (required enum), Base Location (required picker), Travel Buffer Policy (required when status ACTIVE), Max Daily Jobs (required non-negative int), Capability IDs (optional multi-select read-only lookup)
  - Buttons: Save, Cancel/Back to list
  - Inline validation messages + form-level error summary
- Mobile Unit Detail view
  - Summary panel of persisted fields
  - Tabs/sub-nav: Details, Coverage Rules
- Coverage Rules sub-screen (for a Mobile Unit)
  - List/table of rules: Service Area, Priority, Effective Start, Effective End, Updated At
  - Actions: Add Rule, Edit, Remove
  - Rule form modal/page: Service Area picker (read-only list/search), Priority (int), Effective window (start/end timestamps with timezone semantics)
  - Buttons: Save, Cancel
- Travel Buffer Policies List screen
  - Table/list: Name/ID, Type (FIXED_MINUTES/DISTANCE_TIER), Updated At
  - Actions: View, Create
- Travel Buffer Policy Create/Edit form
  - Fields: Type (required), parameters by type:
    - FIXED_MINUTES: Minutes (required, non-negative)
    - DISTANCE_TIER: Tier rows (distance thresholds + minutes; validated)
  - Buttons: Save, Cancel/Back to list
  - Read-only audit fields: created/updated timestamps, version

## Layout
- Top: Admin header + breadcrumbs
- Left: Admin navigation (Location Management section)
- Main (varies by route):
  - List pages: title + primary CTA (Create) above table
  - Detail pages: title + Back to list; summary panel; tabs (Details | Coverage Rules)
  - Forms: single-column form with grouped sections; Save/Cancel pinned at bottom
- Footer: optional system status/help links

## Interaction Flow
1. Navigate to Admin → Location Management → Mobile Units.
2. View Mobile Units list; select an item to open Mobile Unit detail, or click Create.
3. Create Mobile Unit (happy path):
   1) Open Create form.
   2) Enter Name; select Base Location (picker); set Status = ACTIVE; enter Max Daily Jobs (>= 0); select Travel Buffer Policy; optionally select Capability IDs.
   3) Click Save → call create endpoint → on success redirect to Mobile Unit detail.
   4) Detail shows persisted values including created/updated timestamps (and version if available).
4. Update Mobile Unit:
   1) From detail, click Edit (or inline edit).
   2) Update allowed fields; ensure required fields satisfied (including Travel Buffer Policy when ACTIVE).
   3) Click Save → call update endpoint with atomic replace semantics → return to detail.
5. Manage Coverage Rules:
   1) From Mobile Unit detail, open Coverage Rules tab.
   2) Click Add Rule → choose Service Area via picker; set Priority; set effective start/end (timezone-aware).
   3) Save → refresh rules list.
   4) Edit/Remove rule from list; validate effective window and required fields.
6. Travel Buffer Policies:
   1) Navigate to Admin → Location Management → Travel Buffer Policies.
   2) Create policy: choose Type; enter required parameters (minutes or tiers); Save → redirect to policy detail.
   3) Update policy similarly; enforce tier validation for DISTANCE_TIER.
7. Key validation/edge cases:
   1) Block Save with clear errors when required fields missing or invalid (e.g., negative max daily jobs, missing travel buffer policy when ACTIVE).
   2) Coverage rule effective end before start → error.
   3) DISTANCE_TIER tiers invalid (missing rows, non-monotonic thresholds, invalid minutes) → error.
   4) Backend validation failures (capability IDs, service areas, domain rules) → show form-level error summary and field highlights.

## Notes
- Entry points and breadcrumbs must match:
  - Admin > Location Management > Mobile Units
  - Admin > Location Management > Mobile Units > {Mobile Unit Name}
  - Admin > Location Management > Travel Buffer Policies
- After create/update, redirect to the relevant detail screen and provide “Back to list”.
- Capability IDs are validated by Service Catalog via backend; UI uses read-only lookup (no free-text creation).
- Service Areas are picker-only (no CRUD here); UI depends on a read-only list/search endpoint (backend may proxy).
- Enforce Location domain rules in UI where possible:
  - Atomic replace semantics on update (warn about overwriting arrays/coverage rules if applicable).
  - Timezone semantics for timestamps/effective windows.
  - Required fields and non-negative integer constraints.
- Moqui screen/form/service wiring required to call backend endpoints and manage transitions between list/create/detail/tabs.
- Basic audit visibility required: created/updated timestamps, version; optionally show audit events section when available.
- Requirements are incomplete-risk: keep UI flexible for final field names/enum values and endpoint shapes (wire to decisions for list/service area pickers).
