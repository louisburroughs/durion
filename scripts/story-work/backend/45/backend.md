Title: [BACKEND] [STORY] Rules: Maintain Substitute Relationships and Equivalency Types
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/45
Labels: type:story, domain:workexec, status:needs-review, agent:workexec, layer:domain

## Backend Implementation for Story

**Original Story**: [STORY] Rules: Maintain Substitute Relationships and Equivalency Types

**Primary Domain:** `domain:workexec` (Work Order Execution owns runtime behavior for substitution usage; Product/Parts owns catalog data)

### Story Description
As a **Product Admin**, I want substitute relationships so that WorkExec can suggest alternatives when items are unavailable. Substitute relationships must be queryable, auditable, and enforceable (automatic suggestion vs. require approval).

### WorkExec Implementation Details (authored by workexec-domain-agent)

#### Data Model
- `SubstituteLink` (table: `substitute_link`)
  - `id` (UUID, PK)
  - `part_id` (FK -> part.part_id) NOT NULL
  - `substitute_part_id` (FK -> part.part_id) NOT NULL
  - `substitute_type` (ENUM: `EQUIVALENT`, `APPROVED_ALTERNATIVE`, `UPGRADE`, `DOWNGRADE`) NOT NULL
  - `is_auto_suggest` (boolean) default false
  - `priority` (int) default 100  -- lower is preferred
  - `effective_from` (timestamp) NULL
  - `effective_to` (timestamp) NULL
  - `is_active` (boolean) default true
  - `created_by`, `created_at`, `updated_by`, `updated_at`
  - `version` (optimistic-locking integer)
  - Unique constraint: `(part_id, substitute_part_id)`

- `SubstituteType` is an enum/lookup; can be represented in code and DB as enum or small reference table.

- `SubstituteAudit` (immutable audit log)
  - `audit_id`, `link_id`, `operation` (CREATE/UPDATE/DELETE), `payload_before`, `payload_after`, `actor_id`, `timestamp`, `correlation_id`

**Notes:** store `part` references as IDs (no denormalized product blobs). Keep financial or price-sensitive decisions out of substitution logic.

#### REST API (WorkExec-controlled endpoints)
- `POST /api/v1/substitutes`
  - Request: {"partId","substitutePartId","substituteType","isAutoSuggest", "priority", "effectiveFrom?","effectiveTo?"}
  - Response: 201 Created with created resource JSON
  - Permissions: `ROLE_PRODUCT_ADMIN` or `ROLE_INVENTORY_ADMIN`
  - Idempotency: accept `Idempotency-Key` header; duplicate requests return existing resource

- `PUT /api/v1/substitutes/{id}`
  - Request: fields to update (optimistic `version` required)
  - Response: 200 OK (or 409 on version conflict)

- `GET /api/v1/parts/{partId}/substitutes?availableOnly=true&locationId=...&limit=10`
  - Response: list of substitute candidates with availability metadata: `{substitutePartId, substituteType, priority, availableQuantityByLocation[], leadTimeDays}`
  - Support query param `availableOnly` to apply inventory filter server-side

- `DELETE /api/v1/substitutes/{id}`
  - Soft-delete (`is_active=false`) and record audit

- `GET /api/v1/substitutes/{id}` for detail

#### Consumption API / Helper (WorkExec usage)
- `POST /api/v1/workorders/{workOrderId}/suggest-substitutes` (idempotent)
  - Accepts payload listing candidate substituted parts and flags which to apply automatically.
  - WorkExec uses `GET /api/v1/parts/{partId}/substitutes` internally to build suggestions.

#### Behavior: Automatic vs Approval-required
- If `is_auto_suggest=true` and a candidate is available at the servicing location and meets policy, WorkExec may automatically apply the substitution when configured (e.g., low-impact consumables). Automatic application must be configurable per shop and opt-in.
- If `is_auto_suggest=false`, or policy requires customer consent (e.g., price/brand change), WorkExec must mark the WorkOrder with a `SubstitutionPendingApproval` flag or create a task for Service Advisor; no automatic changes to billed items occur until explicit approval.
- Permission model: only authorized roles may approve substitutions; approvals and rejections must be auditable and create events.

#### Ranking / Availability Rules
- Ranking heuristic (applied server-side when producing candidates):
  1. In-stock at servicing location (highest)
  2. In-stock at nearby locations (distance/transfer time)
  3. Supplier lead time
  4. `priority` field in `SubstituteLink`
  5. SubstituteType preference (e.g., `EQUIVALENT` > `APPROVED_ALTERNATIVE` > `UPGRADE` > `DOWNGRADE`)
- Provide per-candidate metadata: `availableQuantityByLocation`, `estimatedTransferTimeHours`, `leadTimeDays`, `priceDeltaMinor`.

#### Indexing & Queries
- DB indexes:
  - `idx_sub_link_partid_active_priority (part_id, is_active, priority)`
  - `idx_sub_link_substitute_partid (substitute_part_id)`
  - `idx_sub_link_effective (effective_from, effective_to)` if time-limited
- Provide a denormalized cache/materialized view per location for fast candidate lookup, refreshed asynchronously (recommended) or maintained in a distributed cache (Redis) keyed by `partId:locationId`.

#### Concurrency, Idempotency & Transaction Boundaries
- Use optimistic locking (`version`) on `SubstituteLink` updates; return 409 on conflict with guidance to retry.
- `POST /substitutes` support `Idempotency-Key` header to make create idempotent.
- All create/update audit writes should be in the same DB transaction as the primary write (audit row inserted in same transaction) to maintain consistency.
- For bulk apply operations (applying a substitution to a WorkOrder), use a single transactional operation in WorkExec to ensure WorkOrder item replacement and billing impacts are atomic.

#### Error Handling
- On unique constraint violation for duplicate `(part_id, substitute_part_id)`, return 409 with existing resource link.
- Validation errors return 400.
- Operational errors: 500 with correlation id for tracing.

#### Auditing & Observability
- Every create/update/delete of `SubstituteLink` must write an immutable audit entry (`SubstituteAudit`) with before/after JSON and actor.
- Emit metric counters: `substitute.links.created`, `substitute.links.updated`, `substitute.suggestions.generated`, `substitute.applied`, `substitute.apply.failures`.
- Emit tracing spans when building suggestions and when applying a substitution to a WorkOrder (attach `workOrderId`, `partId`, `substitutePartId`, `actorId`).

#### Tests & Acceptance Criteria (Given/When/Then)
- Scenario: Create a substitute link
  - Given Product Admin credentials
  - When they POST a valid substitute link
  - Then the link is created, an audit entry is recorded, and response 201 is returned

- Scenario: Query substitutes and rank by availability
  - Given part P has substitutes S1 (in-stock), S2 (out-of-stock, short lead), S3 (in-stock but low priority)
  - When WorkExec requests substitutes for P at location L
  - Then response order: S1, S3, S2 and each candidate includes availability metadata

- Scenario: Auto-apply substitute
  - Given `is_auto_suggest=true` for candidate and policy allows auto-apply
  - When WorkExec executes auto-apply flow for a WorkOrder
  - Then WorkOrder line is replaced atomically and audit/event is created

- Scenario: Approval required
  - Given `is_auto_suggest=false`
  - When candidate is suggested
  - Then WorkOrder is flagged for Service Advisor approval and no billing change occurs until approval

- Scenario: Concurrency conflict
  - Given two clients update the same `SubstituteLink` concurrently
  - When second commit attempts with stale `version`
  - Then API returns 409 and no data lost; audit shows first update

### Implementation Guidance / Files to Add
- JPA entity `SubstituteLink` + `SubstituteAudit`
- `SubstituteRepository` (Spring Data JPA) with queries `findByPartIdAndIsActiveOrderByPriority` and reverse index methods
- `SubstituteService` encapsulating ranking/availability logic (injected inventory client)
- `SubstituteController` exposing above endpoints with request validation and permission checks
- Integration tests mocking inventory availability service and exercising ranking and transactional apply

---

*WorkExec domain agent note:* This output follows `durion/.github/agents/domains/workexec-domain.agent.md` and the agent’s story validation checklist. If any of the following are not acceptable, please open a clarification issue: reversible state rules for substitution application, payroll/billing side effects, or explicit rollback semantics for applied substitutions.
