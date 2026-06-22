# Story: Read-only Retread Plant Labor & Overhead Cost Report endpoint

> Target repo: **louisburroughs/durion-positivity-backend** — issue #724
> Parent capability: CAP-316 — louisburroughs/durion#328
> Labels: `type:story`, `domain:accounting`, `status:needs-review`
> Branch: `cap/316-labor-overhead-cost-report`

## Description
Provide a **read-only** accounting reporting endpoint that returns the MRTI "Retread Plant — Labor and
Overhead Costs" matrix for a given **location/dealer** and **fiscal year**: every cost line (per the
CAP-316 canonical taxonomy) with **12 monthly amounts + YTD**, plus section subtotals and a currency
context. Amounts are derived from **posted GL data** (no new posting path). The endpoint maps each report
line to its GL account(s) via Accounting-domain mapping and aggregates server-side so the frontend renders
the returned matrix directly.

See `docs/capabilities/CAP-316/CAP-316-spec.md` (durion) for the full line taxonomy, definitions, and
aggregation rules.

## Acceptance Criteria
- `GET /v1/accounting/reports/retread/labor-overhead?locationId={id}&fiscalYear={yyyy}[&asOfMonth={1-12}]`
  returns the full cost-line hierarchy in canonical order (Labor §1, Overhead §2) with `monthly[12]`
  (index 0 = January) and `ytd` per line.
- Leaf-line monthly amounts equal the sum of posted journal-entry-line amounts for the mapped GL
  account(s), filtered by `locationId` dimension and transaction month within the fiscal year.
- Subtotal lines (`1.1`, `1.3`, `2.1`, `2.4`, `2.7`, `2.9`, `2.11`, `2.15`, **Total Labor**,
  **Total Overhead**, **Total Labor & Overhead**) are computed from their children, never mapped directly,
  and equal the column-wise sum of children.
- `ytd` equals the sum of monthly values for elapsed months (1 … `asOfMonth`; default 12).
- Sign is preserved for credit/income lines (2.13 Inventory charge, 2.14 Rubber dust income).
- Line `2.11.4` is returned in USD; all other amounts in the location's local currency. Response carries
  `currency`, `localCurrencyPerUsd`, and `averageRate` (1.00 for US plants).
- Each line includes metadata: `code`, `label`, `parentCode`, `level`, `costType`
  (`FIXED|VARIABLE|FIXED_IF_LOW_VOLUME`), `isSubtotal`, and `definition` text.
- Reflects **posted** ledger state only; the endpoint performs no mutation/posting/inference.
- Unknown/zero-activity line → amounts `0` (line still present). Location with no postings → all `0`,
  full layout preserved.
- Authorization enforced via the financial-reporting permission family; unauthorized → `403`.
- Provider behavioral contract test proves amounts trace to posted GL data for a seeded location/period.

## Tasks
- [ ] Define the report-line → GL-account **mapping** mechanism (coordinate with Accounting domain agent;
      decide persisted master-data vs. static config for v1). Mapping must be location-aware.
- [ ] Add `LaborOverheadCostReport` response DTOs (report header + ordered line list).
- [ ] Implement a read-only service that queries posted ledger lines by account set + `locationId`
      dimension + period, and assembles monthly + YTD + subtotals per the canonical taxonomy.
- [ ] Add controller endpoint `GET /v1/accounting/reports/retread/labor-overhead` with OpenAPI annotations
      and permission gating.
- [ ] Regenerate `OpenAPI.yaml` and update the Angular SDK (`durion-positivity-sdk-angular`) per the
      controller-change contract chain.
- [ ] Update `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` (durion) with a **CAP-316**
      section: operationId, path, request/response shape, behavioral assertions, ADR constraints, contract
      test traceability.
- [ ] Unit tests: mapping resolution, monthly bucketing, subtotal roll-ups, YTD with mid-year `asOfMonth`,
      negative-sign lines, USD-only line 2.11.4.
- [ ] Provider contract + controller tests; integration test against seeded posted journal data.

## Dependencies
- CAP-050 Chart of Accounts (GL account catalog / metadata).
- CAP-051 GL posting / ledger lines and the dimension model (`locationId`, cost-center).
- CAP-054 Financial reporting conventions (`/v1/accounting/reports/...`, posted-state semantics).
- Accounting-domain report-line → GL-account mapping (authored during refinement).

## Notes for Agents
- Follow accounting domain conventions; do **not** introduce a posting path — this is a pure read model.
- Place alongside the existing financial reporting controller/service so it inherits reporting
  permissions, error handling, and correlation/trace standards (AD-008).
- Keep the canonical line order/numbering exactly as in the CAP-316 spec (the form layout is contractual).
- Closed-period rules do not block reads; the report simply reflects posted state.
