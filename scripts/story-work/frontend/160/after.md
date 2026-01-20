STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** pricing-strict

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)  
**Primary Persona:** Account Manager (and/or Pricing Analyst)  
**Business Value:** Ensure promotions are only applied to intended customers/vehicles by configuring eligibility criteria and providing explainable eligibility decisions during estimate pricing.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Account Manager / Pricing Analyst  
- **I want** to configure eligibility rules for a promotion based on account and vehicle attributes (including optional fleet-size threshold)  
- **So that** the pricing engine can correctly apply or deny promotions with an explanation (reason code) during estimate pricing.

### In-scope
- Promotion eligibility rule configuration UI (create, edit, delete, list) attached to a specific promotion.
- Eligibility rule “test/evaluate” UI that calls an evaluation service and displays decision + reason.
- Frontend wiring for Moqui screens, forms, transitions, and service calls.
- Validation and error handling aligned to business rules (fail-safe deny, explicit reason codes).

### Out-of-scope
- Creating/editing promotions themselves (assumed already exists and navigable).
- Implementing pricing calculation, applying discounts, tax/total math (owned by pricing backend/services).
- Defining vehicle tags taxonomy and account fleet size computation (owned by Inventory/CRM systems of record).
- Advanced promotion stacking/precedence (explicitly out of scope; only eligibility rules).

---

## 3. Actors & Stakeholders

- **Primary user:** Account Manager / Pricing Analyst (configures rules; runs evaluation test)
- **Secondary user:** Service Advisor (consumes accurate outcomes indirectly during estimate pricing)
- **System actor:** Pricing Engine / Estimate Pricing flow (calls eligibility evaluation)
- **External domains (SoR):**
  - **CRM**: account data incl. fleet size
  - **Inventory**: vehicle data incl. tags/categories
- **Compliance/Audit stakeholder:** Finance/Operations (needs traceability for why promo applied/denied)

---

## 4. Preconditions & Dependencies

- User is authenticated in the frontend and has permission to manage promotions eligibility (permission token/authorization handled by Moqui security).
- A **Promotion** exists and has a stable identifier (`promotionId`).
- Backend services exist (or will exist) for:
  - CRUD of `PromotionEligibilityRule`
  - Eligibility evaluation given `promotionId` and context (`accountId`, `vehicleId` optional)
- UI has a navigation path to a promotion detail/config screen.

**Dependency risks:** service endpoints/parameters are not defined in provided inputs; must be clarified (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Promotion detail screen: action “Eligibility Rules”
- Optional: Promotions list → select promotion → Eligibility Rules

### Screens to create/modify
1. **Screen:** `PromotionDetail` (existing; modify)
   - Add navigation link/tab to `PromotionEligibilityRules`
2. **Screen:** `PromotionEligibilityRules` (new)
   - Lists rules for `promotionId`
   - Actions: Add Rule, Edit Rule, Delete Rule, Test Eligibility
3. **Screen/Dialog:** `PromotionEligibilityRuleEdit` (new, could be embedded dialog form)
   - Create/update form with validation and operator/value inputs
4. **Screen/Dialog:** `PromotionEligibilityTest` (new, could be section in rules screen)
   - Input: accountId, vehicleId (optional depending on rules), run evaluation, show decision + reason

### Navigation context
- All screens scoped by `promotionId` in the path or parameters.
- Breadcrumbs: Promotions → Promotion {code/name} → Eligibility Rules

### User workflows
**Happy path: configure and test**
1. Open promotion → Eligibility Rules
2. Click “Add Rule”
3. Choose condition type, operator, value
4. Save → rule appears in list
5. Open “Test Eligibility”, enter context, run test
6. See result: Eligible/Ineligible + reason code

**Alternate paths**
- Edit existing rule and re-test.
- Delete rule (with confirm) and verify list updates.
- Attempt save with invalid inputs → inline validation errors.

---

## 6. Functional Behavior

### Triggers
- Screen load for `PromotionEligibilityRules` with `promotionId`
- User submits Create/Update/Delete rule
- User submits Evaluate/Test eligibility

### UI actions
- **List view**
  - Fetch rules for promotion
  - Display each rule condition type/operator/value
  - Provide per-row actions: Edit, Delete
- **Create/Edit**
  - Form fields update based on `conditionType` (e.g., fleet size expects numeric)
  - Save triggers backend create/update service
- **Delete**
  - Confirmation modal; on confirm, call delete service; refresh list
- **Test/Evaluate**
  - Call evaluation service with `promotionId` + context
  - Display `isEligible` and `reasonCode` returned

### State changes
- Rule records created/updated/deleted in backend.
- No local state persisted beyond screen session.

### Service interactions
- `find` rules by promotionId
- `create` rule
- `update` rule
- `delete` rule
- `evaluate` eligibility

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Required fields:** `promotionId`, `conditionType`, `operator`, `value`
- `value` validation by condition type:
  - `ACCOUNT_ID_LIST`: must be a non-empty list of identifiers (format TBD); UI should accept comma-separated input and trim whitespace.
  - `VEHICLE_TAG`: must be non-empty string token (no commas unless explicitly allowed—TBD).
  - `ACCOUNT_FLEET_SIZE`: must be an integer or numeric threshold (exact numeric type TBD); reject non-numeric.
- `operator` must be compatible with `conditionType` (matrix TBD; must be clarified).

### Enable/disable rules
- Disable “Save” until form passes client-side validation.
- Disable “Test” while evaluation call in-flight.

### Visibility rules
- Show/hide context fields on Test panel:
  - If any configured rule requires account context, show/require `accountId`.
  - If any rule requires vehicle context, show/require `vehicleId`.
  - If both exist, require both.
  - If rules list is empty, Test is allowed but expected result behavior must be clarified (eligible vs ineligible).

### Error messaging expectations
- Display backend validation errors inline at field level when possible; otherwise show a top-of-form error summary.
- For evaluation failures due to missing context, show a specific message including `reasonCode` (e.g., “Missing account context”).
- For dependent service unavailability during evaluation: show “Eligibility evaluation unavailable; promotion treated as not eligible” (wording must align to backend reason code; TBD).

---

## 8. Data Requirements

### Entities involved
- `Promotion` (read-only in this story; used for context)
- `PromotionEligibilityRule` (CRUD)
- `EligibilityDecision` (evaluation response)

### Fields (type, required, defaults)

**PromotionEligibilityRule**
- `ruleId` (UUID, required for existing; generated on create)
- `promotionId` (UUID, required; read-only in form once scoped)
- `conditionType` (ENUM, required): `ACCOUNT_ID_LIST`, `VEHICLE_TAG`, `ACCOUNT_FLEET_SIZE`
- `operator` (ENUM, required): **TBD exact allowed values per condition type**
- `value` (string, required): serialized value (comma list / token / numeric string)

### Read-only vs editable
- `ruleId`: read-only
- `promotionId`: read-only (inferred from route)
- Others editable when user has manage permission

### Derived/calculated fields
- None in UI; evaluation returns derived decision (`isEligible`, `reasonCode`)

---

## 9. Service Contracts (Frontend Perspective)

> Backend endpoints are not specified in provided inputs; below is a Moqui-frontend contract expectation that MUST be mapped to actual services once confirmed.

### Load/view calls
- **List rules**
  - Input: `promotionId`
  - Output: array of `PromotionEligibilityRule`
  - Errors:
    - 401/403 → route to unauthorized screen or show permission error
    - 404 promotion not found → show not found

### Create/update calls
- **Create rule**
  - Input: `promotionId`, `conditionType`, `operator`, `value`
  - Output: created `PromotionEligibilityRule` (including `ruleId`)
- **Update rule**
  - Input: `ruleId` + editable fields
  - Output: updated rule

### Delete call
- **Delete rule**
  - Input: `ruleId`
  - Output: success boolean/empty

### Submit/transition calls
- None (no lifecycle states specified for rules)

### Error handling expectations
- Map field validation errors to form fields when backend returns structured validation.
- On unknown server errors: show generic failure and allow retry; do not assume rule saved.

---

## 10. State Model & Transitions

### Allowed states
- No explicit state machine for `PromotionEligibilityRule` provided.

### Role-based transitions
- If user lacks permission:
  - Read-only access (if allowed) or block entirely (TBD)
  - Create/Edit/Delete buttons hidden or disabled with tooltip “Insufficient permissions”

### UI behavior per state
- N/A beyond permissions and loading/error states (loading spinner, empty state message)

---

## 11. Alternate / Error Flows

- **Empty state (no rules configured):**
  - Show message “No eligibility rules configured”
  - Provide CTA “Add Rule”
  - Test behavior with no rules: **TBD** (Open Question)
- **Validation failures on save:**
  - Highlight invalid fields; preserve user input; no navigation
- **Concurrency conflicts:**
  - If update/delete fails due to missing rule (already deleted), refresh list and show “Rule no longer exists”
- **Unauthorized access (401/403):**
  - Show access denied; do not display sensitive data
- **Evaluation service unavailable:**
  - Show evaluation unavailable; do not infer eligibility; if backend returns fail-safe `isEligible=false` with reason, display it

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View eligibility rules for a promotion
**Given** I am an authenticated Account Manager with permission to manage promotion eligibility  
**And** a promotion exists with `promotionId`  
**When** I navigate to the promotion’s “Eligibility Rules” screen  
**Then** the system loads and displays the list of `PromotionEligibilityRule` records for that `promotionId`  
**And** if no rules exist, an empty state is shown with an “Add Rule” action

### Scenario 2: Create an account ID list eligibility rule
**Given** I am on the “Eligibility Rules” screen for a promotion  
**When** I click “Add Rule”  
**And** I select `conditionType = ACCOUNT_ID_LIST`  
**And** I select a valid operator for account list matching  
**And** I enter a non-empty list of account IDs in `value`  
**And** I click “Save”  
**Then** the rule is persisted via a create service call  
**And** the newly created rule appears in the rules list

### Scenario 3: Prevent saving invalid fleet size rule
**Given** I am adding an eligibility rule  
**When** I select `conditionType = ACCOUNT_FLEET_SIZE`  
**And** I enter a non-numeric `value`  
**And** I click “Save”  
**Then** the UI blocks submission and displays a validation error indicating fleet size must be numeric  
**And** no create/update service call is made

### Scenario 4: Edit an existing rule
**Given** at least one eligibility rule exists for the promotion  
**When** I click “Edit” on a rule  
**And** I change the operator and/or value to valid inputs  
**And** I click “Save”  
**Then** the UI calls the update service  
**And** the list refreshes showing the updated rule values

### Scenario 5: Delete an existing rule with confirmation
**Given** an eligibility rule exists in the list  
**When** I click “Delete”  
**And** I confirm deletion  
**Then** the UI calls the delete service  
**And** the rule is removed from the list  
**And** a success message is shown

### Scenario 6: Test eligibility and display decision + reason
**Given** a promotion has one or more eligibility rules configured  
**When** I open the “Test Eligibility” panel  
**And** I provide the required context inputs (accountId and/or vehicleId as required by configured rules)  
**And** I click “Run Test”  
**Then** the UI calls the eligibility evaluation service with `promotionId` and the provided context  
**And** the UI displays the returned `isEligible` result  
**And** the UI displays the returned `reasonCode`

### Scenario 7: Evaluation fails due to missing required context
**Given** a promotion has eligibility rules that require `accountId`  
**When** I attempt to run the eligibility test without providing `accountId`  
**Then** the UI blocks the call and shows a “missing account context” error  
**Or** if backend is called, the UI displays the backend `reasonCode` indicating missing context (exact behavior depends on backend contract)

---

## 13. Audit & Observability

### User-visible audit data
- Display `ruleId` (or a short form) in the rule list details view (useful for support).
- If backend provides `createdBy/createdDate/lastUpdatedBy/lastUpdatedDate`, display on edit/view (TBD availability).

### Status history
- Not applicable unless backend provides history; do not invent.

### Traceability expectations
- Frontend should include `promotionId` and `ruleId` in structured logs for create/update/delete actions (console/network logs as per project convention).
- Eligibility test results should be traceable (promotionId, input context, decision, reasonCode) in UI event logging if project convention supports it.

---

## 14. Non-Functional UI Requirements

- **Performance:** Rules list should render within 1s after data load for typical rule counts (assume < 100).
- **Accessibility:** All form controls labeled; errors announced; keyboard navigable dialogs.
- **Responsiveness:** Works on tablet width; forms usable without horizontal scroll.
- **i18n/timezone/currency:** Not applicable (no monetary/currency display). Reason codes displayed as provided; optionally map to human-readable strings if a mapping exists (TBD).

---

## 15. Applied Safe Defaults

- SD-UI-EMPTY-STATE: Provide a standard empty-state with CTA when no rules exist; safe because it doesn’t alter business logic. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UI-INFLIGHT-DISABLE: Disable submit buttons during in-flight service calls to prevent duplicate submissions; safe ergonomic default. (Impacted: Functional Behavior, Alternate/Error Flows)
- SD-UI-PAGINATION-LOCAL: If rules list exceeds a reasonable threshold, use standard table pagination defaults; safe because it doesn’t affect data. (Impacted: UX Summary)

---

## 16. Open Questions

1. **Service contracts:** What are the exact Moqui service names/endpoints for:
   - list rules by `promotionId`
   - create/update/delete rule
   - evaluate eligibility (`promotionId`, `accountId`, `vehicleId`) and return shape (`isEligible`, `reasonCode`, optional explanation text)?
2. **Rule combination logic:** When multiple rules exist for a promotion, is eligibility evaluated with **AND** (all must pass) or **OR** (any pass)? Is this configurable per promotion?
3. **Operator matrix:** Which operators are valid per `conditionType` (and their semantics)? E.g., does `ACCOUNT_ID_LIST` support `IN` only, also `NOT_IN`? Does `VEHICLE_TAG` support `EQUALS` only?
4. **Identifier formats:** What is the canonical format for `accountId` and `vehicleId` in UI input (UUID vs human code)? Should the UI provide lookup/search selectors instead of free-text?
5. **No-rules behavior:** If a promotion has **zero** eligibility rules, is it considered eligible for all contexts, or ineligible until rules are defined?
6. **Reason codes catalog:** What is the authoritative initial enum set for `reasonCode` and should the UI map them to user-friendly messages?
7. **Permissions:** What permission(s) should gate view vs manage (create/edit/delete) eligibility rules in the Moqui frontend?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/160  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Account Manager**, I want **to restrict offers to certain accounts or vehicle categories** so that **promotions are applied correctly**.

## Details
- Eligibility: specific accounts, simple tags (e.g., trailer/tractor), optional fleet-size threshold.
- Return eligibility decision with reason.

## Acceptance Criteria
- Configure eligibility.
- Evaluate eligibility with explanation.

## Integration Points (Workorder Execution)
- Workorder Execution calls eligibility evaluation during estimate pricing.

## Data / Entities
- PromotionEligibilityRule

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