---
title: Phase 3 – UX & Validation Alignment (Location & Scheduling)
date: 2026-01-25
status: IN_PROGRESS
---

## Bay Validation Rules

- **Name (required, trimmed):**
  - Trim leading/trailing whitespace before validation and persistence.
  - Must be unique within the same `locationId`.
  - Error: `BAY_NAME_TAKEN_IN_LOCATION` (409) with field error on `name`.
- **Bay Type (enum):** One of `ALIGNMENT_STATION`, `SERVICE_BAY`, `WASH_BAY` (extendable; confirm final set).
- **Status (enum):** One of `ACTIVE`, `OUT_OF_SERVICE`.
- **Capacity:**
  - `maxConcurrentVehicles` integer ≥ 1 (upper bound TBD, propose ≤ 10 for UI guardrails; backend authoritative).
  - Error: `VALIDATION_ERROR` (400) for out-of-range.
- **Constraints (optional):**
  - `supportedServiceIds` (array of catalog service IDs) — invalid IDs → `UNKNOWN_SERVICE_ID` (400).
  - `requiredSkillRequirements[]` with `skillId` and optional `minLevel` — invalid IDs → `UNKNOWN_SKILL_ID` (400).
- **AuthZ:** `location:manage` required; otherwise `FORBIDDEN` (403).

## Appointment Validation & UX

- **Required fields:** `sourceType`, `sourceId`, `facilityId`, `scheduledStartDateTime`, `scheduledEndDateTime`.
- **Association checks:**
  - Vehicle must belong to customer: mismatch → `VEHICLE_CUSTOMER_MISMATCH` (409).
  - Not found: `CUSTOMER_NOT_FOUND` or `VEHICLE_NOT_FOUND` (404).
- **Scheduling conflicts:**
  - Conflicts return `SCHEDULING_CONFLICT` (409) with `conflictType` = `SOFT`|`HARD`.
  - UX: show alternatives inline; allow override for `SOFT` via `overrideSoftConflicts=true` + `overrideReason`.
- **Idempotency:**
  - Header `Idempotency-Key` required for create; duplicate key within 24h replays prior success.
  - UX: retry-safe; show banner with correlation/request ID if provided.
- **Timezone:**
  - Store UTC; display in facility timezone (`facilityTimeZoneId` IANA).
  - Input/output include ISO-8601 offset (e.g., `-05:00`).

### Detailed UI Patterns (Vue 3 + Quasar)

- **Form Components:** Use `QForm` with `QInput`, `QSelect`, and `QDate`/`QTime` combos; validate on submit and per-field.
- **Conflict UX:**
  - Render a `QBanner` with `type="warning"` when `SCHEDULING_CONFLICT` occurs.
  - List `suggestedAlternatives` as clickable chips; selecting one updates start/end.
  - Show `QCheckbox` for "Override soft conflict" when `conflictType=SOFT`, and a `QInput` for `overrideReason` (required when overriding).
- **Association Errors:**
  - If `VEHICLE_CUSTOMER_MISMATCH`, highlight vehicle picker; show inline helper text and disable submit until fixed.
  - For 404s, display a `QDialog` explaining the missing entity and provide navigation to search.
- **Idempotency & Retry:**
  - Generate UUID per form submit; persist in local state until completion.
  - On network retry, reuse the same Idempotency-Key.
  - Display correlation/request ID in `QBanner` for supportability.
- **Timezone Input/Display:**
  - Show times in facility timezone; display zone abbreviation (e.g., EST/EDT) near the inputs.
  - Convert user input to ISO-8601 with offset before sending; on load, render using facility zone.

## Error Handling → UX Mapping

- **400 (VALIDATION_ERROR):**
  - Map `fieldErrors` to inputs; keep user entries intact, focus first invalid field.
- **401 (Unauthorized):**
  - Redirect to login or show auth-required modal; preserve navigation intent.
- **403 (Forbidden):**
  - Show access-denied state; disable create actions where permission is known.
- **404 (Not Found):**
  - Show not-found page or inline message; provide back navigation.
- **409 (Conflict):**
  - Bay name duplicate → inline field error on `name`.
  - Scheduling conflict → inline alternatives and override affordance for `SOFT` conflicts.
- **5xx (Server errors):**
  - Show generic error banner; allow retry; capture correlation/request ID.

### Implementation Notes (Frontend)

- Centralize error mapping in an HTTP interceptor; normalize backend envelopes to `{ code, message, fieldErrors, correlationId }`.
- For field errors, bind `error`/`error-message` on Quasar inputs; focus the first invalid field.
- Persist minimal form state on navigation so users don’t lose input after errors.

## Accessibility & Responsive Expectations

- **Labels & focus:** Ensure descriptive labels; move focus to first error; announce errors for screen readers.
- **Keyboard:** All actions accessible via keyboard; clear focus states.
- **Responsive:** Tablet-first layout; forms stack fields; list views paginate with accessible controls.
- **Empty states:** Provide helpful guidance and retry options for lookup pickers; allow save without optional constraints.

### Additional A11y Guidance (Quasar)

- Use `aria-describedby` to associate error messages with inputs; ensure `role="alert"` on error banners.
- Ensure keyboard focus moves to the conflict banner on `409` responses; provide shortcuts to select alternatives.
- Verify color contrast for warning/error states meets WCAG 2.1 AA.

## Next Actions

- Implement frontend validation per above; backend remains authoritative.
- Confirm final enums for `bayType` and `status`.
- Document conflict override flow in issue #139.
- Post summarized clarifications to GitHub issues (#141 and #139) and remove `blocked:clarification` when dependencies are implemented.
