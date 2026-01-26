# [FRONTEND] [STORY] Contacts: Maintain Contact Roles and Primary Flags
## Purpose
Enable CSRs (and other authorized roles) to assign multiple role codes to an account contact and set a “primary” flag per role. Support auto-demotion so only one contact can be primary for a given role (e.g., Billing) without requiring manual unsetting. Provide validation and pre-save warnings (including billing contact requirements) while minimizing exposure of sensitive contact details.

## Components
- Account Details page section: “Contacts”
- Contacts list/table
  - Contact name
  - Roles summary (chips/badges)
  - Primary indicators per role (e.g., “Primary Billing”)
  - Action: “Edit Roles” (per contact row)
- “Edit Roles” modal
  - Role selection checkboxes: BILLING, APPROVER, DRIVER
  - For each role: “Primary” toggle (enabled only when role is checked)
  - Inline validation / warning area (pre-save)
  - Save button
  - Cancel button
  - Dirty-form confirmation dialog (on close/cancel with unsaved changes)
- Loading states (modal + contacts refresh)
- Error messaging surface (400 validation, 403 permission, 404 not found)

## Layout
- Top: Account header (account name/ID) + primary actions
- Main: “Contacts” section
  - Contacts table/list with per-row roles summary + “Edit Roles” action
- Modal overlay (center): “Edit Roles” form with role rows and primary toggles; footer actions Save/Cancel

## Interaction Flow
1. View contacts and current role assignments
   1. CSR opens Account Details and scrolls to “Contacts”.
   2. UI displays each contact with a roles summary and any primary role indicators.
2. Assign multiple roles to a contact (Scenario 1)
   1. CSR clicks “Edit Roles” on a contact with no roles.
   2. Modal opens showing role checkboxes (all unchecked) and disabled primary toggles.
   3. CSR checks APPROVER and DRIVER.
   4. Primary toggles for checked roles become enabled; CSR optionally sets one/both as primary.
   5. CSR clicks Save.
   6. UI calls the maintain-roles service with accountId/contactId and role assignments (including isPrimary flags).
   7. On success, modal closes; contacts view refreshes; roles summary shows both roles.
3. Change primary billing contact with auto-demotion (Scenario 3)
   1. Precondition: Contact A is Primary for BILLING; Contact B has BILLING (or CSR selects BILLING for B).
   2. CSR opens “Edit Roles” for Contact B and toggles Primary for BILLING on.
   3. CSR clicks Save.
   4. Backend auto-demotes Contact A for BILLING; UI does not require an intermediate step.
   5. Contacts view refreshes showing Contact B as Primary Billing and Contact A no longer primary for Billing.
4. Pre-save billing validation / warnings
   1. When modal opens (or before Save), UI loads account contacts data needed for billing validation.
   2. If billing requirements are not met (e.g., no valid billing contact email), show a warning/validation message in the modal before Save.
   3. If supported, display masked email indicators (e.g., “has primary email: yes/no”) rather than full email values.
5. Error and permission handling
   1. If Save returns 400, show field-level or modal-level validation messages and keep modal open.
   2. If Save returns 403, show “not authorized” message; disable Save and/or close modal per UX decision.
   3. If Save returns 404, show “account/contact not found” message and prompt refresh/navigation.
6. Dirty-form detection
   1. If CSR changes any checkbox/toggle and clicks Cancel or closes modal, show confirmation dialog.
   2. If confirmed, discard changes and close; otherwise remain in modal.

## Notes
- Role assignment supports multiple roles per contact; each role can independently be marked primary.
- Auto-demotion is required: setting a contact as primary for a role must unset any other primary for that same role without extra UI steps.
- Primary toggle must be disabled when its role checkbox is unchecked; unchecking a role should clear its primary state.
- Contacts section must be added/extended on the Account Details page to display role assignments and primary indicators.
- Implement Save/Cancel with clear loading states and a post-save refresh of the contacts list.
- Handle service errors explicitly: 400 (validation), 403 (permission), 404 (not found).
- Security/authorization: only users with permission to view role assignments can see role details; only users with assign/update permission can edit/save.
- Testing requirements: include coverage for auto-demotion behavior and billing requirement validation/warnings.
- Requirements are incomplete/TBD in places (service name, exact DTO fields); keep UI adaptable to final API contract (roleCode/roleLabel/isPrimary, and “hasPrimaryEmail” style indicators).
