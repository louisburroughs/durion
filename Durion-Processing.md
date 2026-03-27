# Durion Processing — Wave D: Accounting Domain (CAP-049–055)

**Status: IN PROGRESS**
**Branch:** `cap/accounting-wave-d`
**Base:** `master` (4f65a78)
**Target repo:** `durion-positivity-frontend`
**PR target:** TBD

---

## Domain Ownership

| Domain | Capability | Stories | Angular Feature |
|--------|-----------|---------|-----------------|
| `accounting` | CAP-049 | 208, 207, 206, 205, 177, 179, 180, 181, 183, 184, 185, 182 | `src/app/features/accounting/` |
| `accounting` | CAP-050 | 202 | `src/app/features/accounting/` |
| `accounting` | CAP-051 | 178 | `src/app/features/accounting/` |
| `accounting` | CAP-052 | 195 | `src/app/features/accounting/` |
| `accounting` | CAP-053 | 192 | `src/app/features/accounting/` (missing wireframe — design fallback required) |
| `accounting` | CAP-054 | 123 | Cross-domain: `src/app/features/workexec/` (operational cost display) |
| `accounting` | CAP-055 | 186 | `src/app/features/accounting/` |

## Accounting Deferred / Blocked

| Capability | Status | Reason |
|-----------|--------|--------|
| CAP-275 | `deferred` | Server-side Moqui JWT story; contract status = `draft`; frontend is optional admin UI only |

## Operation ID Resolution Notes

All accounting worksets have empty `operation_ids`. These will be resolved via direct OpenAPI inspection of
`durion-positivity-backend/pos-accounting/openapi.yaml` during implementation (same approach as CAP-007).

---

## Steps

- [ ] Step 1: Read source materials — PRDs, all 7 capability manifests + worksets, all story markdown files, wireframes, accounting contract guide, pos-accounting OpenAPI
- [ ] Step 2: Designer first-pass — produce design brief for accounting domain (layout guidance, token usage, responsive expectations, table/ledger patterns)
- [ ] Step 3: Create branch `cap/accounting-wave-d` from `master`
- [ ] Step 4: Inspect `pos-accounting/openapi.yaml` — map operation IDs per story; record in this file under "Resolved Operation IDs"
- [ ] Step 5: anvil — decompose CAP-049 stories into implementation cards (HTML Specialist + TypeScript Specialist ownership)
- [ ] Step 6: Implement CAP-049 — TypeScript (models, accounting service, event envelope + event ingestion views)
- [ ] Step 7: Implement CAP-049 — HTML/CSS (templates, table/ledger layouts, loading/error/empty states)
- [ ] Step 8: anvil — decompose CAP-050–053, CAP-055 stories into implementation cards
- [ ] Step 9: Implement CAP-050–053, CAP-055 — TypeScript (posting config, AR payment, credit memo, error routing pages)
- [ ] Step 10: Implement CAP-050–053, CAP-055 — HTML/CSS (form/table templates, styles)
- [ ] Step 11: Implement CAP-054 — cross-domain operational cost display in workexec domain
- [ ] Step 12: Register accounting routes in `app.routes.ts`; update shell navigation entries
- [ ] Step 13: Designer final sign-off on integrated accounting UI
- [ ] Step 14: Code Review Agent — accounting acceptance, ADR compliance, regression check
- [ ] Step 15: Iterate fixes until Code Review returns PASS
- [ ] Step 16: Build verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`)
- [ ] Step 17: Test verification (`npm test -- --watch=false`)
- [ ] Step 18: Fix any build/test failures
- [ ] Step 19: Test Coverage Agent — harden accounting domain coverage
- [ ] Step 20: Update run artifacts for CAP-049, CAP-050, CAP-051, CAP-052, CAP-053, CAP-054, CAP-055
- [ ] Step 21: Commit all changes
- [ ] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --story accounting-wave-d --base master --head cap/accounting-wave-d --title "feat(accounting): Wave D — Accounting Domain (CAP-049–055)"`

---

## Resolved Operation IDs

| Story | CAP | operationId(s) | API Path | RO/RW | Notes |
|-------|-----|---------------|----------|-------|-------|
| 208 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events` | RO | Event envelope contract viewer |
| 207 | 049 | `submitEvent` | POST `/v1/accounting/events` | RW | Event sync endpoint; confirm path with contract guide |
| 205 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events` | RO | Event validation display |
| 206 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events` | RO | Idempotency display |
| 177 | 049 | `listEvents`, `getEvent`, `recordRefund` | GET `/v1/accounting/events`; POST audit | RO | Refund visibility via events |
| 179 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events?eventType=PaymentReceived` | RO | PaymentReceived ingestion monitor |
| 180 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events?eventType=InvoiceAdjusted` | RO | InvoiceAdjusted ingestion monitor |
| 181 | 049 | `listEvents`, `getEvent`, `retryEventProcessing`, `getReprocessingHistory` | GET/POST `/v1/accounting/events*` | RO+retry | InvoiceIssued monitoring with retry |
| 182 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events?eventType=WorkorderReversed` | RO | Reverse completion monitor |
| 183 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events?eventType=WorkCompleted` | RO | WorkCompleted ingestion monitor |
| 184 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events?eventType=InventoryAdjustment` | RO | InventoryAdjustment monitor |
| 185 | 049 | `listEvents`, `getEvent` | GET `/v1/accounting/events?eventType=InventoryIssued` | RO | InventoryIssued monitor |
| 202 | 050 | `listPostingRuleSets`, `createPostingRuleSet`, `updatePostingRuleSet`, `publishPostingRuleSet`, `archivePostingRuleSet` | GET/POST/PUT `/v1/accounting/posting-rule-sets*` | RW | Configure event type posting mappings |
| 178 | 051 | `applyPayment` | POST `/v1/accounting/payment-applications` | RW | Apply payment to invoice; `applicationRequestId` idempotency key |
| 195 | 052 | `createCreditMemo`, `listCreditMemos`, `getCreditMemo` | POST/GET `/v1/accounting/credit-memos*` | RW | Issue credit memo/refund |
| 192 | 053 | `listBills`, `executePayment`, `getPayment`, `getPaymentByRef` | GET/POST `/v1/accounting/vendor-bills*`, `/v1/accounting/payments*` | RW | AP vendor payment; `paymentRef` idempotency |
| 123 | 054 | None (workexec domain; uses Shopmgr context) | N/A | RO | Cross-domain cost display in workexec; no accounting API calls |
| 186 | 055 | `listEvents`, `getEvent`, `retryEventProcessing`, `reprocessSuspendedEvent`, `getReprocessingHistory` | GET/POST `/v1/accounting/events*` | RO+retry | Failed/quarantined event routing |

**SDK packages used:** `sdk-accounting` (`AccountingEventsApi`, `PaymentApplicationsApi`, `CreditMemosApi`, `PostingRulesApi`, `APPaymentsApi`, `AuditTrailApi`)

---

## Completed Waves

| Wave | Capabilities | Domain | PRs |
|------|-------------|--------|-----|
| CRM Wave A | CAP-089, CAP-090, CAP-091, CAP-092 (partial) | `crm` | PR #1, #2 |
| Workexec Wave B | CAP-002, CAP-003 | `workexec` | PR #3 |
| Workexec Wave B-cont | CAP-004, CAP-005 | `workexec` | PR #4 |
| Workexec+Billing Wave C | CAP-006, CAP-007 | `workexec`, `billing` | PR #5 |

---

# ── ARCHIVED ── Wave C: CAP-006 + CAP-007

**Status: COMPLETED** | **PR:** #5 | **Branch:** `cap/workexec-wave-c`
