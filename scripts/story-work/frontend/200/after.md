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
[FRONTEND] [STORY] GL: Post Journal Entry with Period Controls and Atomicity

### Primary Persona
GL Accountant / Controller (back-office user responsible for posting journal entries)

### Business Value
Enable controlled, auditable posting of journal entries to the ledger with strict period enforcement and atomic commit semantics, preventing financial corruption and ensuring traceability from source events to posted entries.

---

## 2. Story Intent

### As a / I want / So that
- **As a** GL Accountant / Controller  
- **I want** to review a draft journal entry and post it to the ledger only when the accounting period is eligible  
- **So that** ledger balances and trial balance aggregates remain consistent, and the originating source event is marked Posted with a permanent journal entry reference.

### In-scope
- A Moqui screen flow to **view a Journal Entry** and **initiate posting**
- UI display of **period status** for the JE posting date (Open/Closed/other if returned)
- Posting action that calls a backend “post” service and handles:
  - atomic success
  - period-closed rejection (or alternate policy if defined)
  - validation failures (unbalanced, invalid account, etc.)
  - concurrency conflicts
- Display of post results: posted timestamp/status, JE reference, and source event linkage

### Out-of-scope
- Creating/editing journal entries and journal entry lines
- Defining accounting periods, opening/closing periods
- Defining GL accounts, posting rule sets, or mappings
- Any GL account balance drilldown UI (only confirm that posting updates are reflected via returned status/data)
- Implementing backend atomicity/period logic (frontend consumes it)

---

## 3. Actors & Stakeholders
- **GL Accountant / Controller (primary user):** initiates posting, needs clear blocking reasons and audit evidence.
- **Auditor (secondary stakeholder):** expects immutable traceability and viewable status history.
- **Upstream Source System / Module (stakeholder):** needs the source event marked Posted with JE reference.
- **System Admin (stakeholder):** expects permission enforcement and safe error messaging.

---

## 4. Preconditions & Dependencies

### Preconditions
- A `JournalEntry` exists in a **postable pre-post state** (exact state names TBD; see Open Questions) and is balanced per backend validation rules.
- User is authenticated in the frontend.

### Dependencies (must exist or be stubbed)
- Backend endpoints/services to:
  - Load a journal entry by ID (including lines, totals, postingDate, status)
  - Fetch accounting period status for a posting date **or** provide it on JE load
  - Post a journal entry (atomic operation)
- Authorization contract defining what permission is required to post (TBD; see Open Questions)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a GL work queue/list (if exists) linking to:  
  `#/gl/journal-entry/<journalEntryId>` (route/path TBD to match repo conventions; see Open Questions)
- Direct navigation via URL with `journalEntryId`

### Screens to create/modify
1. **Screen: `GL/JournalEntryDetail` (new or modify existing)**
   - Shows JE header + lines + computed totals + current status
   - Contains primary action: **Post Journal Entry**
   - Shows accounting period status for JE posting date (Open/Closed/etc.)

2. **Dialog/Subscreen: `GL/PostJournalEntryConfirm` (recommended)**
   - Confirmation step summarizing impacts:
     - posting date & derived period
     - JE balanced status (as known by backend)
     - source event reference (if present)
   - Action: Confirm Post

### Navigation context
- Breadcrumb: GL → Journal Entries → Journal Entry Detail
- After successful posting:
  - Stay on detail screen and refresh state (preferred)
  - Show success banner with posted reference info

### User workflows
**Happy path**
1. User opens JE detail
2. User confirms JE is ready, sees period is Open
3. User clicks Post → confirm dialog → Confirm
4. UI calls post service
5. UI displays Posted state + JE/ledger references and disables further posting

**Alternate paths**
- Period closed: user attempts post, backend rejects; UI shows blocking reason and next-step guidance per policy (policy unclear → see Open Questions)
- Validation failure (unbalanced/invalid accounts): UI shows actionable errors; user cannot post
- Concurrency: JE already posted/changed; UI prompts refresh and shows latest state

---

## 6. Functional Behavior

### Triggers
- Screen load with `journalEntryId`
- User clicks **Post Journal Entry**
- User confirms posting in dialog

### UI actions
- **On load:** call load service; render header/lines/totals/status
- **Post button enabled only when:**
  - user has permission to post (if permission is exposed to UI or returned via service)
  - JE status is in “Ready/Draft/Approved” (exact states TBD)
  - JE is not already Posted
  - Period status is Open (or policy-driven)
- **On Post confirm:**
  - call post service with `journalEntryId` (and any required parameters, e.g., expectedVersion)

### State changes (frontend-visible)
- JE status transitions to `Posted` on success
- Source event status shown as `Posted` with JE reference (if source event is displayed/returned)
- UI becomes read-only for posting action (post button hidden/disabled)
- Status history/audit section updates with posting attempt outcome (if returned)

### Service interactions (Moqui)
- Use Moqui screen actions (`service-call` or `entity-find` as appropriate) to:
  - load JE data
  - post JE
- Use transitions for:
  - refresh after post
  - error routing (remain on same screen with messages)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (UI behavior)
- UI must treat backend as authoritative for:
  - balanced check
  - period eligibility
  - account validity/locked status
  - atomic posting eligibility
- UI must display backend validation errors inline at top as message list and near relevant fields where possible.

### Enable/disable rules
- Disable Post action when JE is already Posted.
- Disable Post action when backend indicates period is Closed (unless policy allows override/redirect; TBD).
- Disable Post action while a post request is in flight (prevent double-submit).

### Visibility rules
- If JE status is Posted:
  - Show posted timestamp (if returned)
  - Show posting user (if returned)
  - Show immutable banner “Posted entries cannot be modified; corrections require a reversing entry” (text-only informational)

### Error messaging expectations
Backend error codes should be surfaced with user-friendly messages, while retaining a technical code for support (e.g., “Period is closed (BusinessRule:PeriodClosed)”).

---

## 8. Data Requirements

> Note: Exact entity field names are not provided in inputs; below is the **frontend data contract expectation** and must be mapped to actual Moqui entities/services during implementation.

### Entities involved (conceptual)
- `JournalEntry`
- `JournalEntryLine`
- `AccountingPeriod` (lookup by posting date, or embedded in JE response)
- `SourceEventRef` (reference to originating event/document)
- `LedgerEntry` (not required to display line-by-line, but JE should reflect that ledger posting occurred)

### Fields (type, required, defaults)

**JournalEntry (read-only in this story)**
- `journalEntryId` (string/UUID) — required
- `status` (string/enum) — required
- `postingDate` (date) — required
- `currencyUomId` (string) — required
- `description` (string) — optional
- `sourceEventId` (string/UUID) — optional (but required to satisfy “source event transitions” AC if applicable)
- `sourceEventType` (string) — optional
- `totalDebit` (decimal) — required (display)
- `totalCredit` (decimal) — required (display)
- `isBalanced` (boolean) — optional (if backend provides; otherwise derived visually by totals equality but **do not** treat as authoritative)
- `postedAt` (datetime) — optional (present if Posted)
- `postedByUserId` (string) — optional (present if Posted)
- `version` / `lastUpdatedStamp` (for optimistic locking) — optional but recommended

**JournalEntryLine (read-only)**
- `lineSeqId` or `lineId` — required
- `glAccountId` — required
- `glAccountName` — optional
- `debitAmount` (decimal >= 0) — required
- `creditAmount` (decimal >= 0) — required
- `memo` (string) — optional

**AccountingPeriod (read-only)**
- `accountingPeriodId` — optional
- `startDate` / `endDate` — optional
- `status` (Open/Closed/…) — required if shown

### Read-only vs editable by state/role
- All fields are **read-only** in this story.
- Only action is “Post” (state transition).

### Derived/calculated fields (frontend display only)
- “Out of balance amount” = abs(totalDebit - totalCredit) (for display); **backend still enforces** balanced rule.

---

## 9. Service Contracts (Frontend Perspective)

> Service names/endpoints are not provided; Moqui developer must bind to actual services. The following is the required contract shape.

### Load/view calls
1. **Get Journal Entry Detail**
   - Input: `journalEntryId`
   - Output: JournalEntry header + lines + (optional) period info + permissions hints
   - Errors:
     - `NOT_FOUND` → show “Journal Entry not found”
     - `UNAUTHORIZED`/`FORBIDDEN` → show access denied screen/message

2. **Get Accounting Period Status** (if not embedded)
   - Input: `postingDate` (and possibly `businessUnitId`)
   - Output: period status + id/date range
   - Errors: service unavailable → show warning “Period status unavailable; posting will be validated on submit.”

### Submit/transition calls
3. **Post Journal Entry (atomic)**
   - Input: `journalEntryId`
   - Optional inputs (TBD): `expectedVersion` for concurrency, `overridePolicyFlag` if overrides exist
   - Output on success:
     - updated JE status = Posted
     - `postedAt`, `postedBy`
     - reference(s) confirming ledger update and source event link (at minimum `journalEntryId` plus source event updated flag/id)
   - Error handling expectations (non-exhaustive):
     - `BusinessRule:PeriodClosed` → show blocking message; do not change UI state
     - `Validation:Unbalanced` → show validation message; do not change UI state
     - `Validation:InvalidAccount` → show validation message (include which account/line if returned)
     - `CONFLICT` (already posted / version mismatch) → prompt refresh and reload JE
     - `SERVER_ERROR` → show generic failure, keep user on page, allow retry if safe

---

## 10. State Model & Transitions

> Exact statuses are not defined in inputs; frontend must be tolerant to backend-provided values.

### Allowed states (minimum)
- `Draft` or `ReadyForPosting` (pre-post)
- `Posted` (terminal/immutable)

### Role-based transitions
- User with posting permission can transition:
  - pre-post → Posted (only if period eligible and validations pass)
- Users without permission:
  - cannot invoke post; should see disabled action or be blocked with a forbidden error if they attempt direct transition

### UI behavior per state
- **Pre-post:** show Post action (if permitted) and warnings if period not Open / period unknown.
- **Posted:** hide/disable Post; show posted metadata; show immutable info banner.

---

## 11. Alternate / Error Flows

### Validation failures
- Backend returns one or more validation errors:
  - UI displays error summary
  - Keeps user on detail screen
  - Post action remains available only if errors are transient (generally remains available after user refresh; no edits in this story)

### Concurrency conflicts
- If backend returns conflict indicating JE already posted/changed:
  - UI shows “This journal entry changed since you opened it”
  - Provide action: Refresh (re-load JE detail)
  - After refresh, reflect new status and disable posting if posted

### Unauthorized access
- If load fails with forbidden:
  - show access denied state, no JE data rendered

### Empty states
- JE has zero lines:
  - show “No lines available”
  - Post must be disabled; backend likely rejects as invalid (still handle if user tries via direct call)

---

## 12. Acceptance Criteria

### Scenario 1: Successful posting to an open period
**Given** I have permission to post journal entries  
**And** a journal entry exists in a pre-post status with debits equal credits  
**And** the journal entry posting date is within an Open accounting period  
**When** I open the Journal Entry detail screen  
**And** I click “Post Journal Entry” and confirm  
**Then** the system posts the journal entry successfully  
**And** the UI refreshes to show status “Posted” with posted timestamp/user (if provided)  
**And** the UI disables further posting actions for that entry  
**And** the UI shows that the source event is marked Posted with a journal entry reference (when source event info is provided by backend).

### Scenario 2: Attempted posting to a closed period is blocked
**Given** I have permission to post journal entries  
**And** a balanced journal entry exists in a pre-post status  
**And** the posting date is within a Closed accounting period  
**When** I attempt to post the journal entry  
**Then** the UI shows a blocking error indicating the period is closed (including backend error code `BusinessRule:PeriodClosed`)  
**And** the journal entry remains in the pre-post status in the UI after refresh  
**And** no posted metadata is shown.

### Scenario 3: Attempted posting of an unbalanced entry is rejected
**Given** I have permission to post journal entries  
**And** a journal entry exists where total debits do not equal total credits  
**When** I attempt to post the journal entry  
**Then** the UI shows a validation error including code `Validation:Unbalanced`  
**And** the journal entry status does not change to Posted.

### Scenario 4: Backend transaction failure results in no partial posting
**Given** I have permission to post journal entries  
**And** a balanced journal entry exists in an Open period  
**When** I attempt to post the journal entry  
**And** the backend responds with an internal error after beginning posting  
**Then** the UI shows a non-success error message and does not show Posted status  
**And** when I refresh the journal entry detail, it is still not Posted.

### Scenario 5: Concurrency conflict (already posted)
**Given** I have permission to post journal entries  
**And** I am viewing a pre-post journal entry  
**And** the journal entry is posted by another user/session  
**When** I attempt to post it  
**Then** the UI shows a conflict message and prompts me to refresh  
**And** after refresh, the UI shows status Posted and disables posting.

### Scenario 6: Unauthorized user cannot post
**Given** I do not have permission to post journal entries  
**When** I open the journal entry detail screen  
**Then** the Post action is not available (hidden or disabled)  
**And** if I attempt to invoke posting via direct URL/action, the UI shows an access denied error.

---

## 13. Audit & Observability

### User-visible audit data
- Display (when provided):
  - `postedAt`
  - `postedByUserId` (or username)
  - JE status history entries (if backend returns history)

### Status history
- At minimum, the UI should show current status.
- If backend returns history events (recommended), show chronological list including:
  - attempted posting (success/fail) with timestamp and actor

### Traceability expectations
- On success, UI must show a persistent link/reference between:
  - `sourceEventId` (if present) and `journalEntryId`
- UI must preserve and display backend-provided correlation identifiers in an expandable “Technical details” section (if provided), without exposing secrets.

---

## 14. Non-Functional UI Requirements
- **Performance:** JE detail loads within 2 seconds for up to 200 lines on typical network; show loading skeleton/spinner during fetch.
- **Accessibility:** All actions keyboard accessible; confirmation dialog focus-trapped; error summary announced via ARIA live region.
- **Responsiveness:** Usable on tablet widths; line table supports horizontal scroll if needed.
- **i18n/timezone/currency:**
  - Format currency amounts using `currencyUomId`
  - Display datetimes in user’s timezone; do not alter postingDate semantics.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide explicit empty-state messaging for “no lines” and “not found”; safe because it affects only UX clarity, not domain policy. (Impacted: UX Summary, Alternate/Empty states)
- SD-UI-DOUBLE-SUBMIT-GUARD: Disable Post button while request in-flight to prevent duplicate submits; safe because it reduces accidental repeats without changing business behavior. (Impacted: Functional Behavior, Error Flows)
- SD-ERR-MAP-GENERIC: Map unknown backend errors to a generic failure message while retaining technical code when available; safe because it doesn’t change outcomes, only presentation. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Closed period policy (blocking):** The requirement says “block posting or redirect per policy (with recorded rationale)”. What is the approved policy?
   - A) Strict block (reject)  
   - B) Redirect to next open period (auto-adjust date)  
   - C) Allow with override permission (requires explicit permission + audit reason capture UI)

2. **Posting permission contract (blocking):** What permission/scope controls posting journal entries in this system (e.g., `gl.je.post`, `accounting:gl:post`)? Should UI rely on:
   - a permissions payload returned by backend, or
   - a fixed permission name checked via Moqui artifact authz?

3. **JournalEntry state names (blocking for enable/disable rules):** What are the exact backend statuses for JE lifecycle (e.g., Draft/Approved/Ready/Posted/Void)? Which are eligible for posting?

4. **Service/API names and response shapes (blocking for Moqui wiring):** What are the actual Moqui services/endpoints for:
   - load JE detail (+ lines)
   - retrieve accounting period status (if separate)
   - post JE (and required request params like `expectedVersion`)?

5. **Source event linkage fields (blocking for AC completeness):** How is “Source event status transitions to Posted with JE reference” exposed to the frontend?
   - Is there a `sourceEvent` object returned on post?
   - Or must the UI call a separate service to verify/update source event status?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] GL: Post Journal Entry with Period Controls and Atomicity  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/200  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] GL: Post Journal Entry with Period Controls and Atomicity

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
GL: Post Journal Entry with Period Controls and Atomicity

## Acceptance Criteria
- [ ] Posting is atomic (all lines committed or none)
- [ ] Closed periods block posting or redirect per policy (with recorded rationale)
- [ ] Posting updates ledger/trial-balance aggregates
- [ ] Source event status transitions to Posted with JE reference


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