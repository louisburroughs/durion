# Plan: Restore pos-mcp-server Tool Facades to Their Established Purpose

**Source issue:** [durion-positivity-backend#1519](https://github.com/louisburroughs/durion-positivity-backend/issues/1519) (see the analysis comment for the evidence trail)
**Status:** READY FOR KICKOFF (after rebase — see WS-0.1)
**Baseline:** durion-positivity-backend `4c2ffb1` (post-#1518 merge). This plan's facts were verified at that commit; WS-0.1 refreshes them.
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
- **D4 — Preconditions.** #1499 and #1512 (RBAC audit remediation) are assumed
  **merged before kickoff**; Wave 4 re-derives permission seeds against that merged
  state. The owner will request a **rebase of the working branches before kickoff**;
  no workset starts until WS-0.1 records the rebased SHA.

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
  and moves on. Dispositions marked ⚠ OWNER already need explicit sign-off before
  implementation.

---

## 5. Wave 0 — Kickoff & tooling (blocking; everything depends on this wave)

### WS-0.1 Rebase + matrix refresh
**Agent:** anvil (or API Orchestrator directly). **Deps:** owner's rebase request.
1. Rebase/reset `claude/issue-1519-tool-facade-1o21fs` per owner instruction; record SHA here.
2. Confirm #1499 and #1512 are merged into the base; record their merge commits.
3. Re-run the (still-unmodified) checker; diff its output against Section 7's matrix.
   Update the matrix for any drift (new endpoints, moved controllers) in a doc-only commit.
**Acceptance:** SHA recorded; matrix marked REFRESHED.
**Evidence:**

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
**Evidence:**

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
prefix maps to the serving module. Resolve every cell marked **VERIFY**. Escalate
every ⚠ OWNER cell as one consolidated question list to the product owner. Output:
matrix updated in place, all cells either CONFIRMED or owner-decided.
**Acceptance:** no VERIFY cells remain; ⚠ OWNER cells have recorded decisions.
**Evidence (per domain group):**

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
**WS-3.PRICESEARCH** · **WS-3.TAXCALC** · **WS-3.TAXRATE** (targets in Section 7).
**Acceptance per workset:** every downstream leg passes checker v2; tool returns a
correct composed answer in tests incl. one degraded-leg case; tool `@Tool`
description updated to describe the composed semantics (the LLM reads it).
**Evidence (per workset):**

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

## 7. Disposition matrix (per @Tool method)

Legend: **REPOINT** = config/signature change to an existing endpoint · **COMPOSE** =
multi-call coordination (Wave 3) · **OK** = verified reachable at baseline · **VERIFY** =
Wave 1 confirms detail · ⚠ OWNER = product sign-off required before implementation.
Route prefixes are gateway route ids (`Path=/{route}/**`, StripPrefix=1).

| Tool.method | Configured today (broken unless OK) | Disposition → target |
| --- | --- | --- |
| **Accounting.getAccountBalance** | `/accounting/v1/accounting/accounts/{id}/balance` | REPOINT → `/accounting/v1/accounting/gl-accounts/{glAccountId}/balance` |
| **Accounting.searchJournalEntries** | `.../journal-entries/search?q=` | REPOINT → `/accounting/v1/accounting/journal-entries` (GET; VERIFY filter params) |
| **Accounting.getFinancialSummary** | `.../summary/{period}` | COMPOSE → income-statement + balance-sheet (+ trial-balance) for period (WS-3.FINSUM) |
| **Reporting.getSalesReport** | `/accounting/v1/reporting/sales/{period}` | COMPOSE → `/accounting/v1/accounting/reports/financial/income-statement` (revenue section; VERIFY period params) (WS-3.REPORTS) |
| **Reporting.getInventoryReport** | `.../reporting/inventory/{locationId}` | REPOINT → `/inventory/v1/inventory/locations/{locationId}/inventory-rollup` (cross-domain; base-url note) |
| **Reporting.getRevenueReport** | `.../reporting/revenue/{period}` | COMPOSE → income-statement revenue + AR aging (WS-3.REPORTS) ⚠ OWNER (exact composition) |
| **Catalog.getProduct** | `/catalog/v1/catalog/products/{id}` | REPOINT → `/catalog/v1/products/{productId}` (or `/detail`; VERIFY which serves the NL intent better) |
| **Catalog.searchCatalog** | `/catalog/v1/catalog/search?q=` | REPOINT → `/catalog/v1/products/search` (VERIFY verb+params; consider also `/v1/products/services/search` as Wave 3 follow-up) |
| **Catalog.getCatalogByCategory** | `/catalog/v1/catalog/categories/{cat}` | REPOINT → `/catalog/v1/catalog-items/{type}` (VERIFY category≈type semantics) ⚠ OWNER if mismatch |
| **Customer.getCustomer** | `/customer/v1/customers/{id}` | REPOINT → `/customer/v1/crm/accounts/parties/{partyId}` |
| **Customer.searchCustomers** | `/customer/v1/customers/search?q=` | REPOINT → `/customer/v1/crm/accounts/parties/search` (VERIFY verb/params) |
| **Customer.getCustomerHistory** | `/customer/v1/customers/{id}/history` | COMPOSE → party interactions + crm snapshot + invoices-by-customer + workorder search (WS-3.CUSTHIST) |
| **Events.getEventTypes** | `/event-receiver/v1/events/eventTypes` | REPOINT → `/event-receiver/v1/eventTypes/active` |
| **Events.searchEvents** | `.../events/summary?q=` | REPOINT → `/event-receiver/v1/events/summary/lastDay` family (VERIFY EventSummaryController's query surface) |
| **Events.getEventHistory** | `.../events/summary?entityId=` | REPOINT/VERIFY → entity-filtered summary endpoint; if none, COMPOSE over summary windows ⚠ OWNER |
| **Hr.getEmployee / getEmployeeSchedule** | `/people/...` | OK (route+path verified) |
| **Hr.searchEmployees** | `/people/v1/people?q=` | REPOINT (false pass — `/v1/people` belongs to people-contact, different route) → `/people/v1/people/employees` list (VERIFY params) or `/people-contact/v1/people?q=` ⚠ OWNER (which population: employees vs all people) |
| **Inventory.checkStock** | `/inventory/v1/inventory/stock/{sku}` | REPOINT → `/inventory/v1/inventory/availability/by-sku` (per V36/ADR-0057) |
| **Inventory.searchInventory** | `.../inventory/search?q=` | REPOINT → `/inventory/v1/inventory/availability/by-sku` (same controller, per V36; VERIFY param mapping) |
| **Inventory.getLocationStock** | `.../locations/{id}/stock` | REPOINT → `/inventory/v1/inventory/locations/{locationId}/inventory-inquiry` (per V36) |
| **Invoice.getInvoice / searchInvoices** | — | OK |
| **Invoice.getInvoicesByCustomer** | `/invoice/v1/invoices/customer/{id}` | REPOINT → `/invoice/v1/invoices/search` with customer filter (VERIFY search params) |
| **Location.getLocation** | — | OK |
| **Location.searchLocations** | `/location/v1/locations/search?q=` | REPOINT → `/location/v1/locations` list or `/v1/locations/roster` (VERIFY filter params) |
| **Location.getLocationInventory** | `/location/v1/locations/{id}/inventory` | REPOINT → `/inventory/v1/inventory/locations/{locationId}/inventory-rollup` (cross-domain) |
| **Order.getOrder** | `/order/v1/orders/{orderId}` | REPOINT → `/order/v1/orders/carts/{orderId}` |
| **Order.searchOrders** | `/order/v1/orders/search?q=` | REPOINT → `/order/v1/orders/carts` (GET list; VERIFY filters) |
| **Pricing.getPriceForSku** | `/price/v1/pricing/sku/{sku}` | COMPOSE → catalog `/v1/products/by-code` → `POST /price/v1/price/quotes` (or catalog effective-price per ADR-0054) (WS-3.PRICE-SKU) |
| **Pricing.searchPricing** | `/price/v1/pricing/search?q=` | COMPOSE/VERIFY → promotions offers list + price-books resolve (WS-3.PRICESEARCH) ⚠ OWNER (intent definition) |
| **Pricing.getPriceList** | `/price/v1/pricing/lists/{id}` | REPOINT → `/catalog/v1/products/price-books` (ADR-0054: price books live in catalog; VERIFY by-id shape) |
| **ShopManager.getShopStatus** | `/shop-manager/v1/shop/{shopId}/status` | COMPOSE → appointments (by location) + `/shop-manager/v1/schedules/view` + workorder search (WS-3.SHOPSTATUS) |
| **ShopManager.getShopQueue** | `.../shop/{shopId}/queue` | COMPOSE → open workorders at location + upcoming appointments (WS-3.SHOPQUEUE) |
| **ShopManager.searchShops** | `.../shop/search?q=` | REPOINT → `/location/v1/locations` roster (shops are locations; VERIFY) |
| **Tax.calculateTax** | GET `/calculate?amount=&locationId=` (direct) | COMPOSE → resolve location→address via `/location/v1/locations/{id}`, then `POST /v1/tax/calculate` (direct client, ADR-0021 body contract) (WS-3.TAXCALC) |
| **Tax.getTaxRate** | `/rates/{locationId}` (direct) | COMPOSE → derive effective rate via canonical `POST /v1/tax/calculate` probe (WS-3.TAXRATE) ⚠ OWNER (semantics of "the rate") |
| **Tax.getTaxSummary** | `/summary/{period}` (direct) | REPOINT → `/accounting/v1/accounting/reports/financial/tax-liability` (moves off direct client; gateway-routed) |
| **Vehicle.getVehicle** | `/customer/v1/vehicles/{id}` | REPOINT → `/vehicle-inventory/v1/vehicle-registry/{vehicleId}` |
| **Vehicle.searchVehicles** | `/customer/v1/vehicles/search?q=` | REPOINT (false pass — wrong route) → `/vehicle-inventory/v1/vehicles/search` |
| **Vehicle.getVehiclesByCustomer** | `/customer/v1/vehicles/customer/{id}` | REPOINT → `/customer/v1/crm/accounts/parties/{partyId}/vehicles` (or `/v1/crm/{customerId}/vehicles`; VERIFY id semantics vs ADR-0012) |
| **Workorder.getWorkorder / searchWorkorders** | — | OK |
| **Workorder.getWorkorderStatus** | `.../workorders/{id}/status` | REPOINT → `/workorder/v1/workorders/{workorderId}` (status in body; description says "status"; consider `/detail`) |
| **Admin.* (4 methods)** | `/security-service/...` | OK (`/v1/users/{userId}/permissions` + `/v1/audit/events` verified) — include in WS-0.3 manifest anyway |
| **ExaWebSearch.webSearch** | external SaaS | OUT OF SCOPE |

**Count note:** with route-aware matching (WS-0.2), the true broken count at baseline
is ~36 of 45 templates (the #1519 body says 35; the analysis comment corrected to 34;
both predate discovery of the two route-mismatch false passes). Checker v2 output is
authoritative; update this note in WS-0.1/WS-0.2.

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
