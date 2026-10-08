---
type: ADR
title: 'ADR-0072: Data Classification and Minimisation for Event Payloads, Replicas, Logs and DLQs'
description: Four data classes; RESTRICTED values stay encrypted in their owner, revealed only by permission with audit, and only masked forms such as last4 travel.
status: draft
adr_status: proposed
created: '2026-10-08'
related: [ADR-0018, ADR-0022, ADR-0044, ADR-0046, ADR-0050, ADR-0062, ADR-0065, ADR-0070, ADR-0071]
tags: [adr, events, platform, security, supplier]
---
# ADR-0072: Data Classification and Minimisation for Event Payloads, Replicas, Logs and DLQs

**Status:** PROPOSED **Date:** 2026-10-08 **Deciders:** Platform Owner (acceptance pending), Chief Architect, Security & Authorization Domain
**Affected Issues:** louisburroughs/durion-positivity-backend#2617 (analysis and ruling), louisburroughs/durion-positivity-backend#2621 (first application),
louisburroughs/durion-positivity-backend#2517 (S24), louisburroughs/durion#550 (CAP:550)

> **How to read this.** This record is **PROPOSED**. The Platform Owner reviews it before it is accepted; until then no decision below is in force. It
> writes down, as platform policy, the Chief Architect analysis of louisburroughs/durion-positivity-backend#2617 (comment 6062021008) and the Security &
> Authorization Domain's ruling on the same issue (comment 6062214186, rulings 1-9), both dated 2026-10-08. Each "Proposed decision" below becomes
> ✅ **Resolved** on acceptance. Its two amendments, to [ADR-0044](0044-platform-event-only-domain-walls.adr.md) §3 and
> [ADR-0070](0070-bill-intake-ownership-and-vendor-master.adr.md) Decision 7, are a **merge gate** for #2621: that story may be implemented now, but its
> pull request must not merge until this ADR is ACCEPTED.

---

## Context

### Current state

- **No platform classification.** Nothing in the ADR set classifies the data that travels in event payloads or sits in replicas, logs and DLQs. Each case
  has been settled locally:
  - ADR-0070 Decision 7 keeps bank details off Kafka (specification OI-14), case by case;
  - ADR-0050 §7 encrypts pos-supplier's exchange-audit payloads and redacts them "driven by data classification", for that audit only;
  - Security DECISION-INVENTORY-013 curates audit API responses and gates raw payloads behind a permission, for audit APIs only;
  - ADR-0046 sets log levels and has no payload-content rule;
  - ADR-0062 isolates tenants from each other, not data from operators, backups or the broker.
- **The case that exposed the gap (#2617).** Verified on backend `main` at `8e9262491`:
  - pos-supplier accepts any `scheme` (up to 32 characters) and any `number` (up to 64) as a vendor tax registration and stores them in clear in
    `supplier_vendor.tax_registrations` (`jsonb`). `GET /v1/supplier/vendors` returns the full numbers to every holder of `supplier:vendor:read`.
  - `VendorFactPublisher.toFact` copies `scheme`, `number` and `region` verbatim onto `supplier.vendor.updated` (`SupplierVendorUpdatedV1`, schema
    version 1), as ADR-0070 Decision 7 requires ("carries every vendor field").
  - `supplier_event_outbox` stores each envelope in clear and is never purged. `supplier.outbox.replay-requested` re-sends stored envelopes verbatim, so
    a number the vendor master no longer holds can be published again at any time.
  - `supplier.events.v1` keeps records 7 days and its DLQ 30 days. Five consumer groups (pos-accounting, pos-catalog, pos-inventory, pos-order,
    pos-workorder) receive and parse every one of the topic's ~14 supplier event types, so topic ACLs cannot narrow who receives the number.
  - `DeadLetterPublishingRecoverer` copies the full record value to `{topic}.dlq`, and the operations runbook inspects DLQs with
    `kafka-console-consumer.sh --from-beginning`, which prints values to the operator's terminal.
  - S24 (#2517), as first written, would copy the full numbers into pos-accounting's `ext_supplier_vendor`, although no S24 rule reads them.

### The problem

A tax-registration number is normally a business number (EIN, GST/HST BN, QST, VAT). For a sole-proprietor vendor it can be a US SSN or a Canadian SIN,
which pos-supplier must be able to hold for 1099 and T4A filing. The scheme cannot tell the two apart: `scheme` is free text, an SSN and an EIN are both
nine digits, and a SIN looks like the root of a BN. A protection that depends on the scheme therefore fails open.

[ADR-0044](0044-platform-event-only-domain-walls.adr.md) R3 already requires replicas to copy "the minimum fields required", but nothing says which
values may never leave their owner, and ADR-0044 §3 makes the only way out of a live payload a `.v2` topic with the owner dual-publishing, which keeps the
value on the topic for the whole migration window.

### Drivers

- A personal identifier filed under a business scheme cannot be detected, so protection must be uniform across schemes.
- Every copy path multiplies exposure: the topic, its DLQ, the never-purged outbox, verbatim replay, each replica and its backups, and each log.
- No consumer needs a full registration number: louisburroughs/durion-positivity-backend#2615 needs "on file" and the last four characters, and S32
  (#2523) Q4 needs presence.
- The owner must still be able to hold the full number, because 1099 and T4A filing need it, and refusing it would push payee data into spreadsheets.
- Pre-production: no production data exists, so a clean rule costs less now than at any later point.

### Scope

Every module that publishes or consumes domain events, keeps an ADR-0044 replica, writes logs, metric tags, exception messages or audit rows, or operates a
DLQ; the `pos-domain-events` contract tests; and the backend operations runbook. Out of scope:

- the closed, classified tax-scheme vocabulary, a later pos-tax stub that the Accounting Domain specifies (AW48; Security ruling 5);
- where bank details for electronic payment live (specification OI-14), beyond classing them RESTRICTED;
- 1099 and T4A filing, which asks for any wider reveal grant when it is built.

---

## Decision

### 1. Four data classes

**Proposed decision:** Every field the platform stores, publishes, copies or logs belongs to one of four classes. The class decides where the value may
appear.

| Class | What it covers | Where it may appear |
| --- | --- | --- |
| **RESTRICTED** | Personal government identifiers; **every tax-registration number, whatever its scheme**; bank-account and card data; credentials and secrets | Only in the owner's store, encrypted at field level (Decision 3), and in a reveal response (Decision 4). Elsewhere only as a masked derivative (Decision 2) |
| **CONFIDENTIAL** | Names, postal addresses and e-mail addresses of natural persons; masked derivatives such as `last4` | On a fact, in a replica where ADR-0044 R3 needs them, and in API responses. Never logged at INFO or above, never a metric tag; a masked derivative is never logged at all |
| **INTERNAL** | Everything else: identifiers, statuses, amounts, dates, business references, configuration | Anywhere the module's own rules allow, at the log levels of ADR-0046 |
| **PUBLIC** | Data the platform publishes outside the tenant on purpose, such as published API reference material | Anywhere. A value is PUBLIC only when a decision says so |

- **RESTRICTED in detail:**
  - government-issued identifiers of natural persons: SSN, SIN, ITIN and any other personal taxpayer id;
  - every tax-registration number, business numbers included: EIN, BN / GST-HST, QST and VAT;
  - bank-account and card data: account and routing numbers, IBAN, card number, CVV (OI-14 is the precedent);
  - credentials and secrets: passwords, API keys, tokens and private keys.
- **CONFIDENTIAL in detail:** names, postal addresses and e-mail addresses of natural persons, sole-proprietor vendors included, and the masked derivatives
  of RESTRICTED values (`last4`).

- **Classify by what a field can hold, not by its label.** A field that can hold a RESTRICTED value is RESTRICTED, and nothing branches on a scheme, a
  type code or any other label to lower the class.
- **The owner classifies.** The module that owns a fact (ADR-0044 R6) classifies each field when it adds the field. An unclassified field is INTERNAL only
  when it cannot hold anything higher; when in doubt, the higher class applies.
- The Security ruling set RESTRICTED, CONFIDENTIAL and INTERNAL as working classes. PUBLIC completes the scale and is assigned only by decision, so it
  loosens nothing that the ruling restricted.

### 2. RESTRICTED values never leave their owner

**Proposed decision:** A RESTRICTED value never appears:

- on Kafka: a topic, a DLQ, a replay, or the owner's outbox payload from which those are published;
- in a consumer replica, or in its backups;
- in a log line at any level, a metric tag, an exception message, a validation message or `fieldErrors` entry, or an audit row;
- in any API response other than the reveal response of Decision 4.

Outside the owning service only a **masked derivative** may appear. The derivative for an identifier is `last4`:

- **Rule:** remove every non-alphanumeric character, then keep the last four characters. `last4` is `null` when fewer than eight characters remain, so a
  short number never shows half of itself.
- **Computed once, by the owner,** at write time and stored next to the ciphertext. Facts carry the stored value, and no consumer derives it again.
- **CONFIDENTIAL:** `last4` may travel on a fact, sit in a replica and appear in API responses; it is never logged at any level and never used as a metric
  tag.
- **Presence is the derivative too.** Being on a fact's list means the value is on file; consumers need no separate flag.

Owners **accept** the RESTRICTED values they legitimately need, personal identifiers included (Security ruling 5), and protect them under Decisions 3 and 4,
rather than refusing them.

### 3. At rest: field-level encryption under a per-purpose key

**Proposed decision:** The owning service encrypts each RESTRICTED value at field level. Database or disk encryption alone is not enough, because it does
not protect against SQL readers, `pg_dump` backups, support sessions or a restored copy.

- **Envelope:** the `AuditPayloadCipher` pattern from pos-supplier (ADR-0050 §7): AES-256-GCM, a header `version | keyIdLen | keyId` used as AAD, a random
  96-bit nonce, and decrypt-only previous keys for rotation. Extracting the shared envelope and key-policy code is preferred to copying it.
- **One key per purpose.** A key protects one kind of data and is never reused for another: data with different lifetimes (a 400-day exchange audit, a
  number that lives as long as the vendor) needs keys that rotate independently. Properties follow
  `pos.<module>.<purpose>.encryption.key` / `.key-id` / `.previous-keys`, bound from an environment variable provisioned from the secret store, never from
  a committed file.
- **Bind each ciphertext to its row.** The AAD also carries the tenant id, the aggregate id and the element id, so a ciphertext copied into another row,
  aggregate or tenant fails authentication.
- **Fail-closed key policy.** Startup fails when the key is missing unless every active profile is `dev` or `test`, where an ephemeral key and one WARN
  are allowed. The failure message names the environment variable and never prints key material.
- **Masked reads never decrypt.** The derivative is stored next to the ciphertext and read from there.
- **Migrating existing data.** A SQL migration cannot hold the key, so clear values already stored are encrypted by a **Flyway Java migration**
  (a `JavaMigration` registered as a Spring bean, which Spring Boot hands to Flyway) with the cipher injected. It is idempotent, honours row-level security
  the way the module's other migrations do (ADR-0062), and logs counts only. When it finishes, no stored row holds a clear value.
- **Backups are not rewritten.** They age out under their normal retention, unless Decision 9 makes the case a data incident.

### 4. The reveal pattern

**Proposed decision:** A person sees a full RESTRICTED value only through a dedicated, audited reveal call.

- **Masked by default.** Every read, list and write response returns the masked shape (for an identifier `{elementId, ..., last4}`), never the value.
- **Dedicated permission `<domain>:<resource>:reveal`.** The resource names the data class (as `people:employee_pii:view` does), and the action is
  `reveal`, not `view` or `read`, so ADR-0062 §7's SUPPORT ceiling (`RolePermissionBaselineTest#supportIsReadOnly`) structurally keeps it off SUPPORT. It is
  granted to the narrowest roles that need it, and registered in all three permission catalogs with one `CATALOG_VERSION` bump.
- **A POST with a reason.** `POST .../{elementId}/reveal` with body `{reason}`, trimmed, at least 10 characters (otherwise 400 `JUSTIFICATION_REQUIRED`) and
  at most 500 (otherwise 400 `VALIDATION_ERROR`). POST, because the call writes an audit row, and so that it stays out of caches and URL logs. The
  response carries the value with `Cache-Control: no-store`.
- **Audit in the same transaction, fail-closed.** Before the value is returned, the service inserts one append-only, tenant-scoped audit row in the same
  transaction: the actor from the security context (ADR-0018, ADR-0022), the actor's roles, the aggregate and element ids, the non-restricted attributes
  (such as the scheme), the reason, the correlation id, the time and the outcome. The row never holds the value or its derivative. If the insert fails,
  the call fails and **reveals nothing**; the insert never runs in a separate (`REQUIRES_NEW`) transaction or after the value is returned.
- **Refusals reveal nothing.** A 403 writes no row. A decryption failure answers 500 with a module-specific `..._UNREADABLE` code, is logged with ids and
  the key id only, and is audited with outcome `UNREADABLE`.
- **Reviewed by someone else.** Reveal rows are read through the module's audit-read permission, held where possible by roles other than the revealers.
- **Writes under masking.** Each stored element has a stable id (UUIDv7). An update that sends the id without a value keeps the stored ciphertext; changing
  an attribute that describes the value (such as its scheme or region) requires re-entering the value; an unknown id is 400 `VALIDATION_ERROR`; an omitted
  element is removed. Change detection compares ids and stored attributes, never ciphertext.
- **In the browser,** a revealed value is shown only until its dialog closes and is never cached, stored, put in a route or query string, or logged
  ([ADR-0065](0065-frontend-untrusted-content-and-browser-persistence-policy.adr.md)).

The first application is pos-supplier's `supplier:vendor_tax_id:reveal` (granted to ADMIN and CONTROLLER only), with its `supplier_vendor_tax_id_reveal`
audit table read through `supplier:audit:read` (#2621).

### 5. DLQ inspection and runbook handling

**Proposed decision:** Operators handle DLQ and topic contents so that no value leaves the broker host, for every topic, not only the supplier topics.

- **Metadata by default.** The standard inspection command prints metadata only:
  `--property print.value=false --property print.key=true --property print.headers=true --property print.partition=true --property print.offset=true
  --property print.timestamp=true`.
- **One record at a time.** A value may be printed only for one named record: `--partition N --offset M --max-messages 1`.
- **Never `--from-beginning`** with values printed, on a DLQ or any other topic.
- **Never paste a value anywhere.** Output stays in the operator's terminal. It is never pasted into an issue, pull request, ticket or chat, and never
  redirected to a file.
- **Consumers** never put payload text in an exception message bound for a DLQ or a log. Jackson's source inclusion in parse-error locations stays off
  (its default); a module that turns it on breaks this rule.
- A restricted inspection host is not required: the command already runs inside the broker container through `docker exec` on the host (Security ruling 7).

### 6. The contract-test guard

**Proposed decision:** `DomainEventContractTest` in `pos-domain-events` carries a field-name guard (`noRestrictedFieldNames`), parameterised over every
event record it scans.

- It walks record components **recursively**: nested records, `List`, `Set` and `Map` element types, and arrays.
- It fails on any component whose name, compared case-insensitively, is one of: `number`, `ssn`, `sin`, `tin`, `itin`, `taxId`, `taxNumber`, `nationalId`,
  `accountNumber`, `routingNumber`, `iban`, `cardNumber`, `pan`, `cvv`, `password`, `secret`.
- **Allowlist.** Exceptions sit in a map inside the test, each entry with a written reason and a Security sign-off reference. It **starts empty**: on `main`
  the only match is `SupplierVendorUpdatedV1.TaxRegistration.number`, which #2621 removes.
- The guard is a backstop for names, not proof of classification. Classifying each field remains the owner's job (Decision 1). Annotation-based
  classification of payload fields is not decided here and needs its own amendment.

### 7. Amendment to ADR-0044 §3: withdrawing a RESTRICTED field in place

**Proposed decision:** ADR-0044 §3 keeps its rule that payload changes within a version are additive-only, with one exception. A **RESTRICTED** field may be
withdrawn from a live payload **in place**, on the same `eventType` and topic, with **no `.v2` topic and no dual-publish**, only when all of the following
hold:

1. **No consumer reads the field.** The pull request records the search of every consumer of the event type that shows it.
2. **`schemaVersion` is bumped** on the envelope; the record keeps its name (the `ProductUpdatedV1` precedent), and its Javadoc cites this amendment.
3. **The owner's outbox is scrubbed in the same release.** A migration rewrites every stored payload of that event type to the new shape and version, and
   clears `last_error` on those rows, so verbatim outbox replay cannot publish the old value again. Rows of other event types are untouched.
4. **Consumers apply only the new version.** A consumer marks an older-version event of that type processed, counts it (metric tags `eventType` and
   `schemaVersion` only), skips it and never logs its payload. Replicas are seeded from the owner's replay (ADR-0044 §4 bootstrap).

After the new publisher is deployed and every consumer group on the topic shows zero lag, records up to the high-water mark on every partition of the topic
and its DLQ are deleted with `kafka-delete-records.sh`, and the offsets are recorded on the pull request; other event types in that range stay replayable
from the outbox. Dual-publishing is ruled out because it would keep the RESTRICTED value on the topic for the whole migration window. A field that a
consumer does read is outside this exception and needs its own amendment, reviewed by Security.

### 8. Amendment to ADR-0070 Decision 7: the vendor fact carries masked registrations

**Proposed decision:** `supplier.vendor.updated` carries every vendor field **except bank details and full tax-registration numbers**. Tax registrations
travel as `{scheme, region, last4}` at `schemaVersion` 2 (Decision 7 above); a registration on the list means its number is on file. The full number stays
in pos-supplier, encrypted (Decision 3) and revealed only through `supplier:vendor_tax_id:reveal` (Decision 4). No `ext_supplier_vendor` copy holds a full
number: pos-accounting's copy holds `tax_registrations` as `[{scheme, region, last4}]`, and the pos-order and pos-inventory copies hold no registrations.

### 9. Incident rule

**Proposed decision:** Before any scrub or purge of a RESTRICTED value, the implementer runs a **read-only, counts-only** check in every environment that
holds data (alpha: through SSM, as `pos_user`): the values grouped by their non-restricted attributes (for registrations, by `scheme`), and the number of
aggregates holding at least one. The output is counts, never values, and goes on the pull request.

- **Zero, or synthetic test entries only:** a pre-production security defect, closed by the implementing story. Not a data incident.
- **Any value that may belong to a real person or business:** stop. It is a **data incident**, escalated to the Platform Owner, who decides on
  notification and on early destruction of backups. Nothing further is deleted before that decision, except the broker purge of Decision 7.

### 10. Test data

**Proposed decision:** Tests, fixtures, examples, OpenAPI `@Schema` examples and documents use obviously fake RESTRICTED values only, never real or
realistic-looking ones: `000-00-1234` (SSN shape; area 000 is never issued), `000000000RT0001` (BN shape), `FAKE1234`, and `FAKE-123` (seven alphanumerics,
so `last4` is `null`). Assertions compare against constants and never print the value in a failure message.

---

## Alternatives Considered

The four options weighed in louisburroughs/durion-positivity-backend#2617, and two variants of the migration:

1. **Allow as is (option 1).**
   - **ALT-001: Description:** keep full numbers on the fact and in copies, documented as an accepted risk, relying on topic ACLs, at-rest encryption and
     log redaction.
   - **ALT-002: Rejection reason:** it breaks ADR-0044 R3 for S24's copy; a shared topic makes ACLs ineffective; the never-purged outbox and verbatim replay
     give the value an unbounded lifetime; the DLQ holds it 30 days and the runbook prints it; and an accepted risk needs a classification to be accepted
     against, which did not exist.
2. **Classify and restrict at entry, alone (option 2).**
   - **ALT-003: Description:** refuse personal-id schemes (SSN, SIN, ITIN), or keep them in a protected field that never goes on the fact, while business
     numbers travel.
   - **ALT-004: Rejection reason:** refusing them pushes 1099 and T4A payee data into spreadsheets, and classifying by scheme fails open because a personal
     number filed under a business scheme cannot be detected. Its at-rest half is kept: Decisions 3 and 4 protect every number in the owner.
3. **Minimise the fact (option 3). Chosen.**
   - **ALT-005: Description:** the fact carries scheme, region and `last4`; the full number stays with the owner, readable by permission with an audit.
   - **ALT-006: Why chosen:** it meets every known consumer and removes the value from the topic, the DLQ, the outbox, every replica and every consumer log
     at once, even when a number is misclassified.
4. **Field-level encryption on the fact and in the copies (option 4).**
   - **ALT-007: Description:** encrypt the number on the fact and in each replica, with keys from the secret store.
   - **ALT-008: Rejection reason:** every consuming service needs the key, so the clear value still reaches every consumer, and none needs it; replay is
     tied to key rotation, because every key that sealed a never-purged outbox row must live as long as the row; and five services gain key distribution to
     move data nobody reads.
5. **A `.v2` topic with dual-publishing, per ADR-0044 §3 as written.**
   - **ALT-009: Description:** publish the minimised fact on `supplier.events.v2` and keep v1 until consumers move.
   - **ALT-010: Rejection reason:** v1 keeps the number on the topic for the whole window, the topic's ~14 other event types would have to move or split,
     and the window protects nobody: no consumer reads the field.
6. **Database-level encryption only, in the owner.**
   - **ALT-011: Description:** rely on disk or database encryption at rest instead of field-level encryption.
   - **ALT-012: Rejection reason:** it does not protect against SQL readers, `pg_dump` backups, support sessions or a restored copy (Security ruling 3).

---

## Consequences

### Positive ✅

- **POS-001:** A personal identifier recorded under any scheme never reaches Kafka, a DLQ, a replica or a log, so the protection holds even when the
  scheme is wrong.
- **POS-002:** Authors of new facts and replicas get one rule to apply, instead of a case-by-case ruling per field.
- **POS-003:** Consumers need no keys; key distribution stays inside the one owning service, and replay never depends on key rotation.
- **POS-004:** Every view of a full value is attributable: one permission, one reason, one audit row written before the value is returned.
- **POS-005:** A live fact can shed a RESTRICTED field in one release, without a topic migration or a window in which the value stays published.

### Negative ⚠️

- **NEG-001:** Each owner of RESTRICTED data builds and operates field-level encryption: a per-purpose key in the secret store, a Java backfill migration
  and a fail-closed startup check, which adds one more secret per environment.
- **NEG-002:** A consumer that later needs a full value cannot copy it; it must ask the owner through a reveal-style call designed when the need arrives,
  which ADR-0044 R1 constrains for domain modules.
- **NEG-003:** The in-place withdrawal of Decision 7 weakens ADR-0044 §3's additive-only guarantee. It is safe only while its preconditions are checked by
  people at review time.
- **NEG-004:** The contract guard matches names only. A RESTRICTED value under an innocuous name passes it, and a legitimate field named `number` needs an
  allowlist entry with a sign-off.
- **NEG-005:** Operators lose the convenience of browsing DLQ values; diagnosing a failure takes one record at a time.

### Neutral

- Backups that already hold clear values are not rewritten; they age out under their retention unless Decision 9 declares an incident.
- ADR-0050 §7's classification-driven redaction of the exchange audit now refers to these classes; its retention and capture levels are unchanged.
- ADR-0046's log levels are unchanged; this ADR adds a rule about what a line may contain, not at which level it is written.

---

## Implementation Notes

- **IMP-001: First application.** louisburroughs/durion-positivity-backend#2621 minimises `supplier.vendor.updated` at `schemaVersion` 2, encrypts and
  masks registrations in pos-supplier, adds `supplier:vendor_tax_id:reveal` and its audit, scrubs the outbox, adds the contract guard and changes the
  runbook. It may be implemented now; it merges only after this ADR is ACCEPTED.
- **IMP-002: Order.** #2621 merges before S24 (#2517), whose copy and `schemaVersion >= 2` consumer rule the Accounting Domain amends from the Security
  ruling. #2615 needs a wording change only (`payeeTinLast4` reads the copy's `last4`). The vendor pages (louisburroughs/durion-positivity-frontend#469)
  show `last4` and the reveal action. The closed scheme vocabulary is a later pos-tax stub.
- **IMP-003: Migration.** The Java backfill (Decision 3), then the SQL outbox scrub (Decision 7), at the next free pos-supplier Flyway versions; the
  counts-only check before both (Decision 9); the broker and DLQ purge after deploy, with the offsets on the pull request.
- **IMP-004: Contract chain.** Controller, DTO, event and permission changes run **API Artifacts Sync** after the push, as for any OpenAPI or permission
  change.
- **IMP-005: Existing facts.** On `main` the guard's only match is the field #2621 removes; no other event field is known to be RESTRICTED. A field found
  later is withdrawn under Decision 7.

### Compliance checks

- **CHK-001:** `DomainEventContractTest#noRestrictedFieldNames` passes, and a test-only record with a nested `List<Inner>` whose `Inner` has `taxId` makes it
  fail. Every allowlist entry has a reason and a Security sign-off reference.
- **CHK-002:** For each RESTRICTED field: the stored column holds neither the value nor its separator-free form; the cipher's tests cover the envelope,
  the AAD binding, the key-policy matrix (`{}`, `{alpha}`, `{prod,dev}`, `{unknown}` fail; `{dev}`, `{test}` start with a WARN) and rotation; the
  purpose's key is never read by another cipher.
- **CHK-003:** A log capture (ListAppender at DEBUG) across create, update, reveal, replay and outbox publish never contains the fake value or its `last4`.
- **CHK-004:** The reveal permission is in all three catalogs at one bit and one `CATALOG_VERSION`; `RolePermissionBaselineTest` passes; a mutation that
  moves the audit insert after the return, or into `REQUIRES_NEW`, fails a test.
- **CHK-005:** Each replica of an affected fact has no column for the RESTRICTED value, and feeding it an older-version fact proves the skip and the value's
  absence from every table.
- **CHK-006:** The runbook's DLQ section shows the metadata-only command and the single-record rule, and no command in it prints values with
  `--from-beginning`.
- **CHK-007:** A pull request that withdraws a field in place records the consumer search, the counts-only check and the purge offsets.
- **CHK-008:** A pull request that adds a payload or replica field which can hold a CONFIDENTIAL or RESTRICTED value states its class in the description.

### Changes required in other ADRs

Applied as dated amendments when this ADR is accepted; until then each carries a one-line note that the amendment is pending.

- **ADR-0044 §3:** the in-place withdrawal of a RESTRICTED field (Decision 7).
- **ADR-0070 Decision 7:** the vendor fact carries tax registrations as `{scheme, region, last4}` and no full number (Decision 8).

No change: ADR-0046 (log levels), ADR-0050 §7 (its cipher is reused), ADR-0062 (tenant isolation), ADR-0071 (the tenant's own tax registrations, owned by
pos-tax, are not vendor data; their numbers are RESTRICTED under Decision 1 when a fact carries them).

---

## References

- **REF-001: Related issues:** louisburroughs/durion-positivity-backend#2617 (Chief Architect analysis, comment 6062021008; Security ruling, comment
  6062214186), #2621 (first application), #2517 (S24), #2615, #2523 (S32); louisburroughs/durion#550 (CAP:550)
- **REF-002: Related ADRs:** [ADR-0018](0018-audit-actor-fields-from-security-context.adr.md),
  [ADR-0022](0022-audit-stable-person-identifier-claim-policy.adr.md), [ADR-0044](0044-platform-event-only-domain-walls.adr.md),
  [ADR-0046](0046-environment-log-level-policy.adr.md), [ADR-0050](0050-supplier-vendor-profile-configuration.adr.md),
  [ADR-0062](0062-postgres-row-level-multitenancy.adr.md), [ADR-0065](0065-frontend-untrusted-content-and-browser-persistence-policy.adr.md),
  [ADR-0070](0070-bill-intake-ownership-and-vendor-master.adr.md), [ADR-0071](0071-tax-per-tenant-pluggable-providers.adr.md)
- **REF-003: Security domain decisions:** DECISION-INVENTORY-001 (deny by default), -002 (permission key format), -013 (curated by default; raw payloads
  only behind a permission, redacted), in the security [AGENT_GUIDE.md](../../domains/security/.business-rules/AGENT_GUIDE.md); AUD-SEC-004
  (`audit:payload:view`), in the audit [AGENT_GUIDE.md](../../domains/audit/.business-rules/AGENT_GUIDE.md)
- **REF-004: Related documentation:** [SPEC-accounting-workspace.md](../../domains/accounting/SPEC-accounting-workspace.md) §4.9 (AW23), AW48, OI-14;
  `durion-positivity-backend/docs/OPERATIONS_RUNBOOK.md` ("Consumers: retry and DLQ"); `pos-supplier` `AuditPayloadCipher`;
  `pos-domain-events` `DomainEventContractTest`

---

## Sign-Off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Chief Architect | Chief Architect | 2026-10-08 | Analysis on #2617 (comment 6062021008): option 3 plus option 2's at-rest controls |
| Security & Authorization Domain | Security & Authorization Domain Agent | 2026-10-08 | Ruling on #2617 (comment 6062214186), rulings 1-9; endorses this ADR |
| Platform Owner | Louis Burroughs | Pending | Approved drafting; reviews before acceptance |

---

## Timeline

- **Proposed**: 2026-10-08
- **Accepted**: pending Platform Owner review

---

## Changelog

- **2026-10-08**: Initial draft from the Chief Architect analysis and the Security ruling on louisburroughs/durion-positivity-backend#2617; pending notes
  added to ADR-0044 §3 and ADR-0070 Decision 7.
