## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Contacts: Manage Communication Preferences & Consent Flags (Per Person)

### Primary Persona
Customer Service Representative (CSR)

### Business Value
Ensure outbound communications (notifications/marketing) follow the customer’s stated preferred channel and consent choices, improving compliance and customer experience while providing an auditable record of changes.

---

## 2. Story Intent

### As a / I want / So that
- **As a** CSR  
- **I want** to view and update a person’s communication preferences (preferred channel + email/SMS consent flags)  
- **So that** communications adhere to customer preferences and consent, with a clear audit trail.

### In-scope
- UI to **view** and **edit** a person’s:
  - preferred communication channel
  - email marketing consent flag
  - SMS notification consent flag
  - “last updated” timestamp and “update source” (viewable; source may be system-populated)
- Moqui screen(s), forms, transitions, and service calls required for:
  - load existing preferences for a person
  - create initial preference record if missing
  - update existing preference record
- Standard error handling and empty states
- Basic role-gating for CSR access (exact permission name is an open question)

### Out-of-scope
- Defining or changing CRM domain business rules (controlled vocabularies, lifecycle policies)
- Customer dedup/merge behavior
- Workorder Execution notification sending logic (consumer use is “stubbed”)
- Bulk updates across multiple people
- Managing contact points (emails/phones) themselves

---

## 3. Actors & Stakeholders
- **CSR (primary user):** updates preferences on behalf of a customer/person.
- **Customer/Person:** whose preferences are recorded.
- **Downstream consumers (e.g., Workorder Execution):** read preferences to choose channel; not modified here.
- **CRM backend services:** system of record for preferences and audit metadata.

---

## 4. Preconditions & Dependencies
- CSR is authenticated in the POS frontend.
- CSR is authorized to view/edit CRM contact data (permission/scope naming TBD).
- A **Person/Party** record exists and the CSR is operating in that person’s context (personId/partyId).
- Backend endpoints/services exist (or will exist per backend story #107) to:
  - retrieve a person’s communication preference record
  - create/update it
- Frontend has a consistent mechanism to attach correlation/request IDs and handle 401/403/404/409/500.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an existing **Contact/Person detail** area in POS:
  - “Communication Preferences” section/tab/panel.
- Direct navigation URL (suggested Moqui route; exact base path depends on app conventions):
  - `/crm/contact/preferences?partyId=<id>` or nested under contact detail screen.

### Screens to create/modify
- **Modify existing Contact/Person detail screen** to include a “Communication Preferences” section.
- **New or nested screen** for edit flow if not inline:
  - Screen: `apps/pos/screens/crm/contact/CommunicationPreferences.xml` (name illustrative; align with repo conventions)

### Navigation context
- Breadcrumb: Contacts → {Person Name} → Communication Preferences
- Must maintain the selected person context (partyId/personId) across transitions.

### User workflows
**Happy path (existing preferences)**
1. CSR opens person record.
2. UI loads current preference record.
3. CSR edits preferred channel and/or consent flags.
4. CSR saves; UI shows success and updated “last updated/source”.

**Happy path (no preferences yet)**
1. CSR opens person record.
2. UI shows “No preferences recorded yet”.
3. CSR enters values and saves.
4. UI now shows persisted values + audit metadata.

**Alternate paths**
- CSR cancels edits → no changes persisted.
- Backend validation error → show field-level errors where possible.
- Unauthorized → show access denied message and disable editing.
- Person not found → show not found state and link back to search.

---

## 6. Functional Behavior

### Triggers
- Entering the Communication Preferences view for a specific person.
- Clicking “Edit” (if read-only view by default) or editing inline.
- Clicking “Save”.

### UI actions
- View mode:
  - Display preferred channel, consent flags, last updated timestamp (UTC rendered in local time), update source.
- Edit mode:
  - Preferred channel selection control (controlled vocabulary).
  - Consent flags toggles (Email marketing, SMS notifications).
  - Save/Cancel actions.
- Save action:
  - Calls backend create/update service.
  - Shows success toast/alert and returns to view mode.

### State changes (frontend)
- `loading` → `loaded`
- `editing` toggles
- `saving` → `saved` or `error`
- Local dirty-tracking to prevent accidental navigation loss (confirm dialog)

### Service interactions (frontend → Moqui/backend)
- On load: fetch preference by person identifier.
- On save:
  - If record exists: update call.
  - If record missing: create call (or upsert if backend supports).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Preferred channel:
  - Must be one of allowed enum values: `EMAIL`, `SMS`, `PHONE`, `NONE` (from backend story).
  - UI must prevent submission if not selected **(unless backend allows null; backend says Not Null)**.
- Consent flags:
  - UI must submit booleans or null per backend contract.
  - UI must clearly communicate that “unset/null” is treated as **opt-out** downstream (per backend rule).
- updateSource:
  - Mandatory per backend rule.
  - UI must provide a value or ensure the integration layer supplies it consistently.

### Enable/disable rules
- If user lacks edit permission:
  - Fields are read-only; hide/disable Save; show informational banner.
- While saving:
  - Disable inputs and Save to prevent duplicate submits.

### Visibility rules
- If no record exists:
  - Show empty-state copy and “Add preferences” CTA.
- Always show audit metadata when available:
  - `updatedTimestampUTC`
  - `updateSource`

### Error messaging expectations
- 400 validation errors:
  - Show a top-level “Fix the highlighted fields” message.
  - Map known field errors to corresponding controls (channel/consents).
- 403:
  - “You don’t have permission to update communication preferences.”
- 404:
  - If person not found: “Contact not found.”
  - If preference not found (but person exists): treat as empty state, not error (depends on backend response; see Open Questions).
- 409:
  - Show “Preferences were updated by someone else. Reload and try again.” (if backend supports optimistic locking/versioning; otherwise open question).

---

## 8. Data Requirements

### Entities involved (conceptual; CRM-owned)
- `CommunicationPreference` (primary)
- `ConsentRecord` (mentioned in frontend prompt; backend story uses two boolean fields—needs clarification whether a separate entity exists)

### Fields (frontend)
**Identifier/context**
- `partyId` or `customerId` (UUID) — required context to load/save.

**Editable**
- `preferredCommunicationChannel` (Enum; required)
- `emailMarketingConsent` (Boolean nullable)
- `smsNotificationConsent` (Boolean nullable)

**Read-only (display)**
- `updatedTimestampUTC` (DateTime UTC)
- `updateSource` (String)

### Defaults
- Consent flags:
  - If backend returns null: display as “Not given / Opted out (default)” (wording must be confirmed).
- Preferred channel:
  - No default should be assumed unless backend defines one (do not guess).

### Read-only vs editable by state/role
- CSR with edit permission: editable fields above.
- Without permission: all fields read-only.

### Derived/calculated fields
- “Effective consent” display helper:
  - If value is `true` → “Opted in”
  - If `false` or `null` → “Opted out” (but preserve null vs false in edit UI if backend distinguishes)

---

## 9. Service Contracts (Frontend Perspective)

> Note: exact service names/routes in Moqui are not provided in inputs; these are **integration placeholders** and require confirmation (see Open Questions). The frontend must be written to a stable contract.

### Load/view calls
- **Operation:** Get communication preferences for a person
- **Input:** `partyId` (UUID)
- **Expected outcomes:**
  - `200 OK` with `CommunicationPreference` payload
  - `404 Not Found` if person does not exist
  - Either `404` (preference missing) or `200` with empty/null object (needs clarification)

### Create/update calls
- **Operation:** Create or update preferences
- **Input payload:**
  - `partyId`/`customerId` (UUID)
  - `preferredCommunicationChannel` (Enum)
  - `emailMarketingConsent` (Boolean|null)
  - `smsNotificationConsent` (Boolean|null)
  - `updateSource` (String) — must be sent or injected
- **Expected outcomes:**
  - `201 Created` for initial create OR `200 OK` for update (exact codes TBD)
  - `400 Bad Request` for validation issues
  - `403 Forbidden` if unauthorized
  - `404 Not Found` if person missing
  - `409 Conflict` if concurrency/version conflict (if implemented)

### Submit/transition calls
- Not a lifecycle workflow; no additional transitions beyond save/cancel UI transitions.

### Error handling expectations
- Errors must be surfaced non-destructively with:
  - preserved form state
  - clear message
  - ability to retry
- Logs/telemetry: client-side capture of failure with correlation id (if available) without exposing PII.

---

## 10. State Model & Transitions

### Allowed states (UI-level)
- `VIEW` (loaded, not editing)
- `EDIT` (dirty tracking enabled)
- `SAVING`
- `ERROR` (recoverable)

### Role-based transitions
- CSR with permission:
  - VIEW → EDIT → SAVING → VIEW
- CSR without permission:
  - VIEW only; EDIT not allowed

### UI behavior per state
- VIEW: show values + metadata; show Edit button if permitted.
- EDIT: show inputs + Save/Cancel; show warning on navigation if dirty.
- SAVING: disable inputs; show progress indicator.
- ERROR: show error banner and allow retry or cancel.

---

## 11. Alternate / Error Flows

### Validation failures (400)
- Backend returns validation errors (invalid enum, wrong types).
- UI maps:
  - `preferredCommunicationChannel` invalid → field error “Select a valid channel.”
  - consent invalid → field error “Consent must be yes/no/unset.”
  - missing updateSource → blocking error (should not occur if UI injects)

### Concurrency conflicts (409) — if supported
- When save returns 409:
  - UI prompts reload; offer “Reload preferences” action that re-fetches and discards local edits (after confirmation).

### Unauthorized access (401/403)
- 401: redirect to login/session renewal per app convention.
- 403: stay on page, disable editing, show banner.

### Empty states
- No preference record:
  - Show empty state + Add button (enters EDIT with blank form).
- No person context / missing partyId:
  - Show error and link back to Contacts search.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View existing communication preferences
**Given** I am an authenticated CSR with permission to view CRM contacts  
**And** I am viewing a person with an existing CommunicationPreference record  
**When** I open the “Communication Preferences” section  
**Then** I see the preferred communication channel  
**And** I see the email marketing consent value  
**And** I see the SMS notification consent value  
**And** I see “last updated” timestamp and “update source”.

### Scenario 2: Create preferences when none exist
**Given** I am an authenticated CSR with permission to edit CRM contacts  
**And** I am viewing a person with no existing CommunicationPreference record  
**When** I choose “Add preferences”  
**And** I set preferred channel to “EMAIL”  
**And** I set email marketing consent to opt-in  
**And** I set SMS notification consent to opt-out  
**And** I click “Save”  
**Then** the system persists the preferences  
**And** I return to view mode showing the saved values  
**And** the “last updated” timestamp is populated  
**And** the “update source” is populated with the configured CSR source identifier.

### Scenario 3: Update only the preferred channel and keep other fields unchanged
**Given** I am an authenticated CSR with permission to edit CRM contacts  
**And** a person has existing preferences with preferred channel “EMAIL” and email marketing consent set to opt-in  
**When** I edit the preferences and change preferred channel to “SMS”  
**And** I click “Save”  
**Then** the preferred channel is “SMS”  
**And** the email marketing consent remains opt-in  
**And** the SMS notification consent remains unchanged  
**And** the “last updated” timestamp is updated.

### Scenario 4: Display null consent as opt-out (informational)
**Given** I am an authenticated CSR with permission to view CRM contacts  
**And** a person’s SMS notification consent is null  
**When** I view the Communication Preferences section  
**Then** the UI indicates SMS consent is not granted (treated as opted out by default)  
**And** the raw value remains unchanged unless I explicitly set it.

### Scenario 5: Validation error on invalid preferred channel
**Given** I am an authenticated CSR with permission to edit CRM contacts  
**When** I attempt to save preferences with an invalid preferred channel value  
**Then** the save fails with a validation error  
**And** I see an error message indicating the channel is invalid  
**And** my entered values remain on screen for correction.

### Scenario 6: Unauthorized edit attempt
**Given** I am an authenticated CSR without permission to edit CRM contacts  
**When** I open the Communication Preferences section  
**Then** I can view the existing values (if authorized to view)  
**And** I cannot edit or save changes  
**And** I see a message indicating I lack permission to update preferences.

---

## 13. Audit & Observability

### User-visible audit data
- Display:
  - `updatedTimestampUTC` (rendered in user locale/timezone)
  - `updateSource` (string identifier)
- If backend provides actor name/id, show only if permitted and not sensitive (not required by this story).

### Status history
- Not required unless backend provides audit history endpoint (out of scope). Only “last updated” is required.

### Traceability expectations
- Frontend should attach/request a correlation ID (if app standard) and log:
  - page load failures
  - save failures
  - 403/409 occurrences
- Do not log customer PII beyond identifiers already in URL/context.

---

## 14. Non-Functional UI Requirements
- **Performance:** Preference load should not block rendering of the rest of contact detail; show skeleton/loading indicator for this section.
- **Accessibility:** All controls keyboard-navigable; toggles have accessible labels; error messages announced (ARIA).
- **Responsiveness:** Works on tablet-sized POS devices; form fields stack vertically on narrow widths.
- **i18n/timezone:** Render UTC timestamp in local timezone; all labels/messages use i18n keys if project convention exists.
- **Security:** Do not expose preferences in console logs; respect authorization errors without leaking details.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-state message and “Add preferences” CTA when no preference record exists; safe because it does not change business rules, only UI affordance. (Impacted: UX Summary, Error Flows)
- SD-ERR-MAP-HTTP: Map HTTP 400/401/403/404/409/500 into consistent toast/banner patterns; safe because it follows backend status semantics without inventing policies. (Impacted: Business Rules, Service Contracts, Error Flows)
- SD-UX-DIRTY-CONFIRM: Add dirty-form navigation confirmation on unsaved edits; safe because it’s UI ergonomics only. (Impacted: Functional Behavior, UX Summary)

---

## 16. Open Questions
1. **Identifier naming:** In frontend context, is the person key `partyId`, `personId`, or `customerId`? Which is passed in routes and to services?
2. **Backend contract specifics:** What are the exact endpoints or Moqui services to call for:
   - load preferences
   - create/update preferences  
   (Include request/response schemas and error payload format.)
3. **Missing record behavior:** When a person exists but has no preferences yet, does the backend return:
   - `404` (preference not found), or
   - `200` with null/empty payload?
4. **updateSource handling:** Should the frontend send `updateSource` explicitly (e.g., constant `CSR_PORTAL`), or will Moqui/backend infer it from authenticated client/app? If constant, what is the exact required value?
5. **ConsentRecord entity:** Does the frontend need to manage a separate `ConsentRecord` entity, or are consent flags only fields on `CommunicationPreference` (as in backend story #107)?
6. **Authorization model:** What permission/role controls viewing vs editing communication preferences in the POS frontend (exact roles/scopes)? Should view be allowed when edit is denied?
7. **Optimistic locking:** Is there a version/ETag field to include for concurrency control? If yes, what field and how should 409 be resolved (reload vs overwrite)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Contacts: Store Communication Preferences and Consent Flags — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/171

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Contacts: Store Communication Preferences and Consent Flags
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/171
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Contacts: Store Communication Preferences and Consent Flags

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to record preferred channel and opt-in/opt-out flags** so that **communications follow customer preferences**.

## Details
- Per-person preferences: preferred channel; basic consent flags for SMS/email.
- Track last-updated and source.

## Acceptance Criteria
- Can set/get preferences.
- Consent flags are available via API.
- Audit is captured.

## Integration Points (Workorder Execution)
- Workorder Execution uses preferences to select notification channel (stubbed initially).

## Data / Entities
- CommunicationPreference
- ConsentRecord

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*

====================================================================================================

BACKEND STORY REFERENCES (FOR REFERENCE ONLY)

----------------------------------------------------------------------------------------------------

Backend matches (extracted from story-work):


[1] backend/107/backend.md

    Labels: type:story, domain:crm, status:ready-for-dev

----------------------------------------------------------------------------------------------------

END BACKEND REFERENCES