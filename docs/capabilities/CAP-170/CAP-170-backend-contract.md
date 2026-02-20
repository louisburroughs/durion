---
title: CAP-170 Backend Contract Summary
capability: CAP:170
domain: inventory
created: 2026-02-19
status: draft
---

# CAP-170 — Availability & Inventory Visibility (Backend Contract Summary)

Summary of backend contract updates made to support CAP-170. This document summarizes changes to the domain contract guide and the expected API behavior for implementers and reviewers.

- **Files updated/created:**
  - Updated: `domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md` (added CAP-170 Availability section)
  - Created: `docs/capabilities/CAP-170/CAP-170-backend-contract.md` (this file)

- **Gateway endpoints (v1 inventory):**
  - `GET http://localhost:8080/v1/inventory/availability/{productId}` — query per-location availability
  - `POST http://localhost:8080/v1/inventory/availability/{productId}` — update availability (implementation details tracked in backend issues)

- **Schema / response shape:**
  - `GET` returns a JSON array of per-location availability objects. Each object contains `productId`, `locationId`, `locationName` (optional), `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, `uom`, `asOfTimestamp`, and optional `expectedReceiptsQuantity`.
  - The OpenAPI artifact in `pos-inventory/openapi.json` contains `InventoryAvailabilityResponse` schema; the contract requires that the service emit a multi-location array using the contract field names above (mapping to OpenAPI property names is acceptable during transition).

- **ATP (Available-To-Promise) formula:**
  - `ATP = On-Hand − Active Reservations`
  - Active reservation statuses included: `RESERVED`, `ALLOCATED`, `PICK_ASSIGNED`, `ISSUE_PENDING`
  - Excluded statuses: `CANCELLED`, `RELEASED`, `EXPIRED`, `FULFILLED`

- **Behavioral assertions (track in Issue #48):**
  - `200 OK` with `[]` when product exists but has no stock across locations
  - `404 Not Found` when `productId` does not exist
  - `400 Bad Request` when `productId` is malformed or missing
  - ATP must equal `onHandQuantity - sum(active reservations)` per-location
  - Response must be a per-location array even for single-location results

- **Related pipeline/work items (no new REST endpoints):**
  - Issue #46: Manufacturer feed ingestion and normalization (NormalizedAvailability, UnmappedManufacturerParts)
  - Issue #47: Distributor feed normalization, DistributorSkuMap, lead-time normalization policy, ship-from region normalization

- **Tests & validation:**
  - Provider contract tests MUST cover the behavioral assertions above and seed normalized availability data from ingestion pipelines.

---

Commit message for changes: `docs(capability): update inventory backend contract guide for CAP-170`
# CAP-170 — Availability & Inventory Visibility (Backend Contract)

**Domain:** product
**Scope:** Backend contract guidance for Availability and Inventory Visibility (product detail + inventory mapping)

## Purpose

Document the product-facing API contract shape for availability information and the inventory-side expectations for CAP-170. This file is a companion to the product `BACKEND_CONTRACT_GUIDE.md` and provides inventory-perspective notes for implementers and contract testers.

## Product gateway endpoints (consumer-facing)

- `GET http://localhost:8080/v1/products/{productId}/detail?location_id={locationId}`
  - Consolidated product detail view containing `availability` information. Implemented by `pos-catalog` and backed by `pos-inventory` live lookups.

- `GET http://localhost:8080/v1/products/manufacturerPartMap/resolve?mpn={mpn}&manufacturerId={manufacturerId}`
  - Resolve external manufacturer part numbers to internal product IDs (used during feed normalization).

## AvailabilityInfo (inventory perspective)

The product API exposes `AvailabilityInfo` inside `ProductDetailView`. Inventory implementers must provide these fields when responding to catalog lookups.

- `onHandQuantity` (integer) — quantity physically on-hand at the requested location
- `availableToPromiseQuantity` (integer) — ATP quantity considering reservations and supply
- `leadTime` (object) — `LeadTimeInfo` containing `minDays`, `maxDays`, `displayText`, `source`, `asOf`, `confidence`
- `status` (string enum) — one of `OK`, `UNAVAILABLE`, `STALE`, `ERROR`
- `asOf` (date-time) — timestamp for the availability measurement
- `confidence` (string enum) — one of `LOW`, `MEDIUM`, `HIGH`

## Status semantics (inventory responsibilities)

- `OK` — Fresh, authoritative inventory data for the location; confidence should be MEDIUM or HIGH.
- `UNAVAILABLE` — Inventory reports zero on-hand/ATP for the location.
- `STALE` — Inventory data may be outdated (e.g., fallback cached values used); indicate when last successful refresh occurred via `asOf` and set `confidence` to LOW.
- `ERROR` — Inventory service could not determine availability (errors, timeouts); return `asOf` if available and `confidence` == LOW.

## Provider test expectations (inventory implementers)

- Ensure the inventory service exposes deterministic data for seeded test locations so catalog contract tests can assert numeric availability values.
- ContractBehaviorIT tests should include:
  - Fresh-data case -> `availability.status` == `OK` and appropriate `onHandQuantity`/`availableToPromiseQuantity`.
  - Zero-stock case -> `availability.status` == `UNAVAILABLE` and `onHandQuantity` == 0.
  - Inventory service error -> `pos-catalog` should return `availability.status` in {`STALE`, `ERROR`} and `confidence` == `LOW` rather than omitting the `availability` object.

## Feed normalization and mapping (ties to product)

- Feed normalization work (manufacturer/distributor) should drive `manufacturerPartMap/resolve` functionality; see backend issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/46
  - https://github.com/louisburroughs/durion-positivity-backend/issues/47
  - https://github.com/louisburroughs/durion-positivity-backend/issues/48

## Testing notes

- Inventory implementers: provide a seeded dataset and test harness that the `pos-catalog` ContractBehaviorIT can use (DB fixtures or migration-based seeds are preferred).
- Avoid in-memory-only approaches for contract tests; use the same DB-backed test strategy used across other `pos-*` modules.

## Traceability

- Capability manifest: `docs/capabilities/CAP-170/CAPABILITY_MANIFEST.yaml`
- Product guide entry: `domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md`

---

_Last updated: 2026-02-19_
