# [FRONTEND] [STORY] Locations: Create Bays with Constraints and Capacity
## Purpose
Enable a Shop Administrator to create a service bay under an existing location, capturing bay type, status, capacity, and constraint references (services and skills). The goal is to ensure scheduler/dispatch can select an appropriate ACTIVE bay that meets service/skill requirements. The UI should support a happy-path create flow and persist/display constraints and capacity on the Bay Detail (or Bay List) after creation.

## Components
- Page header with breadcrumb (Locations > Location Detail > Bays > Create Bay)
- Location context summary (Location name + ID)
- Create Bay form
  - Name (text input, must be unique)
  - Bay Type (select/dropdown)
  - Status (select; default ACTIVE)
  - Capacity: Max Concurrent Vehicles (number input; default 1)
  - Constraints: Supported Services (multi-select with async lookup)
  - Constraints: Required Skills (multi-select with async lookup)
- Inline validation messages (required/unique/number constraints)
- Primary action button: Create Bay (submit)
- Secondary action button: Cancel (navigate back)
- Loading states for services/skills lookups
- Error banner/toast for create failure or lookup failure

## Layout
- Top: Breadcrumb + page title “Create Bay”
- Main (single column form):
  - Location context card
  - Form sections: Basics (Name, Type, Status) → Capacity → Constraints (Services, Skills)
  - Bottom row: [Cancel] (left) and [Create Bay] (right)

## Interaction Flow
1. Admin navigates to a specific Location and opens “Create Bay” for that Location.
2. System loads Create Bay screen with Location context visible; Status defaults to ACTIVE; Max Concurrent Vehicles defaults to 1.
3. System fetches Services lookup options and Skills lookup options; show loading indicators until available.
4. Admin enters a unique Bay Name, selects a Bay Type, optionally adjusts Status, and sets Max Concurrent Vehicles (e.g., 1).
5. (Optional) Admin selects Supported Services (multi-select) and Required Skills (multi-select) from lookup results.
6. Admin clicks Create Bay.
7. Client validates inputs (required fields, numeric capacity, name uniqueness where possible); if invalid, show inline errors and prevent submit.
8. On valid submit, system sends create request including: location reference, name, bay type, status, capacity (maxConcurrentVehicles), and selected constraint IDs (services + skills).
9. On success, navigate to the created Bay Detail screen (or Bay List) and show the new bay present.
10. Bay Detail screen displays persisted fields including Status, Capacity, Supported Services, and Required Skills (queryable via bay detail endpoint).
11. If create fails (network/server/validation), show error banner/toast and keep form values for correction/resubmission.
12. If services/skills lookup fails, show an inline error state for that section and allow creation without constraints (unless backend requires them).

## Notes
- Acceptance criteria (happy path): Admin can create a bay under a location with unique name, selected bay type, status ACTIVE, and capacity maxConcurrentVehicles=1; after submit, user is navigated to Bay Detail (or Bay List) showing the new bay.
- Acceptance criteria (constraints): Selected Supported Services and Required Skills are included in the create request and are displayed on Bay Detail after fetching via the bay detail endpoint.
- Constraints are references by ID (services + skills); UI should store/display human-readable labels while submitting IDs.
- Status should default to ACTIVE to support scheduler/dispatch selection of appropriate ACTIVE bays.
- Capacity input should be numeric and non-negative; default to 1 as indicated.
- Risk/requirements incomplete: exact endpoint paths, required fields, and uniqueness validation mechanism are not specified; implement UI to handle backend validation errors gracefully and surface field-level messages when returned.
- Navigation target after create is flexible (Bay Detail preferred; Bay List acceptable); ensure one is implemented consistently with product routing.
