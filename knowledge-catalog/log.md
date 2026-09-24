# Directory Update Log

## 2026-09-24

* **Regenerated**: 66 ADR, 16 domain, 45 module and 85 platform-document concepts from `docs/adr/`, `domains/`, the backend module suite, and the declared platform areas.
* **Curated**: `domains/accounting/SPEC-inventory-adjustment-gl-posting.md` (`type: Specification`, `status: proposed`) — the ruling on
  durion-positivity-backend#2186 and the `InventoryAdjustedV1` fact → GL posting flow; indexed under the accounting domain and linked from the inventory
  domain index. The accounting and inventory cross-domain contracts now name `inventory.scrap.posted`, `inventory.adjustment.posted` and
  `inventory.product-value.changed` in place of the pre-ADR-0044 `inventory.inventoryAdjustment`.
* **Structure**: the accounting domain's inferred **Implemented by** list now names `pos-inventory` in place of `pos-workorder` — the new
  specification names the producer module often enough to enter the top four by mention count. Module ownership is unchanged
  (`pos-inventory` still belongs to the inventory domain).

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
