## Objective
Create a phased plan to unblock all `blocked:clarification` location issues by resolving open questions, confirming contracts, and posting resolutions as comments on the actual GitHub issues in `durion-moqui-frontend`.

## Scope (Unresolved Issues)
- Issue #141 — Locations: Create Bays with Constraints and Capacity (`domain:location`, `blocked:clarification`).
- Issue #139 — Scheduling: Create Appointment with CRM Customer and Vehicle (`domain:location`, `blocked:clarification`).

## Phased Plan

### Phase 1 – Contract & Ownership Confirmation
- [ ] Confirm authoritative domain/label for appointments (shopmgr vs location/workexec) per DECISION-INVENTORY-010; update issue #139 label if needed.
- [ ] Confirm canonical screen routes and parameter names for location subresources (bays) and appointments in `durion-moqui-frontend` (e.g., `LocationDetail`, `BayCreate`, `AppointmentEdit`).
- [ ] Confirm REST/service path patterns and envelopes for location bays (`/rest/api/v1/location/...`) and scheduling/appointments (shopmgr screen/service paths).
- [ ] Capture standard error envelope shape (code, message, fieldErrors, correlationId) for both issues; record conflict code for bay name (`BAY_NAME_TAKEN_IN_LOCATION`).
- [ ] Determine ID types and examples for service/skill lookups (issue #141) and CRM customer/vehicle IDs (issue #139).

### Phase 2 – Data & Dependency Contracts
- [ ] Services/Skills lookup: document exact endpoints, query params, and response fields `{id, displayName}` for `supportedServiceIds` and `requiredSkillRequirements.skillId` (issue #141).
- [ ] Bay API response: confirm identifier field (`bayId` vs `id`), returned fields (capacity, constraints), and pagination envelope for bay list (issue #141).
- [ ] Appointment contracts: document endpoints/DTOs for CRM customer search, CRM vehicles-by-customer, and appointment create (issue #139); include idempotency header usage.
- [ ] Timezone contract for appointment `startAt`/`endAt` (UTC vs local offset) and source of shop timezone (issue #139).
- [ ] Requested services structure: confirm if free-text only or catalog-backed IDs; document lookup if IDs required (issue #139).

### Phase 3 – UX/Validation Alignment
- [ ] Bay uniqueness and validation: confirm server-side rules for trimmed name, enum values for `bayType`, `status` (`ACTIVE`, `OUT_OF_SERVICE`), capacity bounds, and optional constraints (issue #141).
- [ ] Appointment validation: confirm required fields, vehicle-customer association checks, and any scheduling conflict codes plus expected UX (issue #139).
- [ ] Error handling: map HTTP codes to UX for both issues (400/401/403/404/409/5xx) and confirm correlation/request ID propagation.
- [ ] Accessibility and responsiveness expectations carried over from consolidated notes (labels, error focus, tablet layout) for both issues.

### Phase 4 – Issue Updates and Closure
- [ ] For each clarified item above, add a concise GitHub issue comment in `durion-moqui-frontend` with: the resolved contract details, open/closed questions, and next implementation steps.
- [ ] Update labels/status on each issue: remove `blocked:clarification` when clarifications are complete; ensure correct `domain:*` label (especially for appointments if shopmgr-owned).
- [ ] If any questions remain unanswered, list them explicitly in the issue comment with requested owner/domain to respond.

## Issue-Specific Checklists

### Issue #141 — Bays with Constraints and Capacity
- [ ] Confirm service lookup endpoint(s) (path, params, response fields).
- [ ] Confirm skills lookup endpoint(s) (path, params, response fields).
- [ ] Confirm bay create/view/list endpoints and response shapes (identifier field name, capacity, constraints arrays, pagination envelope).
- [ ] Confirm error envelope and conflict code for duplicate bay name (`BAY_NAME_TAKEN_IN_LOCATION`).
- [ ] Confirm ID formats for service/skill IDs (opaque string vs UUID) with examples.
- [ ] Post clarification comment to GitHub with the above details and any remaining questions; remove `blocked:clarification` when resolved.

### Issue #139 — Scheduling Appointment with CRM Customer and Vehicle
- [ ] Confirm authoritative domain/label (shopmgr vs location/workexec); update issue labels accordingly.
- [ ] Confirm endpoints/DTOs for CRM customer search and vehicles-by-customer (paths, pagination, fields).
- [ ] Confirm appointment create endpoint/DTO, including idempotency header requirement and success payload (`appointmentId`).
- [ ] Confirm timezone contract for `startAt`/`endAt` and how to source shop/user timezone.
- [ ] Confirm requested services structure (free text vs catalog IDs and lookup endpoint if IDs required).
- [ ] Confirm error codes for mismatches (`CUSTOMER_NOT_FOUND`, `VEHICLE_NOT_FOUND`, `VEHICLE_CUSTOMER_MISMATCH`) and any scheduling conflict code if applicable.
- [ ] Post clarification comment to GitHub with the above details and any remaining questions; remove `blocked:clarification` when resolved.

## Notes
- Use deterministic error mapping and preserve correlation/request IDs in user-visible error banners.
- Treat IDs as opaque; no client-side validation beyond presence/format neutrality.
- When commenting on GitHub issues, include references to DECISION-* items or domain guides where applicable and specify remaining blockers with owners.
