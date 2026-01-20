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
[FRONTEND] [STORY] GL: Build Balanced Journal Entry from Event (Draft JE creation & review UI)

### Primary Persona
Accountant / GL Specialist (back-office user) who monitors and reviews draft Journal Entries generated from source events.

### Business Value
Provide an auditable, UI-accessible representation of automatically generated draft Journal Entries (JEs) created from domain events, enabling review/troubleshooting and ensuring only balanced, traceable entries proceed toward posting workflows.

---

## 2. Story Intent

### As a / I want / So that
- **As a** GL Specialist  
- **I want** to view the draft Journal Entry created from a source event, including header traceability (eventId/source refs/rule version) and balanced lines per currency  
- **So that** I can verify correctness, investigate mapping failures, and support downstream posting controls without needing database access.

### In-scope
- Moqui screen(s) to:
  - Search/list draft Journal Entries and open a JE detail view
  - View JE header traceability fields (event references, mapping rule version)
  - View JE lines including account/category/dimensions and debit/credit amounts
  - Display balance status **per currency** (balanced vs not balanced) as a computed UI check
- Error/empty states for missing data, unauthorized access, and load failures
- Basic navigation entry points from an Accounting/GL menu

### Out-of-scope
- Creating JEs from events in the frontend (event consumption/transformation is backend-owned)
- Editing JE lines or headers (draft generation is automated; story does not define manual correction)
- Posting JEs to the ledger (separate capability/story)
- Defining/maintaining mapping rules, suspense policies, or chart of accounts data management (separate stories)

---

## 3. Actors & Stakeholders
- **Primary user:** Accountant / GL Specialist
- **Secondary stakeholders:**
  - Auditor (needs traceability and immutable history visibility)
  - System Admin (triages mapping/rule issues)
  - Upstream domain owners (Billing, POS, Work Execution) as event producers

---

## 4. Preconditions & Dependencies
- Backend provides persisted draft Journal Entries generated from events, including:
  - Header refs: `eventId`, `sourceModule/sourceSystem`, `sourceEntityRef`, `mappingRuleVersionId` (or equivalent)
  - Lines with: account references, category references, dimension references, debit/credit amounts, currency
- Backend provides an API/service to:
  - List/search JEs
  - Load JE detail with lines
- Authorization permissions exist for viewing accounting/GL journal entries.

**Dependency note (blocking):** The frontend cannot be fully buildable until service names/endpoints, entity names, and permission strings are confirmed (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main menu: **Accounting → General Ledger → Journal Entries**
- Optional deep link: `/accounting/gl/journal-entry/<journalEntryId>` (exact path TBD per repo conventions)

### Screens to create/modify
1. **Screen: `Accounting/GL/JournalEntryList`**
   - Search/list journal entries (default filter Draft)
2. **Screen: `Accounting/GL/JournalEntryDetail`**
   - Read-only header + lines
   - Balance-by-currency summary panel
3. (Optional) **Screen: `Accounting/GL/EventProcessingFailures`** (only if backend exposes failure records; otherwise out-of-scope)

### Navigation context
- From list → click JE row → detail
- From detail → back to list (preserving prior filters in parameters where possible)

### User workflows
#### Happy path
1. User opens Journal Entry list
2. User filters by status = Draft, date range, source module, eventId
3. User opens a JE
4. User reviews header traceability + mapping rule version
5. User reviews lines and confirms balanced per currency (UI computed check matches backend indicator if present)

#### Alternate paths
- User searches by `eventId`; no results → empty state explaining no JE found for that event
- User opens JE detail but lacks permission → access denied screen/message
- JE loads but lines missing/invalid → show data integrity warning banner and allow copy of identifiers for support

---

## 6. Functional Behavior

### Triggers
- User navigates to JE list or JE detail screens.

### UI actions
- List screen:
  - Apply filters (status, date range, eventId, source system/module, currency)
  - Open JE detail
- Detail screen:
  - View header fields
  - View lines table
  - View balance summary per currency
  - Copy key identifiers (journalEntryId, eventId, mappingRuleVersionId) to clipboard (UI-only convenience)

### State changes
- None (read-only in this story). No transitions or edits.

### Service interactions
- `searchJournalEntries` (list)
- `getJournalEntryDetail` (header + lines)
- (Optional) `getJournalEntryAuditTrail` if backend exposes JE audit events

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filters:
  - `eventId` input must be a valid UUID format if provided; otherwise show inline validation error and do not submit search.
  - Date range must have `from <= to` else inline error.
- JE detail display:
  - If JE has multiple currencies, compute balancing per currency group and display per currency totals.
  - If any currency group is not balanced, show **prominent warning**: “Not balanced for currency <CUR>: debits X != credits Y”. (This should be possible only if backend allowed it; still display defensively.)

### Enable/disable rules
- “View detail” action disabled until a row is selected (if using selection model) or always enabled via row click.
- Any “Post”, “Edit”, “Rebuild” buttons **must not** be present unless explicitly required by a future story.

### Visibility rules
- Show mapping rule version only if present; if absent, show “Not available” and a warning badge “Missing mapping reference”.

### Error messaging expectations
- Map backend errors to user-facing messages:
  - 401/403: “You don’t have access to General Ledger journal entries.”
  - 404 on detail: “Journal Entry not found (may have been deleted or you may not have access).”
  - 409/422: show backend-provided error code + message (sanitized) and include IDs for support.

---

## 8. Data Requirements

### Entities involved (conceptual; exact Moqui entity names TBD)
- `JournalEntry` (header)
- `JournalEntryLine` (lines)
- (Optional) `PostingRuleSetVersion` or `MappingRuleVersion`
- (Optional) `GLAccount`, `PostingCategory`, `Dimension` references for display names

### Fields (type, required, defaults)
**JournalEntry (Header)**
- `journalEntryId` (string/UUID, required, read-only)
- `status` (enum: at least `Draft`; required, read-only)
- `transactionDate` (date/datetime, required, read-only)
- `sourceEventId` / `eventId` (UUID, required, read-only)
- `sourceModule` or `sourceSystem` (string, required, read-only)
- `sourceEntityRef` (string, required, read-only)
- `schemaVersion` (string, optional, read-only) — if stored
- `mappingRuleVersionId` (string/UUID, required for traceability; if missing, UI flags)
- `businessUnitId` (string/UUID, optional; display if available)
- `currencyUomId` (string; optional if multi-currency at line level)

**JournalEntryLine**
- `journalEntryLineId` (string/UUID, required)
- `lineNumber` (int, optional but preferred for stable ordering)
- `postingCategoryId` / `glCategoryId` (string/UUID, required)
- `glAccountId` (string/UUID, required)
- `dimensions` (object or separate fields; optional but may be required by rule)
  - examples: `locationId`, `departmentId`, `customerId`, etc. (do not invent which ones are required)
- `currencyUomId` (string, required)
- `debitAmount` (decimal, nullable)
- `creditAmount` (decimal, nullable)
  - Exactly one of debit/credit should be non-zero per line (if backend supports); UI displays both columns.
- `memo/description` (string, optional)

### Read-only vs editable by state/role
- All fields are read-only for all roles in this story.

### Derived/calculated fields (frontend)
- For each `currencyUomId`:
  - `totalDebits = sum(debitAmount)`
  - `totalCredits = sum(creditAmount)`
  - `isBalanced = (totalDebits == totalCredits)` using currency-scale comparison (see Open Question about rounding/scale in UI)

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking:** Actual Moqui service names, parameters, and response structures must be confirmed.

### Load/view calls
1. **List/search**
   - Service: `AccountingServices.searchJournalEntries` (placeholder)
   - Inputs (query params):
     - `status` (default `Draft`)
     - `fromDate`, `toDate`
     - `eventId`
     - `sourceModule`
     - `currencyUomId`
     - pagination: `pageIndex`, `pageSize`
     - sorting: `orderBy` (default newest first by transactionDate/createdAt)
   - Output:
     - array of JE headers + `totalCount`

2. **Detail**
   - Service: `AccountingServices.getJournalEntry` (placeholder)
   - Input: `journalEntryId`
   - Output:
     - header
     - lines[]
     - (optional) computed backend balance status per currency (if provided)

### Create/update calls
- None.

### Submit/transition calls
- None.

### Error handling expectations
- Standard Moqui service error structure surfaced to UI with:
  - `errorCode` (preferred)
  - `message`
  - field errors (for filter validation if backend validates)
- For list/detail failures, UI must show a retry action.

---

## 10. State Model & Transitions

### Allowed states
- `Draft` (required for this story)
- `Posted`, `Cancelled` may exist but are view-only if returned.

### Role-based transitions
- None in this story.

### UI behavior per state
- Draft: normal view
- Posted/Cancelled (if shown in list/detail): show status badge; still read-only.

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid `eventId` format on list filter → inline error; do not call backend.
- Invalid date range → inline error; do not call backend.

### Concurrency conflicts
- If JE disappears between list and detail:
  - Detail load returns 404 → show not found message and link back to list.

### Unauthorized access
- 403 on list or detail:
  - Show access denied page; do not reveal existence of specific JE IDs beyond user-provided input.

### Empty states
- No JEs match filters:
  - Show “No journal entries found” with suggestions (clear filters, search by eventId).

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View draft journal entries list with default Draft filter
**Given** the user has permission to view GL journal entries  
**When** the user navigates to Accounting → General Ledger → Journal Entries  
**Then** the system displays a list of journal entries filtered to status “Draft” by default  
**And** each row shows at minimum journalEntryId, transactionDate, status, sourceEventId/eventId, and sourceModule/sourceSystem  
**And** the user can open a journal entry detail from the list.

### Scenario 2: Search journal entries by eventId
**Given** the user is on the Journal Entries list screen  
**When** the user enters a valid UUID into the Event ID filter and submits  
**Then** the system requests results filtered by that eventId  
**And** if a matching journal entry exists it is shown in the results  
**And** if no matching journal entry exists the UI shows an empty state indicating no results.

### Scenario 3: Prevent search with invalid eventId
**Given** the user is on the Journal Entries list screen  
**When** the user enters an invalid Event ID value (not a UUID) and submits  
**Then** the UI shows an inline validation error for Event ID  
**And** no backend search request is sent.

### Scenario 4: View journal entry detail with traceability header fields
**Given** a draft Journal Entry exists  
**And** the user has permission to view it  
**When** the user opens the Journal Entry detail screen  
**Then** the UI displays the JE header including sourceEventId/eventId, sourceEntityRef, sourceModule/sourceSystem, transactionDate, status, and mappingRuleVersionId  
**And** the UI displays all journal entry lines with debit and credit columns, currency, glAccount reference, posting category reference, and dimension references (if present).

### Scenario 5: Balance check is shown per currency
**Given** a journal entry detail is displayed with lines in one or more currencies  
**When** the UI computes totals grouped by currency  
**Then** the UI shows total debits and total credits for each currency  
**And** for each currency where totals are equal the UI indicates “Balanced”  
**And** for each currency where totals are not equal the UI indicates “Not balanced” and shows the mismatch values.

### Scenario 6: Unauthorized user cannot access journal entries
**Given** the user does not have permission to view GL journal entries  
**When** the user navigates to the Journal Entries list or opens a JE detail URL directly  
**Then** the system shows an access denied message  
**And** does not display journal entry header or line data.

### Scenario 7: Journal entry not found
**Given** the user has permission to view GL journal entries  
**When** the user opens a journalEntryId that does not exist (or is not accessible)  
**Then** the system shows “Journal Entry not found”  
**And** provides a link back to the Journal Entries list.

---

## 13. Audit & Observability

### User-visible audit data
- Display immutable identifiers for traceability:
  - `journalEntryId`, `sourceEventId/eventId`, `mappingRuleVersionId`, `sourceModule/sourceSystem`, `sourceEntityRef`
- If backend supports, show:
  - created timestamp and createdBy (read-only)
  - processing status / last processed timestamp (if JE is tied to event ingestion pipeline)

### Status history
- Not required unless backend already provides status history; if available, render a read-only timeline component.

### Traceability expectations
- Provide “Copy identifiers” action to copy a block containing:
  - journalEntryId
  - eventId
  - mappingRuleVersionId
  - sourceModule
  - transactionDate

---

## 14. Non-Functional UI Requirements
- **Performance:** List screen should load first page within 2s under normal conditions; show loading states.
- **Accessibility:** All tables and badges must be keyboard navigable; status conveyed with text (not color only).
- **Responsiveness:** List and detail usable on tablet widths; lines table supports horizontal scroll.
- **i18n/timezone/currency:**
  - Dates displayed in user locale/timezone.
  - Currency amounts formatted with currency code; do not assume symbol-only.
  - No multi-currency conversion performed in UI.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty state messaging and “Clear filters” action; safe because it does not change domain behavior. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-PAGINATION: Paginate list results with page size default (e.g., 25) and server-side pagination parameters; safe because it is a UI ergonomics concern. (Impacted: Service Contracts, UX Summary)
- SD-ERR-STD: Standard mapping of HTTP 401/403/404 to user-friendly messages with retry/back link; safe because it does not alter business rules. (Impacted: Business Rules, Error Flows)

---

## 16. Open Questions

1. **Backend API/service contract (blocking):** What are the exact Moqui services (names, input params, output fields) to:
   - search/list Journal Entries
   - load Journal Entry detail (including lines)
   - (optional) load JE audit/status history?
2. **Permissions (blocking):** What are the exact permission strings/roles for viewing GL journal entries in the frontend (e.g., `gl.je.view`, `accounting.je.view`)?
3. **Entity/field naming (blocking):** Are the canonical fields named `sourceEventId` vs `eventId`, and `mappingRuleVersionId` vs something else? What are the exact dimension fields available on JE lines?
4. **Rounding/scale for UI balance check (blocking):** Should the UI compare debits/credits using:
   - exact decimal equality as provided by backend, or
   - currency-scale rounding before comparison (and what scale per currency)?
5. **Failure policy visibility (clarification):** The requirement mentions “Mapping failures route to suspense or rejection per policy.” Should the frontend expose a view of:
   - failed events / quarantined items / DLQ references,
   - or is that handled in another admin tool/story?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] GL: Build Balanced Journal Entry from Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/201  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] GL: Build Balanced Journal Entry from Event

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Post Journal Entries to the General Ledger

## Story
GL: Build Balanced Journal Entry from Event

## Acceptance Criteria
- [ ] Mapped events create a draft journal entry with header refs (eventId/source refs/rule version)
- [ ] JE is balanced per currency (debits=credits)
- [ ] Each JE line includes category, account, and dimension references
- [ ] Mapping failures route to suspense or rejection per policy (no partial postings)

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