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
As a Positivity NLTI user, I want a stable, secure NLTI backend entrypoint that provides session management, correlation IDs, and a stable `v1` request/response envelope so downstream planners, auditors and operators can rely on deterministic signals for traceability, observability and safe delegation to downstream capabilities.

## Actors & Stakeholders
- **Primary actor:** Authenticated user (human) interacting via NLTI UI.
- **System:** NLTI Service (Positivity domain).
- **Secondary stakeholders:** API Gateway, AuthN/AuthZ components, Observability tooling, Domain services (future tool callers), SRE.

## Preconditions
- AuthN authenticates requests and supplies a stable subject identifier.
- API Gateway forwards inbound headers (or allows NLTI to set them) including `X-Correlation-Id`.
- NLTI service is reachable via API gateway.
- Clients can provide (or receive) a `sessionId` (client-managed or server-issued).

## Functional Behavior (concise)
1. NLTI Request API
   - Expose `POST /nlt/v1/requests` and `GET /nlt/v1/requests/{requestId}` with a stable JSON contract.
   - Minimal request envelope: `prompt` (string, required for ACTION/QUERY), optional `sessionId`, optional `clientContext` (whitelisted fields only).
   - Returned envelope (`RequestResponseV1`) must include: `requestId`, `correlationId`, `sessionId`, `status` (`ACCEPTED`|`COMPLETE`|`ERROR`), `meta` (timings, validation issues), and `result` (if available).

2. Session Handling
   - If `sessionId` absent, issue a secure opaque `sessionId` (UUIDv4 or equivalent). Persist only minimal session metadata (creation time, user subject) for correlation.
   - Session storage is transient for this story (no conversation memory beyond session-scoped metadata).

3. Correlation & Tracing
   - If request includes `X-Correlation-Id`, echo it in the response and propagate it to logs/traces. Otherwise generate a UUIDv4 correlation id.
   - Start a trace/span for request parsing and validation; attach `correlationId`, `requestId`, `userId` attributes.

4. Validation & Response Codes
   - Synchronous validation failures: 400 with `{status: "error", code: "VALIDATION_ERROR", correlationId, details[]}`.
   - Authorization errors: 401/403 from gateway upstream.
   - Long-running requests: return 202 ACCEPTED with `requestStatus: ACCEPTED` and a `requestId` for polling.

5. Non-functional
   - Rate-limit per-session and per-subject to prevent abuse.
   - Do not persist prompt text beyond what audit policy permits; otherwise store redacted/hash.

## Acceptance Criteria (testable)
- Given an authenticated user POST with valid `prompt`, service returns 200 or 202 with `correlationId`, `sessionId`, `requestId`, and `status`.
- Given missing `prompt`, service returns 400 with structured validation details and correlationId.
- Given inbound `X-Correlation-Id`, server echoes same value back and logs it.
- Given repeated requests with same sessionId, session metadata is reused (no new session issued).

## Security & Privacy
- Never log raw prompts unless audit policy allows; default to storing hashes or redacted text.
- Enforce least-privilege for any downstream calls; this story does not perform downstream mutations.

## Observability & Metrics
- Metrics: `nlt.requests.count`, `nlt.requests.latency_ms`, `nlt.requests.invalid_count` (tag by `status`, `env`).
- Tracing: span for request ingest + validation with `correlationId` and `requestId` attributes.

## Test Scenarios
- Unit: envelope validation, session issuance, correlation propagation.
- Integration: POST/GET flows, error cases (400, 401/403, 500), long-running 202 flows.

## Open Questions / Decisions
- Should `clientContext` accept freeform JSON or a vetted whitelist? (Recommend whitelist to limit leakage.)

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Foundation & Shell (Chat panel + session + command router)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

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
