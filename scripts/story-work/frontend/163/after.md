## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

# 1. Story Header

## Title
[FRONTEND] [STORY] CRM Snapshot Viewer: Fetch & Display CRM Snapshot (Account + Contacts + Vehicles + Preferences) via Moqui

## Primary Persona
Service Advisor / CSR (POS user) using the Durion frontend to view customer context quickly during estimate creation and refresh.

## Business Value
Provide a fast, consistent “single view” of customer/account, contacts, vehicles, and billing-relevant contactability/preferences to speed estimate creation and reduce errors from inconsistent customer context.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Service Advisor / CSR  
- **I want** to retrieve and view a CRM-owned snapshot of account/person, contacts, vehicles, and billing-relevant preferences by **partyId and/or vehicleId**  
- **So that** estimate creation and customer context lookup are fast, consistent, and refreshable without navigating multiple screens.

## In-Scope
- A Moqui screen flow that:
  - Accepts `partyId` and/or `vehicleId` as inputs
  - Calls `GET /v1/crm-snapshot`
  - Displays snapshot metadata (version/timestamp), account summary, contacts (including primary), vehicles, and preferences
  - Handles error cases (400/403/404/500) with clear user messaging
  - Supports “refresh snapshot” action
- Frontend service integration (Moqui service call / REST call) consistent with project conventions.

## Out-of-Scope
- Editing CRM data (contacts, vehicles, preferences) from this UI
- Defining or changing CRM snapshot backend contract (this story consumes it)
- Workorder/estimate creation screens themselves (only provide a viewer/lookup to support them)
- Any deduplication/merge, customer lifecycle state changes

---

# 3. Actors & Stakeholders

- **Primary User:** Service Advisor / CSR (interactive UI user)
- **Downstream Consumer Context:** Workorder Execution (benefits from same snapshot contract)
- **System of Record / Provider:** CRM backend endpoint `GET /v1/crm-snapshot` (SoR for snapshot)
- **Stakeholders:** Billing (consumes billing-relevant preferences), CRM domain owners, POS frontend team, QA

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated into the POS frontend.
- Frontend has a valid server-side capability to call the CRM snapshot endpoint (either directly from Moqui server or via configured API gateway) with required credentials.
- Caller must be authorized per backend rules (allowlisted identity + scope `crm.snapshot.read`), but enforcement occurs server-side.

## Dependencies
- Backend endpoint exists and matches backend story contract:
  - `GET /v1/crm-snapshot?partyId={uuid}&vehicleId={uuid}`
  - At least one identifier required
  - Error codes and status codes as specified (notably `VEHICLE_HAS_NO_ACTIVE_PARTY` on 404)
- Project conventions for Moqui REST calls, screen routing, and Vue/Quasar embedding are assumed but not provided in this prompt (see Open Questions for any needed repo-specific details).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From an “Estimate Draft” or “Customer Lookup” context: a navigation link/button labeled “CRM Snapshot” that opens the snapshot viewer.
- Direct route access for debugging/support: user can open the snapshot viewer screen and input IDs manually.

## Screens to create/modify
- **Create:** `apps/pos/screen/crm/Snapshot.xml` (name indicative; final path must follow repo conventions)
  - Contains:
    - Input form: `partyId` (UUID), `vehicleId` (UUID)
    - Actions: `Load Snapshot`, `Refresh`
    - Display sections: Snapshot Metadata, Account, Contacts, Vehicles, Preferences
- **Modify (optional):** Navigation/menu screen to add link into CRM/Customer tools area (only if such menu exists in repo conventions).

## Navigation context
- URL route pattern suggestion (to be aligned to repo conventions):
  - `/crm/snapshot` (manual input)
  - `/crm/snapshot?partyId=...`
  - `/crm/snapshot?vehicleId=...`
- If entered with query params, screen should auto-load snapshot.

## User workflows
### Happy path (partyId)
1. User opens snapshot viewer (possibly pre-populated with `partyId`).
2. Clicks **Load Snapshot** (or auto-load occurs).
3. UI shows snapshot content and metadata.

### Happy path (vehicleId only)
1. User enters `vehicleId`, clicks **Load Snapshot**.
2. UI shows resolved primary party snapshot (no need to show resolution rules; backend handles it).

### Alternate paths
- Missing both fields → inline validation prevents submit.
- 404 not found / vehicle has no active party → show specific message and keep input values for correction.
- 403 unauthorized → show “Not authorized” message and guidance to contact admin.
- 500 unexpected → show generic error + correlation ID if provided.

---

# 6. Functional Behavior

## Triggers
- Screen load with `partyId` and/or `vehicleId` query parameters present → auto-trigger snapshot load once.
- User clicks `Load Snapshot` or `Refresh` → trigger snapshot load.

## UI actions
- **Load Snapshot**
  - Validates at least one identifier is provided
  - Calls a Moqui service to perform the GET request (server-side recommended to avoid exposing service tokens)
- **Refresh**
  - Repeats load with same identifiers
  - Updates displayed snapshot and metadata version/timestamp
- **Clear**
  - Clears inputs and snapshot display (optional but allowed as UI ergonomics)

## State changes (frontend)
- `idle` → `loading` → `loaded` or `error`
- When new request is made, previous snapshot remains visible until replaced OR is cleared immediately (must be deterministic; see Business Rules section)

## Service interactions
- Invoke `GET /v1/crm-snapshot` with query params.
- Handle status codes:
  - `200`: render snapshot
  - `400`: show validation error (missing identifiers)
  - `403`: unauthorized
  - `404`: not found; if `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY` show specialized message
  - `500`: show generic failure; include correlation ID if present in payload/headers

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- **Rule:** At least one of `partyId` or `vehicleId` must be provided before issuing a request.
  - UI: block submit and show message: “Enter a Party ID or Vehicle ID to load a snapshot.”
- **UUID format validation (UI-level):** If value is non-empty and not a UUID, show inline error and block submit.
  - Message examples:
    - “Party ID must be a valid UUID.”
    - “Vehicle ID must be a valid UUID.”

## Enable/disable rules
- Disable `Load Snapshot` and `Refresh` while request is `loading`.
- Disable `Refresh` when there is no last successful load (no snapshot currently loaded).

## Visibility rules
- Snapshot display sections are visible only when a snapshot is loaded.
- Error banner visible only in `error` state.
- Show empty-state guidance when idle: “Enter Party ID and/or Vehicle ID to view CRM snapshot.”

## Error messaging expectations
- 404 + `VEHICLE_HAS_NO_ACTIVE_PARTY`: “Vehicle exists but has no active associated party. Associate an owner/driver in CRM.”
- Generic 404: “No snapshot found for the provided identifier(s).”
- 403: “You are not authorized to view CRM snapshots. Contact your administrator.”
- 500: “Unable to load CRM snapshot due to a server error. Try again. If the issue persists, provide this correlation ID: {id}.”

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- **External DTO:** CRM Snapshot DTO returned by `GET /v1/crm-snapshot`
- **No local persistence** required; store in screen/view state only.

## Fields (type, required, defaults)

### Inputs
- `partyId`: string (UUID), optional
- `vehicleId`: string (UUID), optional

### Snapshot response (must render if present)
- `snapshotMetadata.snapshotId`: string (UUID), required for display if provided by backend
- `snapshotMetadata.createdAt`: string (ISO-8601 timestamp), required for display if provided
- `snapshotMetadata.version`: string, required for display if provided

- `account.partyId`: string (UUID), required
- `account.accountNumber`: string, optional
- `account.accountName`: string, required/expected
- `account.accountType`: enum `INDIVIDUAL | BUSINESS`, optional unless backend guarantees

- `contacts[]`:
  - `contactId`: UUID
  - `isPrimary`: boolean
  - `name`: string
  - `roles[]`: string array
  - `phoneNumbers[]`: objects `{ type, number }`
  - `emailAddresses[]`: objects `{ type, address }`
  - `preferences`: object with booleans/enums as in backend reference (render as read-only)

- `vehicles[]`:
  - `vehicleId`: UUID
  - `vin`: string
  - `licensePlate`: string nullable
  - `make/model/year`: optional

- `preferences` (account-level):
  - `marketingOptOut`: boolean
  - `doNotContact`: boolean
  - `invoiceDeliveryMethod`: enum `EMAIL | MAIL | NONE`

## Read-only vs editable
- All snapshot fields are **read-only** in this screen.

## Derived/calculated fields (UI-only)
- Display “Primary” badge/indicator for `contacts[].isPrimary === true`.
- Format timestamps to user locale/timezone per app defaults (no business logic changes).

---

# 9. Service Contracts (Frontend Perspective)

> Note: Backend contract is authoritative from backend story #99. Frontend must not invent new fields.

## Load/view calls
- **Operation:** Load CRM snapshot
- **Method/URL:** `GET /v1/crm-snapshot`
- **Query params:**
  - `partyId` optional
  - `vehicleId` optional
- **Client rule:** must send at least one param.

## Create/update calls
- None (read-only UI).

## Submit/transition calls
- None.

## Error handling expectations
- `400 Bad Request`: treat as user-correctable (input problem). Show inline + banner message.
- `403 Forbidden`: treat as authorization failure; no retries; show banner message.
- `404 Not Found`: treat as user-correctable (wrong ID / no relationship). Show banner message; preserve inputs.
- `500 Internal Server Error`: allow retry; show correlation ID if returned.

---

# 10. State Model & Transitions

## Allowed states (screen/view model)
- `idle`: no request yet
- `loading`: request in progress
- `loaded`: snapshot present
- `error`: last request failed

## Role-based transitions
- No additional role-based UI transitions defined beyond backend authorization result (403).
- If the frontend has roles that gate access to the screen, that must be configured (see Open Questions).

## UI behavior per state
- `idle`: show input form + help text; no snapshot sections.
- `loading`: disable inputs/actions; show loading indicator; keep existing snapshot visible OR hide it consistently:
  - **Rule for determinism:** keep existing snapshot visible but visually indicate “Refreshing…” (prevents blanking UI).
- `loaded`: show snapshot sections and metadata; enable Refresh.
- `error`: show error banner; keep input values; if prior snapshot existed, it may remain visible but must be clearly marked as “stale” (optional; see Open Questions).

---

# 11. Alternate / Error Flows

## Validation failures (client-side)
- If both IDs empty → do not call service; show inline message.
- If UUID format invalid → do not call service; show inline message per field.

## Backend 400
- Show message: “Request must include partyId or vehicleId.” (or backend-provided message).
- Keep user inputs.

## Backend 403
- Show not-authorized banner.
- Do not auto-retry.
- Provide guidance to contact administrator.

## Backend 404
- If payload indicates `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY` → show specialized message.
- Else → generic not-found.

## Backend 500 / network failure
- Show generic failure and retry option.

## Concurrency conflicts
- Not applicable (read-only). If backend uses ETag/version, frontend displays version only; no optimistic locking.

## Empty states
- If snapshot returns empty arrays (`contacts=[]`, `vehicles=[]`), render “No contacts found” / “No vehicles found” messages.

---

# 12. Acceptance Criteria

## Scenario 1: Load snapshot by partyId (success)
**Given** I am an authenticated POS user with access to the CRM snapshot viewer  
**And** I enter a valid `partyId` UUID  
**When** I click “Load Snapshot”  
**Then** the app calls `GET /v1/crm-snapshot?partyId={partyId}`  
**And** I see account summary, contacts list, vehicles list, and preferences  
**And** I see snapshot metadata including `version` and `createdAt` (when provided)

## Scenario 2: Load snapshot by vehicleId only (success)
**Given** I am an authenticated POS user with access to the CRM snapshot viewer  
**And** I enter a valid `vehicleId` UUID  
**When** I click “Load Snapshot”  
**Then** the app calls `GET /v1/crm-snapshot?vehicleId={vehicleId}`  
**And** I see a snapshot for the resolved primary party (as returned by backend)  
**And** the UI does not require me to select a party manually

## Scenario 3: Client-side validation blocks missing identifiers
**Given** I am on the CRM snapshot viewer  
**And** `partyId` is empty  
**And** `vehicleId` is empty  
**When** I click “Load Snapshot”  
**Then** no network/service request is made  
**And** I see an inline validation message telling me to enter a Party ID or Vehicle ID

## Scenario 4: 404 vehicle has no active party relationship
**Given** I enter a valid `vehicleId` UUID that exists but has no active party relationship  
**When** I load the snapshot  
**Then** the UI shows an error message indicating the vehicle has no active associated party  
**And** the UI keeps the entered `vehicleId` so I can correct it

## Scenario 5: Unauthorized access (403)
**Given** the backend returns `403 Forbidden` for my request  
**When** I load the snapshot  
**Then** the UI shows a not-authorized error state  
**And** the UI does not repeatedly retry the request

## Scenario 6: Server error (500) exposes correlation ID when present
**Given** the backend returns `500 Internal Server Error` with a correlation ID in the response (header or body)  
**When** I load the snapshot  
**Then** the UI displays a generic error message  
**And** includes the correlation ID value for support troubleshooting

---

# 13. Audit & Observability

## User-visible audit data
- Display `snapshotMetadata.createdAt` and `snapshotMetadata.version` (and `snapshotId` if present) to support “what am I looking at?” traceability.

## Status history
- Not applicable (read-only snapshot viewer). No entity state changes.

## Traceability expectations
- Frontend should attach/request a correlation ID for the call if project conventions support it (e.g., header propagation).
- Log (frontend or Moqui server logs) should include:
  - actor/user id (if available on server side)
  - queried `partyId`/`vehicleId` (avoid logging if considered sensitive; see Open Questions)
  - outcome status code
  - correlation id

---

# 14. Non-Functional UI Requirements

- **Performance:** initial snapshot load should feel responsive; show loading indicator within 200ms of action.
- **Accessibility:** form controls labeled; error messages associated to fields; keyboard navigation supported.
- **Responsiveness:** screen usable on tablet resolutions (common POS form factor).
- **i18n/timezone:** render timestamps in user locale/timezone using existing app conventions; no currency requirements in this story.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATES: Display “No contacts found” / “No vehicles found” when arrays are empty; safe because it’s pure presentation and does not affect domain rules. Impacted sections: UX Summary, Error Flows.
- SD-UX-LOADING-DISABLE: Disable actions during in-flight request; safe because it prevents duplicate calls and doesn’t change business outcomes. Impacted sections: Functional Behavior, State Model.
- SD-ERR-STATUS-MAP: Standard mapping of HTTP 400/403/404/500 to user-facing banners with retry only for 500; safe because it follows backend-implied semantics without inventing policies. Impacted sections: Service Contracts, Error Flows, Acceptance Criteria.

---

# 16. Open Questions

1. **Moqui integration pattern:** Should the frontend call `GET /v1/crm-snapshot` directly from the browser, or must it be proxied via a Moqui service (server-side) to satisfy allowlist + `crm.snapshot.read` scope requirements?  
2. **Route and screen placement:** What is the canonical screen path/module for CRM tools in `durion-moqui-frontend` (e.g., `/apps/pos/screen/crm/...` vs another structure)?  
3. **Error payload shape:** For 404 with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`, is `errorCode` returned in JSON body, and what is the exact field name/path?  
4. **Correlation ID source:** Where should the UI read the correlation ID (response header name like `X-Correlation-Id` vs JSON body field)?  
5. **PII logging policy (frontend/server):** Are partyId/vehicleId considered sensitive identifiers for logging in this workspace? If yes, what masking is required?  
6. **Access control at UI layer:** Is there a frontend role/permission that should hide/disable access to the Snapshot viewer screen even before backend 403 (e.g., “CSR” vs “Tech”)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/163


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/163
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Workorder System**, I want **a single endpoint to fetch account/person, contacts, vehicles, rules, and preferences** so that **estimate creation can be fast and consistent**.

## Details
- Endpoint accepts partyId and/or vehicleId.
- Returns normalized snapshot with version/timestamp.
- Cache-friendly response model.

## Acceptance Criteria
- Returns expected fields.
- Includes timestamps/version.
- Not-found handled cleanly.

## Integration Points (Workorder Execution)
- Workorder Execution calls snapshot at estimate draft and on-demand refresh.

## Data / Entities
- CRM Snapshot DTO / API

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


BACKEND STORY REFERENCES (FOR REFERENCE ONLY)

----------------------------------------------------------------------------------------------------

Backend matches (extracted from story-work):


[1] backend/99/backend.md

    Labels: type:story, domain:crm, status:ready-for-dev, agent:story-authoring, agent:crm, agent:workexec, agent:billing


----------------------------------------------------------------------------------------------------

Backend Story Full Content:



### BACKEND STORY #1: backend/99/backend.md

------------------------------------------------------------

Title: [BACKEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/99
Labels: type:story, domain:crm, status:ready-for-dev, agent:story-authoring, agent:crm, agent:workexec, agent:billing

## Story Intent
As a consuming system (initially Workorder Execution, and also Billing), I need a stable, efficient, CRM-owned read endpoint to retrieve a point-in-time snapshot of core customer relationship data (Account/Party, Contacts, Vehicles, and contactability + billing-relevant preferences), so downstream workflows can operate quickly and consistently without re-deriving CRM association rules.

## Actors & Stakeholders
- **Provider / SoR:** CRM service (`domain:crm`) owns the API and contract.
- **Consumers:** Workorder Execution (`domain:workexec`), Billing (`domain:billing`).
- **Indirect user:** Service Advisor (benefits from faster, consistent estimate creation).

## Preconditions
- Caller is service-to-service authenticated.
- Caller is authorized by **both**:
  - allowlisted service identity, and
  - required scope: `crm.snapshot.read`
- Request includes at least one identifier: `partyId` or `vehicleId`.

## Functional Behavior
### Endpoint
- `GET /v1/crm-snapshot`
- Query params:
  - `partyId` (optional)
  - `vehicleId` (optional)
- Rule: request MUST include at least one of `partyId` or `vehicleId`.

### Snapshot Retrieval (partyId)
- If `partyId` is provided, CRM returns a snapshot for that party/account, including:
  - account summary
  - active contacts (with one primary contact indicated)
  - associated vehicles (active)
  - preferences (v1 scope defined below)

### Snapshot Retrieval (vehicleId-only)
When only `vehicleId` is provided, CRM MUST resolve a single “primary party” deterministically:
1. Find all **active vehicle-party relationships** for the vehicle.
2. Select primary party by precedence:
   1. `PRIMARY_OWNER` (ACTIVE)
   2. else `OWNER` (ACTIVE; most recent)
   3. else `LESSEE/DRIVER` (ACTIVE; most recent)
   4. else any ACTIVE relationship (most recent)
3. Tie-breaker for “most recent”: latest `effectiveFrom`, then latest `updatedAt`.
4. If **no active relationships**, return `404` with error code `VEHICLE_HAS_NO_ACTIVE_PARTY`.
5. Return contacts:
   - all active contacts for the resolved primary party/account
   - mark one as `isPrimary=true` using CRM’s primary-contact flag; if none exists, choose most recently updated contact.

## Alternate / Error Flows
- Missing identifiers (`partyId` and `vehicleId` both absent) → `400 Bad Request`
- Party not found (invalid `partyId`) → `404 Not Found`
- Vehicle not found (invalid `vehicleId`) → `404 Not Found`
- Vehicle has no active party relationship → `404 Not Found` with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`
- Unauthorized (valid auth, but not allowlisted and/or missing scope) → `403 Forbidden`
- Unexpected aggregation failure → `500 Internal Server Error` with correlation ID logged

## Business Rules
- **Domain ownership (Decision):** `domain:crm` owns this read API as a CRM read model. Billing/Workexec are consumers.
- **Contract stability:** response schema is versioned (`/v1/...`) and changes must be backward compatible within a version.
- **Preferences scope (Decision, v1):** “rules and preferences” means **contactability + billing-relevant preferences only**; explicitly exclude Workexec/Billing workflow/policy rules.
- **Authorization (Decision):** NOT open to “any internal caller”; restricted to allowlisted service identities + required scope.

## Data Requirements
### Response (v1) — shape (illustrative)
```json
{
  "snapshotMetadata": {
    "snapshotId": "uuid",
    "createdAt": "ISO-8601-Timestamp",
    "version": "string | ETag"
  },
  "account": {
    "partyId": "uuid",
    "accountNumber": "string",
    "accountName": "string",
    "accountType": "INDIVIDUAL | BUSINESS"
  },
  "contacts": [
    {
      "contactId": "uuid",
      "isPrimary": true,
      "name": "string",
      "roles": ["string"],
      "phoneNumbers": [{ "type": "MOBILE", "number": "string" }],
      "emailAddresses": [{ "type": "PRIMARY", "address": "string" }],
      "preferences": {
        "emailOptIn": true,
        "smsOptIn": false,
        "phoneOptIn": true,
        "doNotContact": false,
        "preferredContactMethod": "EMAIL | SMS | PHONE | NONE",
        "preferredLanguage": "string"
      }
    }
  ],
  "vehicles": [
    {
      "vehicleId": "uuid",
      "vin": "string",
      "licensePlate": "string",
      "make": "string",
      "model": "string",
      "year": 2024
    }
  ],
  "preferences": {
    "marketingOptOut": false,
    "doNotContact": false,
    "invoiceDeliveryMethod": "EMAIL | MAIL | NONE"
  }
}
```

## Acceptance Criteria
- **AC-1: Successful snapshot retrieval by partyId**
  - Given a valid `partyId` exists with contacts and vehicles
  - When calling `GET /v1/crm-snapshot?partyId={partyId}`
  - Then the service returns `200 OK` with the correct snapshot DTO and `snapshotMetadata.version`.

- **AC-2: Successful snapshot retrieval by vehicleId using deterministic party resolution**
  - Given a valid `vehicleId` exists with active relationships
  - When calling `GET /v1/crm-snapshot?vehicleId={vehicleId}`
  - Then the service returns `200 OK`
  - And selects the primary party using precedence `PRIMARY_OWNER > OWNER > LESSEE/DRIVER > any active` with “most recent” tie-breaker.

- **AC-3: Vehicle has no active party relationship**
  - Given a valid `vehicleId` exists but has no active party relationship
  - When calling `GET /v1/crm-snapshot?vehicleId={vehicleId}`
  - Then the service returns `404 Not Found` with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`.

- **AC-4: Missing identifier rejected**
  - Given any state
  - When calling `GET /v1/crm-snapshot` with no query params
  - Then the service returns `400 Bad Request`.

- **AC-5: Authorization is allowlist + scope enforced**
  - Given a valid service token
  - When the caller is not allowlisted and/or missing `crm.snapshot.read`
  - Then the service returns `403 Forbidden`.

- **AC-6: Preferences scope (v1) is limited**
  - Given a valid snapshot request
  - Then the response contains only contactability + billing-relevant preferences
  - And does not contain Workexec/Billing workflow/policy rules.

## Audit & Observability
- **Audit:** log caller identity + queried identifiers + timestamp.
- **Rate limiting:** per-caller rate limits to protect CRM.
- **Metrics:** request count, latency, and status codes, tagged by caller identity.
- **Logging:** structured logs with correlation IDs for all errors (4xx/5xx).

## Open Questions
- None. Decisions were supplied in the issue comments (Decision Record generated by `clarification-resolver.sh` on 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #99 — [BACKEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Workorder System**, I want **a single endpoint to fetch account/person, contacts, vehicles, rules, and preferences** so that **estimate creation can be fast and consistent**.

## Details
- Endpoint accepts partyId and/or vehicleId.
- Returns normalized snapshot with version/timestamp.
- Cache-friendly response model.

## Acceptance Criteria
- Returns expected fields.
- Includes timestamps/version.
- Not-found handled cleanly.

## Integration Points (Workorder Execution)
- Workorder Execution calls snapshot at estimate draft and on-demand refresh.

## Data / Entities
- CRM Snapshot DTO / API

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*

------------------------------------------------------------

====================================================================================================

END BACKEND REFERENCES