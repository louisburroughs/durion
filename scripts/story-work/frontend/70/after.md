## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting: Display Invoice Payment Status & Posting Outcome from Payment/Posting Events

### Primary Persona
POS cashier / manager (invoice viewer), and reconciliation staff (finance ops) using the POS UI to understand invoice payment state.

### Business Value
Ensure invoice status shown in the POS is accurate and traceable after payment outcomes, reducing customer balance disputes and enabling quick reconciliation when postings fail.

---

## 2. Story Intent

### As a / I want / So that
- **As a** POS user viewing an invoice,
- **I want** to see the current payment status and latest payment/posting outcome details (transaction references and posting errors),
- **So that** I can confirm whether an invoice is paid/partially paid/unpaid/failed and know if accounting posting succeeded or needs follow-up.

### In-scope
- Moqui screens to **view** invoice payment status and related payment/posting references.
- UI refresh behavior to reflect latest status (manual refresh + on-navigation reload).
- Display of idempotency/transaction references as read-only audit context.
- Error/empty states when events are missing or backend indicates processing/posting failure.

### Out-of-scope
- Applying payments from the UI (payment capture/void/refund).
- Editing invoice totals, issuing invoices, or triggering accounting postings.
- Defining GL account mappings or posting rules (backend/config).

---

## 3. Actors & Stakeholders
- **Cashier / POS user:** needs simple “Paid vs Not Paid” clarity and references.
- **Store manager:** needs visibility into failed payments/posting errors.
- **Finance / reconciliation:** needs transaction IDs, correlation IDs, and failure markers.
- **Accounting backend service:** system of record for invoice financial/payment status and posting outcomes.
- **Payment service/gateway:** source of payment outcomes (not directly operated from this UI story).

---

## 4. Preconditions & Dependencies
- An invoice exists in backend with an identifier (`invoiceId` or `invoiceNumber`) and payment-related fields.
- Backend exposes an API/view entity that the frontend can call to retrieve:
  - `invoiceStatus` (Paid/PartiallyPaid/Unpaid/Failed/Chargeback if applicable)
  - amounts (total/paid/outstanding) in minor units
  - latest payment transaction reference(s)
  - posting status/error indicator(s) (e.g., `postingError`, posting intent status)
- User is authenticated and authorized to view invoices and payment status.

**Dependency (blocking):** exact Moqui service names, screen locations, and entity/view names are not provided in inputs (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an existing **Invoice detail** screen in POS, add a “Payment & Posting” section/tab.
- If no invoice detail exists, create an invoice view screen accessible via a menu entry under Accounting/Invoices (blocking decision).

### Screens to create/modify
- **Modify**: `InvoiceDetail` screen (or equivalent) to include payment/posting status panel.
- **Create (if needed)**: a sub-screen `InvoicePaymentStatus` embedded in invoice detail for modularity.

### Navigation context
- Route expects `invoiceId` (preferred) as path/parameter.
- Provide a “Back to invoices” navigation consistent with existing POS patterns.

### User workflows
**Happy path**
1. User opens invoice detail.
2. UI loads invoice header + payment status block.
3. UI displays:
   - Payment Status (Paid/PartiallyPaid/Unpaid/Failed)
   - Paid amount and outstanding amount
   - Last transaction reference
   - Posting outcome (Posted/Pending/Failed) if provided
4. User optionally clicks “Refresh” to re-load latest status.

**Alternate paths**
- Invoice exists but has no payments yet → show Unpaid + “No payments recorded”.
- Payment recorded but posting pending → show status + “Posting pending”.
- Posting failed → show prominent warning + reconciliation hint + correlation/intent IDs (if available).
- Unauthorized → show access denied.

---

## 6. Functional Behavior

### Triggers
- Screen load / parameter change (navigating to a different invoice).
- User clicks “Refresh”.

### UI actions
- Read-only display of payment/posting fields.
- Copy-to-clipboard for transaction ID / correlation ID (optional; if not supported by conventions, omit).

### State changes (frontend)
- Local loading states: `loading`, `loaded`, `error`.
- No domain state mutation from frontend in this story.

### Service interactions
- Call a backend read service to fetch an **InvoiceStatusView** (or equivalent view) by `invoiceId`.
- If backend separates posting outcomes, call a second service to fetch latest posting intent/outcome by invoiceId (blocking until confirmed).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `invoiceId` parameter is required; if missing/blank → show validation error and block calls.
- If backend returns invoice not found → show “Invoice not found”.

### Enable/disable rules
- “Refresh” enabled when `invoiceId` present; disabled while `loading=true`.

### Visibility rules
- Show **Posting Failure** banner only when backend indicates `postingError=true` or latest posting status = Failed.
- Show transaction reference section only when at least one reference exists.

### Error messaging expectations
- Map backend error codes (if provided) into user-safe messages:
  - 401/403 → “You don’t have access to view payment status for this invoice.”
  - 404 → “Invoice not found.”
  - 409 (conflict/concurrency) → “Invoice status is being updated. Refresh in a moment.”
  - 5xx/timeouts → “Unable to load payment status. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Invoice` (read-only in this story)
- `InvoiceStatusView` (view entity / DTO; read-only)
- Optional: `PostingIntent` / `PostingOutcomeView` (read-only)

### Fields (type, required, defaults)
**Required to display core status**
- `invoiceId` (string/UUID) — required
- `invoiceStatus` (enum string: `Paid|PartiallyPaid|Unpaid|Failed` (+ `Chargeback` if present)) — required
- `currencyUomId` (string, e.g., USD) — required
- `totalAmountMinor` (integer) — required
- `paidAmountMinor` (integer) — required
- `outstandingAmountMinor` (integer) — required

**Optional audit/trace fields**
- `lastPaymentTransactionId` (string) — optional
- `lastPaymentOccurredAt` (datetime) — optional
- `postingError` (boolean) — optional
- `postingIntentId` (string/UUID) — optional
- `postingStatus` (enum `Pending|Posted|Failed`) — optional
- `postingLastAttemptAt` (datetime) — optional
- `correlationId` (string) — optional

### Read-only vs editable by state/role
- All fields read-only in this story for all roles.

### Derived/calculated fields (UI-only)
- Display money amounts formatted from minor units using `currencyUomId`.
- Display a status badge derived from `invoiceStatus`.
- Display a posting badge derived from `postingStatus` / `postingError`.

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
1. `InvoiceStatusView.get` (name TBD)
   - Input: `invoiceId`
   - Output: fields listed above
   - Errors: 401/403/404/409/5xx

2. (Optional) `PostingOutcome.getLatestByInvoice` (name TBD)
   - Input: `invoiceId`
   - Output: posting intent/outcome fields
   - Errors: same mapping; 404 treated as “no posting record yet”

### Create/update calls
- None.

### Submit/transition calls
- None.

### Error handling expectations
- Frontend must not retry mutations (none). For loads, allow user-initiated retry via Refresh.
- Preserve correlation identifiers from backend response headers/body if available and display only where intended (see Open Questions).

---

## 10. State Model & Transitions

### Allowed states (display-only)
- Invoice payment status:
  - `Unpaid`
  - `PartiallyPaid`
  - `Paid`
  - `Failed`
  - `Chargeback` (only if backend uses it)

- Posting outcome status (if exposed):
  - `Pending`
  - `Posted`
  - `Failed`

### Role-based transitions
- None in UI (no transitions triggered).

### UI behavior per state
- **Paid:** show “Paid” status; outstanding = 0 (display what backend returns).
- **PartiallyPaid:** show partial; show paid/outstanding.
- **Unpaid:** show unpaid; hide transaction refs if none.
- **Failed:** show failed; show last transaction ref if present; show guidance “payment failed”.
- **Chargeback:** show chargeback warning; show original transaction ref if present.
- **Posting Failed:** show posting failure banner + reconciliation fields if present.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing invoiceId → inline error and no call.

### Concurrency conflicts
- Backend returns 409 indicating invoice is mid-update → show non-blocking message and keep last loaded data if available; allow Refresh.

### Unauthorized access
- 401 → redirect to login (if app pattern exists) or show session expired.
- 403 → show access denied.

### Empty states
- No payments recorded: show “No payments recorded yet.”
- No posting record yet: show “Posting not started/pending” (only if backend distinguishes; otherwise hide section).

---

## 12. Acceptance Criteria

### Scenario: View a fully paid invoice
**Given** I am an authenticated user with permission to view invoices  
**And** an invoice exists with `invoiceStatus=Paid`, `paidAmountMinor=totalAmountMinor`, `outstandingAmountMinor=0`  
**When** I open the invoice detail screen for that invoice  
**Then** the UI displays Payment Status = “Paid”  
**And** displays paid/outstanding amounts formatted in the invoice currency  
**And** displays the last payment transaction reference if provided by backend.

### Scenario: View a partially paid invoice
**Given** an invoice exists with `invoiceStatus=PartiallyPaid` and `outstandingAmountMinor > 0`  
**When** I view the invoice  
**Then** the UI displays Payment Status = “Partially Paid”  
**And** shows both paid and outstanding amounts.

### Scenario: View an unpaid invoice with no payments
**Given** an invoice exists with `invoiceStatus=Unpaid`  
**And** no transaction reference is available  
**When** I view the invoice  
**Then** the UI shows “Unpaid”  
**And** shows “No payments recorded yet”  
**And** does not show a blank transaction reference row.

### Scenario: Payment failed
**Given** an invoice exists with `invoiceStatus=Failed`  
**When** I view the invoice  
**Then** the UI shows Payment Status = “Failed”  
**And** displays any available transaction reference and failure indicator fields returned by backend.

### Scenario: Posting failed indicator shown
**Given** an invoice status response includes `postingError=true` (or `postingStatus=Failed`)  
**When** I view the invoice  
**Then** the UI displays a “Posting failed” banner/alert  
**And** displays posting identifiers (postingIntentId/correlationId) if provided  
**And** the UI does not allow editing these values.

### Scenario: Manual refresh updates display
**Given** I am viewing an invoice detail page  
**When** I click “Refresh”  
**Then** the frontend re-calls the load service(s)  
**And** updates the displayed payment/posting status to the latest returned values.

### Scenario: Invoice not found
**Given** I navigate to an invoiceId that does not exist  
**When** the backend returns 404  
**Then** the UI shows “Invoice not found”  
**And** does not display stale invoice payment data.

### Scenario: Unauthorized access
**Given** I lack permission to view invoices  
**When** I navigate to an invoice detail page  
**Then** the UI shows “Access denied” (403) or login flow (401) per application conventions  
**And** no invoice data is displayed.

---

## 13. Audit & Observability

### User-visible audit data
- Show read-only identifiers useful for audit/reconciliation if returned:
  - last payment `transactionId`
  - posting intent ID
  - correlation ID
  - timestamps for last payment and last posting attempt

### Status history
- If backend provides status history, display a minimal “Recent status changes” list; otherwise omit (blocking).

### Traceability expectations
- Every displayed transaction/posting identifier should be copyable and clearly labeled.

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load should not block rendering of the rest of invoice detail; payment block may show skeleton/loading state.
- **Accessibility:** status indicators must be conveyed via text, not color alone; keyboard navigation for Refresh.
- **Responsiveness:** payment/posting section must fit mobile POS layouts.
- **i18n/timezone/currency:** format money by currency; format datetimes in user locale/timezone (only if the app already does so; otherwise blocking).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit “No payments recorded yet” when no payment references are returned; qualifies as UI ergonomics and does not change domain behavior; impacts UX Summary, Error Flows.
- SD-UX-LOADING-SKELETON: Use a loading/skeleton state while fetching; qualifies as UI ergonomics; impacts UX Summary, Functional Behavior.
- SD-ERR-HTTP-MAP: Standard HTTP error-to-message mapping (401/403/404/409/5xx) for read calls; qualifies as standard error-handling mapping; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Which Moqui screen(s) should be modified/extended?** Provide the canonical invoice detail screen path/name in `durion-moqui-frontend` (e.g., `apps/pos/screen/...`) and navigation pattern.
2. **What are the exact backend service names and response fields?** We need the Moqui service endpoints (or REST paths) for:
   - invoice payment/status view by `invoiceId`
   - latest posting outcome by `invoiceId` (if separate)
3. **What is the authoritative enum set for invoice payment status in this system?** Inputs mention Paid/PartiallyPaid/Unpaid/Failed; backend reference includes Chargeback. Confirm allowed values and display labels.
4. **Authorization/permissions:** what permission(s) gate viewing invoice payment and posting outcome? (We must not infer security boundaries.)
5. **Posting outcome model exposure:** should UI show `postingIntentId`, `correlationId`, retry counts/attempt timestamps, or only a boolean `postingError`? Confirm what is safe for POS users vs finance-only roles.
6. **Do we need status history in UI?** If yes, what entity/service provides it and how many entries to show?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/70  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As the **POS system**, I want to update invoice payment status so that customer balances are accurate.

## Details
- Map payment outcomes to invoice statuses: Paid/PartiallyPaid/Unpaid/Failed.
- Include transaction refs.
- Idempotent updates.

## Acceptance Criteria
- Status updates emitted for accounting.
- Retries and idempotency supported.
- UI reflects latest status.

## Integrations
- POS emits PaymentApplied events; accounting responds with posting confirmation events.

## Data / Entities
- PaymentAppliedEvent, InvoiceStatusView, IdempotencyKey

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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