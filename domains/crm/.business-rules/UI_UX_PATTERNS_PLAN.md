# CRM UI/UX Patterns – Priority 4 Implementation Plan

**Version:** 1.0  
**Status:** DRAFT  
**Last Updated:** 2026-01-23  
**Audience:** Frontend developers, UX designers, product stakeholders

---

## Overview

This document outlines the work required to establish consistent UI/UX patterns for the CRM domain frontend. These patterns are prerequisites for implementing Issues #176, #172, #171, and #169 in the Vue 3/Moqui frontend.

---

## Table of Contents

1. [Canonical Navigation Patterns for Entity Views](#canonical-navigation-patterns-for-entity-views)
2. [Duplicate Detection UX Flow](#duplicate-detection-ux-flow)
3. [Optimistic Locking Conflict Resolution](#optimistic-locking-conflict-resolution)
4. [Implementation Roadmap](#implementation-roadmap)
5. [Acceptance Criteria](#acceptance-criteria)

---

## Canonical Navigation Patterns for Entity Views

### Purpose

Establish consistent, predictable navigation after entity creation/update and when viewing entities. This ensures users always know where to navigate next and can access related information.

### Work Items

#### 1.1: Define Party/Account View Route Pattern

**Context:** After creating a commercial account (Issue #176), users need to see the full party details.

**Decisions Required:**

- [A] **Route pattern:** 
  - Option A: `/crm/parties/{partyId}`
  - Option B: `/crm/accounts/{partyId}` (legacy term)
  - Option C: `/crm/parties/{partyId}/view` (explicit verb)
  - **Decision:** ________________

- [ ] **Canonical screen name in Moqui:**
  - Name/location of `PartyView` screen
  - **Decision:** ________________

- [ ] **Data displayed on party view:**
  - Legal name, display name, tax ID, party type, status
  - Billing terms, primary address
  - Creation/modification timestamps
  - Action buttons (Edit, Merge, Deactivate, etc.)
  - Tabs for related data (Contacts, Vehicles, Communication Preferences)
  - **Decision:** ________________

- [ ] **Edit pattern:**
  - Inline edit vs. modal vs. separate screen?
  - **Decision:** ________________

#### 1.2: Define Post-Create Navigation

**Context:** After `crm.createCommercialAccount` succeeds, where does the user go?

**Decisions Required:**

- [ ] **Navigation after party creation:**
  - Option A: Redirect to `/crm/parties/{partyId}` (full party view)
  - Option B: Show confirmation screen with next steps (Add contacts? Assign billing terms?)
  - Option C: Stay on create form with success banner, offer link to full view
  - **Decision:** ________________

- [ ] **Return value from service:**
  - Should `crm.createCommercialAccount` return `partyId` in response?
  - Should it include `viewUrl` to help frontend navigate?
  - **Decision:** ________________

#### 1.3: Define Contact View Pattern

**Context:** After loading contacts for a party, how are they displayed and edited?

**Decisions Required:**

- [ ] **Contact display:**
  - Inline table on party view with roles shown?
  - Separate `/crm/parties/{partyId}/contacts` tab/screen?
  - Modal edit per contact?
  - **Decision:** ________________

- [ ] **Contact edit pattern:**
  - Can users edit roles inline (click row → edit modal → save)?
  - Or only through separate contact form?
  - **Decision:** ________________

#### 1.4: Define Vehicle View Pattern

**Context:** After creating a vehicle (Issue #169), how are vehicles displayed and managed?

**Decisions Required:**

- [ ] **Vehicle display:**
  - Inline table on party view with VINs, nicknames?
  - Separate `/crm/parties/{partyId}/vehicles` screen?
  - **Decision:** ________________

- [ ] **Vehicle detail view:**
  - Route: `/crm/vehicles/{vehicleId}` or `/crm/parties/{partyId}/vehicles/{vehicleId}`?
  - **Decision:** ________________

- [ ] **Data displayed per vehicle:**
  - VIN, nickname, license plate (if available), description
  - Make/model/year (if VIN decoded via fitment service)
  - Creation/modification timestamps
  - **Decision:** ________________

#### 1.5: Breadcrumb & Back Navigation Strategy

**Decisions Required:**

- [ ] **Breadcrumb pattern:**
  - Home > CRM > Parties > {Party Name} > Contacts > {Contact Name}?
  - **Decision:** ________________

- [ ] **Back button behavior:**
  - Should "Back" go to previous route in browser history, or to parent entity view?
  - **Decision:** ________________

- [ ] **Unsaved changes warning:**
  - Warn user when leaving with unsaved form data?
  - **Decision:** ________________

### Deliverable

**File to Create:** `durion-moqui-frontend/runtime/component/durion-crm/docs/NAVIGATION_PATTERNS.md`

```markdown
# CRM Navigation Patterns

## Party/Account Views
- Create: POST /crm/parties → Redirect to /crm/parties/{partyId}
- View: GET /crm/parties/{partyId}
- Edit: PUT /crm/parties/{partyId}
- List: GET /crm/parties (search results)

## Contact Management
- View contacts: GET /crm/parties/{partyId}/contacts (tab on PartyView)
- Edit roles: PUT /crm/parties/{partyId}/contacts/{contactId}/roles
- Dialog: Modal for role assignment

## Vehicle Management
- Create: POST /crm/parties/{partyId}/vehicles
- View: GET /crm/parties/{partyId}/vehicles (tab on PartyView)
- Detail: GET /crm/vehicles/{vehicleId}
- Edit: PUT /crm/vehicles/{vehicleId}

[Include wireframes/screenshots here]
```

---

## Duplicate Detection UX Flow

### Purpose

When a user creates a party/vehicle and the backend detects duplicates, the frontend must present candidates in a way that lets users decide whether to merge, create anyway, or cancel.

### Context

- **Issue #176** mentions: "confirm the backend contract for duplicate detection"
- Backend returns `409 DUPLICATE_CANDIDATES` with a list of candidate matches
- Candidates include: `partyId`, `legalName`, `taxId`, `matchReason`

### Work Items

#### 2.1: Design Duplicate Candidate Modal/Screen

**UX Requirements:**

- [ ] **Modal Title & Message:**
  - "Possible Duplicate Found"
  - "We found existing parties that may match. Please review:"
  - **Decision:** ________________

- [ ] **Candidate Display:**
  - Show a table/list of candidates with:
    - Legal name, tax ID, party type, status
    - Match reason (e.g., "Exact legal name match", "Similar tax ID")
    - Created date, last modified date
    - Radio button or checkbox to select one
  - **Decision:** ________________

- [ ] **Action Buttons:**
  - "Merge with Selected" – Redirect to merge flow
  - "Create Anyway" – Proceed with new party creation
  - "Cancel" – Go back to form without creating
  - **Decision:** ________________

- [ ] **Safety guardrails:**
  - Display warning: "Merging will consolidate contacts, vehicles, and identifiers"
  - Require checkbox confirmation: "I understand this action is permanent"
  - **Decision:** ________________

#### 2.2: Merge Flow Integration

**Context:** When user clicks "Merge with Selected", redirect to merge confirmation.

**Decisions Required:**

- [ ] **Merge confirmation screen:**
  - Show "Winning party" (selected from candidates) and "Losing party" (new form data)
  - Require justification field: "Why are these duplicates?" (min 50 chars, max 500)
  - Preview which data will be preserved/merged
  - **Decision:** ________________

- [ ] **Merge success flow:**
  - After merge, redirect to: merged party view? Or search results?
  - Show success banner: "Parties merged. Redirecting to {PartyName}..."
  - **Decision:** ________________

#### 2.3: Error Handling for Duplicate Detection

**Scenarios:**

- [ ] **Backend duplicate check unavailable (timeout/503):**
  - Option A: Allow create anyway with warning banner
  - Option B: Block create with error "Duplicate check unavailable, please try again"
  - **Decision:** ________________

- [ ] **Unexpected duplicate check response:**
  - Log error, show user-friendly message: "Unexpected error during duplicate check"
  - Offer retry or "Create anyway" option
  - **Decision:** ________________

### Deliverable

**File to Create:** `durion-moqui-frontend/runtime/component/durion-crm/docs/DUPLICATE_DETECTION_UX.md`

```markdown
# Duplicate Detection UX Flow

## Create Party – Duplicate Detected Flow

1. User fills form and clicks "Create"
2. Frontend calls `crm.createCommercialAccount`
3. Backend returns 409 with DUPLICATE_CANDIDATES
4. Frontend shows modal:
   ```
   ┌─────────────────────────────────┐
   │ Possible Duplicate Found        │
   ├─────────────────────────────────┤
   │ We found existing parties that  │
   │ may match. Please review:       │
   │                                 │
   │ ○ Acme Corp (Exact name match)  │
   │ ○ Acme Inc (Similar tax ID)     │
   │                                 │
   │ [Merge] [Create Anyway] [Cancel]│
   └─────────────────────────────────┘
   ```
5. User selects option and proceeds

## Merge Confirmation

[Wireframe for merge confirmation screen]

[Detailed flow diagram]
```

---

## Optimistic Locking Conflict Resolution

### Purpose

When two users edit the same entity (e.g., communication preferences) concurrently, the second save fails with 409 Conflict. The frontend must provide a clear resolution path.

### Context

- **Issue #171** asks: "Is optimistic locking required? If yes, what field is used (`version`, `lastUpdatedStamp`, ETag)?"
- Backend will return 409 with current version info
- User needs to see what changed and decide whether to retry, reload, or discard changes

### Work Items

#### 3.1: Determine Optimistic Locking Field

**Decisions Required (coordinate with Backend):**

- [ ] **Version field name:**
  - Option A: `version` (simple number: 1, 2, 3)
  - Option B: `lastUpdatedStamp` (timestamp)
  - Option C: `eTag` (hash of content)
  - **Decision:** ________________

- [ ] **Where version is stored:**
  - On communication preferences entity?
  - On all CRM entities or only specific ones?
  - **Decision:** ________________

- [ ] **Backend 409 response format:**
  ```json
  {
    "errorCode": "OPTIMISTIC_LOCK_CONFLICT",
    "message": "This record was updated by another user",
    "currentVersion": "v3",
    "submittedVersion": "v2",
    "currentData": { ... },
    "conflictingChanges": [ ... ]
  }
  ```
  - **Confirm with backend** ________________

#### 3.2: Design Conflict Resolution Modal

**UX Requirements:**

- [ ] **Modal Title & Message:**
  - "Save Conflict"
  - "This record was updated by another user since you started editing. Your changes cannot be saved."
  - **Decision:** ________________

- [ ] **Display conflict details:**
  - Show who updated (user name, timestamp)
  - Show what changed (field-by-field comparison)
  - Show current values vs. user's changes
  - **Example:**
    ```
    Field              | Current Value  | Your Change
    ─────────────────────────────────────────────────
    emailPreference    | OPT_OUT        | OPT_IN
    smsPreference      | OPT_IN         | OPT_OUT
    ```
  - **Decision:** ________________

- [ ] **Resolution Options:**
  - "Reload & Retry" – Fetch latest, merge changes where possible, user re-enters edits
  - "Discard My Changes" – Reload from server, lose all edits
  - "Override & Save" – Force save (only if user has EDIT_PRIVILEGED permission?) ⚠️
  - "Cancel" – Close modal, stay in form with edits intact
  - **Decision on which options to include:** ________________

#### 3.3: Implement Smart Merge Logic (Optional)

**Advanced Option:**

- [ ] **Auto-merge non-conflicting changes:**
  - If user edited `emailPreference` and backend updated `smsPreference`
  - Attempt auto-merge: keep both changes
  - Show banner: "Merged with server changes, please verify"
  - Offer to save again
  - **Include this?** ________________

#### 3.4: Handle Special Cases

**Scenarios:**

- [ ] **Conflict on delete:**
  - User tries to delete entity, backend says it was already deleted
  - Message: "This record no longer exists. Redirecting..."
  - **Decision:** ________________

- [ ] **Conflict with version gone missing:**
  - User submits v2, but server only has v5 (gap in version history)
  - Likely indicates data corruption or concurrency issue
  - Show: "Unexpected version mismatch. Please contact support."
  - **Decision:** ________________

### Deliverable

**File to Create:** `durion-moqui-frontend/runtime/component/durion-crm/docs/OPTIMISTIC_LOCKING_UX.md`

```markdown
# Optimistic Locking Conflict Resolution

## Version Field Strategy

- Field name: `version` (incremental integer)
- Included in all upsert requests: `{ partyId, emailPreference, version }`
- Backend increments on each update

## Conflict Flow

[Wireframe for conflict resolution modal]

## Merge Strategy

When 409 Conflict is returned:
1. Parse current version and conflicting fields
2. Attempt intelligent merge of non-overlapping fields
3. Show comparison UI to user
4. Offer "Reload & Retry" or "Discard"

[Detailed examples and code patterns]
```

---

## Implementation Roadmap

### Phase 1: Navigation Patterns (Week 1-2)

**Goal:** Establish canonical routes and screen layout for entity views.

**Tasks:**

1. [ ] Meet with design/PM to finalize route patterns (1.1–1.5)
2. [ ] Create `NAVIGATION_PATTERNS.md` with decisions
3. [ ] Create Moqui screen stubs: `PartyView.xml`, `ContactsTab.xml`, `VehiclesTab.xml`
4. [ ] Implement Vue 3 components: `PartyDetail.vue`, `ContactList.vue`, `VehicleList.vue`
5. [ ] Add router configuration to `durion-moqui-frontend/runtime/component/durion-crm/`
6. [ ] Test basic navigation flow end-to-end

**Deliverable:** Functional party/contact/vehicle views with working navigation

---

### Phase 2: Duplicate Detection UX (Week 3-4)

**Goal:** Implement duplicate detection modal and merge flow.

**Tasks:**

1. [ ] Finalize backend duplicate detection contract (Issue #176 answer)
2. [ ] Create `DUPLICATE_DETECTION_UX.md` with decisions
3. [ ] Implement `DuplicateCandidatesModal.vue` component
4. [ ] Implement merge confirmation screen
5. [ ] Wire up error handling (timeout, 409 response)
6. [ ] Add duplicate detection to `crm.createCommercialAccount` service wrapper
7. [ ] Test with backend returning 409 DUPLICATE_CANDIDATES

**Deliverable:** Working duplicate detection and merge flow for party creation

---

### Phase 3: Optimistic Locking (Week 5-6)

**Goal:** Implement conflict detection and resolution for concurrent edits.

**Prerequisites:**

- [ ] Backend confirms version field strategy
- [ ] Backend implements optimistic locking on communication preferences

**Tasks:**

1. [ ] Finalize optimistic locking field with backend
2. [ ] Create `OPTIMISTIC_LOCKING_UX.md` with decisions
3. [ ] Implement `ConflictResolutionModal.vue` component
4. [ ] Add version tracking to communication preferences form
5. [ ] Add 409 error handling in `crm.upsertCommunicationPreferences` service wrapper
6. [ ] Implement smart merge logic (Phase 3.1 optional)
7. [ ] Test concurrent edits scenario

**Deliverable:** Working conflict resolution UI for concurrent communication preference edits

---

## Acceptance Criteria

### Navigation Patterns (Phase 1)

- [ ] All entity views are accessible via canonical routes (party, contact, vehicle)
- [ ] Users can navigate from entity creation → entity view → related entities
- [ ] Breadcrumbs correctly show navigation path
- [ ] Back button returns to previous screen
- [ ] Unsaved changes warning appears before navigation away
- [ ] Post-create redirect works for all creation endpoints

### Duplicate Detection UX (Phase 2)

- [ ] When backend returns 409 DUPLICATE_CANDIDATES, modal displays candidates with match reasons
- [ ] User can select a candidate and merge
- [ ] User can create anyway (proceed despite duplicates)
- [ ] Merge redirects to merged party view with success banner
- [ ] Duplicate detection timeout gracefully falls back to "Create Anyway"
- [ ] All error scenarios (timeout, 503, invalid response) are handled

### Optimistic Locking UX (Phase 3)

- [ ] When user submits form, version field is included in request
- [ ] On 409 Conflict response, modal shows current vs. submitted values
- [ ] "Reload & Retry" fetches latest and pre-fills form (user can merge manually)
- [ ] "Discard" reloads from server and clears form
- [ ] "Cancel" closes modal and preserves user edits
- [ ] Manual merge scenario works: user edits different fields, save succeeds
- [ ] Delete scenario: appropriate message when entity was deleted server-side

---

## Related Issues

- Issue #176: Party creation (duplicate detection)
- Issue #173: Party merge (navigation, merge flow)
- Issue #172: Contact roles (navigation, inline edit)
- Issue #171: Communication preferences (optimistic locking)
- Issue #169: Vehicle creation (navigation, post-create redirect)

---

## Documentation Artifacts to Create

1. ✅ `NAVIGATION_PATTERNS.md` – Canonical routes, entity view layouts, breadcrumbs
2. ✅ `DUPLICATE_DETECTION_UX.md` – Modal design, merge flow, error handling
3. ✅ `OPTIMISTIC_LOCKING_UX.md` – Version strategy, conflict modal, merge logic

---

## Next Steps

1. **Decisions:** Schedule design/PM review to finalize decisions in sections 1.1–1.5, 2.1–2.3, 3.1–3.4
2. **Documentation:** Create decision records in the three markdown files above
3. **Implementation:** Begin Phase 1 (navigation patterns) once decisions are finalized
4. **Coordination:** Confirm backend has optimistic locking implementation (Phase 3 dependency)

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial plan; three phases (Navigation, Duplicate Detection, Optimistic Locking) |
