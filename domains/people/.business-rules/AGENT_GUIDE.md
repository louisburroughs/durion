# AGENT_GUIDE.md

## Summary

This guide defines the People domain’s **normative** business rules for identity/lifecycle, roles and assignments, and timekeeping-oriented People workflows (breaks, approvals, payroll timekeeping ingestion). It resolves the prior open questions into safe defaults that keep UI and backend behavior deterministic without inventing cross-domain policy. Each decision is indexed and cross-referenced to `PEOPLE_DOMAIN_NOTES.md`.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `PEOPLE_DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-PEOPLE-001 | User lifecycle states and soft offboarding |
| DECISION-PEOPLE-002 | Disable user workflow uses saga + DLQ |
| DECISION-PEOPLE-003 | Role assignment scopes (GLOBAL vs LOCATION) |
| DECISION-PEOPLE-004 | Person-location assignment primary semantics |
| DECISION-PEOPLE-005 | TimekeepingEntry ingestion and deduplication |
| DECISION-PEOPLE-006 | Time period approval atomicity |
| DECISION-PEOPLE-007 | Break type enum and auto-end behavior |
| DECISION-PEOPLE-008 | Employee profile conflict handling (409 vs warnings) |
| DECISION-PEOPLE-009 | Mechanic roster is an HR-synced read model |
| DECISION-PEOPLE-010 | Time entry approval authorization |
| DECISION-PEOPLE-011 | Mechanic roster storage ownership (SoR vs read model) |
| DECISION-PEOPLE-012 | Person↔User cardinality and identifiers |
| DECISION-PEOPLE-013 | Permission naming and UI capability exposure |
| DECISION-PEOPLE-014 | Assignment effective-dating semantics (exclusive end) |
| DECISION-PEOPLE-015 | Timezone display standard for People UIs |
| DECISION-PEOPLE-016 | Break notes requirement for OTHER |
| DECISION-PEOPLE-017 | Optimistic concurrency default (lastUpdatedStamp) |
| DECISION-PEOPLE-018 | Error response schema (400/409) |
| DECISION-PEOPLE-019 | `tenantId` UI visibility policy |
| DECISION-PEOPLE-020 | `technicianIds` query encoding + report range |
| DECISION-PEOPLE-021 | People REST API conventions (paths, paging, shapes) |
| DECISION-PEOPLE-022 | Break API contract and identity derivation |
| DECISION-PEOPLE-023 | Person-location assignment API contract and reason codes |
| DECISION-PEOPLE-024 | Disable user API contract and UX defaults |
| DECISION-PEOPLE-025 | Employee profile defaults and terminated edit rules |
| DECISION-PEOPLE-026 | Role assignment API contract, dating, and history defaults |

## Domain Boundaries

### What People owns (system of record)

- Identity lifecycle: `User`/`Person` status and offboarding semantics
- Employee profile master data and uniqueness rules
- Role definitions and role assignments (scope-aware)
- Person↔Location assignments (effective-dated, primary semantics)
- Payroll timekeeping ingestion read model (`TimekeepingEntry`) and approval workflow state

### What People does *not* own

- Authentication/session enforcement (owned by Security; People provides status)
- Work session/job time source-of-truth (owned by Shop Management / Work Execution)
- Dispatch decisions and assignment workflows (consumers of People data)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| User | Login identity and lifecycle status used for authentication eligibility. |
| Person | Human/employee identity linked to a User (canonical person identifier). |
| EmployeeProfile | HR-like profile data and employment status. |
| Role / RoleAssignment | Permission-bearing role and effective-dated scoped assignment. |
| PersonLocationAssignment | Effective-dated home/location association with primary semantics. |
| TimekeepingEntry | Read model of finalized work sessions for payroll review and approvals. |
| TimePeriod / TimePeriodApproval | Pay period container and append-only approval history. |
| Break | Break lifecycle record; constrained to one active break at a time. |
| Mechanic / MechanicSkill (read model) | Roster data consumed by Dispatch/ShopMgr (HR-synced projection). |

## Invariants / Business Rules

- User lifecycle is `ACTIVE`, `DISABLED` (reversible), `TERMINATED` (irreversible); authentication is blocked when not ACTIVE. (Decision ID: DECISION-PEOPLE-001)
- Disable user is atomic locally then propagated asynchronously via saga; downstream failures do not roll back disable. (Decision ID: DECISION-PEOPLE-002)
- Role assignments are scope-aware (`GLOBAL`/`LOCATION`) and effective-dated; mutations prefer end+recreate over in-place edits. (Decision ID: DECISION-PEOPLE-003)
- Person-location assignments are effective-dated with a single primary “home” assignment per person at a time; primary promotion demotes prior primary atomically. (Decision ID: DECISION-PEOPLE-004)
- TimekeepingEntry ingestion is idempotent by stable source key; no duplicate UI rows. (Decision ID: DECISION-PEOPLE-005)
- Time approvals are period-atomic and append-only; approved entries are immutable. (Decision ID: DECISION-PEOPLE-006)
- Exactly one active break at a time; breaks auto-end at clock-out with audit flags. (Decision ID: DECISION-PEOPLE-007)

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-PEOPLE-001 | User lifecycle model | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-001---user-lifecycle-states-and-soft-offboarding) |
| DECISION-PEOPLE-002 | Disable saga + DLQ | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-002---user-disable-workflow-with-saga-pattern) |
| DECISION-PEOPLE-003 | Role scope model | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-003---role-assignment-scopes-global-vs-location) |
| DECISION-PEOPLE-004 | Primary assignment semantics | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-004---person-location-assignment-primary-flag-semantics) |
| DECISION-PEOPLE-005 | Idempotent ingestion | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-005---timekeeping-entry-ingestion-and-deduplication) |
| DECISION-PEOPLE-006 | Period-atomic approvals | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-006---time-period-approval-atomicity) |
| DECISION-PEOPLE-007 | Break types + auto-end | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-007---break-type-enumeration-and-auto-end-flag) |
| DECISION-PEOPLE-008 | Employee profile conflicts | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-008---employee-profile-conflict-handling-409-vs-warnings) |
| DECISION-PEOPLE-009 | Mechanic roster read model | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-009---mechanic-roster-as-read-model-synced-from-hr) |
| DECISION-PEOPLE-010 | Approval authorization | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-010---time-entry-approval-authorization) |
| DECISION-PEOPLE-011 | Roster storage ownership | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-011---mechanic-roster-storage-ownership) |
| DECISION-PEOPLE-012 | Person↔User cardinality | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-012---person-user-cardinality-and-identifiers) |
| DECISION-PEOPLE-013 | Permission naming + capability exposure | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-013---permission-naming-and-ui-capability-exposure) |
| DECISION-PEOPLE-014 | Effective dating semantics | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-014---assignment-effective-dating-semantics-exclusive-end) |
| DECISION-PEOPLE-015 | UI timezone standard | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-015---timezone-display-standard-for-people-uis) |
| DECISION-PEOPLE-016 | Break notes for OTHER | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-016---break-notes-requirement-for-other) |
| DECISION-PEOPLE-017 | Optimistic concurrency | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-017---optimistic-concurrency-default-lastupdatedstamp) |
| DECISION-PEOPLE-018 | Error schema | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-018---error-response-schema-400409) |
| DECISION-PEOPLE-019 | tenantId visibility | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-019---tenantid-ui-visibility-policy) |
| DECISION-PEOPLE-020 | technicianIds encoding | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-020---technicianids-query-encoding-and-report-range) |
| DECISION-PEOPLE-021 | REST API conventions | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-021---people-rest-api-conventions-paths-paging-shapes) |
| DECISION-PEOPLE-022 | Break contract | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-022---break-api-contract-and-identity-derivation) |
| DECISION-PEOPLE-023 | Assignment contract | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-023---person-location-assignment-api-contract-and-reason-codes) |
| DECISION-PEOPLE-024 | Disable user contract | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-024---disable-user-api-contract-and-ux-defaults) |
| DECISION-PEOPLE-025 | Employee profile defaults | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-025---employee-profile-defaults-and-terminated-edit-rules) |
| DECISION-PEOPLE-026 | Role assignment contract | [PEOPLE_DOMAIN_NOTES.md](PEOPLE_DOMAIN_NOTES.md#decision-people-026---role-assignment-api-contract-dating-and-history-defaults) |

## Open Questions (from source)

### Q: Whether the `Mechanic` roster read model lives in People domain storage or in Shop/Dispatch domain storage?

- Answer: People is the SoR for Person identity and employment status; the Mechanic roster is a **read model** that may live in the consuming domain (Dispatch/ShopMgr) and is populated from People/HR events or People APIs.
- Assumptions:
  - Consumers need local query performance for roster operations.
- Rationale:
  - Keeps system-of-record boundaries clear and avoids duplicated write authority.
- Impact:
  - Define roster sync contract (events or pull) and stable person identifier.
- Decision ID: DECISION-PEOPLE-011

### Q: Is multiple Persons per User allowed?

- Answer: Safe default is 1:1 `User`→`Person` for Durion POS; do not model multiple persons per login identity in v1.
- Assumptions:
  - Shared logins are disallowed; each employee has a unique identity.
- Rationale:
  - Simplifies auditability and access control.
- Impact:
  - UI routes and APIs use `personId` as the stable human identifier.
- Decision ID: DECISION-PEOPLE-012

### Q: For disable user, what is the permission string and how does UI discover allowed options?

- Answer: Use named permissions and backend-provided capability flags; safe defaults: `people:user.disable` for disable action and a response payload that includes allowed disable options (or omits options not permitted).
- Assumptions:
  - UI must not infer policy (assignment termination options) locally.
- Rationale:
  - Prevents drift between UI gating and server enforcement.
- Impact:
  - Backend returns `canDisableUser` and `disableOptions[]` (or equivalent).
- Decision ID: DECISION-PEOPLE-013

### Q: PersonLocationAssignment primary uniqueness scope and effective end inclusivity?

- Answer: Enforce one primary assignment per person overall (home location). Use half-open intervals: `effectiveStartAt` inclusive, `effectiveEndAt` exclusive.
- Assumptions:
  - “Primary” maps to payroll/home-location concepts.
- Rationale:
  - Half-open intervals avoid overlap ambiguity and simplify validation.
- Impact:
  - UI “active” display uses `now >= start && now < end`.
- Decision ID: DECISION-PEOPLE-004, DECISION-PEOPLE-014

### Q: Is `role` required on PersonLocationAssignment and what are allowed values?

- Answer: `role` is optional for the assignment record in v1; when present it is an enum controlled by People domain and presented via a picker.
- Assumptions:
  - Some consumers may want “assignment type” without making it authorization-bearing.
- Rationale:
  - Avoids coupling location assignment to RBAC (RBAC lives in RoleAssignment).
- Impact:
  - If role is introduced later, migration can backfill `role=null`.
- Decision ID: DECISION-PEOPLE-004

### Q: Are assignments editable after creation?

- Answer: Safe default is end+create (immutable core fields) for assignments and role assignments; allow only comments/metadata edits if needed.
- Assumptions:
  - Auditability is a requirement.
- Rationale:
  - Reduces historical ambiguity.
- Impact:
  - UI uses “End assignment” then “Create new assignment”.
- Decision ID: DECISION-PEOPLE-003, DECISION-PEOPLE-014

### Q: For `breakType=OTHER`, are `notes` required and what timezone is used for displaying timekeeping/break timestamps?

- Answer: Notes are optional but recommended for OTHER; UI displays times in user profile timezone when available, otherwise falls back to location timezone.
- Assumptions:
  - Backend stores instants in UTC.
- Rationale:
  - Keeps UX consistent while preserving correct instants.
- Impact:
  - UI must show timezone context (tooltip or label) where ambiguity exists.
- Decision ID: DECISION-PEOPLE-016, DECISION-PEOPLE-015

### Q: Do employee profile and assignments require optimistic concurrency (`lastUpdatedStamp`/version)?

- Answer: Default to optimistic concurrency when the backend exposes a version field (prefer `lastUpdatedStamp`); require clients to send it on update.
- Assumptions:
  - Multiple admins can edit profiles/assignments.
- Rationale:
  - Prevents silent lost updates.
- Impact:
  - UI includes version in submit payload and handles 409 by refresh.
- Decision ID: DECISION-PEOPLE-017

### Q: What is the standard error response format for 400/409, and how should `technicianIds` be encoded for the discrepancy report?

- Answer: Require `errorCode` + `message` with optional `fieldErrors` and `blockingIds` for 409; encode `technicianIds` as repeated query params, with server accepting comma-separated as compatibility.
- Assumptions:
  - UI needs deterministic mapping.
- Rationale:
  - Avoids fragile string parsing.
- Impact:
  - Contract tests should enforce schema stability.
- Decision ID: DECISION-PEOPLE-018, DECISION-PEOPLE-020

### Q: Is `tenantId` considered sensitive in UI?

- Answer: Treat `tenantId` as sensitive; do not display by default, and only show it to admin/support roles if explicitly approved.
- Assumptions:
  - Multi-tenant identifiers can leak internal structure.
- Rationale:
  - Conservative minimization of internal identifiers.
- Impact:
  - UI uses personId/employeeId as primary identifiers.
- Decision ID: DECISION-PEOPLE-019

### Q: What are the default People endpoints/services for timekeeping entry list/detail and discrepancy report?

- Answer: Default to REST endpoints under `/rest/api/v1/people` with paging and stable error schema; story implementers must document the final service names/paths in module docs.
- Assumptions:
  - Moqui REST exposure follows the platform’s `/rest/api/v{version}` convention.
- Rationale:
  - Allows UI implementation to proceed while preserving a single place to finalize naming.
- Impact:
  - Contract tests validate query params and response schema stability.
- Decision ID: DECISION-PEOPLE-021

### Q: What are the break endpoints and must the UI pass timecard/session identifiers?

- Answer: Break operations derive identity and current session from the authenticated user; UI should not pass timecard/session identifiers unless backend explicitly requires it.
- Assumptions:
  - Backend can locate the active clock-in/session for the user.
- Rationale:
  - Avoids fragile client state and prevents spoofing.
- Impact:
  - UI refreshes “current context” after start/end and treats 409 as refresh-required.
- Decision ID: DECISION-PEOPLE-022

### Q: What are the person-location assignment endpoints and are reason codes required?

- Answer: Provide list/create/end endpoints under People REST conventions; reason codes are optional in v1 and can be introduced later via a reference endpoint.
- Assumptions:
  - Audit log captures actor/time even if reason omitted.
- Rationale:
  - Keeps flows shippable while enabling later policy hardening.
- Impact:
  - UI can optionally collect reason text without blocking.
- Decision ID: DECISION-PEOPLE-023

### Q: What are disable user UX defaults (confirmation, navigation) and how are options communicated?

- Answer: Require explicit confirmation (modal confirm) and optionally a reason; remain on refreshed detail after success. Backend communicates options via capability fields.
- Assumptions:
  - Confirm-by-typing is not required by default.
- Rationale:
  - Minimizes UX friction while keeping the action deliberate.
- Impact:
  - UI relies on server-provided `disableOptions`.
- Decision ID: DECISION-PEOPLE-024

### Q: What is the default employee status on create and can terminated employees be edited?

- Answer: Default status on create is `ACTIVE`. Terminated employees are read-only by default; limited edits (contact info) require explicit HR admin permission.
- Assumptions:
  - Payroll/audit integrity requires stability post-termination.
- Rationale:
  - Avoids altering historical payroll identity.
- Impact:
  - UI disables editing for terminated unless capability says otherwise.
- Decision ID: DECISION-PEOPLE-025

### Q: Should role assignment lists default to active-only, and are backdated/future ends allowed?

- Answer: Default to active-only with an explicit `includeHistory=true` option. Allow future-dated ends; allow backdated ends only when they do not contradict already-audited actions (server-enforced).
- Assumptions:
  - Backend enforces constraints and returns 409 when invalid.
- Rationale:
  - Balances UX simplicity with admin flexibility.
- Impact:
  - UI includes history toggle and handles 409 conflicts.
- Decision ID: DECISION-PEOPLE-026

## Todos Reconciled

- Original todo: "Define event name/version for TimekeepingEntry created/updated." → Resolution: Replace with task: `TASK-PEOPLE-001` (define event contract and versioning).
- Original todo: "Document canonical permission names once confirmed." → Resolution: Resolved (safe default naming) + Replace with task: `TASK-PEOPLE-002` (align with actual repo permission catalog).
- Original todo: "Confirm standard header names for correlation/trace." → Resolution: Replace with task: `TASK-PEOPLE-003` (confirm header standard and document in module docs).
- Original todo: "Define default sort for lists." → Resolution: Replace with task: `TASK-PEOPLE-004` (set stable backend sort + UI defaults).

## End

End of document.
