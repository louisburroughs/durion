# [FRONTEND] [STORY] Promotion: Enforce Idempotent Promotion
## Purpose
Ensure the “Promote Estimate to Work Order” action behaves idempotently from the user’s perspective, so repeated clicks or retries safely resolve to a single canonical Work Order. Provide clear UI states for success, retryable failures (timeouts/5xx), non-retryable failures, and corrupted-link scenarios where a promotion record exists but the Work Order is missing. Guide users with safe retry messaging, concurrency-safe controls, and a canonical Work Order link once known.

## Components
- Estimate header/summary (includes estimate status display)
- Primary action button: “Promote to Work Order”
- Inline validation messages (missing estimateId / missing snapshotVersion)
- Promotion status area (panel/banner)
  - Loading indicator + “Promoting…” text
  - Success banner with canonical Work Order reference and “Open Work Order” link/button
  - Retryable error banner with “Retry” guidance and optional “Try again” action
  - Non-retryable error banner with escalation/admin message
  - Correlation/Idempotency key display (when relevant for support)
- Optional secondary action: “Copy correlation id” (or copyable text field)
- Navigation link to canonical Work Order screen (by workOrderId)

## Layout
- Top: Estimate header/summary + status
- Main: Actions row (Promote button) directly under header
- Main (below actions): Promotion result panel/banner (dynamic: idle/loading/success/error)
- [Estimate Header]
  [Promote Button]  →  [Promotion Result Banner/Panel]

## Interaction Flow
1. Idle / ready state
   1. Display “Promote to Work Order” button on the Estimate view.
   2. Enable button only if estimateId and snapshotVersion are present in UI state; otherwise show inline blocking message(s):
      1. Missing estimateId: “Estimate not loaded; refresh and try again.”
      2. Missing snapshotVersion: “Estimate snapshot version is required to promote.”
2. Primary promotion (first attempt)
   1. User clicks “Promote to Work Order”.
   2. UI generates a new idempotency key (UUID) for this attempt and stores it in component state until completion.
   3. Immediately disable the Promote button; show spinner and “Promoting…” in the promotion panel.
   4. Send promotion mutation with required body (estimateId, snapshotVersion) and include the Idempotency-Key header.
   5. On 200 success with canonical workOrderId (and optional workOrderNumber/reference):
      1. Store returned canonical Work Order reference.
      2. Show success banner: “Promoted to Work Order” + Work Order reference + “Open Work Order” link navigating to the canonical Work Order screen.
      3. Keep Promote disabled if returned data indicates the estimate is already promoted; otherwise allow re-click (must still resolve idempotently).
3. Retry behavior (unknown outcome: timeout/5xx)
   1. If request fails with timeout/network error/5xx:
      1. Show retryable error banner: “Promotion status is unknown. It’s safe to retry.”
      2. Re-enable Promote (or show a “Retry promotion” action).
      3. On retry, reuse the same idempotency key for the same attempt until a definitive outcome is received.
4. Retry returns existing Work Order (idempotent retrieval)
   1. User retries after an unknown outcome.
   2. Backend returns success with an existing canonical Work Order reference.
   3. UI treats this as final success and renders the same success banner/link as if it were newly created.
5. Non-retryable failure (validation/authorization/not promotable)
   1. If backend rejects promotion (e.g., estimate not in promotable state, auth failure, other non-retryable errors):
      1. Show non-retryable error banner with backend message when available.
      2. Keep Promote disabled (or disabled until state changes/reload).
      3. Do not mutate estimate state locally.
      4. Show correlation id when present for support.
6. Corrupted link case (promotion record exists but Work Order missing)
   1. If backend indicates promotion exists but Work Order cannot be found (or returns a specific “missing WO” condition):
      1. Show non-retryable escalation banner: “Promotion record exists but Work Order is missing. Please contact an administrator/support.”
      2. Keep Promote disabled to prevent repeated attempts.
      3. Display correlation id/idempotency key for troubleshooting.

## Notes
- Idempotency requirement: any successful response containing a canonical Work Order reference is final, regardless of whether it was created or retrieved.
- Concurrency safety: disable Promote immediately on click; show progress; prevent double-submit; allow safe retry only for retryable/unknown outcomes.
- Header requirement: promotion mutation must include Idempotency-Key header (UUID) per attempt; reuse the same key only when retrying the same attempt.
- Preconditions: estimate must be promotable (typically APPROVED; enforced by backend); snapshotVersion must be available; user must be authenticated/authorized.
- Post-promotion UX: always display canonical Work Order reference and provide navigation to the canonical Work Order screen.
- Error mapping: classify errors into retryable (timeouts/5xx/network) vs non-retryable (backend validation/state/auth); show actionable messaging accordingly.
- Supportability: surface correlation id (and/or idempotency key) in non-retryable/escalation states to aid admin/support investigation.
