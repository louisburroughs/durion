# [FRONTEND] [STORY] Events: Define Canonical Accounting Event Envelope
## Purpose
Provide a Moqui-native UI surface to view (and optionally validate) the Canonical Accounting Event Envelope contract so producers/consumers can align on required fields, data types, and traceability requirements. The screen should clearly communicate correlation/trace headers, identifier strategy (UUIDv7), ingestion monitoring linkage guidance, and ingestion status taxonomy. It must also support validating a user-supplied envelope JSON with clear, non-destructive error handling.

## Components
- Page header: “Canonical Accounting Event Envelope” + breadcrumb (Accounting → Integrations → Event Contracts → …)
- Permission-gated notice area (e.g., “Requires permission: CONTRACT_VIEW”; raw JSON requires elevated permission)
- Contract summary panel (read-only)
  - Correlation & trace headers (required/optional)
  - Identifier strategy (UUIDv7)
  - Raw payload visibility policy (curated by default; raw gated; no cache/log)
  - Ingestion monitoring linkage guidance (canonical posting reference primary; secondary reference)
  - Ingestion status taxonomy (two enum groups; backend-owned)
- Envelope field definitions table (read-only)
  - Field name, type, required/optional, notes/examples
- Validation panel (optional)
  - JSON textarea input
  - “Validate” button
  - Inline JSON parse error message (client-side)
  - Validation results summary (valid boolean)
  - Error list (from backend)
  - Optional “details map” viewer (read-only, no inferred meanings)
  - “Clear” / “Reset” action to empty or restore last payload
- Raw JSON viewer (permission-gated)
  - Toggle: “Show raw JSON” (off by default)
  - Warning banner: “Do not paste secrets; raw JSON not cached or logged”
- Footer actions/links
  - Link to related ingestion screens (Ingestion List, Event Detail, Manual Review, Retry History) as navigation shortcuts

## Layout
- Top: Breadcrumb + page title + small “Contract version/last updated” placeholder (if available)
- Main (two-column on desktop; stacked on mobile):
  - Left/Main: Contract summary panel → Envelope field definitions table
  - Right/Side: Validation panel (textarea + results) + Raw JSON toggle/viewer (if permitted)
- Bottom: Navigation shortcuts to ingestion-related screens

## Interaction Flow
1. User with contract-view permission navigates to Accounting → Integrations → Event Contracts → Canonical Accounting Event Envelope.
2. Screen loads and displays contract summary:
   1) Identifiers are UUIDv7.
   2) Correlation uses required trace header and optional trace header.
   3) Ingestion status taxonomy is shown as backend-owned enums (do not infer meanings beyond labels).
   4) Canonical posting reference is primary; secondary reference is secondary when present.
3. User reviews “Envelope field definitions” table:
   1) Required fields are visually marked (e.g., “Required” badge).
   2) Optional fields show examples/notes where provided (e.g., example values; “backend-owned enum; do not infer”).
4. (Optional) User validates an envelope:
   1) User pastes JSON into textarea and clicks “Validate”.
   2) If JSON is invalid: show inline parse error; do not call backend; keep textarea content.
   3) If JSON is valid: call backend validation; display response:
      - valid = true/false
      - error list (if any), preserving payload for correction
      - if response includes a details map, show it as additional context without inventing meanings.
5. User iterates on validation failures:
   1) Fix JSON and re-validate; previous errors are replaced by latest results.
   2) User can click “Clear/Reset” to remove payload and results.
6. Raw JSON visibility:
   1) Default view shows curated summaries only.
   2) If user has raw-view permission, they may toggle “Show raw JSON”; otherwise show a disabled control with permission hint.
   3) When raw is shown, display warning that raw JSON must not be cached or logged.

## Notes
- Raw payload visibility policy: curated summaries by default; raw JSON display is permission-gated; raw must not be cached or logged.
- Correlation & trace headers must explicitly show which is required vs optional (per decision AD-008).
- Identifier strategy must explicitly state UUIDv7 (per decision AD-006).
- Ingestion monitoring linkage guidance must state canonical posting reference is primary; secondary reference is secondary (per decision AD-011).
- Ingestion status taxonomy must list both enum groups and indicate they are backend-owned (per decision AD-007); UI must not infer or invent semantics beyond provided labels.
- Validation UX constraints:
  - Invalid JSON: inline parse error; no backend call.
  - Backend validation errors: list errors; keep payload for correction.
  - If errors include a details map, display as-is; do not invent meanings.
- Provide navigation shortcuts to related ingestion UI areas (Ingestion List with filtering, Event Detail with audit trail, Manual Review, Retry History) but keep this screen focused on the contract.
- Ensure the contract screen is readable as a “single source of truth” for producers/consumers (required/optional, types, examples, and traceability requirements).
