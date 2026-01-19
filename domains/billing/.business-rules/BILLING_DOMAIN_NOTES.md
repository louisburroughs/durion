# BILLING_DOMAIN_NOTES.md

## Summary

This document provides rationale, tradeoffs, and audit/security considerations for the Billing domain’s normative decisions.
It is intentionally non-normative and must not be used directly for agent execution or CI validation.

## Completed items

- [x] Documented rationale for each `BILL-DEC-###` in `AGENT_GUIDE.md`
- [x] Preserved decision IDs and anchors for deep linking

## Intended uses

- Architecture discussions
- Auditor explanations
- Future design work
- Governance review

## Forbidden uses

- Direct agent input
- CI validation
- Story execution rules

This document explains the rationale, tradeoffs, and audit/security considerations behind the normative decisions in `AGENT_GUIDE.md`.

---

## BILL-DEC-001 — Canonical Invoice Status Enums and State Machine

### Summary

Billing should keep invoice status enums small, stable, and audit-friendly. A compact enum set reduces UI drift and contract test complexity.

### Rationale

- Multiple teams naturally invent synonyms (“FINALIZED”, “POSTED”, “EDITABLE”). That causes brittle mapping and inconsistent logic.
- A minimal state machine lets downstream systems (Accounting, reporting, customer comms) interpret invoice state consistently.

### Notes

- `ERROR` is optional. If introduced, it must be clearly defined as a pipeline/processing state, not an editable state.
- “Posted” is an Accounting concept; Billing may expose accounting posting acknowledgements separately without encoding them into invoice status.

---

## BILL-DEC-002 — Draft Invoice Creation Command (Idempotency + Work Order Preconditions)

### Summary

Draft creation must be idempotent and gated by upstream Work Execution’s `invoiceReady`.

### Rationale

- Operators will click twice; networks will retry; UIs may re-submit. Idempotency prevents duplicate drafts.
- Preconditions protect against billing incomplete work or missing approvals.

### Tradeoffs

- User-initiated creation avoids surprise invoices but adds one click.
- Auto-create may be appropriate in a guided workflow but should be explicit.

### Audit perspective

Draft creation should be auditable: who initiated, when, which workOrderId, what blockers were present.

---

## BILL-DEC-003 — Issuance Gating Model (Blockers + Policy Envelope)

### Summary

Issuance requirements must be backend-enforced and visible to the user in advance via `issuancePolicy` + `issuanceBlockers[]`.

### Rationale

- Frontend guessing creates compliance holes and inconsistent behavior.
- Blockers provide deterministic UX: users see exactly what is missing and can resolve it before attempting issuance.
- Policy must be tenant/account configurable (B2C vs B2B differences, manufacturer programs, regulated services, etc.).

### Auditor explanation

“We can demonstrate, per issuance, which policy version applied and which prerequisites were satisfied.”

### Future considerations

- Policy versioning: store a `policyVersion` on the invoice at issuance for audit defensibility.
- Additional blockers may be added later; backward compatibility requires stable blocker codes.

---

## BILL-DEC-004 — Traceability Snapshot Schema (Canonical Field Names + Immutability)

### Summary

Traceability is a persistent, immutable evidence layer attached to invoices.

### Rationale

- In disputes, regulators and customers ask: “what did you agree to, and when?”
- Keeping references (work order, estimate version, approvals) immutable prevents tampering narratives.

### Field name stability

Supporting both `sourceEstimateId` and `sourceEstimateVersionId` allows:

- Systems that only know the estimate “header” to still link
- Systems that version estimates to link to the exact agreed version

### Risks

- If upstream data disappears, Billing still retains references; artifact retrieval must remain secure and time-bound.

---

## BILL-DEC-005 — Artifact Retrieval Contract (List + Secure Download)

### Summary

Artifacts are retrieved by invoice context with list + secure download primitives.

### Rationale

- Artifact content is sensitive and often legally relevant (signatures, approvals).
- Signed URLs are effectively bearer tokens; they must be short-lived, scoped, and never logged.

### Contract options

- Tokenized download endpoint is preferable when you want uniform auditing and centralized access control.
- Signed URLs can be acceptable when storage is external and you need direct download; still require short TTL and per-user authorization.

### Auditor explanation

“Artifact access is permissioned and logged; links expire.”

---

## BILL-DEC-006 — BillingRules Concurrency Model (ETag/If-Match)

### Summary

Use ETag/If-Match rather than ad hoc `version` fields to avoid silent overwrites.

### Rationale

- Web-native concurrency control with clear semantics.
- Avoids accidental last-write-wins in administrative configuration.

### UX notes

- UI should present a conflict resolution message and reload the latest rules on 409.

---

## BILL-DEC-007 — BillingRules Options/Enums Discovery Endpoints

### Summary

The UI must not hardcode delivery/grouping/payment terms options.

### Rationale

- Options change by tenant policy, geography, integrations, and legal constraints.
- Discovery endpoints allow controlled rollout and A/B changes without redeploying frontend.

### Future considerations

- Cache with ETag to reduce call volume.
- Localize display labels per tenant locale.

---

## BILL-DEC-008 — Permission Model (Canonical Permission Strings)

### Summary

Use stable permission strings with least privilege and explicit separation of view vs manage vs sensitive operations.

### Rationale

Billing operations include irreversible actions (issue invoice, override PO) and sensitive actions (download artifacts). Least privilege is required.

### Auditor explanation

“We can show which permission enabled a given action and who performed it.”

---

## BILL-DEC-009 — Checkout PO Capture and Override Workflow Contract

### Summary

PO capture is write-once and validated server-side; override is explicit and auditable.

### Rationale

- PO is often a contractual control for commercial accounts.
- Write-once prevents silent PO mutation after approval.
- Override must be rare, permissioned, and justified.

### Future considerations

- Uniqueness scope varies (account-level, location-level, tenant-wide). Policy must define it.

---

## BILL-DEC-010 — Manager/Supervisor Approval Semantics (Re-auth Token)

### Summary

Replace “manager approval code” with step-up authentication that returns a short-lived elevation token.

### Rationale

- “Codes” tend to get shared, written down, and reused.
- Step-up auth supports MFA/SSO and reduces credential leakage.
- Tokens can be single-use and TTL-limited; better audit story.

### Auditor explanation

“We don’t store or log manager secrets; we record that an elevated approval occurred and who approved it.”

---

## BILL-DEC-011 — Receipts: Generation, Delivery Statuses, Reprint Policy Evaluation

### Summary

Receipts are generated on successful capture; delivery and reprint are governed by backend policy evaluation.

### Rationale

- Reprint and delivery rules are compliance- and fraud-sensitive.
- Backend policy evaluation avoids hardcoded UI thresholds.

### Notes

- “No Receipt” should suppress delivery but retain the receipt record for audit and later authorized reprint.

---

## BILL-DEC-012 — Partial Payments Policy and Receipt Display Rules

### Summary

Partial payments must be explicitly represented to prevent disputes and double-billing.

### Rationale

- Partial payments are common in B2B fleet operations and negotiated settlements.
- A receipt that looks “final” when it isn’t is a customer dispute generator.

### Future considerations

- Account policy could disable partial payments for certain customers or payment methods.

---

## BILL-DEC-013 — AP Vendor Payments: Status Enums, Idempotency, Allocation Rules

### Summary

AP payments are high-risk; idempotency and explicit statuses are mandatory.

### Rationale

- Duplicate AP payments are expensive and hard to recover.
- Allocation errors cause reconciliation and vendor disputes.
- GL posting is asynchronous; UI must never imply that “payment executed” == “posted.”

---

## BILL-DEC-014 — Posting Error Visibility Policy (Sanitization + Role-Targeted Detail)

### Summary

Split error visibility between frontline users and finance/admin.

### Rationale

- Posting errors can contain sensitive financial mapping data and internal diagnostics.
- Frontline users need actionable steps, not stack traces.

### Implementation notes

- Provide a stable `postingErrorCode` and a short user-safe message.
- Provide a `correlationId` for support.
- Gate detailed diagnostics with a dedicated permission.

---

## BILL-DEC-015 — Observability: Correlation/Tracing Standard (W3C Trace Context)

### Summary

Use W3C Trace Context for end-to-end tracing across WorkExec, CRM, Payment, Accounting, and Artifact services.

### Rationale

- Troubleshooting issuance/payment issues requires cross-service correlation.
- Standard headers reduce custom instrumentation.

---

## BILL-DEC-016 — Frontend Deep-Link Metadata (No Hardcoded Routes)

### Summary

Return link metadata, not URLs; frontend resolves routes.

### Rationale

- Routes change, permissions evolve, and hardcoded URLs in data are brittle and can create unsafe coupling.
- This preserves authorization boundaries and keeps APIs stable.

---

## End

End of document.
