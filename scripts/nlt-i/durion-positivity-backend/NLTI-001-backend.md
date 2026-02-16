repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Foundation & Shell: NLTI API envelope + session + correlation"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:architecture
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:architecture
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As a Positivity NLTI user, I want a stable NLTI backend entrypoint with session and correlation support so that all future NLTI capabilities can rely on consistent request/response envelopes and traceability.

## Actors & Stakeholders
- **Primary actor:** Authenticated user (human) interacting via NLTI UI.
- **System:** NLTI Service (Positivity domain).
- **Secondary stakeholders:** API Gateway, AuthN/AuthZ components, Observability tooling, Domain services (future tool callers).

## Preconditions
- User is authenticated via existing Positivity AuthN.
- NLTI service is reachable via API gateway.
- A client can provide (or receive) a `sessionId` (client-managed or server-issued) and will include it on subsequent calls.

## Functional Behavior
1. **NLTI Request API**
   - Expose an NLTI request endpoint (e.g., `POST /nlt/v1/requests`) that accepts:
     - `prompt` (string, required)
     - `sessionId` (string, optional on first request)
     - `context` (object, optional, reserved for future: active tenant/location/user preferences)
   - The service MUST return a structured response envelope including:
     - `correlationId` (string, required)
     - `sessionId` (string, required in response)
     - `status` (enum: `ok` | `error`)
     - `message` (human-friendly summary)
     - `result` (object; may be empty in this story)
2. **Session Handling**
   - If `sessionId` is missing, issue a new `sessionId` and return it.
   - If `sessionId` is present, associate the request with that session.
   - Do not implement “memory” or personalization beyond session correlation in this story.
3. **Response Rendering Contract**
   - Response MUST be stable and versioned (`/v1/`) to support forward additions.
   - `result` payload MAY contain:
     - `answerText` (string, optional)
     - `plan` (object, optional)
     - `errors` (array, optional)
4. **Error Contract**
   - On validation errors (missing prompt), return a structured error envelope with `status=error`, `message`, and a correlationId.
   - On unexpected errors, return `status=error`, a generic message, and correlationId.

## Alternate / Error Flows
- **Missing `prompt`:** return `status=error` with validation message and correlationId.
- **Upstream gateway auth failure:** request is rejected before NLTI; NLTI does not process.
- **Internal exception:** return `status=error` with generic message; do not leak stack traces to clients.

## Business Rules
- NLTI shell MUST NOT execute business mutations in this story.
- NLTI shell MUST NOT bypass authentication/authorization.
- CorrelationId MUST be returned on every response to support traceability.

## Data Requirements
- Fields:
  - `correlationId`: unique per request
  - `sessionId`: stable within a user’s session
  - `prompt`: stored only if required by later audit capability; for this story, persistence is not required (may log safely).
- Minimal logging:
  - correlationId
  - sessionId
  - request timestamp
  - user identifier (subject) if available

## Acceptance Criteria
- **Given** an authenticated user, **when** they POST a valid NLTI request with `prompt`, **then** the response includes `correlationId`, `sessionId`, and `status=ok`.
- **Given** a first-time request without `sessionId`, **when** the user POSTs, **then** the service returns a new `sessionId` that can be reused on subsequent calls.
- **Given** a request with missing/blank `prompt`, **when** the user POSTs, **then** the service returns `status=error` with a correlationId and a validation message.
- **Given** an internal error, **when** the service fails, **then** the response is `status=error` with correlationId and no sensitive details.

## Audit & Observability
- Log a single structured entry per request including: `correlationId`, `sessionId`, authenticated user id, and outcome (`ok`/`error`).
- Ensure correlationId is propagated to downstream logs (even though no downstream tool calls occur in this story).

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Foundation & Shell (Chat panel + session + command router)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want a Natural Language Task Interface entry point where I can type requests and see structured results so I can start accomplishing work through a single conversational interface.

## Actors & Stakeholders
- Primary: Authenticated Positivity user (Service Manager, Accounting Clerk, etc.)
- Stakeholders: Platform engineering, Security, Domain teams (workexec/accounting/inventory/crm)

## Functional Behavior
- Provide an NLTI panel with:
  - Text input and submit
  - Session-scoped history
  - Rendered responses (answer, plan, errors)
- Provide a backend NLTI endpoint that:
  - Accepts a prompt + session context
  - Returns a structured response envelope with a correlation ID

## Acceptance Criteria
- Given an authenticated user, when they submit a prompt, then a response is returned and displayed with a correlation ID.
- Given an error, when the backend returns a failure, then the UI shows a friendly error and the correlation ID.
- Given multiple prompts in a session, when the user submits sequential requests, then the UI shows session history in order.