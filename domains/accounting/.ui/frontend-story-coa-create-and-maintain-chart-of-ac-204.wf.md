# [FRONTEND] [STORY] CoA: Create and Maintain Chart of Accounts
## Purpose
Enable authorized users to manage a Chart of Accounts by listing, viewing, creating, updating (limited fields), and deactivating GL accounts with effective dating. Provide a server-paginated list with supported filters/search, and a detail view with audit metadata. Ensure all backend validation and policy errors are surfaced clearly while preserving user inputs.

## Components
- Global header with page title “Chart of Accounts” and primary action button “Create GL Account”
- List view
  - Filter controls: Account Type (ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE), Effective dating window (from/to), optional Status chips (Active/Inactive/NotYetActive; derived unless backend supports filtering)
  - Search input (by account code and/or name only if API supports)
  - Results table/list: Account Code, Name, Type, Derived Status, Active From, Active Thru (if any)
  - Pagination controls (server-side): page next/prev, page size selector
  - Empty state and loading state
  - Error banner with retry
- Detail view
  - Read-only field display: accountCode, accountName, accountType, description, activeFrom, activeThru, derived status
  - Audit metadata panel: createdAt, updatedAt, createdBy, updatedBy (if provided)
  - Actions: “Edit” (toggle), “Save”, “Cancel”, “Deactivate”
  - Inline field error messages + top-level error banner
- Create view (or create modal routed page)
  - Form fields: accountCode, accountName, accountType, description, activeFrom
  - Actions: “Create”, “Cancel”
  - Inline validation errors (including duplicate code) + banner for non-field errors
- Deactivate modal/dialog
  - Confirmation text + effective deactivation datetime input (activeThru)
  - Actions: “Confirm Deactivate”, “Cancel”
  - Error display (policy violations, invalid dates, auth)

## Layout
- Top: Page header (Title + “Create GL Account” button)
- Main (List): Filters/search row above results table; pagination at bottom
- Main (Detail): Two-column feel—left: account fields; right: audit metadata; actions aligned top-right of detail header
- Modal overlay: Deactivate confirmation centered over detail

## Interaction Flow
1. List GL accounts
   1) User opens CoA list; UI calls backend list API with page + pageSize and any supported filters/search.
   2) UI renders results table and derived status (Active/Inactive/NotYetActive) based on activeFrom/activeThru when server-side status filtering is not supported.
   3) User changes page or page size; UI re-requests server page (no client-side full dataset paging).
2. Filter/search
   1) User selects Account Type and/or effective dating window; UI requests list with those parameters.
   2) User enters search term; UI only sends search if API supports searching by code/name; otherwise search control is hidden/disabled.
3. View GL account details
   1) User selects a row; UI navigates to detail route using glAccountId and loads via get-by-id API.
   2) Detail shows fields read-only by default plus audit metadata when available.
   3) If 404: show safe “Not found” message with “Back to list”.
   4) If 403/404 must be treated similarly: show generic access/not-found message without confirming existence.
4. Create GL account
   1) User clicks “Create GL Account”; UI shows create form.
   2) User submits; UI calls create API.
   3) On success: navigate to new account detail and display persisted values + audit metadata.
   4) On duplicate code (409 or validation errorCode): show inline error on accountCode and preserve all inputs.
   5) On 400/422: map field errors inline when provided; otherwise show banner; preserve inputs.
   6) On 401: redirect to login; on 5xx/timeout: show retry and preserve inputs.
5. Edit allowed fields (name/description only)
   1) User clicks “Edit”; UI enables accountName and description only; other fields remain read-only.
   2) User clicks “Save”; UI calls patch/update API with allowed fields only.
   3) On success: return to read-only view with updatedAt/updatedBy refreshed if returned.
   4) On validation/auth errors: show inline/banner per error mapping; keep user edits.
6. Deactivate with effective date
   1) From an active account, user clicks “Deactivate”; modal opens with effective datetime input.
   2) User confirms; UI calls deactivate action (preferred) or update activeThru per backend contract.
   3) On success: detail shows activeThru set; derived status updates to Inactive once effective date passes.
   4) Deactivate button disabled if activeThru exists and is in the past (already inactive).
   5) If activeThru exists and is in the future: show “Deactivation scheduled” and do not offer changing the date unless backend explicitly supports it.
   6) If backend policy blocks deactivation (e.g., usage/non-zero balance): show stable error message/code in banner/modal.

## Notes
- Server-side pagination is required; include page size control (SD-UX-PAGINATION).
- Filters/search must reflect backend capabilities: do not claim server-side status filtering or search unless supported; derived status chips are UI-only when necessary.
- Account types are canonical enums: ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE.
- Create-only fields: accountCode, accountType, activeFrom; do not allow editing these post-create.
- activeThru should not be freely editable; only set via Deactivate flow unless backend specifies otherwise.
- Error handling mapping:
  - 400/422: inline field errors when field keys provided; else banner
  - 409: conflict banner; duplicate code inline on accountCode
  - 401: redirect to login
  - 403: not-authorized callout; consider treating 403/404 similarly for safe messaging
  - 5xx/timeout: retry affordance; preserve inputs
- Editing policy for inactive accounts is unclear; default to disabling “Edit” when inactive unless backend contract confirms edits are allowed (TODO).
- Service contracts/endpoints are TBD; UI should be structured to call list/get/create/patch/deactivate services and display stable backend error codes/messages.
