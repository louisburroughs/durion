STOP: Clarification required before finalization

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
- risk:financial-inference

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Accounting: Handle Refund Issued (Review + Trace Refund Against Original Transaction)

**Primary Persona:** Accounting Clerk / Finance Ops User (with accounting permissions)

**Business Value:** Enable finance users to view and validate refund issuance events with complete traceability to the original payment/invoice, ensuring refunds are discoverable, auditable, and exceptions are handled consistently (over-refunds, missing references, duplicates).

---

## 2. Story Intent

**As a** Finance Ops user  
**I want** a UI to review `RefundIssued` events / refund transactions and see their linkage to the original payment and/or invoice (including reason and authorizer)  
**So that** I can audit refund activity, identify exceptions, and provide operational support without needing database access.

### In-scope
- A Moqui screen flow to:
  - List refunds / refund events
  - View a refund’s details and traceability links (original transaction, related invoice/payment)
  - Surface processing/status and failure reasons when refund handling fails
- Basic filtering/search on key identifiers (refundId, eventId, originalTxnRef, date range, status)
- Display audit metadata (authorizer, occurredAt/createdAt, createdBy)
- Error/empty states for missing/invalid references

### Out-of-scope
- Initiating/authorizing a refund from the UI (issuing refunds)
- Defining GL debit/credit mappings, posting categories, or journal entry lines in the UI
- Editing refund financials (amount/currency/reason) after receipt
- Tax/refund policy decisions (jurisdictional rules, GL account selection)

---

## 3. Actors & Stakeholders
- **Primary user:** Finance Ops / Accounting Clerk
- **Secondary stakeholders:** Auditors/Compliance (read-only needs), Customer Support (lookup), Engineering/Support (diagnostics)
- **Upstream system actor (source):** Payment system emitting `RefundIssued` events (system-to-system)

---

## 4. Preconditions & Dependencies
- Backend (Moqui services/entities) provides a persisted representation of refunds/refund events and their linkage to an original transaction (payment and/or invoice).
- Event contract reference exists: “Durion Accounting Event Contract v1” (PDF linked), but required fields/enums are not provided in this prompt.
- Permission model exists for accounting read access and viewing audit metadata.

**Dependency:** The frontend needs stable service endpoints for:
- Searching refunds
- Fetching refund detail (including original transaction link and any processing status/errors)
- (Optional) fetching linked payment/invoice summaries for navigation

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Refunds**
- Deep links:
  - `/accounting/refunds` (list)
  - `/accounting/refunds/<refundId>` (detail)
  - Optional: `/accounting/refunds/event/<eventId>` if eventId is first-class

### Screens to create/modify
1. **Screen:** `accounting/refunds/RefundList.xml`
   - List/search/filter refunds (and/or refund events).
2. **Screen:** `accounting/refunds/RefundDetail.xml`
   - View refund data, status, linkage, and audit trail.
3. **(Optional) Reuse/Link:** existing screens for Payment/Invoice detail if present in repo; otherwise show read-only summaries.

### Navigation context
- Breadcrumb: Accounting → Refunds → Refund Detail
- From Refund Detail, allow navigation to:
  - Original Payment (if resolvable)
  - Invoice (if resolvable)
  - Source Event (if stored)

### User workflows
**Happy path**
1. User opens Refunds list.
2. User searches by refundId or originalTxnRef.
3. User opens Refund Detail.
4. User verifies amount/currency/reason, authorizer, occurredAt, and confirms links to original transaction.

**Alternate paths**
- Refund exists but original payment/invoice is missing → show “Unresolved reference” with identifiers.
- Refund processing failed (backend recorded failure) → show status + failure reason and raw error message (sanitized).
- Duplicate event detected / idempotency conflict → show conflict banner and link to event record(s) if available.

---

## 6. Functional Behavior

### Triggers
- User navigates to Refunds list or Refund Detail.
- User applies filters/search.
- User clicks a linked entity (payment/invoice) from detail.

### UI actions
- Search by:
  - refundId
  - eventId (if applicable)
  - originalTxnRef / originalTransactionId
  - status
  - occurredAt/createdAt date range
  - reasonCode (if enumerated)
- Open detail view
- Copy-to-clipboard for IDs (refundId/eventId/originalTxnRef) for support workflows

### State changes
- Frontend is read-only for refund records in this story (no state mutation except navigation).
- Any “status” displayed is backend-provided and not changed by the UI.

### Service interactions
- List screen calls a search/find service.
- Detail screen calls a get service by `refundId` (and optionally fetches linked payment/invoice summaries).

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Accounting domain rules mention GL impacts and reason-code-dependent treatment; frontend must **not** infer accounting postings. It must display what backend provides.

### Validation
- Search form validation:
  - If user enters an ID, enforce basic format checks if known (UUID). If unknown, allow free-text but label field as “Identifier”.
  - Date range: end date must be >= start date.

### Enable/disable rules
- “View Original Payment” link enabled only when backend returns a resolvable `paymentId` (or a navigation-safe reference).
- “View Invoice” link enabled only when backend returns `invoiceId`.

### Visibility rules
- Show a prominent banner when:
  - Refund status = FAILED
  - Reference missing (original transaction not found)
  - Refund exceeds original amount (if backend marks this failure reason)
  - Duplicate/conflict detected (if backend exposes a conflict flag/reason)

### Error messaging expectations
- Backend error codes (if returned) should map to user-facing messages:
  - `REFUND_EXCEEDS_ORIGINAL_AMOUNT` → “Refund amount exceeds refundable balance of original transaction.”
  - `ORIGINAL_TRANSACTION_NOT_FOUND` → “Original transaction could not be found.”
  - `INVALID_REASON_CODE` → “Refund reason code is invalid or unsupported.”
  - `INGESTION_DUPLICATE_CONFLICT` (or equivalent) → “Duplicate refund event conflict detected; requires investigation.”
- Always show a support-friendly details section with correlation IDs (refundId, eventId, originalTxnRef) without exposing sensitive payment details.

---

## 8. Data Requirements

### Entities involved (frontend-read)
- `Refund` or `RefundTransaction` (name TBD by backend/Moqui entities)
- `Payment` (read-only lookup/navigation)
- `Invoice` (read-only lookup/navigation)
- `AccountingEvent` / stored `RefundIssued` event record (optional, if persisted)

### Fields (type, required, defaults)
**Refund list item (minimum)**
- `refundId` (string/UUID, required)
- `eventId` (string/UUID, optional but preferred)
- `originalTransactionId` or `originalTxnRef` (string, required by business rule; may be unresolved)
- `refundAmount` (decimal, required)
- `currencyUomId` / `currency` (string, required)
- `reasonCode` (string, required)
- `status` (string enum, required) — e.g., `COMPLETED`, `FAILED`, `PENDING` (actual values TBD)
- `occurredAt` (datetime, required if event-based) OR `createdAt` (datetime, required)

**Refund detail (additional)**
- `authorizerId` (string, optional depending on contract; displayed if present)
- `failureReason` (string, optional)
- `failureMessage` (string, optional; must be sanitized)
- `linkedPaymentId` (string/UUID, optional)
- `linkedInvoiceId` (string/UUID, optional)
- `sourceModule` (string, optional)
- `schemaVersion` (string, optional)
- `businessUnitId` (string/UUID, optional)
- Audit fields: `createdByUserId`, `createdAt`, `lastUpdatedByUserId`, `lastUpdatedAt` (as available)

### Read-only vs editable
- All fields are **read-only** in this story.

### Derived/calculated fields (UI-only)
- “Reference status” derived from presence/absence of linked entities:
  - `Resolved` if linked entity IDs present
  - `Unresolved` if missing but originalTxnRef present

---

## 9. Service Contracts (Frontend Perspective)

> Backend service names are not provided; define placeholders that Moqui devs must map to actual services.

### Load/view calls
1. **Search refunds**
   - Service: `AccountingRefundServices.searchRefunds` (placeholder)
   - Inputs:
     - `refundId?`, `eventId?`, `originalTxnRef?`, `status?`, `reasonCode?`
     - `fromDate?`, `thruDate?`
     - `pageIndex`, `pageSize`, `orderBy` (safe default)
   - Output:
     - `refundList[]` with fields listed in Data Requirements
     - `totalCount`

2. **Get refund detail**
   - Service: `AccountingRefundServices.getRefund` (placeholder)
   - Inputs: `refundId` (required)
   - Output: `refund` object including link references and failure metadata

3. **(Optional) Get linked payment/invoice summary**
   - Services: `PaymentServices.getPaymentSummary` / `BillingInvoiceServices.getInvoiceSummary` (placeholders)
   - Inputs: `paymentId` or `invoiceId`
   - Output: minimal summary for navigation confirmation (no sensitive PAN data)

### Create/update calls
- none (read-only story)

### Submit/transition calls
- none

### Error handling expectations
- `401/403`: route to unauthorized screen or show “Not authorized” and hide list data.
- `404` on refund detail: show “Refund not found” with entered ID.
- `409` conflict (duplicate/conflict): show conflict banner; include IDs.
- `5xx` / timeout: show retry affordance and preserve search criteria.

---

## 10. State Model & Transitions

### Allowed states (display-only; owned by backend)
- `PENDING` (if ingestion/processing is async)
- `COMPLETED`
- `FAILED`
- `QUARANTINED` (if conflict/dlq concept exists)

### Role-based transitions
- None in UI (no actions that transition state).

### UI behavior per state
- `COMPLETED`: show normal detail, enable links if available.
- `FAILED`: show failure banner + reason, show “next steps” hint: contact support with IDs.
- `PENDING`: show processing banner; disable navigation to journal entry/posting (if those exist).
- `QUARANTINED`: show quarantine banner and any available diagnostic metadata.

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid date range → block search submission; inline error.
- If UUID format is enforced and invalid → inline error (only if confirmed UUID in Open Questions is resolved).

### Concurrency conflicts
- If refund updates between list and detail load, show latest data (no edits). If backend provides version, ignore in UI.

### Unauthorized access
- If user lacks permission, show access denied; do not leak whether a given refundId exists.

### Empty states
- No refunds match filters → show empty state with suggestion to broaden date range or search by ID.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View refunds list with filters
**Given** the user has permission to view accounting refunds  
**When** the user navigates to `Accounting → Refunds`  
**Then** the system displays a list of refunds with columns including refundId, amount, currency, status, occurredAt/createdAt, and originalTxnRef  
**And** the user can filter by date range and status  
**And** the results update to match the filter criteria.

### Scenario 2: View refund detail with traceability links
**Given** a refund exists with `refundId = R1` and references an original transaction  
**When** the user opens the refund detail for `R1`  
**Then** the system displays refund amount, currency, reasonCode, authorizer (if provided), and occurredAt/createdAt  
**And** the system displays the originalTxnRef/originalTransactionId  
**And** if linked invoiceId/paymentId are provided, the UI renders navigation links to them.

### Scenario 3: Missing original transaction reference resolution
**Given** a refund exists whose originalTxnRef cannot be resolved to a known payment/invoice record  
**When** the user opens the refund detail  
**Then** the UI displays an “Unresolved reference” banner  
**And** the UI still displays originalTxnRef and all refund identifiers for support  
**And** the UI does not render broken navigation links.

### Scenario 4: Refund processing failed due to over-refund
**Given** a refund is in status `FAILED` with failureReason `REFUND_EXCEEDS_ORIGINAL_AMOUNT`  
**When** the user opens the refund detail  
**Then** the UI shows a failure banner with a user-friendly message  
**And** the UI shows the failure reason code and correlation identifiers (refundId, eventId, originalTxnRef).

### Scenario 5: Unauthorized user
**Given** the user does not have permission to view refunds  
**When** the user navigates to the refunds list or a refund detail URL directly  
**Then** the UI shows an access denied message  
**And** no refund data is displayed.

---

## 13. Audit & Observability

### User-visible audit data
- Show (when available): createdAt, createdBy, occurredAt, sourceModule, schemaVersion, authorizerId.
- Provide a “Copy identifiers” block: refundId, eventId, originalTxnRef, businessUnitId.

### Status history
- If backend exposes status history/events, show a read-only timeline; otherwise omit (do not infer).

### Traceability expectations
- UI must always display the immutable linkage identifiers even when linked entities are missing.

---

## 14. Non-Functional UI Requirements
- **Performance:** refunds list should load within 2 seconds for default page size under normal conditions; pagination required.
- **Accessibility:** all interactive elements keyboard-navigable; error banners announced via aria-live; sufficient contrast.
- **Responsiveness:** list and detail usable on tablet widths; avoid overflow for long IDs (wrap/copy behavior).
- **i18n/timezone/currency:** display currency using `currencyUomId`/ISO code and locale formatting; timestamps shown in user’s timezone (if available in app settings).

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide a standard empty-state panel on list when no results; safe because it is purely UX and does not alter domain behavior. (Impacted: UX Summary, Alternate/Empty states)
- SD-UI-PAGINATION: Default pagination (`pageSize=25`, user-selectable 25/50/100) on list; safe because it affects only presentation/performance. (Impacted: UX Summary, Service Contracts)
- SD-ERR-RETRY: On transient errors/timeouts show retry button and preserve filters; safe because it is standard error ergonomics. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Domain/System of Record:** Is this frontend story intended to show data from the **Accounting** domain’s persisted `RefundTransaction` (post-processing), or from a **Payment** domain refund record/event log, or both? (This affects service endpoints and entity naming.)
2. **Canonical identifiers & formats:** Are `refundId` and `eventId` guaranteed UUIDs (and which version)? Should the UI enforce UUID validation or accept arbitrary strings?
3. **Refund status model:** What are the authoritative statuses for refunds in the Moqui backend (e.g., `PENDING/COMPLETED/FAILED/QUARANTINED`), and do we have a status history to display?
4. **Reason codes enumeration:** What is the allowed set of `reasonCode` values for refunds (from “Durion Accounting Event Contract v1”)? Should the UI treat them as opaque strings or provide friendly labels?
5. **Linkage rules:** Does every refund link to a **Payment** only, an **Invoice** only, or potentially both? What fields are returned for linkage (paymentId, invoiceId, originalTransactionId, originalTxnRef)?
6. **Authorization/permissions:** What permission(s)/roles gate access to refund screens (read-only)? Please provide the exact permission tokens used in this project so the Moqui screens can enforce them.
7. **Conflict/duplicate visibility:** If idempotency conflict/DLQ/quarantine exists, does the backend expose a record the UI can query (and what fields), or should the UI only show a generic failure?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Handle Refund Issued — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/177

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Accounting: Handle Refund Issued  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/177  
Labels: frontend, story-implementation, payment  

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Handle Refund Issued

**Domain**: payment

### Story Description

/kiro  
Reverse cash and revenue effects with full traceability.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `RefundIssued` event or authorized refund action

## Main Flow
1. Validate refund authorization and reference
2. Identify original payment and/or invoice
3. Reduce cash/bank balance
4. Adjust AR and/or revenue as appropriate
5. Record refund transaction with reason code
6. Persist linkage to original payment/invoice

## Alternate / Error Flows
- Refund exceeds original payment → block
- Partial refund → supported
- Refund against already credited invoice

## Business Rules
- Refunds must reference an original transaction
- Revenue impact depends on refund reason (pricing error vs goodwill)
- Refunds require explicit authorization

## Data Requirements
- Entities: Refund, Payment, Invoice
- Fields: refundAmount, reasonCode, originalTxnRef

## Acceptance Criteria
- [ ] Cash/bank balance reduces correctly
- [ ] AR and/or revenue adjust appropriately
- [ ] Refund is traceable to original transaction
- [ ] Audit trail captures reason and authorizer

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

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