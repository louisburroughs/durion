# Phase 4 Execution — Shop Management Domain: Issue Resolution and Closure

**Objective:** Post comprehensive resolution comments to GitHub issue #76 and finalize clarification blockers

**Execution Date:** 2026-01-26

---

## Phase 4 Summary

### Issue #76: Appointment: Create Appointment from Estimate or Order
**Status:** ✅ `blocked:clarification` RESOLVED

This issue has been systematically analyzed across **3 phases** (Contract Discovery → Data Contracts → UX Alignment). All blocking questions have been answered with documented contracts and implementation guidance.

---

## Comprehensive Resolution

### Phase 1: Contract & Ownership Confirmation ✅

**Domain Boundaries Confirmed:**
- ✅ shopmgmt owns: appointment creation, eligibility checks, conflict detection/classification, operating-hours enforcement, assignment representation, audit trails
- ✅ Location domain (read-only): operating hours source of truth per DECISION-SHOPMGMT-008
- ✅ People domain (read-only): mechanic HR profile SoR per DECISION-SHOPMGMT-009
- ✅ Notification domain (fire-and-forget): delivery service per DECISION-SHOPMGMT-016

**Backend API Contract Confirmed:**
- **Endpoint:** `POST /v1/shop-manager/appointments` (Spring Boot pos-shop-manager module)
- **Idempotency:** `Idempotency-Key` header per DECISION-SHOPMGMT-014
- **Correlation:** `X-Correlation-Id` header per DECISION-SHOPMGMT-011
- **DTOs:** AppointmentCreateRequest, AppointmentResponse, ConflictResponse

**Moqui Integration Confirmed:**
- **Screens:** AppointmentCreate.xml, AppointmentEdit.xml, AppointmentFind.xml, AppointmentDelete.xml
- **Deep link:** `/shopmgr/appointment/create?sourceType={ESTIMATE|WORKORDER}&sourceId={id}`
- **Error pattern:** Standard ErrorResponse with correlationId

### Phase 2: Data & Dependency Contracts ✅

**All Data Structures Defined:**

**Request DTO (AppointmentCreateRequest):**
```
- sourceType: ESTIMATE | WORKORDER (required)
- sourceId: opaque string (required)
- facilityId: opaque string (required, per DECISION-SHOPMGMT-012)
- scheduledStartDateTime: ISO-8601 with offset (required, per DECISION-SHOPMGMT-015)
- scheduledEndDateTime: ISO-8601 with offset (required)
- clientRequestId: UUID (optional, recommended per DECISION-SHOPMGMT-014)
- overrideSoftConflicts: boolean, default false
- overrideReason: string (required when overrideSoftConflicts=true, per DECISION-SHOPMGMT-007)
```

**Response DTO (AppointmentResponse):**
```
- appointmentId: opaque string
- appointmentStatus: opaque string
- scheduledStartDateTime: ISO-8601 with offset (facility timezone)
- scheduledEndDateTime: ISO-8601 with offset (facility timezone)
- facilityId: opaque string
- facilityTimeZoneId: IANA timezone (e.g., America/New_York)
- sourceType: ESTIMATE | WORKORDER
- sourceId: opaque string
- notificationOutcomeSummary: string (optional, per DECISION-SHOPMGMT-016)
- createdAt: ISO-8601 Z (UTC)
- lastUpdatedAt: ISO-8601 Z (UTC)
```

**Conflict Response DTO (ConflictResponse):**
```
- errorCode: "SCHEDULING_CONFLICT" (fixed)
- message: string
- correlationId: string
- timestamp: instant
- conflicts[]:
  - severity: HARD | SOFT (enum)
  - code: string (e.g., OUTSIDE_OPERATING_HOURS, MECHANIC_UNAVAILABLE)
  - message: string
  - overridable: boolean (HARD=false, SOFT=true per DECISION-SHOPMGMT-002)
  - affectedResource: string
- suggestedAlternatives[]:
  - startDateTime: ISO-8601 with offset
  - endDateTime: ISO-8601 with offset
  - reason: string (optional)
```

**Create Form Model DTO (AppointmentCreateModel):**
```
- facilityId: opaque string
- sourceType: ESTIMATE | WORKORDER
- sourceId: opaque string
- facilityTimeZoneId: IANA timezone
- sourceStatus: string
- suggestedStartDateTime: ISO-8601 with offset (optional)
- suggestedEndDateTime: ISO-8601 with offset (optional)
- businessHoursOpen: time string (optional)
- businessHoursClose: time string (optional)
```

**Exception Classes Created:**
- `SourceNotEligibleException` (HTTP 422)
- `SchedulingConflictException` (HTTP 409)

**Service Contracts Defined:**
- `SourceEligibilityService` — Validate source documents
- `ConflictDetectionService` — Detect HARD/SOFT conflicts
- `AppointmentLoadService` — Load create form model

### Phase 3: UX/Validation Alignment ✅

**Validation Rules (Client + Server):**
- All required fields: facilityId, sourceType, sourceId, scheduledStartDateTime, scheduledEndDateTime
- ISO-8601 timestamp validation with offset (per DECISION-SHOPMGMT-015)
- Conditional: overrideReason required when overrideSoftConflicts=true (per DECISION-SHOPMGMT-007)
- Error response (400): VALIDATION_ERROR with fieldErrors map

**Source Eligibility (Server-authoritative):**
- Estimate: status must be APPROVED or QUOTED (per DECISION-SHOPMGMT-013)
- Work Order: status must NOT be COMPLETED or CANCELLED
- Error codes (422): ESTIMATE_NOT_ELIGIBLE, WORK_ORDER_NOT_ELIGIBLE

**Conflict Handling UI Patterns:**

**HARD Conflicts** (per DECISION-SHOPMGMT-002/008/011):
- Block creation (no override action)
- Show conflict severity, code, message, affected resource
- Display suggested alternatives if available
- User changes time and resubmits

**SOFT Conflicts** (per DECISION-SHOPMGMT-007):
- If user has OVERRIDE_SCHEDULING_CONFLICT permission: show override controls
- If not: show "Manager override required" (block)
- When allowed: require overrideReason (max 500 chars, audit trail)
- Resubmit with overrideSoftConflicts=true

**Operating Hours** (per DECISION-SHOPMGMT-008):
- Violations are HARD conflicts (code: OUTSIDE_OPERATING_HOURS)
- Show facility hours and alternatives
- No override action

**Idempotency** (per DECISION-SHOPMGMT-014):
- Generate clientRequestId once per submit attempt
- Send via Idempotency-Key header + request body
- Retry with same clientRequestId on network error
- Backend returns original response (same appointmentId)

**Error Handling & Correlation ID** (per DECISION-SHOPMGMT-011/012):
- 200/201: Navigate to appointment detail
- 400: Field errors with correlationId visible
- 403: Generic deny (no resource hint) with correlationId
- 404: Not found (no resource hint) with correlationId
- 409: Conflict response with alternatives and override UI
- 422: Eligibility error with "Back to Source"
- 5xx: Generic failure with correlationId for support

**Accessibility** (per WCAG 2.1 AA):
- Keyboard navigation: Tab, Enter, Escape
- ARIA labels on all inputs and buttons
- Focus management: error focus on first field
- Responsive: tablet (768px) and desktop (1200px+)
- Datetime picker: accessible with facility timezone

**Data Privacy** (per DECISION-SHOPMGMT-017):
- DO NOT display customer PII unless authorized
- DO NOT log overrideReason client-side
- DO NOT echo override reason (only "Override applied" indicator)
- Audit trail: server captures who/when/why

---

## DECISION Document Checklist

All Phase 1-3 recommendations grounded in authoritative DECISION documents:

- ✅ DECISION-SHOPMGMT-001: Appointment ownership and responsibility model
- ✅ DECISION-SHOPMGMT-002: Hard vs Soft conflict classification (HARD not overridable; SOFT overridable with permission)
- ✅ DECISION-SHOPMGMT-007: Soft conflict override with reason and audit trail
- ✅ DECISION-SHOPMGMT-008: Operating hours enforcement (HARD conflicts)
- ✅ DECISION-SHOPMGMT-009: People domain boundary (read-only mechanic profiles)
- ✅ DECISION-SHOPMGMT-011: Correlation ID header name (X-Correlation-Id); submit-time conflict detection
- ✅ DECISION-SHOPMGMT-012: Facility scoping (explicit facilityId required; deny-by-default; no cross-facility leaks)
- ✅ DECISION-SHOPMGMT-013: Source eligibility rules (estimate status; workorder status)
- ✅ DECISION-SHOPMGMT-014: Idempotency via clientRequestId
- ✅ DECISION-SHOPMGMT-015: Timezone handling (facility timezone for input/display; ISO-8601 with offset)
- ✅ DECISION-SHOPMGMT-016: Notification outcome summary (optional per response)
- ✅ DECISION-SHOPMGMT-017: PII safety (no customer data unless authorized; no logging of override reason)

---

## Implementation Status

**Backend (pos-shop-manager):**
- ✅ DTOs created and finalized (AppointmentCreateRequest, AppointmentResponse, ConflictResponse, AppointmentCreateModel)
- ✅ Exception classes created (SourceNotEligibleException, SchedulingConflictException)
- ✅ Service interfaces defined (SourceEligibilityService, ConflictDetectionService, AppointmentLoadService)
- ✅ AppointmentsService updated with orchestration methods and implementation TODOs
- ⏳ Service implementations pending (eligibility validation, conflict detection, create orchestration)

**Frontend (durion-moqui-frontend/durion-shopmgr):**
- ✅ Moqui screens identified (AppointmentCreate.xml, etc.)
- ✅ Validation rules documented
- ✅ Conflict UI patterns documented
- ✅ Accessibility requirements documented
- ✅ Implementation checklist provided
- ⏳ Frontend implementation pending (form creation, validation, conflict rendering)

---

## Pending Clarification

### Permission String Identifiers (Non-blocking)

The following permission names are needed from your authorization service:
1. **CREATE_APPOINTMENT** — Controls access to appointment creation form and action
2. **OVERRIDE_SCHEDULING_CONFLICT** — Controls SOFT conflict override UI

These are used in permission gates:
- Without CREATE_APPOINTMENT: Hide "Create Appointment" action on source detail screens
- Without OVERRIDE_SCHEDULING_CONFLICT: Disable override controls on 409 response

**Workaround:** Implement with placeholder permission names and update when service team provides identifiers.

---

## Follow-up Work

### Phase 5: Frontend Implementation (Recommended)

Create a new tracking issue for frontend implementation with the following scope:
1. Create AppointmentCreate.vue component with:
   - Form structure matching AppointmentCreateRequest
   - Client-side validation per Task 3.2
   - Datetime picker with facility timezone conversion
   - Idempotent submit with clientRequestId generation
   
2. Implement conflict handling:
   - Parse ConflictResponse from 409 response
   - Render HARD vs SOFT conflicts with severity indicators
   - Gate override controls based on permission
   - Show suggested alternatives
   
3. Implement error handling:
   - HTTP 400: field errors
   - HTTP 403/404: generic deny
   - HTTP 422: eligibility errors
   - HTTP 5xx: generic failure
   - All with correlationId visible
   
4. Accessibility implementation:
   - ARIA labels, keyboard navigation, focus management
   - Semantic HTML for conflict lists
   
5. Responsive design:
   - Tablet (768px) and desktop (1200px+) layouts
   - Datetime picker accessibility

---

## Issue Status

**Current Labels:** `blocked:clarification`, `domain:shopmgmt`, `priority:high`, `story`

**Recommended Actions:**
1. ✅ Remove `blocked:clarification` label (all blocking questions resolved)
2. ✅ Add `phase:backend-contract` label (Phases 1-3 complete)
3. ⏳ Add `phase:backend-implementation` label (service implementations pending)
4. ⏳ Add `phase:frontend-implementation` label (form and conflict handling pending)

---

## Documentation Artifacts

All analysis and recommendations documented in:
1. `/durion/Durion-Processing-ShopMgmt-Phase1.md` — Contract discovery
2. `/durion/Durion-Processing-ShopMgmt-Phase2.md` — Data structures and service contracts
3. `/durion/Durion-Processing-ShopMgmt-Phase3.md` — Validation, UI patterns, accessibility
4. `/durion/domains/shopmgmt/shopmgmt-questions.md` — Phase 1-3 tasks and acceptance criteria

Backend code artifacts:
1. `/pos-shop-manager/dto/` — All DTOs with full javadocs
2. `/pos-shop-manager/exception/` — Exception classes with javadocs
3. `/pos-shop-manager/service/` — Service interfaces with contract specifications
4. `/pos-shop-manager/service/AppointmentsService.java` — Orchestration layer with implementation TODOs

---

## Conclusion

Issue #76 "Appointment: Create Appointment from Estimate or Order" is **blocked:clarification RESOLVED** with:
- ✅ All backend API contracts documented and DTOs created
- ✅ All data structures defined with field types and validations
- ✅ Conflict handling patterns (HARD vs SOFT) established
- ✅ UX/validation rules documented with accessibility requirements
- ✅ Implementation guidance provided for frontend and backend
- ✅ All DECISION documents referenced and honored
- ⏳ One non-blocking clarification needed: permission string identifiers

**Ready for backend implementation** (service layer, database integration) and **frontend implementation** (Moqui appointment creation form).
