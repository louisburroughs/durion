STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting: Reverse Completion on Workorder Reopen (Visibility + Reversal Controls)

### Primary Persona
Accountant / Accounting Operations User (with accounting reversal permissions)

### Business Value
Ensure accounting state accurately reflects reopened workorders, prevent premature invoicing, and provide a complete, user-visible audit trail for completion reversals.

---

## 2. Story Intent

### As a / I want / So that
**As an** Accounting Operations user,  
**I want** to view a workorder’s completion-related accounting postings and (when permitted) initiate or verify a reversal when a workorder is reopened,  
**so that** the ledger-facing state is consistent, the workorder is not invoice-ready, and all reversal actions are auditable.

### In-scope
- Frontend screens to:
  - View workorder accounting state, related journal entries, and reversal audit trail.
  - Trigger a “Reverse completion postings” action (calls Moqui service) when eligible.
  - Reflect event-driven reversals (i.e., reversal created by backend upon `WorkorderReopened` event) in UI.
- UI enforcement of eligibility rules (disable action when not allowed; show reasons).
- Display errors for blocked reversal scenarios (e.g., already invoiced).

### Out-of-scope
- Defining GL debit/credit mappings, GL accounts, or posting rule content.
- Implementing the event consumer (`WorkorderReopened`) itself (backend responsibility).
- Creating or issuing invoices.
- Editing posted journal entries (must remain immutable).

---

## 3. Actors & Stakeholders
- **Primary user:** Accounting Operations / Accountant
- **Secondary stakeholders:** Financial Controller (auditability), Billing user (depends on invoice-ready flag correctness)
- **System integrations (read-only from UI):**
  - Accounting service/state for workorders
  - Journal entry and audit log storage

---

## 4. Preconditions & Dependencies
- A workorder exists in the system with an accounting record.
- Backend provides Moqui-accessible services/entities to:
  - Load workorder accounting status and invoice-ready flag
  - List completion journal entry and reversal journal entry (if any)
  - List audit events for reversal
  - Initiate reversal (command) OR at minimum expose reversal results after event processing
- Permissions exist and are enforceable by Moqui (e.g., `invoice.adjust`-like or accounting-specific permission for reversal).

**Dependency note:** The referenced backend story for reversal contains open questions about GL accounts and authorization; this frontend story is blocked until a concrete backend contract (services + permissions + entities/fields) is confirmed.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an Accounting module navigation item: **Accounting → Workorders → Workorder Accounting Detail**
- From a Workorder detail screen (if exists): a link/tab “Accounting”

### Screens to create/modify
1. **New/Updated Screen:** `apps/accounting/screen/WorkorderAccountingDetail.xml` (name illustrative)
   - Shows accounting status, invoice-ready, related journal entries, and audit trail.
   - Provides an action button: “Reverse completion postings” (when eligible).
2. **Optional supporting dialog screen:** `apps/accounting/screen/WorkorderReverseCompletionDialog.xml`
   - Captures/echoes reversal reason (reasonCode + optional reasonText) if required by backend.

### Navigation context
- Route pattern (proposed): `/accounting/workorders/{workorderId}`
- Breadcrumb: Accounting > Workorders > {workorderId}

### User workflows
**Happy path (manual reversal command)**
1. User opens Workorder Accounting Detail.
2. UI displays completion posting exists and status is Completed; invoice-ready is true.
3. User clicks “Reverse completion postings”.
4. User confirms and provides reversal reason (if required).
5. UI calls reversal service; on success shows reversal journal entry reference, updates status to Reopened/InProgress and invoice-ready false, and appends audit event.

**Alternate path (event-driven reversal already processed)**
1. User opens screen after `WorkorderReopened` event processed.
2. UI shows status is Reopened and reversal journal entry exists; action button disabled with message “Already reversed”.

**Blocked path (already invoiced)**
1. UI shows status Invoiced (or invoice exists).
2. Action button disabled; screen shows warning and explains reversal is not allowed.

---

## 6. Functional Behavior

### Triggers
- Screen load for a specific `workorderId`
- User action: click “Reverse completion postings”
- Manual refresh action (or polling) to observe event-driven changes

### UI actions
- Load summary and detail sections:
  - Workorder accounting state
  - Completion journal entry reference (if any)
  - Reversal journal entry reference (if any)
  - Audit events list
- Action button states:
  - Enabled only when reversal is allowed
  - Disabled with explicit reason when not allowed
- Confirmation step before calling reversal command

### State changes (UI-observed)
- `isInvoiceReady`: `true` → `false` after reversal
- `accountingStatus`: `Completed` → `Reopened`/`InProgress` (exact state names must match backend)
- Append audit event row on success

### Service interactions
- Read: load workorder accounting and related records
- Command: request reversal (if frontend is responsible for initiating)
- Error mapping for conflicts/validation failures

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, non-authoritative)
- `workorderId` required in route; if missing/invalid: show not-found view.
- If reversal dialog requires reason:
  - `reasonCode` required (dropdown if backend provides list)
  - `reasonText` optional unless backend enforces otherwise

### Enable/disable rules
“Reverse completion postings” button:
- **Enabled when all are true:**
  - Workorder accounting status is `Completed` (or backend-designated reversible status)
  - Completion journal entry exists (or backend indicates “completion postings present”)
  - No invoice exists / not invoiced (hard stop)
  - User has reversal permission
  - No prior reversal exists (or backend indicates not yet reversed)
- **Disabled** otherwise, with inline explanation:
  - “Already invoiced—reversal not permitted”
  - “No completion posting found”
  - “Already reversed”
  - “Insufficient permission”

### Visibility rules
- Journal entries section visible if any journal entry references exist; otherwise show empty state “No accounting postings recorded for this workorder yet.”
- Audit section always visible; empty state if none.

### Error messaging expectations
- Conflict/blocked conditions must be shown as actionable messages:
  - `WORKORDER_ALREADY_INVOICED_REVERSAL_BLOCKED` (example code) → “Workorder already invoiced; completion reversal is not allowed.”
  - `ORIGINAL_COMPLETION_ENTRY_NOT_FOUND` → “Cannot reverse because original completion entry was not found. Contact support.”
  - `DUPLICATE_REQUEST` → “This reversal request was already processed.”

(Exact error codes must be confirmed by backend; see Open Questions.)

---

## 8. Data Requirements

### Entities involved (frontend-read)
> Names are placeholders until confirmed in Moqui entity definitions.

- `WorkorderAccounting` (or equivalent)
- `JournalEntry` (completion + reversal)
- `AccountingAuditEvent` (or `InvoiceAuditEvent`-like audit entity but for workorders)
- Optional: `Invoice` reference / link table to determine invoiced status

### Fields (type, required, defaults)
**WorkorderAccounting**
- `workorderId` (string/uuid, required)
- `accountingStatus` (string/enum, required)
- `isInvoiceReady` (boolean, required, default false)
- `completionJournalEntryId` (string, nullable)
- `reversalJournalEntryId` (string, nullable)
- `invoicedInvoiceId` (string, nullable) OR `isInvoiced` (boolean) (blocking—must be defined)
- `lastUpdatedTxStamp` (timestamp, for optimistic concurrency display)

**JournalEntry (read-only)**
- `journalEntryId` (string, required)
- `transactionDate` (date, required)
- `description` (string, required)
- `originalJournalEntryId` (string, nullable)
- `status` (enum: Draft/Posted/etc., required)
- `currencyUomId` (string, required)

**AccountingAuditEvent (read-only)**
- `auditId` (string, required)
- `eventType` (string, required; e.g., `WORKORDER_COMPLETION_REVERSED`)
- `entityId` (string, required = workorderId)
- `userId` (string, required)
- `timestamp` (datetime, required)
- `detailsJson` (text/json, required)

**Reversal request (write)**
- `workorderId` (required)
- `reasonCode` (required if backend demands; unknown)
- `reasonText` (optional)

### Read-only vs editable by state/role
- All accounting state and journal/audit data is **read-only** in UI.
- Only action is initiating reversal (command), gated by permission.

### Derived/calculated fields (display-only)
- `eligibilityStatus`: derived on frontend from loaded data to drive button enablement and messaging.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui service names/paths must be confirmed; below are **required contract shapes**.

### Load/view calls
1. **Load Workorder Accounting Detail**
   - Service: `accounting.WorkorderAccountingDetail` (example)
   - Input: `workorderId`
   - Output:
     - `workorderAccounting` object (fields above)
     - `completionJournalEntry` (optional)
     - `reversalJournalEntry` (optional)
     - `auditEvents[]`
     - `permissions` flags (optional but recommended): `canReverseCompletion`

2. **List Audit Events**
   - Service: `accounting.WorkorderAccountingAuditList`
   - Input: `workorderId`, paging params
   - Output: list + total

### Create/update calls (command)
1. **Request reversal**
   - Service: `accounting.reverseWorkorderCompletion`
   - Input: `workorderId`, `reasonCode?`, `reasonText?`
   - Output (success):
     - `reversalJournalEntryId`
     - updated `accountingStatus`
     - `isInvoiceReady=false`
     - created `auditId`
   - Output (failure): structured errors with code + message

### Submit/transition calls
- None beyond reversal command.

### Error handling expectations
- 401/403 → show “Not authorized” and hide/disable reversal action.
- 404 workorder not found → show not-found state.
- 409 conflict (already invoiced / already reversed / state mismatch) → show inline error; do not mutate UI state; offer refresh.
- 422 validation (missing reasonCode, invalid state) → highlight fields and show message.
- 500/503 → show retry affordance; do not assume reversal happened.

---

## 10. State Model & Transitions

### Allowed states (UI must treat as enums)
Must be confirmed by backend; expected set (from backend reference):
- `InProgress`
- `Completed`
- `Reopened` (or use `InProgress` after reopen)
- `Invoiced`

### Role-based transitions
- Only users with reversal permission can request reversal from `Completed` → `Reopened/InProgress`.
- No reversal allowed from `Invoiced` (hard stop).

### UI behavior per state
- `Completed`:
  - If completion JE exists and not invoiced and not reversed → reversal action enabled
- `Reopened`/`InProgress`:
  - reversal action disabled; show “Workorder not completed” or “Already reopened”
- `Invoiced`:
  - reversal action disabled; show hard-stop warning
- Unknown state:
  - disable action; show “Unsupported status”; log client-side error for diagnostics

---

## 11. Alternate / Error Flows

### Validation failures
- Missing reasonCode (if required): keep dialog open; show required validation.
- Backend rejects due to invalid state: show message “Workorder not eligible for reversal (status: X).”

### Concurrency conflicts
- If backend uses optimistic locking and returns conflict:
  - UI shows “This record changed since you opened it. Refresh to continue.”
  - Provide “Refresh” button to reload.

### Unauthorized access
- If user can view but not reverse:
  - Screen loads read-only data.
  - Reversal action hidden or disabled with “You do not have permission.”

### Empty states
- No journal entries:
  - Show empty “No completion postings found; nothing to reverse.”
- No audit events:
  - Show empty “No audit events recorded.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View completed workorder with completion posting
Given I have permission to view workorder accounting  
And a workorder exists with accountingStatus "Completed"  
And the workorder has a completionJournalEntryId  
When I open the Workorder Accounting Detail screen for that workorderId  
Then I see the accountingStatus and isInvoiceReady value  
And I see the completion journal entry reference and details  
And I see the reversal action is enabled if no invoice exists and no reversal exists

### Scenario 2: Reverse completion successfully (manual command)
Given I have permission to reverse completion postings  
And a workorder exists with accountingStatus "Completed" and isInvoiceReady true  
And the workorder is not invoiced  
And no reversalJournalEntryId exists  
When I click "Reverse completion postings" and confirm (and provide a reason if required)  
Then the frontend calls the reversal service with workorderId (and reason fields if required)  
And I see a success confirmation  
And I see isInvoiceReady becomes false  
And I see accountingStatus changes to "Reopened" or "InProgress" per backend response  
And I see a reversal journal entry reference displayed  
And I see a new audit event entry for the reversal

### Scenario 3: Reversal blocked because workorder is invoiced
Given I have permission to view workorder accounting  
And a workorder exists that is invoiced (status "Invoiced" or invoice reference present)  
When I open the Workorder Accounting Detail screen  
Then the reversal action is disabled (or not shown)  
And I see a message stating reversal is not permitted because the workorder is already invoiced  
And no reversal service call is possible from the UI

### Scenario 4: Reversal fails because original completion entry is missing
Given I have permission to reverse completion postings  
And a workorder appears eligible in the UI  
But the backend cannot find the original completion journal entry  
When I attempt reversal  
Then the frontend shows an error message indicating the completion entry was not found  
And the UI does not change accountingStatus or isInvoiceReady  
And the user can retry after refresh

### Scenario 5: Duplicate/already reversed
Given a workorder already has reversalJournalEntryId populated  
When I open the Workorder Accounting Detail screen  
Then the reversal action is disabled  
And I see a message "Already reversed"  
And I can view both original and reversal journal entry references

### Scenario 6: Unauthorized reversal attempt
Given I can view the workorder accounting screen  
But I do not have permission to reverse completion postings  
When I open the Workorder Accounting Detail screen  
Then the reversal action is disabled or hidden  
And if I attempt to call reversal via direct navigation/UI manipulation  
Then the backend returns 403 and the UI shows a not-authorized error

---

## 13. Audit & Observability

### User-visible audit data
- Audit timeline/list showing:
  - eventType (`WORKORDER_COMPLETION_REVERSED`)
  - timestamp
  - userId (reopenedByUserId if event-driven, or current user if manual)
  - reasonCode and reasonText (if available)
  - originalJournalEntryId and reversalJournalEntryId (if available)

### Status history
- Display current accountingStatus and last updated timestamp.
- If backend provides status history, show it; otherwise show audit events only.

### Traceability expectations
- UI must display identifiers to support support/audit:
  - `workorderId`
  - `completionJournalEntryId`
  - `reversalJournalEntryId`
  - `auditId` (optional display)
- Frontend logs (console or app logger) should include `workorderId` on load and reversal attempt failures (no PII beyond IDs).

---

## 14. Non-Functional UI Requirements
- **Performance:** Detail screen should load core summary within 2s under normal conditions; audit list may paginate.
- **Accessibility:** All actions keyboard accessible; confirmation dialog focus trapped; labels for inputs; error text associated to fields.
- **Responsiveness:** Works on tablet widths typical for POS back office.
- **i18n/timezone/currency:**
  - Display timestamps in user locale/timezone.
  - Display currency amounts using `currencyUomId` if journal entry lines/amounts are shown (amount display is optional unless required).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for missing journal entries/audit events; safe because it does not change domain logic; impacts UX Summary, Alternate / Error Flows.
- SD-UX-PAGINATION: Paginate audit events list (default page size 25) if long; safe because it is a UI ergonomics choice; impacts UX Summary, Data Requirements.
- SD-ERR-STD-MAPPING: Map common HTTP errors (401/403/404/409/422/5xx) to user messages and retry; safe because it’s standard error handling; impacts Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions

1. **Backend contract (blocking):** What are the exact Moqui service names, input/output fields, and entity names for:
   - Loading workorder accounting detail (status, invoice-ready, completion JE id, reversal JE id)
   - Listing audit events
   - Initiating reversal (if frontend should initiate), including required reason fields?
2. **Authorization (blocking):** What permission(s) gate the reversal action in the UI (exact permission ID/scope), and should the frontend rely on a `canReverseCompletion` flag from backend?
3. **State enum (blocking):** What are the authoritative `accountingStatus` values and which one represents “reopened” (is it `Reopened` or `InProgress`)?
4. **Invoiced detection (blocking):** How does the frontend determine “already invoiced”—via `accountingStatus=Invoiced`, an `invoiceId` reference, or a separate service call?
5. **Reason codes (blocking if required):** Is `reasonCode` mandatory for reversal requests, and if so what is the allowed value set and how is it loaded (enum vs service)?
6. **Event-driven vs user-driven (blocking):** For this frontend story, is reversal *only* event-driven on `WorkorderReopened`, or should users be able to manually request reversal from UI as an operational tool?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Reverse Completion on Workorder Reopen — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/182

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Accounting: Reverse Completion on Workorder Reopen  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/182  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Reverse Completion on Workorder Reopen

**Domain**: user

### Story Description

/kiro  
Safely reverse completion-related accounting state.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `WorkorderReopened` event

## Main Flow
1. Validate reopen authorization
2. Reverse WIP/finished postings if present
3. Mark workorder as not invoice-ready
4. Record reversal audit trail

## Acceptance Criteria
- [ ] Accounting state matches reopened workorder
- [ ] Reversal is fully auditable

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

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