# SPEC: People Directory — Backend

Companion to `SPEC-people-directory.md`. Specifies changes to `pos-people` module
needed to support the directory page: exposing `employeeStatus` on `GET /v1/people`
and adding `?q=` / `?type=` server-side filter params.

---

## 1. Summary of Findings

| Finding | Detail |
|---|---|
| `Person` entity | Already has `status: EmployeeStatus` (ACTIVE, ON_LEAVE, SUSPENDED, TERMINATED, DISABLED) |
| `Person` DTO | Does **not** expose `status` — gap resolved by this spec |
| `GET /v1/people` | Returns all persons unfiltered; no query params |
| `PersonRepository` | Does not implement `JpaSpecificationExecutor` |
| Flyway migration | **Not needed** — `status` column exists in `V1__baseline_people_schema.sql` |

---

## 2. OQ1 Resolution

`Person` entity already discriminates person type via `status`:

| `status` value | Meaning |
|---|---|
| `null` | Non-employee person (created via resolve/ingest, no employee record) |
| `ACTIVE` | Active employee |
| `ON_LEAVE`, `SUSPENDED`, `TERMINATED`, `DISABLED` | Inactive employee |

Frontend filter chips map as follows:

| Chip | `?type=` param | Server predicate |
|---|---|---|
| All | `ALL` (or absent) | no filter |
| Employees | `EMPLOYEE` | `status IS NOT NULL` |
| Active | `ACTIVE` | `status = 'ACTIVE'` |
| Inactive | `INACTIVE` | `status IN ('ON_LEAVE','SUSPENDED','TERMINATED','DISABLED')` |

---

## 3. Changes Required

### 3.1 `Person` DTO — expose `employeeStatus`

**File**: `pos-people/src/main/java/com/positivity/people/internal/dto/Person.java`

Add field:

```java
@Schema(description = "Employee status. Null if the person has no employee record.")
private EmployeeStatus employeeStatus;
```

Add getter/setter for `employeeStatus`. Import `com.positivity.people.internal.enums.EmployeeStatus`.

### 3.2 `PersonServiceImpl.toDto()` — map status

**File**: `...internal/service/PersonServiceImpl.java`

In `toDto()`, add:

```java
dto.setEmployeeStatus(entity.getStatus());
```

No change to `toEntity()` — `employeeStatus` is read-only on `GET /v1/people`; employee
status is managed via `EmployeeController`.

### 3.3 `PersonRepository` — add Specification support

**File**: `...internal/repository/PersonRepository.java`

Add `JpaSpecificationExecutor<Person>` to the interface:

```java
public interface PersonRepository extends JpaRepository<Person, UUID>,
        JpaSpecificationExecutor<com.positivity.people.internal.entity.Person> {
    // existing methods unchanged
}
```

No new dependency — `JpaSpecificationExecutor` is part of `spring-data-jpa`.

### 3.4 New class — `PersonSpecifications`

**File** (new): `...internal/repository/PersonSpecifications.java`

```java
package com.positivity.people.internal.repository;

import com.positivity.people.internal.entity.Person;
import com.positivity.people.internal.enums.EmployeeStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PersonSpecifications {

    private PersonSpecifications() {}

    /**
     * Combined filter for the people directory.
     *
     * @param type  "EMPLOYEE", "ACTIVE", "INACTIVE", or null/blank for no filter
     * @param q     free-text search against firstName, lastName, primaryEmail, username
     */
    public static Specification<Person> directoryFilter(String type, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (type != null) {
                switch (type.toUpperCase()) {
                    case "EMPLOYEE" ->
                        predicates.add(root.get("status").isNotNull());
                    case "ACTIVE" ->
                        predicates.add(cb.equal(root.get("status"),
                                EmployeeStatus.ACTIVE));
                    case "INACTIVE" ->
                        predicates.add(root.get("status").in(
                                EmployeeStatus.ON_LEAVE,
                                EmployeeStatus.SUSPENDED,
                                EmployeeStatus.TERMINATED,
                                EmployeeStatus.DISABLED));
                    default -> { /* ALL or unknown — no predicate */ }
                }
            }

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")),  pattern),
                        cb.like(cb.lower(root.get("lastName")),   pattern),
                        cb.like(cb.lower(root.get("primaryEmail")), pattern),
                        cb.like(cb.lower(root.get("username")),   pattern)
                ));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

### 3.5 `PersonService` interface — add overload

**File**: `...service/PersonService.java`

Add:

```java
@NonNull
List<Person> getAllPeople(@Nullable String type, @Nullable String q);
```

Keep existing `getAllPeople()` — delegate it to the new overload:

```java
@NonNull
default List<Person> getAllPeople() {
    return getAllPeople(null, null);
}
```

Or keep both as abstract and implement both in `PersonServiceImpl`.  
Prefer default method to minimise callers affected (existing unit tests, other callers).

### 3.6 `PersonServiceImpl` — implement filtered `getAllPeople`

**File**: `...internal/service/PersonServiceImpl.java`

```java
@Override
@NonNull
public List<Person> getAllPeople(@Nullable String type, @Nullable String q) {
    return personRepository
            .findAll(PersonSpecifications.directoryFilter(type, q))
            .stream()
            .map(this::toDto)
            .toList();
}
```

Update existing `getAllPeople()` to delegate:

```java
@Override
@NonNull
public List<Person> getAllPeople() {
    return getAllPeople(null, null);
}
```

### 3.7 `PersonController` — add query params to `GET /v1/people`

**File**: `...internal/controller/PersonController.java`

Replace:

```java
@GetMapping
public List<Person> getAllPeople() {
    return personService.getAllPeople();
}
```

With:

```java
@Operation(summary = "Get all people", description = "Retrieve people with optional type and text filters.")
@Parameter(name = "type",  description = "Filter by person type: EMPLOYEE, ACTIVE, INACTIVE, or ALL (default).",
           schema = @Schema(allowableValues = {"ALL", "EMPLOYEE", "ACTIVE", "INACTIVE"}))
@Parameter(name = "q",     description = "Case-insensitive text search on firstName, lastName, primaryEmail, username.")
@ApiResponse(responseCode = "200", description = "List of people returned successfully.")
@GetMapping
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth", scopes = {"people:person:view"})
@PreAuthorize("hasAuthority('people:person:view')")
public List<Person> getAllPeople(
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "q",    required = false) String q) {
    return personService.getAllPeople(type, q);
}
```

Add imports: `org.springframework.web.bind.annotation.RequestParam`,
`io.swagger.v3.oas.annotations.Parameter`, `io.swagger.v3.oas.annotations.media.Schema`.

---

## 4. No Flyway Migration

`status` column already exists in `V1__baseline_people_schema.sql`:

```sql
CREATE TABLE person (
    ...
    status character varying(255),
    CONSTRAINT person_status_check CHECK (
        status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED','DISABLED')
    ),
    ...
);
```

No schema change. No new migration script needed.

---

## 5. API Contract (updated)

```
GET /v1/people
  ?type  = ALL | EMPLOYEE | ACTIVE | INACTIVE   (optional, default ALL)
  ?q     = <string>                              (optional, case-insensitive text match)

→ 200 Array<Person>

Person {
  id:             UUID
  firstName:      string
  lastName:       string
  primaryEmail:   string?
  secondaryEmail: string?
  phoneNumbers:   string[]?
  username:       string?
  employeeStatus: "ACTIVE" | "ON_LEAVE" | "SUSPENDED" | "TERMINATED" | "DISABLED" | null
}
```

`employeeStatus: null` means the person has no employee record (pure person).

---

## 6. ADR Compliance Checklist

| ADR | Requirement | Status |
|---|---|---|
| ADR-0011 | Service layer owns business logic — no query logic in controller | ✓ logic in `PersonServiceImpl` |
| ADR-0013 | JPA entities not exposed in API layer — DTO used | ✓ `Person` DTO, not entity |
| ADR-0014 | Repositories must not contain business logic | ✓ filter logic in `PersonSpecifications` helper |
| ADR-0017 | `@PreAuthorize` on all endpoints | ✓ `people:person:view` retained |
| ADR-0018 | OpenAPI `@Operation` + `@ApiResponse` on all endpoints | ✓ updated annotations |
| ADR-0023 | No raw JPQL/native SQL for queries expressible via Specification | ✓ `JpaSpecificationExecutor` |
| ADR-0024 | Enums stored as STRING, not ORDINAL | ✓ `@Enumerated(EnumType.STRING)` already on entity |
| ADR-0026 | Pagination for list endpoints over threshold — current endpoint unbounded | ⚠ See OQ3 below |
| ADR-0027 | Event emission for mutating operations only | ✓ `GET /v1/people` is read-only, no event needed |

---

## 7. Open Questions

| # | Question | Recommendation |
|---|---|---|
| OQ3 | ADR-0026 may require paginated response (`Page<Person>`) if row count exceeds threshold. What is current person count in production? | If < 1000, return `List<Person>` for v1 (frontend paginates client-side). Add `?page=&size=` in a follow-up story if growth requires it. |
| OQ4 | Should `q` search also match `employeeNumber`? | Include only firstName, lastName, primaryEmail, username for v1. |

---

## 8. Test Coverage

| Test class | What to test |
|---|---|
| `PersonSpecificationsTest` | Unit tests for each `directoryFilter` branch (null, ALL, EMPLOYEE, ACTIVE, INACTIVE, combined q + type) |
| `PersonServiceImplTest` | `getAllPeople(type, q)` delegates to repo with correct spec; `toDto` maps `employeeStatus` |
| `PersonControllerTest` (MockMvc) | `?type=ACTIVE`, `?q=smith`, combined params, no params → all reach service correctly |

---

## 9. Files Changed

| File | Change |
|---|---|
| `internal/dto/Person.java` | + `employeeStatus: EmployeeStatus` field + getter/setter |
| `internal/repository/PersonRepository.java` | + `JpaSpecificationExecutor<Person>` |
| `internal/repository/PersonSpecifications.java` | **NEW** — static Specification factory |
| `service/PersonService.java` | + `getAllPeople(String type, String q)` |
| `internal/service/PersonServiceImpl.java` | implement new overload; map status in `toDto()` |
| `internal/controller/PersonController.java` | add `?type` + `?q` `@RequestParam`s |
