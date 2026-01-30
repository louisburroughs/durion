# AGENT_GUIDE.md

## Summary

This guide defines the Billing domain’s normative business rules and system-of-record boundaries for invoice lifecycle, BillingRules, checkout enforcement, receipts, and billing-orchestrated payments.
Decision IDs in the index are authoritative and map 1:1 to rationale sections in `DOMAIN_NOTES.md`.

## Completed items

- [x] Generated/maintained Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Preserved resolved open questions and contract guidance

## Purpose

The Billing domain is the authoritative system of record (SoR) for:

- Invoice lifecycle (draft creation, validation, issuance/finalization, state transitions)
- Invoice composition (lines, totals, taxes/fees breakdown) derived from immutable upstream snapshots
- Traceability snapshot persisted on invoice (work order, estimate/version, approvals/artifacts references)
- Billing rules for commercial accounts (PO requirement, terms, delivery preferences, grouping strategy)
- Checkout enforcement decisions (policy evaluation + override auditing)
- Billing-orchestrated payments (customer AR payments against invoices; vendor AP payments if implemented in Billing boundary)
- Receipts (generation, delivery, reprint policy evaluation and audit trail)

This guide is **normative**. It is safe for direct agent input, CI validation, and story execution rules.

**Non-normative companion:** `DOMAIN_NOTES.md` (rationale, options, tradeoffs, auditor explanations; forbidden for direct agent execution).

---

## Decision Index (Authoritative)

Decision IDs below correspond 1:1 to sections in `DOMAIN_NOTES.md`.

| Decision ID | Title |
|---|---|
| BILL-DEC-001 | Canonical Invoice Status Enums and State Machine |
| BILL-DEC-002 | Draft Invoice Creation Command (Idempotency + Work Order Preconditions) |
| BILL-DEC-003 | Issuance Gating Model (Blockers + Policy Envelope) |
| BILL-DEC-004 | Traceability Snapshot Schema (Canonical Field Names + Immutability) |
| BILL-DEC-005 | Artifact Retrieval Contract (List + Secure Download) |
| BILL-DEC-006 | BillingRules Concurrency Model (ETag/If-Match) |
| BILL-DEC-007 | BillingRules Options/Enums Discovery Endpoints |
| BILL-DEC-008 | Permission Model (Canonical Permission Strings) |
| BILL-DEC-009 | Checkout PO Capture and Override Workflow Contract |
| BILL-DEC-010 | Manager/Supervisor Approval Semantics (Re-auth Token) |
| BILL-DEC-011 | Receipts: Generation, Delivery Statuses, Reprint Policy Evaluation |
| BILL-DEC-012 | Partial Payments Policy and Receipt Display Rules |
| BILL-DEC-013 | AP Vendor Payments: Status Enums, Idempotency, Allocation Rules |
| BILL-DEC-014 | Posting Error Visibility Policy (Sanitization + Role-Targeted Detail) |
| BILL-DEC-015 | Observability: Correlation/Tracing Standard (W3C Trace Context) |
| BILL-DEC-016 | Frontend Deep-Link Metadata (No Hardcoded Routes) |

---

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| BILL-DEC-001 | Canonical invoice status enums/state machine | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-001--canonical-invoice-status-enums-and-state-machine) |
| BILL-DEC-002 | Idempotent draft creation gated by Workexec readiness | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-002--draft-invoice-creation-command-idempotency--workorder-preconditions) |
| BILL-DEC-003 | Issuance gating with blockers + policy envelope | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-003--issuance-gating-model-blockers--policy-envelope) |
| BILL-DEC-004 | Immutable traceability snapshot schema | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-004--traceability-snapshot-schema-canonical-field-names--immutability) |
| BILL-DEC-005 | Artifact retrieval list + secure download contract | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-005--artifact-retrieval-contract-list--secure-download) |
| BILL-DEC-006 | BillingRules concurrency via ETag/If-Match | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-006--billingrules-concurrency-model-etagif-match) |
| BILL-DEC-007 | Discovery endpoints for options/enums | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-007--billingrules-optionsenums-discovery-endpoints) |
| BILL-DEC-008 | Canonical permission strings, least-privilege model | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-008--permission-model-canonical-permission-strings) |
| BILL-DEC-009 | PO capture + override workflow contract | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-009--checkout-po-capture-and-override-workflow-contract) |
| BILL-DEC-010 | Step-up auth via elevation token (no shared codes) | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-010--managersupervisor-approval-semantics-re-auth-token) |
| BILL-DEC-011 | Receipts generation/delivery/reprint policy evaluation | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-011--receipts-generation-delivery-statuses-reprint-policy-evaluation) |
| BILL-DEC-012 | Partial payments policy and receipt display rules | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-012--partial-payments-policy-and-receipt-display-rules) |
| BILL-DEC-013 | AP vendor payment semantics (if in boundary) | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-013--ap-vendor-payments-status-enums-idempotency-allocation-rules) |
| BILL-DEC-014 | Posting error visibility and sanitization policy | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-014--posting-error-visibility-policy-sanitization--role-targeted-detail) |
| BILL-DEC-015 | W3C Trace Context correlation/tracing standard | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-015--observability-correlationtracing-standard-w3c-trace-context) |
| BILL-DEC-016 | Deep-link metadata (no hardcoded routes) | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#bill-dec-016--frontend-deep-link-metadata-no-hardcoded-routes) |

## Domain Boundaries

### Owned by Billing (SoR)

- Invoice lifecycle management:
  - Draft creation from invoice-ready Work Orders (idempotent)
  - Issuance/finalization (DRAFT → ISSUED) with validation and locking
  - Post-issuance immutability (no edits; corrections via credit/rebill are separate stories)
- Invoice composition:
  - Header, totals, taxes/fees breakdown, and line items derived from immutable snapshots
  - Embedded immutable TraceabilitySnapshot
- BillingRules:
  - Per-account configuration + versioning + audit metadata
  - Policy evaluation payload for checkout enforcement
- Checkout enforcement decisions:
  - PO requirement/format/uniqueness
  - Override evaluation + audit capture
- Payments orchestration:
  - Customer payments against invoices (AR) + receipt generation
  - AP vendor payment execution if implemented in this domain boundary (with async GL posting acknowledgement)
- Receipts:
  - Receipt generation on payment capture
  - Delivery (print/email) and reprint evaluation

### Not owned by Billing (integrated)

- Work Execution: Work Order state machine, invoiceReady flag, BillableScopeSnapshot generation
- CRM: Customer account master data, billing contacts/addresses, account classification
- Accounting: GL posting rules, journal entry creation, AR ledger; consumes Billing events
- Payment Gateway: authorization/capture/void/refund execution (Billing orchestrates through adapter)
- Artifact/Document service: physical storage of approval artifacts (Billing references; secure retrieval via contract)

---

## Canonical Entities / Concepts (Normative)

### Invoice (Billing SoR)

- `invoiceId` (UUIDv7)
- `invoiceNumber` (string, assigned at issuance)
- `workOrderId` (UUIDv7, immutable)
- `customerAccountId` (UUIDv7, immutable)
- `billableScopeSnapshotId` (UUIDv7, immutable)
- `traceability` (TraceabilitySnapshot, immutable)
- `status` (InvoiceStatus enum; see BILL-DEC-001)
- Totals & breakdown:
  - `subtotal`, `taxTotal`, `feeTotal`, `discountTotal`, `grandTotal`, `balanceDue`
  - `taxBreakdown[]`, `feeBreakdown[]` (optional, but must be consistent when present)
- `issuedAt`, `issuedByUserId` (set at issuance)

### TraceabilitySnapshot (invoice-embedded, immutable) (BILL-DEC-004)

Canonical field names:

- `sourceWorkOrderId` (UUIDv7, required) = `workOrderId`
- `sourceBillableScopeSnapshotId` (UUIDv7, required) = `billableScopeSnapshotId`
- `sourceSchemaVersion` (string, required)
- Estimate reference:
  - `sourceEstimateId` (UUIDv7, optional)
  - `sourceEstimateVersionId` (UUIDv7, optional, preferred when present)
- Approvals/artifacts references:
  - `sourceApprovalIds[]` (UUIDv7[], optional but may be required by policy)
  - `artifactRefs[]` (optional; references for secure retrieval)

### BillingRules (per account)

- `accountId` (UUIDv7, unique)
- `isPoRequired` (bool)
- `paymentTermsId` (UUIDv7/string)
- `invoiceDeliveryMethod` (enum)
- `invoiceGroupingStrategy` (enum)
- Concurrency/audit:
  - `etag` (string)
  - `updatedAt`, `updatedByUserId`

### BillingRuleEvaluation (checkout decision DTO)

- `policyVersion` (string, required)
- PO policy:
  - `poRequired` (bool)
  - `poFormatRegex` (optional)
  - `poUniquenessScope` (enum)
- Override policy:
  - `overrideAllowed` (bool)
  - `overrideRequiresSecondApprover` (bool)
  - `overrideReasonCodeRequired` (bool)
- Credit/terms gating:
  - `chargeAccountEligible` (bool)
  - `creditLimitExceeded` (bool, optional)
- `messages[]` with `{code, severity, text}`

### Payments & Receipts

- Customer AR payments:
  - `billingPaymentId` (UUIDv7)
  - `invoiceId`
  - `amount`
  - `status` (gateway/result status)
- Receipts:
  - `receiptId` (UUIDv7)
  - `deliveryStatus` (ReceiptDeliveryStatus; see BILL-DEC-011)
  - `deliverySuppressed` (bool; see “No Receipt” semantics)

### AP Vendor Payments (if implemented in Billing boundary) (BILL-DEC-013)

- `billId` (UUIDv7)
- `billingPaymentId` (UUIDv7)
- `paymentRef` (string, idempotency key)
- Allocation records:
  - `{billId, appliedAmount}`
- Status enums:
  - `INITIATED`, `GATEWAY_PENDING`, `GATEWAY_FAILED`, `GATEWAY_SUCCEEDED`,
    `GL_POST_PENDING`, `GL_POSTED`, `GL_POST_FAILED`

---

## Invariants / Business Rules (Normative)

### Invoice draft creation (BILL-DEC-002)

- One primary invoice per Work Order.
- Allowed only if Work Execution reports:
  - Work Order is Completed
  - `invoiceReady=true`
- Idempotent behavior:
  - If draft exists for `workOrderId`: return existing draft (200)
  - If invoice already ISSUED/PAID/VOID: return 409 conflict with deterministic reason code
- Lines are derived from BillableScopeSnapshot and are not editable in this flow.
- Billing calculates taxes/fees using current tax rules; do not trust upstream totals.

### Invoice issuance / finalization (BILL-DEC-001, BILL-DEC-003)

- Atomic and idempotent per `invoiceId + invoiceVersion`.
- Preconditions:
  - Invoice is in `DRAFT`
  - Required billing data present and consistent with delivery method
  - Traceability policy satisfied (server-side enforcement)
- Postconditions:
  - Invoice becomes immutable (header/lines/totals/traceability)
  - `invoiceNumber`, `issuedAt`, `issuedByUserId` set
  - `InvoiceIssued` event emitted exactly once for that `invoiceId + invoiceVersion`

### Traceability policy (BILL-DEC-003, BILL-DEC-004)

- Billing enforces traceability completeness server-side.
- Frontend must not guess which identifiers are required.
- Backend provides:
  - `issuancePolicy` and `issuanceBlockers[]` in invoice detail (preferred)
  - Structured 422 validation errors on issuance attempt containing the same blocker codes

### BillingRules (BILL-DEC-006, BILL-DEC-007)

- Upsert is idempotent.
- Concurrency control is ETag/If-Match (BILL-DEC-006).
- Enum/option lists are sourced from backend discovery endpoints (BILL-DEC-007).

### Checkout PO capture and override (BILL-DEC-009, BILL-DEC-010)

- PO capture is write-once in the checkout context.
- Override requires:
  - explicit permission
  - reason code (and optional reason notes)
  - supervisor/manager elevation token (BILL-DEC-010)
  - optional second approver per policy evaluation

### Receipts and reprints (BILL-DEC-011)

- Receipt generated on successful capture.
- Delivery policy evaluated server-side; UI renders `allowedActions[]` and `reprintPolicy` output.
- “No Receipt” suppresses delivery but does not suppress receipt generation (see BILL-DEC-011).

### Partial payments (BILL-DEC-012)

- Partial payments are supported unless disabled by account policy.
- Receipts reflect partial vs paid-in-full and display remaining balance.

### AP vendor payments (BILL-DEC-013)

- Execution idempotent by `paymentRef`.
- Validations:
  - grossAmount > 0
  - sum(allocations) ≤ grossAmount
  - bills must be open/payable
- GL posting is asynchronous; UI is read-only for GL posting status.

### Posting error visibility (BILL-DEC-014)

- `postingErrorSummary` for non-admin users is sanitized and non-sensitive.
- Detailed errors are restricted to finance/admin permissions.

---

## Canonical Enums and Statuses (Normative)

### InvoiceStatus (BILL-DEC-001)

Backend authoritative:

- `DRAFT`
- `ISSUED`
- `PAID`
- `VOID`

Optional (only if implemented; if not, do not invent in UI):

- `ERROR` (represents issuance/posting pipeline error state, not an editable state)

Frontend must treat any unknown enum as “unknown state” and require refresh/support.

### ReceiptDeliveryStatus (BILL-DEC-011)

- `PENDING`
- `SENT`
- `FAILED`
- `BOUNCED` (email only)

### AP Vendor Payment Status (BILL-DEC-013)

- `INITIATED`
- `GATEWAY_PENDING`
- `GATEWAY_FAILED`
- `GATEWAY_SUCCEEDED`
- `GL_POST_PENDING`
- `GL_POSTED`
- `GL_POST_FAILED`

---

## API Contracts (Normative)

Concrete Moqui service names may vary; the following HTTP semantics and field shapes are normative.

### Invoice

1) Create Draft from Work Order (idempotent)

- `POST /billing/invoices/draft`
  - Request: `{ "workOrderId": "uuidv7" }`
  - Response: full invoice snapshot recommended
  - Errors:
    - 409 with `{errorCode, message, reasonCode}` when not invoice-ready or already issued
    - 422 with `{errorCode, message, missingFields[]}` when billing data incomplete

1) Get Invoice Detail

- `GET /billing/invoices/{invoiceId}`
  - Includes: totals, breakdowns, line items, traceability snapshot, delivery preferences, issuance metadata
  - Includes: `issuancePolicy` and `issuanceBlockers[]` (BILL-DEC-003)

1) Issue Invoice (command)

- `POST /billing/invoices/{invoiceId}/issue`
  - Request: `{ "elevationToken": "string?" }` (required only if blockers require manager override)
  - Errors:
    - 422 blockers with structured codes
    - 409 if already issued/paid/void

1) Lookup by Work Order

- `GET /billing/invoices/by-workorder/{workOrderId}` (returns 404 if none)

### BillingRules

- `GET /billing/rules/{accountId}`
  - 200 returns BillingRules + ETag header
  - 404 means unconfigured
- `PUT /billing/rules/{accountId}`
  - Requires `If-Match: <etag>` when updating; omitting If-Match creates new config if absent
  - 409 on stale etag

### Discovery / options (BILL-DEC-007)

- `GET /billing/meta/payment-terms`
- `GET /billing/meta/invoice-delivery-methods`
- `GET /billing/meta/invoice-grouping-strategies`
- `GET /billing/meta/po-policies` (optional; if policy varies by tenant/account)
- `GET /billing/meta/reason-codes?category=PO_OVERRIDE|REPRINT|...`

### Artifact retrieval (BILL-DEC-005)

- List artifacts for invoice context:
  - `GET /billing/invoices/{invoiceId}/artifacts`
  - Response: `artifacts[]` with `{artifactRefId, type, createdAt, displayName, contentType, sizeBytes}`
- Secure download:
  - Option A (preferred): `POST /billing/artifacts/{artifactRefId}/download-token` → `{downloadToken, expiresAt}`
    then `GET /billing/artifacts/download?token=...`
  - Option B: time-limited signed URL returned in list only when requested via `?includeSignedUrls=true` and authorized
- Never log signed URLs or tokens.

### Checkout PO (BILL-DEC-009)

- `POST /billing/checkout/{checkoutId}/evaluate` → BillingRuleEvaluation
- `POST /billing/checkout/{checkoutId}/po` (write-once)
- `POST /billing/checkout/{checkoutId}/po-override`
  - requires elevation token and reason codes per policy

### AP Vendor Payments (BILL-DEC-013)

- `GET /billing/ap/vendors/{vendorId}/bills?status=OPEN`
- `POST /billing/ap/payments`
  - Request includes `{vendorId, grossAmount, currencyUomId, paymentRef, allocations[]?}`
- `GET /billing/ap/payments/{billingPaymentId}`
- `GET /billing/ap/payments/by-ref/{paymentRef}` (optional)

### Receipts (BILL-DEC-011)

- `GET /billing/receipts/by-invoice/{invoiceId}`
- `POST /billing/receipts/{receiptId}/deliver` (channel=PRINT|EMAIL)
- `POST /billing/receipts/{receiptId}/reprint`
- Receipt content:
  - Print: printer-ready plain text (ESC/POS-friendly) or a backend-provided print template output
  - Email: PDF attachment or HTML body generated server-side
Frontend must not implement printer-width formatting unless contract explicitly requires it.

---

## Permission Model (Normative) (BILL-DEC-008)

Canonical permission strings:

| Area | Action | Permission |
|---|---|---|
| Invoice | View invoice | `billing:invoice:view` |
| Invoice | Create draft | `billing:invoice:create-draft` |
| Invoice | Issue | `billing:invoice:issue` |
| Invoice | Void | `billing:invoice:void` |
| Traceability | View traceability panel | `billing:traceability:view` |
| Artifacts | List artifacts | `billing:artifact:list` |
| Artifacts | Download artifact | `billing:artifact:download` |
| BillingRules | View | `billing:rules:view` |
| BillingRules | Manage | `billing:rules:manage` |
| Checkout PO | Capture PO | `billing:checkout:po:capture` |
| Checkout PO | Override PO | `billing:checkout:po:override` |
| Supervisor | Elevation (re-auth) | `billing:auth:elevate` |
| Receipts | View | `billing:receipt:view` |
| Receipts | Deliver (print/email) | `billing:receipt:deliver` |
| Receipts | Reprint | `billing:receipt:reprint` |
| AP | View bills | `billing:ap:bill:view` |
| AP | Execute payment | `billing:ap:payment:execute` |
| AP | View payment detail | `billing:ap:payment:view` |
| Posting details | View detailed posting errors | `billing:posting-error:detail:view` |

---

## Observability (Normative) (BILL-DEC-015)

Correlation/tracing standard is W3C Trace Context:

- `traceparent` required
- `tracestate` optional

Logs must include identifiers (non-PII):

- `invoiceId`, `workOrderId`, `accountId`, `billingPaymentId`, `paymentRef` (if permitted), `receiptId`
Never log: PAN/CVV, full billing address/email, signed URLs, tokens, manager PINs/codes.

---

## Deep Linking (Normative) (BILL-DEC-016)

Billing APIs return link metadata (not routes):

- `{ targetDomain, targetType, targetId }`
Frontend resolves to routes per domain router and enforces auth on destination.

---

## Open Questions — Resolved (Full Q/A with provenance)

> Questions are restated from the prior AGENT_GUIDE “Open Questions from Frontend Stories” section and answered below. Each answer is now normative and reconciles TODO/CLARIFY items.

### Source: AGENT_GUIDE.md — A) Invoice traceability & issuance gating

**Question A1:** Backend contract (required): What are the exact field names returned for estimate reference: `sourceEstimateId` vs `sourceEstimateVersionId` vs both? Provide the canonical response schema used by the frontend.  
**Response:** Use both fields with these canonical names inside `traceability`:

- `sourceEstimateId` (optional)
- `sourceEstimateVersionId` (optional, preferred when present)
If only one is available upstream, populate the available one; do not rename. (BILL-DEC-004)

**Question A2:** Issuance gating UX: Does the invoice detail payload include an explicit `issuanceBlockers` (or similar) array we should render/interpret, or must the frontend only learn blockers from the issuance attempt (422 response)?  
**Response:** Invoice detail **must** include `issuancePolicy` and `issuanceBlockers[]` for draft invoices. Issuance attempts returning 422 must include the same blocker codes. UI must not guess. (BILL-DEC-003)

**Question A3:** Permissions: What are the exact permission IDs/roles for viewing traceability panel, issuing invoice, and viewing/downloading approval artifacts?  
**Response:** Canonical permissions:

- Traceability panel: `billing:traceability:view`
- Issue invoice: `billing:invoice:issue`
- Artifacts: `billing:artifact:list` and `billing:artifact:download` (BILL-DEC-008)

**Question A4:** Artifact retrieval API: What service/endpoint returns approval artifacts from invoice context? Input: invoiceId vs approvalIds. Output: signed URL vs separate download endpoint.  
**Response:** Input is `invoiceId`. Billing exposes:

- `GET /billing/invoices/{invoiceId}/artifacts` for list
- Secure download via tokenized endpoint (preferred) or short-lived signed URLs when explicitly requested and authorized. (BILL-DEC-005)

**Question A5:** Policy specificity: Which traceability identifiers are policy-required to issue (approval always? estimate version always? configurable per customer/account)? Frontend must not guess.  
**Response:** Policy is configurable per tenant/account and returned as `issuancePolicy`:

- Always required: `sourceWorkOrderId`, `sourceBillableScopeSnapshotId`, `sourceSchemaVersion`
- Conditionally required:
  - `sourceApprovalIds[]` required for consumer/B2C accounts or where “approvalRequired=true”
  - `sourceEstimateVersionId` required when an estimate exists and policy requires estimate-binding
Backend returns blockers with codes (e.g., `MISSING_APPROVAL`, `MISSING_ESTIMATE_VERSION`). (BILL-DEC-003, BILL-DEC-004)

**Question A6:** Navigation links: Do screens/routes exist for Work Order, Estimate, Approval record? If not, should identifiers be copy-only?  
**Response:** Billing returns link metadata only; UI may render links if the route exists. If route does not exist, display identifier with “copy” action only. Do not block issuance on link availability. (BILL-DEC-016)

### Source: AGENT_GUIDE.md — B) Receipt generation, delivery, reprint

**Question B1:** Moqui contract: exact screen paths and services for fetch/create receipt, request email delivery, request reprint, record print outcome.  
**Response:** Billing exposes receipt capabilities via HTTP endpoints:

- `GET /billing/receipts/by-invoice/{invoiceId}`
- `POST /billing/receipts/{receiptId}/deliver`
- `POST /billing/receipts/{receiptId}/reprint`
Print outcomes are recorded implicitly by deliver/reprint response; no separate endpoint unless required by printer integration. (BILL-DEC-011)

**Question B2:** Partial payments: supported? Should receipt show “PARTIAL PAYMENT” and remaining balance?  
**Response:** Partial payments are supported unless account policy disables them. Receipt and invoice view must display payment type:

- `PAID_IN_FULL` vs `PARTIAL_PAYMENT`
and show remaining balance for partial payments. (BILL-DEC-012)

**Question B3:** Failure handling: when email delivery fails, can UI retry? statuses?  
**Response:** Statuses are `PENDING`, `SENT`, `FAILED`, `BOUNCED`. UI may retry delivery for `FAILED` or `BOUNCED` if policy returns `allowedActions` includes `RETRY_DELIVERY`; otherwise instruct user to update email/address in CRM then retry. (BILL-DEC-011)

**Question B4:** Reprint policy source: backend returns `allowedActions`/policy evaluation?  
**Response:** Yes. Backend returns:

- `reprintPolicy` (e.g., `remainingReprints`, `windowEndsAt`, `requiresReasonCode`, `requiresElevation`)
- `allowedActions[]`  
Frontend must not hardcode thresholds. (BILL-DEC-011)

**Question B5:** Permissions for print/email/reprint and supervisor override?  
**Response:** Canonical permissions:

- Deliver: `billing:receipt:deliver`
- Reprint: `billing:receipt:reprint`
- Elevation: `billing:auth:elevate` (BILL-DEC-008, BILL-DEC-010)

**Question B6:** Printing payload type: plain text vs HTML vs PDF; does frontend format to 40–48 chars?  
**Response:** Backend provides printer-ready content. Frontend does not format widths unless contract explicitly requires it. Email uses server-generated PDF/HTML. (BILL-DEC-011)

**Question B7:** “No Receipt” behavior: backend state transition or suppress delivery only?  
**Response:** “No Receipt” means `deliverySuppressed=true` and no delivery attempt is made, but receipt record still exists for audit and later reprint (if allowed). (BILL-DEC-011)

### Source: AGENT_GUIDE.md — C) Invoice retrieval, draft creation, enums, approvals

**Question C1:** Invoice retrieval contract: load by `workOrderId`, `invoiceId`, or both? exact service names/parameter names?  
**Response:** Both are supported:

- `GET /billing/invoices/{invoiceId}`
- `GET /billing/invoices/by-workorder/{workOrderId}`  
Frontend chooses by context: from Work Order use by-workorder, from invoice list use invoiceId. (BILL-DEC-002)

**Question C2:** Draft creation behavior: show “no invoice” only, or call create draft automatically or via button?  
**Response:** Must be user-initiated via explicit action (button) unless the screen is explicitly an “Invoice Builder” workflow. Backend is idempotent so retries are safe. (BILL-DEC-002)

**Question C3:** Manager approval input semantics: exact “manager approval code” format and rules (code vs re-auth vs token)?  
**Response:** Use re-auth elevation token, not a stored “manager code”:

- `POST /billing/auth/elevate` with manager credentials (PIN/password/SSO step-up)
- Returns short-lived `elevationToken` (e.g., 5 minutes, single-use)
UI never logs or stores raw credentials. (BILL-DEC-010)

**Question C4:** Permission source: session claims vs backend-provided `canFinalize` flags?  
**Response:** Backend returns both:

- Server-enforced permission checks
- `allowedActions[]` and computed flags in invoice detail for UX
UI must not rely on flags for security; backend remains authoritative. (BILL-DEC-008)

**Question C5:** Posting/error display policy: for `postingErrorSummary`, what is safe to show to Service Advisors?  
**Response:** Service Advisor view shows only sanitized, non-sensitive summaries (codes + generic text). Detailed posting errors are restricted behind `billing:posting-error:detail:view`. No stack traces, no account numbers, no internal system identifiers beyond correlationId. (BILL-DEC-014)

**Question C6:** State naming: exact enum values returned (DRAFT/EDITABLE vs FINALIZED/POSTED/ERROR)?  
**Response:** InvoiceStatus is:

- `DRAFT`, `ISSUED`, `PAID`, `VOID`  
Optional: `ERROR` only if implemented. UI must not invent “EDITABLE/FINALIZED/POSTED”; those are UI concepts derived from status:
- editable if `status==DRAFT`
- finalized if `status in {ISSUED,PAID,VOID}` (BILL-DEC-001)

## Todos Reconciled

- None (this domain guide’s work items are captured as Decision IDs and the checklist’s resolved open questions).

---

## End

End of document.
