Title: [BACKEND] [STORY] Vehicle: Store Vehicle Care Preferences
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/102
Labels: type:story, domain:crm, status:ready-for-dev

STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent
As a Service Advisor, I need the ability to record, update, and view specific care preferences and general service notes for a customer's vehicle. This ensures that all service interactions, from estimates to final work execution, are consistently aligned with the customer's explicit expectations and the vehicle's history, thereby improving service quality and customer satisfaction.

## Actors & Stakeholders
- **Service Advisor (Primary Actor):** The user responsible for capturing and maintaining customer vehicle preferences.
- **Mechanic / Technician (Secondary Actor):** Consumes the preference information during service execution to ensure compliance with customer wishes.
- **System:** The POS system responsible for persisting, retrieving, and auditing preference data.
- **Customer:** The beneficiary of the personalized service.

## Preconditions
1.  The Service Advisor is authenticated and has the necessary permissions to view and modify vehicle records.
2.  A unique vehicle record, associated with a customer, exists in the system.

## Functional Behavior
1.  A Service Advisor navigates to a specific vehicle's profile page within the POS system.
2.  The user accesses a dedicated section for "Vehicle Care Preferences & Notes".
3.  The user can input or modify data into both structured preference fields (e.g., preferred tire brand) and a free-form text area for general notes.
4.  Upon saving, the system validates the input and persists the preferences and notes, associating them directly with the vehicle's unique identifier (`VehicleID`).
5.  When this vehicle is added to an Estimate or Work Order, the system retrieves and displays the saved preferences and notes in a designated, read-only section on the document's view.

## Alternate / Error Flows
- **Invalid Data Entry:** If the user enters data that fails validation (e.g., "abc" for a numeric `rotation_interval`), the system will display a user-friendly error message indicating the specific field in error and will prevent the data from being saved until corrected.
- **Vehicle Not Found:** If the Service Advisor attempts to access a non-existent vehicle record, the system will display a "Vehicle not found" error.
- **Save Operation Fails:** If the system encounters a database or service error during the save operation, it will inform the user that the preferences could not be saved and log the critical error for technical review.

## Business Rules
- Vehicle preferences are stored on a per-vehicle basis. A customer with multiple vehicles can have different preferences for each.
- The system must support a combination of structured data fields and at least one unstructured, free-form notes field.
- Once saved, these preferences are considered part of the vehicle's service record and should be readily accessible.
- Any modification to existing preferences must overwrite the previous values, but the change itself must be recorded in an audit log.

## Data Requirements
The implementation requires a new data entity, likely `VehicleCarePreference`, with the following attributes:
- `id`: Unique identifier for the preference record.
- `vehicle_id`: Foreign key, linking to the unique identifier of the vehicle record.
- **Structured Fields:**
    - `preferred_tire_brand`: `String`
    - `preferred_tire_line`: `String`
    - `rotation_interval`: `Integer`
    - `rotation_interval_unit`: `Enum` (e.g., MILES, KM)
    - `alignment_preference`: `String` (e.g., "OEM Spec", "Custom Spec")
    - `torque_spec_notes`: `String`
- **Unstructured Field:**
    - `service_notes`: `Text` (for free-form notes)
- **Standard Audit Fields:**
    - `created_at`: `Timestamp`
    - `updated_at`: `Timestamp`
    - `created_by_user_id`: `UUID`
    - `updated_by_user_id`: `UUID`

## Acceptance Criteria

### AC-1: Create Vehicle Care Preferences
- **Given** a Service Advisor is viewing a vehicle record that has no existing care preferences
- **When** they enter valid data into the preference fields and general notes, and click "Save"
- **Then** the system successfully persists the new preference record associated with the correct `vehicle_id`.

### AC-2: Update Vehicle Care Preferences
- **Given** a vehicle record has existing care preferences
- **When** a Service Advisor modifies one or more preference fields or notes and clicks "Save"
- **Then** the system updates the existing record with the new information and updates the `updated_at` and `updated_by_user_id` fields.

### AC-3: View Preferences on Work Documents
- **Given** a vehicle has saved care preferences
- **When** a user (Service Advisor or Technician) views an Estimate or Work Order that includes this vehicle
- **Then** the system displays the vehicle's saved care preferences and notes in a clear, designated section of the document.

### AC-4: Audit Trail for Changes
- **Given** a Service Advisor has successfully created or updated a vehicle's care preferences
- **When** an authorized user or system process inspects the audit log for that vehicle
- **Then** an audit entry is found detailing the change, including the user who made it, a timestamp, and the before/after values (or a snapshot of the new state).

### AC-5: Input Validation
- **Given** a Service Advisor is creating or editing vehicle preferences
- **When** they enter non-numeric characters into the `rotation_interval` field and attempt to save
- **Then** the system prevents the save operation and displays a validation error message specific to that field.

## Audit & Observability
- **Events to be Logged:**
    - `VehiclePreferenceCreated`: Triggered on the first save for a vehicle.
    - `VehiclePreferenceUpdated`: Triggered on any subsequent save.
- **Log Payload:** Each event log must include `vehicle_id`, the `user_id` of the acting Service Advisor, a `timestamp`, and the full data payload of the preference record.
- **Metrics:** Track the count of `VehiclePreferenceCreated` and `VehiclePreferenceUpdated` events to monitor feature usage.

## Open Questions
1.  **Schema Flexibility:** The story lists specific structured fields (tire brand, rotation interval, etc.) but also mentions "Structured key/values". Is the list of preferences fixed and defined by the schema, or do we need to implement a more flexible entity-attribute-value (EAV) or JSONB model to allow for adding new types of preferences in the future without schema changes? A fixed schema is simpler to implement initially.

## Original Story (Unmodified – For Traceability)
# Issue #102 — [BACKEND] [STORY] Vehicle: Store Vehicle Care Preferences

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Vehicle: Store Vehicle Care Preferences

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to record vehicle-specific care preferences and service notes** so that **the shop can deliver service aligned with customer expectations**.

## Details
- Preferences: preferred tire brand/line, rotation interval, alignment preference, torque spec notes.
- Structured key/values + free-form notes.

## Acceptance Criteria
- Add/update preferences.
- Preferences visible on estimate/workorder.
- Changes audited.

## Integration Points (Workorder Execution)
- Workorder Execution displays preferences at estimate and during execution.

## Data / Entities
- VehiclePreference
- VehicleNote

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*