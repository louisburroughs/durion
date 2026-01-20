## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** crm-pragmatic

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Party: Associate Individuals to Commercial Account

## Primary Persona
Fleet Account Manager

## Business Value
Ensure workorder approval and billing contacts for a commercial account are unambiguous by maintaining CRM-owned, effective-dated relationships with controlled roles and a single primary billing contact.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Fleet Account Manager  
- **I want** to associate existing individual (Person) records to an existing commercial account (Organization) with relationship role(s) and effective dates, including managing the account’s primary billing contact  
- **So that** downstream processes (Workorder Execution, Billing) can reliably retrieve approvers and billing contacts.

## In-scope
- View a commercial account’s current and historical (inactive) linked individuals by role.
- Create a new relationship between an Organization and a Person with:
  - roleType (APPROVER or BILLING; system-defined)
  - effectiveStartDate (required)
  - optional effectiveEndDate (set by deactivation)
  - optional `isPrimaryBillingContact` (only meaningful for BILLING)
- Deactivate an existing relationship (logical end-date; no delete).
- Assign (or change) the single **primary billing contact** for the commercial account, with automatic demotion of any existing primary billing contact (atomic, backend-enforced).
- Frontend integration with Moqui screens/forms/transitions and the CRM service endpoints referenced by the backend story.

## Out-of-scope
- Creating or editing Person/Organization master data (names, addresses, etc.).
- Customer deduplication, merge, “golden record” logic.
- Defining/creating new role types (roles are system-defined).
- Workorder Execution UI or behavior (consumer only).
- Permission model design beyond enforcing 401/403 responses and hiding actions when unauthorized info is available.

---

# 3. Actors & Stakeholders

## Actors
- **Fleet Account Manager (primary):** manages account contacts/approvers/billing contacts.
- **Service Advisor (secondary/read-only):** may view contacts for operational context.

## Stakeholders
- **Billing Department:** depends on accurate billing contact designation.
- **Workorder Execution domain:** consumes approvers/billing contacts read API (no writes).
- **CRM service owner:** system of record for PartyRelationship data.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in the POS frontend.
- User has authorization to manage relationships for the selected commercial account (backend will enforce; UI should respect 403).
- The commercial account (Organization party) exists.
- Individuals (Person parties) to be linked already exist in CRM.

## Dependencies
- Backend CRM endpoints (from backend story #110) must exist and be reachable:
  - `GET /crm/commercial-accounts/{commercialAccountId}/contacts`
  - Create relationship endpoint (not fully specified in backend story; see Service Contracts section)
  - Deactivate relationship endpoint (not fully specified; see Service Contracts section)
  - Set primary billing contact (could be part of create/update; see Service Contracts section)
- Controlled vocabulary for relationship roles includes at minimum: `APPROVER`, `BILLING`.
- Moqui security configuration provides a way to determine current user identity for audit fields (via backend).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From Commercial Account detail context (e.g., “Commercial Account” screen): tab/section “Contacts & Relationships” (naming can match existing app conventions).

## Screens to create/modify
1. **Screen:** `CommercialAccount/Contacts` (new or extend existing account detail screen)
   - Purpose: view/manage individuals linked to the account with roles and effective dates.
2. **Dialog/Subscreen:** `CommercialAccount/AddContactRelationship` (new)
   - Purpose: create a new PartyRelationship between account and person.
3. **Dialog/Subscreen:** `CommercialAccount/DeactivateRelationship` (new)
   - Purpose: confirm and set deactivation (effectiveEndDate).
4. **Optional:** `CommercialAccount/SetPrimaryBillingContact` (may be integrated into list actions)
   - Purpose: assign primary billing contact among active BILLING relationships.

## Navigation context
- Route parameter: `commercialAccountId` (Organization party id).
- Screen should load contacts for this account on entry and after any mutation.

## User workflows

### Happy path: create relationship (APPROVER or BILLING)
1. User opens Commercial Account → Contacts & Relationships.
2. User clicks “Add Individual”.
3. User selects an existing Person (lookup/search control; must return `personId`).
4. User selects roleType (`APPROVER` or `BILLING`).
5. User selects effectiveStartDate.
6. If roleType = BILLING, user may check “Set as primary billing contact”.
7. User saves → list refreshes, new relationship appears as Active.

### Happy path: set/change primary billing contact
1. User identifies an active BILLING relationship row.
2. User clicks “Set as Primary Billing Contact”.
3. UI calls backend; on success, list refresh shows exactly one primary billing contact.

### Happy path: deactivate relationship
1. User clicks “Deactivate” on an active relationship row.
2. UI prompts for effectiveEndDate (default to today, editable).
3. User confirms → relationship becomes Inactive (end-dated) and no longer appears in Active-only view.

### Alternate: view inactive/historical relationships
- User toggles filter `status=INACTIVE` or `ALL` to see end-dated relationships.

---

# 6. Functional Behavior

## Triggers
- Screen load: entering Contacts & Relationships for a commercial account.
- User actions: create relationship, set primary billing contact, deactivate relationship, change filters (roles/status).

## UI actions
- **List display:** show relationships with columns:
  - Individual (name + personId reference)
  - roleType
  - effectiveStartDate
  - effectiveEndDate (blank if active)
  - status (derived: ACTIVE if endDate null or in future per backend semantics; otherwise INACTIVE)
  - primary billing indicator (only relevant for BILLING)
- **Filtering:**
  - Roles filter (multi-select): APPPROVER, BILLING
  - Status filter: ACTIVE (default), INACTIVE, ALL
- **Row actions (conditional):**
  - “Set Primary Billing Contact” visible only when:
    - roleType includes BILLING (or equals BILLING, depending on backend representation)
    - relationship is ACTIVE
    - relationship is not already primary
  - “Deactivate” visible only when relationship is ACTIVE
- **Create action:** “Add Individual” opens create dialog.

## State changes (frontend)
- Maintain local loading and error states for:
  - contacts list fetch
  - create relationship submit
  - primary billing set submit
  - deactivate submit
- After any successful mutation, re-fetch the contacts list using current filters to reflect backend-atomic changes (especially primary demotion).

## Service interactions
- On screen load and after mutations: call `GET /crm/commercial-accounts/{id}/contacts` with query params based on UI filters.
- On create relationship: call CRM create endpoint (see Section 9).
- On set primary: call CRM update endpoint (see Section 9).
- On deactivate: call CRM deactivate endpoint (see Section 9).

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Create relationship requires:
  - `commercialAccountId` (from route/context)
  - `personId` (selected)
  - `roleType` (must be one of allowed enums; UI should restrict to allowed list)
  - `effectiveStartDate` (required, valid date)
- Deactivation requires:
  - `effectiveEndDate` (required; valid date)
- If backend returns `400` for invalid role/date/format → show field-level error if mapped; otherwise show form-level error summary.

## Enable/disable rules
- Disable “Save” button until required fields present.
- Disable mutation actions while the request is in-flight.
- Disable “Set Primary Billing Contact” if row already primary or inactive.

## Visibility rules
- Primary billing contact indicator only displayed for BILLING role rows.
- Inactive relationships show effectiveEndDate and have no actions except view.

## Error messaging expectations
- `404` (account/person/relationship not found): show non-field error “Record not found. Refresh and try again.”
- `409` duplicate/overlap constraint: show “A relationship with this person and role overlaps an existing active date range.”
- `403` forbidden: show “You don’t have access to manage contacts for this account.”
- `500` unexpected: show generic error with correlation id if available.

---

# 8. Data Requirements

## Entities involved (conceptual; CRM-owned)
- `PartyRelationship`
- `Party` (Organization/Person) (read-only in this story)

## Fields (type, required, defaults)

### PartyRelationship (as used by frontend)
- `partyRelationshipId` (UUID, read-only)
- `fromPartyId` (UUID, required; commercialAccountId, read-only on edit)
- `toPartyId` (UUID, required; personId, read-only on edit)
- `roleType` (enum string, required; values at least `APPROVER`, `BILLING`)
- `isPrimaryBillingContact` (boolean, optional; only meaningful if roleType=BILLING; default false if null)
- `effectiveStartDate` (date/datetime, required)
- `effectiveEndDate` (date/datetime, optional; set on deactivation)
- Audit (read-only display if provided by API): `createdAt`, `createdBy`, `updatedAt`, `updatedBy`

### Derived/calculated (frontend)
- `status` derived for display/filtering:
  - ACTIVE when backend returns it as active OR effectiveEndDate is null (prefer backend’s explicit status if provided; otherwise simple derivation based on null end date only, no policy guessing about “future end date” unless backend provides).

## Read-only vs editable by state/role
- If relationship is ACTIVE:
  - editable via actions: set primary billing (affects `isPrimaryBillingContact`), deactivate (sets end date)
  - not editable: fromPartyId/toPartyId/roleType/effectiveStartDate in this story
- If relationship is INACTIVE:
  - all fields read-only (no reactivation in scope)

---

# 9. Service Contracts (Frontend Perspective)

> Note: Backend story explicitly defines only the **read** endpoint; mutation endpoints are not fully specified. Frontend will implement integration using Moqui services once the exact endpoints/services are confirmed. Until then, UI wiring should be structured to call a single Moqui facade service per action (create/deactivate/setPrimary) so endpoint changes are localized.

## Load/view calls

### Load contacts
- **HTTP:** `GET /crm/commercial-accounts/{commercialAccountId}/contacts`
- **Query params:**
  - `roles` optional comma-separated (e.g., `APPROVER,BILLING`)
  - `status` optional default `ACTIVE`: `ACTIVE|INACTIVE|ALL` (if backend only supports ACTIVE/INACTIVE, map ALL to no param or two calls—must be confirmed)
  - `includeIndividuals` optional default `true`
- **Expected response (minimum frontend needs):**
  - List of contact relationship records including:
    - partyRelationshipId
    - personId/toPartyId and display name
    - roleType
    - isPrimaryBillingContact
    - effectiveStartDate/effectiveEndDate
    - (optional) status

## Create/update calls (to be confirmed; frontend expectation)

### Create relationship
- **Proposed shape (needs confirmation):**
  - `POST /crm/commercial-accounts/{commercialAccountId}/contacts`
  - body: `{ personId, roleType, effectiveStartDate, isPrimaryBillingContact? }`
- **Success:** 201 Created (returns created relationship) OR 200 OK (returns updated list)
- **Errors:** 400, 403, 404, 409

### Set primary billing contact
- **Proposed shape (needs confirmation):**
  - `POST /crm/commercial-accounts/{commercialAccountId}/contacts/{partyRelationshipId}/set-primary-billing`
  - or `PATCH ...` with `{ isPrimaryBillingContact: true }`
- **Semantics:** backend demotes any existing primary atomically.
- **Success:** 200 OK

### Deactivate relationship
- **Proposed shape (needs confirmation):**
  - `POST /crm/party-relationships/{partyRelationshipId}/deactivate`
  - body: `{ effectiveEndDate }`
- **Success:** 200 OK

## Moqui integration expectation
- Use Moqui `transition` actions to invoke services; services should be thin wrappers around REST calls (or direct entity services if CRM is within same Moqui runtime—must match repo convention).
- Each UI mutation action should call a dedicated Moqui service, e.g.:
  - `crm.PartyRelationship.createForCommercialAccount`
  - `crm.PartyRelationship.setPrimaryBillingContact`
  - `crm.PartyRelationship.deactivate`
- Service should return structured errors suitable for field mapping (Moqui message errors).

## Error handling expectations
- Map HTTP/service errors to:
  - field errors when backend identifies field (roleType, effectiveStartDate)
  - otherwise show top-of-form error banner
- Always keep the user on the same screen/dialog after failure with inputs preserved.

---

# 10. State Model & Transitions

## Allowed states (relationship validity)
- **ACTIVE:** effectiveEndDate is null (or backend status=ACTIVE)
- **INACTIVE:** effectiveEndDate is set (or backend status=INACTIVE)

## Role-based transitions
- Fleet Account Manager:
  - ACTIVE → INACTIVE via Deactivate action
  - ACTIVE BILLING relationship: set as primary billing contact (primary flag swap occurs across relationships, but the clicked relationship becomes primary)

## UI behavior per state
- ACTIVE:
  - show Deactivate
  - show Set Primary Billing Contact (if BILLING and not primary)
- INACTIVE:
  - hide Deactivate and Set Primary actions
  - display effectiveEndDate prominently in row details (behavioral requirement: must be visible)

---

# 11. Alternate / Error Flows

## Validation failures
- Missing required fields on create/deactivate: prevent submit; show inline required indicators.
- Backend 400: show returned validation message(s); keep dialog open.

## Concurrency conflicts
- If backend responds 409 on primary update or relationship creation due to overlap/duplicate:
  - show conflict message
  - re-fetch list to reflect current server state
  - keep dialog open for user adjustment (for create) / keep user on list (for set primary)

## Unauthorized access
- 401: redirect to login (standard app behavior).
- 403: show access error; disable mutation controls; allow read-only view if read endpoint succeeds, otherwise show forbidden state.

## Empty states
- No ACTIVE contacts: show empty state with action “Add Individual”.
- No results for selected filters: show “No contacts match filters” with reset filters action.

---

# 12. Acceptance Criteria

## Scenario 1: View active approvers and billing contacts (default)
**Given** I am authenticated as a Fleet Account Manager  
**And** a commercial account exists with linked contacts of roles APPROVER and BILLING  
**When** I open the commercial account’s “Contacts & Relationships” screen  
**Then** the UI loads contacts using status=ACTIVE by default  
**And** I can see each contact’s role, effectiveStartDate, and whether they are the primary billing contact (for BILLING)  

## Scenario 2: Create an APPROVER relationship
**Given** I am on a commercial account’s “Contacts & Relationships” screen  
**And** an individual Person exists in CRM  
**When** I add the individual with roleType=APPROVER and an effectiveStartDate  
**And** I save  
**Then** the relationship is created successfully  
**And** the contacts list refreshes and shows the new APPROVER relationship as active  

## Scenario 3: Create a BILLING relationship and set as primary
**Given** I am on a commercial account’s “Contacts & Relationships” screen  
**And** no primary billing contact is currently set OR another primary billing contact exists  
**When** I add the individual with roleType=BILLING and check “Set as primary billing contact”  
**And** I save  
**Then** the relationship is created successfully  
**And** exactly one relationship is shown as primary billing contact after refresh  
**And** if another primary billing contact existed previously, it is no longer primary after refresh  

## Scenario 4: Change primary billing contact (atomic demotion)
**Given** I am viewing active BILLING relationships for a commercial account  
**And** there is an existing primary billing contact  
**When** I click “Set as Primary Billing Contact” on a different active BILLING relationship  
**Then** the selected relationship becomes primary  
**And** the previously primary relationship is no longer primary after the list refresh  
**And** no error is shown  

## Scenario 5: Deactivate a relationship
**Given** I am viewing an active relationship row  
**When** I deactivate the relationship and provide an effectiveEndDate  
**Then** the relationship is end-dated successfully  
**And** it no longer appears in the ACTIVE filter view after refresh  
**And** it appears in the INACTIVE or ALL filter view with effectiveEndDate displayed  

## Scenario 6: Prevent duplicate overlapping active relationship (conflict)
**Given** there is already an active relationship for the same person and role with an overlapping effective date range  
**When** I attempt to create another relationship that overlaps  
**Then** the UI shows a conflict error message  
**And** the relationship is not created  
**And** my entered values remain in the form for correction  

## Scenario 7: Unauthorized attempt
**Given** I am authenticated but not authorized to manage contacts for the commercial account  
**When** I attempt to create, deactivate, or set primary billing contact  
**Then** the backend returns 403  
**And** the UI shows an authorization error  
**And** no changes are applied  

---

# 13. Audit & Observability

## User-visible audit data
- If API returns audit fields, display in a relationship details view (e.g., expandable row detail):
  - createdAt/createdBy
  - updatedAt/updatedBy

## Status history
- Relationship history is represented via effectiveStartDate/effectiveEndDate and inclusion in INACTIVE filter.
- No separate “history timeline” UI required beyond being able to view inactive relationships.

## Traceability expectations
- Each mutation request should include/propagate a correlation id (Moqui/HTTP header pattern per repo conventions) and log it in frontend error logs (without PII beyond IDs).
- Frontend should log action type and involved IDs (commercialAccountId, partyRelationshipId) at info level in dev tools (or Moqui logs if server-side screen actions), respecting safe logging (no names/emails/phones).

---

# 14. Non-Functional UI Requirements

- **Performance:** contacts list should load within 2 seconds under normal conditions for typical accounts (<200 relationships). If larger, UI should remain responsive (show loading state).
- **Accessibility:** all actions reachable by keyboard; dialogs have focus trapping; labels for form controls; error messages associated with fields.
- **Responsiveness:** usable on tablet; table may collapse to stacked rows/cards on small screens (implementation choice).
- **i18n/timezone:** dates displayed in the user’s locale/timezone; input uses standard date picker; store/send ISO date/datetime as required by backend.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Added explicit empty/filter-no-results states with a clear “Add Individual” CTA; qualifies as safe because it does not change business logic, only UX clarity. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-LOADING-AND-RETRY: Standard loading indicators and retry affordance on failed fetch; qualifies as safe because it’s generic error-handling ergonomics. (Impacted: Functional Behavior, Alternate/Error flows)
- SD-ERR-HTTP-STATUS-MAP: Standard mapping of 400/403/404/409/500 to user-facing messages; qualifies as safe because it follows backend-implied semantics without inventing policies. (Impacted: Business Rules, Error Flows, Service Contracts)

---

# 16. Open Questions

- What are the **exact CRM mutation endpoints/service names** for:
  1) creating a PartyRelationship for a commercial account,  
  2) deactivating a PartyRelationship (setting effectiveEndDate),  
  3) setting primary billing contact (and whether it’s part of create/update)?  
- Does the read endpoint `GET /crm/commercial-accounts/{id}/contacts` support `status=ALL`, or must the frontend implement ALL via multiple calls/client-side merging?
- Does the backend represent “role(s)” as **one roleType per relationship record** (as in backend story) or multiple roles per relationship? (Frontend can support one-per-record per backend story; confirm to avoid mismatch.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Party: Associate Individuals to Commercial Account — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/174

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Party: Associate Individuals to Commercial Account
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/174
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Associate Individuals to Commercial Account

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to link individuals to a commercial account with relationship types/roles** so that **workorder approval and billing contacts are unambiguous**.

## Details
- Relationship includes role(s) and effective dates.
- Allow one or more primary billing contacts per account.

## Acceptance Criteria
- Can create relationship with role.
- Primary billing contact can be designated.
- Relationship can be deactivated.

## Integration Points (Workorder Execution)
- Workorder Execution retrieves approvers/billing contacts for an account.

## Data / Entities
- PartyRelationship (roles, flags, effective dates)

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