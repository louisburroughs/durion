# Phase 3 Execution — Shop Management Domain: UX/Validation Alignment

**Objective:** Confirm validation rules, conflict handling UI patterns, override permissions, and accessibility patterns

**Execution Date:** 2026-01-26

---

## Phase 3 Tasks

### Task 3.1 — Source Eligibility Validation
**Status:** ✅ COMPLETE

**Objective:** Establish validation rules for source document eligibility

**Confirmed Rules** (per DECISION-SHOPMGMT-013):
- **Client-side validation:** None (eligibility is server-authoritative)
- **Server-side validation:** 
  - Estimate status must be `APPROVED` or `QUOTED`
  - Work order status must NOT be `COMPLETED` or `CANCELLED`
- **Error codes (HTTP 422):**
  - `ESTIMATE_NOT_ELIGIBLE` — estimate not in eligible status
  - `WORK_ORDER_NOT_ELIGIBLE` — work order in ineligible status
- **UI behavior:** Show eligibility banner; block submit; link back to source or existing appointment if available

**Status:** ✅ Complete; exception class created; validation framework established

---

### Task 3.2 — Appointment Create Validation Rules
**Status:** ✅ COMPLETE

**Objective:** Establish field-level validation requirements

**Confirmed Rules:**
- **`facilityId` (required):** Client-side required + server-side validation (per DECISION-SHOPMGMT-012)
- **`sourceType` (required):** Client-side + server-side validation (enum: ESTIMATE | WORKORDER)
- **`sourceId` (required):** Client-side required + server-side presence check (no format validation)
- **`scheduledStartDateTime` (required):** Client-side required + server-side ISO-8601 validation with offset (per DECISION-SHOPMGMT-015)
- **`scheduledEndDateTime` (required):** Client-side required + server-side ISO-8601 validation with offset
- **`clientRequestId` (optional):** Recommended for idempotency (per DECISION-SHOPMGMT-014)
- **`overrideSoftConflicts` (optional):** Boolean, default false
- **`overrideReason` (conditionally required):** Required only when `overrideSoftConflicts=true`; non-empty string (per DECISION-SHOPMGMT-007)

**Validation Error Response (HTTP 400):**
```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed for one or more fields",
  "correlationId": "string",
  "timestamp": "2026-01-26T00:00:00Z",
  "fieldErrors": {
    "facilityId": "Facility ID is required",
    "scheduledStartDateTime": "Invalid ISO-8601 format"
  }
}
```

**Status:** ✅ Complete

---

### Task 3.3 — Conflict Handling UI Patterns
**Status:** ✅ COMPLETE

**Objective:** Establish UI patterns for HARD vs SOFT conflicts

**Conflict Detection Pattern** (per DECISION-SHOPMGMT-011):
- **Timing:** Submit-time only (no pre-check required)
- **Response:** HTTP 409 with `ConflictResponse` body
- **Suggested Alternatives:** Optional array of alternative time slots

**HARD Conflict Handling:**
- **Block creation:** No override action available (per DECISION-SHOPMGMT-002)
- **UI:**
  - Show conflict list with severity indicators (e.g., 🔴 HARD)
  - Display conflict reason and affected resource
  - Show `suggestedAlternatives[]` when present
  - Display action buttons: "Change Time" (keep user on form) or "Back to Source"
- **Retry:** User changes `scheduledStartDateTime` and resubmits (new conflict detection pass)

**SOFT Conflict Handling:**
- **Permission check:**
  - If user lacks `OVERRIDE_SCHEDULING_CONFLICT` permission: block with message "Manager override required"
  - If user has permission: show override controls
- **UI for overridable conflicts:**
  - Show conflict list with severity indicators (e.g., 🟡 SOFT)
  - Show "Override reason" required input field (max 500 chars per DECISION-SHOPMGMT-017)
  - Display action buttons: "Change Time" (keep user on form) or "Override & Create" (enabled only if reason provided)
- **Retry:** User either changes time or enters reason + overrides with `overrideSoftConflicts=true` and `overrideReason`

**Status:** ✅ Complete

---

### Task 3.4 — Operating Hours Enforcement
**Status:** ✅ COMPLETE

**Objective:** Establish operating hours violation handling

**Enforcement** (per DECISION-SHOPMGMT-008):
- **Classification:** Operating hours violations are HARD conflicts (not overridable)
- **Error code:** `OUTSIDE_OPERATING_HOURS` (returned as HARD conflict in 409 response)
- **Conflict severity:** HARD
- **Conflict overridable:** false

**UI Behavior:**
- Show blocking conflict with facility hours (e.g., "Facility hours: 8:00 AM - 5:00 PM EST")
- Render `suggestedAlternatives[]` if provided (e.g., "Earliest available: 2:00 PM - 4:00 PM EST")
- No override action (already established as HARD conflict)
- User must change time within operating hours

**Status:** ✅ Complete

---

### Task 3.5 — Idempotency and Retry Patterns
**Status:** ✅ COMPLETE

**Objective:** Establish idempotent submission pattern

**Pattern** (per DECISION-SHOPMGMT-014):
- **Generate once:** Create `clientRequestId` (UUID) once per user-initiated submit attempt
- **Persist in state:** Store `clientRequestId` in screen state (e.g., Vue reactive state) until success/final failure
- **On timeout/network error:** Offer "Retry" button using the **same** `clientRequestId`
- **Backend idempotency:** Return original success response (same `appointmentId`) on idempotent retry
- **Idempotency key location:** Via `Idempotency-Key` header (already implemented in controller) OR `clientRequestId` in request body

**Recommended Implementation:**
1. Generate `clientRequestId = UUID.randomUUID()` on form mount
2. Include in request: `{ clientRequestId, ...otherFields }`
3. Send `Idempotency-Key` header = `clientRequestId` (recommended pattern)
4. On success (201): store appointmentId, navigate to appointment detail
5. On network error: display "Retry" button; resubmit with **same** `clientRequestId`
6. Backend returns same `appointmentId` on retry → success

**Status:** ✅ Complete

---

### Task 3.6 — Permission-Gated UI Behavior
**Status:** ⏳ PENDING CLARIFICATION

**Objective:** Confirm permission identifiers and UI gating logic

**Required Permissions:**
1. **`CREATE_APPOINTMENT`** — Required to access and submit appointment creation form
2. **`OVERRIDE_SCHEDULING_CONFLICT`** — Required to override SOFT conflicts

**UI Gating:**
- **Without `CREATE_APPOINTMENT`:**
  - Hide "Create Appointment" action on source detail screens (Estimate Detail, Work Order Detail)
  - Do not show appointment creation navigation
  
- **Without `OVERRIDE_SCHEDULING_CONFLICT`:**
  - Show conflict list on submit
  - Disable "Override & Create" action
  - Show message: "Contact manager to override scheduling conflicts"

**403 Handling** (per DECISION-SHOPMGMT-012):
- Must NOT leak cross-facility existence
- Return generic 403 without indicating whether resource exists or user is out-of-facility

**Status:** ⏳ Pending exact permission string identifiers from authorization service

---

### Task 3.7 — Error Handling and Correlation ID Propagation
**Status:** ✅ COMPLETE

**Objective:** Establish HTTP code to UX mapping

**Error Response Mapping:**

| HTTP Code | Error Code | Frontend Behavior | User Message |
|-----------|-----------|------------------|--------------|
| 200/201 | N/A | Navigate to Appointment Detail | Appointment created |
| 400 | VALIDATION_ERROR | Show field errors with focus on first error | "Please correct the highlighted fields" |
| 403 | FORBIDDEN | Show generic deny message | "You don't have permission to perform this action" (no resource hint) |
| 404 | NOT_FOUND | Show generic not found | "The requested resource was not found" (no resource hint) |
| 409 | SCHEDULING_CONFLICT | Render conflicts and alternatives; gate override UI per Task 3.6 | "Scheduling conflict detected" (see Task 3.3) |
| 422 | ESTIMATE_NOT_ELIGIBLE / WORK_ORDER_NOT_ELIGIBLE | Block with reason; offer "Back to Source" link | "Estimate is not eligible for appointment creation (status: DRAFT)" |
| 5xx | Internal Server Error | Show generic failure with correlationId | "An unexpected error occurred. Reference: {correlationId}" |

**CorrelationId Visibility:**
- **All error responses:** Surface `correlationId` in error banners (e.g., "Error Reference: 12345abc")
- **User provides to support:** "My error reference is 12345abc"
- **Logging:** Include correlationId in frontend console logs (not logs sent to server)

**Correlation Header Propagation** (per DECISION-SHOPMGMT-011):
- **Outbound:** Frontend includes `X-Correlation-Id` header in all requests (generated once per page session, propagated across retries)
- **Inbound:** Extract from response body or response header if present
- **Display:** Show in error messages

**PII Safety** (per DECISION-SHOPMGMT-017):
- **DO NOT log** `overrideReason` client-side (sent to server; not logged locally)
- **DO NOT display** customer PII in appointment details unless explicitly authorized
- **DO NOT echo** user-entered text in error messages (sanitize)

**Status:** ✅ Complete

---

### Task 3.8 — Accessibility and Responsiveness
**Status:** ✅ DOCUMENTED

**Objective:** Establish accessibility requirements

**Keyboard Navigation:**
- ✅ All form inputs, buttons, and controls accessible via Tab key
- ✅ Enter key submits form (when form valid)
- ✅ Escape key closes modals (conflict details, etc.)
- ✅ Dialog focus trap: focus cycles within conflict details modal

**ARIA Labels:**
- ✅ Form inputs: `aria-label` or associated `<label>` (required field indicator via `aria-required="true"`)
- ✅ Buttons: Clear action text (e.g., "Create Appointment", "Override & Create", "Back to Source")
- ✅ Error messages: `aria-live="polite"` for dynamic error banners
- ✅ Conflict list: `role="list"`, each conflict `role="listitem"` with severity indicator
- ✅ Loading states: `aria-busy="true"` on submit button during request

**Error Focus:**
- Move focus to first error field on validation failure (client-side or server-side)
- Announce error via `aria-live="polite"` region
- Visual indicator: red border or background on error fields

**Responsive Layout:**
- ✅ Usable on tablet widths (768px) and desktop (1200px+)
- ✅ Date/time input: accessible picker supporting facility timezone (per DECISION-SHOPMGMT-015)
  - Option 1: Native `<input type="datetime-local">` + manual timezone offset conversion
  - Option 2: 3rd-party accessible datetime picker (e.g., Quasar QDateTime with ARIA support)
- ✅ Conflict list: readable on all screen sizes; scrollable if needed
- ✅ Override reason input: visible and accessible on narrow screens

**Status:** ✅ Complete

---

### Task 3.9 — PII and Sensitive Data Handling
**Status:** ✅ COMPLETE

**Objective:** Establish data privacy requirements

**PII Safety** (per DECISION-SHOPMGMT-017):
- **DO NOT display** customer/contact PII unless explicitly authorized (e.g., no "Call customer: John Doe 555-1234")
- **DO NOT log** `overrideReason` client-side (user-entered sensitive reason; server logs only)
- **DO NOT echo** user-entered override reason back to UI (only display "Override applied" status)
- **Safe fields:** createdAt, createdBy (if provided), non-sensitive indicators

**Sensitive Fields in Requests:**
- `overrideReason` — Sent to server; NOT logged or echoed client-side

**Sensitive Fields in Responses:**
- `overrideReason` — NOT returned in appointment response; backend stores for audit only
- Customer/contact details — NOT included in appointment details unless explicitly authorized

**Audit Trail** (per DECISION-SHOPMGMT-007):
- Backend audit captures: who, when, why (override reason), result
- Frontend does NOT capture override reason locally (server-authoritative)

**UI Display for Overrides:**
- Show: "Appointment created with manager override" (generic indicator)
- Hide: The actual override reason (server-auditable; not client-visible)

**Status:** ✅ Complete

---

## Phase 3 Blockers

⏳ **Permission string identifiers** — Awaiting exact permission names from authorization service
- Example: `com.positivity.shopmgmt.APPT_CREATE` vs `APPT_CREATE_PERMISSION` vs other convention
- Impact: Cannot complete authorization checks until identifiers provided
- Workaround: Use placeholder identifiers in implementation and update when service team provides exact names

---

## Phase 3 Summary

**Completed Tasks:**
- Task 3.1 ✅ Eligibility validation rules confirmed
- Task 3.2 ✅ Field-level validation rules established
- Task 3.3 ✅ HARD vs SOFT conflict UI patterns defined
- Task 3.4 ✅ Operating hours enforcement (HARD conflict) confirmed
- Task 3.5 ✅ Idempotency and retry pattern established
- Task 3.7 ✅ Error response mapping and correlation ID propagation confirmed
- Task 3.8 ✅ Accessibility and responsiveness requirements documented
- Task 3.9 ✅ PII and sensitive data handling policies confirmed

**Pending Tasks:**
- Task 3.6 ⏳ Permission identifiers needed from authorization service

---

## Implementation Checklist for Frontend

Use this checklist when implementing the Moqui appointment creation screen:

### Form Structure
- [ ] Load `AppointmentCreateModel` on screen mount (GET `/v1/shop-manager/appointments/create-model?sourceType=...&sourceId=...&facilityId=...`)
- [ ] Display facility timezone (from model's `facilityTimeZoneId`)
- [ ] Display source status and eligibility (block if not eligible per Task 3.1)
- [ ] Generate and store `clientRequestId` (UUID) on mount

### Form Fields
- [ ] `sourceType` (read-only display, from URL parameter)
- [ ] `sourceId` (read-only display, from URL parameter)
- [ ] `facilityId` (read-only or selectable per authorization)
- [ ] `scheduledStartDateTime` (required, datetime picker in facility timezone)
- [ ] `scheduledEndDateTime` (required, datetime picker in facility timezone)
- [ ] `overrideSoftConflicts` (checkbox, hidden by default; shown on 409 response)
- [ ] `overrideReason` (textarea, required when `overrideSoftConflicts=true`; max 500 chars)

### Form Validation (Client-side)
- [ ] All required fields must be present before submit
- [ ] ISO-8601 timestamp format validation
- [ ] If `overrideSoftConflicts=true`, then `overrideReason` must be non-empty
- [ ] Show field errors with focus on first error

### Submit Logic
- [ ] POST `/v1/shop-manager/appointments` with body: `{sourceType, sourceId, facilityId, scheduledStartDateTime, scheduledEndDateTime, clientRequestId, overrideSoftConflicts?, overrideReason?}`
- [ ] Include `Idempotency-Key: {clientRequestId}` header
- [ ] Include `X-Correlation-Id: {correlationId}` header (generated once per session)
- [ ] On success (201): Navigate to `AppointmentDetail?appointmentId={appointmentId}`
- [ ] On 409 Conflict: Render conflicts and alternatives; show override controls if user has permission
- [ ] On 422 Eligibility Error: Show blocking message with "Back to Source" link
- [ ] On 4xx/5xx: Show error banner with correlationId; offer "Retry" for network errors

### Conflict Rendering (on 409 response)
- [ ] Render each conflict with severity indicator (HARD=🔴, SOFT=🟡)
- [ ] Show conflict code, message, and affected resource
- [ ] Render `suggestedAlternatives` if present (alternative time slots)
- [ ] For HARD conflicts: show "Change Time" button; disable override action
- [ ] For SOFT conflicts + no permission: show "Manager override required"; disable override action
- [ ] For SOFT conflicts + permission: show "Override reason" input + "Override & Create" button

### Accessibility Implementation
- [ ] All inputs have associated labels or `aria-label`
- [ ] Required fields marked with `aria-required="true"`
- [ ] Error messages in `aria-live="polite"` regions
- [ ] Conflict list uses semantic HTML: `<ul>`, `<li>` with proper roles
- [ ] Focus management: on error, move focus to first error field
- [ ] Keyboard navigation: Tab cycles through all controls, Enter submits, Escape closes modals
- [ ] ARIA labels on buttons: "Create Appointment", "Override & Create", "Change Time", etc.

### Responsive Design
- [ ] Layout works on tablet (768px) and desktop (1200px+)
- [ ] Datetime picker is accessible on all screen sizes
- [ ] Conflict list is readable; scrollable if needed
- [ ] Override reason input visible on narrow screens

### Data Privacy
- [ ] DO NOT log `overrideReason` to console (user-sensitive)
- [ ] DO NOT display customer PII in appointment details
- [ ] DO NOT echo override reason in success message (only generic "Override applied")
- [ ] Display correlationId in all error messages for support reference

---

## Next Steps

Proceed to **Phase 4: Issue Updates and Closure**
- Post comprehensive resolution to Issue #76
- Confirm all DECISION references documented
- Update issue labels and status
- Create follow-up implementation tracking (Phase 5: Frontend Implementation)

