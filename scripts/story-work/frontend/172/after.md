STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:domain-conflict
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** integration-conservative

## 1. Story Header

### Title
Contacts: Maintain Contact Roles and Primary Flags (Billing / Approver / Driver)

### Primary Persona
CSR (Customer Service Representative)

### Business Value
Ensures downstream workflows (estimate approval and invoice delivery) consistently target the correct contact(s) by role, with an optional enforcement rule for billing contacts when invoices are delivered by email.

---

## 2. Story Intent

### As a / I want / So that
- **As a** CSR  
- **I want** to assign one or more roles (Billing, Approver, Driver) to contacts on a customer account and optionally mark one contact as “primary” per role  
- **So that** estimate approvals and invoicing use the correct person automatically and consistently.

### In Scope
- View existing contacts for a customer account and their role assignments.
- Assign/unassign one or more roles to a contact for that account.
- Mark/unmark a contact as **Primary** for a given role (enforce single-primary-per-role per account).
- Enforce validation when configured: **at least one Billing contact required** when invoice delivery method is **EMAIL** (scope/config TBD).
- UI error handling and messaging for validation and authorization failures.
- Moqui screen/form/service wiring for the above.

### Out of Scope
- Creating/editing the underlying Contact / Party record details (name, phone, email) beyond displaying identifiers needed for selection.
- Managing the master list of roles (i.e., CRUD of role definitions) unless already provided elsewhere.
- Implementing downstream consumers (work order execution approvals, invoice sending); this story only maintains role data used by them.

---

## 3. Actors & Stakeholders
- **CSR**: assigns roles and primary flags.
- **Billing/Invoicing process** (consumer): uses Billing role + primary to determine invoice recipient.
- **Work Order / Estimate approval process** (consumer): uses Approver role to determine approver(s).
- **Dispatch/Operations** (consumer): may use Driver role to identify driver contact(s).
- **System admin/config owner**: sets/controls whether “billing contact required for EMAIL invoices” is enforced (configuration scope TBD).

---

## 4. Preconditions & Dependencies
- A **Customer Account** exists and is accessible to the CSR.
- The account has **one or more Contacts** associated (via PartyRelationship or equivalent).
- Backend provides (or will provide) endpoints/services to:
  - Load account contacts and current role assignments.
  - Update role assignments and primary flags with proper validation.
- Backend provides the **invoice delivery method** for the customer account (at least “EMAIL” vs other).
- Permissions exist to control CSR access to modify contacts/roles.

**Dependency note (blocking):** Backend reference story indicates domain conflict (CRM vs Payment). Frontend needs a stable SoR/service boundary and endpoints. Until clarified, this story remains blocked.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Customer Account** context (e.g., account detail screen): “Contacts” tab/section → “Roles” management.
- From **Contact detail** within an account: “Roles” panel.

### Screens to create/modify
1. **Screen: Account Contacts List (existing or new)**
   - Add a column/summary showing assigned roles and primary markers (e.g., “Billing (Primary), Approver”).
   - Add action: “Edit Roles” per contact.
2. **Screen/Dialog: Edit Contact Roles (new)**
   - For selected account + contact: show role checkboxes and “Primary” toggle per role where applicable.
   - Save/Cancel actions.
3. **Optional Screen: Account Role Overview (new if needed)**
   - Shows, per role, which contact is primary and all contacts assigned.

### Navigation context
- All role operations are **scoped to a customer account** (accountId) and a contact (partyId/contactId).
- Breadcrumb: Account → Contacts → (Contact) → Roles (modal/panel).

### User workflows
**Happy path (assign roles):**
1. CSR opens account contacts list.
2. CSR selects a contact → “Edit Roles”.
3. CSR checks roles (Billing/Approver/Driver).
4. CSR optionally sets Primary for Billing (and/or other roles if supported).
5. CSR clicks Save → UI persists and refreshes list.

**Alternate path (change primary):**
1. CSR sets another contact as Primary Billing.
2. System demotes previous primary automatically.
3. UI reflects new primary on refresh.

**Alternate path (configured validation):**
1. Account invoice delivery method is EMAIL.
2. CSR attempts to remove Billing role from the last billing contact.
3. Save fails with a clear validation error; UI remains in edit mode.

---

## 6. Functional Behavior

### Triggers
- Enter account contacts screen.
- Click “Edit Roles” for a contact.
- Toggle a role checkbox.
- Toggle “Primary” for a role.
- Click Save.

### UI actions
- Load contacts + existing role assignments.
- Allow selecting multiple roles for a contact.
- For each role assigned, allow setting Primary (if role supports primary; see Open Questions).
- On Save:
  - Submit role assignment changes.
  - Display success toast/message.
  - Refresh displayed role summaries.

### State changes (UI-level)
- Edit form has dirty tracking; Save disabled until changes exist.
- While saving: show spinner/disable inputs to prevent double-submit.
- After successful save: close modal/panel and refresh list.

### Service interactions
- Load: fetch account contacts, role assignments, invoice delivery method (if needed for validation display).
- Save: call update service; handle validation errors and authorization errors explicitly (see Error Flows).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Role validity:** UI must only allow selecting from the backend-provided role list OR the known enumerated set (BILLING/APPROVER/DRIVER) *if and only if backend confirms it*. If backend may change list, UI must load roles from backend.
- **Single primary per role per account:** When user marks a contact as primary for a role:
  - UI may allow it and rely on backend to demote prior primary automatically.
  - UI must refresh after save to reflect the resulting primary.
- **Primary requires role:** UI must not allow setting Primary for a role unless that role is selected for the contact.
- **Billing contact required when invoice delivery is EMAIL (configurable):**
  - UI should display a warning/inline requirement when invoice delivery is EMAIL and enforcement is enabled.
  - UI must surface backend validation error if user attempts to violate rule.

### Enable/disable rules
- Primary toggle disabled unless role checkbox is selected.
- Save disabled if:
  - No changes, or
  - Form has invalid state (e.g., Primary toggled while role unchecked; should be impossible via UI).

### Visibility rules
- Show “Primary” controls only for roles that support a primary designation (assumed all roles unless clarified; see Open Questions).
- If CSR lacks permission: screen may be view-only (no Edit Roles action) or Save fails with 403; must handle both.

### Error messaging expectations
- Validation errors shown inline at top of dialog and/or on the relevant control:
  - Example: “At least one Billing contact is required when invoice delivery method is Email.”
- Authorization: “You do not have permission to update contact roles for this account.”
- Not found: “Account or contact not found. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (conceptual; frontend consumption)
- **CustomerAccount** (or Party representing account)
  - invoiceDeliveryMethod (needs value “EMAIL” at minimum)
- **Contact** (Party)
  - contactId / partyId
  - displayName
  - email/phone (optional display)
- **ContactRoleAssignment** (name TBD; backend references “ContactRole” and “PartyRelationship”)
  - accountId
  - contactId
  - roleName (enum/string: BILLING, APPROVER, DRIVER)
  - isPrimary (boolean)

### Fields (type, required, defaults)
- `roleName`: string/enum, required for each assignment row.
- `isPrimary`: boolean, default false.
- `contactId`, `accountId`: required identifiers.

### Read-only vs editable by state/role
- Editable only for users with appropriate permission (permission name TBD).
- If no edit permission: role list is read-only; no Save.

### Derived/calculated fields (UI)
- `rolesSummary` per contact (derived for display): join of roles + “(Primary)” where applicable.
- `isLastBillingContact` (derived at runtime): computed from loaded assignments for the account, used only to display caution; backend is authoritative for enforcement.

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking note:** Concrete service names/endpoints are not provided in inputs. Below are required capabilities and suggested Moqui service patterns; exact names must be confirmed.

### Load/view calls
- **GetAccountContactsAndRoles**
  - Inputs: `accountId`
  - Returns:
    - account: `invoiceDeliveryMethod` (and whether enforcement is enabled, if exposed)
    - contacts: list with ids + displayName (+ email)
    - roleAssignments: list of `{contactId, roleName, isPrimary}` or embedded per contact
    - validRoles: list of role codes/labels (optional but preferred)

### Create/update calls
- **UpdateContactRolesForAccount**
  - Inputs:
    - `accountId`
    - `contactId`
    - `roles`: array of `{roleName, isPrimary}` (or separate arrays)
  - Backend behavior required:
    - Validate roleName is valid.
    - Enforce “primary requires role”.
    - Enforce single primary per role per account (auto-demote prior primary OR reject; must be clarified).
    - Enforce billing-contact-required-if-email when configured.

### Submit/transition calls
- None beyond update; no workflow state machine implied for this UI.

### Error handling expectations
- 400 validation error: return structured message(s) that UI can display (field-level preferred, else general).
- 403: show permission error; keep UI in view-only or block save.
- 404: show not-found; recommend refresh/navigation back.
- Concurrency: if backend uses optimistic locking/versioning, return conflict code; UI should reload and prompt user (see Error Flows).

---

## 10. State Model & Transitions

### Allowed states
No explicit entity lifecycle states provided for role assignments. Treat assignments as simple active records.

### Role-based transitions
- CSR with edit permission:
  - Can add/remove roles and set primary.
- CSR without edit permission:
  - View only.

### UI behavior per state
- Loading: skeleton/spinner.
- Loaded + editable: Edit Roles enabled.
- Loaded + read-only: Edit Roles hidden/disabled, role info visible.
- Saving: disable inputs, show progress.
- Save failed: remain in dialog with errors displayed.

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid role (backend rejects):
  - UI shows: “Selected role is not valid.” and refreshes valid roles list (if endpoint supports).
- Primary flag without role:
  - UI prevents this by disabling primary toggle; if backend still returns error, show it.
- Removing last Billing contact when EMAIL invoicing and enforcement enabled:
  - UI shows the backend error; does not close dialog.

### Concurrency conflicts
- If backend indicates role assignments changed since load:
  - UI shows: “Roles were updated by another user. Reloading latest values.”
  - UI reloads and discards local changes (unless versioned merge is supported; not assumed).

### Unauthorized access
- If user opens edit but save returns 403:
  - UI shows permission error, disables Save, and offers Close.

### Empty states
- Account has no contacts:
  - Show empty state: “No contacts found for this account.” Provide navigation to contact creation (if exists) or informational text (no creation in scope).

---

## 12. Acceptance Criteria

### Scenario 1: Assign multiple roles to a contact
**Given** a customer account with a contact “Jane Doe” with no assigned roles  
**When** the CSR opens “Edit Roles” for Jane Doe and selects roles “BILLING” and “APPROVER” and clicks Save  
**Then** the system saves the role assignments successfully  
**And** the contacts list displays Jane Doe with both roles shown in the roles summary.

### Scenario 2: Designate a primary billing contact
**Given** a customer account with contacts “Jane Doe” and “John Smith” both assigned the “BILLING” role and neither is primary  
**When** the CSR sets “Jane Doe” as Primary for “BILLING” and saves  
**Then** Jane Doe is shown as “BILLING (Primary)” after refresh  
**And** John Smith is shown as “BILLING” (not primary).

### Scenario 3: Change the primary billing contact (auto-demote behavior)
**Given** a customer account where “Jane Doe” is “BILLING (Primary)” and “John Smith” has “BILLING”  
**When** the CSR sets “John Smith” as Primary for “BILLING” and saves  
**Then** John Smith becomes “BILLING (Primary)”  
**And** Jane Doe is no longer primary for “BILLING”.

### Scenario 4: Enforce required billing contact rule when invoice delivery is EMAIL (when enabled)
**Given** a customer account configured with invoice delivery method “EMAIL”  
**And** “Jane Doe” is the only contact assigned the “BILLING” role  
**When** the CSR removes the “BILLING” role from Jane Doe and clicks Save  
**Then** the system rejects the save with a validation error indicating a billing contact is required  
**And** the UI keeps the CSR in the edit dialog with the error message visible.

### Scenario 5: Prevent setting Primary without role
**Given** the CSR is editing roles for a contact  
**When** the CSR has not selected the “BILLING” role  
**Then** the UI does not allow toggling “Primary” for “BILLING”  
**And** no request is sent that sets `isPrimary=true` for an unassigned role.

### Scenario 6: Unauthorized user cannot modify roles
**Given** a user without permission to edit contact roles opens the account contacts page  
**When** they attempt to access role editing (via direct URL or UI action)  
**Then** the UI prevents editing (Edit action hidden/disabled) or the save fails with 403  
**And** the UI displays an authorization error message.

---

## 13. Audit & Observability

### User-visible audit data
- After save, show success message including contact name.
- If available from backend, show “Last updated by / at” for role assignments (optional; do not assume exists).

### Status history
- Not required unless backend exposes role-assignment change history.

### Traceability expectations
- Frontend should include `accountId`, `contactId` in logs for role update attempts (without exposing PII beyond displayName in UI).

---

## 14. Non-Functional UI Requirements
- **Performance:** Contacts + roles load within 2s for typical accounts; handle pagination if large contact lists (only if existing screen supports it).
- **Accessibility:** All controls keyboard-accessible; primary toggles and role checkboxes have accessible labels; validation errors announced.
- **Responsiveness:** Dialog/panel usable on tablet widths; avoid wide tables without wrapping.
- **i18n/timezone/currency:** Not applicable (no currency). Role labels should be displayable via i18n keys if the frontend supports it; do not hardcode user-facing strings without i18n mechanism if the project requires it (unknown → see Open Questions).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a non-destructive empty state when an account has no contacts; qualifies as safe because it does not affect domain rules and only improves usability. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-DOUBLE-SUBMIT: Disable Save while request in-flight to prevent duplicate submissions; qualifies as safe because it is UI ergonomics with no policy impact. (Impacted: Functional Behavior, Error Flows)
- SD-ERR-STANDARD-MAP: Map common HTTP errors (400/403/404/409/5xx) to user-friendly messages while preserving backend message text when provided; qualifies as safe because it’s standard error presentation without changing business logic. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions
1. **[BLOCKER] Domain ownership / SoR:** Is the authoritative domain for managing contact roles `CRM` (recommended) or `Payment/Billing` (as current labels suggest)? Frontend needs to know which Moqui component/screen tree and services own these updates.
2. **[BLOCKER] Backend contract:** What are the exact Moqui service names (or REST endpoints) and payload shapes for:
   - Loading account contacts + role assignments + invoice delivery method
   - Updating a contact’s roles and primary flags
3. **[BLOCKER] Primary behavior:** When setting a new primary for a role, should the system **auto-demote** the prior primary (as assumed in backend reference), or **reject** until the existing primary is cleared?
4. **[BLOCKER] Config scope for “billing contact required for EMAIL”:** Is this enforcement:
   - Global system configuration,
   - Per customer account configuration,
   - Or derived strictly from invoice delivery method with no separate toggle?
5. **Role list source of truth:** Is the role list fixed to {BILLING, APPROVER, DRIVER} for MVP, or must UI load from backend to support future extensibility?
6. **Which roles support “Primary”:** Only Billing, or any role can have a single primary per account?
7. **Permissions:** What permission(s)/roles gate editing contact roles in the frontend (e.g., `CSR_CONTACT_EDIT`, `ACCOUNT_MAINTAIN`)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Contacts: Maintain Contact Roles and Primary Flags  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/172  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Contacts: Maintain Contact Roles and Primary Flags

**Domain**: payment

### Story Description

/kiro  
# User Story

## Narrative
As a **CSR**, I want **to set contact roles (billing, approver, driver) and primary flags** so that **the correct person is used in approvals and invoicing**.

## Details
- Allow multiple roles per person/account.
- Optionally enforce at least one billing contact when invoice delivery is email.

## Acceptance Criteria
- Can assign roles.
- Primary billing contact can be designated.
- Validation enforced when configured.

## Integration Points (Workorder Execution)
- Estimate approval uses approver role.
- Invoice delivery uses billing contact.

## Data / Entities
- ContactRole
- PartyRelationship

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