# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates People-domain story implementations (People/HR, Users,
Timekeeping UI) against the domain’s normalized decisions. It is designed to
prevent UI-side policy drift, ensure error handling and authorization are
consistent, and ensure list/report screens are safe (paging, idempotency, and
non-leaky authorization).

## Completed items

- [x] Rewrote checklist to required template structure
- [x] Added acceptance criteria for every previously listed open question

## Scope / Ownership

- [ ] Feature belongs to People domain UI and does not re-implement WorkExec/
      ShopMgr business rules.
- [ ] Read-only screens do not expose mutation controls.
- [ ] Mutations are done only via backend contracts (no local business rule
      computation).

## Data Model & Validation

- [ ] Required fields validated client-side; 400 field errors rendered without
      data loss.
- [ ] Date validations enforced:
  - [ ] Range filters: `from <= thru`.
  - [ ] Effective dating: `effectiveStartAt <= effectiveEndAt`.
- [ ] Enums validated against authoritative values where available.
- [ ] Effective dating uses half-open interval semantics for “active”
      determination. (Decision ID: DECISION-PEOPLE-014)

## API Contract

- [ ] People APIs follow consistent REST conventions for paging and error
      schema. (Decision ID: DECISION-PEOPLE-021, DECISION-PEOPLE-018)
- [ ] Break start/end derives identity/session from auth context and treats 409
      as refresh-required. (Decision ID: DECISION-PEOPLE-022)
- [ ] Assignment list/create/end is implemented and primary semantics reflect
      server outcomes. (Decision ID: DECISION-PEOPLE-004, DECISION-PEOPLE-023)
- [ ] Role assignment list/create/end is implemented with active-only default
      and history toggle. (Decision ID: DECISION-PEOPLE-026)
- [ ] Report filter encoding matches the decided query encoding.
      (Decision ID: DECISION-PEOPLE-020)

## Events & Idempotency

- [ ] UI does not assume delivery semantics; it always re-renders based on
      server state.
- [ ] Retry-safe behaviors:
  - [ ] Approve/reject period actions are idempotent from the UI perspective.
  - [ ] Break start/end handles “already started/ended elsewhere” with a
        refresh flow.

## Security

- [ ] Backend enforces authorization; UI gates by named permissions and
      capability flags.
- [ ] No resource existence leakage via error differences (403/404 handling
      consistent).
- [ ] `tenantId` is not displayed by default. (Decision ID: DECISION-PEOPLE-019)

## Observability

- [ ] Correlation/request IDs are surfaced in error UI when present.
- [ ] UI logs do not contain PII, notes, or full payloads.

## Performance & Failure Modes

- [ ] Lists are paged and stable-sorted; no unbounded loads.
- [ ] Report preserves last successful results on subsequent failure.
- [ ] Clear UI states for 401/403/404/409 and retryable failures.

## Testing

- [ ] Unit tests cover client-side date validation, required fields, and query
      serialization.
- [ ] Integration tests validate error schema parsing (400/409) and
      refresh-on-409 flows.

## Documentation

- [ ] Document final endpoint/service names and permissions for each screen.
- [ ] Document timezone display rules and any server-side report range limits.

## Acceptance Criteria (per resolved question)

### 1) TimekeepingEntry list/detail endpoints and schemas are defined

- Decision ID: DECISION-PEOPLE-021
- Verify:
  - [ ] List uses paging parameters consistently.
  - [ ] Detail endpoint returns all fields required for display without UI
        inference.

### 2) Permission hook and Payroll Clerk access are implementable

- Decision ID: DECISION-PEOPLE-013
- Verify:
  - [ ] UI gates by named permissions (not role-name heuristics).
  - [ ] Unauthorized users see access denied without leaking resource
        existence.

### 3) `tenantId` is treated as sensitive

- Decision ID: DECISION-PEOPLE-019
- Verify:
  - [ ] `tenantId` is not displayed unless explicitly enabled for admin/support.

### 4) TimekeepingEntry rejection metadata and approval history are handled

- Decision ID: DECISION-PEOPLE-006, DECISION-PEOPLE-021
- Verify:
  - [ ] Detail screen renders available rejection metadata/history if provided.
  - [ ] If fields are absent, UI does not invent/derive them.

### 5) Employee name display is deterministic

- Decision ID: DECISION-PEOPLE-012, DECISION-PEOPLE-021
- Verify:
  - [ ] If API provides display name, UI uses it; otherwise UI performs an
        explicit People lookup.

### 6) Break flow endpoints and error semantics are implemented

- Decision ID: DECISION-PEOPLE-022, DECISION-PEOPLE-018
- Verify:
  - [ ] Start/end break uses auth-derived context.
  - [ ] 409 conflicts trigger a refresh of current context.

### 7) Break identity/session identifiers are not client-guessed

- Decision ID: DECISION-PEOPLE-022
- Verify:
  - [ ] UI does not require passing timecard/session IDs unless explicitly
        required by backend.

### 8) `breakType=OTHER` notes handling matches policy

- Decision ID: DECISION-PEOPLE-016
- Verify:
  - [ ] Notes are optional; UI prompts but does not hard-block.

### 9) Timezone display is consistent

- Decision ID: DECISION-PEOPLE-015
- Verify:
  - [ ] UI displays in user timezone when available; otherwise falls back to
        location timezone.

### 10) “Last used break type” behavior is safe

- Decision ID: DECISION-PEOPLE-022
- Verify:
  - [ ] UI may store last-used locally; it does not require backend support.

### 11) PersonLocationAssignment endpoints exist (list/create/end + location picker)

- Decision ID: DECISION-PEOPLE-023
- Verify:
  - [ ] Location picker returns selectable active locations.
  - [ ] Create/end flows refresh and render server results.

### 12) Assignment `role` optionality and values are handled

- Decision ID: DECISION-PEOPLE-004
- Verify:
  - [ ] UI supports `role=null` and uses picker only if enum provided.

### 13) Primary uniqueness scope is enforced and observable

- Decision ID: DECISION-PEOPLE-004
- Verify:
  - [ ] Only one primary assignment per person at a time.
  - [ ] UI reflects automatic demotion after refresh.

### 14) Effective end semantics are exclusive

- Decision ID: DECISION-PEOPLE-014
- Verify:
  - [ ] “Active” logic uses half-open interval semantics.

### 15) Assignment editability follows end+create preference

- Decision ID: DECISION-PEOPLE-014, DECISION-PEOPLE-003
- Verify:
  - [ ] UI uses end+create rather than in-place edits for core fields.

### 16) Permissions gate view vs manage for assignments

- Decision ID: DECISION-PEOPLE-013
- Verify:
  - [ ] View-only users cannot access mutation actions.

### 17) Assignment reason codes are optional in v1

- Decision ID: DECISION-PEOPLE-023
- Verify:
  - [ ] UI does not hard-require reason code unless backend indicates it.

### 18) Employee profile edit endpoint/schema is authoritative

- Decision ID: DECISION-PEOPLE-021
- Verify:
  - [ ] Edit screen loads via a single authoritative endpoint/schema.

### 19) Standard error response format is used

- Decision ID: DECISION-PEOPLE-018
- Verify:
  - [ ] 400 renders field errors.
  - [ ] 409 renders conflict with actionable refresh guidance.

### 20) Routing/menu conventions for People screens are consistent

- Decision ID: DECISION-PEOPLE-021
- Verify:
  - [ ] Navigation does not create duplicate entry points across modules.

### 21) Default employee status on create is ACTIVE

- Decision ID: DECISION-PEOPLE-025
- Verify:
  - [ ] Create form defaults to ACTIVE unless user selects otherwise.

### 22) Terminated employee edit rules are enforced

- Decision ID: DECISION-PEOPLE-025
- Verify:
  - [ ] Terminated employees are read-only by default.

### 23) Optimistic concurrency is honored when provided

- Decision ID: DECISION-PEOPLE-017
- Verify:
  - [ ] UI submits version token and handles 409 by refresh.

### 24) Role assignment list/create/end endpoints exist and support dating

- Decision ID: DECISION-PEOPLE-026
- Verify:
  - [ ] Ending supports future-dated ends.
  - [ ] Backdated ends return 409 when invalid.

### 25) Role assignment end reason codes are not hard-required by default

- Decision ID: DECISION-PEOPLE-026
- Verify:
  - [ ] UI can capture reason text but does not block without reason unless
        backend requires it.

### 26) Role assignment list defaults active-only with history toggle

- Decision ID: DECISION-PEOPLE-026
- Verify:
  - [ ] History is visible only when user opts in.

### 27) Disable user detail load and disable action are implementable

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] Backend capability/options are used to render disable options.

### 28) statusReasonCode is optional unless backend requires it

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] UI does not hard-require reason code unless backend indicates required.

### 29) Disable confirmation UX is explicit

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] A confirm step exists and is not bypassable.

### 30) Post-disable navigation stays on refreshed detail

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] UI refreshes detail and remains on page.

### 31) Discrepancy report technician filter encoding and range behavior

- Decision ID: DECISION-PEOPLE-020
- Verify:
  - [ ] `technicianId=<id>` repeated query params are used.
  - [ ] UI handles server-enforced max date range and messages clearly.

## End

End of document.
