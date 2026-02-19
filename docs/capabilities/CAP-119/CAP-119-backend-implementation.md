# CAP-119 Backend Implementation (pos-people)

## Scope

- Capability: `CAP119`
- Backend child issues: `louisburroughs/durion-positivity-backend#86`, `louisburroughs/durion-positivity-backend#87`
- Domain: `people`
- Branch: `cap/CAP119`
- OpenAPI source of truth (read-only): `durion-positivity-backend/pos-people/openapi.json`

## Summary

Implemented location management and staffing assignment APIs in `pos-people` with new persistence models, service orchestration, event registration, and contract coverage.

### Implemented Endpoints

- `GET /v1/locations`
- `POST /v1/locations`
- `GET /v1/locations/{locationId}`
- `PUT /v1/locations/{locationId}`
- `DELETE /v1/locations/{locationId}` (soft delete)
- `GET /v1/locations/{locationId}/staff`
- `POST /v1/locations/{locationId}/staff`
- `DELETE /v1/locations/{locationId}/staff/{personId}`

### Key Behavior Implemented

- Enforced unique location `code` with `409 Conflict` on duplicates.
- Enforced timezone validation with IANA lookup (`ZoneId.of`) and `400 Bad Request` on invalid values.
- Implemented location soft-delete by setting `active=false`.
- Enforced assignment overlap rejection for same `(personId, locationId)` window with `409 Conflict`.
- Implemented automatic primary demotion: assigning a new primary demotes existing active primary assignment for that person.
- Added consistent not-found/conflict exception mapping in `PeopleExceptionHandler`.

## Files Changed

### Backend repo (`durion-positivity-backend`)

- `pos-people/src/main/java/com/positivity/people/internal/controller/LocationController.java`
- `pos-people/src/main/java/com/positivity/people/internal/controller/PeopleExceptionHandler.java`
- `pos-people/src/main/java/com/positivity/people/internal/dto/AssignStaffRequest.java`
- `pos-people/src/main/java/com/positivity/people/internal/dto/CreateLocationRequest.java`
- `pos-people/src/main/java/com/positivity/people/internal/dto/LocationDto.java`
- `pos-people/src/main/java/com/positivity/people/internal/dto/PersonLocationAssignmentDto.java`
- `pos-people/src/main/java/com/positivity/people/internal/dto/UpdateLocationRequest.java`
- `pos-people/src/main/java/com/positivity/people/internal/entity/Location.java`
- `pos-people/src/main/java/com/positivity/people/internal/entity/PersonLocationAssignment.java`
- `pos-people/src/main/java/com/positivity/people/internal/enums/LocationType.java`
- `pos-people/src/main/java/com/positivity/people/internal/exception/DuplicateLocationCodeException.java`
- `pos-people/src/main/java/com/positivity/people/internal/exception/LocationAssignmentNotFoundException.java`
- `pos-people/src/main/java/com/positivity/people/internal/exception/LocationNotFoundException.java`
- `pos-people/src/main/java/com/positivity/people/internal/exception/PersonLocationAssignmentConflictException.java`
- `pos-people/src/main/java/com/positivity/people/internal/repository/LocationRepository.java`
- `pos-people/src/main/java/com/positivity/people/internal/repository/PersonLocationAssignmentRepository.java`
- `pos-people/src/main/java/com/positivity/people/internal/service/LocationServiceImpl.java`
- `pos-people/src/main/java/com/positivity/people/service/LocationService.java`
- `pos-people/src/main/java/com/positivity/people/internal/config/PeopleEventTypes.java`
- `pos-people/src/test/java/com/positivity/people/contract/LocationContractBehaviorIT.java`

### Coordination repo (`durion`)

- `docs/capabilities/CAP-119/CAP-119-backend-implementation.md` (this file)

## Design Decisions

- Kept package boundaries: new implementation classes remain under `com.positivity.people.internal.*`, with only `LocationService` exposed under `com.positivity.people.service`.
- Preserved thin-controller pattern: `LocationController` delegates all business logic to service layer.
- Used `@EmitEvent` on all mutation endpoints and registered event types in `PeopleEventTypes`.
- Used `@NonNull` on public service API and implementation method parameters.
- Followed ADR-0017 status code conventions: `201` create, `204` delete, `404` not found, `409` conflicts, `400` validation.

## How to Test

- Full module tests (Maven clean test path):
  - `./mvnw -pl pos-people -am clean test`
- Focused CAP-119 contract + architecture tests:
  - `runTests` on:
    - `pos-people/src/test/java/com/positivity/people/contract/LocationContractBehaviorIT.java`
    - `pos-people/src/test/java/com/positivity/people/ArchitectureTest.java`

## Verification Notes

- Sonar security issue scan run on changed CAP-119 files (`LocationServiceImpl`, `LocationController`, `Location`, `PersonLocationAssignment`): no potential security issues/vulnerabilities detected.
- `runTests` focused execution result: `13 passed, 0 failed` for CAP-119 + architecture.
