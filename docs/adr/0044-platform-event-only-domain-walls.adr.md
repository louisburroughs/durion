---
type: ADR
title: 'ADR-0044: Event-Only Domain Walls and Module Communication Policy'
description: Domain modules may not call each other synchronously — cross-domain data moves as events into local replicas — with named scoped exceptions (pos-warranty, pos-order to pos-invoice; pos-marketing to pos-platform-sender) enforced by pos-archunit's DomainWallsTest.
status: stable
adr_status: accepted
created: '2026-07-08'
related: [ADR-0006, ADR-0009, ADR-0011, ADR-0012, ADR-0013, ADR-0014, ADR-0015, ADR-0016, ADR-0017, ADR-0020, ADR-0021, ADR-0022, ADR-0025, ADR-0026, ADR-0027, ADR-0040, ADR-0042, ADR-0043, ADR-0054, ADR-0058, ADR-0062, ADR-0070, ADR-0071, ADR-0072]
tags: [adr, events, platform]
---
# ADR-0044: Event-Only Domain Walls and Module Communication Policy

**Status:** ACCEPTED — amended 2026-10-08 (in-place withdrawal of a RESTRICTED field, [ADR-0072](0072-data-classification-event-payload-minimisation.adr.md));
previously amended 2026-10-05 (extraction worker and inbound-mail edge join the Utility class, [ADR-0070](0070-bill-intake-ownership-and-vendor-master.adr.md));
previously amended 2026-10-03 (pos-marketing → pos-platform-sender FI-2 send, file-scoped), 2026-10-02 (consumer rethrow set, durion-positivity-backend#2355),
2026-09-23 (consumer transaction shape, durion-positivity-backend#2146),
2026-09-09 (tenant context on the event channel, [ADR-0062](0062-postgres-row-level-multitenancy.adr.md)),
2026-09-07 (pos-workorder → pos-price labor-rate resolution, file-scoped),
2026-09-02 (pos-workorder → pos-catalog labor-time resolution, file-scoped) and
2026-08-10 (pos-supplier stock-inquiry sync-read exception; pos-order → pos-invoice back-port dated 2026-07-23); see §Amendments
**Date:** 2026-07-08 (accepted 2026-07-08)
**Deciders:** Architecture, Backend Lead
**Affected Issues:** durion-positivity-backend#823, #1002

---

## Context

Backend domain modules are coupled by ~24 synchronous REST clients across 10 modules (full call matrix in the issue-823 coupling assessment that produced this ADR;
that assessment was retired once this record superseded it, and survives in git history). Reads dominate (customer, location, and people reference data is fetched on
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

| Class                        | Modules                                                                                                                                                                                                                                                                                                                                                                                                       | May be called synchronously? |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| **Utility**                  | `pos-api-gateway`, `pos-security-service`, `pos-documents` (per [ADR-0020](0020-documents-centralized-creation.adr.md)), `pos-image`, `pos-tax` (per [ADR-0021](0021-tax-api-consumption-and-internal-access-policy.adr.md); also owns tenant tax-profile data per [ADR-0071](0071-tax-per-tenant-pluggable-providers.adr.md)), `pos-event-receiver`, `pos-price`, extraction worker and inbound-mail edge (working names, new 2026-10-05, per [ADR-0070](0070-bill-intake-ownership-and-vendor-master.adr.md)) | Yes — by any module          |
| **Domain**                   | `pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inquiry`, `pos-inventory`, `pos-invoice`, `pos-location`, `pos-order`, `pos-people` (HR), `pos-people-contact` (new), `pos-shop-manager`, `pos-vehicle-inventory`, `pos-vehicle-fitment`, `pos-vehicle-reference-*`, `pos-workorder`, `pos-bulk-loader`, `pos-supplier` (new, 2026-08-10), `pos-platform-sender` (new, 2026-10-03)                      | No — events only             |
| **Libraries / non-deployed** | `pos-events`, `pos-shared-dtos`, `pos-domain-events` (new), `pos-security-common`, `pos-tax-common`, `pos-bulk-ingest-lib`, `pos-document-helper`, `pos-dependencies`, `pos-archunit`                                                                                                                                                                                                                         | n/a                          |

`pos-tax` and `pos-price` are utilities because they are stateless _computation_ (tax and price determination), not data lookups — replicating their rule engines into callers
would be worse than the call. `pos-documents` is a utility because ADR-0020 mandates centralized document creation via its render API. `pos-mcp-server` is a gateway client
(bearer-token relay) and follows client rules, not module rules. The extraction worker and the inbound-mail edge are utilities because they are stateless
isolation boundaries for untrusted input, not owners of business data (2026-10-05 amendment). pos-tax also owns tenant tax-profile data and publishes
`tax.registration.changed`; it stays a utility for its computation calls (2026-10-08 amendment).

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
  "tenantId": "<UUID of the tenant the aggregate belongs to; required since 2026-09-09, ADR-0062>",
  "payload": {}
}
```

- Topics: `{domain}.events.v1` (facts) and `{domain}.commands.v1` (requests to the owner). Keyed by `aggregateId` so per-aggregate ordering is preserved.
- Identifiers in payloads are UUID-typed per [ADR-0027](0027-uuid-typed-id-contract-policy.adr.md); `eventId` is UUIDv7 per
  [ADR-0013](0013-platform-uuid-identifier-strategy.adr.md).
- Payload changes within a version MUST be additive-only. Breaking changes require a new topic version (`.v2`), with the owner dual-publishing during the migration window.
  *Amended 2026-10-08 by [ADR-0072](0072-data-classification-event-payload-minimisation.adr.md) Decision 7: a RESTRICTED field may be withdrawn in place
  when no consumer reads it (see §Amendments).*
- `aggregateVersion` is a monotonic per-aggregate sequence; consumers use it to detect gaps and to ignore out-of-date updates.

### 4. Reliability mechanisms (mandatory before a module migrates)

**Decision:** ✅ **Resolved**

- **Transactional outbox.** Producers MUST NOT publish directly from business transactions. Each producer module adds an `event_outbox` table (Flyway) written in the same
  transaction as the state change, drained by a background publisher. At-least-once delivery is the guarantee.
- **Idempotent consumers.** Each consumer module keeps a `processed_events` table keyed by `eventId`, written in the same transaction as the replica update; that
  transaction is the handler's own, not the listener's (amended 2026-09-23, see §Amendments). Redelivery MUST be harmless.
- **Retry and DLQ.** A consumer failure in the retryable set retries with backoff; the set is named and defined once (amended 2026-10-02, see
  §Amendments). A record that still fails, or whose failure the consumer lets propagate on purpose, goes to `{topic}.dlq` and alerts.
  A failure the consumer classifies as permanent (a malformed payload, a business rejection that redelivery cannot fix) is logged and,
  where the consumer records failures, marked processed instead of dead-lettered (amended 2026-09-23, see §Amendments). Either way a failed command MUST surface to its
  requester as a failed/pending item, not silently drop.
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

### 2026-10-08 — Withdrawing a RESTRICTED field in place ([ADR-0072](0072-data-classification-event-payload-minimisation.adr.md))

§3's additive-only rule gains one exception: a RESTRICTED field may be withdrawn from a live payload on the same `eventType` and topic, with no `.v2`
topic and no dual-publish, only when no consumer reads it, `schemaVersion` is bumped, the owner's outbox is scrubbed in the same release and consumers
apply only the new version. The rollout stops old publishers before the scrub, and the broker purge deletes only up to fixed per-partition cutoffs after
a separate DLQ inventory (ADR-0072 Decision 7).

### 2026-10-08 — pos-tax owns tenant tax-profile data and publishes registrations ([ADR-0071](0071-tax-per-tenant-pluggable-providers.adr.md))

pos-tax was classed a utility because it is stateless computation. ADR-0071 keeps it a utility for that computation and adds data it owns.

- **Data.** pos-tax is the source of truth for each tenant's tax registrations, exemption certificates and provider bindings (ADR-0071 §3, §7). People
  change them only through front doors — pos-accounting, pos-customer and pos-tenant — which call pos-tax under R2.
- **Facts.** pos-tax publishes `tax.registration.changed` v1 on `tax.events.v1` with the §4 mechanisms: a transactional outbox, the per-tenant manifest
  `tax.manifest.v1` and re-send. pos-accounting and pos-order keep `ext_tax_registration` replicas (R3). It is the only utility that publishes a domain
  fact; exemption certificates and bindings publish nothing until a consumer needs them.
- **Computation unchanged.** Calculate, refund, commit, void, rate lookup and plausibility stay synchronous utility calls (R2); pos-tax picks the provider
  plug-in per tenant and country, invisibly to callers.
- **Enforcement.** pos-tax stays in `DomainWallsTest`'s `UTILITY_MODULES`; the topic joins the event contract registry when S31 builds it.

### 2026-10-05 — Extraction worker and inbound-mail edge join the Utility class ([ADR-0070](0070-bill-intake-ownership-and-vendor-master.adr.md))

ADR-0070 adds two services for supplier bills that people bring in by upload, import or email. Both join the **Utility** class (§1 table) under their working
names; the table takes the module names when they are built.

- **Extraction worker** (ADR-0070 §5). A stateless reader of untrusted PDFs, images, spreadsheets and inbound CFDI XML that returns fields with per-field
  confidence. It holds no business logic and is the only component holding the extraction provider's credentials. pos-accounting invokes it under R2 as an
  asynchronous job; it runs with CPU, memory, time and decompression-ratio limits.
- **Inbound-mail edge** (ADR-0070 §6). Owns mail transport (MX, SPF / DKIM / DMARC alignment, rate limits, `Message-ID` idempotency, address provisioning),
  takes the tenant only from the receiving address, and hands file references to pos-accounting as a command. It never creates a bill, never replies to
  senders and does not call pos-platform-sender.
- **Rationale.** Neither owns a fact another module replicates; each exists to keep untrusted input and third-party credentials away from the ledger. Replicating
  them would be meaningless, and putting a command topic in front of the extraction call would add nothing the asynchronous job does not already give.
- **Enforcement.** When each module is built it is added to `DomainWallsTest`'s `UTILITY_MODULES`. Neither may call a domain module synchronously.

### 2026-10-03 — Scoped exception: FI-2 message sends from pos-marketing to pos-platform-sender

Ratified by the owner on 2026-10-03 (durion#540) as a scoped exception, in place of the utility
classification first proposed with the module's build.

The FI-2 contract (`domains/positivity/PLATFORM_SENDER_CONTRACT.md`) was written for a shared
platform sender outside this workspace: `pos-marketing` calls it synchronously and consumes its
`sender.outcomes.v1` facts. That sender is now `pos-platform-sender`, a module in
durion-positivity-backend that delivers email through Amazon SES and SMS through AWS End User
Messaging. It joins the **Domain** class (§1 table).

- **Decision.** **`pos-marketing`** MAY call `pos-platform-sender`'s send API
  (`POST /platform-sender/v1/messages`) synchronously, from `PlatformSenderClient` only. No other
  module may call it without its own amendment, and `pos-platform-sender` calls no domain module
  synchronously.
- **Rationale.** The send worker needs the sender's answer per recipient, now: accepted (with the
  provider message id the later outcome facts carry), refused for good, or not delivered, and that
  answer drives its retry ladder. A command event would put a pending state and a result topic in
  front of a call the provider answers in milliseconds, and the provider credentials and delivery
  state behind the answer cannot move into a replica.
- **Degradation contract.** A refusal is a 4xx and permanent; an outage, throttling or a provider
  call with no answer is a 5xx, and the send worker's bounded retry absorbs it. The `messageId`
  idempotency key means a retry never delivers twice: a send the provider may have taken keeps its
  claim, and every replay of it answers 503 `SEND_IN_FLIGHT`. A sender outage delays a campaign's
  sends; a recipient whose attempts run out (`pos.marketing.send.max-attempts`) is recorded as
  failed, and the rest of the campaign carries on.
- **It calls no domain module.** Address resolution (FI-2 §1: "address resolution belongs to the
  sender") runs on two R3 replicas, `ext_customer_person_party` (from `customer.events.v1`) and
  `ext_people_contact_person` (from `people-contact.events.v1`), reconciled per §4 against
  `customer.manifest.v1` and `people-contact.manifest.v1`. Its only synchronous outbound calls are
  the provider (AWS) and the startup event-type registration (R2).
- **It owns one fact.** `sender.outcomes.v1` (FI-2 §2) is published through the module's
  transactional outbox with the tenant on the envelope and the Kafka header (2026-09-09 amendment);
  the provider event's own id is the idempotency key, so a redelivered provider event never
  becomes a second fact.
- **Transport.** The call is direct (`pos.marketing.sender.base-url`), not gateway-routed and not
  user-facing: a shared secret (`X-Pos-Sender-Secret`) authenticates the caller and `X-Tenant-Id`
  carries the caller's bound tenant, since no gateway sits on this hop to inject it.
- **Enforcement (class-level, not module-level).** The exception is scoped to the named client
  class `PlatformSenderClient` under pos-marketing's `internal.client`, expressed in
  `DomainWallsTest`'s per-source-file exception map, whose census test also pins that
  `pos-platform-sender` is not in `UTILITY_MODULES`. A second pos-marketing client, or any other
  module's client, targeting `pos-platform-sender` fails the build.

### 2026-10-02 — Consumer rethrow set (durion-positivity-backend#2355)

Ratified by the owner on 2026-10-02. #2355 proposed the whole of `TransactionException` as the fourth member of the
set; the ratified set names three of its types instead, for the reason given below.

The 2026-09-23 amendment pinned what a consumer rethrows as `TransientDataAccessException`, and the code and issues
that followed described that as covering "a connection blip, lock timeout or deadlock". It covers only the last two.
In Spring's hierarchy a dropped or refused connection (PgJDBC `08xxx`, Hibernate's `JDBCConnectionException`) is a
`DataAccessResourceFailureException`, which extends `NonTransientDataAccessResourceException`; a failure the driver
reports as recoverable is a `RecoverableDataAccessException`, also outside the transient branch; and a failure to
open the handler's `REQUIRES_NEW` transaction is a `CannotCreateTransactionException`, which is not a
`DataAccessException` at all. Every consumer that followed the reference shape therefore treated a lost connection
as permanent. A command listener logged and dropped the command. A consumer that records failures did worse: its
catch wrote the `processed_events` mark, so an event lost to a connection failure in the middle of its transaction
was recorded as processed and never applied.

The rule is now:

- **A consumer rethrows the retryable set for container retry.** The set is six types:
  - `TransientDataAccessException`: a lock timeout, a deadlock, a query timeout, a locking failure.
  - `RecoverableDataAccessException`: a failure the driver reports as recoverable on a fresh connection.
  - `DataAccessResourceFailureException`: a dropped or refused connection, an exhausted pool.
  - `CannotCreateTransactionException`: the handler's transaction could not be opened.
  - `TransactionSystemException`: the commit or rollback failed in the transaction infrastructure.
  - `TransactionTimedOutException`: the transaction ran past its deadline.

  Spring's "non-transient" means "retrying at once will not help", and the container's exponential backoff, followed
  by `{topic}.dlq`, is the answer to exactly that.
- **Every other `TransactionException` is permanent.** `UnexpectedRollbackException`,
  `IllegalTransactionStateException`, `NoTransactionException` and the rest of `TransactionUsageException`, and
  `HeuristicCompletionException` report a state the same code reaches again on redelivery: something inside the
  handler marked the transaction rollback-only, or the code asked the transaction manager for something it cannot do.
  Retrying them holds the partition through the whole backoff and dead-letters a record that was never going to apply,
  which is the failure the 2026-09-23 amendment removed. For the same reason the two subclasses of
  `CannotCreateTransactionException` that describe the transaction manager and not the database,
  `NestedTransactionNotSupportedException` and `TransactionSuspensionNotSupportedException`, are excluded from the set
  by name.
- **The whole cause chain is inspected.** A failure is retryable if it, or any exception in its cause chain, is one
  of those types. A service that wraps a lost connection in its own exception has still lost the connection. Being
  wrong in that direction costs a few retries and a dead letter somebody looks at; being wrong in the other loses
  the event silently. Only these Spring types count: a driver or Hibernate exception that reaches the consumer
  untranslated, with no Spring type anywhere in its chain, is not in the set and stays permanent.
- **The set has one definition.** `RetryableConsumerFailures.isRetryable(Throwable)` in `pos-tenancy-common`
  (`com.positivity.tenancy.kafka`) lists the types and the two excluded subclasses, and no consumer names them
  itself. Changing the set means changing those lists and this section, and nothing else.
- **"Permanent" still means what it meant.** A malformed payload, an unsupported type, a constraint or integrity
  rejection, a business rejection, a programming error, a transaction left rollback-only: anything outside the set is
  a failure redelivery cannot fix. The consumer logs it and, where it records failures, marks the record processed in a transaction of its
  own, as the 2026-09-23 amendment describes. A consumer that deliberately rethrows more than the set (one that
  lets every database failure propagate because a lost record cannot be recovered) keeps doing so.
- **Classify first, then mark.** A consumer whose catch records the failed record asks the classifier at the head
  of that catch and rethrows on a retryable failure before it logs, counts or writes anything. No
  `processed_events` mark may be written for a record that was not applied and can still be retried.
- **Redelivery was already required to be harmless** (§4, idempotent consumers). Widening the set redelivers more
  records; it adds no new requirement on a handler.

Retry count, backoff, dead-letter topics and consumer groups are unchanged.

`pos-archunit`'s `KafkaConsumerRetryArchitectureTest` enforces the shape: a consumer class that catches
`TransientDataAccessException` fails the build, and so does a `@KafkaListener` class whose broad catch guards handler
work without asking the classifier. Each module pins the behaviour with a propagation test that throws
`DataAccessResourceFailureException` and asserts that no mark was written. A `QueryTimeoutException` is transient and
exercises none of this, which is why every module's earlier tests passed over the gap.

### 2026-09-23 — Consumer transaction shape (durion-positivity-backend#2146)

§4 originally checked `processed_events` "in the same transaction as the replica update", and the reference consumer
(#837) implemented that by making the `@KafkaListener` method `@Transactional` and catching the handler's exception
as a permanent failure before writing the mark. That catch cannot work. The handlers are `@Transactional` services or
call Spring Data repositories, which are transactional too, so an exception leaving them marks the shared transaction
rollback-only. The commit after the catch then throws `UnexpectedRollbackException`, the container retries the record
through its whole back-off and dead-letters it, and the mark is rolled back on every attempt. A failed command held its
single-partition topic for 31 s this way (#2145).

The consumer shape is now:

- **The listener method is not `@Transactional`.** It parses the record, checks `processed_events`, and drops a record
  that is already marked or malformed, all before any transaction is opened.
- **The handler and its mark commit together, in the handler's own transaction** (`TransactionTemplate`,
  `PROPAGATION_REQUIRES_NEW`). A success is applied and marked atomically, as §4 intended. A permanent failure rolls
  back only that transaction.
- **A permanent failure is recorded, where the consumer records failures, in a transaction of its own.** The catch
  writes the mark after the handler's transaction has rolled back, so redelivery does not retry the record. A consumer
  whose contract leaves a failed record unmarked keeps doing so.
- **Transient failures still propagate.** `TransientDataAccessException` is rethrown, so the container retries the
  record and no mark is written for a record that was not applied. (The 2026-10-02 amendment above widens what is
  rethrown from that one type to a named set of six.)

The first cut of this fix, in `pos-inventory`'s `InventoryCommandListener` (#2145), wrote the mark in a second
transaction after every handler, successful or not. That opens an at-least-once window between the two commits, which
its pick-command handlers tolerate. Some handlers elsewhere do not: `pos-order` applies goods receipts as deltas and
appends a timeline row per event, so the shape above is the platform shape and #2145's is the exception.

Each module pins the shape with a Spring-backed test that uses a real transaction manager and a `@Transactional`
handler that throws, both bare and inside an enclosing transaction. `pos-inventory`'s
`InventoryCommandListenerTransactionTest` is the template. Mock-based listener tests cannot see this defect, which is
why every module's copy of the old shape passed them.

### 2026-09-09 — Tenant context on the event channel ([ADR-0062](0062-postgres-row-level-multitenancy.adr.md))

ADR-0062 makes every domain row tenant-scoped under Postgres RLS. The event channel carries the tenant so that a
consumer's replica write lands in the right tenant and never in none:

- **Envelope.** `tenantId` (UUID) is a required envelope field (§3). The module's outbox writer stamps it from the
  bound tenant as it queues the envelope (`DomainEventEnvelope.stampedWith`), the same tenant it writes on the outbox
  row and the Kafka header, and refuses an envelope already built for a different tenant (`pos-workorder`'s writer,
  which builds the envelope itself, passes that tenant to the ten-argument `of(...)` directly). A sender that bypasses
  the outbox (a reconciliation manifest, sent under `@PlatformScoped` with no bound tenant) passes the tenant
  explicitly. So a producer without a bound tenant cannot publish through the outbox, and no path publishes an
  envelope without a tenant. Messages published before the field existed (2026-09-10) carry none; consumers read the
  field as nullable for that compatibility and must not depend on its absence for anything else.
- **Kafka header.** The outbox publisher also sets a `tenantId` record header from the outbox row, so a consumer can
  bind before deserialising the payload.
- **Consumer binding.** A `RecordInterceptor` shipped in `pos-tenancy-common` binds `TenantContext` from the header
  before every `@KafkaListener` and clears it after. A record without the header is not processed; it goes to
  `{topic}.dlq` (§4) and alerts. Consumer database work is then scoped exactly like a request; a replica write for
  another tenant is rejected by RLS `WITH CHECK`.
- **Outbox and ledger tables are global with tenant data.** `event_outbox` and `processed_events` are `@TenantGlobal`
  tables carrying `tenant_id` as a plain column, because the poller and the idempotency check run unbound. They are
  the only place a module handles more than one tenant's rows in one statement.
- **Reconciliation is per tenant.** Manifests on `{domain}.manifest.v1` are emitted per tenant, one per tenant per
  window, and re-emit requests carry `tenantId`. **Landed 2026-09-11 (plan WS4-3, backend #1952):** `ReconciliationManifestV1`
  gains a required `tenantId`; each `ManifestPublisher` groups a closed window's rows by tenant and publishes one
  manifest per tenant (zero-count manifests included); consumers compare drift and request replay per tenant. This
  closes the WS4-1 interim exception, under which the owner had summarised every tenant's rows of a window into one
  platform-tenant record. A manifest published before the field existed carries no tenant and is **skipped**, not
  read as the platform tenant's record: an earlier review round found that reading it that way compares an all-tenant
  count against a single-tenant ledger scan and reports drift forever, and the replay it would trigger can only
  requeue one tenant's events anyway. Every listener instead logs a WARN and counts
  `replica.manifest.skipped{reason="missing_tenant"}` on such a manifest (verified in, e.g.,
  `pos-inventory`'s `CatalogManifestListener`); the corresponding `processed_events` rows recorded before their
  `tenant_id` column existed do not self-heal on replay and need the backfill the runbook documents.
- **Module classification (§1).** `pos-tenant` is a domain module that owns the tenant registry and the owning
  `account`. It publishes `tenant.created`, `tenant.provisioned`, `tenant.updated`, `tenant.suspended`,
  `tenant.reactivated`, and `tenant.decommissioned` on `tenant.events.v1` with the public projection only (`id`,
  `slug`, `display_name`, `status`); `pos-security-service` publishes `tenant.provisioned` on the same topic once
  the role template and initial administrator are seeded. Every module keeps a global `ext_tenant` replica of that
  projection and never calls `pos-tenant` synchronously.
- **Security model (§5).** Unchanged: the `tenantId` header is trusted because the broker is internal and producers
  are service identities. It is a scoping value, not an authorization one; `actor` remains audit-only.

### 2026-07-16 — Scoped exception: pos-warranty v1 synchronous clients

`pos-warranty` (new domain module, durion-positivity-backend#786) is granted a scoped exception to R1 for its v1: synchronous `@LoadBalanced RestClient` calls from
`com.positivity.warranty.internal.client` to `pos-invoice`, `pos-workorder`, `pos-catalog`, `pos-customer`, and `pos-vehicle-inventory` are permitted, per the warranty claims PRD §9.4 (approved 2026-07-15; PRD retired after delivery,
issue durion-positivity-backend#786 / PR #920, principles carried into
`domains/warranty/.business-rules/BACKEND_CONTRACT_GUIDE.md`). Rationale: candidate-line origin search across invoices/workorders and settlement
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
  "as of 2026-07-22" enforcement note that the backend-local pointer stub carried before it
  was removed in favour of this canonical record.
- **Boundaries.** All other supplier data flows (price catalog, stock report, order lifecycle,
  invoices, shipment, workorder authorization) remain event-only per the main decision.

### 2026-09-02 — Scoped exception: synchronous catalog labor-time resolution from pos-workorder

Estimated service time (book time) gains its transport per ADR-0058 §5 (durion-positivity-backend#1569,
sourcing plan §6): a second grant-surface read, `com.positivity.catalog.service.ServiceLaborTimeService`,
resolved over `POST /v1/catalog/labor-times/resolve`.

- **Decision.** **`pos-workorder`** MAY call `pos-catalog`'s `ServiceLaborTimeService` read API
  synchronously to resolve the vehicle-specific labor time for a `LABOR` estimate item at quote
  time. No other module may call it, no write path is included, and `pos-catalog` calls no domain
  module synchronously as part of serving it.
- **Rationale.** The vehicle-keyed labor-time matrix cannot ride events: it is large (millions of
  rows at aggregator scale), licensed (per-source contract terms decide whether times may be
  replicated at all, and QUERY_ONLY sources may never be persisted — ADR-0058 §4), and
  query-shaped (a quote needs one answer for one vehicle now). The degraded/offline path is the
  vehicle-agnostic `defaultLaborHours` carried additively on the `catalog.service.updated` fact
  at **schemaVersion 2** (the additive in-place bump per ADR-0044 §3, following the
  `ProductUpdatedV1` schema-v2 precedent; version-1 consumers are unaffected), which
  pos-workorder replicates — a prefill fallback, never the vehicle-correct answer.
- **Degradation contract.** The read never throws for a miss or vendor-side failure: typed
  statuses `RESOLVED | NO_TIME_AVAILABLE | SOURCE_UNAVAILABLE`, with source/revision/match-grade
  provenance on `RESOLVED`. A pos-catalog outage degrades to the replica default hours, then to a
  blank prefill the service writer types over — the estimate flow itself must never fail.
- **Enforcement (class-level, not module-level).** The exception is scoped to the named client
  class `CatalogLaborTimeClientImpl` under pos-workorder's `internal.client`, expressed in
  `DomainWallsTest`'s per-source-file exception map; a second pos-workorder client targeting
  pos-catalog still fails the build. The granted type joins the pos-archunit
  `GRANTED_GRANT_SURFACE_TYPES` census (ADR-0026 D2/D4).
- **Boundaries.** Everything else pos-workorder needs from pos-catalog continues to ride the
  `catalog.service.updated` / `catalog.product.updated` facts.

### 2026-09-07 — Scoped exception: synchronous labor-rate resolution from pos-workorder to pos-price

Tier 0 of durion-positivity-backend#1575 adds the other operand of a labor line. A third
grant-surface read, `com.positivity.price.service.ShopLaborRateService`, is resolved over
`POST /v1/labor-rates/quote`.

- **Decision.** **`pos-workorder`** MAY call `pos-price`'s `ShopLaborRateService` read API
  synchronously (from `PriceLaborRateClientImpl` only) to resolve the hourly labor rate, with the
  shop labor matrix applied, for a `LABOR` estimate item at quote time. No other module may call
  it without its own explicit ADR-0044 amendment/grant, no write path is included, and `pos-price` calls no domain module synchronously as part of serving it.
- **Rationale.** The rate is a *sell price* and belongs in pos-price under
  [ADR-0054](0054-sell-price-system-of-record-split.adr.md); pos-catalog owns how long an
  operation takes and pos-price owns what an hour of it costs. The two grants are deliberately
  a matched pair rather than one combined edge, so either half can change source or shape
  without dragging the other.
- **Why not an event.** The matrix makes the answer a function of the quote — which conditions
  (corrosion, restricted access, after-hours, a fleet contract) the writer agreed apply, and in
  which order those steps compound — not a value that can be broadcast and cached. A replica
  would have to carry every location's rate and matrix table and then re-implement the
  compounding, which is precisely the derivation this edge exists to own; two implementations of
  it is two answers to what a customer is charged.
- **Degradation contract.** The read never throws for a miss: typed statuses
  `RESOLVED | NO_RATE_AVAILABLE`, with the answering row's scope, id and effective-from as
  provenance, and each applied matrix step itemised with the rate it produced so an invoice can
  show its derivation. A pos-price outage degrades to a blank price the service writer types
  over — the estimate flow itself must never fail. There is deliberately no replica fallback
  here, unlike the labor-time edge: a stale rate is a wrong number on an invoice, where a stale
  vehicle-agnostic *time* is only a less precise prefill.
- **Enforcement (class-level, not module-level).** The exception is scoped to the named client
  class `PriceLaborRateClientImpl` under pos-workorder's `internal.client`, expressed in
  `DomainWallsTest`'s per-source-file exception map; pos-workorder now holds two file-scoped
  grants and a third client targeting either module still fails the build. The granted type
  joins the pos-archunit `GRANTED_GRANT_SURFACE_TYPES` census (ADR-0026 D2/D4).
- **Boundaries.** Everything else pos-workorder needs from pos-price continues to ride events.
  Nothing in this amendment permits pos-price to be written from pos-workorder, and rate
  *authoring* stays a human-facing pos-price API behind `pricing:labor_rate:manage`.

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

- `durion/docs/architecture/INTERNAL_TRANSPORT_AND_SERVICE_DISCOVERY.md` — its `direct-discovery` classification no longer authorizes domain→domain calls;
  `startup-infra`, `gateway-exception`, `tax-exemption`, and `external` categories remain valid.
- The javadoc "platform rule" against replicating address data (see ADR-0016 row above).

---

## References

- durion-positivity-backend#823 — Create stronger domain walls and looser coupling for certain domains
- issue-823 coupling assessment — full call-graph scan, feasibility assessment, and five-phase migration plan; retired once this ADR
  superseded it, available in git history
- The backend-local copy of this ADR was removed on 2026-09-20; this file is the only canonical record
