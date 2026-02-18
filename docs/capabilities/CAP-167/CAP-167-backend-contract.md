# CAP-167 — Backend Contract Summary

**Capability ID:** CAP:167
**Title:** [CAP] MSRP & Base Pricing Policies
**Domain:** product
**Manifest:** /docs/capabilities/CAP-167/CAPABILITY_MANIFEST.yaml

## Backend child issues

- https://github.com/louisburroughs/durion-positivity-backend/issues/52

## OpenAPI changes summary (authoritative)

- added: []
- changed:
  - /v1/products/catalog/name/{name}
  - /v1/products/name/{name}
  - /v1/products/noninventory/name/{name}
- removed: []

> Notes: OpenAPI (pos-catalog/openapi.json) is the source of truth. The items listed under "changed" were previously marked deprecated in the guide but are present in the current OpenAPI and therefore considered active.

## Gateway Path Requirement

All examples and endpoints in this document must use the API Gateway format:

`http://localhost:8080/v{version}/{domain}/{resource}`

Example: `GET http://localhost:8080/v1/products/product/{productId}`

## Relevant endpoints (from OpenAPI)

- `POST http://localhost:8080/v1/products/costs/supplier-item` — Create supplier-item cost tiers. (201, 400, 403)
- `GET http://localhost:8080/v1/products/costs/supplier-item/{supplierId}/{itemId}` — Read supplier-item cost tiers. (200, 404)
- `PUT http://localhost:8080/v1/products/costs/supplier-item/{supplierId}/{itemId}` — Update supplier-item cost tiers. (200, 400, 404, 409)
- `DELETE http://localhost:8080/v1/products/costs/supplier-item/{supplierId}/{itemId}` — Delete supplier-item cost. (204, 404)

## Provider test hints

- For create/update supplier-item cost endpoints, test valid tier structures (contiguous, minQuantity starting at 1) and invalid overlapping tiers.
- Verify 403 is returned when missing the required authority; verify 409 on optimistic-lock version conflicts when updating.

## Outstanding TODOs / Clarifications

- Pricing semantics: confirm whether `PricingInfo.msrp` vs `baseCost` map to CAP-167 requirements; add explicit mapping if business rules require it. See backend child issue #52 for implementation details.
- Idempotency behavior for create endpoints: TODO — reference backend issue #52 to decide whether an `Idempotency-Key` header is required.

## Handoff payloads for stories

- CAPABILITY_MANIFEST_PATH: /docs/capabilities/CAP-167/CAPABILITY_MANIFEST.yaml
- BACKEND_CONTRACT_GUIDE_PATH: /domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md


### Story payloads

```yaml
- capability_label: "CAP:167"
  capability_id: "CAP:167"
  domain: "product"
  parent_capability_number: 167
  parent_capability_url: "https://github.com/louisburroughs/durion/issues/167"
  parent_capability_title: "[CAP] MSRP & Base Pricing Policies"
  parent_stories_list: []
  backend_child_issues:
    - "https://github.com/louisburroughs/durion-positivity-backend/issues/52"
```
