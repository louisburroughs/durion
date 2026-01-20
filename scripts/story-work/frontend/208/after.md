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

# 1. Story Header

## Title
[FRONTEND] [STORY] Accounting Events: View & Validate Canonical Accounting Event Envelope (Contract UI)

## Primary Persona
- **Domain Architect / Backend Engineer (Accounting)** maintaining the canonical event envelope contract and verifying conformance.

## Business Value
Provide an implementation-ready, Moqui-native UI surface to **view and validate** the Canonical Accounting Event Envelope contract details in the system so producers/consumers can align on required fields, data types, and traceability requirements without ambiguity—reducing integration defects and audit gaps.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Domain Architect (Accounting),
- **I want** a frontend screen to view the Canonical Accounting Event Envelope specification (fields, constraints, examples, schema version),
- **So that** cross-module teams can reliably implement and validate event payloads against a single, discoverable contract.

## In-scope
- Moqui screen(s) that present the canonical envelope requirements:
  - Required envelope fields and meaning
  - Expected types/format constraints
  - Line and tax array structures
  - Traceability context examples (invoiceId, workOrderId, etc.)
  - Schema version string and governance note
- Optional “validate payload” utility UI that accepts a JSON payload and runs validation against the schema **if** a backend validation service exists.

## Out-of-scope
- Defining the schema itself (contract definition is a backend/repo artifact)
- Publishing schema to external Git repo or schema registry (governance process)
- Producing or ingesting events from other modules
- Any debit/credit posting mappings, GL account logic, tax policy definition

---

# 3. Actors & Stakeholders
- **Accounting Domain Architect**: owns contract semantics and required fields.
- **Producer team engineers** (Order/Billing/Workexec/Inventory): need contract clarity for producing events.
- **Accounting service engineers**: need contract clarity for consumption/validation.
- **Auditors / Compliance (read-only)**: need traceability expectations documented and discoverable.

---

# 4. Preconditions & Dependencies
- Moqui frontend module is running and can render new screens under an Accounting navigation context.
- A source of truth for the canonical envelope exists (one of):
  1) backend endpoint exposing schema/version + field definitions, or  
  2) schema file accessible to frontend build/runtime, or  
  3) stored schema record in Moqui entities.
- Authentication/authorization exists for accessing Accounting configuration/contract screens.

**Dependency (blocking):** backend contract for retrieving schema/spec and (optional) validating payloads is not defined in provided inputs.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Main navigation: **Accounting → Integrations → Event Contracts → Canonical Accounting Event Envelope**
- Direct route: `/accounting/events/contract` (proposed; final route depends on existing screen tree)

## Screens to create/modify
- **New Screen:** `component://durion/screen/accounting/events/Contract.xml` (name illustrative)
  - Subscreens:
    - `Summary` (version, purpose, required envelope keys)
    - `EnvelopeFields` (table of fields)
    - `LineItemSchema` (lines[] structure)
    - `TaxItemSchema` (tax[] structure)
    - `TraceabilityContext` (common keys)
    - `ValidatePayload` (optional utility)
- **Modify Navigation Screen** to add menu link (where Accounting nav is defined).

## Navigation context
- Within Accounting domain screens; breadcrumb shows Accounting > Integrations > Event Contracts > Canonical Envelope.

## User workflows
### Happy path: view contract
1) User opens Canonical Envelope screen.
2) Screen loads schema metadata (schemaVersion, lastUpdated, source link).
3) User reviews required fields and nested structures.
4) User copies example JSON for producers.

### Alternate path: validate a payload (if supported)
1) User opens Validate tab.
2) Pastes JSON payload.
3) Submits.
4) UI shows validation result: pass/fail with error list (field paths + codes).

---

# 6. Functional Behavior

## Triggers
- Screen load triggers retrieval of contract/spec data.
- “Validate” action triggers backend validation (if provided).

## UI actions
- View schema version and required/optional fields.
- Expand/collapse sections for `lines[]`, `tax[]`, and `traceabilityContext`.
- Copy-to-clipboard for example payloads (UI ergonomics).
- Validate JSON payload (optional, gated by backend availability).

## State changes
- No domain state machine changes (read-only contract display).
- If validation utility exists, store only transient UI state (textarea content, last result). No persistence unless explicitly specified (not in scope).

## Service interactions
- `loadContractSpec` (required): fetch contract metadata + field definitions.
- `validateContractPayload` (optional): validate user-provided payload against schema.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- When validating payload:
  - JSON must parse; otherwise show inline error “Invalid JSON”.
  - If backend returns schema validation errors, render them as a list with:
    - `errorCode`
    - `jsonPath` (e.g., `$.lines[0].totalAmount`)
    - human message
- Display must include the required envelope fields (from story inputs):
  - `eventId`, `eventType`, `sourceModule`, `sourceEntityRef`, `occurredAt`, `businessUnitId`, `currencyUomId`, `lines[]`, `tax[]`, `metadata`, `schemaVersion`
  - Traceability fields exist (e.g., `workOrderId`, `invoiceId`, `poId`, `receiptId`) — shown under `traceabilityContext`.

## Enable/disable rules
- Validate button disabled until textarea contains non-empty content.
- Tabs/sections always available if spec loaded; show error state if spec load fails.

## Visibility rules
- Screen is visible only to authorized users (permission TBD → Open Question).
- Validation utility tab visible only if backend supports validation endpoint (feature-flagged by service response or config).

## Error messaging expectations
- Load failure: show non-technical summary + correlation/requestId if provided.
- Unauthorized: show standard “Not authorized” screen and do not render spec content.

---

# 8. Data Requirements

## Entities involved
**Unclear** from provided inputs. Options:
- None (spec served by backend as static JSON)
- `AccountingEventSchema` entity (versioned schema records)
- `Artifact`/`Content` entity storing schema JSON and metadata

This must be confirmed (Open Questions).

## Fields (type, required, defaults)
For display purposes, the UI must support representing at least:

### Contract metadata
- `schemaVersion` (string, SemVer) — required
- `schemaFormat` (string, e.g., `json-schema-2020-12`) — optional
- `publishedAt` (datetime, UTC) — optional
- `sourceUri` (string URL) — optional but recommended

### Envelope field definitions (display model)
- `fieldName` (string) — required
- `required` (boolean) — required
- `type` (string) — required
- `format` (string) — optional
- `description` (string) — required
- `example` (string/number/object) — optional

### LineItem structure (minimum per input + backend ref)
- `lineType` (enum: `PARTS`, `LABOR`, `FEES`, `DISCOUNT`) — required
- `description` (string) — optional/required unclear
- `quantity` (decimal) — required
- `unitPrice` (integer, smallest currency unit) — required
- `totalAmount` (integer, smallest currency unit) — required (discounts negative)

### TaxItem structure
- `taxType` (string) — required
- `taxRate` (decimal) — optional/required unclear
- `taxableAmount` (integer) — required
- `taxAmount` (integer) — required

### Traceability context
- Key/value object with optional keys like `workOrderId`, `invoiceId`, `poId`, `receiptId` (strings)

## Read-only vs editable by state/role
- Entire screen is read-only.
- Validation input is editable by user; not persisted.

## Derived/calculated fields
- None. (If backend returns “required fields count” etc., UI may show derived counts as presentation only.)

---

# 9. Service Contracts (Frontend Perspective)

> Moqui screens will call services via standard `service-call` / REST as per project conventions (exact mechanism depends on repo patterns; not provided here).

## Load/view calls (required)
### `AccountingEventContract.getCanonicalEnvelopeSpec` (name TBD)
**Request**
- none, or optional `schemaVersion` to view a specific version

**Response (minimum)**
- `schemaVersion`
- `fields[]` with the envelope required fields listed above
- `lineItemSchema`
- `taxItemSchema`
- `traceabilityExamples`
- optional `sourceUri`

**Error handling**
- 401/403: render unauthorized
- 404: show “Contract not found”
- 5xx: show load failure with retry

## Create/update calls
- none (out-of-scope)

## Submit/transition calls (optional)
### `AccountingEventContract.validatePayload`
**Request**
- `payloadJson` (string) OR parsed object (TBD)
- optional `schemaVersion`

**Response**
- `valid` (boolean)
- `errors[]` where each error has: `errorCode`, `jsonPath`, `message`

**Error handling**
- 400 invalid JSON or schema mismatch → render errors inline
- 413 payload too large (if enforced) → show “Payload too large”

**Blocking:** these services/endpoints are not defined in provided inputs.

---

# 10. State Model & Transitions
- No business entity state transitions in this story.
- UI states:
  - `loading`
  - `loaded`
  - `loadFailed`
  - `unauthorized`
  - (optional) `validationIdle`, `validationRunning`, `validationFailed`, `validationPassed`

---

# 11. Alternate / Error Flows

## Validation failures
- Invalid JSON in textarea → client-side parse error; do not call backend.
- Backend validation returns errors → list them; keep payload in textarea for correction.

## Concurrency conflicts
- Not applicable (read-only).

## Unauthorized access
- If user lacks permission, show standard unauthorized screen; no partial spec display.

## Empty states
- If `fields[]` is empty (unexpected), show “No fields defined for this contract” and prompt to check backend publication.

---

# 12. Acceptance Criteria

### Scenario 1: View canonical envelope contract successfully
**Given** an authorized user navigates to Accounting → Integrations → Event Contracts → Canonical Accounting Event Envelope  
**When** the screen loads  
**Then** the UI displays the contract `schemaVersion`  
**And** lists the required envelope fields: `eventId`, `eventType`, `sourceModule`, `sourceEntityRef`, `occurredAt`, `businessUnitId`, `currencyUomId`, `lines`, `tax`, `metadata`, `schemaVersion`  
**And** shows the nested structure requirements for `lines[]` and `tax[]`  
**And** shows traceability context examples including at least `workOrderId` and `invoiceId`.

### Scenario 2: Contract load failure
**Given** an authorized user navigates to the Canonical Envelope screen  
**When** the contract spec service returns a server error  
**Then** the UI shows a load failure message  
**And** provides a retry action  
**And** does not display partial or stale contract data.

### Scenario 3: Unauthorized access
**Given** a user without required permission attempts to access the Canonical Envelope screen  
**When** the screen loads  
**Then** the UI renders an unauthorized/access-denied state  
**And** does not reveal the contract field list.

### Scenario 4 (optional): Validate a conforming payload
**Given** the validation capability is enabled  
**And** the user opens the Validate tab  
**And** pastes a JSON payload containing all required envelope fields including non-empty `lines[]` and `tax[]` arrays  
**When** the user clicks Validate  
**Then** the UI shows a “valid” result  
**And** no validation errors are displayed.

### Scenario 5 (optional): Validate a non-conforming payload
**Given** the validation capability is enabled  
**And** the user opens the Validate tab  
**And** pastes a JSON payload missing `eventId`  
**When** the user clicks Validate  
**Then** the UI shows an “invalid” result  
**And** displays an error with a path indicating the missing field (e.g., `$.eventId`) and a stable `errorCode`.

### Scenario 6: Client-side invalid JSON
**Given** the user opens the Validate tab  
**When** the user enters malformed JSON and clicks Validate  
**Then** the UI shows “Invalid JSON”  
**And** does not call the backend validation service.

---

# 13. Audit & Observability

## User-visible audit data
- Display `schemaVersion` and (if provided) `publishedAt` and `sourceUri` for traceability.

## Status history
- Not applicable unless backend provides historical versions. If versions exist, UI should allow selecting version (Open Question).

## Traceability expectations
- All service calls should log/trace with identifiers:
  - `schemaVersion` (if part of request)
  - request/correlation id (from standard headers)
- UI should surface correlation id on error if provided in response headers/body.

---

# 14. Non-Functional UI Requirements
- **Performance:** Contract screen should load within 2s under normal conditions (excluding backend outages).
- **Accessibility:** Keyboard navigable tabs/sections; sufficient contrast; aria labels for copy buttons.
- **Responsiveness:** Works on tablet width (POS admin usage).
- **i18n/timezone/currency:** Display `occurredAt` requirement explicitly as UTC; no currency formatting assumptions beyond “smallest unit integer” note (do not calculate).

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging when the contract spec returns no fields; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows.
- SD-UX-RETRY: Provide retry button on load failure; qualifies as safe error recovery; impacts Error Flows, Acceptance Criteria.
- SD-OBS-CORRELATION-ID: Display correlation/request id in error state when provided; qualifies as safe observability boilerplate; impacts Audit & Observability, Error Flows.

---

# 16. Open Questions
1) **Backend source of truth:** Where will the frontend retrieve the canonical envelope spec from (endpoint vs stored entity vs bundled schema file)? Provide the exact contract (URL/service name, request/response).  
2) **Permissions:** What permission(s)/role(s) gate access to the contract screen (e.g., `accounting.contract.view`)?  
3) **Schema versions:** Should the UI support selecting historical schema versions, or only show the latest? If versions exist, what is the list endpoint/contract?  
4) **Validation utility:** Is a backend payload validation endpoint planned/available? If yes, provide contract and error code taxonomy.  
5) **Field constraints:** Confirm required formats: is `eventId` UUIDv7 (per accounting checklist) vs UUIDv4 (backend reference text shows v4 example); and are monetary values always integers in smallest unit?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Events: Define Canonical Accounting Event Envelope  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/208  
Labels: frontend, story-implementation, order

## Frontend Implementation for Story

**Original Story**: [STORY] Events: Define Canonical Accounting Event Envelope

**Domain**: order

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Ingest Accounting Events (Cross-Module)

## Story
Events: Define Canonical Accounting Event Envelope

## Acceptance Criteria
- [ ] Event envelope includes eventId, eventType, sourceModule, sourceEntityRef, occurredAt, businessUnitId, currencyUomId, lines[], tax[], metadata, schemaVersion
- [ ] Supports multi-line totals (parts/labor/fees/discount/tax)
- [ ] Schema is versioned and published as a contract
- [ ] Traceability fields exist (workorderId, invoiceId, poId, receiptId, etc. where applicable)

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