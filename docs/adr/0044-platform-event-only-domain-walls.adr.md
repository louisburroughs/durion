# ADR-0044: Event-Only Domain Walls and Module Communication Policy

**Status:** ACCEPTED — amended 2026-08-10 (pos-supplier stock-inquiry sync-read exception; pos-order → pos-invoice back-port dated 2026-07-23; see §Amendments)
**Date:** 2026-07-08 (accepted 2026-07-08)
**Deciders:** Architecture, Backend Lead
**Affected Issues:** durion-positivity-backend#823, #1002

---

## Context

Backend domain modules are coupled by ~24 synchronous REST clients across 10 modules (full call matrix in
`durion-positivity-backend/docs/module-coupling/issue-823-event-only-domain-walls-assessment.md`). Reads dominate (customer, location, and people reference data is fetched on
nearly every workorder/invoice/shop-manager flow), but several edges are writes: accounting applies payments and credit memos against pos-invoice and triggers invoice
regeneration in pos-workorder; pos-customer performs full vehicle CRUD against pos-vehicle-inventory; pos-security-service writes user↔person links into pos-people and
pos-customer.

This coupling means: a callee outage cascades into caller request failures; deployment ordering matters; and domain models leak across module boundaries through client DTOs.

The platform already contains the seed of the alternative: pos-workorder publishes a versioned JSON event envelope to Kafka (`workorder.events.v1`), pos-customer consumes it,
and pos-accounting / pos-vehicle-inventory have Kafka listeners for topics (`payment.cleared.v1`, `vehicle.updates`, `workorder.completed`) that nothing produces yet.

---

## Decision

Domain modules communicate with each other **only through asynchronous events on Kafka**. Synchronous REST between modules is reserved for a small, named set of **utility
modules**. Consumers hold **read-only local replicas** of the reference data they need, kept in sync by events from the owning module. Cross-module writes become **command
events** with result events and pending states.

### 1. Module classification

**Decision:** ✅ **Resolved**

| Class                        | Modules                                                                                                                                                                                                                                                                                                       | May be called synchronously? |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| **Utility**                  | `pos-api-gateway`, `pos-security-service`, `pos-documents` (per [ADR-0020](0020-documents-centralized-creation.adr.md)), `pos-image`, `pos-tax` (per [ADR-0021](0021-tax-api-consumption-and-internal-access-policy.adr.md)), `pos-event-receiver`, `pos-price`                                               | Yes — by any module          |
| **Domain**                   | `pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inquiry`, `pos-inventory`, `pos-invoice`, `pos-location`, `pos-order`, `pos-people` (HR), `pos-people-contact` (new), `pos-shop-manager`, `pos-vehicle-inventory`, `pos-vehicle-fitment`, `pos-vehicle-reference-*`, `pos-workorder`, `pos-bulk-loader`, `pos-supplier` (new, 2026-08-10) | No — events only             |
| **Libraries / non-deployed** | `pos-events`, `pos-shared-dtos`, `pos-domain-events` (new), `pos-security-common`, `pos-tax-common`, `pos-bulk-ingest-lib`, `pos-document-helper`, `pos-dependencies`, `pos-archunit`                                                                                                                         | n/a                          |

`pos-tax` and `pos-price` are utilities because they are stateless _computation_ (tax and price determination), not data lookups — replicating their rule engines into callers
would be worse than the call. `pos-documents` is a utility because ADR-0020 mandates centralized document creation via its render API. `pos-mcp-server` is a gateway client
(bearer-token relay) and follows client rules, not module rules.

### 2. Rules of separation

**Decision:** ✅ **Resolved** — normative language per RFC 2119.

- **R1 — No domain-to-domain synchronous calls.** A domain module MUST NOT call another domain module's REST API, directly or via the gateway. This includes "just one small
  lookup."
- **R2 — Utility calls allowed.** Any module MAY call a utility module synchronously (direct Eureka discovery or the documented exception mechanism per the service-discovery
  policy). Startup-infra registrations (permissions → pos-security-service per [ADR-0025](0025-permissions-yaml-registration-policy.adr.md), event types → pos-event-receiver,
  document templates → pos-documents) remain synchronous and best-effort.
- **R3 — Reads use local replicas.** When a domain module needs another domain's data, it maintains a read-only local replica populated exclusively by the owner's events.
  Replicas MUST copy the minimum fields required, MUST be tolerant of staleness, and MUST NOT be written by anything except the event consumer. Replica tables are named
  `ext_{owner}_{entity}` (e.g. `ext_location_address`) so ownership is visible in every schema.
- **R4 — Writes use command events.** When a domain module needs another domain to change state, it publishes a command event to the owner's command topic. The owner is the
  sole writer of its data, validates the command, and publishes a result event (applied/rejected). Initiating flows MUST model a pending state and MUST carry an idempotency
  key.
- **R5 — Kafka is the backbone.** Domain and command events flow over Kafka. The `@EmitEvent` → `pos-event-receiver` pipeline remains **audit-only** and MUST NOT be used for
  module-to-module data flow.
- **R6 — One owner per fact.** Every data element has exactly one owning module; only the owner publishes events about it. Consumers never re-publish replica data as their own
  events.

### 3. Event contract standard

**Decision:** ✅ **Resolved**

A new non-deployed library **`pos-domain-events`** holds the envelope and all versioned payload DTOs. It is importable by every module (ArchUnit allowance identical to
`pos-shared-dtos`).

Envelope (extends the existing pos-workorder `KafkaProducer` envelope):

```json
{
  "eventId": "<UUIDv7>",
  "eventType": "customer.party.updated",
  "schemaVersion": 1,
  "aggregateId": "<UUID of the owning aggregate>",
  "aggregateVersion": 42,
  "occurredAtUtc": "2026-07-08T12:00:00Z",
  "sourceService": "pos-customer",
  "correlationId": "<propagated from the initiating request when available>",
  "actor": "<user id or service name, for audit only>",
  "payload": {}
}
```

- Topics: `{domain}.events.v1` (facts) and `{domain}.commands.v1` (requests to the owner). Keyed by `aggregateId` so per-aggregate ordering is preserved.
- Identifiers in payloads are UUID-typed per [ADR-0027](0027-uuid-typed-id-contract-policy.adr.md); `eventId` is UUIDv7 per
  [ADR-0013](0013-platform-uuid-identifier-strategy.adr.md).
- Payload changes within a version MUST be additive-only. Breaking changes require a new topic version (`.v2`), with the owner dual-publishing during the migration window.
- `aggregateVersion` is a monotonic per-aggregate sequence; consumers use it to detect gaps and to ignore out-of-date updates.

### 4. Reliability mechanisms (mandatory before a module migrates)

**Decision:** ✅ **Resolved**

- **Transactional outbox.** Producers MUST NOT publish directly from business transactions. Each producer module adds an `event_outbox` table (Flyway) written in the same
  transaction as the state change, drained by a background publisher. At-least-once delivery is the guarantee.
- **Idempotent consumers.** Each consumer module keeps a `processed_events` table keyed by `eventId` (checked in the same transaction as the replica update). Redelivery MUST
  be harmless.
- **Retry and DLQ.** Transient consumer failures retry with backoff; poison messages go to `{topic}.dlq` and alert. A DLQ'd command MUST surface as a failed/pending item, not
  silently drop.
- **Bootstrap and backfill.** Owners MUST provide a replay mechanism (snapshot export endpoint or administrative re-emit-all) to seed new replicas and repair drift.
- **Reconciliation.** A scheduled job per consumer compares replica `aggregateVersion`s (or count/checksum) against the owner and triggers targeted re-sync on drift.
  Duplication without reconciliation is not permitted. Reconciliation itself flows over the event channel: owners publish periodic reconciliation manifests on
  `{domain}.manifest.v1` and consumers request targeted re-emit via the owner's command topic — reconciliation MUST NOT use synchronous domain-to-domain calls (decided
  2026-07-08, durion-positivity-backend#823/#840).
- **Kafka as tier-1 infrastructure.** Kafka becomes a required runtime dependency for all domain modules in docker/alpha/prod profiles (no more `@ConditionalOnProperty` opt-in
  for domain flows), with consumer-lag and DLQ monitoring in the observability stack.

### 5. Security model for the event channel

**Decision:** ✅ **Resolved**

Events bypass the gateway, so gateway JWT validation and `X-Authorities` ([ADR-0011](0011-api-gateway-security-architecture.adr.md) /
[ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md)) do not apply on this channel. The trust model is:

- The broker is reachable only on the internal network; there are no external producers or consumers. Producer identity is asserted by `sourceService` and, where the
  deployment supports it, broker ACLs restrict which service may produce to which topic.
- Consumers authorize **command events by topic and producer**, not by user authorities. The `actor` field is audit metadata only — it MUST NOT be used to bypass or re-derive
  permission checks. User-permission enforcement happens once, at the edge where the initiating request entered the system (gateway → controller `@PreAuthorize`).
- Replica data inherits the read-permission posture of the consuming module's own endpoints.

### 6. Domain-specific decisions

**Decision:** ✅ **Resolved**

- **Accounting is event-only** (inbound and outbound). Its customer/invoice/workorder clients are retired; billing-rule and invoice read models are event-fed; payment
  application, payment reversal, credit-memo application, and invoice regeneration become command events consumed by pos-invoice / pos-workorder with result events back.
- **Vehicle owns its writes.** Vehicle registry create/update/delete moves out of pos-customer CRM; the frontend calls pos-vehicle-inventory through the gateway. pos-customer
  keeps a read-only vehicle mirror fed by `vehicle.events.v1` to serve its cross-cutting queries. Per [ADR-0012](0012-vehicle-party-relationships-in-customer.adr.md),
  **vehicle-party associations remain owned by pos-customer** and party-association events continue to originate there; only registry ownership of the vehicle record itself is
  affected. pos-vehicle-fitment and the vehicle-reference modules are unchanged (external API lookups).
- **People splits into contact and HR.** New module `pos-people-contact` owns `Person`, `PersonContactPoint`, and the authoritative `user_person_links` store
  ([ADR-0015](0015-identity-entity-relationships.adr.md), [ADR-0043](0043-user-person-linkage-authority.adr.md)) and publishes contact/link events. `pos-people` retains HR
  (Employee, timekeeping per [ADR-0006](0006-workexec-domain-ownership-boundaries.adr.md), availability, staffing, work sessions) and publishes availability and assignment
  events. pos-security-service's user↔person linking becomes command + confirmation events, and its `users.person_id` becomes the event-fed projection that ADR-0043 §2 already
  sanctions as an alternative (see §Changes to other ADRs).
- **Customer and location become publishers.** `customer.events.v1` and `location.events.v1` (including address data — see the note on the javadoc platform rule below) replace
  all remaining customer/location REST clients in inventory, invoice, people, shop-manager, and workorder. Callers of pos-tax source the `destinationAddress` required by
  ADR-0021 from their local location replicas.

### 7. Enforcement

**Decision:** ✅ **Resolved**

- `pos-archunit` gains a cross-module rule: classes in `com.positivity.*.internal.client` MUST NOT target domain services — RestClient base URLs / service-ids are restricted
  to the utility whitelist. Rule ships report-only during migration and flips to build-failing when the final migration phase completes (phasing in the supporting analysis).
- Per-module `ArchitectureTest` classes gain the mirrored rule plus the `pos-domain-events` import allowance. This extends, and does not alter, the intra-module package
  boundary rules of [ADR-0026](0026-service-contract-boundary-policy.adr.md). (ADR-0026 was amended 2026-08-27 — D1–D5: `{domain}.service` is a grant surface, membership by
  grant, ungranted interfaces live in `internal.service`. The extension relationship stated here is unchanged; the sole grant this ADR names, `SupplierStockService`, is
  exactly the type that remains on a grant surface.)
- The utility whitelist lives in one place (a constant list in `pos-archunit`) and changing it requires amending this ADR.

---

## Consequences

**Positive.** Domain modules deploy, fail, and evolve independently; read paths keep working through producer outages; domain models stop leaking through client DTOs; the
event stream becomes a first-class integration surface (audit, analytics, future consumers).

**Negative / accepted.** Reads may lag the owner by seconds — validation against replicas is best-effort and command flows need pending/compensation UX; storage and migration
cost per replica; event contracts become the platform's most rigid API and demand versioning discipline; Kafka becomes tier-1 operational surface (lag, DLQ, partition
management); one frontend contract change (vehicle writes).

**Explicitly rejected alternatives.** Routing domain events through `pos-event-receiver` (single
point of failure, not designed as a broker); keeping sync reads with caching (does not remove the
runtime dependency or the model leak); broad synchronous write exceptions as a general pattern.
Any domain-to-domain synchronous write exception MUST stay narrow, money-movement-specific, and be
approved by ADR amendment.

---

## Amendments

### 2026-07-16 — Scoped exception: pos-warranty v1 synchronous clients

`pos-warranty` (new domain module, durion-positivity-backend#786) is granted a scoped exception to R1 for its v1: synchronous `@LoadBalanced RestClient` calls from
`com.positivity.warranty.internal.client` to `pos-invoice`, `pos-workorder`, `pos-catalog`, `pos-customer`, and `pos-vehicle-inventory` are permitted, per
`durion-positivity-backend/docs/PRD-warranty-claims-module.md` §9.4 (approved 2026-07-15). Rationale: candidate-line origin search across invoices/workorders and settlement
execution against pos-invoice are inherently synchronous counter flows. The dependency is one-directional: no module calls into pos-warranty synchronously; warranty state
leaves the module only as `warranty.*` domain events (including a full claim snapshot event for replica builders).

- **Enforcement.** Encoded in `DomainWallsTest` (pos-archunit) as a per-consumer exception map (`pos-warranty` → exactly those five targets). The utility whitelist is
  **unchanged**; any other module adding a synchronous domain client, and pos-warranty targeting any other domain module, still fails the build. Widening the map requires a
  further amendment to this ADR.
- **Evolution.** Migration to event-fed read-only replicas (R3) remains the target pattern for these reads; it MUST accompany any warranty v2.

### 2026-07-22 — Pos-warranty settlement remains synchronous against pos-invoice

Following durion-positivity-backend#924, `pos-warranty` v2 retires its synchronous read clients in
favor of event-fed `ext_*` replicas for candidate-line search and reference lookups. The final
exception is narrowed to `pos-invoice` only for settlement execution and authoritative
reconciliation.

- **Decision.** `InvoiceClient.createAdjustment` and `InvoiceClient.createRefund` remain
  synchronous calls from `com.positivity.warranty.internal.client` to `pos-invoice`. The matching
  reconciliation reads, `getInvoiceAdjustments` and `getInvoiceRefunds`, also remain synchronous.
- **Rationale.** Warranty settlement is a money-moving counter-flow that must fail loudly in the
  initiating request path. Replacing it with a Kafka command topic would force a pending/confirmation
  state machine for customer-visible refunds and weaken the current "not refunded unless invoice
  accepted it" guarantee. The reconciliation reads must check the authoritative invoice state
  immediately after the write; an event-fed replica can lag and falsely report drift.
- **Enforcement.** `DomainWallsTest` narrows `SCOPED_MODULE_EXCEPTIONS` to the permanent
  `pos-warranty` → `pos-invoice` settlement edge only. The exception does not reopen any other
  synchronous domain reads or writes, and widening it still requires a further amendment to this
  ADR.
- **Boundaries.** Warranty settlement requests to `pos-invoice` MUST carry strong idempotency keys
  so retries remain safe. Result events from `pos-invoice` remain useful for audit, analytics, and
  downstream consumers, but they are not the settlement authority for warranty.

### 2026-07-23 — Pos-order checkout/cancellation is synchronous against pos-invoice (back-ported 2026-08-10)

> **Documentation back-port.** This edge has been live in `DomainWallsTest`'s
> `SCOPED_MODULE_EXCEPTIONS` (`pos-order` → `pos-invoice`) since the counter-sale order parity
> work (durion-positivity-backend#1071/#1072), where the enforcement javadoc cites an "ADR-0044
> amendment 2026-07-23" — but the entry was never added to this canonical ADR (only to the
> since-retired backend-local copy). PRCR-003 (2026-08-10) surfaced the drift; this entry
> regularizes it. The decision content below records what shipped.

- **Decision.** The counter-sale checkout handshake creates the fronting invoice at checkout, and
  the cancellation saga reverses settled payments, via synchronous calls from
  `com.positivity.order.internal.client` to `pos-invoice`.
- **Rationale.** Same money-moving counter-flow class as the 2026-07-22 warranty settlement
  exception: invoice creation and payment reversal must fail loudly in the initiating request
  path. Settlement signals remain asynchronous on `payment.events.v1`.
- **Enforcement.** `SCOPED_MODULE_EXCEPTIONS` carries `pos-order` → `pos-invoice`. Widening
  requires a further amendment to this ADR.

### 2026-08-10 — Scoped exception: synchronous supplier stock-inquiry reads from pos-supplier

`pos-supplier` (new domain module for outbound supplier connectivity, durion#372, architecture in
`docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md`) is added to the
**Domain** class (§1 table): its cross-module integration is event-only (`supplier.commands.v1` /
`supplier.events.v1` topics per §3) with one scoped read exception, approved in the supplier
integration review (durion#374, §12 decisions 4–5).

- **Decision.** **`pos-catalog`** (owner of the Product Detail composition — it already serves
  product-detail display from its `ext_inventory_availability` / `ext_product_lead_time` replicas)
  and **`pos-order`** (procurement flows) MAY call `pos-supplier`'s `SupplierStockService`
  **read API** synchronously for live vendor stock availability/quote. No other module may call
  `pos-supplier` synchronously, `pos-supplier` calls no domain module synchronously, and no write
  path is included in the exception.
- **Rationale.** Live vendor availability is an inherently synchronous counter flow: the user is
  quoting or raising a purchase order and needs the vendor's answer now. The freshness requirement
  is seconds, not minutes — an event-fed replica of external vendor stock cannot meet it, and
  pre-fetching entire vendor inventories to simulate liveness would be worse than the call.
- **Degradation contract.** Callers MUST apply the positivity composition semantics
  (DECISION-POSITIVITY-004/006/007/011): short per-binding timeouts, `SUPPLIER_UNAVAILABLE` status
  on failure or open breaker, null (never zero) numeric fields when status is non-OK, and `asOf`
  timestamps on all values. A `pos-supplier` outage MUST degrade the calling screen's supplier
  component only — never fail the composition.
- **Enforcement (class-level, not module-level).** A bare `SCOPED_MODULE_EXCEPTIONS` entry
  (`pos-catalog`/`pos-order` → `pos-supplier`) would permit *any* synchronous call to
  `pos-supplier`, including writes. The exception is therefore scoped to a **named client class**:
  each caller's sole permitted client source is a single stock-inquiry client (e.g.
  `SupplierStockClient` under `internal.client`) whose only target surface is the
  `SupplierStockService` read API. `DomainWallsTest` MUST be extended to express per-source-file
  scoping for this entry (origin module → target module → allowed client source pattern), so any
  other client source in those modules targeting `pos-supplier` still fails the build. Delivered
  with the CAP-319 implementation (durion-positivity-backend#1225), which also refreshes the stale
  "as of 2026-07-22" enforcement note in the backend-local ADR pointer stub
  (`durion-positivity-backend/docs/adr-0044-event-only-domain-walls.md`).
- **Boundaries.** All other supplier data flows (price catalog, stock report, order lifecycle,
  invoices, shipment, workorder authorization) remain event-only per the main decision.

---

## Changes required in other ADRs

Verified against the ADR texts in this directory (2026-07-08).

### Amendments required

| ADR                                                                                                                  | Subject                                                                               | Required change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| -------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [ADR-0009](0009-backend-domain-responsibilities-guide.adr.md) — Backend domain responsibilities guide                | Domain responsibility matrix with "Integrates With" columns                           | Add a `pos-people-contact` row; split the current pos-people row into contact vs HR responsibilities; update "Integrates With" entries so domain↔domain integration is described as event topics rather than REST calls; reflect accounting as event-only.                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| [ADR-0011](0011-api-gateway-security-architecture.adr.md) — API gateway security architecture                        | Gateway-enforced security; trust model                                                | Add a section stating the gateway trust model governs synchronous/client traffic only; the asynchronous Kafka channel uses the trust model in ADR-0044 §5. No change to token or header semantics.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| [ADR-0012](0012-vehicle-party-relationships-in-customer.adr.md) — Vehicle-party relationships belong in pos-customer | Associations owned by pos-customer                                                    | Association ownership is **unchanged**, but add a clarifying note: vehicle _registry_ CRUD is no longer proxied through pos-customer — the frontend calls pos-vehicle-inventory via the gateway, and pos-customer serves its cross-cutting queries from a read-only `ext_vehicle_*` replica fed by `vehicle.events.v1`. Party-association events still originate from pos-customer.                                                                                                                                                                                                                                                                                                           |
| [ADR-0014](0014-gateway-internal-service-security.adr.md) — Internal service security via gateway route control      | Secure-by-default route whitelist                                                     | Add explicit routes for `pos-people-contact` and confirm the vehicle-inventory route covers the registry write endpoints that become frontend-facing. pos-tax non-registration stance unchanged.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| [ADR-0015](0015-identity-entity-relationships.adr.md) — Identity entity relationships                                | Person definition; Person↔User invariants (I5–I7)                                     | Invariants unchanged; update the owning-module references: `Person`, `PersonContactPoint`, and `user_person_links` move from pos-people to `pos-people-contact`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| [ADR-0017](0017-api-controller-http-response-codes.adr.md) — API controller HTTP response codes                      | Canonical response matrix (no 202 today)                                              | Add `202 Accepted` semantics for endpoints whose effect is enqueuing a command event (response carries a tracking/idempotency reference and a pending-state resource), plus a convention for surfacing async rejection (result event rejected → status resource, not a late HTTP error).                                                                                                                                                                                                                                                                                                                                                                                                      |
| [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md) — Roles/JWT permission governance                     | Permission-based backend authorization                                                | Add: event consumption is not authorized via permissions/`X-Authorities`; command events are authorized by topic/producer per ADR-0044 §5, with the initiating user's permission check performed at the original synchronous edge and `actor` recorded for audit.                                                                                                                                                                                                                                                                                                                                                                                                                             |
| [ADR-0042](0042-openapi-annotation-standards.adr.md) — OpenAPI annotation standards                                  | Mandatory OpenAPI annotations, MCP discovery                                          | Add `pos-people-contact` and the newly frontend-facing pos-vehicle-inventory write endpoints to the enforcement inventory (backend rollout baseline likewise). Async event contracts are out of OpenAPI scope — topic contracts live in `pos-domain-events`; AsyncAPI adoption may be proposed separately.                                                                                                                                                                                                                                                                                                                                                                                    |
| [ADR-0043](0043-user-person-linkage-authority.adr.md) — User–person linkage authority and translation                | `user_person_links` sole source of truth; §2 prefers sync resolve at token-issue time | Two amendments: (a) module references move from pos-people to `pos-people-contact` (link store ownership follows the split); (b) **flip the §2 preference** — the _preferred_ option becomes the one ADR-0043 already sanctions as the alternative: `pos-security-service.users.person_id` retained strictly as a projection written only from the link event, never by user-CRUD code. Link creation/removal initiated by security flows becomes command + confirmation events. Token-issue-time derivation then reads the local projection (no sync call), preserving the [ADR-0022](0022-audit-stable-person-identifier-claim-policy.adr.md) claim contract and its fallback/metric rules. |

### Reviewed — no change required (reaffirmed)

| ADR                                                                    | Why no change                                                                                                                                                                                                                                                                  |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [ADR-0006](0006-workexec-domain-ownership-boundaries.adr.md)           | Timekeeping stays in the people/HR domain; the split does not move any ADR-0006 assignment. Cross-domain integration contracts it references now flow over events per this ADR.                                                                                                |
| [ADR-0013](0013-platform-uuid-identifier-strategy.adr.md)              | Envelope `eventId` complies.                                                                                                                                                                                                                                                   |
| [ADR-0016](0016-location-entity-semantics.adr.md)                      | Verified: it does **not** codify the "do not replicate address data" rule. That rule exists only as javadoc in `pos-invoice` `LocationServiceClient` and `pos-workorder` `LocationClient` and is superseded directly by R3; remove the javadoc when those clients are retired. |
| [ADR-0020](0020-documents-centralized-creation.adr.md)                 | Reaffirmed: pos-documents is a utility; synchronous render calls remain the mandated pattern.                                                                                                                                                                                  |
| [ADR-0021](0021-tax-api-consumption-and-internal-access-policy.adr.md) | Reaffirmed: pos-tax is a utility with direct internal calls; its `destinationAddress` contract is satisfied from callers' location replicas.                                                                                                                                   |
| [ADR-0022](0022-audit-stable-person-identifier-claim-policy.adr.md)    | Claim contract unchanged; derivation flows through the amended ADR-0043 mechanism. Update the link-store module reference alongside ADR-0043's.                                                                                                                                |
| [ADR-0025](0025-permissions-yaml-registration-policy.adr.md)           | Startup-infra registration is explicitly exempt (R2); `pos-people-contact` follows the existing pattern.                                                                                                                                                                       |
| [ADR-0026](0026-service-contract-boundary-policy.adr.md)               | Scope is intra-module package boundaries (`service` vs `internal`), which this ADR does not alter. Optionally add a pointer to ADR-0044 for cross-module transport rules.                                                                                                      |
| [ADR-0027](0027-uuid-typed-id-contract-policy.adr.md)                  | Event payload identifiers are UUID-typed; compliant.                                                                                                                                                                                                                           |

### Superseded non-ADR documents

- `durion-positivity-backend/docs/service-discovery-migration/client-policy-matrix.md` — its `direct-discovery` classification no longer authorizes domain→domain calls;
  `startup-infra`, `gateway-exception`, `tax-exemption`, and `external` categories remain valid.
- The javadoc "platform rule" against replicating address data (see ADR-0016 row above).

---

## References

- durion-positivity-backend#823 — Create stronger domain walls and looser coupling for certain domains
- `durion-positivity-backend/docs/module-coupling/issue-823-event-only-domain-walls-assessment.md` — full call-graph scan, feasibility assessment, and five-phase migration
  plan
- `durion-positivity-backend/docs/adr-0044-event-only-domain-walls.md` — backend-local copy of this ADR
