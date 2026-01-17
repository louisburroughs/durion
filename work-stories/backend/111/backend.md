Title: [BACKEND] [STORY] Party: Create Individual Person Record
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/111
Labels: type:story, domain:crm, status:ready-for-dev

## Story Intent
As a Customer Service Representative (CSR), I want to create an individual person record with contact information, so that I can associate that person to business activities like commercial accounts, vehicles, and work orders.

## Actors & Stakeholders
- **Primary Actor:** CSR
- **System of Record:** CRM / Party Service (owns Party/Person master data)
- **Downstream Consumers:** Workorder Execution, Billing (reference Party/Person but do not own it)

## Preconditions
- CSR is authenticated and authorized to create person records.
- Party/CRM service is available.

## Functional Behavior
1. CSR calls “Create Individual Person” endpoint.
2. Request includes: `firstName`, `lastName`, `preferredContactMethod`, optional lists of emails and phones.
3. System validates required fields and formats.
4. System creates a `Person` record with a new immutable `personId`.
5. System creates zero-to-many `ContactPoint` records and associates them to `personId`.
6. System returns `201 Created` with `personId`.

## Alternate / Error Flows
- Missing required fields (e.g., `lastName`) → `400 Bad Request`.
- Invalid email format → `400 Bad Request`.
- Persistence failure → `500 Internal Server Error`; transaction rolled back.

## Business Rules (Resolved)
- **Domain authority:** `domain:crm` is the system of record for Party/Person entities; payment systems may reference but do not own Party/Person.
- **Required fields:** `firstName` and `lastName`.
- **Identifier:** `personId` is system-generated, unique, immutable.
- **Contact points:** zero-to-many phones and emails.
- **Preferred contact method enum (resolved):** `EMAIL | PHONE_CALL | SMS | NONE`.
- **Role tags (billing contact, driver, approver) (resolved):** out of scope for this story; do not add placeholder schema.
- **Duplicate detection (resolved):** duplicates are acceptable for this story; no dedup/merge workflow required.

## Data Requirements
### Person
- `personId` (UUID)
- `firstName` (string, required)
- `lastName` (string, required)
- `preferredContactMethod` (enum, required)
- `createdAt`, `updatedAt`

### ContactPoint
- `contactPointId` (UUID)
- `personId` (FK)
- `contactType` (enum; e.g., `EMAIL`, `PHONE_MOBILE`, `PHONE_HOME`)
- `value` (string)
- `isPrimary` (boolean, default false)
- `createdAt`, `updatedAt`

## Acceptance Criteria
- **AC1:** Minimal create (name + preferred method) returns `201` and persists a Person.
- **AC2:** Create with two emails and two phone numbers persists four ContactPoints.
- **AC3:** Missing `lastName` returns `400` and persists nothing.
- **AC4:** Invalid email format returns `400` and persists nothing.

## Audit & Observability
- Audit event on success: `PERSON_CREATED` with `personId`, actorId, timestamp.
- Metric: `person_creation_total` increments on success.

## Open Questions
None.

---

## Original Story (Unmodified – For Traceability)
(See original content in the issue history prior to this rewrite.)