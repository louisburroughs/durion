## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Audit: Ledger Traceability & Explainability Viewer (JE ↔ Ledger Lines ↔ Source Event ↔ Mapping/Rule Versions)

## Primary Persona
Financial Controller / Auditor

## Business Value
Enable audit, reconciliation, and compliance workflows by providing an internal, read-only, end-to-end traceability view from any posted Journal Entry or Ledger Line back to the originating business event and the accounting configuration versions (mapping/rules) used—without allowing any mutation of posted accounting records.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Financial Controller / Auditor  
- **I want** a read-only “Explainability” view that shows the chain **Source Event → Mapping Version → Rule Version → Journal Entry → Ledger Lines** (and reversal relationships)  
- **So that** I can explain how a financial posting was produced, prove immutability, and trace any GL impact back to its source business document/event.

## In-scope
- Moqui frontend screens to **search and view** posted Journal Entries and Ledger Lines with:
  - Traceability identifiers: `sourceEventId`, `sourceEventType`, `mappingVersionId`, `ruleVersionId`
  - Immutable `sourceEventSnapshot` (rendered read-only)
  - Reversal linkage via `originalJournalEntryId` and/or `reversalJournalEntryId` (if present)
- UI affordances that make immutability explicit (read-only forms, no edit/delete actions for POSTED records)
- Navigation/drilldown between:
  - JE detail → ledger lines list
  - JE detail → mapping version detail (read-only)
  - JE detail → rule version detail (read-only)
  - JE detail → referenced original JE (reversal chain)
  - Ledger line detail → parent JE
- Frontend handling of backend immutability errors (e.g., attempted mutation returns 409) by ensuring the UI does not present mutation actions.

## Out-of-scope
- Creating/reversing/posting Journal Entries (command operations)
- Public-facing explainability endpoint design (backend owns API shape)
- Defining GL account mappings, posting rules, or chart-of-accounts semantics
- Changing backend data model, enforcement, or audit event emission

---

# 3. Actors & Stakeholders
- **Primary user:** Financial Controller / Auditor
- **Secondary users:** Accounting Ops, Support Engineer (internal troubleshooting)
- **Systems:** Moqui frontend, Accounting backend services/APIs (data source), Identity/permissions

---

# 4. Preconditions & Dependencies
- Backend persists and serves (per backend reference #124) Journal Entry fields:
  - `journalEntryId`, `status`, `postedTimestamp`, `sourceEventId`, `sourceEventType`, `mappingVersionId`, `ruleVersionId`, `originalJournalEntryId` (nullable), `sourceEventSnapshot` (JSON)
- Backend exposes read endpoints for:
  - Searching/listing Journal Entries and Ledger Lines
  - Viewing JE detail and its Ledger Lines
  - Viewing mapping version and rule version referenced by a JE (or at minimum IDs with a resolvable view)
- Permissions exist to allow read-only access for audit users (exact permission tokens are not defined in provided inputs)
- Moqui screen framework and project conventions available in `durion-moqui-frontend` README (dependency for routing/screen structure)

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Main navigation: **Accounting → Audit / Traceability**
- Deep links:
  - `/accounting/audit/journal-entry/{journalEntryId}`
  - `/accounting/audit/ledger-line/{ledgerLineId}`

## Screens to create/modify
Create new screens under an Accounting/Audit space (screen names indicative; align to repo conventions during implementation):
1. **AuditTraceabilitySearch** (search hub)
2. **JournalEntryDetail** (read-only)
3. **LedgerLinesByJournalEntry** (embedded section or sub-screen)
4. **LedgerLineDetail** (read-only)
5. **MappingVersionDetail** (read-only; if backend supports)
6. **RuleVersionDetail** (read-only; if backend supports)

## Navigation context
- Breadcrumbs: Accounting → Audit/Traceability → (Search | JE {id} | Ledger Line {id})
- Consistent “Back to results” behavior when arriving from search.

## User workflows
### Happy path (JE-centered)
1. User opens **Audit/Traceability Search**
2. Searches by `journalEntryId` or `sourceEventId` (optional additional filters)
3. Opens a **Journal Entry Detail**
4. Sees:
   - Status (POSTED/REVERSED/DRAFT if present but focus on POSTED)
   - Traceability IDs and timestamps
   - Source event snapshot (read-only JSON viewer)
   - Linked Mapping Version and Rule Version (click to open detail screens if available)
   - Ledger lines list (drill into a ledger line)
   - Reversal relationship (link to original JE if this JE is a reversal)

### Alternate path (Ledger-line-centered)
1. User searches or opens Ledger Line detail via deep link
2. Views Ledger Line detail and navigates to parent JE
3. Continues traceability chain from JE

### Alternate path (missing linked resources)
- If mapping/rule version details are not retrievable, UI still displays IDs and provides a “Copy ID” action; linked navigation is disabled with explanatory text.

---

# 6. Functional Behavior

## Triggers
- User navigates to the Audit/Traceability screens.
- User submits a search form.
- User opens a JE or Ledger Line detail page.
- User clicks drilldown links (to ledger lines, mapping version, rule version, original JE).

## UI actions
- Search form submit
- Result row click → navigate to detail
- Copy-to-clipboard for IDs (`journalEntryId`, `sourceEventId`, `mappingVersionId`, `ruleVersionId`)
- Expand/collapse source event snapshot rendering (read-only)

## State changes (frontend)
- No domain state transitions initiated by this story.
- Frontend state is purely view state: loading, loaded, empty, error.

## Service interactions (Moqui)
- Use Moqui screen transitions to invoke services that load:
  - JE list (search)
  - JE detail by ID
  - Ledger lines by JE ID
  - Ledger line detail by ID
  - Mapping version by ID (if available)
  - Rule version by ID (if available)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Search inputs:
  - If `journalEntryId` is provided, it must be a valid UUID format (UUIDv7 is still UUID format). If invalid, block submit and show inline error: `Invalid ID format`.
  - If `sourceEventId` is provided, validate UUID format similarly.
- Detail routes:
  - If route param is not a UUID, show Not Found / Invalid URL message and do not call backend.

## Enable/disable rules
- If JE `status` is `POSTED`:
  - UI must show **no edit/delete** actions.
- If JE `status` is `DRAFT` (if reachable):
  - Still treat as read-only in this story (since mutation/reversal is out-of-scope).
- If JE has `originalJournalEntryId`:
  - Show “Reversal of: {originalJournalEntryId}” and enable navigation link to original JE.
- If mapping/rule version endpoints are not available or return 404:
  - Disable navigation to those details; show IDs only.

## Visibility rules
- Always display traceability block for any JE:
  - `sourceEventId`, `sourceEventType`, `mappingVersionId`, `ruleVersionId`
  - If any is null/missing, display as “Missing” and flag as audit concern (non-blocking UI warning), because backend AC requires these for POSTED.

## Error messaging expectations
- Backend returns 401/403: show “You do not have access to view this audit data.”
- Backend returns 404: show “Not found.”
- Backend returns 409 on mutation attempts: should not be reachable via UI; if encountered (e.g., user hits an old route), show “This record is immutable; changes are not allowed.”

---

# 8. Data Requirements

## Entities involved (frontend view models)
- `JournalEntry`
- `LedgerLine` (or `LedgerEntry` depending on backend naming; must match actual API/entity)
- `PostingMappingVersion` (name TBD)
- `PostingRuleVersion` (name TBD)

## Fields (type, required, defaults)

### JournalEntry (read-only)
- `journalEntryId` (UUID, required)
- `status` (enum: DRAFT/POSTED/REVERSED, required)
- `postedTimestamp` (datetime UTC, required when status=POSTED; optional otherwise)
- `sourceEventId` (UUID, **required when status=POSTED**)
- `sourceEventType` (string, required when status=POSTED)
- `mappingVersionId` (UUID, required when status=POSTED)
- `ruleVersionId` (UUID, required when status=POSTED)
- `originalJournalEntryId` (UUID, optional)
- `sourceEventSnapshot` (JSON, required when status=POSTED per backend reference; render read-only)
- Standard audit fields if available: `createdAt`, `createdBy`, `updatedAt`, `updatedBy` (read-only)

### LedgerLine (read-only)
(Exact shape not provided; must be pulled from backend contract. UI should at minimum support:)
- `ledgerLineId` (UUID, required)
- `journalEntryId` (UUID, required)
- `glAccountId` or `glAccountCode` (string/UUID, optional if provided)
- `debitAmount` (decimal, optional)
- `creditAmount` (decimal, optional)
- `currencyUomId` (string, required if amounts shown)
- `postedTimestamp` (datetime, optional)
- Additional dimensions (optional, display if present)

## Read-only vs editable by state/role
- All fields are read-only in this story regardless of role.
- UI must not include form controls that imply editability for POSTED records.

## Derived/calculated fields (frontend-only)
- `isImmutable = (status == POSTED)` used to hide any mutation affordances
- Display helper: formatted timestamps in user locale while preserving UTC value in tooltip/details

---

# 9. Service Contracts (Frontend Perspective)

> Note: Provided inputs do not define actual Moqui services/endpoints. The frontend must integrate with whichever Moqui services are exposed. Below are required capabilities and suggested service names to be aligned during implementation.

## Load/view calls
- **Search Journal Entries**
  - Capability: list JEs by filters: `journalEntryId`, `sourceEventId`, `sourceEventType`, `status`, `postedTimestamp` range
  - Suggested service: `accounting.JournalEntrySearch`
  - Returns: paged list with summary fields
- **Get Journal Entry Detail**
  - Input: `journalEntryId`
  - Suggested service: `accounting.JournalEntryGet`
  - Returns: full JE record including `sourceEventSnapshot`
- **List Ledger Lines for a Journal Entry**
  - Input: `journalEntryId`
  - Suggested service: `accounting.LedgerLineListByJournalEntry`
- **Get Ledger Line Detail**
  - Input: `ledgerLineId`
  - Suggested service: `accounting.LedgerLineGet`
- **Get Mapping Version Detail (optional but desired)**
  - Input: `mappingVersionId`
  - Suggested service: `accounting.PostingMappingVersionGet`
- **Get Rule Version Detail (optional but desired)**
  - Input: `ruleVersionId`
  - Suggested service: `accounting.PostingRuleVersionGet`

## Create/update calls
- None (read-only story)

## Submit/transition calls
- None

## Error handling expectations
- 400 validation errors: surface field-level errors on search form
- 401/403: route to an “Unauthorized” state (screen message) without exposing data
- 404: show not found
- 409: show immutable/conflict message; log client-side as unexpected for read-only screens
- Network/timeouts: show retry action; do not partially render stale data without labeling it

---

# 10. State Model & Transitions

## Allowed states (displayed, not mutated)
- Journal Entry: `DRAFT`, `POSTED`, `REVERSED`

## Role-based transitions
- None initiated in UI (out-of-scope).  
- UI must assume backend enforces immutability: POSTED records cannot be updated/deleted; corrections via reversal JE.

## UI behavior per state
- `POSTED`:
  - Highlight immutability (read-only)
  - Require traceability fields displayed; if missing, show warning banner “Traceability data missing (unexpected for POSTED).”
- `REVERSED`:
  - Show reversal relationship fields if present (original JE link)
- `DRAFT`:
  - Read-only display only; no actions (this story)

---

# 11. Alternate / Error Flows

## Validation failures
- Invalid UUID in search fields → inline error; no service call
- Invalid UUID in route param → show invalid request page/state

## Concurrency conflicts
- If backend indicates the JE changed between list and detail (e.g., ETag mismatch), simply reload detail; since read-only, no merge required.

## Unauthorized access
- 401/403 from any load call → show access denied message; do not render partial sensitive content; provide link back to home/accounting.

## Empty states
- Search returns 0 results → show “No journal entries found” with tips (search by JE ID, source event ID)
- JE exists but has 0 ledger lines (unexpected) → show empty state “No ledger lines available” and allow user to copy JE ID for support.

---

# 12. Acceptance Criteria

## Scenario 1: Search journal entries by source event id
**Given** I have permission to view accounting audit data  
**And** I am on the Audit/Traceability Search screen  
**When** I enter a valid `sourceEventId` and submit the search  
**Then** the system displays a list of matching Journal Entries (paged)  
**And** each result shows `journalEntryId`, `status`, and `postedTimestamp` when available.

## Scenario 2: Prevent invalid ID input
**Given** I am on the Audit/Traceability Search screen  
**When** I enter an invalid UUID value into `journalEntryId`  
**And** I submit the search  
**Then** the UI blocks the request  
**And** shows an inline validation error indicating the ID format is invalid.

## Scenario 3: View JE explainability chain
**Given** I have opened a Journal Entry detail page for a `POSTED` entry  
**When** the page loads  
**Then** I can see `sourceEventId`, `sourceEventType`, `mappingVersionId`, and `ruleVersionId`  
**And** I can view the `sourceEventSnapshot` in a read-only format  
**And** I can view the list of Ledger Lines associated to the JE  
**And** each Ledger Line can be opened to see its details.

## Scenario 4: Reversal traceability navigation
**Given** I have opened a Journal Entry detail page for a JE that has `originalJournalEntryId` populated  
**When** I click the “Original Journal Entry” link  
**Then** I am navigated to the detail view for `originalJournalEntryId`  
**And** the system loads and displays that JE’s traceability and ledger lines.

## Scenario 5: Missing mapping/rule version details
**Given** I am on a Journal Entry detail page  
**And** the backend does not provide a retrievable detail for `mappingVersionId` (404 or capability absent)  
**When** the page renders  
**Then** the UI still displays the `mappingVersionId` value  
**And** disables the drilldown link with an explanation “Mapping version details unavailable.”

## Scenario 6: Unauthorized access
**Given** I do not have permission to view accounting audit data  
**When** I navigate to a Journal Entry detail URL  
**Then** the system shows an access denied state  
**And** does not display journal entry fields or ledger lines.

---

# 13. Audit & Observability

## User-visible audit data
- Display audit identifiers and timestamps:
  - `journalEntryId`, `postedTimestamp`, `status`
  - `sourceEventId`, `sourceEventType`
  - `mappingVersionId`, `ruleVersionId`
  - Reversal link (`originalJournalEntryId`) when present

## Status history
- If backend provides status history for JE, display as read-only timeline (optional). If not available, omit.

## Traceability expectations
- From JE detail, user can traverse to:
  - Ledger Lines
  - Mapping/Rule versions (when available)
  - Original JE (when reversal relationship exists)

---

# 14. Non-Functional UI Requirements

- **Performance:** JE detail page should load core JE header quickly; ledger lines and snapshot may load in parallel. Target: initial content rendered within 2s on typical broadband for average payload sizes.
- **Accessibility:** All interactive elements keyboard reachable; snapshot viewer supports scrolling and copy; labels for ID fields.
- **Responsiveness:** Works on tablet widths used in back-office; content stacks appropriately.
- **i18n/timezone/currency:** Timestamps displayed in user locale with UTC preserved; currency formatting based on `currencyUomId` when amounts shown (do not assume multi-currency beyond display).

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for zero search results and zero ledger lines; qualifies as safe UX ergonomics; impacts UX Summary, Alternate/Error Flows, Acceptance Criteria.
- SD-UX-PAGINATION: Use standard pagination for search results; safe and reversible UI behavior; impacts UX Summary, Service Contracts, Acceptance Criteria.
- SD-ERR-HTTP-MAP: Standard mapping of 401/403/404/409/network errors to UI states; safe because it does not change domain policy; impacts Service Contracts, Alternate/Error Flows.

---

# 16. Open Questions
1. What are the **exact Moqui services / REST endpoints** (names, parameters, response shapes) for:
   - JournalEntry search, get-by-id
   - LedgerLine list-by-JE, get-by-id
   - MappingVersion get-by-id
   - RuleVersion get-by-id
2. What permission(s)/scope(s) should gate access to these audit screens (e.g., `accounting.audit.view`), and which roles are expected to have them?
3. What is the canonical naming: **LedgerLine vs LedgerEntry** in the frontend/backend contract for posted lines?
4. Should the UI include a dedicated search by **GL account** and/or **posted date range**, or is MVP limited to ID-based lookups?
5. Is `sourceEventSnapshot` always present for POSTED entries, and are there any redaction requirements for sensitive fields in the snapshot before displaying it?

---

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/188

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/188  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability

**Domain**: general

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Audit: Maintain Immutable Ledger Audit Trail and Explainability

## Acceptance Criteria
- [ ] Ledger lines and JEs are immutable once posted (corrections via reversal)
- [ ] Store rule version and mapping version used for each posting
- [ ] Provide explainability view: event → mapping → rules → JE → ledger lines
- [ ] Full traceability from any GL line to source event/business document


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