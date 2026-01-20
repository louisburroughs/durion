## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:people
- status:draft

### Recommended
- agent:people-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

# 1. Story Header

### Title
[FRONTEND] [STORY] Dispatch: View Imported Mechanic Roster & Skills (HR-synced, Read-only)

### Primary Persona
Dispatch Manager (and Shop Manager) using the POS/Shop Management UI to select an assignable mechanic.

### Business Value
Ensures dispatch decisions use the authoritative HR roster (identity/status/home location/skills) and prevents assigning deactivated mechanics, reducing mis-assignments and compliance risk.

---

# 2. Story Intent

### As a / I want / So that
**As a** Dispatch Manager,  
**I want** to view the HR-synced mechanic roster including skills and assignment eligibility,  
**so that** I can confidently pick the right mechanic for work based on current HR status and qualifications.

### In-scope
- New/updated Moqui UI screens to **list and view** mechanics imported from HR (read model).
- UI clearly indicates **assignable vs not assignable** based on status (`ACTIVE` assignable; `INACTIVE` not assignable; `ON_LEAVE` shown as not assignable unless backend says otherwise—see Open Questions).
- UI supports searching/filtering roster (name, status, skill).
- UI displays skills snapshot (skill code + metadata if provided) per mechanic.
- UI is **read-only** (no local edits) to respect HR as System of Record.
- Basic frontend error/empty-state handling consistent with Moqui.

### Out-of-scope
- Triggering HR sync/import from UI (manual “Sync Now”) unless backend already exposes it (not provided).
- Editing mechanic profile, skills, or status in this UI.
- Implementing dispatch assignment workflows themselves (only roster visibility for selection support).
- Defining/implementing event ingestion or reconciliation jobs (backend responsibility).

---

# 3. Actors & Stakeholders
- **Dispatch Manager**: primary user consuming roster to choose mechanics.
- **Shop Manager**: secondary consumer for staffing visibility.
- **HR Administrator (external system)**: maintains roster/skills in HR; not a UI actor here.
- **System/Integration**: populates/updates the mechanic read model via events and/or reconciliation.
- **Security/RBAC**: controls access to roster screens (permission names TBD).

---

# 4. Preconditions & Dependencies
- Backend (Durion positivity backend) provides an API (or Moqui service facade) to:
  - query mechanic list with status and home location
  - view mechanic details including skill snapshot
- Backend enforces HR as SoR and upsert/idempotency rules; frontend only consumes read model.
- Authentication exists; user has permission to view roster.
- Data model includes `personId` (aka `hrPersonId`) as the external key and `status` enum.

Dependency reference: Backend story #72 (domain:people) describes `Mechanic`, `MechanicSkill`, and status semantics.

---

# 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Dispatch → Mechanics (Roster)** (exact menu placement may vary by repo conventions; see Open Questions if nav structure is unknown).
- Deep link:
  - List: `/dispatch/mechanics`
  - Detail: `/dispatch/mechanics/{personId}` (or `{mechanicId}` if that’s the stable UI key—see Open Questions)

### Screens to create/modify
1. **Screen:** `dispatch/MechanicRoster.xml` (list/search)
2. **Screen:** `dispatch/MechanicDetail.xml` (detail + skills)
3. Optional reusable widgets:
   - `component/MechanicStatusChip.xml` (status badge/assignable indicator)

### Navigation context
- From roster list, select a row → mechanic detail.
- From detail, back to list preserving filters (Moqui screen parameters).

### User workflows
**Happy path (view roster)**
1. User opens Mechanics (Roster)
2. System loads mechanics list
3. User filters by status/skill/search name
4. User opens a mechanic
5. System displays detail: identity, status, home location, skills list

**Alternate paths**
- No mechanics found → empty state with suggestions (clear filters)
- Mechanic exists but no skills → show “No skills recorded”
- Mechanic is inactive/on leave → show “Not assignable” state

---

# 6. Functional Behavior

### Triggers
- Screen load for list and detail routes.
- User applies filters/search; pagination changes.

### UI actions
**Roster list**
- Search by name (first/last or combined)
- Filter by `status` (multi-select)
- Filter by `skillCode` (single or multi—see Open Questions)
- Click mechanic row → navigate to detail

**Mechanic detail**
- View static fields and skills table/list
- No edit actions

### State changes
- None in domain data (read-only screens).
- UI state includes filter parameters, current page, sort.

### Service interactions (Moqui)
- Use Moqui `transition` with `service-call` (or `entity-find` if the frontend reads Moqui entities directly; depends on architecture—see Open Questions).
- List query service: fetch paged roster
- Detail query service: fetch a mechanic + skills by key

---

# 7. Business Rules (Translated to UI Behavior)

### Validation
- Route param must include mechanic identifier (personId or mechanicId).
- Reject/handle invalid identifiers:
  - If not found → show 404-style “Mechanic not found”
  - If malformed → show validation error and return to list

### Enable/disable rules
- “Assignable” indicator:
  - `status=ACTIVE` → assignable = true
  - `status=INACTIVE` → assignable = false
  - `status=ON_LEAVE` → display as non-assignable by default **only if backend provides an explicit `assignable` boolean; otherwise treat as “not assignable” requires clarification**.
- UI must not show “Edit” or “Deactivate” actions (HR SoR).

### Visibility rules
- If user lacks permission: do not render screen; show unauthorized error page.

### Error messaging expectations
- Network/service error → show non-technical message (“Unable to load mechanics. Try again.”) and log details.
- 403 → “You don’t have access to view mechanics roster.”
- 409/ordering/idempotency errors are backend-side and should not appear in UI for read-only flows; treat as generic load failure if received.

---

# 8. Data Requirements

### Entities involved (frontend view model)
From backend story (read model):
- `Mechanic`
- `MechanicSkill`

Frontend should expect fields (names may vary; map in service contract):

**Mechanic**
- `mechanicId` (UUID) *(optional for UI keying)*
- `personId` (string; required; external key, “HR Person ID”) *(required)*
- `firstName` (string; required)
- `lastName` (string; required)
- `status` enum: `ACTIVE | INACTIVE | ON_LEAVE` *(required)*
- `hireDate` (date; optional)
- `terminationDate` (date; optional)
- `homeLocationId` or `locationId` (string; optional unless required by dispatch)
- `lastSyncedAt` (timestamp; optional, display-only)
- `version` (int) or `lastEffectiveAt` (timestamp; optional, display-only)

**MechanicSkill**
- `skillCode` (string; required)
- `proficiencyLevel` (string/int; optional)
- `certifiedDate` (date; optional)
- `expirationDate` (date; optional)

### Required, defaults
- No frontend defaults for domain fields.
- Filters default:
  - status filter default = `ACTIVE` only (safe UX default; does not change data)

### Read-only vs editable
- All fields are read-only in UI.

### Derived/calculated fields
- `displayName` = `${firstName} ${lastName}` (UI-only)
- `assignable` derived from status unless backend provides explicit `assignable`

---

# 9. Service Contracts (Frontend Perspective)

> Note: Exact service names/endpoints are not provided; this section specifies required contracts. If the repo uses Moqui REST or service calls, implement accordingly.

### Load/view calls

1) **List mechanics (paged)**
- **Operation:** `GET /api/mechanics`
- **Query params:**
  - `q` (string; optional) name search
  - `status` (repeatable or comma-separated; optional)
  - `skillCode` (optional)
  - `locationId` (optional)
  - `page` (int; default 1)
  - `pageSize` (int; default 25)
  - `sort` (string; e.g., `lastName,firstName`)
- **Response 200:**
```json
{
  "data": [
    {
      "personId": "HR123",
      "mechanicId": "uuid-optional",
      "firstName": "Sam",
      "lastName": "Lee",
      "status": "ACTIVE",
      "homeLocationId": "LOC1",
      "lastSyncedAt": "2026-01-01T10:00:00Z"
    }
  ],
  "page": 1,
  "pageSize": 25,
  "total": 123
}
```

2) **Mechanic detail**
- **Operation:** `GET /api/mechanics/{personId}` (or `{mechanicId}`)
- **Response 200:**
```json
{
  "personId": "HR123",
  "firstName": "Sam",
  "lastName": "Lee",
  "status": "ACTIVE",
  "homeLocationId": "LOC1",
  "hireDate": "2023-05-01",
  "terminationDate": null,
  "version": 17,
  "lastSyncedAt": "2026-01-01T10:00:00Z",
  "skills": [
    { "skillCode": "BRAKES", "proficiencyLevel": "2", "certifiedDate": "2024-02-01", "expirationDate": null }
  ]
}
```

### Create/update calls
- None (read-only).

### Submit/transition calls
- None.

### Error handling expectations
- `401` → redirect to login per app convention.
- `403` → show unauthorized screen.
- `404` (detail) → show “Mechanic not found” with link back to roster.
- `5xx` → show generic load failure; allow retry.
- Frontend should surface backend `errorCode`/`message` if present but avoid showing raw stack traces.

---

# 10. State Model & Transitions

### Allowed states (Mechanic.status)
- `ACTIVE`
- `INACTIVE`
- `ON_LEAVE`

### Role-based transitions
- None in UI (no transitions or edits).

### UI behavior per state
- `ACTIVE`: shown as assignable
- `INACTIVE`: shown as not assignable; included only if filter includes INACTIVE
- `ON_LEAVE`: shown as not assignable (unless backend provides `assignable=true`—see Open Questions)

---

# 11. Alternate / Error Flows

### Validation failures
- Invalid route param → show error and redirect to roster with message.

### Concurrency conflicts
- Not applicable (read-only). If backend returns 409 unexpectedly, treat as load failure.

### Unauthorized access
- If user lacks permission:
  - list screen: show unauthorized
  - detail screen: show unauthorized (do not leak existence of personId)

### Empty states
- No results after filters → show empty list and “Clear filters” action.
- Mechanic has zero skills → show “No skills recorded from HR.”

---

# 12. Acceptance Criteria (Gherkin)

### Scenario 1: View active mechanic roster
Given I am an authenticated user with permission to view mechanics roster  
When I navigate to the Mechanics (Roster) screen  
Then I see a paginated list of mechanics including name, HR Person ID, status, and home location (if available)  
And by default only ACTIVE mechanics are shown

### Scenario 2: Filter mechanics by status
Given I am on the Mechanics (Roster) screen  
When I set the status filter to include INACTIVE  
Then the list includes mechanics with status INACTIVE  
And each INACTIVE mechanic is clearly indicated as not assignable

### Scenario 3: Search mechanics by name
Given I am on the Mechanics (Roster) screen  
When I search for "Lee"  
Then the list updates to show only mechanics whose name matches "Lee"  
And the current search term is preserved when I paginate

### Scenario 4: View mechanic detail including skills
Given I am on the Mechanics (Roster) screen  
When I open a mechanic detail page  
Then I see the mechanic’s name, HR Person ID, status, and last synced timestamp (if provided)  
And I see a list of skill tags/certifications (if any) with their attributes

### Scenario 5: Mechanic not found
Given I am an authenticated user with permission to view mechanics roster  
When I navigate to a mechanic detail URL for a non-existent identifier  
Then I see a “Mechanic not found” message  
And I am offered a link to return to the roster screen

### Scenario 6: Unauthorized access is blocked
Given I am authenticated but do not have permission to view mechanics roster  
When I navigate to the Mechanics (Roster) screen  
Then I see an unauthorized/access denied screen  
And no mechanic roster data is displayed

### Scenario 7: Service failure shows retryable error
Given I am on the Mechanics (Roster) screen  
When the mechanics list service returns a server error  
Then I see a non-technical error message  
And I can retry loading the list

---

# 13. Audit & Observability

### User-visible audit data
- Display-only fields when available:
  - `lastSyncedAt`
  - `version` or `lastEffectiveAt`

### Status history
- Not required unless backend exposes history endpoint (not provided). If backend provides it later, add a “Status/Sync History” panel in a separate story.

### Traceability expectations
- Frontend logs (console/app logging) should include correlation/request ID if provided in headers.
- UI should not expose raw payloads that include unnecessary PII.

---

# 14. Non-Functional UI Requirements
- **Performance:** list loads within 2s for first page under normal conditions; pagination should not refetch unchanged filters unnecessarily.
- **Accessibility:** roster table and filters keyboard navigable; status indicators include text (not color-only).
- **Responsiveness:** usable on tablet width; filters collapse into a drawer/stack as needed (implementation detail).
- **i18n/timezone:** timestamps rendered in user’s locale/timezone per app conventions; no currency.

---

# 15. Applied Safe Defaults
- **SD-UX-01 (Empty States):** Provide standard empty-state messaging and “Clear filters” action; safe because it affects only UI ergonomics. *(Impacted: UX Summary, Error Flows)*
- **SD-UX-02 (Pagination Defaults):** Default `pageSize=25` and preserve filters in query params; safe because it does not change domain behavior. *(Impacted: UX Summary, Service Contracts, Acceptance Criteria)*
- **SD-ERR-01 (Generic Load Error Handling):** Map 401/403/404/5xx to standard UI messages; safe because it follows HTTP semantics without altering policies. *(Impacted: Service Contracts, Error Flows, AC)*

---

# 16. Open Questions
1. What is the **actual Moqui integration pattern** in this repo for Vue/Quasar screens: does the frontend call **Moqui REST endpoints**, **Moqui services**, or a separate backend gateway? Provide existing example endpoints/service names to align implementation.
2. What is the **stable identifier** for routing: should mechanic detail route by `personId` (HR key) or `mechanicId` (local UUID)?
3. What are the **authorization permissions/roles** required to view the roster and detail screens (exact permission strings / role names)?
4. For `ON_LEAVE` mechanics: should they be treated as **not assignable**, or does dispatch allow assignment with warning? (Domain rule not explicitly stated for UI.)
5. Should roster filtering by skill support **multi-select** and should it match by **skillCode only** or also proficiency/certification status?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Dispatch: Import Mechanic Roster and Skills from HR — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/136


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Dispatch: Import Mechanic Roster and Skills from HR
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/136
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Import Mechanic Roster and Skills from HR

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want shopmgr to sync mechanic roster, roles, skills, and home location from durion-hr so that dispatch can assign the right mechanic.

## Details
- Ingest mechanic identities, roles, skill tags, location affiliations.
- Track active/assignable status.

## Acceptance Criteria
- Mechanics present with hrPersonId.
- Deactivated mechanics not assignable.
- Sync idempotent.

## Integrations
- HR→Shopmgr roster API or MechanicUpserted events.

## Data / Entities
- MechanicProfile, SkillTag, LocationAffiliation

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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