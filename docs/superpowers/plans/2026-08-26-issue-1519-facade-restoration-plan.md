# Plan: Restore pos-mcp-server Tool Facades to Their Established Purpose

**Source issue:** [durion-positivity-backend#1519](https://github.com/louisburroughs/durion-positivity-backend/issues/1519) (see the analysis comment for the evidence trail)
**Status:** EXECUTING — Wave 0 complete, Wave 1 complete, Wave 2 (+WS-0.3) in flight; #1499/#1512 gate cleared by owner 2026-08-26 (Wave 4 unblocked once Waves 2-3 land)
**Baseline:** durion-positivity-backend `3384210` (post-#1520 merge; branch restarted from `origin/main` 2026-08-26). Originally authored at `4c2ffb1`; matrix re-verified at `3384210` with no drift.
**Working branch:** `claude/issue-1519-tool-facade-1o21fs` (both repos)

---

## 1. Product decisions (locked — do not re-litigate in worksets)

These were decided by the product owner on 2026-08-26 and govern every workset:

- **D1 — Facades are the primary curated NL surface.** An LLM must be able to serve
  the common intents (lookup, search, status, summary per domain) through facade tools
  alone, without falling back to raw OpenAPI-discovered operations. Discovered ops
  remain a complement for the long tail; **no facade is deleted merely because a
  discovered op covers it.** (This supersedes the "delete the facade" bucket proposed
  in the #1519 analysis comment.)
- **D2 — Compose, don't narrow.** Where a facade names a resource no single service
  publishes (shop status, financial summary, price-for-SKU, …), the facade
  **coordinates multiple real service calls** inside pos-mcp-server and assembles the
  answer. Deleting or narrowing the intent is the fallback only when composition is
  genuinely impossible, and requires owner sign-off.
- **D3 — ADR-0021 stands.** "No direct API calls" is a statement about the consumer
  (the LLM), not about transport. TaxFacadeTool's direct call to `pos-tax:8091`
  remains the sanctioned exception (ADR-0014/ADR-0021, #641). All other facades route
  via the gateway (`http://pos-api-gateway` + `/{route}/v1/...`) or documented
  load-balanced routes.
- **D4 — Preconditions.** #1499 and #1512 (RBAC audit remediation) merged before
  execution — **confirmed by the owner 2026-08-26**; Wave 4 re-derives permission seeds
  against that merged state. Rebase performed and recorded in WS-0.1.

## 2. Governing references (read before coding)

| Ref | Why it binds this work |
| --- | --- |
| ADR-0011 / ADR-0014 | Gateway is the security boundary; routes are whitelist-only; internal services get no route |
| ADR-0021 | pos-tax internal-only contract; strict `POST /v1/tax/calculate` validation semantics |
| ADR-0042 | OpenAPI annotations are the REST contract source; `openapi.yaml` per module is the truth facades must match |
| ADR-0054 | Sell-price system-of-record split (governs where PricingFacadeTool reads from) |
| ADR-0057 + `V36` migration | Availability vs on-hand permission split; already names InventoryFacadeTool's real targets |
| `pos-mcp-server/README.md` §Facade tools | V18 seed-derivation procedure (mirror downstream `@PreAuthorize`); Flyway "never edit applied migrations" rule |
| `pos-api-gateway/src/main/resources/application.yml` | Route table: `Path=/{route}/**` + `StripPrefix=1`; the `{route}→service` mapping is authoritative for which module serves a facade path |

## 3. Definition of done (global invariants)

The issue closes when ALL of the following hold on the rebased branch:

1. **Every `@Tool` method resolves.** For each facade `@Tool` method: the resolved
   request (`base-url` + URI template, **HTTP method included**) matches an operation
   published in the `openapi.yaml` of **the module the gateway route actually
   targets** — or the method is a composition in which every downstream call
   individually satisfies this rule.
2. **Tests assert the contract, not the config.** No facade test derives its expected
   URI from the same property the tool reads. Expected method+path pairs live in one
   place (see WS-0.3) that the contract checker verifies against `openapi.yaml`.
3. **Checker is CI-wired and green.** `scripts/check-mcp-facade-paths.py` (upgraded
   per WS-0.2) runs in CI; the baseline file is empty by Wave 5.
4. **Permission seeds re-derived.** A new Flyway migration (V37+, never editing
   applied ones) re-derives `mcp_tool_permission` rows for every retargeted tool from
   the real target endpoints' merged `@PreAuthorize` guards, per the V18 procedure
   documented in the README. `PermissionGatingInvariantTest` and
   `FacadeToolPermissionSeedTest` updated and passing.
5. **Docs updated.** `pos-mcp-server/README.md` §Facade tools reflects the restored
   surface (incl. composition tools); this plan's evidence column is filled in.
6. **Eval guard holds.** `RetrievalLockTest` / tool-selection fixtures still pass;
   if fixtures referenced old tool semantics, they are updated in the same PR.

## 4. Execution model — how multiple agents run this

- **Ledger:** this document. Each workset has an ID, an owner agent type (from
  `durion/.claude/agents/`), dependencies, steps, acceptance criteria, and an
  `Evidence:` line the executing agent fills with commit SHA + test output reference.
  Mark `[x]` only with evidence.
- **Claiming:** an agent claims a workset by writing `CLAIMED <agent> <date>` on its
  Evidence line in a commit, before starting. One workset, one agent at a time.
  Worksets inside a wave with no listed dependency on each other run **in parallel**.
- **Branch/PR discipline:** all work on `claude/issue-1519-tool-facade-1o21fs`
  (durion-positivity-backend). Suggested PR cut: one PR per wave (Wave 2 may split
  into two PRs — gateway-domain repoints vs cross-domain repoints — if review size
  demands). PR titles reference #1519.
- **Verification commands (run before every push):**
  ```bash
  cd durion-positivity-backend
  ./mvnw -pl pos-mcp-server -am test              # module tests
  ./mvnw spotless:apply                            # format (then check git status)
  python3 scripts/check-mcp-facade-paths.py        # contract check (baselined until Wave 5)
  ./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test   # only if packages moved
  ```
- **Backend code research:** subagents use `mcp__tokensave-backend__*` tools for
  durion-positivity-backend Java exploration (per durion `CLAUDE.md`), passing
  `seen_node_ids` forward via `exclude_node_ids`.
- **Escalation:** any workset that discovers its disposition (Section 7) is wrong
  does NOT improvise — it records the finding on its Evidence line, flags the owner,
  and moves on. (All six dispositions that originally required ⚠ OWNER sign-off were
  decided 2026-08-26 and are recorded in the matrix.)

---

## 5. Wave 0 — Kickoff & tooling (blocking; everything depends on this wave)

### WS-0.1 Rebase + matrix refresh
**Agent:** anvil (or API Orchestrator directly). **Deps:** owner's rebase request.
1. Rebase/reset `claude/issue-1519-tool-facade-1o21fs` per owner instruction; record SHA here.
2. Confirm #1499 and #1512 are merged into the base; record their merge commits.
3. Re-run the (still-unmodified) checker; diff its output against Section 7's matrix.
   Update the matrix for any drift (new endpoints, moved controllers) in a doc-only commit.
**Acceptance:** SHA recorded; matrix marked REFRESHED.
**Evidence:** 2026-08-26 — backend branch restarted at `3384210` (origin/main, post-#1520;
prior remote branch was deleted on the #1518 merge, so the restart is a clean re-cut, not a
rebase of unmerged work). durion plan branch based on `a690692` (origin/master). Checker v1
re-run at `3384210`: output unchanged (10/45 ok, 35 breaks, 0 baselined) — matrix REFRESHED,
no drift; `4c2ffb1..3384210` touches only gateway permission catalog, security tests and
`scripts/generate-permissions.py` (Wave 4-relevant, matrix-neutral). **Gate outstanding:**
#1499 and #1512 are still OPEN as of this refresh (partial remediation merged via
#1518/#1520). Per D4 they must be merged before Wave 4 executes; Waves 0-3 are not blocked.

### WS-0.2 Contract checker v2
**Agent:** anvil. **Deps:** WS-0.1.
Upgrade `scripts/check-mcp-facade-paths.py` to close the four gaps found in #1519 review:
1. **Route-aware resolution:** parse the gateway route table
   (`pos-api-gateway/src/main/resources/application.yml`) and resolve each template's
   first segment to the routed service; match the stripped path **only against that
   module's** `openapi.yaml`. (Kills the false passes: `/customer/v1/vehicles/search`
   currently "matches" pos-vehicle-inventory, `/people/v1/people?q=` currently
   "matches" pos-people-contact — both actually 404 through their configured routes.)
2. **Base-url modelling:** resolve `base-url` + template per property instead of
   blind-stripping segment one; model the pos-tax direct-call exception
   (`http://pos-tax:8091/v1/tax` + relative template → match against `pos-tax/openapi.yaml`).
3. **HTTP method comparison:** read each `@Tool` method's verb from the facade source
   (or a declared manifest, see WS-0.3) and require the target `openapi.yaml` path to
   publish that verb. (Catches `calculateTax` GET vs published `post`-only.)
4. **Profile overlays:** accept `--profile alpha|prod|dev` merging
   `application-{profile}.yml` over `application.yml`; CI runs the alpha profile
   (the deployed one, per `docker-compose.yml`).
Regenerate `scripts/mcp-facade-paths-baseline.json` with checker v2 output (expect
~36 entries once false passes are counted). Add a self-test in the spirit of
`mutation-check-selftest.sh`.
**Acceptance:** checker v2 + baseline committed; false passes now reported; self-test green.
**Evidence:** 2026-08-26 — COMPLETE, commit `10d5bd2`. v2 reports (profile alpha):
8/45 resolve, **37 breaks = 36 path-not-published + 1 method-mismatch** (tax calculate GET
vs published post-only). Both route false-passes (vehicles search via /customer, people
search via /people) now flagged. Baseline `scripts/mcp-facade-paths-baseline.json`
regenerated (37 entries); `check-mcp-facade-paths-selftest.py` all-pass (route-aware match,
wrong-module break, direct-base-url tax resolution, method mismatch, baseline gating,
profile deep-merge).

### WS-0.3 Single source of truth for expected contracts
**Agent:** API Surface Coder. **Deps:** WS-0.2.
Create `FacadeContract` (test-scope constants class or YAML manifest in
`pos-mcp-server/src/test/resources/`) enumerating, per `@Tool` method: HTTP method,
route prefix, downstream path template. Rewrite every `*FacadeToolTest` to assert
`MockRestServiceServer` expectations **from this manifest**, not from the property
the tool reads. Checker v2 (WS-0.2) validates the manifest against `openapi.yaml`s —
this is the loop-closure that makes the #1519 failure mode (test encodes the same
assumption as the code) structurally impossible.
**Acceptance:** all facade tests read expectations from the manifest; checker
validates the manifest; a deliberate manifest corruption fails the checker (prove once, revert).
**Evidence:**

---

## 6. Waves 1–5

### Wave 1 — Disposition confirmation (read-only, fast, fully parallel)
**Agents:** domain agents (`durion/.claude/agents/domains/`) per row-group of the
Section 7 matrix; Code Review Agent consolidates.
For each facade: confirm the proposed target endpoint exists on the rebased branch
(path, verb, query/body params, `@PreAuthorize` guards), and confirm the route
prefix maps to the serving module. Resolve every cell marked **VERIFY**. (The six
former ⚠ OWNER cells were all decided by the product owner on 2026-08-26 and are
recorded in the matrix; two produced follow-on issues #1521/#1522.) Output: matrix
updated in place, all cells CONFIRMED.
**Acceptance:** no VERIFY cells remain.
**Evidence:** 2026-08-26 — COMPLETE. Two read-only research agents verified all targets at
`3384210` (verbs, params, merged `@PreAuthorize` guards, file:line cites). Matrix rewritten
in place with confirmed targets. Contradictions found and resolved with the owner same day:
catalog-items/{type} has no GET (→ products/search?category, the owner's pre-approved
fallback); price-books collection POST-only (→ by-id GET, matches tool signature);
event summaries take zero params (→ RESHAPE getEventSummary(window), owner-approved);
JE list filters only exact entryNumber (→ RESHAPE to general-ledger report, owner-approved);
/v1/appointments has no GET (→ schedules/view + workexec/wip for shop composes);
/v1/invoices/search has no customer param (→ items/search?partyId + de-dup);
pos-people has no employee list (→ DEFERRED #1523, owner-decided);
promos+price-books have no list GETs (→ RESHAPE searchPricing to lookups, owner-decided);
price quotes require mandatory customerTierId (→ PRICE-SKU composes MSRP + optional
location, owner-decided); availability param is productSku not sku.

### Wave 2 — Mechanical repoints (Bucket A)
**Agent per workset:** API Surface Coder (config+tool signature), Backend Testing
Agent (tests). **Deps:** Wave 1 row confirmed; WS-0.3 manifest in place.
Per domain workset (parallel): update `application.yml` template default (+ alpha
overlay if the base-url changes), adjust `@Tool` method params where the real
endpoint's contract differs (path variable names, query params), update the
WS-0.3 manifest row, and let the rewritten test assert the real contract. Remove the
corresponding baseline entries.
Worksets: **WS-2.CAT** (Catalog ×3) · **WS-2.CUST** (Customer ×2: get/search) ·
**WS-2.INV** (Inventory ×3, targets fixed by V36/ADR-0057) · **WS-2.ORD** (Order ×2) ·
**WS-2.EVT** (Events ×3) · **WS-2.LOC** (Location ×2: search + cross-domain inventory) ·
**WS-2.INVC** (Invoice ×1) · **WS-2.WO** (Workorder ×1) · **WS-2.VEH** (Vehicle ×3,
route change customer→vehicle-inventory) · **WS-2.HR** (Hr ×1: searchEmployees route fix) ·
**WS-2.ACC** (Accounting ×2: balance/journal) · **WS-2.SHOP** (searchShops→locations) ·
**WS-2.TAXSUM** (getTaxSummary→accounting tax-liability, moves off the direct client).
**Acceptance per workset:** checker v2 passes for the domain's templates with
baseline entries removed; module tests green; Spotless clean.
**Evidence (per workset):**

### Wave 3 — Composition facades (Bucket B)
**Agent per workset:** Domain Data Coder (logic) + API Surface Coder (tool surface) +
Backend Testing Agent; domain agent reviews semantics. **Deps:** Wave 1 decisions;
WS-0.3. Wave 2 need not be complete, but shared plumbing (below) lands first.
**Shared plumbing (WS-3.0, blocking for this wave):** a small composition support in
`internal/orchestration/tools/` — sequential/parallel downstream calls through the
existing instrumented RestClient, per-call error capture so one failed leg degrades
the answer instead of failing the tool, and a composed-response envelope (JSON with
per-source sections + `sources` list). Unit-tested with MockRestServiceServer
multi-expectation tests driven by the WS-0.3 manifest (a composition's manifest row
lists **all** its downstream calls).
Worksets (parallel after WS-3.0): **WS-3.FINSUM** · **WS-3.REPORTS** ·
**WS-3.CUSTHIST** · **WS-3.SHOPSTATUS** · **WS-3.SHOPQUEUE** · **WS-3.PRICE-SKU** ·
**WS-3.TAXCALC** (targets in Section 7; PRICESEARCH moved to Wave 2 as a lookup reshape).
WS-3.TAXCALC also removes the deferred `getTaxRate` method, template, and manifest row (#1522).
**Acceptance per workset:** every downstream leg passes checker v2; tool returns a
correct composed answer in tests incl. one degraded-leg case; tool `@Tool`
description updated to describe the composed semantics (the LLM reads it).
**Evidence:** WS-3.0 COMPLETE 2026-08-26, commit `23d96b8` — `ToolComposition` fluent
support (named legs, sequential execution inside `render()`, per-leg envelopes:
ok/not_authorized(403, no body leak)/error(no stack traces), `.require()` → top-level
ok|degraded status, JSON envelope with `sections` + `sources`). `ToolCompositionTest`
13/13 green under full quality gates. Remaining Wave 3 worksets code against this API.
**Evidence (per remaining workset):**

### Wave 4 — Permission seed re-derivation
**Agent:** Security & Authorization Domain Agent designs; anvil implements. **Deps:**
Waves 2–3 complete (targets final); D4 (#1499/#1512 merged).
1. For every retargeted or composed tool, re-run the V18 derivation procedure
   (README §Facade tools) against the **real** target endpoints: union the merged
   class+method `@PreAuthorize` codes across all of a tool class's `@Tool` methods;
   compositions union across **all downstream endpoints**. Cross-check codes against
   the services' `permissions.yaml` (ADR-0025) as remediated by #1499/#1512.
2. Ship as new migration `V37__facade_permission_rederivation.sql` (+ h2 twin):
   delete-and-reinsert per tool, idempotent, with the derivation table in the
   migration comment. **Never edit V18/V35/V36.**
3. Update `FacadeToolPermissionSeedTest` to assert seed == derivation manifest;
   `PermissionGatingInvariantTest` stays green (no tool with zero permission rows).
**Acceptance:** migration applied clean on pg+h2 test runs; seed test derives from
the same manifest as WS-0.3 (one truth for "what does this tool call").
**Evidence:**

### Wave 5 — Enforcement, docs, close-out
**Agent:** Documentation Agent + anvil. **Deps:** Waves 2–4.
1. Baseline file emptied; checker v2 wired as a required CI step (fail on any entry).
2. `pos-mcp-server/README.md` §Facade tools rewritten: restored purpose statement
   (curated NL surface per D1), composition tools listed with their downstream calls,
   V37 referenced beside V18/V35/V36.
3. Re-run eval suites (`RetrievalLockTest`, fixture validation); update tool-selection
   fixtures whose expected tools changed semantics.
4. Full reactor build; PR(s) merged; comment on #1519 summarizing with links; close.
**Acceptance:** Definition of done (Section 3) fully satisfied.
**Evidence:**

---

## 7. Disposition matrix (per @Tool method) — CONFIRMED by Wave 1 (2026-08-26)

Legend: **REPOINT** = config/signature change to an existing endpoint · **RESHAPE** = repoint
plus an owner-approved signature change so the tool matches the real contract · **COMPOSE** =
multi-call coordination (Wave 3) · **OK** = verified reachable · **DEFERRED** = method removed
from the live surface, follow-on issue tracks the real endpoint. Guards listed are the
downstream `@PreAuthorize` codes Wave 4 seeds from (file:line cites are in the Wave 1 agent
reports). Route prefixes are gateway route ids.

| Tool.method | Disposition → confirmed target | Guard(s) |
| --- | --- | --- |
| Accounting.getAccountBalance | REPOINT → `GET /accounting/v1/accounting/gl-accounts/{glAccountId}/balance` (UUID id; no code/name finder exists — description must say UUID) | `accounting:coa:view` |
| Accounting.searchJournalEntries | RESHAPE (owner 2026-08-26) → `getGeneralLedger(startDate, endDate, accountId?)` → `GET /accounting/v1/accounting/reports/financial/general-ledger` (JE list only filters exact `entryNumber` — unusable for search) | `reporting:view:financial-statements` |
| Accounting.getFinancialSummary | COMPOSE (WS-3.FINSUM) → income-statement (`startDate`+`endDate`) + balance-sheet (`asOfDate`=endDate) + trial-balance (**`asOf`**=endDate — param name differs!) | `reporting:view:financial-statements` |
| Reporting.getSalesReport | REPOINT → `GET /accounting/v1/accounting/reports/financial/income-statement?startDate&endDate` (period→range mapping in facade) | `reporting:view:financial-statements` |
| Reporting.getInventoryReport | REPOINT → `GET /inventory/v1/inventory/locations/{locationId}/inventory-rollup` (parent-location semantics: empty for a bare site — description must say so; site-level inquiry is the fallback) | `inventory:on_hand:view` |
| Reporting.getRevenueReport | COMPOSE (WS-3.REPORTS, owner 2026-08-26) → income-statement revenue lines + aged-receivables (`asOfDate`=endDate) | `reporting:view:financial-statements` |
| Catalog.getProduct | REPOINT → `GET /catalog/v1/products/{productId}` (`/detail` requires `location_id` and 400s without it — do not default to it) | `hasRole('ADMIN') or catalog:product:view` |
| Catalog.searchCatalog | REPOINT → `GET /catalog/v1/products/search?q={query}` (also takes `category`/`sku`/`brand`/`limit`) | same |
| Catalog.getCatalogByCategory | REPOINT → `GET /catalog/v1/products/search?category={category}` — owner's pre-approved fallback; the chosen `/v1/catalog-items/{type}` has **no GET** (POST/PUT/DELETE only) | same |
| Customer.getCustomer | REPOINT → `GET /customer/v1/crm/accounts/parties/{partyId}` (identity projection per ADR-0015; thin payload — description sets expectations) | `crm:party:view` |
| Customer.searchCustomers | REPOINT → `GET /customer/v1/crm/accounts/parties?name={query}` (browse: unified directory incl. individuals, ci-contains, paginated; the POST `/search` is commercial-only and ignores paging) | `crm:party:view` |
| Customer.getCustomerHistory | COMPOSE (WS-3.CUSTHIST) → snapshot `/v1/crm/snapshot/party/{partyId}` + interactions `/v1/crm/parties/{partyId}/interactions` + invoice lines `/invoice/v1/invoices/items/search?partyId=` (de-dup by invoice) + workorders `/workorder/v1/workorders/search?customerId=` | `crm:party:view`, `crm:interaction:view`, `invoice:manage`, `workorder:workorder:view` |
| Events.getEventTypes | REPOINT → `GET /event-receiver/v1/eventTypes/active` | none (unauthenticated read; seed `AUTHENTICATED`) |
| Events.searchEvents | RESHAPE (owner 2026-08-26) → `getEventSummary(window: lastHour|lastDay|lastWeek)` → `GET /event-receiver/v1/events/summary/{window}` (endpoints take zero params) | none (seed `AUTHENTICATED`) |
| Events.getEventHistory | DEFERRED → [#1521](https://github.com/louisburroughs/durion-positivity-backend/issues/1521); method removed in WS-2.EVT | — |
| Hr.getEmployee / getEmployeeSchedule | OK (route+path verified) | `people:employee:view` |
| Hr.searchEmployees | DEFERRED (owner 2026-08-26) → [#1523](https://github.com/louisburroughs/durion-positivity-backend/issues/1523) — pos-people has **no** employee list/search (`/v1/people/employees` is POST-only); method removed in WS-2.HR | — |
| Inventory.checkStock | REPOINT → `GET /inventory/v1/inventory/availability/by-sku?productSku={sku}` — param is **`productSku`**, not `sku` | `inventory:availability:read` |
| Inventory.searchInventory | REPOINT → same endpoint (`productSku` + optional `locationId`) | `inventory:availability:read` |
| Inventory.getLocationStock | REPOINT → `GET /inventory/v1/inventory/locations/{locationId}/inventory-inquiry` | `inventory:on_hand:view` (distinct family per ADR-0057) |
| Invoice.getInvoice / searchInvoices | OK (note: blank `q` returns empty page, not all) | `invoice:manage` |
| Invoice.getInvoicesByCustomer | REPOINT → `GET /invoice/v1/invoices/items/search?partyId={customerId}` + de-dup lines by invoice in the facade (`/v1/invoices/search` has no customer param; newest-200-lines bound disclosed in description) | `invoice:manage` |
| Location.getLocation | OK | `location:read` |
| Location.searchLocations | REPOINT → `GET /location/v1/locations` (zero params) + in-facade ci name/code contains-filter (roster paginates and would truncate a scan) | `location:read` |
| Location.getLocationInventory | REPOINT → `GET /inventory/v1/inventory/locations/{locationId}/inventory-inquiry` (cross-domain) | `inventory:on_hand:view` |
| Order.getOrder | REPOINT → `GET /order/v1/orders/carts/{orderId}` | `isAuthenticated()` + `order:order:view` |
| Order.searchOrders | RESHAPE → `listOrders(status?, clerkId?, terminalId?)` → `GET /order/v1/orders/carts` (no customer/date/free-text filters exist; description constrains the intent) | `order:order:view` |
| Pricing.getPriceForSku | COMPOSE (WS-3.PRICE-SKU, owner 2026-08-26: MSRP + optional location) → `GET /catalog/v1/products/search?sku=&detailed=true` → active MSRP; optional `locationId` adds `GET /catalog/v1/products/pricing/effective-price/{locationId}/{productId}`. No tier hop (quotes require mandatory `customerTierId`) | `catalog:product:view`, `catalog:location_price_override:read` |
| Pricing.searchPricing | RESHAPE (owner 2026-08-26: lookups) → `getPromotionByCode(promoCode)` → `GET /price/v1/promotions/offers/by-code/{promoCode}` + `listPriceRestrictions()` → `GET /price/v1/price/restrictions/rules` (neither promos nor price-books publish a list GET) | `pricing:promotion:view`, `pricing:rule:view` |
| Pricing.getPriceList | REPOINT → `GET /catalog/v1/products/price-books/{priceBookId}` (by-id exists and matches the tool signature; collection is POST-only; ADR-0054) | `catalog:price_book:read` |
| ShopManager.getShopStatus | COMPOSE (WS-3.SHOPSTATUS) → `GET /shop-manager/v1/schedules/view?locationId&date` (date defaults to today; `/v1/appointments` has **no GET list**) + `GET /workorder/v1/workexec/wip?locationId=` + `GET /location/v1/locations/{locationId}` | `shop:schedule:view`, `workorder:wip:view`, `location:read` |
| ShopManager.getShopQueue | COMPOSE (WS-3.SHOPQUEUE) → `GET /workorder/v1/workexec/wip?locationId=` (the only location-filtered open-workorder surface; `/v1/workorders/search` filters neither location nor status) + schedules/view | `workorder:wip:view`, `shop:schedule:view` |
| ShopManager.searchShops | REPOINT → `GET /location/v1/locations` + in-facade filter (shops are locations) | `location:read` |
| Tax.calculateTax | COMPOSE (WS-3.TAXCALC) → `GET /location/v1/locations/{locationId}` → map `postalCode`/`country`→`countryCode`/`state`→`regionCode` → direct `POST /v1/tax/calculate` with one synthesized line item (`quantity=1`, `unitPrice=amount`, non-zero enforced). Degraded path when the location has no address (address fields optional in LocationResponseDTO). Use nested `destinationAddress`, never the legacy flat fields | `location:read`, `tax:calculate` |
| Tax.getTaxRate | DEFERRED → [#1522](https://github.com/louisburroughs/durion-positivity-backend/issues/1522); method removed in WS-3.TAXCALC | — |
| Tax.getTaxSummary | REPOINT → `GET /accounting/v1/accounting/reports/financial/tax-liability` (moves off the direct client; verify guard in-workset — expected `reporting:view:financial-statements`) | verify in WS-2.TAXSUM |
| Vehicle.getVehicle | REPOINT → `GET /vehicle-inventory/v1/vehicle-registry/{vehicleId}` (returns deactivated too — check `isActive`) | `isAuthenticated()` + `vehicle-inventory:registry:view` |
| Vehicle.searchVehicles | REPOINT → `GET /vehicle-inventory/v1/vehicles/search?q={query}` (min 3 chars, 6 when VIN-shaped; GET params are `q`/`enableContains`) | `vehicle-inventory:search:view` |
| Vehicle.getVehiclesByCustomer | REPOINT → `GET /customer/v1/crm/{customerId}/vehicles` (`customerId` **is** the partyId; the `parties/{partyId}/vehicles` path is POST-only) | `crm:vehicle:view` |
| Workorder.getWorkorder / searchWorkorders | OK (search filters: `q`/`customerId`/`vehicleId` only) | `workorder:workorder:view` |
| Workorder.getWorkorderStatus | REPOINT → `GET /workorder/v1/workorders/{workorderId}` (response carries `status`; `/detail` is `isAuthenticated()`-only and 400s on not-found) | `workorder:workorder:view` |
| Admin.* (4 methods) | OK — verbs verified. Caveat: `/v1/audit/events` applies only `fromDate`/`toDate`/`actorId`/`eventType`/`aggregateId`; other documented params are accepted but ignored — tool description must not advertise them | `security:permission:view`, `security:audit:view` |
| ExaWebSearch.webSearch | OUT OF SCOPE | — |

**Count note (final):** checker v2 (WS-0.2) is authoritative; Wave 1 confirmed the two
route-mismatch false passes (vehicles search, HR search), putting the pre-fix broken count at
~36-37 of 45 including the tax method mismatch.

## 8. Risks & guardrails

- **Composed tools can mask downstream authz gaps** — Wave 4 unions permissions over
  *all* legs; the degraded-leg envelope must not leak data the caller's permissions
  would have denied (each leg still relays the caller's bearer token; a 403 leg
  renders as "not authorized for X", never retried with elevated context).
- **Flyway:** V18/V35/V36 are applied — additive V37 only (README rule).
- **Scope creep:** no new backend endpoints in this plan. If Wave 1 concludes an
  intent genuinely needs a new downstream endpoint, that is a separate story per
  domain, raised to the owner — not built here.
- **`workorder` is one word** in all code/docs produced (durion platform rule).
- **Controller-chain rule** (durion CLAUDE.md) is not triggered: no downstream
  controllers change in this plan; only pos-mcp-server internals + config.
