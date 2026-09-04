---
title: Warranty Backend Contract Guide
domain: warranty
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/warranty/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-warranty/openapi.yaml
openapi_commit: d82be65
last_verified_utc: 2026-07-16T15:49:22Z
last_updated: 2026-07-16
api_reference_generated: domains/warranty/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Warranty Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Warranty domain behavior (module `pos-warranty`, Eureka name `WARRANTY`, package `com.positivity.warranty`).

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and the generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-warranty/openapi.yaml`
- Generated API reference: `domains/warranty/.business-rules/BACKEND_API_REFERENCE.generated.md`
- PRD (domain spec): `durion-positivity-backend/docs/PRD-warranty-claims-module.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- ADR-0044 (event-only domain walls, incl. the scoped warranty sync-client exception): `docs/adr/0044-platform-event-only-domain-walls.adr.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the capability section below.
2. Validate behavior constraints (state machine, settle-customer-first, suggest-don't-dictate) before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in the generated API reference (or `pos-warranty/openapi.yaml` directly).
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open the generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

Gateway routing: external path `/warranty/warranty/...` with `X-API-Version: 1` → `lb://WARRANTY /v1/warranty/...` (the first `/warranty` segment routes to the service and is stripped; the version header is rewritten into the path — same doubled-segment convention as `pos-inventory`). Pre-versioned form: `/warranty/v1/warranty/...`.

## Domain Invariants

- **Settle the customer first, chase the vendor after.** `SETTLED` means the customer is done. Open vendor reimbursements or part returns keep a claim out of `CLOSED` but never out of `SETTLED` — enforced by the state machine.
- **Suggest, don't dictate.** Eligibility and proration are computed from structured policy terms and pre-fill an outcome; a human always makes the final call. An approval that contradicts the computed suggestion requires an override reason and sets `overrodeSuggestion = true`.
- **Small counter-facing state machine.** The claim status set is customer-explainable (`DRAFT`, `SUBMITTED`, `IN_REVIEW`, `INFO_NEEDED`, `APPROVED`, `DENIED`, `SETTLED`, `CLOSED`, `CANCELLED`). Vendor reimbursement and part return (RMA) run their own child lifecycles and never appear in the claim status.
- **Claim code** is the business identifier: `WC-{yyyy}-{seq}` (e.g. `WC-2026-000123`), zero-padded 6-digit sequence resetting yearly. UUIDv7 remains the primary key; `claimCode` is unique and is the search key customers and vendors use.
- **Denormalized snapshots at claim time.** VIN, odometer, prices, and descriptions are frozen onto the claim/lines at intake so a claim stays self-explanatory even if upstream data changes. No cross-service foreign keys.
- **Full audit trail.** Every status change, decision, override, and proration input is recorded (`ClaimStatusHistory`, `prorationInputs` JSONB per line).
- **Warranty state leaves the module only as `warranty.*` domain events** on topic `warranty.events.v1` (including a full `warranty.claim.snapshot` aggregate for replica builders). No module calls into pos-warranty synchronously.
- Illegal state transitions return the standard `ApiError` envelope with `nextAction` listing the legal moves.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| Warranty Claims Module (no `cap:` id — tracked against PRD) | [durion-positivity-backend#786](https://github.com/louisburroughs/durion-positivity-backend/issues/786) | draft | Providers, policies, registrations, claims, settlements, reimbursements, part returns |

## Frontend API Lookup

Providers, policies, registrations (admin/setup):

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| List providers | `listProviders` | GET | `/v1/warranty/providers` | Filters: `status`, `providerType` |
| Create provider | `createProvider` | POST | `/v1/warranty/providers` | |
| Get provider | `getProvider` | GET | `/v1/warranty/providers/{id}` | |
| Update provider | `updateProvider` | PUT | `/v1/warranty/providers/{id}` | |
| List policies | `listPolicies` | GET | `/v1/warranty/policies` | Filters: `providerId`, `coverageType` |
| Create policy | `createPolicy` | POST | `/v1/warranty/policies` | |
| Get policy | `getPolicy` | GET | `/v1/warranty/policies/{id}` | |
| Update policy | `updatePolicy` | PUT | `/v1/warranty/policies/{id}` | |
| Find policies covering a product | `findApplicablePolicies` | GET | `/v1/warranty/policies/applicable` | `productEntityId`, `manufacturerId`, `categoryId`, `saleDate`, `coverageType`; policy must be in effect on the original sale date |
| Search registrations | `searchRegistrations` | GET | `/v1/warranty/registrations` | Filters: `customerId`, `vehicleId`, `status` |
| Create registration (sold coverage) | `createRegistration` | POST | `/v1/warranty/registrations` | Road-hazard/extended plans; manufacturer warranties are implicit and need no registration |
| Get registration | `getRegistration` | GET | `/v1/warranty/registrations/{id}` | |
| Update registration | `updateRegistration` | PUT | `/v1/warranty/registrations/{id}` | |

Claim intake and adjudication (counter flow):

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Create draft claim | `createClaim` | POST | `/v1/warranty/claims` | Snapshots VIN/odometer from pos-vehicle-inventory |
| Search claims | `searchClaims` | GET | `/v1/warranty/claims` | `customerId`, `vehicleId`, `status`, `claimCode`, `locationId`; pageable |
| Get claim | `getClaim` | GET | `/v1/warranty/claims/{id}` | |
| Edit claim | `updateClaim` | PUT | `/v1/warranty/claims/{id}` | Only while `DRAFT`/`INFO_NEEDED` |
| Find origin sale lines | `searchCandidateLines` | GET | `/v1/warranty/claims/candidate-lines` | `customerId`, `vehicleId`, `sku`, `productEntityId`; cross-service search of invoices + workorders; walk-in fallback = manual line, claim flagged `originUnverified` |
| Add claim line | `addLine` | POST | `/v1/warranty/claims/{id}/lines` | Provenance: `sourceType` `INVOICE_LINE` \| `WORKORDER_PART` \| `WORKORDER_SERVICE` \| `MANUAL` |
| Remove claim line | `removeLine` | DELETE | `/v1/warranty/claims/{id}/lines/{lineId}` | |
| Add photo evidence | `addPhoto` | POST | `/v1/warranty/claims/{id}/photos` | URL-reference pattern; policy may require ≥1 photo before submit |
| Remove photo | `removePhoto` | DELETE | `/v1/warranty/claims/{id}/photos` | Query param `url` |
| Add staff note | `addNote` | POST | `/v1/warranty/claims/{id}/notes` | |
| Submit claim | `submitClaim` | POST | `/v1/warranty/claims/{id}/submit` | Intake complete; runs eligibility automatically |
| (Re)compute eligibility | `evaluateEligibility` | POST | `/v1/warranty/claims/{id}/eligibility` | On-demand suggestion refresh |
| Approve / deny / request info | `decideClaim` | POST | `/v1/warranty/claims/{id}/decision` | Deny requires reason; contradicting the suggestion requires override reason |
| Execute settlement | `createSettlement` | POST | `/v1/warranty/claims/{id}/settlements` | First successful settlement moves the claim to `SETTLED` |
| Cancel claim | `cancelClaim` | POST | `/v1/warranty/claims/{id}/cancel` | Only from `DRAFT`, `SUBMITTED`, `INFO_NEEDED` |
| Close claim | `closeClaim` | POST | `/v1/warranty/claims/{id}/close` | Requires all reimbursements and part returns terminal |

Back office (reimbursement, RMA):

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Submit vendor reimbursement | `submitReimbursement` | POST | `/v1/warranty/claims/{id}/reimbursement/submit` | Records `vendorClaimReference` |
| Update reimbursement status | `updateReimbursement` | PUT | `/v1/warranty/claims/{id}/reimbursement` | Approved / partially approved / denied / credit received / written off |
| Reimbursement worklist | `listReimbursements` | GET | `/v1/warranty/reimbursements` | Filters: `status`, `providerId` (open credits) |
| Create part return (RMA) | `createPartReturn` | POST | `/v1/warranty/claims/{id}/part-returns` | Driven by policy `requiresPartReturn` or staff choice |
| Update part return | `updatePartReturn` | PUT | `/v1/warranty/part-returns/{id}` | RMA number, carrier, tracking, disposition, status |
| Part-return worklist | `listPartReturns` | GET | `/v1/warranty/part-returns` | Filter: `status` (hold shelf / shipping) |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` (bearer) plus the endpoint permission from the matrix below.
- Gateway callers send `X-API-Version: 1` on the external `/warranty/warranty/...` path.

## Permission Matrix

17 permissions, registered at startup via `permissions.yaml` (`PermissionRegistrationSupport`, mirroring `pos-documents`), enforced per endpoint with `@PreAuthorize`:

| Operation(s) | Required Permission |
| --- | --- |
| `listProviders`, `getProvider` | `warranty:provider:view` |
| `createProvider`, `updateProvider` | `warranty:provider:manage` |
| `listPolicies`, `getPolicy`, `findApplicablePolicies` | `warranty:policy:view` |
| `createPolicy`, `updatePolicy` | `warranty:policy:manage` |
| `searchRegistrations`, `getRegistration` | `warranty:registration:view` |
| `createRegistration`, `updateRegistration` | `warranty:registration:manage` |
| `searchClaims`, `getClaim`, `searchCandidateLines`, `evaluateEligibility` | `warranty:claim:view` |
| `createClaim`, `updateClaim`, `addLine`, `removeLine`, `addPhoto`, `removePhoto`, `addNote` | `warranty:claim:create` |
| `submitClaim` | `warranty:claim:submit` |
| `decideClaim` | `warranty:claim:decide` |
| `createSettlement` | `warranty:claim:settle` |
| `cancelClaim` | `warranty:claim:cancel` |
| `closeClaim` | `warranty:claim:close` |
| `listReimbursements` | `warranty:reimbursement:view` |
| `submitReimbursement`, `updateReimbursement` | `warranty:reimbursement:manage` |
| `listPartReturns` | `warranty:part-return:view` |
| `createPartReturn`, `updatePartReturn` | `warranty:part-return:manage` |

Suggested role mapping (PRD §9.1): counter/service-advisor = `view/create/submit`; manager = `decide/settle/cancel`; back office = `reimbursement:*`, `part-return:*`, `close`; admin = `provider/policy/registration:manage`.

## Capability Sections

## Warranty Claims Module (PRD-tracked)

### Capability Metadata

- Capability ID: none — work tracked against the PRD, not a `cap:` id
- Capability Status: draft
- Domain: warranty
- Parent Issue: [durion-positivity-backend#786](https://github.com/louisburroughs/durion-positivity-backend/issues/786)
- Backend Issues: [durion-positivity-backend#786](https://github.com/louisburroughs/durion-positivity-backend/issues/786); implementation PR [durion-positivity-backend#920](https://github.com/louisburroughs/durion-positivity-backend/pull/920)
- Related durion issues: [#350](https://github.com/louisburroughs/durion/issues/350) (ADR-0044 amendment), [#351](https://github.com/louisburroughs/durion/issues/351) (this guide)
- Module: `pos-warranty`
- OpenAPI Source: `durion-positivity-backend/pos-warranty/openapi.yaml`
- Last Verified OpenAPI Commit: `d82be65`

### Scope & Intent

Full warranty-claim domain for a tire dealer: vendor warranty policies (structured terms), claim intake and adjudication, customer settlement, vendor reimbursement tracking, and defective-part return (RMA). Warranty covers all parts and services — tires, parts, and dealer-performed labor. Claim types: `MANUFACTURER_DEFECT`, `DEALER_WORKMANSHIP`, `ROAD_HAZARD`, `EXTENDED_PLAN`. Settlement paths: `REPLACEMENT_WORKORDER`, `INVOICE_CREDIT`, `REFUND`, `PRORATED_CREDIT`, plus `GOODWILL` and `NO_ACTION`.

### API Operation References (OpenAPI Source of Truth)

See `Frontend API Lookup` above — the tables there are the complete v1 surface (35 operations). Do not copy schemas from this guide; read them from the OpenAPI source.

### Behavioral Assertions

Claim state machine (`ClaimStateMachineTest`):

- Legal transitions only: `DRAFT → SUBMITTED → IN_REVIEW → APPROVED | DENIED | INFO_NEEDED`; `INFO_NEEDED → IN_REVIEW`; `APPROVED → SETTLED → CLOSED`; `DENIED → IN_REVIEW` (appeal, reason required) or `DENIED → CLOSED`; `DRAFT | SUBMITTED | INFO_NEEDED → CANCELLED`. Terminal: `CLOSED`, `CANCELLED`.
- Illegal transitions return `409` with the `ApiError` envelope; `nextAction` lists the legal moves.
- `APPROVED` requires a decision actor; if the decision contradicts the computed suggestion, an override reason is mandatory and `overrodeSuggestion = true` is recorded.
- `CLOSED` requires every `VendorReimbursement` in a terminal state (`CREDIT_RECEIVED`, `WRITTEN_OFF`, `NOT_APPLICABLE`, `DENIED`) and every `PartReturn` terminal.
- Claim edits (`updateClaim`, lines, photos) are only allowed in `DRAFT`/`INFO_NEEDED`.

Claim code:

- `WC-{yyyy}-{seq}`: fixed `WC` prefix, claim-creation year, zero-padded 6-digit yearly-resetting sequence allocated at claim creation (Postgres sequence per year; concurrency covered by `ClaimCodeConcurrencyTest`).

Eligibility and proration (`EligibilityServiceImplTest`, `ProrationServiceTest`):

- Eligibility runs automatically at submit and on demand via `evaluateEligibility`. Candidate policies are matched via `appliesTo` (product → brand/manufacturer → category → ALL, most specific wins) where the policy was in effect on `originSaleDate`.
- Each structured term produces a per-term pass/fail entry in `eligibilityReasons`; result is `ELIGIBLE` / `INELIGIBLE` / `INDETERMINATE` (missing facts) plus a `suggestedOutcome` with computed amounts.
- Proration methods per policy: `NONE` (100%), `TREAD_DEPTH` (`(measuredDepth − pullPoint) / (originalDepth − pullPoint)`), `MILEAGE` (`(mileageLimit − milesDriven) / mileageLimit`), `TIME` (`(durationMonths − monthsElapsed) / durationMonths`). Credit = fraction × `originalUnitPrice` × quantity, clamped to [0, original price]. Tread depth is stored as integer 32nds of an inch; money as `BigDecimal(19,4)`.
- All proration inputs are frozen into `prorationInputs` on the claim line for audit; staff overrides of computed amounts are audited with reason.

Settlements (`SettlementServiceImplTest`, `SettlementOutboxTest`, `SettlementFailedPathPersistenceTest`):

- `createSettlement` executes one settlement on an `APPROVED` (or already `SETTLED`) claim: creates an invoice adjustment or refund via pos-invoice for credit/refund types, links a validated replacement workorder, or records `NO_ACTION`. The first successful settlement moves the claim to `SETTLED`.
- A claim can carry multiple settlements (e.g. prorated credit **and** replacement workorder). `coveredAmount`/`customerAmount` split what coverage pays from what the customer still owes.
- Replacement workorders are created through the normal workorder flow and only **linked** (`replacementWorkorderId`); `pos-workorder` is never written to and stays unaware of warranty.
- Invoice adjustments and refunds created by warranty carry `externalReference = claimCode` (max 64 chars) — this is the idempotency/correlation key pos-invoice dedupes on and the cross-domain reconciliation key.
- A failed pos-invoice write returns `502` and records the settlement as `FAILED` (status set `PENDING`/`COMPLETED`/`FAILED`).

Reimbursement (`ReimbursementServiceImplTest`):

- One `VendorReimbursement` per claim per provider (normally one). Lifecycle: `NOT_SUBMITTED → SUBMITTED → APPROVED | PARTIALLY_APPROVED | DENIED`, then `CREDIT_RECEIVED` or `WRITTEN_OFF`; `DEALER`-type providers skip reimbursement (`NOT_APPLICABLE`).
- Reimbursement never blocks the customer settlement (settle-customer-first) and never writes to `pos-accounting` — accounting consumes events.

Part return / RMA (`PartReturnServiceImplTest`):

- Created when the governing policy has `requiresPartReturn` or staff choose to return the part. Status: `AWAITING_PART → ON_HOLD → SHIPPED → RECEIVED_BY_VENDOR | SCRAPPED | CLOSED`; disposition: `HOLD_FOR_INSPECTION`, `RETURN_TO_VENDOR`, `SCRAP_AUTHORIZED`, `CUSTOMER_RETAINED`.
- v1 keeps the physical hold shelf manual; the `PartReturn` record is the system of record.

Intake snapshots:

- VIN, `odometerAtClaim`, and `odometerUnit` are read from pos-vehicle-inventory at intake and frozen onto the claim; claim lines snapshot `sku`, `description`, `originalUnitPrice` (and `dotNumber`/tread depths for tires).
- Walk-in claims with no locatable sale are supported: manual origin entry with `originUnverified = true`.

### Status Code Semantics (ADR-0017)

| Scenario | HTTP Status |
| --- | --- |
| Reads, searches, worklists | 200 |
| Create draft claim / provider / policy / registration / part return / note / settlement | 201 |
| Lifecycle actions (submit, decision, cancel, close, eligibility, line/photo mutations, reimbursement, RMA update) | 200 |
| Validation failure | 400 |
| Missing permission | 403 |
| Entity not found | 404 |
| Illegal state transition / not editable / not settleable | 409 (`ApiError` with `nextAction`) |
| Submit attempted with no claim line (`WARRANTY_CLAIM_MISSING_LINES`) | 422 — well-formed request, but PRD §6 requires at least one claim line before a claim can leave intake |
| Submit attempted without required photo evidence (`WARRANTY_CLAIM_PHOTO_EVIDENCE_REQUIRED`) | 422 — well-formed request, but the winning policy's `requiresPhotoEvidence` flag (PRD §3.2) is unmet |
| Replacement workorder could not be resolved (settlement) | 422 |
| pos-invoice write failed during settlement (recorded as `FAILED`) | 502 |

### Audit and Security Rules

- Thin controllers; `@PreAuthorize` per endpoint (see Permission Matrix); permission bits synced into `pos-api-gateway` `DownstreamPermissionCatalog`.
- Decision/actor identity derives from the authenticated security context (ADR-0018) — callers do not pass actor identity in request bodies.
- `@EmitEvent` on all state-changing endpoints. Write audit event ids: `WARRANTY_PROVIDER_CREATE/UPDATE`, `WARRANTY_POLICY_CREATE/UPDATE`, `WARRANTY_REGISTRATION_CREATE/UPDATE`, `WARRANTY_CLAIM_CREATE/UPDATE/SUBMIT/DECIDE/SETTLE/CANCEL/CLOSE`, `WARRANTY_REIMBURSEMENT_SUBMIT/UPDATE`, `WARRANTY_PART_RETURN_CREATE/UPDATE`. Search audit ids: `WARRANTY_CLAIM_SEARCH`, `WARRANTY_CANDIDATE_LINE_SEARCH`. (The `@EmitEvent` → pos-event-receiver pipeline is audit-only per ADR-0044 R5 — it is not the integration channel.)

### ADR Constraints

- ADR-0044 — Event-only domain walls, **amended 2026-07-16 with a scoped pos-warranty v1 exception**: synchronous `@LoadBalanced RestClient` calls from `com.positivity.warranty.internal.client` to exactly `pos-invoice`, `pos-workorder`, `pos-catalog`, `pos-customer`, and `pos-vehicle-inventory` are permitted (PRD §9.4, approved 2026-07-15). One-directional: no module calls into pos-warranty synchronously. Enforced by `DomainWallsTest` (pos-archunit) as a per-consumer exception map; widening it requires a further ADR amendment. Migration to event-fed read-only replicas (R3) is mandatory for any warranty v2.
- ADR-0044 §3/§4 — Integration events use the `DomainEventEnvelope` on `warranty.events.v1`, transactional outbox (`event_outbox` drained by `OutboxPublisher`), keyed by `aggregateId`; additive-only payload changes within v1.
- ADR-0013 / ADR-0027 — UUIDv7 primary keys; UUID-typed identifiers in API and event payloads.
- ADR-0017 — Status code semantics above; state-machine violations return `ApiError` with `nextAction`.
- ADR-0018 — Actor identity from security context only.
- ADR-0042 — OpenAPI annotations on all controllers; `pos-warranty/openapi.yaml` is the committed machine-readable contract.

### Events & Dependencies

Integration events — topic `warranty.events.v1`, payload DTOs in `pos-domain-events` (`com.positivity.domainevents.warranty`), standard `DomainEventEnvelope`, transactional outbox:

| Event | Payload class | Consumer | Purpose |
| --- | --- | --- | --- |
| `warranty.claim.settled` | `WarrantyClaimSettledV1` | accounting, reporting | Customer made whole; emitted once per executed settlement; carries settlement type + amounts |
| `warranty.reimbursement.submitted` | `WarrantyReimbursementSubmittedV1` | `pos-accounting` | Expected vendor credit (`apVendorId`, amount, `vendorClaimReference`) |
| `warranty.reimbursement.resolved` | `WarrantyReimbursementResolvedV1` | `pos-accounting` | Approved / partially approved / denied / credit received — matching against vendor activity |
| `warranty.part-return.requested` | `WarrantyPartReturnRequestedV1` | `pos-inventory` (follow-up) | Defective-unit quarantine/hold |
| `warranty.part-return.shipped` | `WarrantyPartReturnShippedV1` | `pos-inventory` (follow-up) | Part shipped to vendor |
| `warranty.claim.snapshot` | `WarrantyClaimSnapshotV1` | replica builders (ADR-0044 R3) | Full claim aggregate, emitted after every claim-visible mutation |

Consumes: nothing in v1 (no inbound event subscriptions).

Synchronous dependencies (scoped ADR-0044 exception; clients in `com.positivity.warranty.internal.client`):

| Callee | Client | When | Contract |
| --- | --- | --- | --- |
| `pos-vehicle-inventory` | `VehicleInventoryClient` | Intake | Read vehicle record (VIN, odometer) → snapshot onto claim |
| `pos-invoice` | `InvoiceClient` | Origin matching; settlement | Search invoices/lines by customer/vehicle/SKU; create `InvoiceAdjustment` (type `WARRANTY`); initiate refund (`RefundRecord`) — both carry `externalReference = claimCode` |
| `pos-workorder` | `WorkorderClient` | Origin matching; settlement validation | Search workorder part/service lines; resolve replacement workorder. Never written to |
| `pos-catalog` | `CatalogClient` | Intake, policy `appliesTo` | Product/manufacturer lookup; original tread depth where cataloged |
| `pos-customer` | `CustomerClient` | Intake | Customer typeahead/validation |

Cross-domain identity touchpoints: `WarrantyProvider.apVendorId` → `pos-accounting` `ap_vendor.vendorId` (lets accounting match vendor credits to claims); `WarrantyProvider.manufacturerId` → `pos-catalog` `ProductEntity.manufacturerId` (policy matching by product manufacturer).

### Contract Test Traceability

Provider tests: `durion-positivity-backend/pos-warranty/src/test/java/com/positivity/warranty/`

- State machine: `internal/domain/ClaimStateMachineTest`
- Claim lifecycle + intake: `internal/service/ClaimServiceImplTest`, `internal/service/ClaimCodeConcurrencyTest`
- Eligibility/proration: `internal/service/EligibilityServiceImplTest`, `internal/service/ProrationServiceTest`, `PolicyFindApplicableJpaTest`
- Settlements: `internal/service/SettlementServiceImplTest`, `internal/service/SettlementOutboxTest`, `internal/service/SettlementFailedPathPersistenceTest`
- Reimbursement / RMA: `internal/service/ReimbursementServiceImplTest`, `internal/service/PartReturnServiceImplTest`
- Candidate lines: `internal/service/CandidateLineServiceImplTest`
- Events/outbox: `internal/service/ClaimSnapshotPublisherTest`, `internal/config/OutboxPublisherTest`, `internal/repository/OutboxEventRepositoryTest`
- Controllers/permissions/errors: `internal/controller/WarrantyControllersWebMvcTest`, `internal/exception/WarrantyExceptionHandlerTest`
- Sync clients: `internal/client/InvoiceClientTest`, `internal/client/WorkorderClientTest`, `internal/client/VehicleInventoryClientImplTest`
- Architecture: `ArchitectureTest` (module) + `DomainWallsTest` in `pos-archunit` (ADR-0044 exception map)

### Open Questions / Non-Goals

Open questions (defaulted, not blocking — PRD §12):

- Tax on credits/refunds is delegated to `pos-invoice`/`pos-tax` at settlement time (adjustment carries pre-tax amount).
- Appeals are modeled as `DENIED → IN_REVIEW` with mandatory reason; no separate appeal entity.
- Goodwill outside policy is allowed: claim with no policy, `settlementType = GOODWILL`, always a human decision, dealer-funded.
- `WarrantyRegistration` auto-creation on road-hazard/extended-plan sale is manual/API in v1; order/invoice event consumption later.
- Photo storage is URL strings; no managed attachment service exists in the platform.
- Multi-location visibility: claims carry `locationId`; scoping deferred to standard RBAC.

Non-goals (v1):

- Direct EDI/portal integration with manufacturer claim systems (manual submission; module records the vendor claim reference).
- Customer self-service portal.
- Automated GL posting (accounting consumes events; warranty writes nothing to `pos-accounting`).
- National-account / fleet billing programs.
- Warranty *sales* (plans are sold as normal order/invoice line items; this module records resulting coverage as a `WarrantyRegistration`).

---

## Events & Cross-Domain Dependencies

- Warranty state leaves the module only as `warranty.*` events on `warranty.events.v1`; the sync clients above are a scoped, one-directional ADR-0044 exception for v1 reads and settlement execution.
- Invoice adjustments/refunds created by warranty are correlated by `externalReference = claimCode`.
- Any contract-affecting change must update `pos-warranty/openapi.yaml` and this guide, and — if event payloads change — the `pos-domain-events` warranty DTOs (additive-only within v1).

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-warranty/openapi.yaml`
- OpenAPI source revision: `d82be65` (merge of durion-positivity-backend#920)
- Last verified UTC: `2026-07-16T15:49:22Z`
- Generated API reference: `domains/warranty/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `durion-positivity-backend/docs/PRD-warranty-claims-module.md`
- `docs/adr/0044-platform-event-only-domain-walls.adr.md` (§Amendments — pos-warranty v1 exception)
- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- [durion-positivity-backend#786](https://github.com/louisburroughs/durion-positivity-backend/issues/786) · [durion-positivity-backend#920](https://github.com/louisburroughs/durion-positivity-backend/pull/920) · [durion#350](https://github.com/louisburroughs/durion/issues/350)
