Title: [BACKEND] [STORY] Payment: Print/Email Receipt and Store Reference
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/7
Labels: type:story, domain:billing, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:ready-for-dev

### Recommended
- agent:billing
- agent:story-authoring

---

## Story Intent

Enable **Cashiers** to generate, print, and optionally email payment receipts to customers immediately after successful payment capture, ensuring customers have verifiable proof of payment including transaction details, and support reprint functionality for lost or damaged receipts.

This story establishes the **receipt generation and delivery pipeline** that serves as the customer-facing artifact for payment confirmation and the audit trail for transaction verification.

---

## Actors & Stakeholders

### Primary Actors
- **Cashier**: Generates receipt after payment capture, reprints receipts on customer request
- **Payment Service**: Provides receipt data (authorization code, transaction reference, payment details)
- **Receipt Generation Service**: Composes receipt content, formats for print/email, stores receipt reference
- **Print Service**: Sends receipt to POS terminal printer
- **Email Service**: Sends receipt to customer email address (optional)

### Secondary Stakeholders
- **Customer**: Receives printed or emailed receipt as proof of payment
- **CRM Service**: Provides customer contact information (email, phone) for receipt delivery
- **Audit Service**: Logs receipt generation, delivery attempts, and reprint requests
- **Accounting Team**: Uses receipt references for reconciliation and dispute resolution

---

## Preconditions

1. Payment has been successfully captured (authorization complete, funds transferred)
2. Invoice exists with associated order and line items
3. Payment service has returned transaction details (authorization code, transaction ID, gateway response)
4. POS terminal is connected to a receipt printer (for physical receipt)
5. Customer contact information is available in CRM (for email receipt, optional)
6. Cashier has permission to generate and reprint receipts

---

## Functional Behavior

### 1. Generate Receipt After Successful Payment Capture

**Trigger**: Payment service confirms successful payment capture

**Flow**:
1. Payment service emits `PaymentCaptured` event with:
   - Invoice ID, payment ID, amount, currency
   - Authorization code, transaction reference (gateway transaction ID)
   - Payment method, timestamp, gateway response
2. Receipt Generation Service receives event
3. Service retrieves receipt data:
   - Invoice details (invoice number, total, line items summary)
   - Payment details (amount paid, payment method, auth code, transaction ref)
   - Customer details (name, account number, if available)
   - Timestamp, cashier name/ID, terminal ID
4. Service composes receipt content using template with template version reference:
   - Header: Store name, address, phone, tax/VAT ID
   - Transaction section: Invoice number, payment ID, date/time, POS terminal ID, cashier name/ID
   - Payment section: Amount paid, payment method, auth code, transaction ref, last 4 digits of card (if applicable)
   - Taxes: Total tax amount + tax rate/breakdown (if available and space allows)
   - Multi-tender: List each tender with amount, method, auth code per tender
   - Footer: Payment received statement, return policy, customer service contact, receipt number
5. Service generates unique receipt reference: `RCP-{invoiceId}-{timestamp}-{sequence}`
6. Service stores receipt record with templateId, templateVersion, and status "GENERATED"
7. Service returns receipt payload (formatted text or PDF) to POS

**Outcome**: Receipt content is ready for printing or emailing

---

### 2. Print Receipt to POS Terminal Printer

**Trigger**: Receipt generation completes successfully

**Flow**:
1. POS receives receipt payload from Receipt Generation Service
2. POS formats receipt for thermal printer (line width, font size, alignment)
3. POS sends print command to terminal printer with receipt content
4. Printer prints receipt
5. POS updates receipt record with delivery status:
   - `deliveryMethod: PRINT`
   - `deliveryStatus: SUCCESS`
   - `deliveryTimestamp: `
6. Cashier hands printed receipt to customer

**Alternate**: If printer is offline or out of paper, display error to cashier and offer email option

**Outcome**: Customer receives printed receipt

---

### 3. Email Receipt to Customer (Optional)

**Trigger**: Customer requests email receipt, or printer is unavailable

**Flow**:
1. Cashier selects "Email Receipt" option on POS
2. POS prompts for email address or retrieves from CRM:
   - If customer account exists, suggest stored email from CRM
   - If no account or email not on file, prompt cashier to enter manually
3. Cashier confirms email address
4. POS sends receipt email request to Email Service with:
   - Receipt reference, invoice ID
   - Customer email address
   - Receipt content (HTML or PDF attachment)
   - Idempotency key: `(receiptReference, emailAddress)`
5. Email Service sends email with subject: "Payment Receipt - {store name} - {invoice number}"
6. Email Service returns delivery confirmation (sent, failed, bounced)
7. POS updates receipt record with email delivery status:
   - `deliveryMethod: EMAIL`
   - `deliveryStatus: SUCCESS | FAILED`
   - `emailAddress: ` (encrypted at rest)
   - `emailDeliveryTimestamp: `
   - Failure reason if applicable
8. POS displays confirmation to cashier: "Receipt emailed to {email}"

**Email SLA:** 95% delivered within 5 minutes of request (valid address, normal email provider operations)

**Email Retry Policy:** up to 3 retries over 60 minutes (5m, 15m, 40m) for transient failures only; hard bounces marked as FAILED with reason

**Outcome**: Customer receives receipt via email

---

### 4. Store Receipt Reference for Audit and Reprint

**Trigger**: Receipt is generated (regardless of delivery method)

**Flow**:
1. Receipt Generation Service stores receipt record in database:
   - Receipt reference (unique ID)
   - Invoice ID, payment ID
   - Receipt content canonical fields (normalized)
   - TemplateId and templateVersion (immutable record of template used)
   - Generation timestamp, cashier ID, terminal ID
   - Delivery method(s): PRINT, EMAIL, or both
   - Delivery status for each method
2. Receipt reference is included in invoice record for lookup
3. Receipt is retained per retention policy (see below)

**Retention Policy:**
- **Hot storage (0-24 months):** Keep rendered print-text and email PDF/HTML for fast reprints and exact reproduction
- **Warm/Cold storage (3-7 years):** Archive rendered artifacts to cold/compressed storage, or regenerate deterministically from canonical data + original template version
- **Purge:** After 7 years unless legal hold flag present

**Outcome**: Receipt is retrievable for reprint or audit

---

### 5. Reprint Receipt on Customer Request

**Trigger**: Customer requests duplicate receipt (lost or damaged)

**Flow**:
1. Cashier searches for receipt by:
   - Invoice number
   - Receipt reference
   - Transaction date + customer name
   - Last 4 digits of card or authorization code (with elevated permission)
2. POS displays receipt record with delivery history
3. **Authorization check:**
   - Cashier (same day only): no verification required
   - Cashier (after 7 days): require invoice number + last4 + transaction date, OR customer account login, OR supervisor override
   - Supervisor/Manager: may reprint anytime
   - Customer service (with `REPRINT_RECEIPT` permission): may reprint anytime with reason code
4. POS checks reprint count: max 5 reprints per receipt (require supervisor override beyond 5)
5. POS retrieves receipt content from Receipt Generation Service using original templateVersion
6. POS adds **DUPLICATE** or **REPRINT** watermark with:
   - Reprint timestamp
   - Reprinted-by user ID
   - Reason code (if supervisor override)
7. POS renders receipt using original templateVersion to ensure exact reproduction
8. POS prints receipt with original details + reprint metadata
9. POS logs reprint event to audit log:
   - Original receipt reference
   - Reprint timestamp, cashier ID / approver ID
   - Reprint reason (if captured)
   - Reprint count (incremented)
10. Receipt record is updated with reprint count and last reprint timestamp

**Outcome**: Customer receives duplicate receipt with watermark

---

## Alternate / Error Flows

### 1. Printer Offline or Out of Paper

**Trigger**: Print command fails (printer unavailable, out of paper, paper jam)

**Flow**:
1. POS detects printer error
2. POS displays alert to cashier: "Printer unavailable. Load paper or select email option."
3. Cashier resolves printer issue (load paper, clear jam) and retries, OR
4. Cashier selects "Email Receipt" option as fallback
5. If email also fails or customer declines, mark receipt as "DELIVERY_FAILED" with failure reason "Printer offline, fallback to email"
6. Store receipt reference for later reprint request

**Outcome**: Customer may receive receipt later via reprint or email

---

### 2. Customer Email Address Invalid or Bounces

**Trigger**: Email service returns bounce or invalid address error (after retry policy exhaustion)

**Flow**:
1. Email Service attempts to send receipt email with 3 retries over 60 minutes
2. Email bounces (invalid address, mailbox full, spam filter)
3. Email Service logs bounce event with provider reason/message ID
4. After 3 retries, Email Service marks delivery as FAILED with reason
5. POS displays error to cashier: "Email delivery failed: {reason}"
6. Cashier offers alternatives:
   - Re-enter correct email address and retry
   - Print receipt instead
   - Customer can retrieve receipt later via online portal (if available)
7. Update receipt record with `deliveryStatus: FAILED`, `failureReason: {provider error}`, `failedEmail: {address}`

---

### 3. Receipt Generation Fails Due to Missing Data

**Trigger**: Payment captured successfully but invoice or payment details are incomplete

**Flow**:
1. Receipt Generation Service attempts to compose receipt
2. Service detects missing required fields (e.g., invoice number, payment amount)
3. Service logs error with missing field details
4. Service returns error to POS: "Cannot generate receipt: Missing {field}"
5. POS displays error to cashier: "Receipt generation failed. Contact supervisor."
6. Cashier can complete transaction but customer does not receive receipt immediately
7. Supervisor investigates and manually generates receipt if possible

**Outcome**: Receipt generation blocked until data issue resolved

---

### 4. Customer Declines Receipt (Paper or Email)

**Trigger**: Customer explicitly declines receipt

**Flow**:
1. Cashier asks customer: "Would you like a receipt?"
2. Customer declines
3. Cashier selects "No Receipt" option on POS
4. POS generates receipt reference and stores receipt content but does NOT print or email
5. Receipt record is marked with `deliveryMethod: DECLINED`, `deliveryStatus: CUSTOMER_DECLINED`
6. Receipt remains available for later reprint if customer changes mind

**Outcome**: Receipt stored but not delivered; customer can request reprint later

---

### 5. Unauthorized Reprint Attempt

**Trigger**: User without reprint permission attempts to reprint receipt

**Flow**:
1. User searches for receipt and clicks "Reprint"
2. POS checks user permissions for `REPRINT_RECEIPT` permission
3. If permission missing, display error: "You do not have permission to reprint receipts. Contact supervisor."
4. Log unauthorized reprint attempt to audit log with user ID, receipt reference, timestamp
5. Reprint is blocked

**Outcome**: Unauthorized reprint prevented and logged

---

## Business Rules

### 1. Receipt Content Requirements (v1 contract)

**Mandatory on all receipts (print + email):**
- **Store:** name, address, phone, tax/VAT ID (if applicable), location identifier
- **Receipt identifiers:** receiptReference, invoice number, payment ID, POS terminal ID, cashier ID/name
- **Date/time:** transaction timestamp (local time) + timezone
- **Amounts:** total paid, currency, total due (at payment time), change given (if cash)
- **Payment method details (per tender):** method type, amount, auth code + gateway transaction reference (when applicable), card brand + last4 (card only)
- **Taxes:** total tax amount, tax-included indicator
- **Footer:** "payment received" statement + customer service contact

**Optional (channel-specific):**
- **Print (thermal):** line item summary only (3 lines max + "see invoice for details"), discounts total, tax breakdown (if space allows)
- **Email:** full line item list + per-line tax (optional), discounts/promotions, loyalty summary (if enabled), return policy full text link

**Tax rule:** Always show total tax amount; show tax rate/breakdown only when available and space allows. If tax-exempt, show "Tax Exempt" + exemption reason code (no certificate number printed).

---

### 2. Receipt Format and Templates

Receipt format MUST be determined by delivery method:
- **Printed (Thermal)**: 40-48 character width, plain text, left-aligned, bold headers
- **Email (HTML)**: Responsive HTML template with store branding
- **Email (PDF)**: Standard 8.5x11 inch format for easy printing by customer

**Template Ownership & Versioning:**
- **Billing** owns receipt content structure and legal compliance
- **Marketing** owns branding elements (subject to Billing approval)
- **Change control:** Templates stored in controlled repository with approvals
- **Each receipt must store:** templateId + templateVersion for immutable record
- **Reprints must render using original templateVersion** to ensure exact reproduction

**Template changes are additive where possible; breaking changes require new templateId.**

---

### 3. Receipt Reference Format

Receipt reference MUST follow format: `RCP-{invoiceNumber}-{timestamp}-{sequence}`

Example: `RCP-INV-12345-20260105T143022Z-001`

Receipt reference MUST be unique and immutable once generated.

---

### 4. Reprint Authorization and Watermarking

**Authorization Matrix:**
- **Cashier:** may reprint same day without extra verification
- **Supervisor/Manager:** may reprint anytime
- **Customer service (with `REPRINT_RECEIPT` permission):** may reprint anytime

**Identity Verification Policy:**
- Same day: none required
- After 7 days: require one of: invoice number + last4 + transaction date, OR customer account login, OR supervisor override

**Limits:**
- Max 5 reprints per receipt within retention window
- Override beyond 5 requires supervisor + reason code

**Watermarking (mandatory on all reprints):**
- Always add **DUPLICATE** or **REPRINT** + reprint timestamp + reprinted-by user ID + reason code (if applicable)

---

### 5. Email Receipt Consent and Privacy

**Email Delivery:**
- Treat as **event-driven and asynchronous** with idempotent retries
- SLA: 95% delivered within 5 minutes (valid address, normal email provider)
- Retry: up to 3 attempts over 60 minutes (5m, 15m, 40m) for transient failures only
- No retry for hard bounces; mark FAILED with reason
- Idempotency key: `(receiptReference, emailAddress)`

**Privacy & PCI Compliance:**
- Email addresses MUST be encrypted at rest
- Never store PAN; store only card brand + last4 + gateway transaction ID + auth code
- Do NOT write email addresses to logs; log hashed value for correlation
- Email addresses from manual cashier entry NOT stored in CRM without customer consent
- Email receipt PDFs should NOT include full customer account number
- Receipt search by last4/auth code requires elevated permission

---

### 6. Receipt Retention and Storage Format

**System of Record for Receipt:**
- Receipt service database retains canonical receipt data fields (normalized)
- Each receipt stores templateId + templateVersion (immutable)
- Optional: content hash/signature for tamper-evidence

**Rendered Output Storage:**
- **Hot storage (0-24 months):** Keep rendered print-text and email PDF/HTML for fast reprints and exact reproduction
- **Warm/Cold storage (years 3-7):** Archive rendered artifacts to compressed cold storage OR regenerate deterministically from canonical data + original template version
- **Purge:** After 7 years unless legal hold flag present

**Strict Reproducibility:** Either store rendered artifact, OR guarantee deterministic re-rendering (same template version + formatting rules).

---

### 7. Multi-Tender Payments on Receipts

One receipt per invoice settlement showing all tenders:
- Tender list ordered by capture time
- Each tender: method, amount, auth code/transaction reference
- Totals: paid total, remaining balance (should be 0 on "paid in full"), change given
- If partial payment, receipt indicates **PARTIAL PAYMENT** and remaining balance

---

## Data Requirements

### Receipt (Primary Model)

```json
{
  "receiptId": "string (UUID)",
  "receiptReference": "string (format: RCP-{invoiceNumber}-{timestamp}-{sequence})",
  "invoiceId": "string (UUID)",
  "invoiceNumber": "string",
  "paymentId": "string (UUID)",
  "generatedAt": "timestamp (ISO 8601)",
  "generatedBy": "string (cashier user ID)",
  "terminalId": "string",
  "templateId": "string (required, identifies receipt template)",
  "templateVersion": "string (required, version of template used)",
  "receiptContent": {
    "storeInfo": {
      "storeName": "string",
      "address": "string",
      "phone": "string",
      "taxId": "string (optional)"
    },
    "transactionInfo": {
      "invoiceNumber": "string",
      "paymentId": "string",
      "transactionDate": "timestamp (ISO 8601)",
      "cashierName": "string",
      "terminalId": "string"
    },
    "paymentInfo": {
      "totalPaid": "decimal",
      "currency": "string (ISO 4217)",
      "paymentTenders": [
        {
          "method": "enum (CREDIT_CARD, DEBIT_CARD, CASH, CHECK, ACH, OTHER)",
          "amount": "decimal",
          "authorizationCode": "string",
          "transactionReference": "string (gateway transaction ID)",
          "cardBrand": "string (optional, e.g., Visa, Mastercard)",
          "cardLast4": "string (optional, for card payments)"
        }
      ]
    },
    "taxInfo": {
      "totalTax": "decimal",
      "taxRate": "decimal (optional, if available)",
      "taxExempt": "boolean (optional)",
      "exemptionReason": "string (optional, reason code only)"
    },
    "customerInfo": {
      "customerName": "string (optional)",
      "accountNumber": "string (masked, last 4 only for account customers, optional)"
    },
    "lineItemsSummary": "string (optional, for print receipts; full list for email)",
    "footer": "string (payment received statement, return policy, customer service contact)"
  },
  "deliveryMethods": [
    {
      "method": "enum (PRINT, EMAIL, DECLINED)",
      "status": "enum (SUCCESS, FAILED, PENDING, CUSTOMER_DECLINED)",
      "timestamp": "timestamp (ISO 8601)",
      "emailAddress": "string (optional, encrypted at rest, for EMAIL method)",
      "failureReason": "string (optional, if status is FAILED)"
    }
  ],
  "reprintHistory": [
    {
      "reprintedAt": "timestamp (ISO 8601)",
      "reprintedBy": "string (user ID)",
      "reprintReason": "string (optional, e.g., 'LOST', 'DAMAGED')"
    }
  ],
  "reprintCount": "integer",
  "retentionExpiresAt": "timestamp (ISO 8601, generation date + 7 years)"
}
```

---

### ReceiptDelivery (Nested Model for Tracking)

Used within Receipt model to track print and email delivery attempts.

**Key Fields**:
- `method`: PRINT, EMAIL, or DECLINED
- `status`: SUCCESS, FAILED, PENDING, CUSTOMER_DECLINED
- `timestamp`: When delivery was attempted
- `emailAddress`: For email deliveries (encrypted at rest)
- `failureReason`: Error message if delivery failed (e.g., "Printer offline", "Email bounced")

---

### ContactRef (Integration Model from CRM)

Provided by CRM service for email receipt delivery (encrypted when stored with receipt).

```json
{
  "customerId": "string (UUID)",
  "customerName": "string",
  "emailAddress": "string (encrypted at rest)",
  "emailVerified": "boolean",
  "emailPreference": "enum (RECEIPTS_ALLOWED, RECEIPTS_DECLINED, NO_PREFERENCE)"
}
```

---

## Acceptance Criteria

### AC1: Receipt Produced on Successful Payment Capture

**Given** a payment has been successfully captured with amount $50.00  
**And** payment method is Visa ending in 1234  
**And** authorization code is "AUTH-987654"  
**And** gateway transaction reference is "TXN-GW-12345"  
**When** the Receipt Generation Service receives the `PaymentCaptured` event  
**Then** a receipt is generated with:  
  - Invoice number  
  - Payment amount: $50.00  
  - Payment method: Visa ****1234  
  - Authorization code: AUTH-987654  
  - Transaction reference: TXN-GW-12345  
  - Receipt reference: RCP-{invoiceNumber}-{timestamp}-001  
  - TemplateId and templateVersion recorded  
**And** the receipt record is stored with status "GENERATED"

---

### AC2: Printed Receipt Delivered to Customer

**Given** a receipt has been generated  
**And** the POS terminal printer is online  
**When** the receipt is sent to the printer  
**Then** the receipt is printed with all mandatory fields  
**And** the receipt delivery record is updated with:  
  - `method: PRINT`  
  - `status: SUCCESS`  
  - `timestamp: `  
**And** the cashier hands the printed receipt to the customer

---

### AC3: Email Receipt Sent to Customer (Optional)

**Given** a receipt has been generated  
**And** the customer has email address "customer@example.com" on file in CRM  
**When** the cashier selects "Email Receipt" option  
**Then** an email is sent to "customer@example.com" with:  
  - Subject: "Payment Receipt - {store name} - {invoice number}"  
  - Body: HTML formatted receipt content  
  - Attachment: PDF receipt (optional)  
**And** the receipt delivery record is updated with:  
  - `method: EMAIL`  
  - `status: SUCCESS`  
  - `emailAddress: customer@example.com` (encrypted at rest)  
  - `timestamp: `  
**And** the POS displays: "Receipt emailed to customer@example.com"  
**And** email delivery is guaranteed at 95% within 5 minutes (valid address, normal operations)

---

### AC4: Receipt Reference Stored for Audit

**Given** a receipt has been generated  
**When** the receipt generation completes  
**Then** the receipt record is stored in the database with:  
  - Unique receipt reference (RCP-{invoiceNumber}-{timestamp}-{sequence})  
  - Canonical receipt content fields  
  - TemplateId and templateVersion (immutable)  
  - Generation timestamp, cashier ID, terminal ID  
  - Delivery method(s) and status  
**And** the receipt reference is included in the invoice record  
**And** the receipt is retained for 7 years from generation date

---

### AC5: Reprint Supported for Lost Receipts

**Given** a receipt was originally generated with reference "RCP-INV-12345-20260105T143022Z-001"  
**And** the receipt was printed 2 days ago  
**When** a customer requests a duplicate receipt  
**And** the cashier searches for the receipt by invoice number "INV-12345"  
**And** the cashier (same day): clicks "Reprint Receipt" without verification  
**Then** the original receipt content is retrieved using original templateVersion  
**And** a "REPRINT" watermark is added with:  
  - "Reprinted on: {current date/time}"  
  - "Reprinted by: {cashier ID}"  
**And** the receipt is printed with original details plus reprint metadata  
**And** the reprint is logged to audit log with receipt reference, timestamp, cashier ID  
**And** the receipt record is updated with reprint count incremented

**When** a customer requests reprint 10 days later  
**And** the cashier attempts reprint without verification  
**Then** the system requires one of: invoice number + last4 + transaction date, OR customer account login, OR supervisor override

---

### AC6: Printer Offline Falls Back to Email Option

**Given** a receipt has been generated  
**And** the POS terminal printer is offline or out of paper  
**When** the POS attempts to print the receipt  
**Then** the POS displays error: "Printer unavailable. Load paper or select email option."  
**And** the cashier can select "Email Receipt" as fallback  
**And** if email is sent successfully, the receipt delivery record includes:  
  - `method: EMAIL` (fallback)  
  - `status: SUCCESS`  
  - `failureReason: "Printer offline, fallback to email"`

---

### AC7: Customer Declines Receipt

**Given** a payment has been successfully captured  
**When** the cashier asks the customer "Would you like a receipt?"  
**And** the customer declines  
**And** the cashier selects "No Receipt" option  
**Then** the receipt reference is generated and stored  
**And** the receipt record is marked with:  
  - `deliveryMethod: DECLINED`  
  - `deliveryStatus: CUSTOMER_DECLINED`  
**And** no receipt is printed or emailed  
**And** the receipt remains available for later reprint if customer changes mind

---

### AC8: Reprint Count Limited and Enforced

**Given** a receipt has been reprinted 5 times  
**When** a user (non-supervisor) attempts to reprint again  
**Then** the system displays: "Maximum reprints reached. Contact supervisor for override."  
**When** a supervisor approves override with reason code (e.g., "LOST_BY_CUSTOMER")  
**Then** the reprint is allowed and logged with override reason

---

## Audit & Observability

### Required Audit Events

1. **ReceiptGenerated**
   - Timestamp, receipt reference, invoice ID, payment ID, cashier ID, terminal ID, templateId, templateVersion, generation latency

2. **ReceiptPrinted**
   - Timestamp, receipt reference, printer ID, print status (success/failed), failure reason (if failed)

3. **ReceiptEmailed**
   - Timestamp, receipt reference, email address (hashed for logging), send status (sent/failed/bounced), provider message ID (if available), failure reason (if failed)

4. **ReceiptReprinted**
   - Timestamp, receipt reference, reprint count, cashier ID / approver ID, reprint reason (if captured), original generation date, authorization method (same-day / verified / override)

5. **ReceiptDeliveryFailed**
   - Timestamp, receipt reference, delivery method (print/email), failure reason, fallback action taken

6. **UnauthorizedReprintAttempt**
   - Timestamp, receipt reference, user ID (who attempted), permission check result, denial reason

7. **ReceiptRetentionPolicyApplied**
   - Timestamp, receipt reference, archival action, storage tier transition (hot → warm → cold), purge (if 7-year retention elapsed)

---

### Metrics to Track

- **Receipt Generation Latency:** Time from `PaymentCaptured` event to receipt generation complete (target: <2 seconds at p95)
- **Print Success Rate:** Percentage of print attempts that succeed (target: >98%)
- **Email Delivery Rate:** Percentage of email receipts successfully delivered (target: >95% within 5 minutes)
- **Email Bounce Rate:** Percentage of email receipts that bounce (target: <5%)
- **Email Retry Success:** Percentage of retries that succeed after initial transient failure (target: >80%)
- **Reprint Request Rate:** Average number of reprint requests per day (should be low, <5% of total receipts)
- **Fallback Usage Rate:** Percentage of receipts delivered via fallback method (email after print failure) (target: <2%)
- **Max Reprint Limit Breaches:** Count of supervisor overrides for >5 reprints per day
- **Receipt Storage Size:** Total storage used by receipt records for retention compliance (hot vs. cold tiers)
- **Authorization Rate:** Percentage of reprint requests by permission level (cashier, supervisor, customer service)

---

## Resolved Open Questions Summary

✅ **Q1 - Receipt Content (v1 contract):** Mandatory fields on all receipts (store, identifiers, date/time, amounts, payment details per tender, taxes, footer). Optional channel-specific sections (print summary vs. email detail).

✅ **Q2 - Template Ownership & Versioning:** Billing owns content structure; Marketing owns branding; templates versioned in controlled repository. Each receipt stores templateId + templateVersion. Reprints use original template version for exact reproduction.

✅ **Q3 - Email Delivery SLA & Retry:** 95% delivered within 5 minutes. Up to 3 retries over 60 minutes for transient failures. No retry for hard bounces. Idempotent by (receiptReference, emailAddress).

✅ **Q4 - Reprint Authorization:** Cashier same-day (no verification), after 7 days (identity check), Supervisor/Manager anytime, Customer service with permission anytime. Max 5 reprints per receipt; supervisor override beyond 5. Mandatory watermark (DUPLICATE/REPRINT + timestamp + user ID).

✅ **Q5 - Retention Duration & Storage Format:** 7-year retention. Hot storage (0-24 months) keeps rendered artifacts for fast reprints. Warm/Cold storage (3-7 years) archives or regenerates deterministically from canonical data + original template. Purge after 7 years unless legal hold.

✅ **Q6 - Multi-Tender Payments:** Single receipt per invoice settlement with tender list ordered by capture time. Each tender shows method, amount, auth code. Totals show paid, remaining balance, change. Partial payments indicated.

✅ **Q7 - Privacy & PCI Compliance:** Email addresses encrypted at rest, never stored in logs (hashed), manual entry NOT auto-stored in CRM. Never store PAN; store only card brand + last4 + gateway transaction ID. Email PDFs don't include full account number. Receipt search by last4/auth code requires elevated permission.

---

## Original Story (Unmodified – For Traceability)

# Issue #7 — [BACKEND] [STORY] Payment: Print/Email Receipt and Store Reference

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

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

### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*