Title: [BACKEND] [STORY] Vehicle: Associate Vehicles to Account and/or Individual
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/104
Labels: backend, story-implementation, type:story, domain:crm, status:ready-for-dev, agent:story-authoring, agent:crm

## Story Intent
As a Fleet Account Manager, I need to establish and maintain official relationships between a vehicle, its owning commercial account, and its optional primary driver, so downstream systems (Workorder Execution, Billing) can default to the correct billing entity and contact.

## Actors & Stakeholders
- **Primary Actor:** Fleet Account Manager
- **Service Owner / SoR:** CRM domain/service (vehicle↔party associations lifecycle)
- **Downstream Consumers:** Workorder Execution, Billing/Invoice workflows (read-only)

## Preconditions
- Vehicle exists.
- Commercial account (Organization party) exists.
- Optional driver (Person party) exists.
- Actor is authorized to manage vehicle associations for the commercial account.

## Functional Behavior

### 1) Establish initial ownership (`OWNER`)
- Create a `VehiclePartyAssociation` with `associationType=OWNER`, `effectiveStartDate=S`, `effectiveEndDate=NULL`.

### 2) Assign optional primary driver (`PRIMARY_DRIVER`)
- Create a `VehiclePartyAssociation` with `associationType=PRIMARY_DRIVER`, `effectiveStartDate=S`, `effectiveEndDate=NULL`.
- Requires an active `OWNER` association.

### 3) Owner reassignment behavior (Decision)
- **Implicit reassignment on create:** creating a new active `OWNER` association automatically end-dates the existing active `OWNER` and creates the new one **atomically**.
- **Idempotency:** if the existing active owner is the same party, treat as a no-op (do not create duplicates).
- **Date/time semantics:** use **[start, end)** (exclusive end) when using timestamps; set old owner `endAt = newOwner.startAt`.

### 4) Driver handling when owner changes (Decision)
- Do **not** automatically end-date or modify `PRIMARY_DRIVER` when `OWNER` changes; it persists until explicitly changed.

## Alternate / Error Flows
- Unknown vehicle/account/driver → `404`.
- Assign driver without active owner → validation error (`400`).
- Invalid association type → `400`.
- Conflicting historical overlap that cannot be reconciled automatically (e.g., start date intersects past segments) → `409`.

## Business Rules
- **Domain authority (Decision):** CRM is the system of record for vehicle↔party associations; payment/billing are consumers.
- Exactly one active `OWNER` per vehicle at a time.
- Zero or one active `PRIMARY_DRIVER` per vehicle at a time.
- All changes are non-destructive: end-date old record(s), create new record(s); no hard deletes.

## Data Requirements
- `VehiclePartyAssociation`:
  - `associationId` (UUID)
  - `vehicleId` (FK)
  - `partyId` (FK)
  - `associationType` (`OWNER`, `PRIMARY_DRIVER`)
  - `effectiveStartDate`, `effectiveEndDate`
  - audit fields

## Acceptance Criteria
- Can create initial `OWNER` association for a vehicle.
- Creating a new `OWNER` when one is active performs implicit reassignment atomically (end-date old + create new) with no overlap.
- Assigning `PRIMARY_DRIVER` creates an active association and requires an active owner.
- Owner reassignment does not modify `PRIMARY_DRIVER` unless explicitly requested.
- Consumers can query current associations for a vehicle (owner + optional driver).

## Audit & Observability
- Audit log all create/end-date operations with actor and timestamps.
- Publish domain events:
  - `VehicleOwnerAssociated` / `VehicleOwnerReassigned`
  - `VehiclePrimaryDriverAssigned` / `VehiclePrimaryDriverUnassigned`

## Open Questions
- None. Decisions were supplied in the issue comments (Decision Document dated 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #104 — [BACKEND] [STORY] Vehicle: Associate Vehicles to Account and/or Individual

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Vehicle: Associate Vehicles to Account and/or Individual

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to link a vehicle to a commercial account and optionally a primary driver** so that **workorders default to the right billing entity and contacts**.

## Details
- Vehicle associated to an account (owner) and optionally an individual (driver).
- Support reassignment with history.

## Acceptance Criteria
- Create/update associations.
- History preserved on reassignment.

## Integration Points (Workorder Execution)
- Workorder Execution fetches owner/driver for selected vehicle.

## Data / Entities
- VehiclePartyAssociation (owner/driver, dates)

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