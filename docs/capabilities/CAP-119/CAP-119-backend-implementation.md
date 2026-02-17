# CAP-119 Backend Implementation (pos-people)

## Scope
- Capability: `CAP119`
- Backend child issue: `louisburroughs/durion-positivity-backend#86`
- Domain: `people`
- Branch: `cap/CAP119`
- OpenAPI source of truth (read-only): `durion-positivity-backend/pos-people/openapi.json`

## Implementation Checklist
- Updated person access assignment request flow with explicit request validation for required `roleCode`.
- Added assignment date-window validation (`endDate >= startDate`) in service layer.
- Improved upstream error status mapping to preserve `401`, `403`, and `409` semantics from security integration.
- Replaced stale `ContractBehaviorIT` employee tests with CAP119 assignment contract tests against `/v1/people/{personUuid}/access/*`.
- Added contract assertions for happy path, validation failure, auth failure behavior, overlap conflict behavior, and revoke flow.
- Aligned ArchUnit rule to avoid treating test classes as public service API.
- Verified full module test suite and focused contract test execution.

## Files Changed

### Backend repo (`durion-positivity-backend`)
- `pos-people/src/main/java/com/positivity/people/internal/controller/PersonAccessController.java`
- `pos-people/src/main/java/com/positivity/people/internal/controller/PeopleExceptionHandler.java`
- `pos-people/src/main/java/com/positivity/people/internal/service/PeopleAccessControlServiceImpl.java`
- `pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java`
- `pos-people/src/test/java/com/positivity/people/ArchitectureTest.java`
- `pos-people/src/test/java/com/positivity/people/service/PeopleAccessControlServiceTest.java`
- `pos-people/src/test/java/com/positivity/people/service/UserPersonTranslationServiceTest.java`

### Coordination repo (`durion`)
- `docs/capabilities/CAP-119/CAP-119-backend-implementation.md` (this file)

## Critical Snippets

### Controller signature and request guard
```java
@PostMapping("/{personUuid}/access/assignments")
public ResponseEntity<UserRoleDto> createAssignment(
        @PathVariable UUID personUuid,
        @Valid @RequestBody PersonRoleAssignmentRequest request) {
    if (request.getRoleCode() == null || request.getRoleCode().isBlank()) {
        throw new IllegalArgumentException("roleCode is required");
    }

    UserRoleDto created = peopleAccessControlService.assignRoleToPerson(
            personUuid,
            request.getRoleCode(),
            request.getLocationId(),
            request.getStartDate(),
            request.getEndDate());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

### Service method validation
```java
@Override
@NonNull
public UserRoleDto assignRoleToPerson(
        @NonNull UUID personUuid,
        @NonNull String roleCode,
        UUID locationId,
        LocalDateTime startDate,
        LocalDateTime endDate) {
    validateDateWindow(startDate, endDate);
    String userId = resolveUserId(personUuid);
    UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder()
            .userId(userId)
            .roleCode(roleCode)
            .locationId(locationId)
            .startDate(startDate)
            .endDate(endDate)
            .build();
    return securityServiceClient.assignRole(request);
}

private void validateDateWindow(LocalDateTime startDate, LocalDateTime endDate) {
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
        throw new IllegalArgumentException("endDate must be greater than or equal to startDate");
    }
}
```

### Error mapping preserving conflict semantics
```java
private HttpStatus determineHttpStatus(SecurityServiceException ex) {
    int statusCode = ex.getHttpStatus();

    return switch (statusCode) {
        case 400 -> HttpStatus.BAD_REQUEST;
        case 401 -> HttpStatus.UNAUTHORIZED;
        case 403 -> HttpStatus.FORBIDDEN;
        case 404 -> HttpStatus.NOT_FOUND;
        case 409 -> HttpStatus.CONFLICT;
        case 502 -> HttpStatus.BAD_GATEWAY;
        case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
        case 504 -> HttpStatus.GATEWAY_TIMEOUT;
        default -> {
            if (statusCode >= 500 && statusCode < 600) {
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (statusCode >= 400 && statusCode < 500) {
                yield HttpStatus.BAD_REQUEST;
            }
            yield HttpStatus.INTERNAL_SERVER_ERROR;
        }
    };
}
```

### Repository query snippet
- No repository changes were required for CAP119 in `pos-people`; assignment persistence/query behavior is delegated through the existing security-service facade client.

### ContractBehaviorIT sample (contract examples)
```java
@Test
@DisplayName("concurrency invariant: overlap conflict maps to 409")
void createAssignment_overlapConflict() throws Exception {
    UUID personUuid = UUID.randomUUID();

    when(peopleAccessControlService.assignRoleToPerson(eq(personUuid), eq("MECHANIC"), any(), any(), any()))
            .thenThrow(new SecurityServiceException("Overlapping assignments are not allowed", 409));

    String payload = """
            {
              "roleCode": "MECHANIC",
              "locationId": "22222222-2222-2222-2222-222222222222",
              "startDate": "2026-06-01T00:00:00",
              "endDate": "2026-08-01T00:00:00"
            }
            """;

    mockMvc.perform(withAuth(post("/v1/people/{personUuid}/access/assignments", personUuid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Overlapping assignments are not allowed"));
}
```

## Contract Alignment Notes
- `openapi.json` was treated as authoritative and not edited.
- Implementation was aligned to the documented `/v1/people/{personUuid}/access/assignments/{roleCode}` revoke path and status contracts.
- Validation and error handling were tightened to enforce expected `400`/`409` outcomes from contract assertions.

## Configuration Changes
- No runtime configuration/property changes were required.
- No event type registration changes were required; existing `PEOPLE_ACCESS_*` event types remain unchanged.

## Verification
- Focused contract behavior test: `ContractBehaviorIT` (8/8 passing via `runTests`).
- Full module tests: `./mvnw -pl pos-people test` (46 tests, 0 failures, 0 errors).
- Sonar analysis triggered for all changed files; no blocker/high/security issues remained in changed code after final edits.

## Completion Details
- Branch: `cap/CAP119`
- Commit hash: `712621440ee5c08bc231494b7169861cbdfe3ccc`
- Files changed vs main:
    - `pos-people/src/main/java/com/positivity/people/internal/controller/PeopleExceptionHandler.java`
    - `pos-people/src/main/java/com/positivity/people/internal/controller/PersonAccessController.java`
    - `pos-people/src/main/java/com/positivity/people/internal/service/PeopleAccessControlServiceImpl.java`
    - `pos-people/src/test/java/com/positivity/people/ArchitectureTest.java`
    - `pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java`
    - `pos-people/src/test/java/com/positivity/people/service/PeopleAccessControlServiceTest.java`
    - `pos-people/src/test/java/com/positivity/people/service/UserPersonTranslationServiceTest.java`
- Push verification: `git ls-remote --heads origin cap/CAP119` returned `712621440ee5c08bc231494b7169861cbdfe3ccc refs/heads/cap/CAP119`
