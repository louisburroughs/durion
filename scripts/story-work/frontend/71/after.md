STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:draft

### Recommended
- agent:billing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

**Title:** [FRONTEND] Payment: Print/Email Receipt and Store Reference (Post-Capture + Reprint)

**Primary Persona:** Cashier (secondary: Supervisor/Manager, Customer Service)

**Business Value:** Provide customers immediate proof of payment (printed or emailed), ensure auditable linkage between payment/invoice/receipt, and support controlled reprints without financial or privacy risk.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Cashier  
- **I want** the POS to generate a receipt after a successful payment capture and allow printing and optional emailing  
- **So that** customers receive proof of payment and the business retains a traceable receipt reference for audit and reprint.

### In-scope
- Showing receipt actions immediately after successful capture:
  - auto-generate receipt (or fetch generated receipt)
  - print receipt
  - optionally email receipt
  - allow “No receipt” (store reference without delivery)
- Storing/displaying the **receipt reference** on the payment/invoice UI
- Reprint flow with:
  - permission gating
  - identity verification prompts when required (policy-driven)
  - watermarking indicator (“REPRINT/DUPLICATE”) in rendered output (as provided by backend, or clearly marked if frontend overlays)
- Display receipt delivery status/history (print/email/declined) and reprint count
- Standard error handling and recovery UI (printer unavailable, email invalid, unauthorized)

### Out-of-scope
- Implementing backend receipt generation, storage, retention, retry policies, or template rendering
- Defining/altering financial settlement, reconciliation, or accounting posting
- Creating/updating CRM contact data (manual email entry must not be persisted to CRM)
- Designing printer drivers; only invoking existing POS print mechanism

---

## 3. Actors & Stakeholders

- **Cashier:** prints/email receipts; performs same-day reprints
- **Supervisor/Manager:** overrides reprint limits / performs reprints anytime (subject to permissions)
- **Customer Service:** may reprint with permission and reason code
- **Customer:** receives printed/email receipt
- **Billing (Moqui backend services):** receipt retrieval, email send request, reprint logging
- **CRM (external):** provides suggested email contact when customer account exists
- **POS Printer subsystem:** prints receipt payload

---

## 4. Preconditions & Dependencies

### Preconditions
- A **payment capture** succeeded for an **invoice** (invoice exists and is identifiable via invoiceId/invoiceNumber)
- User is authenticated in POS and has required permissions for actions they attempt

### Dependencies (must exist/confirmed)
- Backend exposes services/endpoints to:
  - retrieve receipt by invoice/payment/receiptReference
  - request email delivery (idempotent)
  - record print attempt result and reprint events (or backend does this implicitly)
- Frontend has access to:
  - current terminal/printer configuration identifier (terminalId, printerId if applicable)
  - current user identity (userId, displayName) and permission checks
- Printer integration available in frontend runtime (Quasar app) to send a print job

> Note: The backend story is detailed, but frontend cannot assume specific REST paths; Moqui service names/parameters must be confirmed (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
1. **Post-payment success screen/panel** (immediately after capture confirmation)
2. **Invoice detail screen** (paid invoice -> receipt section)
3. **Receipt search/reprint screen** (via invoice number / receipt reference)

### Screens to create/modify (Moqui)
1. **Modify existing**: `PaymentComplete` / `InvoicePayment` success view (where capture success is shown)
   - Add “Receipt” action group: **Print**, **Email**, **No Receipt**, **View Details**, **Reprint** (if allowed)
2. **Create/Modify**: `ReceiptDetail` screen
   - Shows receipt reference, invoice/payment linkage, delivery history, reprint count, and actions
3. **Create** (optional if not existing): `ReceiptLookup` screen
   - Search by invoice number or receipt reference (minimum)
   - Displays matching receipt(s) and allows navigation to `ReceiptDetail`

### Navigation context
- From payment success → receipt preview/detail modal or dedicated screen
- From invoice detail → receipt detail
- From receipt lookup → receipt detail → actions

### User workflows
**Happy path (print):**
- Payment captured → system loads/creates receipt → cashier clicks Print → print job succeeds → UI shows “Printed” with timestamp and updates delivery status.

**Happy path (email):**
- Payment captured → cashier clicks Email → UI suggests CRM email (if available) or allows entry → confirm → backend accepts request → UI shows “Email queued/sent” status.

**Alternate paths:**
- Printer error → show error + offer email fallback
- Customer declines receipt → record “declined” status without printing/emailing
- Reprint after 7 days → require verification inputs or supervisor override (per backend policy)

---

## 6. Functional Behavior

### Triggers
- **On payment capture success UI render**: load receipt data (or create draft receipt record) for the invoice/payment.
- **On Print click**: request printable content (if not already loaded) and send to printer.
- **On Email click**: request email delivery through backend with selected email.
- **On Reprint click**: enforce authorization/verification + request reprint content and print.

### UI actions (explicit)
- Buttons:
  - `Print Receipt`
  - `Email Receipt`
  - `No Receipt`
  - `Reprint Receipt`
  - `View Receipt Details`
- Email entry modal:
  - Prefill from CRM if provided
  - Validate email format before submit
  - Explicit consent text: “Email will be used only to send this receipt and not saved to your profile.” (copy must be confirmed if required)

### State changes (frontend-visible)
- Receipt delivery attempt creates/updates a delivery record:
  - method PRINT/EMAIL/DECLINED
  - status SUCCESS/FAILED/PENDING/CUSTOMER_DECLINED
- Reprint increments reprintCount and adds reprintHistory entry (as returned by backend)

### Service interactions (frontend perspective)
- Load receipt for invoice/payment (idempotent)
- Submit email delivery request (idempotent by backend key)
- Submit reprint request / fetch reprint-renderable artifact
- Optional: notify backend of print success/failure if backend doesn’t infer it

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Email address:
  - Required only when emailing
  - Must pass basic format validation client-side
  - Must be revalidated server-side; show server message on failure
- Reprint verification:
  - If backend indicates verification is required (e.g., >7 days), prompt for required factors (invoice + last4 + transaction date) OR supervisor override flow

### Enable/disable rules
- Disable “Email Receipt” if:
  - backend indicates emailing not available OR no permission
- Disable “Reprint” if:
  - user lacks permission OR backend indicates max reprints reached without override
- Disable actions while a request is in-flight (prevent double-submits)

### Visibility rules
- Show receipt reference once available (post-load)
- Show delivery history table only when receipt exists
- Show “DUPLICATE/REPRINT” indicator on reprints (either within returned artifact or as a UI banner when printing a reprint)

### Error messaging expectations
- Printer offline: “Printer unavailable. Load paper or select email option.”
- Unauthorized: “You do not have permission to reprint receipts. Contact supervisor.”
- Missing data from backend: “Cannot generate receipt: Missing <field>”
- Email failure: show reason without exposing sensitive data; never log/display full gateway secrets

---

## 8. Data Requirements

### Entities involved (frontend view models; backend SoR = Billing)
- `Receipt`
- `ReceiptDelivery` (nested)
- `ContactRef` (from CRM, optional)

### Fields (type, required, defaults)
**Receipt (read-only in UI except delivery requests):**
- receiptId (string/UUID, read-only)
- receiptReference (string, required once generated, read-only)
- invoiceId (string/UUID, required, read-only)
- invoiceNumber (string, required, read-only)
- paymentId (string/UUID, required, read-only)
- generatedAt (datetime, required, read-only)
- generatedBy (string, required, read-only)
- terminalId (string, required, read-only)
- templateId (string, required, read-only)
- templateVersion (string, required, read-only)
- deliveryMethods[] (array, read-only)
- reprintCount (int, read-only)
- reprintHistory[] (array, read-only)

**Email request payload (editable input):**
- emailAddress (string, required for email)
- source (enum: CRM_SUGGESTED | MANUAL_ENTRY)
- consentAcknowledged (boolean) — **needs confirmation if required**

**Reprint request payload (editable input when required):**
- receiptReference (string, required)
- reprintReasonCode (string, optional/required depending on policy)
- verification:
  - last4 (string, optional/required depending on policy)
  - transactionDate (date, optional/required depending on policy)
  - supervisorOverride (boolean/credential token?) **TBD**

### Read-only vs editable by state/role
- Cashier can initiate print/email; can reprint same day (if permitted)
- Supervisor can override reprint limit and verification requirements (exact mechanism TBD)

### Derived/calculated fields (display only)
- “Age of receipt” = now - generatedAt (used to determine when verification is required, but **policy must come from backend**; UI should not hardcode day thresholds unless confirmed)
- Delivery status summary (latest per method)

---

## 9. Service Contracts (Frontend Perspective)

> Moqui service names, screen transitions, and response shapes must be confirmed. Below is the required contract behavior.

### Load/view calls
1. **Get receipt for payment/invoice**
   - Input: invoiceId and/or paymentId (preferred), fallback invoiceNumber
   - Output: Receipt model including receiptReference, template/version, deliveryMethods, reprintCount
   - Behavior: idempotent; if receipt exists return it; if not and payment captured, create/generate or return a “generating” status (TBD)

2. **Get suggested email from CRM**
   - Input: customerAccountId (from invoice context)
   - Output: ContactRef (emailAddress masked in UI as needed, plus emailVerified/emailPreference)

### Create/update calls
3. **Request email receipt delivery**
   - Input: receiptReference, invoiceId, emailAddress, idempotencyKey (backend-defined; frontend passes stable key if required)
   - Output: updated deliveryMethods entry with status PENDING/SUCCESS/FAILED and timestamp; failureReason if failed

4. **Record print attempt (if required)**
   - Input: receiptReference, printerId/terminalId, outcome SUCCESS/FAILED, failureReason (if any)
   - Output: updated deliveryMethods

### Submit/transition calls
5. **Request reprint**
   - Input: receiptReference, reasonCode (if required), verification inputs (if required)
   - Output: printable payload (text/PDF) plus updated reprintCount/history

### Error handling expectations
- 401/403: show permission error; do not retry automatically
- 409: show state conflict (e.g., reprint limit reached, receipt immutable conflict)
- 422: show validation errors (missing email, invalid email, missing required verification)
- 503: show “Service unavailable, try again” with retry button

---

## 10. State Model & Transitions

### Receipt lifecycle (as displayed)
- GENERATED (implicit once receipt exists)
- Delivery method states per method:
  - PRINT: SUCCESS/FAILED/PENDING (if backend uses pending)
  - EMAIL: PENDING/SUCCESS/FAILED
  - DECLINED: CUSTOMER_DECLINED

### Allowed transitions (UI-driven)
- On payment captured:
  - None → GENERATED (backend)
- Delivery:
  - GENERATED → add PRINT SUCCESS/FAILED
  - GENERATED → add EMAIL PENDING then SUCCESS/FAILED
  - GENERATED → add DECLINED CUSTOMER_DECLINED
- Reprint:
  - GENERATED → increment reprintCount + append reprintHistory + print artifact

### Role-based transitions (UI gating)
- Cashier:
  - print/email
  - reprint same-day (if permitted)
- Supervisor/Manager:
  - reprint anytime
  - override beyond max reprints (requires reason code)
- Customer service:
  - reprint anytime with permission

> UI must rely on backend-provided “allowedActions” or permission + policy result; do not hardcode 7-day/5-reprint rules unless backend contract explicitly returns them.

---

## 11. Alternate / Error Flows

1. **Printer offline/out of paper**
- When print fails, show actionable error and offer:
  - retry print
  - email receipt
  - view receipt reference for later
- If backend requires recording failure, call record-print-failure service.

2. **Email invalid**
- Client-side validation blocks submit
- Server-side 422 shows field-level error; allow correction and resubmit
- Do not persist manual email anywhere except the delivery request

3. **Email delivery failure**
- Backend returns FAILED with reason; UI shows failure and allows retry (subject to idempotency rules)
- If backend marks hard bounce, UI must not auto-retry; allow entering a new email and resubmitting.

4. **Receipt generation missing data**
- If backend cannot generate, display blocking error, show receipt reference if already assigned; provide “Contact supervisor” guidance.

5. **Unauthorized reprint attempt**
- 403 → show message and log correlation id in UI (if available)

6. **Concurrency/idempotency**
- Double click on email/print: UI must disable while pending; if backend returns “already requested,” treat as success and refresh receipt.

7. **Empty states**
- Invoice has no receipt yet (but payment captured): show “Receipt is generating” and poll/refresh button (polling behavior TBD)

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Receipt details available after successful capture
Given a cashier completes a payment capture successfully for an invoice with an associated paymentId  
When the payment success screen loads  
Then the UI requests the receipt details for that invoice/payment  
And the UI displays the receiptReference once returned  
And the UI displays receipt identifiers (invoiceNumber, paymentId, timestamp) and delivery history if present.

### Scenario 2: Print receipt successfully
Given a receipt exists for the captured payment  
And the user has permission to print receipts  
When the cashier clicks "Print Receipt"  
Then the POS sends the printable receipt payload to the configured printer  
And the UI shows a success confirmation with the print timestamp  
And the receipt delivery history reflects PRINT status SUCCESS after refresh.

### Scenario 3: Printer error offers email fallback
Given a receipt exists for the captured payment  
When the cashier clicks "Print Receipt" and the printer returns an error  
Then the UI displays "Printer unavailable. Load paper or select email option."  
And the UI offers actions to Retry Print or Email Receipt  
And no duplicate print requests are issued while the prior attempt is unresolved.

### Scenario 4: Email receipt using CRM-suggested email
Given a receipt exists for the captured payment  
And the invoice is linked to a customer account with an email address in CRM  
When the cashier clicks "Email Receipt"  
Then the UI displays the CRM-suggested email address for confirmation  
When the cashier confirms  
Then the UI submits an email delivery request for that receiptReference and email  
And the UI shows delivery status as PENDING or SUCCESS based on response  
And the receipt delivery history includes an EMAIL entry.

### Scenario 5: Email receipt with manual email entry (not stored in CRM)
Given a receipt exists for the captured payment  
And no CRM email is available  
When the cashier clicks "Email Receipt"  
Then the UI prompts for an email address  
And blocks submission until the email is syntactically valid  
When the cashier submits a valid email  
Then the UI requests email delivery  
And the UI does not create or update any CRM contact record.

### Scenario 6: Customer declines receipt
Given a payment capture succeeded and a receipt can be generated/stored  
When the cashier selects "No Receipt"  
Then the UI records the receipt as declined (via backend)  
And no print or email delivery is initiated  
And the receipt remains retrievable for later reprint.

### Scenario 7: Reprint allowed (same day)
Given a receipt exists and was generated today  
And the cashier has reprint permission for same-day reprints  
When the cashier clicks "Reprint Receipt"  
Then the UI requests a reprint artifact from the backend (or the original with reprint watermark)  
And prints it  
And the UI shows that the receipt is a reprint/duplicate  
And the reprint count increases after refresh.

### Scenario 8: Reprint requires verification after policy threshold
Given a receipt exists and backend indicates verification is required to reprint  
When the cashier clicks "Reprint Receipt"  
Then the UI prompts for the required verification inputs or supervisor override  
And if the cashier does not provide required inputs  
Then the UI blocks reprint and displays a clear requirement message.

### Scenario 9: Reprint denied due to permissions
Given a receipt exists  
And the user lacks reprint permission  
When the user attempts to reprint  
Then the UI displays an authorization error  
And the receipt is not printed.

---

## 13. Audit & Observability

### User-visible audit data
- Receipt reference
- Delivery history entries (method, status, timestamp; failure reason when failed)
- Reprint count and last reprint timestamp

### Status history
- Show chronological list of delivery attempts and reprints (read-only)

### Traceability expectations
- All frontend service calls include correlation id (if available in Moqui request context)
- Do not log full email addresses; display them to user only where needed

---

## 14. Non-Functional UI Requirements

- **Performance:** Receipt load should not block rendering; show loading state; target <2s perceived load for receipt actions (network permitting)
- **Accessibility:** All actions keyboard-navigable; dialogs have focus trapping; status messages announced via ARIA live region
- **Responsiveness:** Works on standard POS tablet/desktop resolutions; receipt actions usable in narrow layouts
- **i18n/timezone/currency:** Display timestamps in store/local timezone; currency displayed using ISO currency from receipt/payment data (no hardcoded $)

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE:** Provide explicit empty/loading states for receipt not yet available; qualifies as UI ergonomics. Impacted sections: UX Summary, Alternate/Error Flows.
- **SD-UX-INFLIGHT-GUARD:** Disable action buttons during in-flight requests to prevent double-submit; qualifies as UI ergonomics. Impacted sections: Functional Behavior, Alternate/Error Flows.
- **SD-ERR-STD-MAPPING:** Map 401/403/409/422/503 to standard user messages and retry affordances; qualifies as standard error-handling mapping. Impacted sections: Service Contracts, Alternate/Error Flows.
- **SD-OBS-CORRELATION:** Include correlation/request id in client error reports and logs when available, without PII; qualifies as observability boilerplate. Impacted sections: Audit & Observability.

---

## 16. Open Questions

1. **Moqui contract:** What are the exact Moqui screen paths and services to (a) fetch/create receipt for invoice/payment, (b) request email delivery, (c) request reprint artifact, and (d) record print outcome (if needed)? Provide service names + parameters + response fields.
2. **Partial payments:** Can an invoice be partially paid in this POS flow, and if so should the receipt UI explicitly show “PARTIAL PAYMENT” and remaining balance (as backend rules describe), or is this story limited to “paid in full” captures only?
3. **Failure handling:** When email delivery fails, should the UI allow immediate retry from the POS (new request) or only allow changing email and resubmitting? What statuses does backend return (PENDING/SENT/FAILED/BOUNCED)?
4. **Reprint policy source:** Will backend return policy evaluation such as `allowedActions`, `verificationRequired`, `maxReprintsReached`, and `requiresReasonCode`? If not, where should frontend get the 7-day / 5-reprint thresholds without hardcoding?
5. **Permissions:** What are the exact permission identifiers used in this Moqui app for print/email/reprint and supervisor override?
6. **Printing payload type:** Will printable content be plain text, HTML, or PDF for thermal printing? Does frontend need to format to 40–48 char width, or will backend provide printer-ready text?
7. **“No Receipt” behavior:** Is “No Receipt” a backend state transition on receipt delivery (DECLINED), or does it only suppress printing/emailing while still generating the receipt automatically?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Payment: Print/Email Receipt and Store Reference — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/71

Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Payment: Print/Email Receipt and Store Reference

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Cashier**, I want receipts so that customers have proof of payment.

## Details
- Receipt includes invoice ref, auth code, timestamp, transaction refs.
- Email receipt optional.

## Acceptance Criteria
- Receipt produced on successful capture.
- Receipt ref stored.
- Reprint supported.

## Integrations
- Payment service returns receipt data; CRM provides email contact (optional).

## Data / Entities
- Receipt, ReceiptDelivery, ContactRef

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