# Shop Management Domain - Open Questions & Phase Implementation Plan

**Created:** 2026-01-25  
**Status:** Phase Planning  
**Scope:** Unblock ALL shop management domain issues with `blocked:clarification` status through systematic backend contract discovery and GitHub issue resolution

---

## Executive Summary

This document addresses **1 unresolved shop management domain issue** with `blocked:clarification` status. The objective is to systematically resolve all blocking questions through backend contract research and communicate resolutions via GitHub issue comments in `durion-moqui-frontend`, enabling implementation to proceed.

**Coverage Status:**
- ⏳ **This Document:** Issue #76 (1 issue)
- 🎯 **Target Domain:** Shop Management (Scheduling/Appointments)
- 📊 **Blocking Questions:** Estimated 45+ questions to resolve

**Critical Note:** Issue #76 (Appointment: Create Appointment from Estimate or Order) involves extensive cross-domain coordination with Location (operating hours), People (mechanic profiles), and Notification domains. The story references **17 DECISION-SHOPMGMT-* documents** that must be consulted to understand authoritative domain boundaries and policy enforcement patterns.

---

## Scope (Unresolved Issues)

### Issue #76 — Appointment: Create Appointment from Estimate or Order
- **Status:** `blocked:clarification`, `domain:shopmgmt`
- **Primary Persona:** Scheduler (Service Advisor / Dispatcher)
- **Value:** Server-authoritative appointment creation with conflict detection, operating hours enforcement, and idempotent submission
- **Blocking:** Backend API contracts, Moqui screen paths, permission identifiers, facility scoping requirements, conflict handling patterns, timezone semantics

---

## Phased Plan

### Phase 1 – Contract & Ownership Confirmation

**Objective:** Identify authoritative services, endpoints, domain boundaries, and Moqui screen integration patterns

**Tasks:**
- [x] **Task 1.1 — Domain ownership and boundaries (CRITICAL)** ✅ COMPLETE
  - [x] Confirm shopmgmt domain owns appointment creation, eligibility checks, conflict detection/classification, and operating-hours enforcement per DECISION-SHOPMGMT-001/002/008/017
  - [x] Confirm Location domain owns operating hours (consumed by shopmgmt) per DECISION-SHOPMGMT-008
  - [x] Confirm People domain owns mechanic profile SoR; shopmgmt may expose minimal derived mechanic display fields but UI must not require People calls per DECISION-SHOPMGMT-009
  - [x] Confirm Notification domain owns delivery; shopmgmt may return outcome summary per DECISION-SHOPMGMT-016
  - [x] Document cross-domain integration points and read-only vs write boundaries
  
- [x] **Task 1.2 — REST endpoint/service mapping (Appointment Creation)** ✅ COMPLETE
  - [x] Identify base path for shopmgmt appointments API (confirmed: `POST /v1/shop-manager/appointments`)
  - [x] Identify "load create model from source" endpoint (deferred to implementation phase)
  - [x] Identify "create appointment from source" endpoint:
    - [x] Path confirmed: `POST /v1/shop-manager/appointments`
    - [x] Request payload: `AppointmentCreateRequest` with `facilityId`, `sourceType`, `sourceId`, `scheduledStartDateTime`, `scheduledEndDateTime`, optional `overrideSoftConflicts`/`overrideReason`
    - [x] Success response: `AppointmentResponse` with `appointmentId`, `appointmentStatus`, `scheduledStartDateTime`, `scheduledEndDateTime`, `facilityTimeZoneId`
    - [x] Conflict response (409): ConflictResponse structure documented (implementation pending)
  - [x] Confirm submit-time conflict detection pattern (documented; implementation pending)
  - [x] Confirm idempotency requirement: `Idempotency-Key` header implemented

- [x] **Task 1.3 — Moqui screen/service mapping** ✅ COMPLETE
  - [x] Confirm Moqui screen paths for component:
    - [x] AppointmentCreate.xml located
    - [x] AppointmentEdit.xml located
    - [x] AppointmentFind.xml located
    - [x] AppointmentDelete.xml located
  - [x] Confirm Moqui transitions (implementation pending in screens)
  - [x] Confirm menu wiring (implementation pending)
  - [x] Confirm deep link patterns: `/shopmgr/appointment/create?sourceType={ESTIMATE|WORKORDER}&sourceId={id}`

- [x] **Task 1.4 — Error envelope and correlation patterns** ✅ COMPLETE
  - [x] Confirm standard error shape for shopmgmt domain: `ErrorResponse` with `errorCode`, `message`, `correlationId`, `timestamp`, `fieldErrors`
  - [x] Document eligibility error codes (implementation pending)
  - [x] Document conflict error codes (implementation pending)
  - [x] Document validation error codes (implementation pending)
  - [x] Document permission error codes (implementation pending)
  - [x] Verify correlation ID propagation: `X-Correlation-Id` header implemented in AppointmentsController

**Acceptance:** ✅ COMPLETE — Domain boundaries documented in Durion-Processing-ShopMgmt-Phase1.md; Issue #76 updated with backend contracts (pos-shop-manager module); Moqui screen paths confirmed; error envelope and correlation ID patterns verified

---

### Phase 2 – Data & Dependency Contracts

**Objective:** Resolve entity schemas, ID types, status enums, conflict classification, and cross-domain dependencies

**Tasks:**
- [ ] **Task 2.1 — Appointment create model structure (read model)**
  - [ ] Confirm `AppointmentCreateModel` fields:
    - [ ] `sourceType` (enum: `ESTIMATE` | `WORK_ORDER`) required
    - [ ] `sourceId` (UUID vs opaque string) required
    - [ ] `facilityId` (UUID vs opaque string) required
    - [ ] `facilityTimeZoneId` (IANA TZ string, e.g., `America/New_York`) required per DECISION-SHOPMGMT-015
    - [ ] `sourceStatus` (string) required
    - [ ] `isEligible` (boolean) required
    - [ ] `ineligibilityCode` (string) optional (e.g., `ESTIMATE_NOT_ELIGIBLE`)
    - [ ] `ineligibilityMessage` (string, safe) optional
    - [ ] `defaults` (object) optional: `suggestedDurationMinutes`, `requiredSkills[]`, `preferredSkills[]`, `serviceSummary` (PII-safe)
    - [ ] `existingAppointmentId` (UUID vs opaque string) optional (permission-gated server-side?)
  - [ ] Confirm PII-safety requirements for `serviceSummary` and other display fields per DECISION-SHOPMGMT-017

- [ ] **Task 2.2 — Create appointment request structure**
  - [ ] Confirm `CreateAppointmentRequest` fields:
    - [ ] `facilityId` (UUID vs opaque string) **required** per DECISION-SHOPMGMT-012
    - [ ] `sourceType` (enum string) **required**
    - [ ] `sourceId` (UUID vs opaque string) **required**
    - [ ] `scheduledStartDateTime` (string, ISO-8601 with offset) **required** per DECISION-SHOPMGMT-015
    - [ ] `clientRequestId` (UUID recommended) **required** per DECISION-SHOPMGMT-014
    - [ ] `overrideSoftConflicts` (boolean) optional, default false
    - [ ] `overrideReason` (string) conditionally required when `overrideSoftConflicts=true` per DECISION-SHOPMGMT-007
  - [ ] Confirm idempotency key location: header (`Idempotency-Key`) or body field (`clientRequestId`)?
  - [ ] Confirm whether `facilityId` is also required for the **load** call or only for writes per DECISION-SHOPMGMT-012

- [ ] **Task 2.3 — Create appointment response structure (success)**
  - [ ] Confirm `CreateAppointmentResponse` fields:
    - [ ] `appointmentId` (UUID vs opaque string) required
    - [ ] `appointmentStatus` (opaque string) required per DECISION-SHOPMGMT-013
    - [ ] `scheduledStartDateTime` (ISO-8601 with offset) required
    - [ ] `facilityTimeZoneId` (IANA TZ string) required per DECISION-SHOPMGMT-015
    - [ ] `estimateId` (UUID vs opaque string) optional (when `sourceType=ESTIMATE`)
    - [ ] `workOrderId` (UUID vs opaque string) optional (when `sourceType=WORK_ORDER`)
    - [ ] `notificationOutcomeSummary` (string) optional per DECISION-SHOPMGMT-016
  - [ ] Confirm appointment status enum values (if known) or treat as opaque per DECISION-SHOPMGMT-013

- [ ] **Task 2.4 — Conflict response structure (409)**
  - [ ] Confirm `ConflictResponse` fields:
    - [ ] `errorCode` = `SCHEDULING_CONFLICT` (string) required per DECISION-SHOPMGMT-011
    - [ ] `conflicts[]` required:
      - [ ] `severity` (enum: `HARD` | `SOFT`) required per DECISION-SHOPMGMT-002
      - [ ] `code` (string) required
      - [ ] `message` (string, safe) required
      - [ ] `overridable` (boolean) required
    - [ ] `suggestedAlternatives[]` optional per DECISION-SHOPMGMT-011:
      - [ ] `startDateTime` (ISO-8601 with offset) required
      - [ ] `endDateTime` (ISO-8601 with offset) required
  - [ ] Confirm HARD conflict semantics: **not overridable** per DECISION-SHOPMGMT-002
  - [ ] Confirm SOFT conflict semantics: **overridable with permission + reason + audit** per DECISION-SHOPMGMT-007
  - [ ] Confirm operating hours violations are presented as HARD conflicts per DECISION-SHOPMGMT-008

- [ ] **Task 2.5 — Source eligibility rules**
  - [ ] Confirm estimate eligibility: status in `APPROVED` or `QUOTED` per DECISION-SHOPMGMT-013
  - [ ] Confirm work order eligibility: status NOT in `COMPLETED` or `CANCELLED` per DECISION-SHOPMGMT-013
  - [ ] Confirm error codes for ineligible sources:
    - [ ] `ESTIMATE_NOT_ELIGIBLE` (422)
    - [ ] `WORK_ORDER_NOT_ELIGIBLE` (422)
  - [ ] Confirm whether backend returns `existingAppointmentId` when source is already linked (permission-gated?)

- [ ] **Task 2.6 — Identifier types and immutability**
  - [ ] Confirm `appointmentId`, `sourceId`, `facilityId`, `estimateId`, `workOrderId` types (UUID vs opaque string)
  - [ ] Treat all IDs as opaque; no client-side validation beyond presence

- [ ] **Task 2.7 — Timezone and timestamp handling**
  - [ ] Confirm timestamp format: ISO-8601 with offset per DECISION-SHOPMGMT-015
  - [ ] Confirm display timezone: facility timezone using `facilityTimeZoneId` per DECISION-SHOPMGMT-015
  - [ ] Confirm input semantics: UI collects date/time in facility timezone, converts to ISO-8601 with offset before submit
  - [ ] Confirm timezone source: backend provides `facilityTimeZoneId` in create model per DECISION-SHOPMGMT-015

- [ ] **Task 2.8 — Facility scoping requirements**
  - [ ] Confirm facility scoping: all write requests include `facilityId` explicitly per DECISION-SHOPMGMT-012
  - [ ] Confirm whether `facilityId` is required for load (read) call or only for writes
  - [ ] Confirm 403/404 error handling: do not leak cross-facility existence per DECISION-SHOPMGMT-012

**Acceptance:** All entity schemas documented with field types, enums, and identifier examples; conflict classification clear; timezone contract resolved; facility scoping confirmed

---

### Phase 3 – UX/Validation Alignment

**Objective:** Confirm validation rules, conflict handling UI patterns, override permissions, and accessibility patterns

**Tasks:**
- [ ] **Task 3.1 — Source eligibility validation**
  - [ ] Client-side validation: none (eligibility is server-authoritative)
  - [ ] Server-side validation: estimate status must be `APPROVED` or `QUOTED`; work order status must NOT be `COMPLETED` or `CANCELLED`
  - [ ] Error codes: `ESTIMATE_NOT_ELIGIBLE` (422), `WORK_ORDER_NOT_ELIGIBLE` (422)
  - [ ] UI behavior: show eligibility banner; block submit; link back to source or to existing appointment if provided

- [ ] **Task 3.2 — Appointment create validation rules**
  - [ ] `scheduledStartDateTime` is required (client-side + server-side)
  - [ ] `scheduledStartDateTime` must be valid ISO-8601 with offset (server-enforced)
  - [ ] `facilityId` is required (client-side + server-side per DECISION-SHOPMGMT-012)
  - [ ] `clientRequestId` is required (client-side + server-side per DECISION-SHOPMGMT-014)
  - [ ] If `overrideSoftConflicts=true`, then `overrideReason` is required (non-empty) per DECISION-SHOPMGMT-007
  - [ ] Confirm validation error response: 400 with `fieldErrors[]`

- [ ] **Task 3.3 — Conflict handling UI patterns**
  - [ ] Conflict detection: submit-time only (409 on submit; no pre-check required) per DECISION-SHOPMGMT-011
  - [ ] HARD conflicts:
    - [ ] Block creation; no override action per DECISION-SHOPMGMT-002
    - [ ] Show conflict list with severity indicators
    - [ ] Show `suggestedAlternatives[]` when present per DECISION-SHOPMGMT-011
    - [ ] Keep user on create screen; allow changing `scheduledStartDateTime` and resubmit
  - [ ] SOFT conflicts:
    - [ ] If user lacks override permission: block with message "Manager override required"
    - [ ] If user has override permission: show override controls (required `overrideReason` input + "Override & Create" action) per DECISION-SHOPMGMT-007
    - [ ] Resubmit with `overrideSoftConflicts=true` and `overrideReason`
  - [ ] Confirm override permission identifier (exact permission string needed)

- [ ] **Task 3.4 — Operating hours enforcement**
  - [ ] Operating hours violations are presented as HARD conflicts per DECISION-SHOPMGMT-008
  - [ ] Error code: `OUTSIDE_OPERATING_HOURS` (as HARD conflict in 409 or as 422?)
  - [ ] UI behavior: show blocking conflict; render `suggestedAlternatives[]` if provided; no override action

- [ ] **Task 3.5 — Idempotency and retry patterns**
  - [ ] Generate `clientRequestId` once per user-initiated submit attempt per DECISION-SHOPMGMT-014
  - [ ] Persist `clientRequestId` in screen state for retries until success/final failure
  - [ ] On timeout/network error: offer "Retry" using the **same** `clientRequestId` per DECISION-SHOPMGMT-014
  - [ ] Backend must return original success response (same `appointmentId`) on idempotent retry
  - [ ] Confirm idempotency key location: header or body field?

- [ ] **Task 3.6 — Permission-gated UI behavior**
  - [ ] Required permissions (exact permission identifiers needed):
    - [ ] Create appointment: `CREATE_APPOINTMENT` (exact string?)
    - [ ] Override SOFT conflicts: `OVERRIDE_SCHEDULING_CONFLICT` (exact string?)
  - [ ] Confirm deny-by-default enforcement: 403 must not leak data existence per DECISION-SHOPMGMT-012
  - [ ] UI behavior without `CREATE_APPOINTMENT`: do not show "Create Appointment" action on source detail screens
  - [ ] UI behavior without `OVERRIDE_SCHEDULING_CONFLICT`: disable override action; show permission message

- [ ] **Task 3.7 — Error handling and correlation ID propagation**
  - [ ] Map HTTP codes to UX:
    - [ ] 200/201 success → navigate to Appointment Detail
    - [ ] 400 `VALIDATION_ERROR` → field errors with `correlationId`
    - [ ] 403 → access denied without leaking cross-facility existence per DECISION-SHOPMGMT-012
    - [ ] 404 → not found without leaking cross-facility existence per DECISION-SHOPMGMT-012
    - [ ] 409 `SCHEDULING_CONFLICT` → render conflicts and alternatives; gate override UI
    - [ ] 422 (eligibility/policy) → blocking message with `errorCode` and `correlationId`
    - [ ] 5xx → generic failure with `correlationId` and retry option
  - [ ] Confirm `correlationId` is surfaced in all error banners (user-visible)
  - [ ] Confirm `X-Correlation-Id` header propagation per DECISION-SHOPMGMT-011
  - [ ] Confirm free-text `overrideReason` must NOT be logged client-side per DECISION-SHOPMGMT-017

- [ ] **Task 3.8 — Accessibility and responsiveness**
  - [ ] Keyboard navigation: forms, date/time pickers, conflict lists, buttons
  - [ ] ARIA labels: inputs, buttons, error messages, loading states, conflict severity indicators
  - [ ] Error focus: move focus to first error field on validation failure
  - [ ] Responsive layout: usable on tablet widths and desktop
  - [ ] Date/time input: accessible picker supporting facility timezone per DECISION-SHOPMGMT-015

- [ ] **Task 3.9 — PII and sensitive data handling**
  - [ ] Confirm PII-safety requirements: do not display or log customer PII unless explicitly authorized per DECISION-SHOPMGMT-017
  - [ ] Confirm `overrideReason` must NOT be logged client-side per DECISION-SHOPMGMT-017
  - [ ] Confirm `serviceSummary` must be PII-safe (backend-enforced)
  - [ ] UI should display, when returned by backend:
    - [ ] `createdAt`/`createdBy` (if included)
    - [ ] Non-sensitive indicator that an override occurred (e.g., "Override applied") without echoing `overrideReason`

**Acceptance:** All validation rules, conflict handling patterns, override permissions, and error handling documented; accessibility requirements clear; PII/sensitive data policies confirmed

---

### Phase 4 – Issue Updates and Closure

**Objective:** Post comprehensive resolution comments to GitHub issue in `durion-moqui-frontend` and update labels

**Tasks:**
- [ ] **Task 4.1 — Issue #76 GitHub comment (Appointment Creation)**
  - [ ] Post clarification comment with:
    - [ ] **Domain boundaries:** Shopmgmt owns appointment creation; Location owns operating hours; People owns mechanic SoR; Notification owns delivery
    - [ ] Confirmed endpoints:
      - [ ] Load create model from source (path, query params, response structure)
      - [ ] Create appointment from source (path, request payload, success response, conflict response)
    - [ ] Moqui screen paths:
      - [ ] Estimate Detail screen path and action/transition
      - [ ] Work Order Detail screen path and action/transition
      - [ ] Appointment Create screen path (new)
      - [ ] Appointment Detail screen path (existing)
    - [ ] Entity structures:
      - [ ] `AppointmentCreateModel` (eligibility, defaults, facilityTimeZoneId)
      - [ ] `CreateAppointmentRequest` (facilityId, sourceType, sourceId, scheduledStartDateTime, clientRequestId, overrideSoftConflicts, overrideReason)
      - [ ] `CreateAppointmentResponse` (appointmentId, appointmentStatus, facilityTimeZoneId, notificationOutcomeSummary)
      - [ ] `ConflictResponse` (conflicts[], suggestedAlternatives[])
    - [ ] Permission gates:
      - [ ] `CREATE_APPOINTMENT` (exact string needed)
      - [ ] `OVERRIDE_SCHEDULING_CONFLICT` (exact string needed)
    - [ ] Eligibility rules: Estimate status `APPROVED`/`QUOTED`; Work Order NOT `COMPLETED`/`CANCELLED`
    - [ ] Conflict classification:
      - [ ] HARD conflicts: not overridable per DECISION-SHOPMGMT-002
      - [ ] SOFT conflicts: overridable with permission + reason + audit per DECISION-SHOPMGMT-007
      - [ ] Operating hours violations: HARD conflicts per DECISION-SHOPMGMT-008
    - [ ] Error codes:
      - [ ] 422: `ESTIMATE_NOT_ELIGIBLE`, `WORK_ORDER_NOT_ELIGIBLE`, `OUTSIDE_OPERATING_HOURS` (or HARD conflict)
      - [ ] 409: `SCHEDULING_CONFLICT` with `conflicts[]` and `suggestedAlternatives[]`
      - [ ] 400: `VALIDATION_ERROR` with `fieldErrors[]`
      - [ ] 403/404: access denied without leaking cross-facility existence per DECISION-SHOPMGMT-012
    - [ ] Timezone handling: ISO-8601 with offset; display in facility timezone using `facilityTimeZoneId` per DECISION-SHOPMGMT-015
    - [ ] Idempotency: `clientRequestId` required; retry with same ID per DECISION-SHOPMGMT-014
    - [ ] Facility scoping: `facilityId` required for writes per DECISION-SHOPMGMT-012; confirm if required for reads
    - [ ] PII/sensitive data: do not log `overrideReason` client-side; `serviceSummary` must be PII-safe per DECISION-SHOPMGMT-017
    - [ ] Correlation: `X-Correlation-Id` header per DECISION-SHOPMGMT-011
    - [ ] DECISION references: DECISION-SHOPMGMT-001, -002, -007, -008, -009, -011, -012, -013, -014, -015, -016, -017
    - [ ] Any remaining open questions with requested owner/domain
  - [ ] Update label: remove `blocked:clarification` when clarifications complete

- [ ] **Task 4.2 — Cross-cutting documentation updates**
  - [ ] Update `domains/shopmgmt/.business-rules/` with:
    - [ ] Appointment creation from source: eligibility rules, conflict classification (HARD/SOFT), override requirements
    - [ ] Conflict handling patterns: submit-time detection, suggested alternatives, HARD vs SOFT semantics
    - [ ] Facility scoping: `facilityId` required for writes; cross-facility access denied without leakage
    - [ ] Timezone handling: ISO-8601 with offset; display in facility timezone
    - [ ] Idempotency: `clientRequestId` pattern for retries
    - [ ] Permission model: `CREATE_APPOINTMENT`, `OVERRIDE_SCHEDULING_CONFLICT`
    - [ ] PII/sensitive data: do not log `overrideReason`; `serviceSummary` must be PII-safe
  - [ ] Update `domains/location/.business-rules/` (if cross-reference needed) with:
    - [ ] Clarify Location owns operating hours (consumed by shopmgmt) per DECISION-SHOPMGMT-008
  - [ ] Update `domains/people/.business-rules/` (if cross-reference needed) with:
    - [ ] Clarify People owns mechanic profile SoR; shopmgmt may expose derived fields per DECISION-SHOPMGMT-009

- [ ] **Task 4.3 — Verification and tracking**
  - [ ] Verify all DECISION-SHOPMGMT-* references (001, 002, 007, 008, 009, 011, 012, 013, 014, 015, 016, 017) are accurate and complete
  - [ ] Verify all blocking questions from story section 16 (Open Questions) are addressed
  - [ ] Create follow-up issues if any clarifications spawn new work items
  - [ ] Update this document's status to `Resolved` when all GitHub comments posted

**Acceptance:** GitHub issue #76 has comprehensive clarification comment; labels updated; remaining blockers documented

---

## Issue-Specific Blocking Questions

### Issue #76 — Appointment: Create Appointment from Estimate or Order

**Section 1: Domain Boundaries (CRITICAL PRIORITY)**
1. **Confirm shopmgmt domain owns:** Appointment creation, eligibility checks, conflict detection/classification, operating-hours enforcement (per DECISION-SHOPMGMT-001/002/008/017)
2. **Confirm Location domain owns:** Operating hours source of truth; consumed by shopmgmt (per DECISION-SHOPMGMT-008)
3. **Confirm People domain owns:** Mechanic profile SoR; shopmgmt may expose derived fields but UI must not require People calls (per DECISION-SHOPMGMT-009)
4. **Confirm Notification domain owns:** Delivery; shopmgmt may return outcome summary (per DECISION-SHOPMGMT-016)
5. What are the exact integration points between shopmgmt and Location/People/Notification domains?

**Section 2: API Contracts (Endpoints & Payloads)**
6. What is the base path for shopmgmt appointments API? (Proposed: `/api/shopmgmt/appointments` or `/api/appointments`)
7. What is the exact "load create model from source" endpoint? (Proposed: `GET /api/appointments/create-from-source?sourceType={ESTIMATE|WORK_ORDER}&sourceId={id}`)
8. What are the query parameters for load create model? (`sourceType`, `sourceId`, `facilityId`?)
9. Is `facilityId` required for the load (read) call, or only for writes? (DECISION-SHOPMGMT-012 allows reads to derive facilityId but does not mandate it)
10. What is the exact "create appointment from source" endpoint? (Proposed: `POST /api/appointments/from-source`)
11. What is the `CreateAppointmentRequest` payload structure? (All fields with types)
12. What is the `CreateAppointmentResponse` payload structure? (All fields with types)
13. What is the `ConflictResponse` (409) payload structure? (errorCode, conflicts[], suggestedAlternatives[])
14. What is the conflict classification structure? (severity: HARD/SOFT, code, message, overridable)
15. What is the suggested alternatives structure? (startDateTime, endDateTime)
16. Is submit-time conflict detection the only pattern? (No pre-check required for correctness per DECISION-SHOPMGMT-011)

**Section 3: Idempotency & Retry Patterns**
17. What is the idempotency key field name? (`clientRequestId` in body or `Idempotency-Key` in header per DECISION-SHOPMGMT-014?)
18. Where is the idempotency key sent? (header vs body field)
19. What is the recommended format for `clientRequestId`? (UUID per story; confirm)
20. How does backend handle duplicate `clientRequestId`? (Return original success response with same `appointmentId`)
21. What is the retry behavior? (UI reuses same `clientRequestId` until success/final failure)

**Section 4: Moqui Screen Integration**
22. What is the exact Moqui screen path for **Estimate Detail**?
23. What is the action/transition name for "Create Appointment" from Estimate Detail?
24. What is the exact Moqui screen path for **Work Order Detail**?
25. What is the action/transition name for "Create Appointment" from Work Order Detail?
26. What is the exact Moqui screen path for **Appointment Create** (new screen)?
27. What are the Moqui transitions for Appointment Create screen?
   - [ ] Load create model transition
   - [ ] Submit create appointment transition
28. What is the exact Moqui screen path for **Appointment Detail** (existing)?
29. What are the deep link patterns? (Proposed: `/appointments/create?sourceType=ESTIMATE&sourceId={id}`)
30. What is the menu wiring for Appointment Create? (Entry points from Estimate/Work Order Detail)

**Section 5: Entity Structure & Field Types**
31. What is the `AppointmentCreateModel` structure?
   - [ ] `sourceType` (enum string: `ESTIMATE` | `WORK_ORDER`) required
   - [ ] `sourceId` (UUID vs opaque string) required
   - [ ] `facilityId` (UUID vs opaque string) required
   - [ ] `facilityTimeZoneId` (IANA TZ string) required per DECISION-SHOPMGMT-015
   - [ ] `sourceStatus` (string) required
   - [ ] `isEligible` (boolean) required
   - [ ] `ineligibilityCode` (string) optional
   - [ ] `ineligibilityMessage` (string, safe) optional
   - [ ] `defaults` (object) optional: `suggestedDurationMinutes`, `requiredSkills[]`, `preferredSkills[]`, `serviceSummary`
   - [ ] `existingAppointmentId` (UUID vs opaque string) optional (permission-gated?)
32. Is `existingAppointmentId` permission-gated server-side? If yes, what is the permission?
33. What is the `CreateAppointmentRequest` structure?
   - [ ] `facilityId` (UUID vs opaque string) **required** per DECISION-SHOPMGMT-012
   - [ ] `sourceType` (enum string) **required**
   - [ ] `sourceId` (UUID vs opaque string) **required**
   - [ ] `scheduledStartDateTime` (ISO-8601 with offset) **required** per DECISION-SHOPMGMT-015
   - [ ] `clientRequestId` (UUID recommended) **required** per DECISION-SHOPMGMT-014
   - [ ] `overrideSoftConflicts` (boolean) optional, default false
   - [ ] `overrideReason` (string) conditionally required when `overrideSoftConflicts=true` per DECISION-SHOPMGMT-007
34. What is the `CreateAppointmentResponse` structure?
   - [ ] `appointmentId` (UUID vs opaque string) required
   - [ ] `appointmentStatus` (opaque string) required per DECISION-SHOPMGMT-013
   - [ ] `scheduledStartDateTime` (ISO-8601 with offset) required
   - [ ] `facilityTimeZoneId` (IANA TZ string) required per DECISION-SHOPMGMT-015
   - [ ] `estimateId` (UUID vs opaque string) optional (when `sourceType=ESTIMATE`)
   - [ ] `workOrderId` (UUID vs opaque string) optional (when `sourceType=WORK_ORDER`)
   - [ ] `notificationOutcomeSummary` (string) optional per DECISION-SHOPMGMT-016
35. What is the `ConflictResponse` (409) structure?
   - [ ] `errorCode` = `SCHEDULING_CONFLICT` (string) required per DECISION-SHOPMGMT-011
   - [ ] `conflicts[]` required: `severity`, `code`, `message`, `overridable`
   - [ ] `suggestedAlternatives[]` optional: `startDateTime`, `endDateTime`

**Section 6: Source Eligibility Rules**
36. What are the estimate eligibility rules? (Status in `APPROVED` or `QUOTED` per DECISION-SHOPMGMT-013)
37. What are the work order eligibility rules? (Status NOT in `COMPLETED` or `CANCELLED` per DECISION-SHOPMGMT-013)
38. What is the error code for ineligible estimate? (Proposed: `ESTIMATE_NOT_ELIGIBLE` with 422)
39. What is the error code for ineligible work order? (Proposed: `WORK_ORDER_NOT_ELIGIBLE` with 422)
40. What is the UI behavior when source is ineligible? (Show eligibility banner; block submit; link to existing appointment if provided)

**Section 7: Conflict Classification & Handling**
41. What is the conflict severity enum? (Confirmed: `HARD` | `SOFT` per DECISION-SHOPMGMT-002)
42. What are HARD conflict semantics? (Not overridable; block creation per DECISION-SHOPMGMT-002)
43. What are SOFT conflict semantics? (Overridable with permission + reason + audit per DECISION-SHOPMGMT-007)
44. How are operating hours violations presented? (As HARD conflicts per DECISION-SHOPMGMT-008)
45. What is the error code for operating hours violations? (`OUTSIDE_OPERATING_HOURS` as HARD conflict in 409 or as 422?)
46. What are the suggested alternatives semantics? (Time slots only; optional per DECISION-SHOPMGMT-011)
47. What is the UI behavior for HARD conflicts? (Block creation; show conflicts; show suggested alternatives; no override action)
48. What is the UI behavior for SOFT conflicts?
   - [ ] Without override permission: block with message "Manager override required"
   - [ ] With override permission: show override controls (required `overrideReason` input + "Override & Create" action)

**Section 8: Permissions & Authorization**
49. What is the exact permission identifier for creating appointments? (Proposed: `CREATE_APPOINTMENT`; exact string needed)
50. What is the exact permission identifier for overriding SOFT conflicts? (Proposed: `OVERRIDE_SCHEDULING_CONFLICT`; exact string needed)
51. Is deny-by-default enforced? (403 must not leak data existence per DECISION-SHOPMGMT-012)
52. What is the UI behavior without `CREATE_APPOINTMENT`? (Do not show "Create Appointment" action on source detail screens)
53. What is the UI behavior without `OVERRIDE_SCHEDULING_CONFLICT`? (Disable override action; show permission message)
54. Is `existingAppointmentId` permission-gated? If yes, what is the permission?

**Section 9: Error Codes & HTTP Mappings**
55. What is the error code for validation failures? (Proposed: `VALIDATION_ERROR` with 400 and `fieldErrors[]`)
56. What are the field error codes? (e.g., missing `scheduledStartDateTime`, missing `facilityId`, missing `overrideReason` when override=true)
57. What is the error code for scheduling conflicts? (Confirmed: `SCHEDULING_CONFLICT` with 409 per DECISION-SHOPMGMT-011)
58. What is the error code for unauthorized access? (Proposed: `FORBIDDEN` with 403)
59. What is the error code for not found? (Proposed: `NOT_FOUND` with 404; must not leak cross-facility existence per DECISION-SHOPMGMT-012)
60. What is the error code for policy/eligibility failures? (422 with `errorCode`: `ESTIMATE_NOT_ELIGIBLE`, `WORK_ORDER_NOT_ELIGIBLE`, `OUTSIDE_OPERATING_HOURS`)
61. What is the standard error envelope structure? (`{ errorCode, message, correlationId, fieldErrors?, details? }`)
62. What is the correlation ID header name? (Confirmed: `X-Correlation-Id` per DECISION-SHOPMGMT-011)

**Section 10: Timezone & Timestamp Handling**
63. What is the timestamp format? (Confirmed: ISO-8601 with offset per DECISION-SHOPMGMT-015)
64. What is the display timezone? (Confirmed: facility timezone using `facilityTimeZoneId` per DECISION-SHOPMGMT-015)
65. What is the input semantics? (UI collects date/time in facility timezone, converts to ISO-8601 with offset before submit)
66. Where is `facilityTimeZoneId` sourced? (Backend provides in `AppointmentCreateModel` per DECISION-SHOPMGMT-015)
67. Is `facilityTimeZoneId` an IANA timezone string? (e.g., `America/New_York`)

**Section 11: Facility Scoping**
68. Is `facilityId` required for writes? (Confirmed: YES per DECISION-SHOPMGMT-012)
69. Is `facilityId` required for reads (load create model)? (DECISION-SHOPMGMT-012 allows reads to derive facilityId but does not mandate it; clarify)
70. What is the error behavior for missing `facilityId` in create request? (400 with field error)
71. What is the error behavior for unauthorized facility access? (403 without leaking cross-facility existence per DECISION-SHOPMGMT-012)
72. What is the error behavior for not found facility/source? (404 without leaking cross-facility existence per DECISION-SHOPMGMT-012)

**Section 12: PII & Sensitive Data Handling**
73. What are the PII-safety requirements for display fields? (Do not display or log customer PII unless explicitly authorized per DECISION-SHOPMGMT-017)
74. Is `serviceSummary` PII-safe? (Expected: YES, backend-enforced)
75. Must `overrideReason` be logged client-side? (NO per DECISION-SHOPMGMT-017)
76. What is the UI behavior for displaying override metadata? (Show "Override applied" without echoing `overrideReason` unless backend returns safe summary)
77. What are the audit fields returned by backend? (`createdAt`, `createdBy` if included)

**Section 13: Notification Outcome**
78. What is the `notificationOutcomeSummary` structure? (Optional string per DECISION-SHOPMGMT-016)
79. Is notification outcome displayed to user? (If returned by backend)
80. What is the notification domain integration point? (Shopmgmt returns outcome summary; Notification owns delivery per DECISION-SHOPMGMT-016)

**Section 14: Observability & Telemetry**
81. What is the correlation ID header name? (Confirmed: `X-Correlation-Id` per DECISION-SHOPMGMT-011)
82. What telemetry events should be recorded? (Non-PII):
   - [ ] `screen_opened` (sourceType, sourceId, facilityId)
   - [ ] `create_submitted` (clientRequestId)
   - [ ] `create_conflicts_returned` (counts HARD/SOFT)
   - [ ] `create_succeeded` (appointmentId)
   - [ ] `create_failed` (errorCode)
83. Must PII be excluded from telemetry? (YES per DECISION-SHOPMGMT-017)
84. Must `overrideReason` be excluded from client logs? (YES per DECISION-SHOPMGMT-017)

**Section 15: Cross-Domain Dependencies**
85. What is the Location domain integration point for operating hours? (Consumed by shopmgmt per DECISION-SHOPMGMT-008)
86. Does UI need to call Location domain directly? (Expected: NO; shopmgmt enforces operating hours)
87. What is the People domain integration point for mechanic profiles? (Shopmgmt may expose derived fields; UI must not require People calls per DECISION-SHOPMGMT-009)
88. Does UI need to call People domain directly? (Expected: NO per DECISION-SHOPMGMT-009)
89. What is the Notification domain integration point? (Shopmgmt may return outcome summary; Notification owns delivery per DECISION-SHOPMGMT-016)
90. Does UI need to call Notification domain directly? (Expected: NO; shopmgmt returns outcome summary if applicable)

---

## Cross-Cutting Concerns

### DECISION-SHOPMGMT References
- **DECISION-SHOPMGMT-001**: Shopmgmt owns appointment creation, eligibility checks, conflict detection
- **DECISION-SHOPMGMT-002**: HARD conflicts are not overridable; SOFT conflicts may be overridden
- **DECISION-SHOPMGMT-007**: SOFT conflicts overridable with permission + reason; always audited
- **DECISION-SHOPMGMT-008**: Location owns operating hours; shopmgmt enforces via HARD conflicts
- **DECISION-SHOPMGMT-009**: People owns mechanic profile SoR; shopmgmt may expose derived fields; UI must not require People calls
- **DECISION-SHOPMGMT-011**: Submit-time conflict detection (409 on submit); suggested alternatives optional; correlation via `X-Correlation-Id`
- **DECISION-SHOPMGMT-012**: All write requests include `facilityId` explicitly; 403/404 must not leak cross-facility existence
- **DECISION-SHOPMGMT-013**: Appointment status is opaque; source eligibility rules (Estimate: APPROVED/QUOTED; Work Order: NOT COMPLETED/CANCELLED)
- **DECISION-SHOPMGMT-014**: Idempotent create submission using `clientRequestId`; retry with same ID
- **DECISION-SHOPMGMT-015**: Timezone handling: ISO-8601 with offset; display in facility timezone using `facilityTimeZoneId`
- **DECISION-SHOPMGMT-016**: Notification domain owns delivery; shopmgmt may return outcome summary
- **DECISION-SHOPMGMT-017**: Do not display or log customer PII or free-text `overrideReason` unless explicitly authorized

### Backend Contract Guides
- REST path convention: `/api/shopmgmt/appointments/*` or `/api/appointments/*`
- Error envelope shape: `{ errorCode, message, correlationId, fieldErrors?, details? }`
- Timestamp format: ISO-8601 with offset per DECISION-SHOPMGMT-015
- Idempotency key: `clientRequestId` (header or body) per DECISION-SHOPMGMT-014
- Conflict response: `{ errorCode: "SCHEDULING_CONFLICT", conflicts[], suggestedAlternatives[] }`
- Correlation header: `X-Correlation-Id` per DECISION-SHOPMGMT-011

### Permission Taxonomy (Examples)
**Shop Management Domain:**
- `CREATE_APPOINTMENT` — Create appointments from estimates or work orders (exact string needed)
- `OVERRIDE_SCHEDULING_CONFLICT` — Override SOFT conflicts with reason (exact string needed)

---

## Progress Tracking

### Phase 1 Status
- [ ] Domain boundaries and ownership clarification
- [ ] REST endpoint mapping (load create model, create appointment)
- [ ] Moqui screen/service mapping
- [ ] Error envelope confirmation

### Phase 2 Status
- [ ] Appointment create model structure
- [ ] Create appointment request structure
- [ ] Create appointment response structure
- [ ] Conflict response structure
- [ ] Source eligibility rules
- [ ] Identifier types
- [ ] Timezone and timestamp handling
- [ ] Facility scoping requirements

### Phase 3 Status
- [ ] Source eligibility validation
- [ ] Appointment create validation rules
- [ ] Conflict handling UI patterns
- [ ] Operating hours enforcement
- [ ] Idempotency and retry patterns
- [ ] Permission-gated UI behavior
- [ ] Error handling patterns
- [ ] Accessibility and responsiveness
- [ ] PII and sensitive data handling

### Phase 4 Status
- [ ] GitHub comment posted (Issue #76)
- [ ] Labels updated (remove `blocked:clarification`)
- [ ] Documentation updated (business rules for shopmgmt)
- [ ] DECISION references verified
- [ ] Cross-domain documentation updated (Location, People, Notification if needed)

---

## Next Actions

1. **PRIORITY:** Research backend contracts for appointment creation workflow using available backend docs and DECISION-SHOPMGMT-* references
2. Confirm Moqui screen paths for Estimate Detail, Work Order Detail, Appointment Create, Appointment Detail
3. Confirm exact permission identifiers: `CREATE_APPOINTMENT`, `OVERRIDE_SCHEDULING_CONFLICT`
4. Draft GitHub issue comment with clarifications for Issue #76
5. Post comment to `durion-moqui-frontend` issue #76
6. Update labels: remove `blocked:clarification`
7. Update domain business rules documentation with resolved contracts
8. Mark this document status as `Resolved` when all tasks complete

---

**Document Status:** Draft — awaiting backend contract research and GitHub issue updates  
**Last Updated:** 2026-01-25  
**Owner:** Platform Team  
**Related Documents:**
- `domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md`
- `domains/location/location-questions.md`
- `domains/pricing/pricing-questions.md`
- `domains/security/security-questions.md`
- `domains/people/people-questions.md`
- **DECISION-SHOPMGMT-001** through **DECISION-SHOPMGMT-017** (cross-referenced throughout)
