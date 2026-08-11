# AGENT_GUIDE.md

## Summary

This guide defines the Product domain's normative backend rules for the Durion platform. In implementation, this domain maps to `durion-positivity-backend/pos-catalog`.

The Product domain owns product/service/non-inventory catalog entities, catalog grouping, and product detail composition for pricing/availability signals. It does not own inventory quantity truth, pricing engine policy truth, or scheduling/work execution workflows.

## ADR References

- [ADR-0006: WorkExec Domain Ownership Boundaries](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md)
- [ADR-0009: Backend Domain Responsibilities Guide](../../../docs/adr/0009-backend-domain-responsibilities-guide.adr.md)
- [ADR-0011: API Gateway Security Architecture](../../../docs/adr/0011-api-gateway-security-architecture.adr.md)
- [ADR-0013: Platform UUID Identifier Strategy](../../../docs/adr/0013-platform-uuid-identifier-strategy.adr.md)
- [ADR-0014: Gateway Internal Service Security](../../../docs/adr/0014-gateway-internal-service-security.adr.md)
- [ADR-0017: API Controller HTTP Response Codes](../../../docs/adr/0017-api-controller-http-response-codes.adr.md)

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to this guide
- [x] Reconciled domain rules with implemented `pos-catalog` OpenAPI

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-PRODUCT-001 | Product domain maps to `pos-catalog` |
| DECISION-PRODUCT-002 | API base path and gateway routing |
| DECISION-PRODUCT-003 | Catalog item type model (`product`, `service`, `noninventory`) |
| DECISION-PRODUCT-004 | Product detail endpoint is a composed read model |
| DECISION-PRODUCT-005 | Authorization is role-based in current implementation |
| DECISION-PRODUCT-006 | Event emission is required for create/update mutations |
| DECISION-PRODUCT-007 | Unsupported type handling returns 400 |
| DECISION-PRODUCT-008 | Substitutes endpoint is read-only and currently not implemented |
| DECISION-PRODUCT-009 | IDs are opaque UUID values to clients |
| DECISION-PRODUCT-010 | Standard error envelope required for future hardening |
| DECISION-PRODUCT-011 | Catalog pricing surface is list/MSRP reference only (ADR-0054) |

## Domain Boundaries

### What Product owns (System of Record)

- Product entities and their descriptive attributes
- Service entities and non-inventory product entities
- Catalog entities and catalog membership lists
- Read model endpoint for product detail (`/v1/products/product/{productId}`)

### What Product does not own

- Inventory on-hand/availability source-of-truth (Inventory domain)
- Workorder execution and substitution application workflows (WorkExec domain)
- Security policy definition and role management (Security domain)

## Key entities

| Entity | Description |
| --- | --- |
| `ProductEntity` | Product master record |
| `ServiceEntity` | Service catalog item |
| `NonInventoryProductEntity` | Non-inventory catalog item |
| `CatalogEntity` | Grouping of products/services/non-inventory items |
| `ProductDetailView` | Composed response including pricing/availability signals |

## Invariants / Business Rules

- All API routes are under `/v1/products`.
- `type` path parameter for generic item mutations must be one of: `product`, `service`, `noninventory`.
- Mutation authorization requires catalog edit/delete roles (`ROLE_CATALOG_EDIT`, `ROLE_CATALOG_DELETE`) or admin.
- Read authorization requires `ROLE_CATALOG_VIEW` or admin.
- Event emission annotations are mandatory on create/update mutations:
  - `CATALOG_ITEM_CREATE`
  - `CATALOG_ITEM_UPDATE`
  - `CATALOG_CATALOG_CREATE`
  - `CATALOG_CATALOG_UPDATE`
- Substitutes endpoint (`GET /v1/products/substitutes/{productId}`) returns `501 Not Implemented` until capability lands.

## Decision mapping

| Decision ID | Rule | Primary ADR |
| --- | --- | --- |
| DECISION-PRODUCT-001 | Product domain implementation is `pos-catalog` | [ADR-0009](../../../docs/adr/0009-backend-domain-responsibilities-guide.adr.md) |
| DECISION-PRODUCT-002 | Primary contract path is `/v1/products/*` | [ADR-0011](../../../docs/adr/0011-api-gateway-security-architecture.adr.md) |
| DECISION-PRODUCT-003 | Generic item mutation requires strict type switch | [ADR-0009](../../../docs/adr/0009-backend-domain-responsibilities-guide.adr.md) |
| DECISION-PRODUCT-004 | Product detail view is composite and may degrade gracefully | [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md) |
| DECISION-PRODUCT-005 | Controller guards are role-based `@PreAuthorize(hasRole(...))` | [ADR-0014](../../../docs/adr/0014-gateway-internal-service-security.adr.md) |
| DECISION-PRODUCT-006 | Mutations emit `@EmitEvent` for audit/observability | [ADR-0009](../../../docs/adr/0009-backend-domain-responsibilities-guide.adr.md) |
| DECISION-PRODUCT-007 | Unsupported types return `400 Bad Request` | [ADR-0017](../../../docs/adr/0017-api-controller-http-response-codes.adr.md) |
| DECISION-PRODUCT-008 | Substitutes endpoint exists but is intentionally 501 | [ADR-0017](../../../docs/adr/0017-api-controller-http-response-codes.adr.md) |
| DECISION-PRODUCT-009 | Clients must treat IDs as opaque | [ADR-0013](../../../docs/adr/0013-platform-uuid-identifier-strategy.adr.md) |
| DECISION-PRODUCT-010 | Standardized error envelope is required as contract hardening follow-up | [ADR-0017](../../../docs/adr/0017-api-controller-http-response-codes.adr.md) |
| DECISION-PRODUCT-011 | `pos-catalog` prices are reference-only; transactional quoting is owned by `pos-price` (customer pays → `pos-price`; list/MSRP → `pos-catalog`) | [ADR-0054](../../../docs/adr/0054-sell-price-system-of-record-split.adr.md) |

## Open Questions

### Q: Should Product authorization migrate from role-based guards to permission-based guards?

- Current state: role-based guards in controllers.
- Migration path: permission registration is already present in module startup.
- Recommendation: move to fine-grained permissions once security gateway/claims contract is finalized.

### Q: Should substitutes remain in Product domain?

- Current state: substitutes endpoint exists in Product API but returns `501 Not Implemented`.
- Recommendation: keep Product-side read endpoint if it is catalog lookup only; execute/apply flows should remain in WorkExec/Inventory workflows.
