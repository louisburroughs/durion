STOP: Clarification required before finalization

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

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] CRM Contacts: Manage Multiple Contact Points (Email/Phone) with Primary per Kind

**Primary Persona:** CSR (Customer Service Representative)

**Business Value:** Enables reliable customer communication by storing multiple labeled emails/phone numbers and selecting a primary per kind for downstream usage (e.g., approvals and invoice delivery in Workorder Execution).

---

## 2. Story Intent

**As a** CSR,  
**I want** to add, edit, remove, and mark primary phone numbers and email addresses for a customer (person or account),  
**So that** I can contact them using the correct method and downstream systems can display the right contact details.

### In-scope
- View a customer’s existing contact points (EMAIL/PHONE) with label tags (WORK/HOME/MOBILE/OTHER) and primary indicator per kind.
- Add a new contact point (kind + value + optional label + primary flag).
- Edit an existing contact point (value, label, primary flag).
- Remove an existing contact point.
- Enforce “single primary per kind” behavior in UI (and reflect backend enforcement).
- Basic formatting validation for email and phone **on the client** (non-authoritative; backend remains source of truth).

### Out-of-scope
- Customer deduplication/merge, golden record rules, uniqueness policy beyond what backend enforces.
- Customer hierarchy/relationships management.
- Communication consent/preferences (SMS/email marketing consent).
- Workorder Execution UI changes (consumer behavior only referenced).
- Bulk import/export of contact points.

---

## 3. Actors & Stakeholders

- **CSR (Primary Actor):** Manages contact points on a customer record.
- **Customer (Subject):** Person or account whose contact points are stored.
- **Workorder Execution (Downstream Consumer):** Reads contact points for approvals/invoice delivery display.
- **CRM Domain Services (System):** Provides authoritative validation, persistence, and “single primary per kind” enforcement.

---

## 4. Preconditions & Dependencies

### Preconditions
- CSR is authenticated in the frontend session.
- CSR is authorized to view and manage contact points for the selected customer.

### Dependencies
- A customer context exists (at minimum a `partyId`/`customerId` identifier) to load/save contact points.
- Backend endpoints or Moqui services exist (or will be delivered) to:
  - List contact points for a customer
  - Create contact point
  - Update contact point
  - Delete contact point
  - Enforce single-primary-per-kind server-side

### Blocking dependency (needs clarification)
- Confirm whether contact points attach to **Person only**, **Account/Organization only**, or **generic Party (Person or Organization)** and what identifier the frontend should use (`partyId` vs `personId` vs `accountId`).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a **Customer Detail** screen (person/account), via a navigation item/tab: **“Contacts”** or **“Contact Points”**.

### Screens to create/modify
1. **Modify existing customer detail screen** to include a “Contact Points” section OR create a child screen:
   - Suggested path (placeholder): `apps/pos/screen/crm/customer/CustomerDetail.xml` with a subscreen `ContactPoints.xml`
2. **ContactPoints screen/component**:
   - Displays list grouped by `kind` (EMAIL, PHONE) with primary marked.
   - Actions: Add, Edit, Remove; Set Primary (either via edit or quick action).

### Navigation context
- Breadcrumb: Customer Search → Customer Detail → Contact Points.
- Customer identity header should show minimal context (e.g., customer display name and ID) without exposing sensitive data beyond what’s already allowed.

### User workflows
**Happy path**
1. CSR opens customer → Contact Points.
2. CSR clicks “Add Email” or “Add Phone”.
3. CSR enters value, label, optionally sets as primary → Save.
4. List refreshes, primary indicators updated.

**Alternate paths**
- Edit existing contact point: change label/value, optionally toggle primary.
- Remove contact point: confirm deletion; list refreshes.
- Set primary when another primary exists: UI warns/indicates that previous primary will be demoted; after save, list shows only one primary for that kind.

---

## 6. Functional Behavior

### Triggers
- Screen load with `customerId/partyId` parameter.
- User actions: add/edit/delete/set primary.

### UI actions
- **Load list:** On screen render, call load service to fetch contact points for customer.
- **Add:** Open modal/dialog or inline form for new contact point.
- **Edit:** Open modal/dialog prefilled with selected contact point fields.
- **Delete:** Show confirmation dialog; on confirm, call delete; refresh list.
- **Set primary:** Either:
  - via edit form checkbox `isPrimary`, or
  - via list action “Make Primary” that triggers update with `isPrimary=true`.

### State changes (client-side)
- Local form state transitions: `idle → editing → saving → success|error`.
- After any successful create/update/delete, re-fetch contact points to reflect server-side demotion logic.

### Service interactions (Moqui)
- Use Moqui screen transitions calling services (or REST calls if frontend uses API gateway—must be clarified by repo conventions).
- Service errors map to field-level or form-level messages (see §9).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, non-authoritative)
- `kind` required; must be `EMAIL` or `PHONE`.
- `value` required.
- If `kind=EMAIL`: must match basic email pattern (e.g., contains `@` and domain). On failure: block save and show “Invalid email format”.
- If `kind=PHONE`: basic phone validation (digits + allowed separators). On failure: block save and show “Invalid phone number format”.
- `label` optional; if provided must be one of `WORK`, `HOME`, `MOBILE`, `OTHER`.
- `isPrimary` defaults false.

### Enable/disable rules
- Save disabled until required fields valid.
- While saving, disable inputs and prevent duplicate submits.

### Visibility rules
- Show primary indicator per contact point (e.g., “Primary” badge).
- Group or filter by kind (EMAIL/PHONE) for clarity.

### Error messaging expectations
- Backend validation errors should be shown near the relevant field when possible; otherwise as a form banner.
- Duplicate contact point attempt should show a specific message if backend returns a recognizable error (e.g., “That email already exists for this customer.”).

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `ContactPoint` (CRM-owned)
- Customer identifier entity/context: `Party` / `Person` / `Organization` (needs clarification which identifier is used by frontend).

### Fields
**ContactPoint**
- `id` (UUID, read-only, required for edit/delete)
- `customerId` or `partyId` (UUID, read-only in UI; supplied from page context)
- `kind` (enum: `PHONE`, `EMAIL`; required; editable on create; **clarify** if editable on update)
- `label` (enum: `WORK`, `HOME`, `MOBILE`, `OTHER`; optional)
- `value` (string; required)
- `isPrimary` (boolean; required; default false)

### Read-only vs editable by state/role
- CSR with proper permission: may create/update/delete.
- If CSR lacks permission: screen shows read-only list and hides add/edit/delete actions; unauthorized service calls must show “Not authorized”.

### Derived/calculated fields
- `displayValue` (derived formatting for phone/email for display; non-persistent).
- `kindGroupTitle` (EMAIL/PHONE headings; non-persistent).

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact service names/REST routes must follow `durion-moqui-frontend` conventions (needs repo confirmation). Below defines required capabilities and expected payloads.

### Load/view calls
- **LoadContactPoints**
  - Input: `{ customerId|partyId: UUID }`
  - Output: `{ contactPoints: ContactPoint[] }` sorted with primary first within each kind (preferred but not required; UI may sort).

### Create/update calls
- **CreateContactPoint**
  - Input: `{ customerId|partyId, kind, value, label?, isPrimary }`
  - Success: returns created `ContactPoint` (including `id`) OR 201 + id.
  - Errors:
    - 400 validation (invalid format, invalid enums)
    - 409 conflict (duplicate contact point) — if backend uses it
    - 403 unauthorized
- **UpdateContactPoint**
  - Input: `{ id, value, label?, isPrimary }` (+ `kind` only if backend allows changing kind; must be clarified)
  - Success: returns updated ContactPoint.
  - Side-effect: if `isPrimary=true`, backend demotes prior primary of same kind.

### Delete calls
- **DeleteContactPoint**
  - Input: `{ id }`
  - Success: 204 or success response.
  - If deleting primary: allowed; no auto-promotion (UI must reflect that after refresh).

### Error handling expectations
- Map structured backend errors to:
  - field errors: attach to `value`, `label`, `kind`
  - general errors: show banner
- Always re-enable UI after error; do not lose user input on validation failures.

---

## 10. State Model & Transitions

### Allowed states (UI-level)
- `Viewing` (list)
- `Adding` (create form active)
- `Editing` (edit form active)
- `Saving` (pending service response)
- `Error` (recoverable; user can retry/correct)

### Role-based transitions
- Authorized CSR:
  - Viewing → Adding/Editing → Saving → Viewing
  - Viewing → Deleting(confirm) → Saving → Viewing
- Unauthorized user:
  - Viewing only; any attempted transition to Saving should be blocked client-side if permissions known; otherwise handle 403.

### UI behavior per state
- Saving: disable form, show spinner/progress, prevent navigation if it would lose unsaved changes (confirm dialog).

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid email/phone format: block save; show inline error on `value`.
- Missing required: show required indicators; save disabled.

### Concurrency conflicts
- If backend returns 409/412 due to stale update (if supported): show “This contact was updated by someone else. Refresh and try again.” and refresh list.

### Unauthorized access
- On 403 during load: show “You do not have access to this customer’s contact details.”
- On 403 during mutation: show “Not authorized to modify contact points.”

### Empty states
- No contact points: show empty state text:
  - “No email addresses on file.” and “No phone numbers on file.”
  - Provide Add action if authorized.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View contact points
**Given** I am an authorized CSR  
**And** a customer exists with 1 email and 1 phone contact point  
**When** I open the customer’s Contact Points screen  
**Then** I see the email and phone displayed with their labels and primary indicators  
**And** the primary contact point for each kind is clearly indicated

### Scenario 2: Add a non-primary phone contact point
**Given** I am an authorized CSR  
**And** I am viewing a customer’s Contact Points  
**When** I add a new PHONE contact point with label MOBILE, value "555-123-4567", and isPrimary=false  
**Then** the new contact point appears in the PHONE list after save  
**And** no existing primary PHONE contact point is changed

### Scenario 3: Add an email as primary when none exists
**Given** I am an authorized CSR  
**And** the customer has no primary EMAIL contact point  
**When** I add a new EMAIL contact point with isPrimary=true  
**Then** it is displayed as the primary EMAIL contact point after save

### Scenario 4: Change primary within a kind (demotion)
**Given** I am an authorized CSR  
**And** the customer has two PHONE contact points where Phone A is primary and Phone B is not  
**When** I update Phone B to set isPrimary=true  
**Then** Phone B is shown as primary after save  
**And** Phone A is shown as not primary after save  
**And** there is only one primary PHONE contact point displayed

### Scenario 5: Delete a contact point
**Given** I am an authorized CSR  
**And** the customer has at least one EMAIL contact point  
**When** I delete one EMAIL contact point and confirm deletion  
**Then** it no longer appears in the EMAIL list after save

### Scenario 6: Client-side invalid email format is blocked
**Given** I am an authorized CSR  
**When** I attempt to add an EMAIL contact point with value "invalid-email-address"  
**Then** the Save action is disabled or blocked  
**And** I see an error message “Invalid email format” associated with the value field

### Scenario 7: Backend rejects duplicate contact point
**Given** I am an authorized CSR  
**And** the customer already has an EMAIL contact point with value "a@b.com"  
**When** I attempt to add another EMAIL contact point with value "a@b.com"  
**Then** the save fails  
**And** I see an error message indicating the contact point already exists  
**And** no duplicate is added to the list

### Scenario 8: Unauthorized user cannot edit
**Given** I am authenticated but not authorized to modify customer contact points  
**When** I open the Contact Points screen  
**Then** I can view contact points if permitted  
**And** I do not see Add/Edit/Delete actions  
**When** I attempt to call a mutation action (e.g., via direct URL or intercepted request)  
**Then** the UI shows a “Not authorized” error and no changes occur

---

## 13. Audit & Observability

### User-visible audit data
- Not required to display audit history in this story.

### Status history
- Not applicable (no lifecycle states beyond primary flags).

### Traceability expectations
- Frontend should include correlation/request ID headers if that is a project convention (needs repo confirmation).
- Log (frontend console/dev logging per conventions) minimal contextual info on failures: customerId, contactPointId (if applicable), operation type—avoid logging contact `value` to reduce PII leakage.

---

## 14. Non-Functional UI Requirements

- **Performance:** Initial load should render list within 1s under normal conditions once data is returned; avoid unnecessary reloads (but refresh after mutations is required).
- **Accessibility:** All actions keyboard-navigable; dialogs have focus trap; form fields have labels and error text announced (Quasar best practices).
- **Responsiveness:** Works on tablet resolutions commonly used at POS; list layout adapts without horizontal scrolling where possible.
- **i18n/timezone/currency:** Not applicable (no currency/time). Text should be written in a way compatible with future i18n (no concatenated hard-coded fragments in logic).

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE**
  - **Assumed:** Provide explicit empty-state messaging per kind and show Add action if authorized.
  - **Why safe:** Pure UX guidance; does not change domain rules or persistence.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria.
- **SD-UX-POST-MUTATION-REFRESH**
  - **Assumed:** Re-fetch contact points after create/update/delete to reflect server-side primary demotion and canonical ordering.
  - **Why safe:** Ensures UI matches source-of-truth backend outcomes; no policy invented.
  - **Impacted sections:** Functional Behavior, Service Contracts, Acceptance Criteria.
- **SD-ERR-MAP-GENERIC**
  - **Assumed:** Map unknown server errors to a generic banner message and preserve user input.
  - **Why safe:** Standard error handling; does not alter business logic.
  - **Impacted sections:** Business Rules, Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions

1. **Customer Identifier / Attachment:** Are contact points attached to a generic **Party** (person or organization) or specifically to **Person** and/or **Account**? What is the canonical identifier passed in routes and service calls (`partyId`, `personId`, `customerId`)?
2. **Kind Mutability:** Is `kind` editable on an existing contact point (e.g., change EMAIL → PHONE), or immutable after creation?
3. **UI Location/Route Convention:** What is the correct Moqui screen path and navigation entry for customer contact points in this repo (which customer detail screen is authoritative)?
4. **Backend Contract Shape:** Are we calling Moqui services directly (screen transitions) or a REST API layer? If REST, what are the exact endpoints and error payload schema for validation/duplicate conflicts?
5. **Phone Validation Policy:** What constitutes “basic formatting validation” for phones in this product (E.164, digits-only, allow extensions)? Client-side validation needs an explicit rule to avoid blocking valid numbers.
6. **Permissions Model:** What role/scope controls “manage contact points” vs “view contact points” for CSRs in the frontend?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Contacts: Capture Multiple Contact Points — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/170


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Contacts: Capture Multiple Contact Points
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/170
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Contacts: Capture Multiple Contact Points

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to store multiple phone numbers and emails per person/account** so that **I can reach them via the right contact point**.

## Details
- Support type tags: work, mobile, home.
- Basic formatting validation.

## Acceptance Criteria
- Add/update/remove contact points.
- Identify primary contact point per type.

## Integration Points (Workorder Execution)
- Workorder Execution displays contact points during approval/invoice delivery.

## Data / Entities
- ContactPoint (type, value, primaryFlag)

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