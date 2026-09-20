# Directory Update Log

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
