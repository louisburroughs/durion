Title: [BACKEND] [STORY] Users: Provision User and Link to Person
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/91
Labels: type:story, domain:security, status:ready-for-dev

## Story Intent
As an Admin, I want to provision a new system user (credentials + roles) and link that user to an existing person/employee record, so that the employee can authenticate and be correctly attributed across downstream systems (workexec/timekeeping).

## Actors & Stakeholders
- **Admin** (initiates provisioning)
- **Security Service (`pos-security-service`)**: system of record for `User`
- **People Service (`pos-people`)**: system of record for `Person` and `UserPersonLink`
- **Downstream services** (workexec/shopmgr/etc.) consume the link for attribution

## Preconditions
- Admin is authenticated and authorized to provision users.
- A `Person` exists in `pos-people` and has `personId`.

## Functional Behavior (Resolved)

### 1) Ownership / API Location
- `domain:security` owns the orchestration and hosts the initiating API endpoint.
- `domain:people` owns the canonical `UserPersonLink` record.

### 2) Decomposition / Integration Style
- Implement as an **event-driven** flow using a `UserCreated` / `UserProvisioned` event.

### 3) Provisioning Flow
1. Admin calls provisioning endpoint in `pos-security-service` (e.g., `POST /users/provision`).
2. Security service creates (or resolves idempotently) the `User`.
3. Security service publishes `UserCreated` (or `UserProvisioned`) event via transactional outbox.
4. People service consumes the event and creates/ensures the `UserPersonLink` idempotently.

### 4) Partial Failure Behavior
- If user creation succeeds but link creation fails:
  - Do **not** delete/deactivate the user automatically.
  - Use retry + DLQ + alerting for operational remediation.
  - Optional: represent an “unlinked” status if the model supports it, but not required.

## Data Requirements
### Event payload (minimum)
- `userId`
- `personId`
- `tenantId` (if applicable)
- `correlationId`
- `occurredAt`
- `eventVersion`

### Link idempotency
- Enforce uniqueness via DB constraint on `userId` (if strictly 1:1) or `(userId, personId)`.

## Acceptance Criteria
- **AC1:** Provisioning endpoint creates (or resolves) user and returns success without waiting for link completion.
- **AC2:** `UserCreated/UserProvisioned` is published exactly-once effectively (outbox + idempotent consumer).
- **AC3:** People service creates `UserPersonLink` idempotently from event.
- **AC4:** If linking fails, message retries; on exhaustion goes to DLQ; alert emitted; user remains.

## Audit & Observability
- Audit `USER_PROVISIONED` when user is created; include `adminId`, `userId`, `personId`, roles, timestamp, correlationId.
- Metrics for provisioning success/failure and link-consumer success/failure.

## Open Questions
None.

---

## Original Story (Unmodified – For Traceability)
(See original content in the issue history prior to this rewrite.)