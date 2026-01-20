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
[FRONTEND] [STORY] Accounting: Manage Posting Categories, Mapping Keys, and Effective-Dated GL Mappings

### Primary Persona
Financial Controller / Accountant

### Business Value
Enable consistent, deterministic classification of producer financial events into Posting Categories and effective-dated GL mappings (account + dimensions), with auditability and validation, so journal entry generation can be automated reliably.

---

## 2. Story Intent

### As a / I want / So that
**As a** Financial Controller,  
**I want** to create and maintain Posting Categories, producer-facing Mapping Keys, and effective-dated mappings from categories to GL accounts (and required dimensions),  
**so that** producer systems can deterministically resolve posting instructions for a transaction date and finance can audit configuration history.

### In-scope
- Moqui frontend screens to:
  - List/view/create/update/deactivate Posting Categories
  - List/view/create/update/deactivate Mapping Keys and link them deterministically to a Posting Category
  - Create new effective-dated GL mappings for a Posting Category (no overlapping ranges)
  - View mapping history for a category (effective ranges + audit metadata)
  - Validate and surface API errors (overlap conflicts, missing GL account, deactivated category, etc.)
- Read-only resolution test utility (optional UI) to validate a mapping key resolves for a given transaction date **if** backend provides an endpoint.

### Out-of-scope
- Defining or editing Chart of Accounts itself (assumed managed elsewhere; this story only selects from existing GL accounts)
- Defining Posting Rule Sets, journal entry generation, or posting to ledger
- Tax rules, revenue recognition, or any debit/credit mapping logic
- Automatic “close previous mapping end date” behavior unless explicitly confirmed (see Open Questions)

---

## 3. Actors & Stakeholders
- **Financial Controller / Accountant**: configures categories/keys/mappings, reviews history, resolves validation errors.
- **Auditor (read-only)**: reviews configuration history and audit fields.
- **Producer System Integrator (read-only)**: verifies mapping keys and resolution behavior (if UI utility exists).
- **System (Moqui backend services)**: enforces invariants (uniqueness, effective dating, non-overlap, immutability/versioning policy).

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui application.
- Authorization exists for managing posting configuration (exact permission names unclear; see Open Questions).
- Backend provides services/endpoints for:
  - Posting Category CRUD + deactivate
  - Mapping Key CRUD + link to Posting Category
  - GL Mapping create (new version) + list/history by category
  - Chart of Accounts lookup (list GL accounts for selection and validate existence)
  - (Optional) mapping resolution by key + date for producers/test utility
- Backend enforces audit logging and immutability/versioning rules; frontend must not assume it can “hard update” records if backend requires create-new-version semantics.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Accounting → Configuration → Posting Setup**
  - Tabs or sub-screens:
    - **Posting Categories**
    - **Mapping Keys**
    - **GL Mappings**

### Screens to create/modify
Create a screen tree under something like:
- `apps/frontend/screen/Accounting/PostingConfig.xml` (container)
  - `.../PostingCategoryList.xml`
  - `.../PostingCategoryDetail.xml`
  - `.../MappingKeyList.xml`
  - `.../MappingKeyDetail.xml`
  - `.../GlMappingList.xml` (scoped to category)
  - `.../GlMappingCreate.xml` (scoped to category)
  - (Optional) `.../ResolutionTest.xml`

> Exact filenames/paths may differ by repo conventions; implement using existing menu/screen patterns in `durion-moqui-frontend`.

### Navigation context
- From **Posting Categories list** → open **Category detail**:
  - view category fields + status
  - see linked mapping keys
  - see GL mapping history (effective ranges)
  - actions: edit (if allowed), deactivate, create new GL mapping

- From **Mapping Keys list** → open **Key detail**:
  - view/edit key fields
  - link/unlink to a Posting Category (deterministic 1:1)
  - status and audit metadata

### User workflows

#### Happy path: create category + mapping key + GL mapping
1. Create Posting Category (code + description) → saved ACTIVE.
2. Create Mapping Key (unique key string) → associate to category.
3. Add GL Mapping for category: select GL account + optional dimensions, set effective start (and optional end).
4. Verify mapping appears in history and is ACTIVE for date.

#### Alternate path: new mapping version for same category
1. On category detail, choose “New Mapping Version”.
2. Enter new effective start date (and optional end).
3. Save; backend rejects if overlap. UI surfaces conflict details.

#### Alternate path: deactivate category
1. Deactivate Posting Category.
2. UI must prevent creating new GL mappings for INACTIVE category; if attempted, backend error surfaced.

---

## 6. Functional Behavior

### Triggers
- User navigates to Posting Setup screens.
- User submits create/update/deactivate forms.
- User selects a category to view mappings and history.

### UI actions
- **List screens**: filter/search, open detail, create new.
- **Detail screens**: edit allowed fields (depending on immutability policy), deactivate, view history.
- **GL mapping create**: choose category, select GL account, set effective dates, set dimensions.

### State changes (frontend perspective)
- Category status: `ACTIVE` → `INACTIVE` (deactivate only; no delete)
- Mapping key status: (if modeled) active/inactive; otherwise existence + association changes
- GL mapping: creation of a new mapping record/version; prior mapping remains for history

### Service interactions
- On screen load: call load/list services for categories, keys, mappings, and GL accounts.
- On submit: call create/update/deactivate services; handle validation and conflicts.
- For overlap conflicts: handle `409 Conflict` with payload identifying conflicting mapping(s) (exact format TBD; see Open Questions).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Posting Category:
  - `categoryCode` required, unique, uppercase/underscore format preferred (format rule not specified; do **not** enforce unless confirmed).
  - `description` required (or optional—unclear; see Open Questions).
- Mapping Key:
  - Unique system-wide (required).
  - Must resolve deterministically to exactly one Posting Category.
- GL Mapping:
  - `postingCategoryId` required.
  - `glAccountId` required and must exist in CoA.
  - `effectiveStartDate` required.
  - `effectiveEndDate` optional; if provided must be >= start date.
  - No overlapping effective ranges for same Posting Category.
  - Cannot create new mappings for INACTIVE category.

### Enable/disable rules
- “Deactivate Category” disabled if already INACTIVE.
- “Add/New GL Mapping” disabled when category INACTIVE.
- If backend enforces immutability-by-version:
  - “Edit” on existing GL mapping is disabled; only “Create new mapping version” is available.

### Visibility rules
- Show mapping history list for a category, including effective dates, account, dimensions, status, createdBy/createdAt.
- If backend returns conflict details on overlap, display them inline under the effective date fields.

### Error messaging expectations
Frontend must map backend errors to actionable messages:
- Uniqueness violation → “Code/Key already exists.”
- Overlap conflict (409) → “Effective dates overlap with existing mapping <id> (<start>–<end>).”
- GL account not found (400) → “Selected GL account no longer exists; refresh and try again.”
- Unauthorized (401/403) → show access denied and hide write actions.

---

## 8. Data Requirements

> Field names below reflect story intent; actual entity/field names must match backend and Moqui entities/services.

### Entities involved (conceptual)
- `PostingCategory`
- `MappingKey` (or equivalent; backend reference conflates with category code—needs clarification)
- `GlMapping`
- `GlAccount` (Chart of Accounts reference entity)
- `AuditLog` / audit fields on each entity

### Fields

#### PostingCategory
- `postingCategoryId` (id/UUID) — read-only
- `categoryCode` (string) — required, unique, editable? (unclear if immutable)
- `description` (string/text) — required? (unclear)
- `statusId` or `status` (`ACTIVE`/`INACTIVE`) — read-only except via deactivate action
- `createdAt`, `createdBy`, `updatedAt`, `updatedBy` — read-only

#### MappingKey
- `mappingKeyId` — read-only
- `mappingKey` (string) — required, unique
- `postingCategoryId` (FK) — required (enforces deterministic 1:1)
- `status` (if present) — active/inactive
- audit fields — read-only

#### GlMapping
- `glMappingId` — read-only
- `postingCategoryId` — required (selected/scoped)
- `glAccountId` — required (selected from CoA)
- `dimensions` — object/fields (unknown list; see Open Questions)
- `effectiveStartDate` (date) — required
- `effectiveEndDate` (date nullable) — optional
- `supersededByMappingId` (nullable) — read-only
- audit fields — read-only

### Read-only vs editable by state/role
- Users without manage permission: all screens read-only; cannot see create/edit/deactivate actions.
- Existing mappings: editable only if backend supports updates; otherwise read-only and new-version only (needs clarification).

### Derived/calculated fields
- “Active for date” indicator (computed in UI by comparing date range) for display only; not authoritative.

---

## 9. Service Contracts (Frontend Perspective)

> Service names are placeholders; implement using Moqui `service-call` to existing services defined in backend component. If not present, raise mismatch.

### Load/view calls
- `PostingCategoryFind` (list with filters: code contains, status)
- `PostingCategoryGet` (by id)
- `MappingKeyFind`
- `MappingKeyGet`
- `GlMappingFindByCategory` (history list)
- `GlAccountFind` (for selection; supports search by code/name)

### Create/update calls
- `PostingCategoryCreate`
- `PostingCategoryUpdate` **or** `PostingCategoryVersionCreate` (immutability policy unclear)
- `PostingCategoryDeactivate`
- `MappingKeyCreate`
- `MappingKeyUpdate` / `Deactivate`
- `GlMappingCreate` (new mapping record/version)

### Submit/transition calls
- Deactivation actions treated as explicit services: `...Deactivate`

### Error handling expectations
- Validation errors: `400` with field error details; bind to form fields.
- Conflict overlap: `409` with conflicting mapping identifiers and dates (payload format TBD).
- Not found: `404` when opening details for deleted/nonexistent entities.
- Unauthorized: `401/403` should route to login or show forbidden.

---

## 10. State Model & Transitions

### Allowed states
- Posting Category: `ACTIVE`, `INACTIVE`
- Mapping Key: (if modeled) `ACTIVE`, `INACTIVE`
- GL Mapping: no explicit state; effective dating defines activity. May also have superseded linkage.

### Role-based transitions
- Financial Controller with permission:
  - Category: create, update (if allowed), deactivate
  - Mapping Key: create, update (if allowed), deactivate
  - GL Mapping: create new version
- Read-only roles:
  - view only

### UI behavior per state
- INACTIVE category:
  - cannot add new GL mappings or new mapping keys associated to it (enforced in UI + backend)
  - history remains visible
- Effective date not covering “today”:
  - show “No active mapping for today” informational banner on category detail (non-blocking)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields → inline field errors, prevent submit.
- End date earlier than start date → inline error, prevent submit (client-side) and also handle backend response.

### Concurrency conflicts
- If backend uses optimistic locking (e.g., `version` field), on save conflict show: “This record was changed by another user. Reload to continue.” Provide Reload action.

### Unauthorized access
- User without permission attempting to access create/edit URL directly:
  - screen renders read-only and/or returns forbidden; must not leak privileged actions.

### Empty states
- No categories: show empty state with “Create Posting Category” action if authorized.
- Category with no GL mappings: show “No mappings yet” with “Add Mapping” action if authorized.
- No GL accounts returned: show error “No GL accounts available; check CoA setup.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create posting category
**Given** I am logged in as a Financial Controller with permission to manage posting configuration  
**When** I create a Posting Category with code `REVENUE_PARTS` and description `Revenue from Parts Sales`  
**Then** the category is saved with status `ACTIVE`  
**And** the category appears in the Posting Categories list

### Scenario 2: Prevent duplicate posting category codes
**Given** a Posting Category with code `REVENUE_PARTS` already exists  
**When** I attempt to create another Posting Category with code `REVENUE_PARTS`  
**Then** I see an error indicating the code must be unique  
**And** the duplicate category is not created

### Scenario 3: Create mapping key linked to a category
**Given** a Posting Category `REVENUE_PARTS` exists  
**When** I create a Mapping Key `pos.sale.item.parts.standard` linked to `REVENUE_PARTS`  
**Then** the mapping key is saved  
**And** the key is visible on the category detail screen

### Scenario 4: Prevent duplicate mapping keys
**Given** a Mapping Key `pos.sale.item.parts.standard` already exists  
**When** I attempt to create another Mapping Key with the same value  
**Then** I see an error indicating the mapping key must be unique  
**And** the duplicate key is not created

### Scenario 5: Create an effective-dated GL mapping
**Given** a Posting Category `REVENUE_PARTS` exists and is `ACTIVE`  
**And** a GL Account `40010` exists in the Chart of Accounts  
**When** I create a GL Mapping for `REVENUE_PARTS` to GL Account `40010` with effective start date `2026-02-01` and no end date  
**Then** the mapping is saved  
**And** it appears in the mapping history for `REVENUE_PARTS`

### Scenario 6: Reject overlapping effective dates
**Given** a GL Mapping exists for category `REVENUE_PARTS` effective `2026-02-01` through (no end date)  
**When** I attempt to create another GL Mapping for `REVENUE_PARTS` effective `2026-01-15` through `2026-03-01`  
**Then** the system rejects the save with a conflict error  
**And** I see a message identifying the conflicting mapping and overlapping date range

### Scenario 7: Block new mappings for inactive category
**Given** a Posting Category `REVENUE_PARTS` is `INACTIVE`  
**When** I view the category detail screen  
**Then** the “Add/New GL Mapping” action is disabled or hidden  
**And** if I attempt to submit a new mapping anyway  
**Then** I see an error indicating mappings cannot be created for inactive categories

### Scenario 8: Handle missing GL account
**Given** I am creating a GL Mapping and select a GL account that is no longer valid  
**When** I submit the mapping  
**Then** I see an error indicating the GL account is invalid or not found  
**And** the mapping is not created

---

## 13. Audit & Observability

### User-visible audit data
- On detail screens show:
  - createdBy / createdAt
  - updatedBy / updatedAt (if applicable)
- For GL mapping history show createdAt/createdBy per mapping record.

### Status history
- For categories and mapping keys: show status changes if backend exposes audit events; otherwise show current status and audit fields.

### Traceability expectations
- Each created mapping/version must be traceable by ID and effective date range.
- If backend supports it, include links from a mapping record to audit event entries.

---

## 14. Non-Functional UI Requirements
- **Performance**: list screens should load within 2s for up to 500 records; use pagination.
- **Accessibility**: all form controls labeled; keyboard navigable; error messages announced (aria-live) via Quasar patterns.
- **Responsiveness**: usable on tablet widths; forms stack vertically.
- **i18n/timezone/currency**:
  - Dates displayed in user locale/timezone; effective dates are date-only (no time).
  - Currency not directly displayed/edited in this story.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty state components with primary CTA when lists are empty; qualifies as safe because it changes no domain behavior; impacts UX Summary, Alternate/Empty states.
- SD-UX-PAGINATION: Paginate list screens with default page size (e.g., 25) and server-side filtering where supported; safe because it only affects presentation/perf; impacts UX Summary, Service Contracts.
- SD-ERR-STANDARD-MAPPING: Map HTTP 400/401/403/404/409 to standard inline/toast handling consistent with app conventions; safe because it follows backend signals; impacts Service Contracts, Error Flows.

---

## 16. Open Questions
1. **Mapping Key vs Category Code Modeling**: Are “Mapping Keys” a separate entity (many keys → one posting category), or is the producer key exactly the `categoryCode` (1:1) as suggested in backend reference note? Frontend screen design depends on this.
2. **Immutability/Versioning Rules in UI**: Are Posting Categories and Mapping Keys editable in-place, or must edits create new versioned records (append-only)? Same question for GL mappings (create-new-version only vs allow updates before superseded).
3. **Overlap Policy**: When a new mapping is created with a start date inside an existing open-ended mapping, should the system (a) reject with 409 always, or (b) automatically end-date the previous mapping? UI flow differs.
4. **Dimensions Definition**: What is the authoritative list of required/optional financial dimensions for GL mappings (e.g., departmentId, locationId, costCenterId, businessUnitId)? Are they freeform strings, references to entities, or fixed enums?
5. **Authorization**: What are the exact permissions/scopes for managing posting categories/keys/mappings, and should auditors have read-only access by default?
6. **Resolution Endpoint**: Is there a supported backend endpoint for “resolve mapping key + transaction date → category + GL account + dimensions”? If yes, provide request/response and error codes so a small “Resolution Test” UI can be included.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Categories: Define Posting Categories and Mapping Keys  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/203  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Categories: Define Posting Categories and Mapping Keys

**Domain**: general

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
Categories: Define Posting Categories and Mapping Keys

## Acceptance Criteria
- [ ] Posting categories exist for business meaning (Labor Revenue, Sales Tax Payable, COGS Tires, etc.)
- [ ] Mapping keys used by producers resolve deterministically to categories
- [ ] Category→Account/Dimensions mappings are effective-dated and audit-logged
- [ ] Invalid/overlapping mappings are rejected per policy


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