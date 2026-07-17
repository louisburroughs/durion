# Odoo Parity Plan — pos-accounting

> Status: DRAFT for review · Created 2026-07-17 · Branch: `claude/pos-accounting-odoo-parity-idfuje`
>
> Goal: bring `durion-positivity-backend/pos-accounting` to functional parity with the Odoo 19 `addons/account`
> capabilities that matter for a US, single-currency, event-driven POS platform — without breaking established
> Durion conventions. Companion plan: `plan-odoo-parity-pos-tax.md` (tax engine parity; several stories here
> depend on it).
>
> Sources: `comp-accounting-overview.md`, `comp-reconciliation-internals.md`,
> `comp-vs-pos-accounting-comparison.md`, `accounting-questions.md` / `.txt`, code survey of
> `pos-accounting` (2026-07-17), platform ADRs (`durion/docs/adr/`), accounting business rules
> (`domains/accounting/.business-rules/`).

---

## 0. Ground rules for the executing agent team

These are non-negotiable constraints. Every story below must be validated against them before merge.

1. **ADR-0044 (event-only domain walls).** pos-accounting is a DOMAIN module: inbound and outbound
   integration is Kafka events only. New cross-service behavior (invoice tax breakdown, payment GL posting
   triggers, reconciliation feeds) arrives via events consumed from `{domain}.events.v1` topics and local
   `ext_*` replicas — never via new RestClients. Commands out of accounting are command events with pending
   state + idempotency key. Outbox (`EventOutbox`/`OutboxProcessor`) and `ProcessedEvent` dedup are the
   required reliability rails and already exist — reuse them.
2. **ADR-0013/0027**: UUID v7 IDs via `@UUIDv7Id`; UUID-typed identifiers in DTOs/services.
   **ADR-0024**: `createdAt`/`updatedAt` via Spring auditing with injected `Clock`. **ADR-0018**: actor
   fields from `SecurityContextHelper`, never from request body.
3. **ADR-0017/0042**: canonical status codes (incl. 202 for async command acceptance), `ApiError` envelope,
   full OpenAPI annotation set; controller change ⇒ regenerate `openapi.yaml` ⇒ update Angular SDK
   (per `durion/CLAUDE.md`).
4. **Accounting domain decisions AD-001…AD-015** (`.business-rules/BACKEND_CONTRACT_GUIDE.md` /
   `AGENT_GUIDE.md`): AR reduced only by application records (AD-002), overpayment → customer credit
   (AD-003), apply-payment idempotent + atomic (AD-010), posting only in open periods (AD-012), HALF_UP
   at currency scale rounded per line then summed, debit≈credit within ±0.0001.
5. **Module conventions**: entities in `internal/entity`, services behind `service/` interfaces
   (ADR-0026, ArchUnit-enforced), `@EmitEvent` + `EventTypes` registry entry for every mutating endpoint,
   permissions added to `pos-accounting/src/main/resources/permissions.yaml`
   (note: this module is YAML-driven, which diverges from the code-first `{Module}PermissionRegistry`
   convention in the backend `CLAUDE.md` — keep the YAML pattern; do not migrate mid-plan), Flyway
   migrations starting at **V8** (V1–V7 live post-baseline-reset; do not touch archives), Spotless +
   Checkstyle + SpotBugs must pass, ArchUnit after any package change.
6. **Scope guard (explicit non-goals, confirmed by questions docs + comp docs):** multi-currency/FX
   gain-loss, storno, multi-company, statutory localization (chart templates, tax grids, EDI/Peppol),
   price-included pricing, cash-basis tax exigibility. Currency columns stay `USD`-defaulted. Do not build
   these even where Odoo has them.

**Where Odoo is the reference vs where Durion already wins.** Adopt from Odoo: storage-boundary balance
enforcement, gapless posted-entry numbering, reversal-as-linked-entry lifecycle, lock-date/period model,
"reverse, never delete" side-effect symmetry, rounding test vectors, data-driven report definitions.
Keep Durion's approach (do NOT import Odoo's): event-driven posting rules instead of in-document
`_sync_dynamic_lines`, document-level payment application instead of line-level partials, outbox/idempotency
rails, jurisdiction-first tax delegation, single stateless tax calculator.

---

## 1. Gap register (evidence-based)

| # | Gap vs Odoo | Current state (evidence) | Workstream |
|---|---|---|---|
| G1 | Balance invariant only at service layer | `JournalEntryServiceImpl.validateBalance` (±0.0001); no DB constraint in `V1__baseline_accounting_schema.sql` | A |
| G2 | No journal-entry numbering (gapless or otherwise) | No number column; UUID-only identification | A |
| G3 | Reversal lifecycle half-wired | `reverseJournalEntry` never sets `REVERSED`, never writes `reversalJournalEntry`/`reversedByJournalEntry` links | A |
| G4 | No inalterability/hash chain | Soft immutability via service guards only | A (decision) |
| G5 | Period service is a stub — every period is open | `AccountingPeriodServiceImpl.isPeriodOpen` returns `true`; no entity, no lock dates; violates AD-012 | B |
| G6 | AR cash-receipt GL posting unwired | `GLPostingServiceImpl.postPaymentApplication` has zero callers; invoice balance derived, ledger never sees cash receipt | C |
| G7 | Payment-application reversal produces no reversing JE | `PaymentApplicationReversal` row only | C |
| G8 | No auto-allocation ordering for payments | Caller-supplied order only | C |
| G9 | Weak concurrency guard on `ReceivablePayment` | No `@Version`/lock; relies on uniq constraint | C |
| G10 | Credit-memo tax hardcoded at 10%, single tax-payable account | `CreditMemoServiceImpl.calculateCreditAmounts:149` | D (needs tax plan WS-T5) |
| G11 | Posting rules: eventType-equality conditions only; no proportional split of one amount | `PostingRuleEvaluatorImpl.matchesCondition:511`, `resolveAmount:549` | E |
| G12 | Bank/statement reconciliation is a dead scaffold | `Reconciliation` entity + repo exist; no service/controller; no statement ingestion | F |
| G13 | Reporting lacks trial balance, GL report, aged AR/AP | Only income statement, balance sheet, labor/overhead, drilldowns | G |
| G14 | Thin COA: 5 flat account types, no reconcilable flag, no tags, ~2 seeded accounts | `GLAccount`, `R__seed_reference_accounting.sql` | H |
| G15 | No fiscal-position analog (per-counterparty account substitution) | `GLMappingResolver` keys on category/key/dimensions/date only | E (partial) / open question |

---

## 2. Workstream A — Ledger integrity hardening

Odoo reference: `_check_balanced` SQL assertion, `sequence.mixin` gapless numbering,
`_reverse_moves` linkage, `inalterable_hash`.

### Story A1 — DB-level balance enforcement
- **Change**: Postgres `CONSTRAINT TRIGGER` (deferrable, initially deferred) on `journal_entry_line`
  insert/update/delete that sums debits/credits per `journal_entry_id` and rejects
  `abs(Σdebit − Σcredit) > 0.0001` when the parent entry status is `POSTED`; plus a simple CHECK that
  a line has exactly one of debit/credit non-negative populated (match current entity semantics first —
  verify whether lines store signed amounts or debit+credit columns before writing SQL).
- **Files**: Flyway `V8__je_balance_constraint.sql`; keep the existing service-layer check (belt and
  braces — service gives the friendly `JE_NOT_BALANCED` 422, trigger is the storage backstop).
- **AC**: direct SQL insert of an unbalanced posted entry fails; full test suite green; an IT proves the
  trigger fires and that DRAFT entries are exempt (drafts may be transiently unbalanced while edited).
- **Effort**: S. **Deps**: none.

### Story A2 — Posted-entry numbering (gapless per period)
- **Design**: number assigned only at POST time (drafts unnumbered — mirrors Odoo `/draft/` naming),
  format `JE-{YYYYMM}-{seq}` with a per-month sequence row locked `FOR UPDATE` at assignment
  (`accounting_sequence` table: `scope_key` uniq, `next_value`). Gapless is achievable because POST is
  the only assignment point and runs in the same tx as the status flip. Do **not** introduce Odoo's
  per-journal partitioning — Durion has no journal concept; sequence scope is `entry-month`.
  Add `entry_number` (uniq, nullable) to `journal_entry`.
- **API**: expose `entryNumber` in JE DTOs/list filters; OpenAPI + SDK regeneration.
- **AC**: two concurrent posts get consecutive numbers (IT with parallel threads); reversals get their own
  numbers; gap-detection query (`generate_series` vs stored) available as a repository method used by G-workstream
  reports.
- **Research flag**: confirm with product whether statutory gapless numbering is actually required for a US
  POS or whether ordinal-per-month is enough — if not required, a plain sequence (gaps allowed on rollback)
  is simpler; decide before implementation. (Open question OQ-1.)
- **Effort**: M. **Deps**: none.

### Story A3 — Finish the reversal lifecycle
- **Change**: `reverseJournalEntry` must (1) set original status → `REVERSED`, (2) write both linkage FKs,
  (3) stamp `reversedAt`/actor, (4) refuse to reverse an already-reversed entry (409), (5) accept an
  optional `reversalDate` validated against open periods (B-workstream) defaulting to original date if its
  period is open, else current open period — Odoo's date-picking behavior, simplified.
- **Also**: emit `ACCOUNTING_JOURNAL_ENTRY_REVERSE` (already registered? verify in `EventTypes`) and an
  outbound domain event so downstream read models see the flip.
- **AC**: reversal round-trip test asserts status, linkage both directions, traceability endpoint shows the
  pair; double-reversal rejected; mapper exposes linkage fields (they already exist in schema/mapper —
  wire, don't add).
- **Effort**: S. **Deps**: B1 for period validation (can land first with date defaulting stubbed to "any").

### Story A4 — Inalterability hash chain (DECISION REQUIRED, default: defer)
- Odoo's chained-hash mode exists for jurisdictions that mandate tamper-evidence. US POS context likely
  does not require it; Durion already has `AccountingAuditLog` + event lineage which the comp doc rates
  as stronger operationally. **Recommendation: record as explicit non-goal via a short ADR**
  (`/create-adr`), revisit if a compliance requirement appears. If product says build it: chain =
  SHA-256(prev_hash ‖ entry_number ‖ posted payload canonical JSON), column on `journal_entry`, verified
  by a scheduled integrity job; one-way "restrict mode" flag per deployment. (Open question OQ-2.)
- **Effort**: M if built. **Deps**: A2 (numbering defines chain order).

---

## 3. Workstream B — Accounting periods and lock dates

Odoo reference: five lock dates + per-user time-boxed exceptions with audit. Durion simplification:
one period lifecycle + one hard-lock date + permissioned override with audit. This unblocks AD-012,
which the current stub silently violates.

### Story B1 — AccountingPeriod entity + service
- **Model**: `accounting_period` table — `period_id` (UUIDv7), `period_code` (`YYYY-MM`, uniq),
  `start_date`, `end_date`, `status` (`OPEN` / `CLOSING` / `CLOSED`), `closedAt/By`, `reopenedAt/By`,
  audit columns. Flyway `V9`. Auto-provision: first posting into a nonexistent period creates it OPEN
  (keeps today's zero-admin behavior); backfill migration creates periods spanning existing JE dates.
- **Service**: replace stub internals of `AccountingPeriodService` — `isPeriodOpen` reads the table;
  `closePeriod(periodCode)` validates no DRAFT JEs dated inside (list them in a 422 payload otherwise)
  then flips to CLOSED; `reopenPeriod` requires elevated permission and records reason (mandatory
  justification string).
- **Endpoints**: `GET /v1/accounting/periods`, `POST /periods/{code}/close`, `POST /periods/{code}/reopen`.
  Permissions: `accounting:period:view|close|reopen` (+ `accounting:period:override`, B2) in
  `permissions.yaml`. Events: `ACCOUNTING_PERIOD_CLOSE` (approval preset), `ACCOUNTING_PERIOD_REOPEN`
  (approval).
- **AC**: closing a period with drafts inside → 422 listing entry IDs; close/reopen audit-logged with actor;
  `PERIOD_CLOSED` error code (already reserved in `.business-rules/ERROR_CODES.md` per questions doc #190)
  returned on post-into-closed.
- **Effort**: M. **Deps**: none.

### Story B2 — Enforcement + override path
- **Enforcement points** (all must consult the service): `JournalEntryService.postJournalEntry`
  (manual + engine paths), `PostingEngineOrchestrator` (event-driven postings whose `transactionDate`
  falls in a closed period → route the `AccountingEvent` to `SUSPENDED` with
  `failureReasonCode=PERIOD_CLOSED`, reprocessable after reopen or with override), credit-memo posting,
  AP payment GL posting, reversal (A3).
- **Override**: caller holding `accounting:period:override` may post into a CLOSED period by supplying a
  justification; recorded in `AccountingAuditLog` with period, entry, actor, justification — this is the
  Durion-shaped version of Odoo's lock-date exceptions (skip time-boxing in v1; note as possible follow-up).
- **Hard lock**: single org-level `hard_lock_date` (config table row, settable via endpoint, never
  post before it, no override) — the irreversible piece of Odoo's model worth keeping.
- **AC**: matrix IT covering each posting path × open/closed/override/hard-locked; suspended events
  reprocess cleanly after reopen.
- **Effort**: M. **Deps**: B1.

---

## 4. Workstream C — AR completion (payment application → ledger)

Odoo reference: reconciliation flips payment state and posts liquidity↔receivable; unreconcile reverses
side-effect entries. Durion keeps document-level application but must make the ledger tell the truth.

### Story C1 — Wire cash-receipt GL posting
- **Change**: on successful `applyPayment`, enqueue (outbox, same tx) a `PAYMENT_APPLICATION_GL_POSTING`
  work item consumed by the existing async pattern (mirror `AP_PAYMENT_GL_POSTING` flow) that calls the
  currently-dormant `GLPostingServiceImpl.postPaymentApplication` → Dr Cash/Undeposited Funds, Cr AR.
  Idempotency: posting key = applicationRequestId (reuse `IdempotencyService`).
- **Account resolution**: do NOT hardcode account IDs; resolve via posting categories/mapping keys
  (`PAYMENT_APPLICATION` posting category + mapping key seeded in `R__seed_reference_accounting.sql`,
  plus new GL accounts `1000 Cash`/`1090 Undeposited Funds` — see H1). Choice of Cash vs Undeposited
  Funds is a config default pending reconciliation workstream F (Odoo's "outstanding receipts" account
  is the analog). (Open question OQ-3.)
- **AC**: applying a payment yields a POSTED, balanced JE with `sourceEventId` = application request;
  replay/duplicate application does not double-post; trial balance (G1) shows AR decreasing.
- **Effort**: M. **Deps**: H1 (accounts), B2 (period check inside posting).

### Story C2 — Reversal symmetry
- **Change**: `reversePaymentApplication` additionally enqueues a reversing JE (via A3's
  `reverseJournalEntry` against the C1 entry, found by posting key). Preserves "reverse, never delete"
  (matches Odoo's unlink-cascade philosophy and Durion's own `PaymentApplicationReversal` design).
- **AC**: apply→reverse leaves net-zero GL effect but four rows of history (JE + reversal JE, application +
  reversal); reversing an application whose JE posted into a now-closed period follows B2 rules.
- **Effort**: S. **Deps**: C1, A3.

### Story C3 — Auto-allocation strategy (parity nicety)
- **Change**: optional `allocationStrategy` on the apply-payment command (`CALLER_ORDER` default,
  `OLDEST_FIRST` by invoice date) — Odoo makes ordering an explicit caller-controllable concept; keep
  caller-order as default so existing clients are untouched.
- **AC**: strategy honored; documented in contract guide; OpenAPI updated.
- **Effort**: S. **Deps**: none.

### Story C4 — Concurrency hardening
- **Change**: add `@Version` (optimistic lock) to `ReceivablePayment`; retry-once-on-conflict in
  `PaymentApplicationServiceImpl`; IT with two threads applying against the same payment concurrently
  (one must 409 or serialize; unappliedAmount never negative).
- **Effort**: S. **Deps**: none. Flyway `V10` (version column).

---

## 5. Workstream D — Credit memo correctness

Blocked on tax plan WS-T5 (tax breakdown must reach accounting). See `plan-odoo-parity-pos-tax.md`.

### Story D1 — Replace hardcoded 10% tax reversal
- **Change**: `CreditMemoServiceImpl.calculateCreditAmounts` derives `taxReversed` from the invoice's
  **stored** tax (from `ExtInvoice` — today a single scalar `tax`; after tax-plan T5 it carries the
  breakdown), pro-rated by credit ratio using HALF_UP at currency scale with a final-line residual
  correction (Odoo's last-partial residual dump pattern — port its 17.79/17.80 test vectors).
  Never recompute tax at credit time (freeze-after-finalize principle; rate drift protection —
  comp-tax exercise 3).
- **AC**: credit memo for an invoice with tax T and ratio r posts `taxReversed = round(T×r)` with residual
  correction on the final full-balance credit so cumulative credits sum exactly to T; regression test with
  the fictional-10% case removed.
- **Effort**: S once data available. **Deps**: tax plan T5 (interim step possible: use scalar
  `ExtInvoice.tax` immediately — strictly better than 10% — then upgrade to breakdown).

### Story D2 — Per-jurisdiction tax-payable posting (DECISION, default: single account)
- Odoo's repartition lets one tax hit many accounts. Durion today has one configured tax-payable account.
  Filing is by state; if liability reporting (tax plan T8) is served by report-time aggregation of the
  persisted breakdown, a single GL account suffices and this story is a non-goal. Only if Finance wants
  per-state GL accounts: extend `CreditMemoGLConfig` + invoice-posting rules to map
  `jurisdictionType/state → account` via a new `MappingKey` dimension. (Open question OQ-4.)
- **Effort**: M if built. **Deps**: tax plan T5, E1.

---

## 6. Workstream E — Posting-rule engine enhancements

Odoo reference: repartition lines (factor-percent splits) and fiscal positions. Durion's rules-on-events
model is architecturally preferred (comp doc calls it "arguably cleaner"); enhance it, don't replace it.

### Story E1 — Proportional split lines (repartition-inspired)
- **Change**: extend the rules JSON schema with an optional `factorPercent` (0–100, 4dp) per line and a
  `splitGroup` marker; lines in one split group share an `amountField` and their factors must sum to 100
  (422 `UNBALANCED_RULES` on publish otherwise — extends the existing publish validation from questions
  doc #202). Evaluator computes raw shares, rounds HALF_UP per line, dumps the residual cent(s) on the
  largest line (Odoo `_distribute_delta_amount_smoothly` reference).
- **Files**: `PostingRuleEvaluatorImpl`, `POSTING_RULES_SCHEMA.md` (update the schema doc the questions
  docs commissioned), publish-time validator, rule-set contract tests.
- **AC**: one event amount splits 60/40 across two accounts with Σ = source amount exactly, for adversarial
  amounts (0.01, 33.33/33.33/33.34 three-way); existing single-line rules untouched (schema additive).
- **Effort**: M. **Deps**: none.

### Story E2 — Richer condition predicates
- **Change**: extend `matchesCondition` beyond `eventType ==` to a small whitelist grammar:
  `payload.<path> <op> <literal>` with ops `== != > >= < <=` and `&&` conjunction. No general expression
  engine, no scripting (SpotBugs/security posture; Odoo's sandboxed-formula addon is the cautionary
  reference — keep it declarative). First-match-wins semantics unchanged.
- **AC**: rule matching on `payload.paymentMethod == 'CASH'` routes to a different account than card;
  malformed predicate fails at **publish** time, not evaluation time; fuzz tests for parser.
- **Effort**: M. **Deps**: none.

### Story E3 — Mapping resolution test endpoint (deferred item, now due)
- Questions doc #203 Q6 deferred a dry-run endpoint. Build it now — it is the operational equivalent of
  Odoo's preview behavior and de-risks E1/E2 rollout: `POST /v1/accounting/mappings/resolve-test`
  accepting `{eventType, samplePayload, transactionDate}` → resolved lines with accounts, amounts, and
  the rule/version/mapping that matched, **without persisting**. Permission `accounting:posting_rules:view`.
- **Effort**: S. **Deps**: E1, E2 (ship last).

---

## 7. Workstream F — Reconciliation (decide, then build one model)

**Blocking decision (OQ-5)**: the repo contains two contradictory reconciliation concepts —
the comp doc describes automated processor (Stripe) reconciliation, the questions docs (#187) specify a
manual CSV bank-statement reconciliation UI, and the code has a dead `Reconciliation` scaffold matching
neither fully. Recommendation, based on Odoo's architecture (statement lines are ledger reality checks)
and what the platform actually has (Stripe as the only money pipe):

### Story F1 — Stripe payout reconciliation (recommended first)
- **Change**: ingest Stripe payout/balance-transaction events (via pos-event-receiver or the positivity
  integration domain — must arrive as Kafka events per ADR-0044, not a new REST poller in accounting;
  coordinate with Positivity domain agent for the feed) into a `processor_settlement` table; auto-match
  settlement lines against `ReceivablePayment`/`APPayment` records by processor reference + amount;
  post matched batches Dr Cash / Cr Undeposited Funds (closing the loop opened in C1); unmatched lines
  sit in `UNMATCHED` status with a review endpoint.
- **AC**: a payout containing N charges flips N payments to settled and posts one batched JE; unmatched
  line workflow (list, manually match, write off ≤ configurable threshold with reason).
- **Effort**: L. **Deps**: C1, H1. **Research flag**: exact Stripe event schema available in the
  platform's event bus today — survey pos-event-receiver + positivity integration before design.

### Story F2 — Manual CSV bank reconciliation (build to #187 spec, second)
- The questions docs contain a full resolved spec: endpoints (`/v1/accounting/reconciliations` family:
  `/import`, `/match`, `/unmatch`, `/adjustments`, `/finalize`, `/report`, `/audit`), CSV-only MVP,
  signed-amount model, 1-to-1 and N-to-1 matching within ±0.01, statement vs GL ending balance gate on
  finalize, permissions `accounting:reconciliation:*`, single currency. Implement exactly that spec,
  reusing (or rewriting — assess first) the dead `Reconciliation` entity; resolve the adjustment-type enum
  contradiction between the two questions files by adopting the `.md` recommendation
  (`BANK_FEE, NSF_FEE, INTEREST_EARNED, FLOAT_ADJUSTMENT, OTHER`) unless Finance objects (OQ-6);
  adjustments post real JEs via posting categories.
- **Prerequisite**: `reconcilable` flag on GLAccount (H1) to scope which accounts can be reconciled —
  Odoo's `account.reconcile` flag, and the `.txt` file's own #187 answer, both call for it.
- **Effort**: L. **Deps**: H1, B2 (adjustments respect periods), E1 optional.

### Story F3 — Auto-matching rules (Odoo `account.reconcile.model` analog) — backlog
- Only after F1/F2 usage data. Declarative match rules (amount tolerance, label regex, counterparty) with
  write-off templates. Do not build speculatively.

---

## 8. Workstream G — Reporting parity

Odoo reference: declarative `account.report`. Durion's `StatementLineMapping` data-driven model is the
same idea at smaller scale — extend it rather than inventing a report DSL.

### Story G1 — Trial balance
- `GET /v1/accounting/reports/financial/trial-balance?asOf=` — per-account debit/credit/balance from
  POSTED lines, grand totals proving Σdebit=Σcredit (surfaces A1 violations operationally). Include
  `entryNumber` gap-check summary (A2) as a report footnote block. Permission
  `reporting:view:financial-statements` (exists).
- **Effort**: S.

### Story G2 — General ledger report + aged AR/AP
- GL report: per-account chronological posted lines with running balance, filterable by account/date —
  extends existing drilldown repo queries. Aged AR: bucket open invoice balances (0-30/31-60/61-90/90+)
  from `InvoiceBalanceCalculator` inputs; aged AP mirrors from `VendorBill`/`APPaymentAllocation`.
  Export via existing `ReportExportService`; rendered PDF via pos-documents (ADR-0020) — no PDF libs
  in-module.
- **Effort**: M. **Deps**: none (better after C1 so cash is truthful).

### Story G3 — Journal/operations dashboard KPIs (Odoo dashboard analog) — backlog
- Draft count, suspended events count, unreconciled settlement lines, period-close readiness. Cheap
  aggregation endpoints feeding Grafana/frontend; build after F/B land.

---

## 9. Workstream H — Chart of accounts enrichment

### Story H1 — Account metadata + seed expansion
- **Change**: add to `GLAccount`: `reconcilable` boolean (default false; required by F), optional
  `accountSubtype` enum (RECEIVABLE, PAYABLE, BANK_CASH, UNDEPOSITED_FUNDS, TAX_PAYABLE, CURRENT_ASSET,
  FIXED_ASSET, CURRENT_LIABILITY, SALES, COST_OF_SALES, OPERATING_EXPENSE, OTHER — a pragmatic subset of
  Odoo's `account_type` taxonomy; drives report grouping and posting-config validation, e.g. C1 must
  resolve to a BANK_CASH/UNDEPOSITED_FUNDS subtype). Flyway `V11` + expand
  `R__seed_reference_accounting.sql` to a working small-business COA (cash, undeposited funds, AR, AP,
  sales tax payable, revenue, COGS, fees/expense, customer-credit liability) so C/F/G stories have real
  accounts. Keep seed idempotent (existing upsert pattern).
- **AC**: existing accounts unaffected (subtype nullable/backfilled); COA endpoints expose new fields;
  posting-config validation warns when C1/F mappings resolve to an implausible subtype.
- **Effort**: M. **Deps**: none — schedule FIRST; C and F depend on it.

---

## 10. Sequencing (waves for the agent team)

Wave numbering assumes the standard capability flow (contract PR in durion → backend PR → frontend).
Each story = one backend story issue; group into capabilities as shown. Suggested capability IDs continue
the accounting range (CAP-05x/CAP-2xx are taken; coordinate with the capability registry before minting).

| Wave | Stories (parallelizable within wave) | Theme |
|---|---|---|
| 1 | H1, A1, C4, B1 | Foundations: COA metadata, DB invariant, locking, periods table |
| 2 | A2, A3, B2, E1, E2 | Numbering, reversal lifecycle, period enforcement, rule engine |
| 3 | C1, C3, D1(interim scalar), G1, E3 | Cash-receipt posting, allocation strategy, trial balance, dry-run |
| 4 | C2, F1, G2 | Reversal symmetry, Stripe settlement reconciliation, GL/aged reports |
| 5 | F2, D1(breakdown upgrade), D2?, A4?, F3?, G3 | CSV bank rec, breakdown-based credits, decided options |

Cross-plan dependency: tax plan T5 (breakdown events) should land by Wave 4 so D1's upgrade and tax
liability reporting (tax plan T8) can proceed in Wave 5.

Definition of done per story: code + Flyway + `@EmitEvent`/EventTypes + permissions.yaml + OpenAPI
regeneration + Angular SDK (where frontend-visible) + unit & contract IT + ArchUnit green +
`./mvnw -pl pos-accounting -am test` + Spotless + README/contract-guide update.

---

## 11. Where more research is required (before the stories that cite them)

- **R1 (before A2)**: statutory necessity of gapless numbering for this business; whether audit/CPA
  expectations demand it. Determines gapless-with-lock vs plain sequence.
- **R2 (before F1)**: inventory of Stripe events currently flowing through pos-event-receiver /
  positivity domain (payout.paid, balance_transaction schemas) and whether payout line detail is
  available; if not, an integration story in the positivity domain precedes F1.
- **R3 (before B2)**: enumerate every code path that creates/posts a JE (survey found: manual controller,
  orchestrator, credit memo, AP payment async, reversal, labor/overhead?) — the enforcement matrix must
  be exhaustive or the period lock is theater.
- **R4 (before C1)**: confirm whether pos-invoice/payment domain already emits a distinct
  "payment applied" event accounting could key GL posting on, vs triggering from accounting's own
  application flow (ADR-0044 ownership: application record is accounting's fact, so in-module trigger is
  likely correct — verify with architecture agent).
- **R5 (before D2/G2)**: Finance's filing workflow — is jurisdiction-level GL split actually needed, or is
  report-time aggregation sufficient (drives OQ-4).
- **R6 (before F2)**: reconcile the `.md` vs `.txt` adjustment-type enums and the questions-docs'
  implemented-status contradictions (one file claims screens/tests exist; the other says 501 stubs) —
  audit actual controller state for #201–#207 features before scheduling F2 frontend work.

## 12. Open questions (for product/finance/architecture)

- **OQ-1**: Gapless JE numbering — required, or is a simple monotonic sequence acceptable?
- **OQ-2**: Hash-chain inalterability — build, or record as non-goal ADR? (Recommend: non-goal ADR.)
- **OQ-3**: Cash-receipt posting target — direct to Cash, or Undeposited Funds cleared by settlement
  reconciliation (recommended: Undeposited Funds once F1 exists; Cash until then)?
- **OQ-4**: Single sales-tax-payable GL account with report-time jurisdiction breakdown (recommended), or
  per-state GL accounts?
- **OQ-5**: Reconciliation priority — Stripe payout auto-reconciliation first (recommended) or the #187
  manual CSV bank-rec UI first?
- **OQ-6**: Reconciliation adjustment-type enum — adopt `.md` set (`BANK_FEE, NSF_FEE, INTEREST_EARNED,
  FLOAT_ADJUSTMENT, OTHER`)?
- **OQ-7**: Period cadence and ownership — monthly close operated by which role; is CLOSING (soft-close)
  state needed in v1 or is OPEN→CLOSED enough?
- **OQ-8**: Fiscal-position analog (per-counterparty account substitution) — any real Durion use case
  (e.g. fleet/national accounts posting to different AR accounts)? If none, record as non-goal.
