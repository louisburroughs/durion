# CAP-168 Backend Contract: Location Store Pricing (Overrides by Location)

**Capability:** CAP:168
**Domain:** product
**Source manifest:** docs/capabilities/CAP-168/CAPABILITY_MANIFEST.yaml
**Authoritative API spec:** durion-positivity-backend/pos-catalog/openapi.json

## Summary
This document is a derived, developer-facing contract summary for CAP-168. It enumerates the OpenAPI-authoritative endpoints, gateway examples, behavioral assertions (where explicit in OpenAPI), and implementation links.

## OpenAPI-derived endpoints (gateway format)
- `POST http://localhost:8080/v1/products/pricing/location-overrides` — create location price override (`201`, `400`, `403`)
- `POST http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/approve` — approve override (`200`, `404`, `409`, `400`)
- `POST http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/reject` — reject override (`200`, `404`, `409`, `400`)
- `GET  http://localhost:8080/v1/products/pricing/effective-price/{locationId}/{productId}` — resolve effective price (`200`, `404`)
- `POST http://localhost:8080/v1/products/pricing/guardrail-policies` — upsert guardrail policy (`200`, `400`)

## Implementation Links
- Backend issue: https://github.com/louisburroughs/durion-positivity-backend/issues/52
- Capability manifest: docs/capabilities/CAP-168/CAPABILITY_MANIFEST.yaml

## Handoff payload (for story fulfillment prompt)
```yaml
capability_label: "CAP:168"
capability_id: "CAP:168"
domain: "product"
parent_capability_number: 168
parent_capability_url: "https://github.com/louisburroughs/durion/issues/168"
parent_capability_title: "[CAP] Location Store Pricing (Overrides by Location)"
parent_stories_list: []
backend_child_issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/52
```

## Provider test hints
- For `createLocationPriceOverride`, test guardrail validation responses (`400`) for min margin and max discount violations.
- For `approve/reject` endpoints, include optimistic-lock version mismatch tests to assert `409` handling.
- For `effective-price`, test precedence: ACTIVE override wins, otherwise base price returned.

## Notes
- This document was generated from OpenAPI at `durion-positivity-backend/pos-catalog/openapi.json` and is authoritative with respect to endpoint shape and response schemas.
