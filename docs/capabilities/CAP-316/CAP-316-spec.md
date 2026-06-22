# CAP-316 — Retread Plant Labor & Overhead Cost Report (read-only) — Specification

- **Capability:** CAP-316
- **Primary (high-level) story:** louisburroughs/durion#328
- **Domain:** accounting
- **Type:** read-only reporting screen
- **Source artifact:** `LOCostReportingForm2026.xlsx` (MRTI "Retread Plant — Labor and Overhead Costs (Chart of Accounts) Form")

---

## 1. Overview

The source workbook is the MRTI annual **Labor and Overhead Cost Reporting** form for a tire retread
plant. It captures, for one **dealer/location** and one **fiscal year**, the plant's labor and overhead
costs broken into a fixed chart-of-accounts hierarchy, with one column per calendar month plus a
year-to-date (YTD) total.

This capability delivers the **"Master Form"** tab as a new, **read-only** accounting screen in Durion.
The data is sourced from **existing accounting APIs / posted GL data** — the screen never accepts input,
posts, or infers journal entries.

### 1.1 Workbook tab → capability mapping

| Workbook tab | Role | In scope for CAP-316 |
| --- | --- | --- |
| **Master Form** | The page to build: monthly + YTD cost matrix by location/year | ✅ Yes — this is the screen |
| **Definitions** | Per-line include/exclude guidance + Fixed/Variable classification | ✅ Yes — as inline help/tooltips + line metadata |
| **Cost per Unit (Local Currency)** | Same lines divided by production equivalent units | ❌ No — requires non-accounting production counts (separate capability) |
| **Paste From File** | Flat monthly DB-import row per shop (one row per month, one column per leaf line) | ➖ Informs the backend response shape only; not a screen |

### 1.2 Decisions captured from intake

- **Data sourcing:** a **new backend read-only report endpoint** performs the report-line → GL-account
  mapping and monthly/YTD aggregation server-side; the frontend renders the returned matrix.
- **Screen scope:** Master Form monthly + YTD ($), Definitions as help/tooltips, and the currency-conversion row.
- **GL mapping:** the concrete report-line → GL-account mapping is **authored by the Accounting domain
  agent** during refinement. This spec defines the **line taxonomy** and the **mapping structure**, not the
  account numbers.

---

## 2. Screen behavior (read-only)

### 2.1 Selectors / context
- **Location / Dealer** (required) — maps to the accounting `locationId` dimension.
- **Fiscal Year** (required).
- **As-of month** (optional, default = December / full year) — bounds the YTD and which monthly columns
  are populated.
- Header echoes the source form's context: `Dealer / Location`, `Year`, and the note
  *"$ Amounts in Whole Dollars Local Currency (except line 2.11.4)"*.

### 2.2 Grid
- Rows follow the canonical hierarchy in §3 (section → line → sub-line), preserving numbering and order.
- Columns: `January … December`, then `YTD`.
- Subtotal rows: **Total Labor** (§1), **Total Overhead** (§2), **Total Labor & Overhead**.
- All values are whole local-currency dollars. Empty/no-activity cells render `0`.
- Each line offers inline help showing its **definition** (include / do-not-include guidance) and its
  **Fixed/Variable** type from §3.
- A **Currency Conversion** footer row shows "Local Currency per $US" (1.00 for US plants) and the average
  rate used, for context only.

### 2.3 States
- `idle → loading → loaded | empty | error`.
- **empty**: valid scope, no postings → full layout with all `0`.
- **error**: backend `4xx/5xx` surfaced as a non-leaking message; `403` when unauthorized.

### 2.4 Read-only guarantees
- No create/update/delete actions, no posting, no "reverse/imply GL" behavior.
- No editable cells. Export (CSV/print) is presentation-only and optional for v1.

---

## 3. Canonical cost-line taxonomy

Codes, labels, hierarchy, Fixed/Variable type, and definitions are taken verbatim from the workbook's
**Master Form** and **Definitions** tabs. `*` on a type means "Fixed if volume change is minimal"
(per the Definitions note). Leaf lines map to GL accounts; parent lines are computed subtotals.

### §1 Labor

| Code | Label | Type | Roll-up | Definition (include / exclude) |
| --- | --- | --- | --- | --- |
| 1.1 | Wages and Bonuses (including shop manager) | — | sum of 1.1.1–1.1.2 | — |
| 1.1.1 | Hourly wages and bonuses | Variable | leaf | Incl: wages/bonuses for hourly retread-plant workers (production, maintenance, janitor, plant clerk), matching MRT Weekly Production Report hours. Excl: warehousing/distribution/sales/corporate office wages; production outside the MRT process. |
| 1.1.2 | Management salaries | Fixed * | leaf | Incl: salaries/bonuses for salaried retread-plant personnel matching MRT Weekly Production Report hours. |
| 1.2 | Misc Labor (contract and temp production employees) | Variable | leaf | Incl: wages for temporary/contract workers matching MRT Weekly Production Report hours. |
| 1.3 | Taxes and Benefits | — | sum of 1.3.1–1.3.6 | Relating to the wages in §1.1. |
| 1.3.1 | FICA | Variable | leaf | Payroll tax on §1.1 wages. |
| 1.3.2 | Fed Unemployment | Variable | leaf | Federal unemployment tax on §1.1 wages. |
| 1.3.3 | State Unemployment | Variable | leaf | State unemployment tax on §1.1 wages. |
| 1.3.4 | Medical/dental insurance, life, health, disability | Variable | leaf | Employee benefit costs related to §1.1. |
| 1.3.5 | Retirement plan contributions | Variable | leaf | Employer retirement contributions related to §1.1. |
| 1.3.6 | Employee Insurance – workers' comp | Variable | leaf | Workers' compensation insurance for plant employees. |
| 1.5 | Uniforms rental / laundry | Variable * | leaf | Incl: uniforms or shirt/trouser program, if provided. |
| — | **Total Labor** | — | sum of 1.1, 1.2, 1.3, 1.5 | Section subtotal. |

> Note: the form has no §1.4; numbering follows the source form.

### §2 Overhead

| Code | Label | Type | Roll-up | Definition (include / exclude) |
| --- | --- | --- | --- | --- |
| 2.1 | Building | — | sum of 2.1.1–2.1.3 | — |
| 2.1.1 | Depreciation | Fixed | leaf | Incl: "bricks & mortar", only if owned, 20-yr straight line. Excl: if renting (use 2.1.3); building-financing interest; land depreciation. |
| 2.1.2 | Maintenance | Fixed | leaf | Incl: building maintenance, garbage removal fees. |
| 2.1.3 | Rent | Fixed | leaf | Incl: building rental (only the retread-plant % of footage); leasehold improvements if owned. Excl: mortgage payments. |
| 2.2 | Training Costs | Variable * | leaf | Incl: outside-trainer expense. Excl: trainer/trainee labor (use 1.1.1). |
| 2.3 | Employment advertising / recruiting costs | Fixed | leaf | Recruiting/advertising for plant staff. |
| 2.4 | Vehicle expenses (rubber dust trailer, shop truck) | Variable * | sum of 2.4.1–2.4.5 | Incl: vehicles for retread-plant use only; rubber-dust trailer fees. Excl: sales/distribution/warehousing vehicles; vehicle insurance (use 2.7.3); forklifts (use 2.11.5). |
| 2.4.1 | Gas & Oil | — | leaf | Fuel/oil for plant vehicles. |
| 2.4.2 | Maintenance | — | leaf | Maintenance for plant vehicles. |
| 2.4.3 | Taxes | — | leaf | Taxes on plant vehicles. |
| 2.4.4 | Depreciation | — | leaf | Depreciation of plant vehicles (standard methods). |
| 2.4.5 | Rent | — | leaf | Rental of plant vehicles. |
| 2.5 | Telephone | Fixed | leaf | Incl: voice/modem/data lines for plant; cell phones for plant personnel. |
| 2.6 | Travel and Entertainment | Fixed | leaf | Incl: travel for plant manager/employees (e.g. PMPC). Excl: owner/corporate travel; promotional/entertainment (sales cost). |
| 2.7 | Insurance | — | sum of 2.7.1–2.7.3 | Incl: fire/theft/liability for the retread plant; plant-vehicle insurance (2.7.3). Excl: warehouse/sales insurance. |
| 2.7.1 | Fire | — | leaf | Fire insurance for the plant. |
| 2.7.2 | Theft | — | leaf | Theft insurance for the plant. |
| 2.7.3 | Liability | — | leaf | Liability insurance for the plant (incl. plant vehicles). |
| 2.8 | Property Taxes | Fixed | leaf | Incl: property taxes for dealer-owned plant. Excl: if building rented; sales/income taxes. |
| 2.9 | Supplies | — | sum of 2.9.1–2.9.4 | — |
| 2.9.1 | Shop Consumables (rasps, grinding wheels, brushes) | Variable | leaf | Incl: buffing blades/brushes, grinding wheels. Excl: materials assembled to the tire (tread rubber, cushion gum, rope rubber, patches, cement, paint). |
| 2.9.2 | Curing Consumables (envelopes, lube, wicks, poly) | Variable | leaf | Incl: curing envelopes, wicks, envelope lube, poly. |
| 2.9.3 | Miscellaneous | Fixed | leaf | Incl: janitorial supplies, treatment chemicals, etc. |
| 2.9.4 | Office | Fixed * | leaf | Incl: paper, pens, labels, office supplies. |
| 2.10 | Utilities – gas, electric, water | Variable | leaf | Incl: gas/electric/water for the plant (may be an allocated % if shared). Excl: utilities for other operations in the building. |
| 2.11 | Equipment expense | — | sum of 2.11.1–2.11.6 | — |
| 2.11.1 | Maintenance | Variable * | leaf | Incl: equipment repairs/spare parts, computer maintenance, custom-mold cleaning. Excl: items under MRT warranty. |
| 2.11.2 | Rental | Fixed | leaf | Incl: rental fees for air compressors, etc. |
| 2.11.3 | Small tools and equipment | Fixed | leaf | Small tools/equipment purchases. |
| 2.11.4 | Depreciation – MRT process equipment (**$US only**) | Fixed | leaf | Incl: MRT process equipment & installation at start-up/expansion (regardless of ownership); MRT mods > $5,000; 10-yr straight line (MRT provides value). Excl: non-MRT-process equipment. **Reported in USD even on local-currency plants.** |
| 2.11.5 | Depreciation – Other shop equipment (boiler, cyclone, air compressors, capital cost of conversion) | Fixed | leaf | Incl: dealer-installed process support equipment, forklifts, capital conversion costs; 10-yr straight line (forklifts/computers use std methods). Excl: MRT-provided gas hot-water unit (in 2.11.4); equipment sales taxes; equipment financing interest. |
| 2.11.6 | MRTI equipment leases or rent | Fixed | leaf | Incl: MRT software license fees (CIA, BIB TREAD); custom-mold rental. Excl: MRT process equipment rented from MRT/3rd party (in 2.11.4). |
| 2.12 | Administration fees | Fixed | leaf | Incl: allocation for corporate accounting/payroll/personnel support (consistent monthly, not %-of-sales). Excl: corporate costs that would persist if plant closed (senior mgmt salaries, sales commissions). |
| 2.13 | Inventory charge | Variable | leaf | Interest charge/credit for money funding inventory of materials & consumables. Expense = positive; interest income = negative. Excl: casing/stock-casing inventory charges; equipment financing interest. |
| 2.14 | Income from rubber dust sales | Variable | leaf | Income from rubber-dust sales (enter as negative); or removal cost if not sold (positive). Excl: rubber-dust trailer rentals/fees (use 2.4). |
| 2.15 | Production Quality | — | sum of 2.15.1–2.15.2 | — |
| 2.15.1 | Casings scrapped in production (e.g. torn belts by buffer) | Variable | leaf | Incl: cost of casings damaged by the plant during production (casing cost + disposal). Excl: RAR scrap disposal fees. |
| 2.15.2 | Adjustments (customer returns, user price plus transportation both ways) | Variable | leaf | Incl: materials/workmanship adjustment costs (fees, transport), shown in the month paid (not accrual), matching adjustments reported to MRT Quality. Excl: commercial concession adjustments; RAR disposal fees. |
| — | **Total Overhead** | — | sum of 2.1–2.15 | Section subtotal. |
| — | **Total Labor & Overhead** | — | Total Labor + Total Overhead | Grand total. |

### §3.1 Currency conversion (footer)
- **Local Currency per $US** — `1.00` for US plants; otherwise the configured rate.
- The form displays an **Average** rate used for the year (workbook label "Avg").
- Presentation context only; does not alter the stored local-currency amounts (except line 2.11.4 which is USD by definition).

---

## 4. Data sourcing — backend report endpoint

A new **read-only** reporting endpoint returns the full matrix so the frontend does no aggregation.

### 4.1 Proposed contract (to be finalized in BACKEND_CONTRACT_GUIDE)
```
GET /v1/accounting/reports/retread/labor-overhead
    ?locationId={id}&fiscalYear={yyyy}[&asOfMonth={1-12}]
```

Response (shape; field names finalized during backend story):
```jsonc
{
  "locationId": "LOC-107",
  "locationLabel": "Smetzer's Wooster",
  "fiscalYear": 2026,
  "asOfMonth": 12,
  "currency": "USD",
  "localCurrencyPerUsd": 1.00,
  "averageRate": 1.00,
  "lines": [
    {
      "code": "1.1.1",
      "label": "Hourly wages and bonuses",
      "parentCode": "1.1",
      "level": 3,
      "costType": "VARIABLE",          // FIXED | VARIABLE | FIXED_IF_LOW_VOLUME
      "isSubtotal": false,
      "definition": "Incl: ... Excl: ...",
      "monthly": [0,0,0,0,0,0,0,0,0,62645,0,0],  // index 0 = January
      "ytd": 62645
    }
    // ... all lines + subtotal rows in canonical order
  ]
}
```

### 4.2 Aggregation rules (server-side)
- For each **leaf** line, sum posted journal-entry-line amounts whose GL account is in that line's mapped
  account set, whose `locationId` dimension = requested location, and whose transaction date falls in the
  given month of the fiscal year.
- **Subtotal** lines are computed from their children (never mapped directly).
- **YTD** = sum of monthly values for elapsed months (1 … `asOfMonth`).
- Sign conventions: lines 2.13 and 2.14 may be negative (income/credit) per the Definitions; preserve sign.
- Amounts returned as whole local-currency dollars; line 2.11.4 in USD.
- Reflects **posted** ledger state only (consistent with CAP-054 reporting behavior).

### 4.3 Report-line → GL-account mapping
- Mapping is **Accounting-domain-authored** (per intake decision). The endpoint resolves each report line
  to one or more GL accounts via this mapping; the screen never embeds account numbers.
- Open question for refinement: persisted, location-overridable master-data vs. static domain config for v1.

### 4.4 Reuse of existing capabilities
- **CAP-050** Chart of Accounts — GL account catalog / account metadata.
- **CAP-051** GL posting / ledger lines + dimension model (`locationId`, cost-center) — the source of
  posted amounts by account + dimension + date.
- **CAP-054** Financial reporting — established pattern that reporting reflects posted state; this endpoint
  follows the same conventions and lives alongside the `/v1/accounting/reports/...` family.

---

## 5. Authorization, NFRs, observability
- Gated by the financial-reporting permission family (e.g. `reporting:view:financial-statements` or a
  retread-report-specific scope to be confirmed by the Security/Accounting domains). Unauthorized → `403`.
- No PII; data is financial aggregates by location.
- Single round-trip for a full year; target P95 well within standard reporting SLAs.
- Correlation/trace standards (AD-008) apply; reporting reads are auditable but non-mutating.

---

## 6. Out of scope
- Cost-per-Unit view (needs production equivalent-unit counts — non-accounting source).
- Data entry / the "Paste From File" import path.
- Editing the report-line → GL mapping UI (admin capability, if needed, is separate).
- Multi-location consolidation / cross-year comparison (possible future enhancement).

---

## 7. Traceability

| Layer | Repo | Artifact |
| --- | --- | --- |
| Primary (high-level) | louisburroughs/durion | Issue #328 |
| Backend story | louisburroughs/durion-positivity-backend | `01-labor-overhead-cost-report-endpoint.story.md` (this folder) → issue TBD |
| Frontend story | louisburroughs/durion-positivity-frontend | `stories/frontend/CAP_316.frontend.md` (this folder) → issue TBD |
| Contract | louisburroughs/durion | `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` (add CAP-316 section) |
