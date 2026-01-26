# [FRONTEND] [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy

## Purpose
Enable Inventory Managers to manage storage locations within a selected site by listing, creating, editing, and deactivating locations organized in a parent/child hierarchy. Ensure accurate inventory referencing across workflows by enforcing backend-aligned validation (unique barcode per site, cycle prevention, immutable site after creation). Provide deterministic error handling, including destination-required deactivation when a location is non-empty.

## Components
- Page header: “Storage Locations” + breadcrumb (Inventory → Topology → Storage Locations)
- Site context selector (required): site picker + “Select a site” empty state
- Filters toolbar:
  - Status filter (Active / Inactive / All)
  - Parent filter (optional; within site)
  - Search input (name or barcode; server-side)
- Results list/table:
  - Columns: Name, Barcode, Type, Parent, Status, Updated
  - Row actions: View/Open detail
  - Pagination controls (cursor-based: Next/Previous)
- Primary actions: “Create Storage Location” button
- Detail / Edit screen (or drawer) with:
  - Read-only: Site (after creation), Status
  - Editable fields: Name, Barcode, Type (enum from meta endpoint), Parent (picker)
  - JSON fields (opaque): Attributes/Config/Metadata (JSON editor/viewer with syntax validation only; safe render with truncate + expand/copy)
  - Buttons: Save, Cancel, Deactivate (if Active)
- Parent picker modal/panel:
  - Search within site
  - Toggle: “Include inactive parents” (default OFF)
- Deactivate confirmation dialog:
  - Confirm text + warning
  - Optional destination picker shown when required by backend
  - Inline error area for destination-required/invalid destination
- Error presentation:
  - Field-level validation messages
  - Page-level alert with “Technical details” expandable area (correlation ID when available)

## Layout
- Top: Breadcrumb + page title; right-aligned primary CTA “Create Storage Location”
- Main (List): Site picker + filters row above results table; pagination at bottom
- Main (Detail/Edit): Two-column form (left: core fields; right: JSON fields + help); actions pinned bottom-right
- Modals: Parent picker; Deactivate confirm (with destination picker when needed)

## Interaction Flow
1. Navigate to Inventory → Topology → Storage Locations.
2. If no site selected (and no site in query param), show empty state “Select a site” with site picker; disable list actions until selected.
3. Select a site:
   1) Load storage locations for that site.
   2) Load storage type enum values from meta endpoint for create/edit forms.
4. Use filters:
   1) Change Status (Active/Inactive/All) to refresh results.
   2) Enter search (name/barcode) to perform server-side query.
   3) Optionally filter by Parent (within same site).
   4) Paginate using cursor controls.
5. Open a storage location detail:
   1) Display fields including hierarchy (parent reference).
   2) Show Deactivate button only if status is Active.
6. Create a storage location:
   1) Click “Create Storage Location”.
   2) Fill required fields: Site (required), Name, Barcode, Type; optional Parent.
   3) Parent selection:
      - Only locations in same site.
      - Exclude inactive by default; allow “Include inactive parents” toggle.
   4) JSON fields: accept any valid JSON; show syntax errors only.
   5) Submit:
      - If barcode duplicate within site, show field error on Barcode: “Barcode must be unique within the site.”
      - If parent invalid/cycle, show field error on Parent with backend message.
      - On success, navigate to detail and reload record.
7. Update a storage location:
   1) From detail, enter edit mode (or edit inline).
   2) Site is read-only/immutable; Status is read-only.
   3) Change allowed fields (Name/Barcode/Type/Parent/JSON fields).
   4) Save:
      - UI must not send site changes; handle backend rejection deterministically if attempted.
      - If cycle/invalid parent, show Parent field error; do not update displayed hierarchy until successful save + reload.
      - On success (updated record or 204), reload detail.
8. Deactivate a storage location:
   1) Click Deactivate → open confirm dialog.
   2) Submit deactivate without destination initially.
   3) If backend returns destination-required (non-empty), show inline message: “Destination is required to deactivate a non-empty storage location.” and reveal destination picker.
   4) Destination picker rules:
      - Only Active locations in same site.
      - Must exclude the source location.
   5) Resubmit with destination:
      - If invalid destination, show inline message: “Destination must be Active and in the same site.”
      - On success, return to detail/list and reflect Inactive status; Deactivate action hidden/disabled.

## Notes
- Access and actions are permission-gated per inventory permission conventions; hide/disable controls when not permitted.
- Backend is authoritative for hierarchy cycle prevention; UI should prevent obvious self/descendant selection where feasible but must surface backend errors on Parent.
- Storage types are backend-owned enums; UI must load from meta endpoint and not hardcode values.
- Site is immutable after creation; ensure UI does not submit site changes on update.
- Parent and destination pickers must default to excluding inactive locations; parent picker may optionally allow including inactive via explicit toggle (default OFF).
- JSON fields are opaque: validate JSON syntax only; render safely (escape), truncate long content, and provide expand/copy affordances.
- Error handling must map field-level errors when possible; all error states should display correlation ID in “Technical details” when available.
- No reactivation flow in v1; no graphical topology map/tree beyond basic hierarchical navigation/filtering.
