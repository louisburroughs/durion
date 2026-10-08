---
type: ADR
title: 'ADR-0072: Data Classification and Minimisation for Event Payloads, Replicas, Logs and DLQs'
description: Four data classes; RESTRICTED values stay encrypted in their owner, revealed only by permission with audit, and only masked forms such as last4 travel.
status: stable
adr_status: accepted
created: '2026-10-08'
related: [ADR-0018, ADR-0022, ADR-0044, ADR-0046, ADR-0050, ADR-0062, ADR-0065, ADR-0070, ADR-0071]
tags: [adr, events, platform, security, supplier]
---
# ADR-0072: Data Classification and Minimisation for Event Payloads, Replicas, Logs and DLQs

**Status:** ACCEPTED **Date:** 2026-10-08 (accepted 2026-10-08) **Deciders:** Platform Owner, Chief Architect, Security & Authorization Domain
**Affected Issues:** louisburroughs/durion-positivity-backend#2617 (analysis and ruling), louisburroughs/durion-positivity-backend#2621 (first application),
louisburroughs/durion-positivity-backend#2517 (S24), louisburroughs/durion#550 (CAP:550)

> **How to read this.** ✅ **Resolved** marks a decision this ADR makes. It writes down, as platform policy, the Chief Architect analysis of
> louisburroughs/durion-positivity-backend#2617 (comment 6062021008) and the Security & Authorization Domain's ruling on the same issue (comment
> 6062214186, rulings 1-9), both dated 2026-10-08. The Platform Owner's review added five implementation considerations (IC-001 to IC-005), which
> acceptance folds into Decisions 4, 5, 7 and 9. Its two amendments, to [ADR-0044](0044-platform-event-only-domain-walls.adr.md) §3 and
> [ADR-0070](0070-bill-intake-ownership-and-vendor-master.adr.md) Decision 7, are in force, so #2621 may merge once its own checks pass.

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

✅ **Resolved:** Every field the platform stores, publishes, copies or logs belongs to one of four classes. The class decides where the value may
appear.

| Class | What it covers | Where it may appear |
| --- | --- | --- |
| **RESTRICTED** | Personal government identifiers; **every tax-registration number held where a personal identifier could be entered, whatever its scheme**; bank-account and card data; credentials and secrets | Only in the owner's store, encrypted at field level (Decision 3), and in a reveal response (Decision 4). Elsewhere only as a masked derivative (Decision 2) |
| **CONFIDENTIAL** | Names, postal addresses and e-mail addresses of natural persons; masked derivatives such as `last4` | On a fact, in a replica where ADR-0044 R3 needs them, and in API responses. Never logged at INFO or above, never a metric tag; a masked derivative is never logged at all |
| **INTERNAL** | Everything else: identifiers, statuses, amounts, dates, business references, configuration | Anywhere the module's own rules allow, at the log levels of ADR-0046 |
| **PUBLIC** | Data the platform publishes outside the tenant on purpose, such as published API reference material | Anywhere, subject to the serving endpoint's authorization and tenant isolation. A value is PUBLIC only when an ACCEPTED ADR or a recorded Security ruling names the field, and never when the field can hold a CONFIDENTIAL or RESTRICTED value |

- **RESTRICTED in detail:**
  - government-issued identifiers of natural persons: SSN, SIN, ITIN and any other personal taxpayer id;
  - every tax-registration number that is not a published indirect-tax registration (below), business numbers included (EIN, BN, VAT); in a store that
    accepts any personal-id scheme (pos-supplier's vendor registrations), every registration in it, whatever its scheme;
  - bank-account and card data: account and routing numbers, IBAN, card number, CVV (OI-14 is the precedent); the platform stores no card number (PAN):
    payments use the processor's token, and storing a PAN needs its own ADR (PCI DSS scope);
  - credentials and secrets: passwords, API keys, tokens and private keys.
- **Recoverable and non-recoverable RESTRICTED values.** Decisions 3 and 4 (encryption and reveal) apply only to values the owner must be able to read
  back: identifiers, registration numbers and bank-account data. A value the platform must never recover is never encrypted for later reading and never
  revealable: a CVV is not stored after authorisation (nor any other sensitive authentication data), a password is stored only as a one-way verifier
  (an adaptive hash), and other credentials stay secret-store references (ADR-0050 §4). Decision 2 applies to both kinds.
- **Published indirect-tax registrations.** A tax-registration number is INTERNAL only when all three hold, whoever holds it (the tenant or a
  counterparty such as a supplier):
  (a) its field accepts nothing but the configured shape of one indirect-tax regime (for example GST/HST `#########RT####`, QST `##########TQ####`),
  and entry refuses any other value without echo;
  (b) no configured shape can match a value of exactly nine digits once separators are removed;
  (c) the holder must show the number on the documents it issues.

  The configured shapes are platform configuration, shipped with the service and changed only by a reviewed commit. They are never set per tenant, by a
  tenant or by a tax provider. A shape that could be changed at runtime fails (a).

  A number that fails any condition is RESTRICTED, whoever holds it. The tenant's own registrations under ADR-0071 §7 meet all three (S31 limits them to
  `GST_HST` and `QST` and checks their shape), so `tax.registration.changed` and the `ext_tax_registration` replicas may carry them. They are INTERNAL,
  not PUBLIC. A supplier's GST/HST number recorded through the drawer's shape-checked field (S32) meets all three in the same way. The same supplier's
  numbers in pos-supplier's vendor registrations stay RESTRICTED, because that store accepts personal-ID schemes. A regime that fails (b) needs an
  ADR-0071 amendment reviewed by Security. (Security confirmation on PR #571, 2026-10-08.)
- **CONFIDENTIAL in detail:** names, postal addresses and e-mail addresses of natural persons, sole-proprietor vendors included, and the masked derivatives
  of RESTRICTED values (`last4`).

- **Classify by what a field can hold, not by its label.** A field that can hold a RESTRICTED value is RESTRICTED, and nothing branches on a scheme, a
  type code or any other label to lower the class.
- **The owner classifies.** The module that owns a fact (ADR-0044 R6) classifies each field when it adds the field. An unclassified field is INTERNAL only
  when it cannot hold anything higher; when in doubt, the higher class applies.
- The Security ruling set RESTRICTED, CONFIDENTIAL and INTERNAL as working classes. PUBLIC completes the scale and is assigned only by decision, so it
  loosens nothing that the ruling restricted. The decision that assigns PUBLIC never lifts authorization (DECISION-INVENTORY-001) or tenant isolation
  (ADR-0062) unless it says so.

### 2. RESTRICTED values never leave their owner

✅ **Resolved:** A RESTRICTED value never appears:

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
- **Attributes beside a derivative cannot hold the value.** A non-restricted attribute that travels next to a derivative (such as a registration's
  `scheme` or `region`) is INTERNAL only when its accepted shape cannot hold a RESTRICTED value. Until a closed vocabulary exists, the owner enforces a
  shape: for registrations, `scheme`, trimmed and upper-cased, matches `^[A-Z][A-Z _/-]{0,15}$` (letters, spaces, `_`, `/` and `-`; no digit; at most
  16); `region`, trimmed and upper-cased, is an ISO 3166-2 code or subdivision part with letters only (`^[A-Z]{2}(-[A-Z]{1,3})?$`); a subdivision with
  digits waits for a closed vocabulary that lists it. A value outside the shape is refused (400 `VALIDATION_ERROR`) without echoing it. A closed
  vocabulary's codes and regions must themselves match these shapes.

Owners **accept** the RESTRICTED values they legitimately need, personal identifiers included (Security ruling 5), and protect them under Decisions 3 and 4,
rather than refusing them.

### 3. At rest: field-level encryption under a per-purpose key

✅ **Resolved:** The owning service encrypts each RESTRICTED value at field level. Database or disk encryption alone is not enough, because it does
not protect against SQL readers, `pg_dump` backups, support sessions or a restored copy.

- **Envelope:** the `AuditPayloadCipher` pattern from pos-supplier (ADR-0050 §7): AES-256-GCM, a header `version | keyIdLen | keyId` used as AAD, a random
  96-bit nonce, and decrypt-only previous keys for rotation. Extracting the shared envelope and key-policy code is preferred to copying it.
- **One key per purpose.** A key protects one kind of data and is never reused for another: data with different lifetimes (a 400-day exchange audit, a
  number that lives as long as the vendor) needs keys that rotate independently. Properties follow
  `pos.<module>.<purpose>.encryption.key` / `.key-id` / `.previous-keys`, bound from an environment variable provisioned from the secret store, never from
  a committed file.
- **Bind each ciphertext to its row.** The AAD also carries the tenant id, the aggregate id and the element id, so a ciphertext copied into another row,
  aggregate or tenant fails authentication.
- **Fail-closed key policy.** Startup fails when the key is missing unless at least one profile is active and every active profile is `dev` or `test`;
  only then are an ephemeral key and one WARN allowed. No active profile (the default) fails like any other. The failure message names the
  environment variable and never prints key material.
- **Masked reads never decrypt.** The derivative is stored next to the ciphertext and read from there.
- **Migrating existing data.** A SQL migration cannot hold the key, so clear values already stored are encrypted by a **Flyway Java migration**
  (a `JavaMigration` registered as a Spring bean, which Spring Boot hands to Flyway) with the cipher injected. It is idempotent, honours row-level security
  the way the module's other migrations do (ADR-0062), and logs counts only. When it finishes, no stored row holds a clear value.
- **Backups are not rewritten.** They age out under their normal retention, unless Decision 9 makes the case a data incident.

### 4. The reveal pattern

✅ **Resolved:** A person sees a full RESTRICTED value only through a dedicated, audited reveal call.

- **Masked by default.** Every read, list and write response returns the masked shape (for an identifier `{elementId, ..., last4}`), never the value.
- **Dedicated permission `<domain>:<resource>:reveal`.** The resource names the data class (as `people:employee_pii:view` does), and the action is
  `reveal`, not `view` or `read`, so ADR-0062 §7's SUPPORT ceiling (`RolePermissionBaselineTest#supportIsReadOnly`) structurally keeps it off SUPPORT. It is
  granted to the narrowest roles that need it, and registered in all three permission catalogs with one `CATALOG_VERSION` bump.
- **A POST with a reason.** `POST .../{elementId}/reveal` with body `{reason}`, trimmed, at least 10 characters (otherwise 400 `JUSTIFICATION_REQUIRED`) and
  at most 500 (otherwise 400 `VALIDATION_ERROR`). POST, because the call writes an audit row, and so that it stays out of caches and URL logs. The
  response carries the value with `Cache-Control: no-store`.
- **The reason cannot carry the value.** The reason is CONFIDENTIAL: it is never logged and appears only in the audit row and its audit read. Before the
  row is written, the service compares the reason against the stored value, case-insensitive, with every non-alphanumeric character removed from both; a
  reason that contains the value is refused (400 `VALIDATION_ERROR`, without echo) and nothing is revealed. The check runs after the permission check and
  the lookup. A refused attempt writes the audit row with outcome `REASON_REJECTED` and a null reason. The reason stays free text (Security ruling 4).
- **Audit in the same transaction, fail-closed.** Before the value is returned, the service inserts one append-only, tenant-scoped audit row in the same
  transaction: the actor from the security context (ADR-0018, ADR-0022), the actor's roles, the aggregate and element ids, the non-restricted attributes
  (such as the scheme), the reason, the correlation id, the time and the outcome. The row never holds the value or its derivative. The value is released
  only after that transaction commits: if the insert or the commit fails, the call fails and **reveals nothing**; the insert never runs in a separate
  (`REQUIRES_NEW`) transaction or after the value is returned.
- **Every audited outcome commits.** The transactional service returns an explicit outcome (`REVEALED`, `REASON_REJECTED`, `UNREADABLE`) and never throws
  an unchecked exception after writing a row, because that rolls the row back (Spring's default rollback rule). Only after commit does the controller map
  the outcome to 200, 400 or 500.
- **Refusals reveal nothing.** A 403 or 404 writes no row; a reason refused under the rule above writes a `REASON_REJECTED` row. A decryption failure
  answers 500 with a module-specific `..._UNREADABLE` code, is logged with ids and the key id only, and is audited with outcome `UNREADABLE` with a null
  reason, because the reason cannot be checked against a value that could not be read.
- **Reviewed by someone else.** Reveal rows are read through the module's audit-read permission, held where possible by roles other than the revealers.
- **Writes under masking.** Each stored element has a stable id (UUIDv7). An update that sends the id without a value keeps the stored ciphertext; changing
  an attribute that describes the value (such as its scheme or region) requires re-entering the value; an unknown id is 400 `VALIDATION_ERROR`; an omitted
  element is removed. A supplied value is always a change, even when its id and attributes are unchanged: the owner re-encrypts it, recomputes and stores
  its derivative, and publishes the masked fact. Change detection otherwise compares ids and stored attributes, and never uses ciphertext or `last4`
  equality to decide that a write changed nothing.
- **Never an agent tool.** A reveal operation, and any other operation whose response carries a RESTRICTED value, is never exposed as an MCP tool or
  called by pos-mcp-server or any other LLM-driven caller. Its value would enter a model's context, its provider and the conversation store (Decision 2),
  and its reason must be a person's own justification (ADR-0018). Such an operation is protected by a permission whose action is `reveal`; that action is
  reserved for these operations. pos-mcp-server's discovery excludes, for every HTTP method and before any include rule, an operation whose
  `x-required-permissions` holds a `…:reveal` permission or whose path ends with `/reveal`. No configuration can re-include it.
- **In the browser,** a revealed value is shown only until its dialog closes and is never cached, stored, put in a route or query string, or logged
  ([ADR-0065](0065-frontend-untrusted-content-and-browser-persistence-policy.adr.md)).

The first application is pos-supplier's `supplier:vendor_tax_id:reveal` (granted to ADMIN and CONTROLLER only), with its `supplier_vendor_tax_id_reveal`
audit table read through `supplier:audit:read` (#2621).

### 5. DLQ inspection and runbook handling

✅ **Resolved:** Operators handle DLQ and topic contents so that no value is copied beyond the operator's permitted terminal session, for every topic, not
only the supplier topics.

- **Metadata by default.** The standard inspection command prints an allowlist of safe metadata only:
  `--property print.value=false --property print.headers=false --property print.key=true --property print.partition=true --property print.offset=true
  --property print.timestamp=true`. Headers are not safe metadata: `DeadLetterPublishingRecoverer` writes the exception message and stack trace into
  dead-letter headers, and a legacy record's exception can hold payload text.
- **One record at a time.** A value, or a record's headers, may be printed only for one named record: `--partition N --offset M --max-messages 1`.
- **Permitted terminals.** Running the command through `docker exec` on the broker host does not keep its output there: a remote terminal, scrollback or
  session recording can carry or keep it. The runbook names the terminal and recording arrangements allowed for single-record inspection.
- **Never `--from-beginning`** with values printed, on a DLQ or any other topic.
- **Never paste a value anywhere.** Output stays in the operator's terminal. It is never pasted into an issue, pull request, ticket or chat, and never
  redirected to a file.
- **Consumers** never put payload text in an exception message bound for a DLQ or a log. Jackson's source inclusion in parse-error locations stays off
  (its default); a module that turns it on breaks this rule.
- A restricted inspection host is not required: the command already runs inside the broker container through `docker exec` on the host (Security ruling 7),
  within the permitted terminal arrangements above.

### 6. The contract-test guard

✅ **Resolved:** `DomainEventContractTest` in `pos-domain-events` carries a field-name guard (`noRestrictedFieldNames`), parameterised over every
event record it scans.

- It walks record components **recursively**: nested records, `List`, `Set` and `Map` element types, and arrays.
- It fails on any component whose name, compared case-insensitively, is one of: `number`, `ssn`, `sin`, `tin`, `itin`, `taxId`, `taxNumber`, `nationalId`,
  `accountNumber`, `routingNumber`, `iban`, `cardNumber`, `pan`, `cvv`, `password`, `secret`. It also fails on any component whose name ends with
  `registrationNumber`, compared case-insensitively. A field allowed under Decision 1's published indirect-tax registrations takes an allowlist entry
  citing that rule.
- **Allowlist.** Exceptions sit in a map inside the test, each entry with a written reason and a Security sign-off reference. It **starts empty**: on `main`
  the only match is `SupplierVendorUpdatedV1.TaxRegistration.number`, which #2621 removes.
- The guard is a backstop for names, not proof of classification. Classifying each field remains the owner's job (Decision 1). Annotation-based
  classification of payload fields is not decided here and needs its own amendment.

### 7. Amendment to ADR-0044 §3: withdrawing a RESTRICTED field in place

✅ **Resolved:** ADR-0044 §3 keeps its rule that payload changes within a version are additive-only, with one exception. A **RESTRICTED** field may be
withdrawn from a live payload **in place**, on the same `eventType` and topic, with **no `.v2` topic and no dual-publish**, only when all of the following
hold:

1. **No consumer reads the field.** The pull request records the search of every consumer of the event type that shows it.
2. **`schemaVersion` is bumped** on the envelope; the record keeps its name (the `ProductUpdatedV1` precedent), and its Javadoc cites this amendment.
3. **The owner's outbox is scrubbed in the same release.** A migration rewrites every stored payload of that event type to the new shape and version, and
   clears `last_error` on those rows, so verbatim outbox replay cannot publish the old value again. Rows of other event types are untouched.
4. **Consumers apply only the new version.** A consumer marks an older-version event of that type processed, counts it (metric tags `eventType` and
   `schemaVersion` only), skips it and never logs its payload. Replicas are seeded from the owner's replay (ADR-0044 §4 bootstrap).

**Rollout and purge sequence.** No old publisher or writer may recreate a clear payload after the scrub:

1. Deploy consumers that accept the new `schemaVersion` before anything publishes it.
2. Stop every old writer and publisher of the event type.
3. Run the backfill (Decision 3) and the outbox scrub, then start the new publisher.
4. Capture a **fixed** cutoff offset per partition of the topic and its DLQ, taken after the last possible old-format publication. A moving high-water mark
   is never the deletion boundary.
5. Verify that every consumer group has committed past the cutoffs. Zero lag on the topic says nothing about the DLQ, so inventory the event type's DLQ
   records separately and record, for each, its recovery by a sanitised replay or its approved disposition.
6. Delete records up to the cutoffs with `kafka-delete-records.sh`. Other event types in that range stay replayable from the outbox.

The cutoffs and the DLQ recovery evidence go on the pull request. Dual-publishing is ruled out because it would keep the RESTRICTED value on the topic for the whole migration window. A field that a
consumer does read is outside this exception and needs its own amendment, reviewed by Security.

### 8. Amendment to ADR-0070 Decision 7: the vendor fact carries masked registrations

✅ **Resolved:** `supplier.vendor.updated` carries every vendor field **except bank details and full tax-registration numbers**. Tax registrations
travel as `{scheme, region, last4}` at `schemaVersion` 2 (Decision 7 above); a registration on the list means its number is on file. The full number stays
in pos-supplier, encrypted (Decision 3) and revealed only through `supplier:vendor_tax_id:reveal` (Decision 4). No `ext_supplier_vendor` copy holds a full
number: pos-accounting's copy holds `tax_registrations` as `[{scheme, region, last4}]`, and the pos-order and pos-inventory copies hold no registrations.

### 9. Incident rule

✅ **Resolved:** Before any scrub or purge of a RESTRICTED value, the implementer runs a **read-only, counts-only** check in every environment that
holds data (alpha: through SSM, as `pos_user`): the values grouped by their non-restricted attributes, and the number of aggregates holding at least one.
The output is counts, never values, and goes on the pull request.

- **Group only by validated attributes.** An attribute that predates its shape rule (Decision 2) may itself hold a RESTRICTED value, so its raw content is
  never printed: a legacy value that fails the shape counts in one fixed bucket (for registrations, `scheme` outside the shape counts as `UNVALIDATED`).
- **Provenance, not appearance.** Counts cannot show that an entry is synthetic. Entries whose fixture provenance is verified (for example, seeded by a
  named test or migration with a fake value from Decision 10) are counted apart from entries of unknown provenance, and the evidence for that provenance
  goes on the pull request with the counts. Unknown provenance is treated as potentially real.
- **Zero, or entries of verified fixture provenance only:** a pre-production security defect, closed by the implementing story. Not a data incident.
- **Any value that may belong to a real person or business:** stop. It is a **data incident**, escalated to the Platform Owner, who decides on
  notification and on early destruction of backups. Nothing further is deleted before that decision, except the broker purge of Decision 7.

### 10. Test data

✅ **Resolved:** Tests, fixtures, examples, OpenAPI `@Schema` examples and documents use obviously fake RESTRICTED values only, never real or
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
- **NEG-006:** An agent cannot reveal a value even for a user who holds the permission; the person uses the reveal dialog.

### Neutral

- Backups that already hold clear values are not rewritten; they age out under their retention unless Decision 9 declares an incident.
- ADR-0050 §7's classification-driven redaction of the exchange audit now refers to these classes; its retention and capture levels are unchanged.
- ADR-0046's log levels are unchanged; this ADR adds a rule about what a line may contain, not at which level it is written.

---

## Implementation Notes

- **IMP-001: First application.** louisburroughs/durion-positivity-backend#2621 minimises `supplier.vendor.updated` at `schemaVersion` 2, encrypts and
  masks registrations in pos-supplier, adds `supplier:vendor_tax_id:reveal` and its audit, scrubs the outbox, adds the contract guard and changes the
  runbook. This ADR is ACCEPTED, so it merges once its own checks pass.
- **IMP-002: Order.** #2621 merges before S24 (#2517), whose copy and `schemaVersion >= 2` consumer rule the Accounting Domain amends from the Security
  ruling. #2615 needs a wording change only (`payeeTinLast4` reads the copy's `last4`). The vendor pages (louisburroughs/durion-positivity-frontend#469)
  show `last4` and the reveal action. The closed scheme vocabulary is a later pos-tax stub.
- **IMP-003: Migration.** The Java backfill (Decision 3), then the SQL outbox scrub (Decision 7), at the next free pos-supplier Flyway versions; the
  counts-only check before both (Decision 9); the broker and DLQ purge after deploy, in Decision 7's sequence, with the fixed cutoffs and the DLQ recovery
  evidence on the pull request.
- **IMP-004: Contract chain.** Controller, DTO, event and permission changes run **API Artifacts Sync** after the push, as for any OpenAPI or permission
  change.
- **IMP-005: Existing facts.** On `main` the guard's only match is the field #2621 removes; no other event field is known to be RESTRICTED. A field found
  later is withdrawn under Decision 7.

### Implementation considerations

The Platform Owner's review raised these considerations. On acceptance each is folded into the decision it names, which is normative; they stay here as
the reasoning behind those rules.

- **IC-001: Safe incident assessment (Decision 9).** Counts grouped by scheme do not establish whether entries are synthetic, and legacy schemes accept
  arbitrary text that may itself contain a RESTRICTED value. Count entries with verified fixture provenance separately from entries with unknown
  provenance; treat unknown provenance as potentially real under Decision 9. Group only by validated, non-restricted attributes, using a fixed bucket
  for unvalidated legacy attributes rather than printing their raw contents. Record the evidence used to establish fixture provenance with the counts.
- **IC-002: Deployment and purge sequence (Decision 7).** Define a sequence that prevents an old publisher or writer from recreating clear outbox
  payloads after the scrub. Ensure consumers support the new schema before publication resumes, stop all old writers and publishers, complete the
  backfill and outbox scrub, and capture fixed per-partition cutoffs after the last possible old-format publication. Verify consumer progress through
  those cutoffs before purging. Zero lag on the source topic does not establish that DLQ failures have been recovered: separately inventory affected
  DLQ records and document their recovery through sanitised replay, or their approved disposition, before deleting them. Record cutoffs and recovery
  evidence on the pull request; do not use a moving high-water mark as the deletion boundary.
- **IC-003: Durable reveal outcomes (Decision 4).** Inserting an audit row before throwing an unchecked exception from the same transaction can roll
  back the row for `REASON_REJECTED` or `UNREADABLE`. Prefer returning an explicit outcome from the transactional service, committing the audit row,
  and only then mapping the outcome to HTTP 400 or 500 in the controller. Successful reveals likewise release the value only after commit succeeds;
  an audit insert or commit failure releases nothing. Verify audit persistence after the complete request for both error outcomes, as well as the
  successful reveal and audit-failure cases. See [Spring's transaction rollback rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html).
- **IC-004: Replacement-value change detection (Decision 4).** A supplied replacement number is a mutation even when its element id, scheme and
  region are unchanged. Recompute and store its derivative and publish the resulting masked fact; do not use ciphertext equality, or equality of
  `last4`, to decide that the write is unchanged. Cover replacements with different `last4` values and replacements sharing the same `last4`, alongside
  the existing id-without-value rule that preserves the stored ciphertext.
- **IC-005: Safe inspection metadata and terminal handling (Decision 5).** Printing all headers is not necessarily safe metadata inspection:
  `DeadLetterPublishingRecoverer` adds exception messages and stack traces to dead-letter headers. Prefer an allowlist of safe metadata fields and
  suppress arbitrary headers, especially for legacy records whose exceptions may contain payload text. Running the command through `docker exec`
  does not establish that its output stays on the broker host: remote terminals and session recording can carry or retain that output. Define the
  permitted terminal and recording arrangements for single-record value inspection in the runbook. See [Spring's dead-letter header documentation](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#dead-letters).

### Compliance checks

- **CHK-001:** `DomainEventContractTest#noRestrictedFieldNames` passes, and a test-only record with a nested `List<Inner>` whose `Inner` has `taxId` makes it
  fail, and a test-only record with a component `supplierRegistrationNumber` makes it fail. Every allowlist entry has a reason and a Security sign-off reference.
- **CHK-002:** For each RESTRICTED field: the stored column holds neither the value nor its separator-free form; the cipher's tests cover the envelope,
  the AAD binding, the key-policy matrix (`{}`, `{alpha}`, `{prod,dev}`, `{unknown}` fail; `{dev}`, `{test}` start with a WARN) and rotation; the
  purpose's key is never read by another cipher.
- **CHK-003:** A log capture (ListAppender at DEBUG) across create, update, reveal, replay and outbox publish never contains the fake value or its `last4`.
- **CHK-004:** The reveal permission is in all three catalogs at one bit and one `CATALOG_VERSION`; `RolePermissionBaselineTest` passes; a mutation that
  moves the audit insert after the return, or into `REQUIRES_NEW`, fails a test. Tests over the complete request (through the controller and the commit)
  prove the audit row persists for `REVEALED`, `REASON_REJECTED` and `UNREADABLE`, and that an audit-insert or commit failure releases no value.
- **CHK-005:** Each replica of an affected fact has no column for the RESTRICTED value, and feeding it an older-version fact proves the skip and the value's
  absence from every table.
- **CHK-006:** The runbook's DLQ section shows the metadata-only command with `print.headers=false`, the single-record rule and the permitted terminal
  arrangements, and no command in it prints values or headers with `--from-beginning`.
- **CHK-007:** A pull request that withdraws a field in place records the consumer search, the counts-only check with its provenance evidence, the fixed
  per-partition cutoffs and the DLQ recovery evidence.
- **CHK-008:** A pull request that adds a payload or replica field which can hold a CONFIDENTIAL or RESTRICTED value states its class in the description.
- **CHK-009:** A reveal whose reason contains the stored value is refused with nothing revealed and no echo, and the refusal writes one
  `REASON_REJECTED` row with a null reason; a `scheme` or `region` containing a digit, or a `region` outside the ISO 3166-2 shape, is refused without
  echo.
- **CHK-010:** pos-mcp-server's discovery tests prove the exclusion over the real checked-in `openapi.yaml` of every module (the
  `DiscoveryAuditWriteExclusionRealSpecsTest` pattern): no discovered tool has a path ending `/reveal` or a `…:reveal` entry in `x-required-permissions`.
  Synthetic operations carrying only the permission marker, only the path marker, or an include rule that matches them are each excluded. A second test
  over the same specs fails when an operation has one marker without the other. Removing the exclusion, matching only one marker, or moving it into
  configuration fails CHK-010.
- **CHK-011:** An update that sends a replacement value under an existing element id, with the same scheme and region, stores a new ciphertext and
  `last4` and publishes the masked fact, both when the new `last4` differs and when it is the same; an id sent without a value keeps the stored ciphertext.

### Changes required in other ADRs

Applied as dated amendments on acceptance (2026-10-08).

- **ADR-0044 §3:** the in-place withdrawal of a RESTRICTED field (Decision 7).
- **ADR-0070 Decision 7:** the vendor fact carries tax registrations as `{scheme, region, last4}` and no full number (Decision 8).

No change: ADR-0046 (log levels), ADR-0050 §7 (its cipher is reused), ADR-0062 (tenant isolation). ADR-0071 is unchanged: its registrations meet
Decision 1's published indirect-tax conditions (Security confirmation on PR #571).

The accounting specification ([SPEC-accounting-workspace.md](../../domains/accounting/SPEC-accounting-workspace.md) §4.9 "Vendors (AW23)") states the
masked vendor fact and accounting's `[{scheme, region, last4}]` copy.

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
| Security & Authorization Domain | Security & Authorization Domain Agent | 2026-10-08 | Ruling on #2617 (comment 6062214186), rulings 1-9; endorses this ADR; departures confirmed on PR #571 (Security confirmation, 2026-10-08) |
| Platform Owner | Louis Burroughs | 2026-10-08 | Accepted, with implementation considerations IC-001 to IC-005 folded into Decisions 4, 5, 7 and 9 |

---

## Timeline

- **Proposed**: 2026-10-08
- **Accepted**: 2026-10-08

---

## Changelog

- **2026-10-08**: Added implementation considerations for safe incident assessment, deployment and purge sequencing, durable reveal audit outcomes,
  replacement-value change detection, and safe DLQ metadata and terminal handling.
- **2026-10-08**: Initial draft from the Chief Architect analysis and the Security ruling on louisburroughs/durion-positivity-backend#2617; pending notes
  added to ADR-0044 §3 and ADR-0070 Decision 7.
- **2026-10-08**: Review fixes (PR louisburroughs/durion#571): non-recoverable secrets are never encrypted for reading or revealable; the tenant's own
  registration numbers are INTERNAL (ADR-0071 unchanged, Security to confirm); attributes beside a derivative get a shape that cannot hold the value; a
  reveal reason may not contain the value; an empty profile set fails the key policy; a pending note in the accounting specification.
- **2026-10-08**: Security confirmation on PR louisburroughs/durion#571 (comment 6062991022) applied: a registration number is INTERNAL only as a
  published indirect-tax registration (conditions (a)-(c)), otherwise RESTRICTED whoever holds it, so ADR-0071 stays unchanged; PUBLIC's three limits;
  no PAN stored and sensitive authentication data never kept; `scheme` allows `_` and `region` allows letters only; a reason containing the value writes
  a `REASON_REJECTED` audit row with a null reason, and the reason stays free text; the guard also fails on names ending with `registrationNumber`
  (CHK-001, CHK-009). Status stays PROPOSED.
- **2026-10-08**: Security decision on PR #571: a shape-checked indirect-tax number is INTERNAL whoever holds it, counterparties included; shapes are
  platform configuration only.
- **2026-10-08**: Security ruling on louisburroughs/durion-positivity-backend#2621 (comment 6064490514): Decision 4 gains "Never an agent tool" (a
  reveal or any operation returning a RESTRICTED value is never an MCP tool; two-marker exclusion in pos-mcp-server's discovery; the `reveal` action is
  reserved), CHK-010 and NEG-006; an `UNREADABLE` audit row has a null reason. Status stays PROPOSED.
- **2026-10-08**: Accepted by the Platform Owner. Implementation considerations IC-001 to IC-005 folded into the decisions: Decision 4 releases a value
  only after commit, returns every audited outcome instead of throwing, and treats a supplied replacement value as a change; Decision 5 prints no headers
  by default and names permitted terminals; Decision 7 gains a rollout and purge sequence with fixed cutoffs and a separate DLQ inventory; Decision 9
  groups only by validated attributes and separates verified fixture provenance. CHK-004, CHK-006 and CHK-007 extended; CHK-011 added. Amendments to
  ADR-0044 §3 and ADR-0070 Decision 7 applied.
