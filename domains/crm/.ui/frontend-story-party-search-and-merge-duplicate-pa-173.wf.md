# [FRONTEND] [STORY] Party: Search and Merge Duplicate Parties (Basic)
## Purpose
Enable authorized users to find potential duplicate Parties via a criteria-based search and merge exactly two records into a single survivor. The UI guides users through selecting two candidates, choosing the survivor, providing justification, acknowledging irreversibility, and confirming success. It also handles empty/insufficient results, invalid selections, and backend merge/navigation conflicts (including already-merged parties).

## Components
- Page header: “Merge Duplicate Parties”
- Inline guidance text: “Select exactly two parties to merge.”
- Permission/role gate messaging (read-only/disabled state if forbidden)
- Search form
  - Name input
  - Email input
  - Phone input
  - Search button
  - Clear/reset button
  - Validation message: “Provide at least one search criterion.”
- Results list/table (paginated)
  - Select checkbox per row
  - Columns: Display Name, Party ID (UUID), Party Type, Email (optional), Phone (optional)
  - Merged indicator (non-selectable row if `mergedIntoPartyId` present)
  - Empty state panel (0/1 results guidance)
  - Pagination controls (page, page size)
- Selection summary + CTA bar
  - Selected count indicator (e.g., “1 of 2 selected”)
  - Helper message when >2 selected
  - Primary button: “Continue to Merge” (disabled unless exactly 2 selected)
- Merge confirmation screen
  - Two party summary cards (A and B) with same key fields as results
  - Survivor selection control (radio buttons: “Keep as survivor”)
  - Justification textarea (required)
  - Acknowledge checkbox: “I understand this merge is irreversible.” (required)
  - Buttons: Back, Merge Parties (primary), Cancel
  - Error banner area (validation/forbidden/conflict)
- Merge success screen
  - Success banner/message
  - Display: survivorPartyId, sourcePartyId
  - Merge audit reference (if provided)
  - Button/link: “View Survivor Party”
  - Button/link: “Start New Merge”
- Merged-party read handling (party detail navigation)
  - Banner: “This party was merged.”
  - Button/link: “Go to Survivor” (uses `mergedIntoPartyId` from 409 payload)
  - Link: “Back to Merge Search”

## Layout
- Top: Page header + brief instruction line
- Main: Search form (top) → Results table (middle) → CTA bar (bottom)
- Confirm: Two side-by-side party cards (top) → survivor radio + justification + acknowledge (middle) → actions + banner area (bottom)
- Success: Centered success panel with IDs + actions

## Interaction Flow
1. User opens “Merge Duplicate Parties” screen.
2. UI checks permission context:
   1) If 403/forbidden for merge/search, show permission error and disable merge actions.
3. User attempts search with all criteria empty:
   1) Block search; show inline validation “Provide at least one search criterion.”
4. User enters name/email/phone and clicks Search:
   1) Call Party search with pagination/sort defaults.
   2) Render results list with partyId + display name (and optional email/phone).
   3) By default, do not include merged parties; if a row includes `mergedIntoPartyId`, render as “Merged” and non-selectable.
5. Search returns 0 results:
   1) Show empty state guidance (how to broaden criteria); keep search form visible.
6. Search returns 1 result:
   1) Show guidance that two parties are required; “Continue to Merge” remains disabled.
7. User selects parties from results:
   1) Selecting 1 party keeps “Continue to Merge” disabled and shows “Select exactly two parties to merge.”
   2) Selecting exactly 2 distinct parties enables “Continue to Merge.”
   3) Selecting a 3rd party disables CTA and shows helper message indicating only two can be selected.
8. User clicks “Continue to Merge”:
   1) Navigate to confirmation screen with the two selectedPartyIds.
   2) If required state is missing (direct navigation), redirect back to search and show banner “Select two parties to merge.”
9. Confirmation screen:
   1) Display both party summaries.
   2) User selects survivor (A or B); the other becomes source.
   3) User enters required justification and checks irreversible acknowledgment.
   4) Merge button remains disabled until survivor + justification + acknowledgment are complete.
10. User submits merge:
   1) On success, show success screen with survivorPartyId, sourcePartyId, and mergeAuditId (if provided).
   2) “View Survivor Party” navigates to survivor detail.
11. Error handling on submit (stay on confirm screen unless redirected):
   1) 400 validation: show error banner and field-level errors (e.g., missing justification); keep inputs.
   2) 403 forbidden: show permission error; disable merge action.
   3) 404 not found: show banner; offer “Back to Search.”
   4) 409 conflict:
      - If PARTY_MERGED with `mergedIntoPartyId`, show banner and offer redirect to survivor.
      - Otherwise, show “Conflict—refresh and retry” guidance.
   5) 5xx: show generic failure banner; do not assume partial changes.
12. Merged read path (party detail navigation):
   1) If opening a party returns 409 PARTY_MERGED, show “This party was merged” and provide redirect to `mergedIntoPartyId`.

## Notes
- Search is criteria-required (no browse-all); at least one of name/email/phone must be provided before calling search.
- Selection constraint: exactly two distinct parties; merged rows (when present) must be non-selectable and visually indicated.
- Confirmation requirements: survivor selection + justification + irreversible acknowledgment are mandatory before enabling merge submission.
- Keep user on confirmation screen for backend validation/conflict errors; preserve entered justification/selection.
- Direct navigation safeguards: if confirm/success screens lack required state (selectedPartyIds), redirect to search with banner “Select two parties to merge.”
- Conflict handling: support 409 PARTY_MERGED redirect using `mergedIntoPartyId` (DECISION-INVENTORY-014).
- Data display minimum per party: partyId (UUID), partyType (enum), display name (preferred single field or person/org fallback), optional email/phone, optional mergedIntoPartyId.
- TODO (blocking): confirm exact backend service/endpoints and DTO schemas for party search and merge command, including response fields for mergeAuditId and redirect/alias fields.
