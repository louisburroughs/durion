# Directory Update Log

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
