# CAP:091 Backend Implementation Guide

**Capability:** CAP:091 - Vehicle Registry (VINs, Descriptions, Ownership/Association)  
**Domain:** crm  
**Backend Repository:** louisburroughs/durion-positivity-backend  
**Feature Branch:** cap/CAP091  
**Status:** In Progress

---

## Overview

This document describes the backend implementation for CAP:091, which introduces a comprehensive vehicle registry system with VIN management, vehicle-party associations, care preferences, search capabilities, and event-driven updates from workorder execution.

## Stories Overview

| Story Issue | Title | Backend Issue |
|-------------|-------|---------------|
| [durion#102](https://github.com/louisburroughs/durion/issues/102) | Vehicle: Create Vehicle Record with VIN and Description | [#105](https://github.com/louisburroughs/durion-positivity-backend/issues/105) |
| [durion#103](https://github.com/louisburroughs/durion/issues/103) | Vehicle: Associate Vehicles to Account and/or Individual | [#104](https://github.com/louisburroughs/durion-positivity-backend/issues/104) |
| [durion#104](https://github.com/louisburroughs/durion/issues/104) | Vehicle: Vehicle Lookup by VIN/Unit/Plate | [#103](https://github.com/louisburroughs/durion-positivity-backend/issues/103) |
| [durion#105](https://github.com/louisburroughs/durion/issues/105) | Vehicle: Store Vehicle Care Preferences | [#102](https://github.com/louisburroughs/durion-positivity-backend/issues/102) |
| [durion#106](https://github.com/louisburroughs/durion/issues/106) | Vehicle: Ingest Vehicle Updates from Workorder Execution | [#101](https://github.com/louisburroughs/durion-positivity-backend/issues/101) |

---

## Architecture Decisions

### Module Selection

The implementation will extend **`pos-vehicle-inventory`** module rather than `pos-customer` because:

1. Vehicle registry is a distinct bounded context focused on vehicle lifecycle, not customer relationship management
2. Separation allows independent scaling and deployment
3. Aligns with domain-driven design principles
4. `pos-customer` will consume vehicle data via REST API calls or events

### Key Design Patterns

1. **VIN Normalization**: Store both raw VIN (`vin`) and normalized VIN (`vinNormalized`) for uniqueness checks
2. **Temporal Associations**: Use effective dates for vehicle-party associations with [start, end) semantics
3. **Event Sourcing**: Publish domain events for all state changes for downstream consumption
4. **Idempotency**: Use event IDs for duplicate detection in event ingestion
5. **Flexible Preferences**: JSONB storage for vehicle care preferences allows schema evolution

---

## Implementation Checklist

### Story #105: Vehicle CRUD with VIN and Description

**Files to Create/Modify:**

- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/entity/Vehicle.java` (update)
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/CreateVehicleRequest.java` (create)
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/VehicleResponse.java` (create)
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/service/VehicleService.java` (create)
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/controller/VehicleController.java` (update)
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/repository/VehicleRepository.java` (update)

**Key Features:**

- VIN validation (17 characters, no I/O/Q)
- VIN normalization (uppercase, trim, remove separators)
- Global VIN uniqueness constraint
- Required fields: `vin`, `unitNumber`, `description`
- Optional field: `licensePlate`
- Generate immutable `vehicleId` (UUID)
- Emit `VehicleCreated` event

**Entity Structure:**

```java
@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID vehicleId;
    
    @Column(nullable = false)
    private UUID accountId;
    
    @Column(nullable = false, length = 17)
    private String vin;
    
    @Column(nullable = false, length = 17, unique = true)
    private String vinNormalized;
    
    @Column(nullable = false)
    private String unitNumber;
    
    @Column(nullable = false)
    private String description;
    
    @Column
    private String licensePlate;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    private Long version;
}
```

### Story #104: Vehicle-Party Associations

**Files to Create:**

- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/entity/VehiclePartyAssociation.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/CreateAssociationRequest.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/AssociationResponse.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/service/VehicleAssociationService.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/controller/VehicleAssociationController.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/repository/VehiclePartyAssociationRepository.java`

**Key Features:**

- Association types: `OWNER`, `PRIMARY_DRIVER`
- Temporal validity with `effectiveStartDate`, `effectiveEndDate`
- Implicit owner reassignment (atomic end-date old + create new)
- Idempotent operations (no duplicate active associations)
- Primary driver persists across owner changes
- Emit `VehicleOwnerAssociated`, `VehicleOwnerReassigned`, `VehiclePrimaryDriverAssigned` events

**Entity Structure:**

```java
@Entity
@Table(name = "vehicle_party_associations")
public class VehiclePartyAssociation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID associationId;
    
    @Column(nullable = false)
    private UUID vehicleId;
    
    @Column(nullable = false)
    private UUID partyId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssociationType associationType;
    
    @Column(nullable = false)
    private Instant effectiveStartDate;
    
    @Column
    private Instant effectiveEndDate;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    private Long version;
}

public enum AssociationType {
    OWNER,
    PRIMARY_DRIVER
}
```

### Story #103: Vehicle Lookup/Search

**Files to Create:**

- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/SearchVehiclesRequest.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/VehicleSummary.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/SearchVehiclesResponse.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/VehicleSnapshot.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/service/VehicleSearchService.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/controller/VehicleSearchController.java`

**Key Features:**

- Search by VIN, unit number, license plate, owner name
- VIN query normalization
- Minimum query lengths: VIN (6), plate (3), general (3)
- Ranking tiers: exact match → prefix match → contains match (optional)
- Result limits: default 25, hard cap 50
- Cursor-based pagination support
- Return vehicle summary and full vehicle+owner snapshot on selection
- Emit `vehicle.searched` and `vehicle.selected_for_service` events

**Search Request:**

```java
public class SearchVehiclesRequest {
    private String query;
    private Integer limit; // default 25, max 50
    private String cursor; // for pagination
    private Boolean enableContainsMatching; // default false
}
```

**Vehicle Summary:**

```java
public class VehicleSummary {
    private UUID vehicleId;
    private String vin;
    private String unitNumber;
    private String licensePlate;
    private String description;
    private OwnerSummary owner;
}
```

### Story #102: Vehicle Care Preferences

**Files to Create:**

- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/entity/VehicleCarePreference.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/UpsertPreferencesRequest.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/PreferencesResponse.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/service/VehiclePreferencesService.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/controller/VehiclePreferencesController.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/repository/VehicleCarePreferenceRepository.java`

**Key Features:**

- Flexible JSONB storage for preferences
- Common keys: `preferred_tire_brand`, `preferred_tire_line`, `rotation_interval`, `rotation_interval_unit`, `alignment_preference`, `torque_spec_notes`
- Free-form service notes field
- Optimistic concurrency control via version field
- Emit `VehiclePreferenceCreated`, `VehiclePreferenceUpdated` events

**Entity Structure:**

```java
@Entity
@Table(name = "vehicle_care_preferences")
public class VehicleCarePreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID vehicleId;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> preferences;
    
    @Column(columnDefinition = "text")
    private String serviceNotes;
    
    @Column(nullable = false)
    private UUID createdByUserId;
    
    @Column(nullable = false)
    private UUID updatedByUserId;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    @Version
    private Long version;
}
```

### Story #101: Event Ingestion from Workorder Execution

**Files to Create:**

- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/entity/EventProcessingLog.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/dto/VehicleUpdatedEvent.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/service/VehicleEventIngestionService.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/listener/VehicleEventListener.java`
- `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/repository/EventProcessingLogRepository.java`

**Key Features:**

- Consume `VehicleUpdated` events from message queue
- Idempotency via `eventId` tracking in `EventProcessingLog`
- Conflict policies:
  - Mileage decrease: accept and flag (`MILEAGE_DECREASE`)
  - VIN correction: controlled flow with approval (`VIN_CONFLICT` if already exists)
  - Default: last-write-wins with audit
- Dead-letter queue for invalid/failed events
- Emit processing status metrics

**Event Payload:**

```java
public class VehicleUpdatedEvent {
    private String eventId; // UUID
    private String workorderId;
    private UUID vehicleId;
    private Instant eventTimestamp;
    private UpdatedFields updatedFields;
    
    public static class UpdatedFields {
        private String vin;
        private Integer mileage;
        private String notes;
    }
}
```

**Processing Log:**

```java
@Entity
@Table(name = "event_processing_log")
public class EventProcessingLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID logId;
    
    @Column(nullable = false, unique = true)
    private String eventId;
    
    @Column(nullable = false)
    private String workorderId;
    
    @Column(nullable = false)
    private UUID vehicleId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;
    
    @Column(nullable = false)
    private Instant receivedTimestamp;
    
    @Column
    private Instant processedTimestamp;
}

public enum ProcessingStatus {
    SUCCESS,
    SUCCESS_WITH_FLAGS,
    DUPLICATE,
    ERROR_VALIDATION,
    ERROR_NOT_FOUND,
    PENDING_REVIEW
}
```

---

## API Endpoints Summary

### Vehicle CRUD

- `POST /v1/vehicles` - Create vehicle
- `GET /v1/vehicles/{id}` - Get vehicle by ID
- `PUT /v1/vehicles/{id}` - Update vehicle
- `DELETE /v1/vehicles/{id}` - Delete vehicle
- `GET /v1/vehicles/vin/{vin}` - Get vehicle by VIN
- `PUT /v1/vehicles/vin/{vin}` - Update vehicle by VIN

### Vehicle Associations

- `POST /v1/vehicles/{vehicleId}/associations` - Create association (owner/driver)
- `GET /v1/vehicles/{vehicleId}/associations` - Get current associations
- `PUT /v1/vehicles/{vehicleId}/associations/owner` - Reassign owner
- `PUT /v1/vehicles/{vehicleId}/associations/driver` - Assign/change driver

### Vehicle Search

- `POST /v1/vehicles/search` - Search vehicles
- `GET /v1/vehicles/{vehicleId}/snapshot` - Get vehicle+owner snapshot

### Vehicle Preferences

- `GET /v1/vehicles/{vehicleId}/preferences` - Get preferences
- `PUT /v1/vehicles/{vehicleId}/preferences` - Upsert preferences

---

## Testing Strategy

### Contract Behavior Tests

Create `ContractBehaviorIT` tests for each story covering:

1. **Story #105 (CRUD)**:
   - Happy path: create vehicle with valid VIN
   - Duplicate VIN rejection (global uniqueness)
   - Invalid VIN format rejection
   - Missing required field rejection

2. **Story #104 (Associations)**:
   - Create initial owner association
   - Implicit owner reassignment (atomic)
   - Assign primary driver (requires active owner)
   - Driver persists across owner change
   - Idempotent owner assignment

3. **Story #103 (Search)**:
   - Exact VIN match
   - Partial unit number prefix match
   - No results for nonexistent query
   - Result limit enforcement
   - Snapshot retrieval on selection

4. **Story #102 (Preferences)**:
   - Create preferences with valid JSONB
   - Update preferences (optimistic locking)
   - Retrieve preferences on work docs
   - Validation of known keys
   - Flexible schema (unknown keys accepted)

5. **Story #101 (Event Ingestion)**:
   - Successful vehicle update
   - Idempotent duplicate event handling
   - Vehicle not found error
   - Mileage decrease acceptance with flag
   - VIN correction with conflict detection

### ArchUnit Tests

Verify:

- Internal package encapsulation
- Service layer exposure
- No direct repository access from controllers
- Event annotations present on state-changing operations

---

## Configuration Changes

### Application Properties

Add to `pos-vehicle-inventory/src/main/resources/application.yml`:

```yaml
pos:
  vehicle:
    search:
      default-limit: 25
      max-limit: 50
      min-query-length:
        vin: 6
        plate: 3
        general: 3
      enable-contains-matching: false
    preferences:
      max-payload-size: 65536  # 64KB
    event-ingestion:
      mileage-decrease-policy: ACCEPT_AND_FLAG
      vin-change-requires-approval: true
      default-conflict-policy: LAST_WRITE_WINS
```

### Event Type Registration

Create event type initializer in `pos-vehicle-inventory/src/main/java/com/positivity/vehicle/internal/config/VehicleEventTypeInitializer.java` registering:

- `VEHICLE_CREATE`
- `VEHICLE_UPDATE`
- `VEHICLE_SEARCH`
- `VEHICLE_ASSOCIATION_CREATE`
- `VEHICLE_PREFERENCE_UPDATE`

---

## Database Migrations

Create Flyway migrations in `pos-vehicle-inventory/src/main/resources/db/migration/`:

### V1__create_vehicles_table.sql

```sql
CREATE TABLE vehicles (
    vehicle_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    vin VARCHAR(17) NOT NULL,
    vin_normalized VARCHAR(17) NOT NULL UNIQUE,
    unit_number VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    license_plate VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vehicles_account_id ON vehicles(account_id);
CREATE INDEX idx_vehicles_vin_normalized ON vehicles(vin_normalized);
CREATE INDEX idx_vehicles_unit_number ON vehicles(unit_number);
CREATE INDEX idx_vehicles_license_plate ON vehicles(license_plate);
```

### V2__create_vehicle_party_associations_table.sql

```sql
CREATE TABLE vehicle_party_associations (
    association_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(vehicle_id),
    party_id UUID NOT NULL,
    association_type VARCHAR(50) NOT NULL,
    effective_start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_end_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vpa_vehicle_id ON vehicle_party_associations(vehicle_id);
CREATE INDEX idx_vpa_party_id ON vehicle_party_associations(party_id);
CREATE INDEX idx_vpa_type_dates ON vehicle_party_associations(vehicle_id, association_type, effective_start_date, effective_end_date);
```

### V3__create_vehicle_care_preferences_table.sql

```sql
CREATE TABLE vehicle_care_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL UNIQUE REFERENCES vehicles(vehicle_id),
    preferences JSONB NOT NULL,
    service_notes TEXT,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vcp_vehicle_id ON vehicle_care_preferences(vehicle_id);
```

### V4__create_event_processing_log_table.sql

```sql
CREATE TABLE event_processing_log (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL UNIQUE,
    workorder_id VARCHAR(255) NOT NULL,
    vehicle_id UUID NOT NULL REFERENCES vehicles(vehicle_id),
    status VARCHAR(50) NOT NULL,
    details JSONB,
    received_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_timestamp TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_epl_event_id ON event_processing_log(event_id);
CREATE INDEX idx_epl_workorder_id ON event_processing_log(workorder_id);
CREATE INDEX idx_epl_vehicle_id ON event_processing_log(vehicle_id);
CREATE INDEX idx_epl_status ON event_processing_log(status);
```

---

## Dependencies

Add to `pos-vehicle-inventory/pom.xml`:

```xml
<dependency>
    <groupId>com.positivity</groupId>
    <artifactId>pos-events</artifactId>
    <version>${project.version}</version>
</dependency>

<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.0</version>
</dependency>
```

---

## OpenAPI Documentation

All endpoints will be annotated with:

- `@Operation` with summary and description
- `@ApiResponse` for all status codes (200, 201, 400, 404, 409, 500)
- `@Parameter` for all path/query/body parameters
- Request/response examples in annotations

OpenAPI spec will be generated to `pos-vehicle-inventory/target/openapi.json`.

---

## Observability

### Events Emitted

- `VEHICLE_CREATE` - Vehicle created
- `VEHICLE_UPDATE` - Vehicle updated
- `VEHICLE_OWNER_ASSOCIATED` - Owner assigned
- `VEHICLE_OWNER_REASSIGNED` - Owner changed
- `VEHICLE_PRIMARY_DRIVER_ASSIGNED` - Driver assigned
- `VEHICLE_SEARCH` - Search performed
- `VEHICLE_SELECTED_FOR_SERVICE` - Vehicle selected from search
- `VEHICLE_PREFERENCE_CREATED` - Preferences created
- `VEHICLE_PREFERENCE_UPDATED` - Preferences updated

### Metrics

- Vehicle creation rate
- Search request rate and latency (p50, p95, p99)
- Association creation rate
- Event ingestion rate and status distribution
- DLQ message count

### Logging

- INFO: Successful operations with entity IDs
- WARN: Validation failures with field and reason
- ERROR: Persistence failures and system errors
- All logs include correlation ID for distributed tracing

---

## Security

All endpoints protected with Spring Security annotations:

- `@PreAuthorize("hasAuthority('VEHICLE_CREATE')")`
- `@PreAuthorize("hasAuthority('VEHICLE_VIEW')")`
- `@PreAuthorize("hasAuthority('VEHICLE_EDIT')")`
- `@PreAuthorize("hasAuthority('VEHICLE_PARTY_ASSOC_EDIT')")`

Permissions registered in `pos-security-common`.

---

## Next Steps

1. Implement all entities and DTOs
2. Create service layer with business logic
3. Implement controllers with OpenAPI annotations
4. Create repositories with custom queries
5. Add event listeners and emitters
6. Create ContractBehaviorIT tests
7. Add ArchUnit tests
8. Update OpenAPI documentation
9. Commit and push changes
10. Create pull request

---

## Current Implementation Status

### Phase 1: Architecture and Planning ✅ COMPLETE

- ✅ Analyzed all 5 backend stories (#101-#105)
- ✅ Reviewed existing pos-vehicle-inventory module structure
- ✅ Created comprehensive implementation guide with:
  - Entity designs for all stories
  - API endpoint specifications
  - Database migration scripts
  - Testing strategy
  - Configuration requirements
  - Observability plan

### Phase 2: Core Implementation 🚧 IN PROGRESS

The pos-vehicle-inventory module exists with basic vehicle CRUD but requires significant enhancement to meet CAP:091 requirements:

**What Exists:**
- Basic Vehicle entity (inheritance-based, needs refactoring to composition)
- Simple CRUD endpoints (by ID and VIN)
- Basic repository layer

**What Needs Implementation:**
- VIN normalization and global uniqueness
- UUID-based IDs (currently using Long)
- Vehicle-party associations (Story #104)
- Vehicle search with ranking (Story #103)
- Vehicle care preferences with JSONB (Story #102)
- Event ingestion from workorder execution (Story #101)
- Comprehensive validation and error handling
- Event emissions for all state changes
- Contract behavior tests
- OpenAPI documentation updates

### Recommended Next Steps

1. **Refactor Vehicle Entity** (Story #105):
   - Change ID from Long to UUID
   - Add vinNormalized field with unique constraint
   - Add accountId, unitNumber, licensePlate fields
   - Add isActive flag and version for optimistic locking
   - Remove inheritance (Van, Car, etc.) in favor of composition

2. **Implement Service Layer**:
   - Create VehicleService with business logic
   - Add VIN validation and normalization utility
   - Implement global uniqueness checks
   - Add event emission via pos-events

3. **Implement Associations** (Story #104):
   - Create VehiclePartyAssociation entity
   - Implement temporal association logic
   - Add owner reassignment with atomic operations
   - Create VehicleAssociationService and controller

4. **Implement Search** (Story #103):
   - Create SearchVehiclesRequest/Response DTOs
   - Implement VehicleSearchService with ranking
   - Add query normalization and min-length validation
   - Create VehicleSearchController

5. **Implement Preferences** (Story #102):
   - Create VehicleCarePreference entity with JSONB
   - Add Hypersistence Utils dependency for JSONB support
   - Implement VehiclePreferencesService
   - Create VehiclePreferencesController

6. **Implement Event Ingestion** (Story #101):
   - Create EventProcessingLog entity
   - Implement VehicleEventIngestionService
   - Add message listener for VehicleUpdated events
   - Implement conflict resolution policies

7. **Testing**:
   - Create ContractBehaviorIT for each story
   - Add ArchUnit tests for package structure
   - Verify event emissions
   - Test error handling

8. **Documentation**:
   - Update OpenAPI annotations
   - Generate updated openapi.json
   - Update contract guide with examples

---

**Implementation Status:** Architecture Complete, Implementation In Progress  
**Estimated Effort:** 40-60 hours for full implementation  
**Assignee:** GitHub Copilot Agent  
**PR Status:** Ready for architecture review and incremental implementation
