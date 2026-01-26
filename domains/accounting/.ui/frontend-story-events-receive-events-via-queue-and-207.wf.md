# [FRONTEND] [STORY] Events: Receive Events via Queue and/or Service Endpoint
## Purpose
Provide an Accounting Event Ingestion Submit screen that lets authorized users enter a canonical event envelope (including JSON payload) and submit it to a synchronous ingestion endpoint. The screen validates inputs client-side (UUIDv7, required fields, JSON validity) to prevent avoidable failures and renders a normalized outcome (accepted vs replay vs conflict vs rejected). It also enforces permission gating and safe visibility rules while supporting observability via W3C Trace Context and copyable identifiers.

## Components
- Page header: “Event Ingestion Submit” + brief help text
- Permission-gated access banner (access denied state)
- Canonical envelope form
  - specVersion (text, required)
  - eventId (text, optional; UUIDv7 validation)
  - eventType (text, required)
  - sourceModule (text, required)
  - eventTimestamp (date-time input or text, required; ISO-8601 parseable)
  - schemaVersion (text, required)
  - correlationId (text, optional; non-empty trimmed if provided)
  - payload (multiline text area, required; JSON validity)
- Trace context controls
  - traceparent (text; auto-generated if absent; editable/readonly per design)
  - Copy traceparent action
- Actions
  - Validate (optional explicit button) and/or inline validation on submit
  - Submit button (disabled while in-flight)
  - Reset/Clear button
- Inline field validation messages + global validation summary
- Result area (collapsible panels)
  - Success panel (2xx): acknowledgement fields + copy actions
  - Error panel (non-2xx): status, errorCode, message, details (safe summary) + guidance
- Copy actions for: eventId (returned/authoritative), correlationId (if present), traceparent used
- Loading indicator / in-flight state indicator

## Layout
- Top: Page header + short instructions + permission status
- Main (single column):
  - Form section: Envelope fields (grouped), then Payload editor
  - Trace context row: traceparent field + Copy
  - Action row: Submit (primary), Reset (secondary)
  - Result section below form: [Success Panel] or [Error Panel] with copy buttons
- Footer (optional): small print about permissions and payload visibility restrictions

## Interaction Flow
1. User navigates to Event Ingestion Submit screen.
2. If user lacks screen permission, show access denied state; hide/disable form and submit action.
3. User enters required fields: specVersion, eventType, sourceModule, eventTimestamp, schemaVersion, payload.
4. User optionally enters eventId and correlationId.
5. On Submit:
   1. Run client-side validation:
      - Required fields present.
      - If eventId provided, validate UUIDv7 format; block submit on failure.
      - If correlationId provided, ensure non-empty after trim; block submit on failure.
      - Parse payload as JSON; block submit if invalid JSON.
      - eventTimestamp must be ISO-8601 parseable; block submit on failure.
   2. Ensure traceparent exists:
      - If user provided one, propagate unchanged.
      - If absent, generate and attach a W3C traceparent value.
   3. Disable Submit and show in-flight indicator to prevent double-submit.
   4. POST canonical envelope to the configured sync ingestion endpoint via Moqui service.
6. On 2xx response:
   1. Render Success panel with acknowledgement fields: eventId (UUIDv7), receivedAt, and backend-authoritative outcome (accepted/replay/conflict/rejected if provided).
   2. If backend returns eventId even when omitted by user, display backend value as authoritative.
   3. Provide Copy actions for eventId and traceparent used (and correlationId if present).
7. On non-2xx response:
   1. Render Error panel showing HTTP status, errorCode, message, and safe details (if any).
   2. Map canonical error details to inline field errors when keys match (400/422).
   3. For 401/403, show access denied; do not reveal existence of eventId.
   4. For 409, show conflict guidance: use a new eventId or revert payload; display status 409 and backend errorCode.
   5. For 5xx/timeout, show retry guidance and preserve user inputs for resubmission.
8. User may adjust inputs and resubmit; Submit remains disabled only while request is in flight.

## Notes
- Permission gating is deny-by-default for both screen access and submit action; enforce separately.
- Raw payload visibility is restricted; ensure only authorized users can view/edit payload content (or mask/disable payload display per permission).
- Client-side validation requirements:
  - eventId (if provided) must be UUIDv7 (Decision AD-006).
  - payload must be syntactically valid JSON; UI does not validate eventType-specific schema (backend authoritative).
  - eventTimestamp must be ISO-8601 parseable; send as entered (or normalized ISO string if using a picker).
- Observability:
  - Propagate W3C Trace Context traceparent; generate if absent (Decision AD-008).
  - Show copyable identifiers: eventId, correlationId (if present), traceparent used.
- Error handling must map backend canonical error shape to global + field errors when keys match (SD-UX-ERROR-MAP-01; Decision AD-013).
- Result normalization:
  - Screen expects a normalized service return: success map, error map, and traceparent used/propagated.
  - Display backend-authoritative outcome enum if provided (Decision AD-007).
- Service interaction:
  - Moqui screen action calls a Moqui service that performs outbound HTTP POST to the backend sync ingestion endpoint (path TBD).
  - Required headers include Content-Type and traceparent; auth headers per platform standard.
- UX constraints:
  - Disable submit during request; preserve inputs on server errors to support retry.
  - Provide clear guidance for 409 conflicts and 5xx/timeouts; avoid leaking sensitive info on auth failures.
