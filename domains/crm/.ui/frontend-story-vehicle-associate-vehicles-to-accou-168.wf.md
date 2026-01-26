# [FRONTEND] [STORY] Vehicle: Associate Vehicles to Account and/or Individual
## Purpose
Enable users to associate one or more Vehicles to either an Account and/or an Individual within the CRM. This supports accurate ownership/relationship tracking so users can quickly find, link, and manage vehicles in the context of the correct customer record. The UI should make it clear what is being linked, to whom, and prevent duplicate or invalid associations.

## Components
- Page header with context (Account/Individual name) and breadcrumb/back link
- Context switch/tabs: Account view vs Individual view (or a combined “Associations” section)
- “Associated Vehicles” list/table
  - Columns: Vehicle identifier (e.g., VIN/Plate), Make/Model/Year, Status, Linked to (Account/Individual), Actions
- Primary CTA button: “Associate Vehicle”
- Search/select control for Vehicle lookup (typeahead/search results list)
- Optional “Create new Vehicle” link/button (if vehicle not found)
- Association form controls
  - Target selector: Account, Individual, or both (with checkboxes/toggles)
  - Relationship/role dropdown (if applicable)
  - Effective date (optional)
- Confirmation modal for association (summary of links to be created)
- Inline validation and error banners/toasts
- Row actions: View Vehicle, Remove association (with confirm modal)

## Layout
- Top: Breadcrumb/back + Page title (e.g., “Associations: Vehicles”) + primary CTA “Associate Vehicle”
- Main: Section 1 “Associated Vehicles” (table/list with filters/search within list)
- Main (below): Empty state panel when none associated (explain + CTA)
- Modal overlay: “Associate Vehicle” (search + selection + target + confirm)
- Modal overlay: “Remove association” confirmation

## Interaction Flow
1. User navigates to an Account or Individual record and opens the Vehicles association section.
2. System displays the “Associated Vehicles” list for the current context (Account or Individual), including any vehicles linked to both.
3. If no vehicles are associated, show an empty state with guidance and “Associate Vehicle” CTA.
4. User clicks “Associate Vehicle.”
5. Modal opens with Vehicle search/typeahead; user searches by VIN/plate/make-model and selects a vehicle from results.
6. User chooses association target(s): link to the current Account, the current Individual, or both (when allowed by context).
7. User reviews a summary (vehicle + target(s) + any relationship fields) and confirms.
8. System creates the association(s), closes modal, and refreshes the list; show success toast.
9. Edge case: Vehicle already associated to the selected target → block duplicate; show inline message and disable confirm.
10. Edge case: Search returns no results → show “No matches” state with optional “Create new Vehicle” action (if supported).
11. User selects “Remove association” on a vehicle row.
12. Confirmation modal shows what link will be removed (Account and/or Individual); user confirms; system updates list and shows success toast.
13. Error handling: API failure on associate/remove → keep modal open (if applicable), show error banner, allow retry/cancel.

## Notes
- Must support associating a vehicle to an Account, an Individual, or both; UI should clearly indicate current context and what will be linked.
- Prevent duplicate associations and provide clear validation messaging before submission.
- Ensure actions are auditable/confirmable: confirmation modals for create/remove, and clear success/error feedback.
- Table should handle multiple associations per vehicle (e.g., same vehicle linked to both Account and Individual) and display linkage clearly.
- TODO (design): Decide whether Account/Individual context is handled via tabs, a combined “Associations” view, or embedded sections on each record.
- TODO (dev): Define required vehicle identifiers for search (VIN, plate, internal ID) and whether “Create new Vehicle” is in scope for this screen.
