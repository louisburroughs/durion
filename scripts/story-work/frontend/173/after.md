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

**Title:** [FRONTEND] [STORY] Party: Search and Merge Duplicate Parties (Basic)

**Primary Persona:** Admin (back-office / data steward)

**Business Value:** Reduce duplicate customer records so POS workorder customer selection and downstream history are clean, consistent, and resolvable after merges.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Admin  
- **I want** to search for potential duplicate Parties and merge exactly two Parties by selecting a survivor  
- **So that** customer selection and history remain accurate and references to prior Party IDs remain resolvable for downstream systems (e.g., Workorder Execution)

### In-scope
- A Moqui-based UI flow to:
  - search Parties by name/email/phone
  - select exactly two Party records
  - choose survivor vs source
  - confirm merge
  - show merge result (audit reference and alias/redirect behavior as supported by backend)
- Basic UX protections: preventing self-merge, enforcing “exactly two” selection, confirm dialog, error display.
- Displaying post-merge resolution guidance (e.g., “source party now redirects to survivor”) **if backend provides it**.

### Out-of-scope
- Complex “field-by-field conflict resolution” UI (choose which email/name/etc. to keep) unless backend contract explicitly supports it.
- Bulk merging (more than two at once).
- “Unmerge” / rollback UI.
- Defining or changing CRM data model/business policy (backend-owned).
- Editing Party details beyond what’s necessary to initiate the merge.

---

## 3. Actors & Stakeholders

- **Admin (Primary Actor):** Executes searches, selects Parties, initiates merge, confirms.
- **CSR / Service Advisor (Indirect Stakeholder):** Benefits from cleaner workorder selection and customer history.
- **Workorder Execution (Downstream Stakeholder):** Requires merged IDs to remain resolvable (alias/redirect) for historical references.
- **CRM Backend Services (System Actor):** Performs merge transaction, audit creation, aliasing/redirection.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend.
- User has authorization to perform Party merges (role/scope must be defined by backend/security configuration).
- At least two Party records exist.

### Dependencies (blocking where undefined)
- Backend endpoints/services for:
  - Party search (by name/email/phone; excluding merged parties)
  - Merge operation (sourcePartyId + survivorPartyId)
  - Optional: “resolve party id” (alias/redirect lookup) or consistent read behavior after merge  
- Backend definition of Party “merged” behavior (status vs deletion) and redirect mechanics.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From CRM/Party area: **Party Search** screen includes an action “Find Duplicates / Merge”.
- Direct navigation URL (proposed):  
  - `/crm/party/merge` (Search + selection)
  - `/crm/party/merge/confirm` (Confirmation)
  - `/crm/party/merge/result` (Result)

> If the repo’s existing screen path conventions differ, implement within the existing CRM/Party screen tree.

### Screens to create/modify (Moqui Screens)
1. **Screen: PartyMergeSearch**
   - Search form: name/email/phone (at least one must be provided — see Open Questions if backend allows empty searches)
   - Results list (table) with multi-select (exactly two)
   - Action button: “Continue to Merge” enabled only when exactly two selected
2. **Screen: PartyMergeConfirm**
   - Shows the two selected parties (summary fields)
   - Requires user to pick survivor vs source (radio selection)
   - Confirmation checkbox/text acknowledging irreversibility (UI-level; backend still enforces)
   - Action: “Merge Parties” (calls merge service)
3. **Screen: PartyMergeResult**
   - Success message
   - Displays:
     - survivorPartyId
     - sourcePartyId
     - mergeAuditId (if returned)
     - alias/redirect status (if returned)
   - Link to open survivor Party detail screen
   - Optional: “Test resolution” input for partyId if backend supports resolve endpoint

### Navigation context
- Breadcrumb: CRM > Parties > Merge Duplicates
- After successful merge: route to result screen; provide link back to search.

### User workflows
- **Happy path:** Search → select 2 → choose survivor → confirm → merge success → view survivor
- **Alternate path:** Search returns 0/1 results → show empty state guidance
- **Alternate path:** User selects >2 results → CTA disabled and helper message
- **Error path:** Backend validation error (e.g., cannot merge self, unauthorized, conflict) → show error banner and keep user on confirm screen

---

## 6. Functional Behavior

### Triggers
- User submits search form
- User selects rows in results table
- User clicks “Continue to Merge”
- User selects survivor on confirm screen
- User clicks “Merge Parties”

### UI actions
- **Search:**
  - On submit, call backend search service with filters (name/email/phone)
  - Render results; exclude merged parties by default (frontend sends `excludeMerged=true` if supported; otherwise relies on backend default)
- **Selection:**
  - Enforce exactly two selected items to proceed
  - Display inline guidance: “Select exactly two parties to merge”
- **Confirm:**
  - Present both parties; user selects survivor (the other becomes source)
  - Confirm action requires explicit acknowledgement (checkbox) to reduce accidental merges
- **Merge submission:**
  - Call backend merge service with `sourcePartyId`, `survivorPartyId`
  - On success, navigate to result screen and display returned details
  - On failure, remain on confirm screen and show error mapping (see Service Contracts)

### State changes (frontend)
- Local UI state only:
  - `searchCriteria`
  - `searchResults`
  - `selectedPartyIds[2]`
  - `survivorPartyId`
  - submission `loading/error` states

### Service interactions
- `party.search` (read)
- `party.merge` (write/transaction)
- Optional `party.resolveId` or `party.get` that honors alias/redirect

---

## 7. Business Rules (Translated to UI Behavior)

> CRM domain rules apply; frontend enforces UX guardrails but backend remains authoritative.

### Validation
- Search requires at least one criterion: name OR email OR phone (blocking if backend supports empty search/pagination).
- Selection must be exactly two distinct parties.
- Survivor and source must be different.
- Confirm checkbox must be checked before enabling “Merge Parties”.

### Enable/disable rules
- “Continue to Merge” disabled unless exactly two parties selected.
- “Merge Parties” disabled unless:
  - survivor selected
  - confirm acknowledged
  - not currently submitting

### Visibility rules
- Result screen shows merge audit/alias details only if provided by backend response.
- “Merged” parties should not appear in search results (frontend requests exclusion if supported).

### Error messaging expectations
- Self-merge: “Cannot merge a party with itself.”
- Unauthorized: “You don’t have permission to merge parties.”
- Conflict/concurrency: “This party record changed since you loaded it. Refresh and try again.”
- Generic failure: “Merge failed. No changes were applied.”

---

## 8. Data Requirements

### Entities involved (CRM domain)
- **Party** (read/select; post-merge status update happens server-side)
- **MergeAudit** (created server-side; displayed if returned)
- **PartyAlias** (created server-side if redirect is supported; displayed if returned)

### Fields required for UI display (minimum)
For each Party in search results and confirm:
- `partyId` (UUID, required)
- `partyType` (enum: PERSON / ORG if available; optional for this story)
- Display name:
  - Person: `firstName`, `lastName` (or a `fullName` field if backend provides)
  - Org: `organizationName` (if applicable)
- Primary contact points (if available in search payload):
  - `primaryEmail` (string)
  - `primaryPhone` (string)
- Status (to confirm non-merged; ideally `status` enum)

### Read-only vs editable
- All Party fields shown are read-only in this flow.
- Only user input is search filters and survivor selection.

### Derived/calculated
- “Selected count” derived in UI.
- Source party is derived as “the selected party that is not survivor”.

---

## 9. Service Contracts (Frontend Perspective)

> Exact service names/endpoints are not provided; Moqui implementation must bind to actual services once confirmed. This story is **blocked** until backend contract is known.

### Load/view calls
1. **Search Parties**
   - Purpose: find candidate duplicates
   - Proposed request params:
     - `name` (string, optional)
     - `email` (string, optional)
     - `phone` (string, optional)
     - `excludeStatus=MERGED` or `excludeMerged=true` (optional)
     - pagination: `pageIndex`, `pageSize` (optional; safe default)
   - Response:
     - list of Party summaries with fields listed in Data Requirements
     - pagination metadata (optional)

2. **(Optional) Resolve Party ID**
   - Purpose: verify alias/redirect behavior for merged IDs (for Admin confidence)
   - Request: `partyId`
   - Response: canonical Party record or a payload indicating redirected-to ID

### Create/update calls
- None (frontend does not edit Party attributes in this story)

### Submit/transition calls
1. **Merge Parties**
   - Request (required):
     - `sourcePartyId` (UUID)
     - `survivorPartyId` (UUID)
     - `reason` (string, optional) **only if backend requires**
   - Response (preferred):
     - `mergeAuditId` (UUID)
     - `sourcePartyId`
     - `survivorPartyId`
     - `aliasCreated` (boolean) or `redirectToPartyId`
     - `sourcePartyFinalStatus` (e.g., `MERGED`)

### Error handling expectations (UI mapping)
- `400` validation → show inline/banner with backend message; keep on same screen
- `403` forbidden → show permission error; disable merge action
- `404` party not found → inform user; offer to go back to search
- `409` conflict → prompt refresh and retry
- `500` → generic failure; do not assume partial changes

---

## 10. State Model & Transitions

### Allowed states (Party - backend-owned)
- `ACTIVE`
- `INACTIVE`
- `MERGED` (terminal for source)

### Role-based transitions
- Only Admin (or role with merge permission) can initiate merge action.

### UI behavior per state
- Parties with `status=MERGED` should be:
  - excluded from search results by default
  - if somehow shown (e.g., backend returns), display as non-selectable with helper text “Merged parties cannot be merged again” (backend must confirm rule; otherwise treat as Open Question)

---

## 11. Alternate / Error Flows

1. **Empty search results**
   - Show message: “No parties found. Try adjusting your search.”
2. **Only one result**
   - Same as above; cannot proceed to merge
3. **Selecting more/less than two**
   - CTA disabled; helper message displayed
4. **Self-merge attempt**
   - Prevented by UI selection rules; also handle backend `400` with message
5. **Backend rejects due to constraints**
   - Show backend-provided error; no navigation; keep selections
6. **Concurrency conflict (409)**
   - Show “Refresh results” action that re-runs the search and resets selection
7. **Unauthorized (403)**
   - Show error and hide/disable merge entry points

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Search returns candidate parties
**Given** I am authenticated as an Admin with permission to merge parties  
**When** I search parties by name "John Smith"  
**Then** I see a list of matching parties with their partyId and display name  
**And** I can select parties from the list for merging

### Scenario 2: Enforce selecting exactly two parties
**Given** I have performed a party search and results are displayed  
**When** I select 1 party  
**Then** the “Continue to Merge” action is disabled  
**And** I see guidance indicating I must select exactly 2 parties  
**When** I select 2 distinct parties  
**Then** the “Continue to Merge” action becomes enabled  
**When** I select 3 parties  
**Then** the “Continue to Merge” action is disabled

### Scenario 3: Confirm survivor and submit merge successfully
**Given** I have selected exactly two distinct parties A and B  
**When** I continue to the merge confirmation screen  
**Then** I can choose A as the survivor and B as the source  
**And** I must acknowledge the merge is permanent before submission  
**When** I submit the merge  
**Then** the system displays a success result including survivorPartyId and sourcePartyId  
**And** the result includes a merge audit reference if provided by the backend

### Scenario 4: Prevent merging a party with itself
**Given** I am on the merge confirmation screen  
**When** the survivorPartyId equals the sourcePartyId (via tampered URL or invalid state)  
**Then** the UI must block submission  
**And** if submission is attempted, the backend error is shown as “Cannot merge a party with itself.”

### Scenario 5: Merged parties excluded from search results
**Given** a party has been merged and is in status MERGED  
**When** I search using criteria that would match that party  
**Then** that merged party does not appear in results (default behavior)  

### Scenario 6: Backend conflict handling
**Given** I am on the merge confirmation screen with parties A and B selected  
**When** I submit the merge and the backend responds with a 409 Conflict  
**Then** I see an error telling me to refresh and try again  
**And** no success result is shown

---

## 13. Audit & Observability

### User-visible audit data
- On success, display:
  - `mergeAuditId` (if returned)
  - timestamp (if returned; otherwise omit)
  - survivor/source IDs

### Status history
- Not required to display full Party history in this story.
- If backend returns `sourcePartyFinalStatus`, display it.

### Traceability expectations
- Frontend logs (console/network) must not expose PII beyond what is shown in UI.
- For troubleshooting, include correlation/request ID in error UI if backend returns it (safe default only if present).

---

## 14. Non-Functional UI Requirements

- **Performance:** Search results should render efficiently for typical page sizes (safe default pageSize 25).
- **Accessibility:** All actions keyboard accessible; confirmation checkbox and survivor radio group have labels; error messages announced (ARIA live region via Quasar alert where applicable).
- **Responsiveness:** Works on tablet width; tables may scroll horizontally if needed.
- **i18n/timezone/currency:** Not applicable beyond basic string externalization if project already uses i18n.

---

## 15. Applied Safe Defaults

- **SD-UX-PAGINATION-01**: Default search results to paginated loading (pageSize=25) if backend supports it; qualifies as safe UI ergonomics without changing domain rules. (Impacted: UX Summary, Service Contracts, Error Flows)
- **SD-UX-EMPTY-STATE-01**: Provide explicit empty-state messaging and retry guidance on no results; safe because it affects presentation only. (Impacted: UX Summary, Alternate / Error Flows)
- **SD-ERR-MAP-01**: Map HTTP 400/403/404/409/500 to standard user-facing banners while preserving backend message text where safe; safe because it doesn’t alter business logic. (Impacted: Service Contracts, Alternate / Error Flows, Business Rules)

---

## 16. Open Questions

1. **Backend contract:** What are the exact Moqui service names/endpoints and response schemas for:
   - party search (filters, pagination, exclude merged)
   - merge (required fields, response payload incl. mergeAuditId / alias info)
2. **Search requirements:** Is it allowed to search with no criteria (browse) or must at least one of name/email/phone be provided?
3. **Alias/redirect behavior:** Is `PartyAlias` always created on merge, optional, or environment-configured? How should the UI reflect this when not supported?
4. **Party types:** Are we merging only Persons, only Orgs, or both? If both, are cross-type merges allowed?
5. **Authorization:** What role/scope gates the merge UI and merge service (e.g., `crm.party.merge`)? Should unauthorized users see the screen but be blocked, or not see entry points at all?
6. **Post-merge visibility:** Should the UI ever show MERGED parties in results (e.g., with a filter toggle), or always exclude?
7. **Conflict resolution policy:** For “basic” merge, does backend enforce “survivor wins” for primitive/conflicting attributes, and does UI need to warn about discarded data?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Party: Search and Merge Duplicate Parties (Basic) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/173


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Party: Search and Merge Duplicate Parties (Basic)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/173
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Search and Merge Duplicate Parties (Basic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Admin**, I want **to identify and merge obvious duplicate parties** so that **workorder selection remains clean and accurate**.

## Details
- Search by name/email/phone.
- Merge workflow: choose survivor, move relationships/vehicles/contacts, record merge audit.
- Optional alias/redirect record for merged IDs.

## Acceptance Criteria
- Can list possible duplicates.
- Can merge with an audit record.
- References remain resolvable after merge.

## Integration Points (Workorder Execution)
- Workorder Execution references must remain resolvable after merge (alias/redirect lookup).

## Data / Entities
- Party
- MergeAudit
- PartyAlias (optional)

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