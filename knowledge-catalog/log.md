# Directory Update Log

## 2026-10-07

* **Regenerated**: 70 ADR, 16 domain, 47 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Updated**: `domains/accounting.md` after the accounting workspace specification recorded the 2026-10-07 rulings (AW32–AW35); only its
  `generated.at` changed, and no other entry was touched.

## 2026-10-05

* **Regenerated**: 70 ADR, 16 domain, 47 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Updated**: `domains/accounting.md` indexes the new accounting workspace specification
  `domains/accounting/SPEC-accounting-workspace.md` (81 documents). Only that entry was regenerated; timestamp drift in unrelated
  entries and a new backend module (`pos-kafka-common`) were left for a full regeneration.
* **Added**: `adr/0070-bill-intake-ownership-and-vendor-master.md` for the proposed ADR-0070; `adr/index.md` and the root index count it.
* **Pinned**: `pos-supplier` to `positivity` in `MODULE_DOMAIN`. The accounting workspace specification now names it more often than the
  positivity documents do, which would otherwise move its ownership to accounting.
* **Updated**: `adr/0070-bill-intake-ownership-and-vendor-master.md` to accepted (`status: stable`), with domain and topic tags
  (accounting, billing, positivity, supplier, events, security, rbac). `adr/0044-…`, `adr/0049-…` and `adr/0050-…` refreshed for
  their 2026-10-05 ADR-0070 amendments; 0049 and 0050 gain the `supplier` tag. Full regeneration: picks up
  the deferred timestamp drift and adds `backend/pos-kafka-common.md`; `backend/index.md` and the root index count 47 modules.

## 2026-10-03

* **Structure**: `backend/` gains `pos-platform-sender` (the FI-2 email/SMS sender, SES and End User Messaging), pinned to
  the positivity domain in `MODULE_DOMAIN` because its contract names it by role rather than by module name; `domains/positivity.md`
  lists it as an implementing module. Only the entries this change touches were regenerated: the other entries' timestamp drift
  against their sources' last commits predates it and is left for a full regeneration.
* **Regenerated**: 69 ADR, 16 domain, 46 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.

## 2026-10-02

* **Updated**: `domains/general.md` indexes the new gate-run record
  `mcp-server/archive/gate-runs/2026-10-02-scope-graph-rag-gate.md` (62 documents), and `adr/0069` is regenerated after
  its 2026-10-02 Changelog entries (§9 gate run and gate evidence rules).

## 2026-10-01

* **Updated**: `adr/` — the ADR-0068 and ADR-0069 entries are regenerated after their Changelog amendments (entity Nouls,
  web search, overall deadline, router-only routing, monitoring). `domains/general` now lists the question-tagging
  specification and implementation plan (60 documents). Timestamp-only drift in unrelated entries (shallow clone) was
  discarded.

## 2026-09-30

* **Updated**: `adr/` — ADR-0068 is now `status: stable` (accepted by the platform owner, local Jev-protocol provider). The
  gate4 router and NL-interface design records carry amendment blocks, the archive index notes them, and the scope-graph spec
  and plan no longer call ADR-0068 pending. `domains/general` now lists the scope-graph spec and plan (58 documents). Regenerated
  entries only; timestamp-only drift in unrelated entries was discarded.
* **Structure**: ADR-0068 (still proposed) now defaults to a local decision model: the Jev-protocol models Ollama 0.35 serves
  (`nimble`, `tev1`, `tev1:0.8b`) in the in-cell `ollama` container, chosen by a bake-off on the target host. Its catalog
  description is refreshed. No ADR is superseded.
* **Structure**: `adr/` gains ADR-0068 (pre-LLM question tagging with a decision model, TypeSafe Jev, in `pos-mcp-server`;
  proposed). No ADR is superseded. The ADR index and the catalog root count (69) are regenerated. Timestamp-only drift in
  unrelated entries from this regeneration (shallow clones) was discarded.
* **Structure**: `adr/` gains ADR-0069 (scope graph for pre-LLM narrowing in `pos-mcp-server`; proposed), linked from ADR-0068.
  No ADR is superseded.
* **Updated**: `adr/` — ADR-0069 is now `status: stable` (accepted by the platform owner); ADR-0068 is still pending.
  The ADR-0069 entry is regenerated; timestamp-only drift in unrelated entries was discarded.

## 2026-09-28

* **Regenerated**: 67 ADR, 16 domain, 45 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Structure**: `domains/accounting` gains the manual bank reconciliation specification
  (`domains/accounting/SPEC-manual-bank-reconciliation.md`, `type: Specification`, proposed): findings on the delivered story F2
  bank reconciliation (durion-positivity-backend#965), a provider-neutral bank-feed contract, statement import staging, matching and
  outstanding-item rules, approval and correction, accounting-period-close integration and a later `pos-bank-feed-plaid` phase. Module
  ownership is unchanged. Timestamp-only drift in unrelated entries from this regeneration was discarded.
* **Generator**: `scripts/generate-knowledge-catalog.py` now limits a domain's inferred **Implemented by** list to deployable
  services (`kind: Service`). A shared library such as `pos-domain-events` is named by every domain whose contracts it carries but
  implements none of them, and the new specification's mentions of it had displaced `pos-inventory` from the accounting entry's top
  four. The accounting entry is regenerated under the rule (`pos-inventory` returns); no other entry's module list changes.
* **Updated**: `domains/accounting` — the manual bank reconciliation specification is now `status: accepted`: decisions D1–D22 were
  ratified by the platform owner, D2 as an amended matching-complete approval gate (reconciliation baseline, gap bridge, linked `OTHER`
  adjustments, clearing-account governance). The accounting entry is regenerated for the status change; timestamp-only drift in
  unrelated entries was discarded.
* **Structure**: `adr/` gains ADR-0067, *Tenant Functional Currency and Multi-Currency Support* (`status: draft`, decision
  pending): the platform owner reopened the single-currency posture; the ADR stages one functional currency per tenant before
  transactions in several currencies and lists the owner decisions (PC-1 to PC-17, MC-1 to MC-11). The ADR index and the
  catalog root count are regenerated; timestamp-only drift in unrelated entries was discarded.
* **Updated**: `adr/` — ADR-0067 is now `status: stable` (accepted): the platform owner accepted the §2 recommendations subject to
  the rulings in a new §2.1, two of which depart from the table (currency locked at tenant creation rather than activation, one
  cross-currency settlement case in B1 rather than refused); the CAP-316 plant currency is a report-only view and Canada the first
  non-USD market. The ADR entry and index are regenerated; timestamp-only drift in
  unrelated entries was discarded.

## 2026-09-27

* **Updated**: `domains/shopmgmt`, `domains/workexec`, `domains/location`, `backend/pos-shop-manager`,
  `backend/pos-workorder` — stamps refreshed for backend wave 3 (durion-positivity-backend#2268, #2269,
  #2270): the shopmgmt contract guide's #2268 bay-eligibility-at-submit/reschedule section is now joined
  by the #2270 fill-in — the affected-appointments read (`AffectedAppointmentEvaluator`,
  DECISION-SHOPMGMT-022), the reschedule-to-another-resource path, and the DECISION-SHOPMGMT-004
  reschedule allowance (`appointments:reschedule:approve`, bit 542) — the workexec contract guide's
  pre-applied #2269 duty-class placement note was verified against code, and the location contract guide
  gained the deferred wave-2 follow-up on mobile-unit `maxDutyClass` PATCH-clears-on-null semantics.
  Verified against backend branch `claude/great-ritchie-w9lsyo-wave3`, commit `49f08e9e`. Timestamp-only
  drift in unrelated entries from this regeneration was discarded.

## 2026-09-26

* **Regenerated**: 66 ADR, 16 domain, 45 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Structure**: the shopmgmt and location domain entries refreshed after the bay and mobile-unit setup decisions (durion-positivity-backend#2245:
  DECISION-SHOPMGMT-021 to 023, DECISION-LOCATION-025 to 029). The inferred **Implemented by** lists move with the new mention counts: shopmgmt
  adds `pos-workorder` and `pos-location`; location now names `pos-catalog` in place of `pos-customer`. Module ownership is unchanged.
* **Structure**: the location contract guide gains the bay specialty map fact (durion-positivity-backend#2260, #2262); the location
  entry's inferred **Implemented by** list now names `pos-workorder` in place of `pos-people`. Module ownership is unchanged.
* **Structure**: the workexec and shopmgmt domain entries refreshed after the workorder location-transfer decisions (durion-positivity-backend#2258:
  DECISION-INVENTORY-023 to 028, DECISION-SHOPMGMT-024 and 025). The inferred **Implemented by** lists and module ownership are unchanged.

## 2026-09-24

* **Regenerated**: 66 ADR, 16 domain, 45 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Curated**: `domains/accounting/SPEC-inventory-adjustment-gl-posting.md` (`type: Specification`, `status: proposed`) — the ruling on
  durion-positivity-backend#2186 and the `InventoryAdjustedV1` fact → GL posting flow; indexed under the accounting domain and linked from the inventory
  domain index. The accounting and inventory cross-domain contracts now name `inventory.scrap.posted`, `inventory.adjustment.posted` and
  `inventory.product-value.changed` in place of the pre-ADR-0044 `inventory.inventoryAdjustment`.
* **Structure**: the accounting domain's inferred **Implemented by** list now names `pos-inventory` in place of `pos-workorder` — the new
  specification names the producer module often enough to enter the top four by mention count. Module ownership is unchanged
  (`pos-inventory` still belongs to the inventory domain).
* **Curated**: `domains/accounting/.business-rules/STORY_VALIDATION_CHECKLIST.md` (`type: Checklist`) — the accounting domain's story-validation
  checklist, previously the one domain guide missing from the one-per-domain set; indexed in the accounting entry's business-rules section.
* **Curated**: ADR-0008 (cost maintenance, dual ownership) is superseded by ADR-0048, which gains §6 recording what is retired and what is carried
  forward (cost types, weighted-average formula, authorization split); ADR-0009's link to ADR-0008 now resolves. Raised by
  `domains/accounting/SPEC-inventory-adjustment-gl-posting.md` D8 (durion-positivity-backend#2186).

## 2026-09-23

* **Curated**: `docs/architecture/plans/people-employee-register-execution-plan.md` carries OKF frontmatter
  (`type: Plan`, `status: active`) with an H2 title, per `.github/instructions/markdown.instructions.md`
  — added in 3504d5d. `docs/architecture` is a declared `PLATFORM_AREA`, so the next regeneration picks it
  up as a `knowledge-catalog/platform/` concept; until then the plan is undiscoverable through the catalog.
* **Not regenerated, deliberately**: the generator reads the sibling backend checkout, which is currently on an
  unmerged feature branch, so a run now would bake branch-only state into the module concepts. A `--dry-run`
  reports 91 files would change — legitimate drift since the 2026-09-21 run plus that branch. Regenerate from a
  `main` backend checkout once the employee-register waves merge; `--check` passes clean (211 concepts,
  0 problems) in the meantime.

## 2026-09-21

* **Regenerated**: 66 ADR, 16 domain, 45 module and 84 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Structure**: `--check` now fails on a platform document whose `status` word is not in `PLATFORM_STATUS`. An
  unmapped word left the entry with no OKF `status`, which consumers read as `stable`; the 2026-09-06 scope plan's
  `in-progress` was the case that showed it, and is now mapped to `draft`.
* **Curated**: the three design records relocated from the backend (`2026-05-14` aggregate-first discovery,
  `2026-06-09` internal service discovery reconciliation, `2026-08-27` domain interaction diagrams) and the
  observability reference note gained OKF frontmatter with a lifecycle. The service-discovery record is marked
  `superseded` and points at `docs/architecture/INTERNAL_TRANSPORT_AND_SERVICE_DISCOVERY.md`, which absorbed the
  three migration-era documents it named as inputs.

## 2026-09-20

* **Regenerated**: 66 ADR, 16 domain, 45 module and 84 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Structure**: new `platform/` sub-bundle — a fourth concept kind, `Platform Document`, generated from the
  folders `PLATFORM_AREAS` declares in **both** repositories. It brings the 55 documents under
  `docs/architecture`, `docs/governance`, `docs/howto` and `docs/superpowers` into the catalog for the first
  time, alongside the operating documents that stay in `durion-positivity-backend/docs`; `path:` names the
  checkout that holds each one, so a reader never has to know which repo maintains it.
* **Moved**: `durion-positivity-backend/docs` triaged from 63 markdown documents to 6. Seventeen survivors
  moved here — platform architecture and contracts into `docs/architecture/`, the RBAC audit and location-scope
  spike into `domains/security/`, the platform sender contract into `domains/positivity/`, three design records
  into `docs/superpowers/specs/`. Thirty-eight executed plans, delivered PRDs, ADR shadows and remediated
  audits were deleted rather than moved; five service-discovery documents were consolidated into
  `docs/architecture/INTERNAL_TRANSPORT_AND_SERVICE_DISCOVERY.md`, and three JPA-migration documents into one
  ledger. Build, deploy and operating mechanics stayed beside the build they describe.
* **Structure**: `domains/security/` and `domains/positivity/` gained `type: Domain Guide` indexes, opting both
  into document indexing; ten further documents in `domains/security/docs/` became catalog-visible as a result.
* **Added**: ADR-0066 — an accepted, shipped decision (`inventory:availability:*` vs `inventory:onhand:*`) that
  had never been migrated out of the backend and whose original number 0057 was already taken here. Twenty
  references across eleven backend files were swept to the new number.
* **Updated**: nineteen platform documents across `docs/architecture`, `docs/architecture/deployment` and
  `docs/governance` were given real OKF frontmatter, replacing entries whose description was a scraped first
  prose line.
* **Structure**: lifecycle corrected on the three competing AWS host architectures — Docker on EC2 is
  `current` (alpha deploys `deployment/alpha/docker-compose.prod.yml` over SSM), Podman on EC2 and Fargate
  are `proposed` and now carry a status banner; `branching-strategy.md` is marked `superseded` because no
  repository has the `develop` branch it requires. All three previously published as `reference` → `stable`.
* **Structure**: `PLATFORM_STATUS` gained `draft specification` — the five DevOps-framework specs state that
  word and were resolving to no status, which reads as current to an agent checking only for deprecation.

## 2026-09-19

* **Updated**: `domains/shopmgmt` — DECISION-SHOPMGMT-019 (booking horizon) and DECISION-SHOPMGMT-020 (work may
  start before the planned window) added to the domain's business rules; the domain entry's stamp follows them.
* **Updated**: `domains/shopmgmt` — DECISION-SHOPMGMT-018 (unknown operating window vs closure) added to the domain's
  business rules; the domain entry's stamp follows it. ADR and module concepts were left at their current stamps.

## 2026-09-18

* **Regenerated**: 65 ADR, 16 domain, and 45 module concepts from `docs/adr/`, `domains/`, and the backend module suite.

## 2026-09-16

* **Regenerated**: 62 ADR, 16 domain, and 45 module concepts from `docs/adr/`, `domains/`, and the backend module suite.
* **Structure**: every concept now carries a workspace-relative `path:` beside the remote `resource:`, and
  cross-entry links are relative so they resolve when the catalog is read as files rather than served. A
  domain's implementing-module list soft-wraps, which the relative links pushed past the line limit.
* **Moved**: pos-mcp-server docs from the backend into `domains/general/mcp-server/`; `general` owns `pos-mcp-server` via `MODULE_DOMAIN`.

## 2026-09-15

* **Regenerated**: 62 ADR, 16 domain, and 45 module concepts from `docs/adr/`, `domains/`, and the backend module suite.

## 2026-08-19

* **Initialization**: Established the OKF knowledge catalog for `durion` and the backend module suite.
