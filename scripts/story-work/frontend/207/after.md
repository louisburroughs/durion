## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting Events Ingestion: Submit Canonical Event via Sync Endpoint + View Acknowledgement

### Primary Persona
Accounting integration operator / platform engineer (service-to-service producer validation support)

### Business Value
Enable producing modules and operators to submit accounting events through a canonical ingestion endpoint and receive deterministic acknowledgements (accepted/replay/conflict), improving auditability, traceability, and operational support for downstream accounting processing.

---

## 2. Story Intent

### As a / I want / So that
- **As a** platform engineer or accounting integration operator,
- **I want** a Moqui UI to submit a canonical accounting event envelope to the ingestion sync API and view the acknowledgement/result,
- **So that** I can validate integration behavior end-to-end (validation, auth failures, idempotent replay, conflict detection) without needing external tooling.

### In-scope
- Moqui screen(s) to:
  - enter a canonical envelope (specVersion, eventId optional for sync path, eventType, sourceModule, eventTimestamp, schemaVersion, payload, correlationId optional)
  - submit to sync ingestion endpoint
  - display acknowledgement fields (`eventId`, `status`, `receivedAt`) and HTTP outcome semantics (202/200/409/400/422/503/403)
- Validation on the UI aligned to backend contract (required fields, UUIDv7 format where provided)
- Error handling and user-facing messages mapped from stable backend error codes
- Basic “replay” support (re-submit same eventId/payload) and “conflict” testing (same eventId different payload)

### Out-of-scope
- Implementing broker/Kafka/SQS ingestion configuration and operations (backend/infrastructure)
- Downstream mapping/posting screens (journal entries, ledger posting, etc.)
- Defining or validating eventType-specific payload schema beyond treating `payload` as JSON blob (except basic JSON validity)

---

## 3. Actors & Stakeholders
- **Integration Operator / Platform Engineer (human user):** uses UI to submit test events and inspect responses.
- **Producing Module (service principal):** ultimate actor for real ingestion; UI is a diagnostic/support tool.
- **Accounting Ops / Finance:** benefits from predictable ingestion statuses and conflict visibility (via acknowledgement in UI).
- **Security/Admin:** ensures only authorized users can access ingestion UI and that calls are made with proper scopes.

---

## 4. Preconditions & Dependencies
- Moqui is configured to reach the Accounting Ingestion backend base URL (environment-specific).
- Sync ingestion endpoint exists and follows backend story contract:
  - New accept → `202 Accepted`
  - Replay → `200 OK`
  - Conflict → `409 Conflict` with `INGESTION_DUPLICATE_CONFLICT`
  - Validation failure → `400` `INGESTION_VALIDATION_FAILED`
  - Unsupported schema → `422` `INGESTION_SCHEMA_UNSUPPORTED`
  - Dependency failure → `503` `INGESTION_DEPENDENCY_FAILURE`
  - Source mismatch → `403` `INGESTION_FORBIDDEN_SOURCE_MISMATCH`
- Authentication mechanism from Moqui to backend is available (token forwarding or service credential) and must include scope `SCOPE_accounting:events:ingest`. (If not available, see Open Questions—none required if already standardized in this repo.)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main menu: **Accounting → Integrations → Event Ingestion (Sync)**
- Direct route: `/accounting/integration/event-ingestion`

### Screens to create/modify
- **New screen:** `apps/<app>/screens/accounting/integration/EventIngestion.xml` (name/location per repo conventions)
  - Subscreen(s) optional:
    - `EventIngestionSubmit.xml` (form)
    - `EventIngestionResult.xml` (result panel)

### Navigation context
- Screen is under accounting domain navigation.
- Include breadcrumb: Accounting → Integrations → Event Ingestion (Sync).

### User workflows
**Happy path (new event accepted):**
1. User opens Event Ingestion screen.
2. User fills envelope fields; payload provided as JSON text.
3. Clicks **Submit**.
4. UI calls ingestion endpoint and shows acknowledgement with status and receivedAt.

**Alternate path (replay):**
1. User submits same `eventId` with identical payload again.
2. UI shows replay acknowledgement (HTTP 200), clearly indicating replay vs accepted/new.

**Alternate path (conflict):**
1. User submits an event with an `eventId`.
2. User modifies payload but keeps same `eventId`, submits again.
3. UI shows conflict (HTTP 409) with error code and guidance.

**Failure paths:**
- Missing/invalid fields → inline validation and/or server error display.
- Auth/source mismatch → show forbidden with code.
- Dependency failure → show retry guidance.

---

## 6. Functional Behavior

### Triggers
- User clicks **Submit** on the ingestion form.

### UI actions
- Validate required fields locally:
  - required: `specVersion`, `eventType`, `sourceModule`, `eventTimestamp`, `schemaVersion`, `payload`
  - `eventId` optional (sync exception); if provided, validate UUID format and require UUIDv7 pattern if feasible
  - `payload` must be valid JSON
- On submit:
  - Disable submit button and show in-progress state
  - POST envelope to backend ingestion endpoint
  - Render response:
    - acknowledgement panel for 200/202
    - error panel for 4xx/5xx with code/message details

### State changes (frontend)
- Maintain client-side “last submission” model:
  - lastRequest (sanitized), lastResponse, lastHttpStatus, lastErrorCode
- No local persistence required unless Moqui conventions already include request history; do not add new entities unless specified.

### Service interactions
- Moqui service call to an internal service that performs outbound HTTP:
  - `accounting.integration.EventIngestion.submit` (service name suggestion; implement per repo naming conventions)
- Service performs POST to backend URL and returns normalized response map to screen.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Required fields** must be present before submission (client-side) and re-validated server-side.
- **eventId rules:**
  - If user provides `eventId`, UI validates it is a UUID and (where possible) UUIDv7.
  - If not provided, UI allows submission (sync path only); server may generate eventId.
- **payload** must be syntactically valid JSON. UI does not enforce eventType-specific schema.
- **correlationId** optional; if provided, must be non-empty string.

### Enable/disable rules
- Submit disabled while request in flight.
- “Generate eventId” helper button (UI convenience) may generate a UUID, but **must not claim UUIDv7 unless library guarantees it**. If UUIDv7 generation isn’t available, omit this helper (safe-defaults denylist includes identifiers). (See Applied Safe Defaults: none.)

### Visibility rules
- Acknowledgement panel visible after any server response.
- Error details panel visible only on non-2xx responses.

### Error messaging expectations
- Display stable backend error code prominently when provided:
  - `INGESTION_DUPLICATE_CONFLICT`
  - `INGESTION_FORBIDDEN_SOURCE_MISMATCH`
  - `INGESTION_VALIDATION_FAILED`
  - `INGESTION_SCHEMA_UNSUPPORTED`
  - `INGESTION_DEPENDENCY_FAILURE`
- Provide user guidance:
  - 409: “Same eventId with different payload. Use a new eventId or revert payload to original.”
  - 403: “Authenticated producer does not match sourceModule.”
  - 422: “schemaVersion not supported for this eventType.”
  - 503: “Temporary dependency failure. Retry later.”

---

## 8. Data Requirements

### Entities involved
- **No new Moqui entities required** for MVP UI submission and result viewing.
- Backend-owned immutable storage for raw events is assumed (out-of-scope for frontend).

### Fields (type, required, defaults)
Form input model (frontend/Moqui context):
- `specVersion` (string, required) — example: `durion.accounting.ingestion.v1`
- `eventId` (string UUID, optional for sync)
- `eventType` (string, required)
- `sourceModule` (string, required)
- `eventTimestamp` (datetime string, required; ISO-8601)
- `schemaVersion` (string, required; SemVer per backend)
- `correlationId` (string, optional)
- `payload` (JSON object, required; entered as text area, parsed to object before send)

Response/ack model:
- `eventId` (string UUID)
- `status` (string; includes accepted/new vs replay statuses as returned by backend)
- `receivedAt` (datetime string)
- Optional server-provided fields (if present): `payloadHash`, `message`, etc. (display in raw JSON viewer)

### Read-only vs editable by state/role
- All request fields editable prior to submit.
- After response, fields remain editable to allow replay/conflict testing; acknowledgement is read-only.

### Derived/calculated fields
- Client parses `payload` text into JSON object for request.
- Client may compute and show a local “payload validity” indicator (valid/invalid JSON).

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
- None required for initial screen load (static form).
- Optional: load default `specVersion` value from configuration (Moqui conf) rather than hardcoded.

### Create/update calls
- **POST** to backend ingestion endpoint (exact URL path must match backend deployment; use config):
  - Request body: canonical envelope:
    - `specVersion` (string)
    - `eventId` (string UUIDv7, optional)
    - `eventType` (string)
    - `sourceModule` (string)
    - `eventTimestamp` (string datetime)
    - `schemaVersion` (string)
    - `payload` (object)
    - `correlationId` (string, optional)

### Submit/transition calls
- Single submit action mapped to Moqui service:
  - Input: fields above
  - Output (normalized):
    - `httpStatus` (int)
    - `ack` (map: eventId/status/receivedAt/…)
    - `error` (map: code/message/details)

### Error handling expectations
- Map HTTP statuses:
  - 202/200: show acknowledgement, mark outcome = success
  - 409: show error code `INGESTION_DUPLICATE_CONFLICT` and conflict guidance
  - 400: show `INGESTION_VALIDATION_FAILED` + field hints if provided
  - 422: show `INGESTION_SCHEMA_UNSUPPORTED`
  - 403: show `INGESTION_FORBIDDEN_SOURCE_MISMATCH`
  - 503: show `INGESTION_DEPENDENCY_FAILURE` + retry guidance
- Network/timeout: show “Unable to reach ingestion service” with retry guidance; do not expose tokens.

---

## 10. State Model & Transitions

### Allowed states (frontend view)
This UI is stateless beyond last submission; for display purposes classify last outcome as:
- `Idle`
- `Submitting`
- `Accepted` (202)
- `Replay` (200)
- `Conflict` (409)
- `ValidationFailed` (400)
- `SchemaUnsupported` (422)
- `Forbidden` (403)
- `DependencyFailure` (503)
- `NetworkError` (no HTTP response)

### Role-based transitions
- Only authorized UI users may access screen (permission required; see Open Questions if permissions are not standardized).
- All authorized users can submit; no additional transitions.

### UI behavior per state
- Submitting: disable submit, show spinner/progress
- Accepted/Replay: show acknowledgement panel (success styling) and raw response
- Any error: show error panel and raw error response

---

## 11. Alternate / Error Flows

### Validation failures
- Client-side:
  - Missing required fields → prevent submit, show inline messages
  - Invalid JSON payload → prevent submit, show “Payload must be valid JSON”
- Server-side (400):
  - Display backend error code and message; keep user input for correction

### Concurrency conflicts
- Not applicable (single submission). If backend returns optimistic-lock style errors, display raw error and treat as unknown failure.

### Unauthorized access
- If user lacks permission to view screen: show access denied (Moqui standard) and do not render form.
- If backend returns 403: show forbidden with `INGESTION_FORBIDDEN_SOURCE_MISMATCH`.

### Empty states
- On first load: blank form with optional prefilled `specVersion`.
- If no response yet: hide acknowledgement/error panels.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Submit new valid event returns 202 acknowledgement
Given I am an authorized user for Accounting Event Ingestion screen  
And I enter a valid canonical envelope with required fields and valid JSON payload  
When I submit the event  
Then the system sends the envelope to the ingestion sync endpoint  
And I see an acknowledgement with eventId, status, and receivedAt  
And the HTTP outcome is displayed as Accepted (202)

### Scenario 2: Submit replay returns 200 and shows replay outcome
Given I previously submitted an event with eventId "E1" and payload "P1" successfully  
When I submit the same eventId "E1" with the same payload "P1" again  
Then the system shows a successful acknowledgement response  
And the HTTP outcome is OK (200)  
And the UI indicates the result is a replay (idempotent)

### Scenario 3: Submit conflict returns 409 and displays stable error code
Given an event with eventId "E1" and payload "P1" was previously accepted  
When I submit eventId "E1" with a different payload "P2"  
Then the system shows a failure result  
And the HTTP outcome is Conflict (409)  
And the UI displays error code "INGESTION_DUPLICATE_CONFLICT"  
And the UI guidance instructs to change eventId or revert payload

### Scenario 4: Missing required fields are blocked client-side
Given I am on the Event Ingestion screen  
When I leave specVersion empty or payload is invalid JSON  
And I click Submit  
Then the UI prevents submission  
And I see inline validation messages for the missing/invalid fields

### Scenario 5: Unsupported schema returns 422 and is shown clearly
Given I enter a valid envelope with schemaVersion not supported by the backend for the chosen eventType  
When I submit the event  
Then the UI shows a failure result  
And the HTTP outcome is Unprocessable Entity (422)  
And the UI displays error code "INGESTION_SCHEMA_UNSUPPORTED"

### Scenario 6: Backend dependency failure returns 503 with retry guidance
Given the ingestion service storage dependency is unavailable  
When I submit a valid envelope  
Then the UI shows a failure result  
And the HTTP outcome is Service Unavailable (503)  
And the UI displays error code "INGESTION_DEPENDENCY_FAILURE"  
And the UI suggests retrying later

---

## 13. Audit & Observability

### User-visible audit data
- Display `eventId`, `eventType`, `sourceModule`, `schemaVersion`, and `correlationId` (if provided) in the result summary for traceability.
- Display `receivedAt` from acknowledgement.

### Status history
- In-session only: show last outcome and timestamp of submission attempt (client time) plus server `receivedAt`.

### Traceability expectations
- Moqui logs (frontend service layer) should log structured fields:
  - `eventId` (if provided or returned)
  - `eventType`, `sourceModule`, `schemaVersion`
  - `httpStatus`, `errorCode` (if any)
- Do not log full payload by default (may be sensitive/large); if needed, log payload hash only when returned by backend.

---

## 14. Non-Functional UI Requirements

- **Performance:** submission UI should respond immediately; network request timeout should be configurable; show progress within 250ms of submit action.
- **Accessibility:** all form controls labeled; error messages associated to fields; keyboard navigable submit.
- **Responsiveness:** usable on tablet width; payload editor scrolls.
- **i18n/timezone/currency:** display timestamps in user locale but preserve raw ISO string in a “raw response” viewer; no currency handling needed.

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions
- none

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Events: Receive Events via Queue and/or Service Endpoint  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/207  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Events: Receive Events via Queue and/or Service Endpoint

**Domain**: general

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
Events: Receive Events via Queue and/or Service Endpoint

## Acceptance Criteria
- [ ] Provide a synchronous ingestion API endpoint for producing modules
- [ ] Provide an async ingestion channel (queue/topic) where configured
- [ ] Received events are persisted immutably before mapping/posting
- [ ] System returns acknowledgement with eventId and initial status

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