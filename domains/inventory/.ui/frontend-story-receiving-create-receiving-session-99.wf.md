# [FRONTEND] [STORY] Receiving: Create Receiving Session from PO/ASN
## Purpose
Enable receivers to create a new Receiving Session from an existing PO or ASN by entering or scanning a source identifier. The screen validates the identifier for receivability and provides clear inline feedback for common failures (not found, not receivable, missing identifier). On success, it creates a session shell with pre-populated expected lines and navigates directly to the session detail view, showing auditable metadata returned by the service.

## Components
- Page header: “Create Receiving Session”
- Identifier input field (single field supports manual typing and barcode scan-as-keyboard)
- Inline helper/error text under identifier field
- Primary button: “Create Session”
- Secondary button: “Cancel”
- Loading/in-flight state indicator (spinner on button and/or page)
- Page-level alert/banner for non-field-specific errors
- Timeout message panel with “Retry” action (shown after 8s)
- “Technical details” expandable area in error state (shows correlation ID when available)

## Layout
- Top: Page header + brief instruction text (“Enter or scan PO/ASN identifier”)
- Main (center): Identifier field with inline helper text directly beneath
- Main (below field, right-aligned): [Cancel] [Create Session]
- Below actions: Page-level alert area (only visible on errors not tied to the field)
- Bottom: Collapsible “Technical details” (visible when an error occurs; shows correlation ID)

## Interaction Flow
1. User navigates to Receiving → Create Session screen.
2. User enters or scans a PO/ASN identifier into the single identifier field (value trimmed on submit).
3. Submit triggers:
   1. User clicks “Create Session”, or
   2. User presses Enter while focused in the identifier field (only if non-empty).
4. While request is in-flight:
   1. Disable “Create Session” (and optionally the input).
   2. Show loading indicator.
5. Success response:
   1. Create session is returned with sessionId and expected lines.
   2. Navigate to Receiving Session Detail view for returned sessionId.
   3. Session detail view displays expected lines as provided (frontend renders returned lines; no remapping).
   4. Display auditable metadata when available (session id, source doc/type, entry method, timestamps/creator if returned).
6. Error: Missing identifier (blind receiving blocked):
   1. Block submission.
   2. Show inline error under identifier (“Identifier is required” or backend-equivalent).
   3. Stay on create screen; no navigation.
7. Error: Not found:
   1. Show inline error under identifier (e.g., “Source document PO-999 not found.” or backend message).
   2. Stay on create screen; no navigation.
8. Error: Not receivable (closed/fully received):
   1. Show inline error under identifier (backend-provided message preferred).
   2. Stay on create screen; no navigation.
9. Error: Unauthorized/forbidden:
   1. Show page-level alert with permission/forbidden message.
   2. Show correlation ID in “Technical details” when available.
   3. Stay on create screen; no navigation.
10. Error: Other/unmapped errors:
   1. Show page-level alert with backend message (or generic fallback).
   2. Include correlation ID in “Technical details” when available.
11. Timeout handling:
   1. If request exceeds 8 seconds, show timeout message with “Retry”.
   2. Do not auto-retry; user must click Retry to re-submit the same identifier.
12. Cancel:
   1. User clicks “Cancel”.
   2. Navigate back to Receiving landing screen; no changes persisted.

## Notes
- Entry method support: manual typing and barcode scan into the same input (scanner behaves like keyboard input). Default entryMethod for v1 is Manual unless a scanner utility is confirmed and explicitly used.
- Validation feedback mapping:
  - If backend error schema includes field-level error for identifier, render inline helper text on the identifier input.
  - Otherwise render a page-level alert.
  - Always show correlation ID in “Technical details” when available (read from response body or header).
- Create request expectations:
  - identifier (required, trimmed)
  - sourceType (optional/derived; only send if backend requires)
  - entryMethod (required; default Manual in v1)
- Create response expectations (for downstream session detail):
  - sessionId, source identifier/type, entry method, status (expected initial status), timestamps/creator if returned
  - expected lines list with item identifiers/descriptions (if provided), expected qty, received qty default 0
- Backend/proxy contract is marked blocking in the story: endpoints/schemas for create-from-source and load session detail must be confirmed.
- Authorization is blocking: confirm canonical permission strings for viewing create screen, creating a session, and viewing session detail (comment suggests inventory:receiving:create and inventory:receiving:execute).
