---
capability: CAP-117
title: CAP-117 Backend Contract — Identity Orchestration (User ↔ Person Linking)
version: 1.0
generated: 2026-02-18
---

# CAP-117 Backend Contract — Identity Orchestration (User ↔ Person Linking)

Domain: `people`  
Provider module: `pos-people`  
Gateway base URL: `http://localhost:8080/v1/people`

This document specifies the backend contract for CAP-117 covering three backend stories:
- #88 — Employee Profile CRUD (create/update/get, duplicate detection, lifecycle)
- #90 — Disable User (offboarding / soft-delete / saga pattern)
- #91 — Provision User & Link to Person (admin + event-driven linking)

## Conventions
- JSON: camelCase
- Enums: UPPER_SNAKE_CASE where applicable
- Tests naming: `CP-117-NNN` for capability positive flows, `VE-117-NNN` for validation, `LC-117-NNN` for lifecycle

---

## Endpoint: Create Employee

- Method: POST
- URL: `http://localhost:8080/v1/people/employees`
- Purpose: Create an employee profile used for assignment, onboarding and identity orchestration.

Request schema (TypeScript-like):
```ts
interface CreateEmployeeRequest {
  legalName?: string;
  preferredName?: string;
  employeeNumber?: string; // unique when present
  status?: 'ACTIVE' | 'ON_LEAVE' | 'SUSPENDED' | 'TERMINATED';
  hireDate?: string; // yyyy-MM-dd
  terminationDate?: string; // yyyy-MM-dd
  contactInfo?: {
    emails?: string[];
    phones?: string[];
  };
  duplicatePolicy?: 'STRICT' | 'BALANCED'; // default BALANCED
}
```

Successful response:
- `201 Created`
```json
{
  "employeeId": "123e4567-e89b-12d3-a456-426614174000",
  "legalName": "Jane Doe",
  "preferredName": "Jane",
  "employeeNumber": "EMP-001",
  "status": "ACTIVE",
  "hireDate": "2026-01-01",
  "contactInfo": { "emails": ["jane@example.com"] },
  "createdAt": "2026-02-18T12:00:00Z"
}
```

Error / alternate semantics:
- `409 Conflict`: High-confidence duplicate (STRICT policy or exact match on email/phone/employeeNumber).
- `201 Created` with `warnings` array: BALANCED duplicate policy returned soft-warnings; client should review.

Behavioral assertions:
- Duplicate detection must run for every create and update request. STRICT returns `409` on exact matches, BALANCED returns created resource with `warnings` when ambiguous.
- `employeeNumber` uniqueness enforced.
- Database allows optional contactInfo, but business workflows require at least one contact before assignment APIs allow assignments.

Test hints:
- CP-117-001: create employee happy path
- VE-117-001: create with terminationDate < hireDate -> 400
- VE-117-002: create with duplicate exact email -> 409 (STRICT)

---

## Endpoint: Update Employee

- Method: PUT
- URL: `http://localhost:8080/v1/people/employees/{employeeId}`
- Purpose: Update employee profile

Request: same as `CreateEmployeeRequest`.

Responses:
- `200 OK` — updated `EmployeeResponse`
- `404 Not Found` — no such `employeeId`
- `409 Conflict` — duplicate detection conflict as described above

Behavioral assertions:
- Same duplicate detection as create.
- Partial updates must preserve fields not present in the request only when API semantics permit (client should send full resource for PUT).

Test hints:
- CP-117-002: update happy path
- VE-117-003: update non-existent employee -> 404

---

## Endpoint: Get Employee

- Method: GET
- URL: `http://localhost:8080/v1/people/employees/{employeeId}`
- Purpose: Retrieve employee profile

Responses:
- `200 OK` — `EmployeeResponse`
- `404 Not Found` — missing

Employee fields summary:
- `employeeId`: uuid
- `legalName`, `preferredName`
- `employeeNumber` (unique if present)
- `status`: `ACTIVE | ON_LEAVE | SUSPENDED | TERMINATED`
- `hireDate`, `terminationDate` (terminationDate >= hireDate)
- `contactInfo`: structured (emails[], phones[])

Test hints:
- CP-117-003: get happy path

---

## Endpoint: Disable User / Offboard

- Method: POST
- URL: `http://localhost:8080/v1/people/employees/{employeeId}/disable`
- Purpose: Disable a user's account as part of offboarding; sets Person+User to `DISABLED` and emits `user.disabled` event for downstream processing.

Request body:
```json
{
  "disableReason": "Voluntary resignation",
  "assignmentPolicy": "END_ASSIGNMENTS_NOW|END_ASSIGNMENTS_AT_DATE|LEAVE_ASSIGNMENTS_ACTIVE",
  "assignmentEndDate": "2026-03-01" // optional when END_ASSIGNMENTS_AT_DATE
}
```

Responses:
- `200 OK` — processed and event emitted
- `400 Bad Request` — already DISABLED/TERMINATED or invalid payload
- `404 Not Found` — employee not found

Behavioral assertions:
- Allowed status transitions: `ACTIVE -> DISABLED` (reversible via admin restore if supported), `ACTIVE -> TERMINATED` (irreversible).
- If employee already `DISABLED` or `TERMINATED` return `400` with problem details explaining current state.
- On success immediately publish `user.disabled` event with: `{ employeeId, userId?, disableReason, assignmentPolicy }`.
- Downstream services consume `user.disabled` and coordinate offboarding (security cleanup, assignment termination, payroll notifications) in a saga; retries handled by consumers.

Test hints:
- CP-117-010: disable happy path and verify event emission (mock event sink)
- VE-117-010: disabling an already DISABLED returns 400

---

## Endpoint: Create User-Person Link (Admin / Idempotent)

- Method: POST
- URL: `http://localhost:8080/v1/people/user-links`
- Purpose: Create a `UserPersonLink` binding between an auth `userId` and a domain `personId`. This API is admin-facing; the same operation is performed by an event consumer on `UserCreated` events.

Request (TypeScript-like):
```ts
interface CreateUserLinkRequest {
  userId: string; // uuid
  personId: string; // uuid
}
```

Responses:
- `201 Created` — link created (returns `UserPersonLinkResponse`)
- `200 OK` — if the same link already exists (idempotent)
- `409 Conflict` — `userId` already linked to a different `personId` (1:1 constraint)
- `404 Not Found` — `personId` does not exist

Behavioral assertions:
- Enforce 1:1 mapping: a `userId` cannot be linked to multiple `personId`s. Conflicting attempts yield `409`.
- The endpoint is idempotent: repeated identical requests return `200` (if already created) or `201` (on first create).
- The system must also support the event-driven path: consumer of `UserCreated` (or `UserProvisioned`) should call the same internal service to create the link.

UserPersonLinkResponse example:
```json
{
  "linkId": "9f8b7a6c-5d4e-4b2a-8f1e-0a1b2c3d4e5f",
  "userId": "abc-123",
  "personId": "123e4567-e89b-12d3-a456-426614174000",
  "createdAt": "2026-02-18T12:05:00Z",
  "createdBy": "system"
}
```

Test hints:
- CP-117-020: create link happy path -> 201
- CP-117-021: idempotent repeat -> 200
- VE-117-020: conflicting userId -> 409

---

## Endpoint: Get User-Person Link by Person

- Method: GET
- URL: `http://localhost:8080/v1/people/user-links/{personId}`
- Purpose: Retrieve link for a given person

Responses:
- `200 OK` — `UserPersonLinkResponse` or empty representation when no link exists
- `404 Not Found` — `personId` not found

---

## Events
- `user.disabled` — published synchronously by disable endpoint. Consumers: `pos-security-service`, `pos-workexec`, `pos-payroll` (as example). Event must include `employeeId`, optional `userId`, `disableReason`, and `assignmentPolicy`.
- `USER_PERSON_LINK_CREATE` — published on link creation; existing event conventions in repo apply.

## Contract Tests Guidance
- Provide behavioral tests (integration-style) that assert HTTP status codes and event publication.
- Tests should mock event sinks to assert emitted payloads for `user.disabled` and `USER_PERSON_LINK_CREATE`.

## What to document in PR
- OpenAPI changes (add new paths/schemas if not present)
- Event type registration (if new `user.disabled` event type registration required)
- DB constraints (unique index on `employeeNumber`, unique constraint on `userId` -> link table)

---

## References
- OpenAPI (authoritative): `pos-people/target/openapi.json`
- Backend issues: `louisburroughs/durion-positivity-backend#88`, `#90`, `#91`
