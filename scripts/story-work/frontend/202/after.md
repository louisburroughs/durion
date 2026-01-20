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

STOP: Clarification required before finalization

---

## 1. Story Header

**Title**: [FRONTEND] [STORY] Mapping: Configure EventType → Posting Rule Set

**Primary Persona**: Financial Controller / System Administrator

**Business Value**: Enables controlled, auditable configuration of versioned posting rules that deterministically generate balanced journal entries and provide traceability (journal entry → ruleset + version), reducing accounting errors and audit risk.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Financial Controller or System Administrator  
- **I want** to create, version, validate, publish, and archive Posting Rule Sets that map a business `EventType` to accounting postings (with conditional logic)  
- **So that** journal entry generation is balanced, deterministic, and traceable to the exact rule version used.

### In-scope
- Frontend screens to:
  - List Posting Rule Sets and versions
  - View a Posting Rule Set (including version, status, eventType, rules definition)
  - Create a new Posting Rule Set (version 1)
  - Create a new version from an existing rule set/version
  - Publish a DRAFT version (only if it balances / passes server validation)
  - Archive a version (per backend policy)
- Validation UX:
  - Show server-side validation results (including “unbalanced” details)
  - Prevent publish action when backend reports imbalance/invalid references
- Traceability UX (read-only):
  - Display `postingRuleSetId` + `version` metadata clearly on detail views

### Out-of-scope
- Designing the internal semantics of debit/credit mappings (owned by backend rules engine)
- Creating or managing Chart of Accounts, Posting Categories, GL mappings (separate stories)
- Event ingestion processing UI, journal entry creation UI
- Defining or inventing `EventType` values (must come from upstream/canonical list)

---

## 3. Actors & Stakeholders
- **Primary actor:** Financial Controller / System Administrator
- **Stakeholders:**
  - Accounting operations team (needs safe configuration + auditability)
  - Auditors/Compliance (needs immutable version history + traceability)
  - Developers/Integrators (need predictable `EventType` mapping behavior)
- **Systems:**
  - Moqui frontend (this story)
  - Accounting backend service(s) exposing Posting Rule Set APIs (authoritative)

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS admin UI.
- User has required permissions to manage accounting configuration (exact permission strings **TBD**; see Open Questions).
- Backend endpoints exist for:
  - Listing rule sets and versions
  - Viewing a rule set/version
  - Creating rule set and new versions
  - Publishing and archiving
  - Validation errors returned with actionable codes/messages
- Backend provides a source of valid/recognized `EventType` values (endpoint **TBD**; see Open Questions).
- Backend validates referenced GL accounts and rule balancing and returns structured validation failures.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Admin navigation: **Accounting → Posting Rules → Posting Rule Sets** (exact menu placement may vary; implement as a screen entry under accounting configuration)

### Screens to create/modify
1. **Screen:** `apps/accounting/PostingRuleSet/List.xml`
   - List/search table of Posting Rule Sets (latest version summary at minimum)
2. **Screen:** `apps/accounting/PostingRuleSet/Detail.xml`
   - Detail view of a specific `postingRuleSetId`
   - Versions subview (list of versions with status)
3. **Screen:** `apps/accounting/PostingRuleSet/EditVersion.xml`
   - Create version 1 (new rule set) or create new version from an existing one
4. **Dialog/Screen section:** Publish confirmation + results display (could be a transition returning validation result)

### Navigation context
- From list → select rule set → detail
- From detail → select version → view version detail
- From version view → “Create New Version” (clone) → edit → save as DRAFT
- From DRAFT version → “Publish” (runs backend validation; if fails, remain DRAFT and show errors)
- From PUBLISHED version → “Archive” (if allowed)

### User workflows
- **Happy path: create & publish**
  1. User opens Posting Rule Set list
  2. Clicks “New Posting Rule Set”
  3. Selects `EventType`, defines rules JSON/structure, saves as DRAFT (version 1)
  4. Clicks Publish → backend validates balance and references → success → status becomes PUBLISHED
- **Alternate path: create new version**
  1. User opens existing ruleset detail
  2. Chooses a PUBLISHED version → “Create New Version”
  3. Edits rules; saves as DRAFT version N+1
  4. Publishes after backend validation
- **Error path: unbalanced**
  - Publish returns validation error describing imbalance and/or failing condition; UI shows error and blocks publish.

---

## 6. Functional Behavior

### Triggers
- User navigates to Posting Rule Set configuration screens
- User submits create/update version form
- User triggers publish or archive actions

### UI actions
- **List screen**
  - Filter/search (by `eventType`, `status`, date range; pagination)
  - Navigate to detail
- **Detail screen**
  - View metadata (ruleSetId, eventType)
  - View versions (version, status, createdAt/createdBy)
  - Actions:
    - Create New Version (clone)
- **Edit version screen**
  - Fields:
    - `eventType` (required; selection from canonical list)
    - `rulesDefinition` (required; JSON/structured editor—see Open Questions)
    - `status` displayed as read-only (DRAFT after save)
  - Save action creates DRAFT version (new or next version)
- **Publish action**
  - Confirmation prompt
  - Calls publish transition/service
  - On success: route back to version view; show success message; status now PUBLISHED
  - On failure: remain on version view/edit; show validation errors; status remains DRAFT
- **Archive action**
  - Confirmation prompt
  - Calls archive transition/service
  - On success: status becomes ARCHIVED; UI disables publish/edit actions

### State changes (frontend reflects backend state)
- DRAFT → PUBLISHED (publish)
- PUBLISHED → ARCHIVED (archive)
- Any “edit” operation results in **new version** (immutability enforced by backend); frontend must not attempt in-place update of a published/used version.

### Service interactions
- Load list/detail/version data from backend via Moqui service calls (REST or service facade)
- Submit create/version/publish/archive actions to backend
- Display backend validation results and error codes

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `eventType` is required.
- `rulesDefinition` is required and must be valid JSON if entered as JSON text.
- Publishing is blocked if backend validation fails:
  - Unbalanced debits/credits for any supported condition
  - Invalid GL account references
  - Unknown/invalid `EventType`
- Frontend must display backend error code(s) and messages in a user-actionable manner.

### Enable/disable rules
- If version `status=PUBLISHED` or `ARCHIVED`, editing fields are disabled (read-only).
- “Publish” button is visible/enabled only for `status=DRAFT`.
- “Archive” button visible/enabled only for `status=PUBLISHED` (unless backend allows otherwise; treat other states as disabled).
- “Create New Version” enabled on any existing rule set, but source version selection is required.

### Visibility rules
- Always show `postingRuleSetId` and `version` prominently on version detail.
- Show status badge for each version (DRAFT/PUBLISHED/ARCHIVED).

### Error messaging expectations
- For publish failures, show:
  - Summary: “Publish blocked: rule set failed validation”
  - Detail list including backend-provided reasons (e.g., imbalance per condition)
- For unknown `EventType`, show actionable: “Select a recognized EventType (refresh list if newly added).”

---

## 8. Data Requirements

### Entities involved (frontend view models; backend-owned)
- `PostingRuleSet` (rule set identity + eventType)
- `PostingRuleSetVersion` (versioned configuration)
- (Reference) `EventType` catalog/list (source-of-truth upstream/back-end exposed)

### Fields (type, required, defaults)
**PostingRuleSet (logical)**
- `postingRuleSetId` (UUID, read-only)
- `eventType` (string, required, editable only when creating version 1 unless backend allows per-version eventType changes—see Open Questions)

**PostingRuleSetVersion**
- `postingRuleSetId` (UUID, required, read-only)
- `version` (int, required, read-only)
- `status` (`DRAFT|PUBLISHED|ARCHIVED`, required, read-only except via actions)
- `rulesDefinition` (JSON, required, editable only for DRAFT before publish)
- `createdAt` (datetime, read-only)
- `createdBy` (string/userId, read-only)
- `updatedAt` (datetime, read-only)
- `updatedBy` (string/userId, read-only)

### Read-only vs editable by state/role
- **DRAFT**: editable `rulesDefinition` (and `eventType` if creating new ruleset)
- **PUBLISHED/ARCHIVED**: all fields read-only; only “Create New Version” allowed
- Role-based enforcement must also be performed by backend; frontend should hide/disable actions when backend indicates unauthorized.

### Derived/calculated fields
- “Latest version” computed for list view display (either backend provides `latestVersion` or frontend calculates after loading versions)
- Validation result details displayed after publish attempt (from backend response)

---

## 9. Service Contracts (Frontend Perspective)

> Endpoint/service names are **TBD**; frontend must integrate via Moqui service calls consistent with project conventions. Define these as Moqui `service-call` stubs that map to backend REST until concrete names are confirmed.

### Load/view calls
- **List rule sets**
  - Input: optional filters (`eventType`, `status`, paging)
  - Output: list of rule sets with latest version summary
- **Get rule set detail**
  - Input: `postingRuleSetId`
  - Output: rule set + versions list
- **Get version detail**
  - Input: `postingRuleSetId`, `version`
  - Output: version record including `rulesDefinition`

- **List recognized EventTypes**
  - Input: optional search
  - Output: array of `eventType` strings (and optional description/schemaVersion)

### Create/update calls (via new version)
- **Create new rule set (version 1, DRAFT)**
  - Input: `eventType`, `rulesDefinition`
  - Output: `postingRuleSetId`, `version=1`, `status=DRAFT`
- **Create new version (clone + edit)**
  - Input: `postingRuleSetId`, `baseVersion` (optional but recommended), `rulesDefinition`
  - Output: `version=baseVersion+1`, `status=DRAFT`

### Submit/transition calls
- **Publish version**
  - Input: `postingRuleSetId`, `version`
  - Output success: updated version with `status=PUBLISHED`
  - Output failure: `400` with structured validation errors, including imbalance details
- **Archive version**
  - Input: `postingRuleSetId`, `version`
  - Output: updated version with `status=ARCHIVED` (or backend-defined result)

### Error handling expectations
- Map HTTP errors to UI:
  - `400` validation: show field/summary errors (do not retry automatically)
  - `401/403`: show “Not authorized” and disable actions; offer navigation away
  - `404`: show “Rule set/version not found” and return to list
  - `409`: show conflict (e.g., publishing non-DRAFT, concurrent version creation); prompt reload
  - `5xx`: show generic error + correlation id if provided; allow retry

---

## 10. State Model & Transitions

### Allowed states (version status)
- `DRAFT`
- `PUBLISHED`
- `ARCHIVED`

### Role-based transitions
- Users with posting-rule management permission can:
  - Create DRAFT versions
  - Publish DRAFT → PUBLISHED
  - Archive PUBLISHED → ARCHIVED
- Users without permission: read-only access (if allowed) or blocked entirely (TBD; see Open Questions)

### UI behavior per state
- **DRAFT**: editable rules; show “Publish” primary action
- **PUBLISHED**: read-only; show “Create New Version” action; show “Archive” secondary action
- **ARCHIVED**: read-only; hide publish/archive; allow “Create New Version” (if business allows) else disable (TBD)

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid JSON in rulesDefinition:
  - Block save client-side with “Invalid JSON”
- Backend validation on publish:
  - Show list of failing conditions and debit/credit totals (as provided)
  - Keep version as DRAFT
  - Do not silently modify rulesDefinition

### Concurrency conflicts
- If another user creates a newer version while current user is editing:
  - Save attempt may return `409` (or similar)
  - UI should prompt to reload versions list and rebase (create a new version from latest)

### Unauthorized access
- If user lacks permission:
  - Hide “New”, “Publish”, “Archive”, “Create New Version” actions
  - If direct URL access attempted, show error and route to list or unauthorized screen

### Empty states
- No rule sets exist:
  - Show empty state with “Create Posting Rule Set”
- No EventTypes returned:
  - Show message: “No EventTypes available. Check integration configuration.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: List posting rule sets
**Given** I am an authenticated user with permission to view posting rule sets  
**When** I navigate to Accounting → Posting Rules → Posting Rule Sets  
**Then** I see a paginated list of existing posting rule sets  
**And** each row shows at least `eventType`, `postingRuleSetId`, and latest version `status`

### Scenario 2: Create a new rule set as DRAFT
**Given** I have permission to manage posting rule sets  
**And** the system provides a list of recognized `EventType` values  
**When** I create a new posting rule set with a selected `eventType` and a valid `rulesDefinition`  
**Then** the system creates version `1` with `status=DRAFT`  
**And** I can view the new rule set version detail showing `postingRuleSetId` and `version=1`

### Scenario 3: Publish a balanced DRAFT version
**Given** a posting rule set version exists with `status=DRAFT`  
**When** I click “Publish” and confirm  
**Then** the frontend calls the publish service for that `postingRuleSetId` and `version`  
**And** on success the version status updates to `PUBLISHED`  
**And** the UI disables editing for that published version

### Scenario 4: Reject publish for unbalanced rules
**Given** a posting rule set version exists with `status=DRAFT`  
**And** its rules are unbalanced for at least one supported condition  
**When** I click “Publish”  
**Then** the system responds with a validation failure (HTTP 400)  
**And** the UI shows an error summary and the backend-provided imbalance details  
**And** the version remains `DRAFT` and editable

### Scenario 5: Create a new version from an existing rule set
**Given** a posting rule set exists with a `PUBLISHED` version `N`  
**When** I choose “Create New Version” from version `N` and save changes  
**Then** the system creates a new version `N+1` with `status=DRAFT`  
**And** version `N` remains unchanged and read-only

### Scenario 6: Unauthorized user cannot publish
**Given** I am authenticated but do not have permission to publish posting rule sets  
**When** I view a DRAFT posting rule set version  
**Then** I do not see an enabled “Publish” action  
**And** if I attempt to publish via direct URL/action, I receive a not-authorized error and no state changes occur

---

## 13. Audit & Observability

### User-visible audit data
- On version detail, display:
  - `createdAt`, `createdBy`, `updatedAt`, `updatedBy`
  - Status and status change timestamps if provided by backend

### Status history
- Show a version list with status and created timestamp.
- If backend provides audit events (recommended), provide a collapsible “Audit” section per version (actor, action: created/published/archived, timestamp, correlation id).

### Traceability expectations
- UI must always present `postingRuleSetId` + `version` for any view of rulesDefinition.
- Any publish/archive action should log (frontend) a structured info event containing `postingRuleSetId`, `version`, outcome, and correlation id (if returned).

---

## 14. Non-Functional UI Requirements
- **Performance:** List view should load within 2s for up to 50 rows/page (assuming normal network); support server-side pagination.
- **Accessibility:** All actions keyboard-navigable; validation messages associated to inputs; sufficient contrast for status indicators.
- **Responsiveness:** Works on tablet width; tables horizontally scroll or collapse columns.
- **i18n/timezone:** Display timestamps in user locale/timezone (from session settings).  
- **Security:** Do not expose rulesDefinition or error payloads in logs beyond what’s necessary; no secrets.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty state with a primary CTA (“Create Posting Rule Set”) because it is UI ergonomics and does not change domain logic. (Impacted: UX Summary, Error Flows)
- SD-UX-PAGINATION: Use server-side pagination with default page size 25 and user-selectable 25/50/100 because it is standard UI ergonomics. (Impacted: UX Summary, Service Contracts)
- SD-ERR-HTTP-MAP: Map HTTP 400/401/403/404/409/5xx to standard Quasar notifications + inline form errors where applicable because it’s standard error-handling behavior implied by backend contracts. (Impacted: Service Contracts, Alternate/Error Flows)
- SD-OBS-CORRELATION: Display and log correlation/request id when provided in response headers/body because it is observability boilerplate and does not alter business behavior. (Impacted: Audit & Observability, Error Flows)

---

## 16. Open Questions
1. **EventType source & contract:** What is the authoritative endpoint/service to retrieve recognized `EventType` values (and optional descriptions/schema versions)? Is the list static-config or backend-discovered?
2. **Rules editor format:** Should `rulesDefinition` be edited as:
   - raw JSON text,
   - a structured form builder UI,
   - or both (advanced JSON mode + guided form)?
   Provide the expected JSON schema for `rulesDefinition` if JSON is required.
3. **Permissions:** What are the exact permission names/scopes for:
   - viewing posting rule sets
   - creating/upversioning
   - publishing
   - archiving?
4. **Version creation semantics:** When creating a new version, does the API require `baseVersion` (explicit) or does it auto-increment latest? How are concurrent version creations handled (expected 409 behavior and message format)?
5. **Archiving policy:** Are archived versions still selectable for “Create New Version”? Is archiving allowed only for PUBLISHED, and can a version be un-archived?
6. **List view contract:** Does backend provide a flattened “latest version summary” per rule set, or must frontend fetch versions per rule set? (Impacts performance and API calls.)
7. **Validation detail payload:** For unbalanced rule sets, what exact structure is returned (e.g., per condition: debitTotal, creditTotal, failing rule IDs)? Provide sample error response for UI rendering.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Mapping: Configure EventType → Posting Rule Set  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/202  
Labels: frontend, story-implementation, inventory

## Frontend Implementation for Story

**Original Story**: [STORY] Mapping: Configure EventType → Posting Rule Set

**Domain**: inventory

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Maintain Chart of Accounts and Posting Categories

## Story
Mapping: Configure EventType → Posting Rule Set

## Acceptance Criteria
- [ ] Posting rules are versioned and referenced on every journal entry
- [ ] Rules produce balanced debit/credit outputs for representative test fixtures
- [ ] Rules support conditional logic (taxable/non-taxable, inventory/non-inventory)
- [ ] Publishing rules that don’t balance is blocked

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