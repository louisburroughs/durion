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
[FRONTEND] [STORY] Accounting: Ingest WorkCompleted Event (Ops UI for Monitoring, Replay, and Workorder Invoice-Eligibility)

### Primary Persona
Accounting Operations User (Back-office accountant / accounting ops) who monitors accounting event ingestion and resolves failures.

### Business Value
Ensure `WorkCompleted` events are ingested reliably and are traceable/auditable so WIP finalization (if enabled) and workorder invoice-eligibility are correctly applied, reducing billing delays and preventing duplicate/invalid financial processing.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Accounting Operations User  
- **I want** a UI to view the status of ingested `WorkCompleted` events (including validation errors, duplicates, and processing outcomes) and to safely retry/replay failed events  
- **So that** completed workorders become invoice-eligible on time and ingestion problems can be triaged without engineering intervention.

### In-scope
- Moqui screens to:
  - List and filter `WorkCompleted` ingestion records.
  - View a single ingestion record details (payload envelope, validation results, processing outcome).
  - Trigger an operator-initiated **retry/reprocess** action for eligible failed records (subject to permissions).
- UI handling of backend error codes and status mapping (validation/duplicate/not-found/WIP failure).
- Audit/observability display fields (processed timestamps, correlation/event IDs, outcome reason).

### Out-of-scope
- Implementing the actual ingestion processor/business logic (backend-owned).
- Defining GL account mappings, journal entry lines, or posting semantics in the UI.
- Building/altering the canonical event schema (contract owned elsewhere).
- Creating AR, invoices, or revenue recognition flows.

---

## 3. Actors & Stakeholders

- **Accounting Operations User (Primary human actor):** monitors ingestion, investigates failures, initiates retries.
- **System (Accounting Ingestion Service):** produces ingestion status records and exposes APIs consumed by UI.
- **Work Execution domain/system:** upstream producer of `WorkCompleted` events (context only).
- **Billing domain/system:** downstream consumer of “invoice-eligible” status (context only).
- **Audit/Compliance stakeholder:** needs traceability (who retried what, when, why).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend and has access to Accounting module navigation.
- Backend exposes endpoints/services to:
  - Search ingestion records by filters.
  - Retrieve ingestion record details by ID.
  - Trigger retry/reprocess (operator action) for a specific ingestion record.

### Dependencies (Blocking if absent)
- The **backend contract** for ingestion records and retry is not defined in the provided inputs (only a backend story reference exists and is itself blocked). Frontend cannot be finalized without:
  - Entity/DTO field names
  - Status enums
  - Error code taxonomy
  - Retry eligibility rules
  - Authorization permission(s)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main app nav: **Accounting → Event Ingestion → WorkCompleted** (or **Accounting → Events → Ingestion** if that’s the established pattern).

### Screens to create/modify
1. **Screen:** `apps/pos/accounting/events/WorkCompletedIngestionList.xml`
   - Search/list view for ingestion records.
2. **Screen:** `apps/pos/accounting/events/WorkCompletedIngestionDetail.xml`
   - Detail view for a single ingestion record with actions (retry) and payload inspection.

> Note: Exact screen path must match repo conventions; adjust to the project’s existing screen root and menu structure once confirmed.

### Navigation context
- From list → click row → detail screen
- From detail → back to list preserving prior filters (via URL parameters or session-saved search)

### User workflows
#### Happy path (monitoring)
1. User opens list screen.
2. Filters by date range and status = `FAILED` (or equivalent).
3. Opens a record; reviews reason and payload.
4. If eligible, clicks **Retry**.
5. UI shows confirmation, submits retry, shows updated status (e.g., `RETRY_QUEUED` / `PROCESSING` / `SUCCEEDED`).

#### Alternate paths
- View `DUPLICATE` records (idempotency) and understand they are not retried.
- View `WORKORDER_NOT_FOUND` failures and optionally retry after upstream data issue is resolved.
- View `VALIDATION_ERROR` and see it is not retryable (unless policy allows manual override—currently unknown).

---

## 6. Functional Behavior

### Triggers
- User navigates to list screen.
- User selects a record to view details.
- User initiates retry action.

### UI actions
- **List Screen**
  - Filter controls:
    - `status` (single-select or multi-select)
    - `occurredAt` date/time range
    - `workorderId` (text)
    - `eventId` (text)
    - `sourceModule` (text)
  - Table columns (minimum):
    - occurredAt
    - eventId
    - workorderId
    - status
    - reasonCode / errorCode (if any)
    - processedAt (if any)
  - Row click navigates to detail.

- **Detail Screen**
  - Summary panel:
    - eventId, eventType, schemaVersion, occurredAt
    - workorderId, businessUnitId, currencyUomId
    - processingStatus, processedAt, attemptCount
    - lastErrorCode, lastErrorMessage (sanitized)
  - Payload viewer:
    - read-only formatted JSON (collapsed by default)
  - Actions:
    - **Retry/Reprocess** button visible only when record is retry-eligible and user has permission.
    - Confirmation dialog requiring “Reason for retry” (if required by audit policy—currently unclear).

### State changes (frontend-observed)
- After retry submission, record status should transition to a queued/processing status or immediately re-evaluated. UI must refresh the record.

### Service interactions
- `searchIngestionRecords` (list)
- `getIngestionRecord` (detail)
- `retryIngestionRecord` (action)
- Optional: `getConfig` for WIP flag display (read-only), if exposed.

---

## 7. Business Rules (Translated to UI Behavior)

> Business rules provided are primarily backend semantics; UI must reflect them safely without inventing accounting policies.

### Validation
- Filters:
  - Date range: `from <= to`; show inline validation error.
  - `eventId` format: if UUID required, validate format client-side (optional) and server-side (authoritative).
- Retry action:
  - Must require confirmation.
  - Must block action while request in-flight to prevent double-submit.
  - Must show backend error codes and messages if retry rejected.

### Enable/disable rules
- Retry button enabled only if:
  - Record status is in a retryable terminal state (e.g., `FAILED_RETRYABLE`) **(needs clarification)**.
  - User has required permission (e.g., `accounting:events:retry`) **(needs clarification)**.

### Visibility rules
- Show WIP-related processing outcome fields only if present in record (do not assume WIP enabled/disabled unless explicitly provided).
- Do not expose sensitive/internal stack traces; display sanitized error message.

### Error messaging expectations
Map backend errors to user-facing messages (do not guess missing codes; see Open Questions). At minimum:
- Validation failure: “Event payload failed validation; cannot be processed.”
- Not found: “Workorder referenced by event was not found.”
- Duplicate conflict: “Duplicate event ID detected; already processed or conflicting payload.”
- WIP reconciliation failure: “WIP reconciliation failed; retry may be available.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
Because authoritative entity names are not provided, define placeholders to be replaced with actual Moqui entities/services:

- `AccountingEventIngestion` (or similar): ingestion record for canonical accounting events.
- `AccountingEventIngestionAttempt` (optional): per-attempt history.
- Related reference data:
  - Workorder reference (read-only link by `workorderId`), if a screen exists.

### Fields (type, required, defaults)
**Ingestion Record (minimum set)**
- `ingestionId` (string/UUID, required, read-only)
- `eventId` (UUID, required, read-only)
- `eventType` (string, required, read-only) — expected `WorkCompleted`
- `schemaVersion` (string, required, read-only)
- `sourceModule` (string, required, read-only)
- `sourceEntityRef` (string/object, required?, read-only) *(unclear)*
- `occurredAt` (datetime, required, read-only)
- `receivedAt` (datetime, required, read-only)
- `businessUnitId` (string/UUID, required, read-only)
- `currencyUomId` (string, required, read-only)
- `workorderId` (UUID, required, read-only)
- `processingStatus` (enum/string, required, read-only)
- `processedAt` (datetime, optional, read-only)
- `attemptCount` (number, required, read-only; default 0)
- `lastErrorCode` (string, optional, read-only)
- `lastErrorMessage` (string, optional, read-only; sanitized)
- `payloadJson` (text/json, required, read-only)

**Attempt History (if available)**
- `attemptNo` (int)
- `attemptedAt` (datetime)
- `outcomeStatus` (enum)
- `errorCode`/`errorMessage`

### Read-only vs editable by state/role
- All ingestion record fields are read-only.
- Only operator action is `retry` (command), not editing payload.

### Derived/calculated fields
- `retryEligible` (boolean) derived by backend or computed from status; prefer backend-provided to avoid duplicating policy.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementations may use `service-call` to invoke REST or local services; exact service names must be confirmed.

### Load/view calls
1. **Search**
   - Service: `accounting.events.IngestionSearch` *(placeholder)*
   - Inputs:
     - `eventType=WorkCompleted`
     - `status[]` (optional)
     - `occurredAtFrom`, `occurredAtTo` (optional)
     - `workorderId` (optional)
     - `eventId` (optional)
     - pagination: `pageIndex`, `pageSize`
     - sorting: `orderByField`, `orderByDirection`
   - Outputs:
     - `records[]` with fields listed above (subset ok for list)
     - `totalCount`

2. **Detail**
   - Service: `accounting.events.IngestionGet` *(placeholder)*
   - Inputs: `ingestionId` (or `eventId`)
   - Outputs: full ingestion record + attempt history + `retryEligible`

### Create/update calls
- None (UI does not create events).

### Submit/transition calls
1. **Retry**
   - Service: `accounting.events.IngestionRetry` *(placeholder)*
   - Inputs:
     - `ingestionId`
     - `operatorReason` (optional/required? **unclear**)
   - Outputs:
     - updated record status or an acknowledgment `{queued:true}`

### Error handling expectations
- 400 validation errors: show inline banner and field hints where applicable.
- 401/403: show “Not authorized” and hide retry action when permission missing.
- 404: record not found → show not-found state.
- 409: conflict (already processed / not retryable) → show conflict message and refresh record.
- Known domain error codes expected (from backend story/checklist examples):
  - `SCHEMA_VALIDATION_FAILED`
  - `INGESTION_DUPLICATE_CONFLICT`
  - `INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO` *(likely not applicable here; do not surface unless returned)*
  - `workorder_not_found`
  - `wip_reconciliation_failed`

---

## 10. State Model & Transitions

### Allowed states (display + filtering)
Backend-defined; UI must support at least:
- `RECEIVED` (optional)
- `VALIDATED` (optional)
- `PROCESSED_SUCCESS` (or `SUCCEEDED`)
- `FAILED`
- `DUPLICATE_IGNORED` / `DUPLICATE_CONFLICT`
- `RETRY_QUEUED` / `REPROCESSING`

### Role-based transitions
- Only authorized users can initiate `FAILED -> RETRY_QUEUED/REPROCESSING`.
- No UI transition for success states.

### UI behavior per state
- Success: show “Processed” and processed timestamp.
- Failed: show error code/message and show Retry if eligible.
- Duplicate: show duplicate explanation; hide Retry unless policy says retryable (default: not retryable).
- Processing: disable Retry and show spinner/processing indicator.

---

## 11. Alternate / Error Flows

### Validation failures
- User enters invalid UUID filter → UI prevents submission or shows server-side error.
- Backend returns `SCHEMA_VALIDATION_FAILED` for record details: show “Payload failed schema validation” and render payload viewer for analysis.

### Concurrency conflicts
- Retry clicked twice / two users retry simultaneously:
  - Backend returns 409 conflict “already queued/processing”; UI refreshes detail and shows latest status.

### Unauthorized access
- User without permission opens list: either deny access to screen or show empty + authorization error (per app convention).
- User can view but cannot retry: hide/disable Retry and show “Insufficient permissions” on attempt.

### Empty states
- No ingestion records match filters: show “No events found” with suggestion to adjust date range/status.

---

## 12. Acceptance Criteria

### Scenario 1: View WorkCompleted ingestion records list
**Given** I am an authenticated user with access to Accounting event ingestion screens  
**When** I open the WorkCompleted ingestion list screen  
**Then** I see a paginated list of ingestion records filtered to `eventType = WorkCompleted`  
**And** each row shows `occurredAt`, `eventId`, `workorderId`, `processingStatus`, and `processedAt` (if present)

### Scenario 2: Filter by status and date range
**Given** I am on the WorkCompleted ingestion list screen  
**When** I set `status = FAILED` and set an `occurredAt` date range  
**And** I run the search  
**Then** only records matching those filters are displayed  
**And** the URL (or preserved navigation state) retains the applied filters

### Scenario 3: View ingestion record detail with payload
**Given** an ingestion record exists for a WorkCompleted event  
**When** I open its detail screen  
**Then** I can view its identifiers (`eventId`, `workorderId`, `schemaVersion`, `sourceModule`)  
**And** I can view the sanitized last error code/message if the record failed  
**And** I can view the raw payload JSON in a read-only viewer

### Scenario 4: Retry a retry-eligible failed ingestion record
**Given** I have permission to retry ingestion records  
**And** an ingestion record is in a backend-defined retry-eligible failed state  
**When** I click “Retry” and confirm the action  
**Then** the UI calls the retry service with the record identifier  
**And** the UI shows a success acknowledgment and refreshes the record status  
**And** the Retry action is disabled while the request is in-flight

### Scenario 5: Retry rejected due to conflict (already processed/queued)
**Given** I am viewing a failed ingestion record  
**When** I click “Retry”  
**And** the backend responds with HTTP 409 indicating the record is no longer retryable  
**Then** the UI shows a conflict message  
**And** refreshes the record detail to display the current status

### Scenario 6: Unauthorized user cannot retry
**Given** I can view ingestion records but do not have retry permission  
**When** I open a failed ingestion record detail  
**Then** I do not see an enabled Retry action (hidden or disabled per convention)  
**And** if I attempt the retry endpoint via UI controls, I receive an authorization error message

---

## 13. Audit & Observability

### User-visible audit data
- Display:
  - `attemptCount`
  - `processedAt`
  - last retry/attempt timestamp (if attempt history exists)
  - actor for operator-initiated retry (if returned)

### Status history
- If backend provides attempt history, render a chronological list of attempts (attempt no, timestamp, outcome, errorCode).

### Traceability expectations
- UI must surface identifiers for cross-system tracing:
  - `eventId`, `workorderId`, `businessUnitId`, correlationId/traceId if provided.

---

## 14. Non-Functional UI Requirements

- **Performance:** list search should return and render first page within 2 seconds for typical filter usage (dependent on backend).
- **Accessibility:** all actions reachable via keyboard; payload viewer supports screen readers (at minimum provides copy-to-clipboard and proper labels).
- **Responsiveness:** list and detail screens usable on tablet widths; table columns may collapse but must remain functional.
- **i18n/timezone:** all timestamps displayed in user’s locale/timezone with an option to view raw ISO timestamp in detail (read-only). Currency display is informational only (no calculations).

---

## 15. Applied Safe Defaults

- SD-UI-EMPTY-STATE: Provide explicit “No results” empty state and guidance to adjust filters; qualifies as safe UI ergonomics; impacts UX Summary, Alternate / Error Flows.
- SD-UI-PAGINATION: Use standard pagination (pageSize default 25) and server-side paging; qualifies as safe UI ergonomics; impacts UX Summary, Service Contracts.
- SD-UI-INFLIGHT-GUARD: Disable action buttons while requests are in-flight to prevent double-submit; qualifies as safe error-prevention; impacts Functional Behavior, Alternate / Error Flows.

---

## 16. Open Questions

1. **Backend contract for ingestion records:** What are the exact API endpoints/service names, request parameters, and response field names for listing and fetching `WorkCompleted` ingestion records?
2. **Status model:** What are the authoritative `processingStatus` values for ingestion records (including “duplicate” and “retry queued/processing”)?
3. **Retry policy:** Which statuses/errors are retry-eligible (e.g., `wip_reconciliation_failed`, `workorder_not_found`) and which are not (e.g., `SCHEMA_VALIDATION_FAILED`, `INGESTION_DUPLICATE_CONFLICT`)?
4. **Authorization:** What permission(s)/scope(s) gate access to (a) viewing ingestion screens and (b) initiating a retry? (e.g., `SCOPE_accounting:events:ingest` is mentioned for service-to-service; what is the human/operator permission?)
5. **Operator reason/audit requirement:** Must the UI collect a mandatory “retry reason” comment for audit? If yes, what are constraints (min/max length) and where is it stored?
6. **Payload access controls:** Is the full event payload always safe to display to ops users, or must some fields be masked/redacted?
7. **Moqui navigation conventions:** What is the expected screen root/module path and menu placement in `durion-moqui-frontend` for accounting event ingestion UIs?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Ingest WorkCompleted Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/183  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest WorkCompleted Event

**Domain**: user

### Story Description

/kiro
Determine WIP finalization or readiness for invoicing.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `WorkCompleted` event from Workorder Execution

## Main Flow
1. Validate completion event and source workorder
2. If WIP accounting enabled:
   - Transfer WIP to Finished Work
3. Mark workorder as invoice-eligible
4. Persist completion accounting state

## Business Rules
- Completion does not create AR or revenue
- WIP handling is configuration-driven

## Acceptance Criteria
- [ ] WIP is reconciled (if enabled)
- [ ] Workorder marked invoice-ready

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

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