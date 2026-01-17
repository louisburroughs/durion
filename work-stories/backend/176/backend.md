Title: [BACKEND] [STORY] Counts: Plan Cycle Counts by Location/Zone
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/176
Labels: type:story, layer:functional, kiro, domain:inventory, status:ready-for-dev

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:missing-requirements

**Rewrite Variant:** inventory-flexible

## Story Intent
**As an** Inventory Manager,
**I want to** create a scheduled Cycle Count Plan for specific locations and zones within a warehouse,
**so that** I can proactively manage and execute targeted inventory counts to improve stock accuracy and operational efficiency.

## Actors & Stakeholders
- **Inventory Manager (Primary Actor):** Responsible for creating, scheduling, and overseeing cycle count plans.
- **System:** The POS/Inventory Management platform responsible for creating and storing the plan, and generating the associated count tasks.
- **Warehouse Staff (Downstream Consumer):** Executes the counts based on the generated plan.
- **Auditors (Stakeholder):** Rely on the history of count plans and results to verify inventory accuracy and process compliance.

## Preconditions
1. The user is authenticated and has `INVENTORY_PLAN_CREATE` permissions.
2. At least one `Location` with defined `Zones` exists in the system.
3. Inventory stock records exist for items within the target zones.

## Functional Behavior
1.  **Trigger:** The Inventory Manager navigates to the "Cycle Counting" section of the application and initiates the creation of a new "Cycle Count Plan".
2.  **Plan Creation:**
    - The user is prompted to select a `Location` from a list of available locations.
    - Upon selecting a `Location`, the user is presented with a list of associated `Zones`. The user can select one or more zones for the count.
    - The user must specify a `scheduledDate` for the plan to be executed.
    - The user provides an optional `planName` or `description` for easy identification.
3.  **Plan Generation:**
    - Upon submitting the details, the System validates the inputs.
    - If valid, the System creates a new `CycleCountPlan` record with a unique identifier and a status of `PLANNED`.
    - The plan is linked to the selected `Location` and `Zone(s)`.
    - The System generates a list of `StockKeepingUnits` (SKUs) within the selected zones that are to be included in the count.
4.  **Confirmation:** The user receives a confirmation that the Cycle Count Plan has been successfully created and can view it in the list of planned counts.

## Alternate / Error Flows
- **Error - Invalid Location/Zone:** If the user attempts to create a plan for a `Location` or `Zone` that does not exist (e.g., via a stale API call), the system will return a `404 Not Found` error with a clear message: "The specified location or zone could not be found."
- **Error - Date in the Past:** If the user selects a `scheduledDate` that is in the past, the system will return a `400 Bad Request` error with the message: "Scheduled date must be in the future."
- **Edge - Zone with No Inventory:** If a plan is created for a zone that contains no inventory items, the plan is still created successfully, but the associated count list will be empty. The plan status might reflect this (e.g., `PLANNED_NO_ITEMS`).

## Business Rules
- A `CycleCountPlan` must be associated with exactly one `Location`.
- A `CycleCountPlan` must be associated with at least one `Zone`.
- The `scheduledDate` cannot be in the past.
- Once a `CycleCountPlan` status moves from `PLANNED` to `IN_PROGRESS`, its scope (location, zones) cannot be modified.

## Data Requirements
A new data entity, `CycleCountPlan`, is required with the following conceptual schema:

| Field | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `planId` | UUID | Unique identifier for the plan. | `f47ac10b-58cc-4372-a567-0e02b2c3d479` |
| `locationId` | UUID | Foreign key to the `Location` entity. | `e2a3b10c-12ab-43cd-87ef-1a23b4c5d680` |
| `zoneIds` | Array<UUID> | List of foreign keys to `Zone` entities. | `['z1...', 'z2...']` |
| `planName` | String | User-defined name for the plan. | "Q4 High-Value Goods Count - Zone A" |
| `status` | Enum | The current state of the plan. | `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `scheduledDate` | Date | The date the count is scheduled for. | `2025-11-15` |
| `createdBy` | UserID | The ID of the user who created the plan. | `user-123` |
| `createdAt` | Timestamp | Timestamp of plan creation. | `2025-10-20T10:00:00Z` |
| `updatedAt` | Timestamp | Timestamp of last plan modification. | `2025-10-20T10:00:00Z` |

## Acceptance Criteria
**Scenario 1: Successfully Create a Cycle Count Plan**
- **Given** I am an Inventory Manager with `INVENTORY_PLAN_CREATE` permissions,
- **And** a Location "Warehouse A" with Zones "Zone A1" and "Zone A2" exists,
- **When** I submit a request to create a new Cycle Count Plan for "Warehouse A", targeting "Zone A1", with a `scheduledDate` in the future,
- **Then** the system returns a `201 Created` status,
- **And** a new `CycleCountPlan` record is created in the database with a status of `PLANNED` and associated with "Warehouse A" and "Zone A1".

**Scenario 2: Attempt to Create a Plan with a Past Date**
- **Given** I am an Inventory Manager,
- **When** I submit a request to create a new Cycle Count Plan with a `scheduledDate` that is in the past,
- **Then** the system returns a `400 Bad Request` error,
- **And** the response body contains a clear error message indicating the date must be in the future.

**Scenario 3: Attempt to Create a Plan for a Non-Existent Location**
- **Given** I am an Inventory Manager,
- **When** I submit a request to create a new Cycle Count Plan with a `locationId` that does not exist in the system,
- **Then** the system returns a `404 Not Found` error,
- **And** no new `CycleCountPlan` record is created.

## Audit & Observability
- **Audit Log:** An audit event MUST be generated upon the creation, modification, or deletion of any `CycleCountPlan`. The event must include the `planId`, the user performing the action, and the changes made.
- **Logging:** Structured logs should be emitted for the start and end of the plan creation process, including `planId`, `locationId`, and `zoneIds`. Any validation failures or processing errors must be logged at the `ERROR` level.
- **Metrics:**
  - `cycle_count_plans.created.count`: A counter metric incremented each time a plan is successfully created, tagged by `location`.
  - `cycle_count_plans.creation.errors.count`: A counter metric for failed plan creation attempts, tagged by `error_type` (e.g., `validation_error`, `db_error`).

## Open Questions
1.  **Scope of Items:** How are the specific items within a zone selected for counting? Is it all items in the zone, a random sample, items based on ABC analysis (velocity), or something else? This is the most critical missing piece of logic.
2.  **Plan Statuses:** Are the proposed statuses (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) sufficient for the full lifecycle, or are others like `PENDING_APPROVAL` or `RECOUNT_REQUIRED` needed?
3.  **Recurrence:** Is there a requirement to create recurring cycle count plans (e.g., "Count Zone A every Monday")?
4.  **Permissions:** Is a single `INVENTORY_PLAN_CREATE` permission sufficient, or do we need separate permissions for viewing, updating, and cancelling plans?
5.  **Empty Zones:** How should the UI/API respond when a plan is successfully created for a zone that has no items to count? Should this be a warning?

## Original Story (Unmodified – For Traceability)
# Issue #176 — [BACKEND] [STORY] Counts: Plan Cycle Counts by Location/Zone

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #238 - Counts: Plan Cycle Counts by Location/Zone
**URL**: https://github.com/louisburroughs/durion/issues/238
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for backend development
- Coordinate with corresponding frontend implementation if needed

### Technical Requirements
**Backend Implementation Requirements:**
- Use Spring Boot with Java 21
- Implement RESTful API endpoints following established patterns
- Include proper request/response validation
- Implement business logic with appropriate error handling
- Ensure database operations are transactional where needed
- Include comprehensive logging for debugging
- Follow security best practices for authentication/authorization


### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Backend agents: Focus on Spring Boot microservices, Java 21, REST APIs, PostgreSQL. Ensure API contracts align with frontend requirements.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `backend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:37:55.200248514*