## CAP-318 WAVE STATE — complete 2026-08-14

Companion to `docs/capabilities/CAP-317/WAVE_PAUSE_STATE.md`. Same purpose: the decisions an agent
picking this up later must not re-litigate, and the failures that were nearly shipped. Program-level
status lives in `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §11.

Capability: **CAP-318** — [durion#373](https://github.com/louisburroughs/durion/issues/373), Michelin
Price Catalog (B4.0) sync. Governing ADRs: 0044, 0049, 0051, 0052, 0053.

### Where the wave ended

| Slice | Story | PR | Status |
| --- | --- | --- | --- |
| 1 | #1232 EAN/UPC uniqueness + exact-match lookup (pos-catalog) | #1304 | **COMPLETE** |
| 2 | #1224 PRICAT B4.0 sync, staging, quarantine, outbox (pos-supplier) | #1304 | **COMPLETE** |
| 3 | #1308 apply PRICAT events into append-only price entries (pos-catalog) | #1311 | **COMPLETE** |
| 4 | #1309 product-fact replay + #1310 quarantine re-application | #1316 | **COMPLETE** |
| 5 | #1321 serve republish requests (pos-supplier) | #1322 | **COMPLETE** |

`supplier_item_cost` and its cost-tier model were **retired** by #1311 — the append-only
`supplier_price_entry` replaces them. Anything still reading the old table is reading a table that no
longer exists.

### Binding decisions — do NOT re-decide

1. **Cross-domain matching reads a replica, never the owner.** Matching PRICAT lines to products was
   first implemented as a synchronous REST call from pos-supplier to pos-catalog. `DomainWallsTest`
   rejected it (ADR-0044 R1). The permitted read is the `ext_product_code` replica, fed by
   `catalog.product.updated` and matched locally (ADR-0053 §5). This is not a performance choice and
   is not negotiable per-feature; it is why the replay path in decision 2 has to exist.

2. **A replica needs a seed path or it is empty on day one.** The replica holds only facts published
   after its consumer started, so a first deployment would match nothing and quarantine an entire
   catalogue — a failure that looks exactly like a vendor problem. `POST /v1/products/facts/replay`
   (#1309) re-emits product facts through the **ordinary** `CatalogFactPublisher`, cursor-paged by
   product id. Two constraints on it: the cursor is the id, never an offset (a product created
   mid-replay would push another out of the window), and `aggregateVersion` stays the product's
   `updatedAt` so a replay can never regress a replica holding something newer.

3. **Quarantine re-application runs on its own cadence, not the import's.** What makes a quarantined
   line matchable is a change in the *catalogue* — a product created, a code corrected, the replica
   finally seeded — which has nothing to do with when the vendor is next fetched. Tying it to the
   import schedule would strand a correction behind a weekly vendor fetch. Hourly sweep, no vendor
   call, no schedule lease (the work is idempotent by construction; a lease would add a stuck-lease
   failure mode to protect against nothing).

4. **A re-application gets its own manifest per origin import.** It never edits the healed import's
   counters: those record what the vendor sent and how much matched *at the time*, and a fourth chunk
   of a three-chunk import is not something a consumer's completeness check can accept. `reapplied_from_import_id`
   points at the import healed. One manifest **per origin**, not one per sweep — see the review
   catches below.

5. **Chunk boundaries are recorded, not recomputed.** `supplier_pricat_entry.chunk_sequence` is
   written at staging by both the import path and the re-application path (V15). Consumers dedupe
   re-emitted chunks on `(importManifestId, chunkSequence)` because a re-emit necessarily carries new
   event ids and the event-id guard cannot fire for it. Recomputing boundaries from position order
   reproduces them for an ordinary vendor fetch but **not** for a re-application manifest, which
   stages in quarantine-query order — and where a boundary moved, the consumer would skip a sequence
   it had already applied, losing the exact lines the re-emit existed to deliver.

6. **Re-publication is over-broad and bounded.** The request names the import, not the missing chunks,
   because the consumer only knows how many it is short, never which. Over-broad is safe (the consumer
   skips what it holds); under-broad leaves the gap. Bounded by a cooldown (collapses a burst) and an
   attempt cap (stops a broken consumer, loudly).

7. **One consumer group per topic per module.** `processed_events` is keyed by event id alone and every
   consumer records every event it sees — including types it ignores, so the producer's manifest
   reconciles. A second group on `supplier.commands.v1` would record ids the first still had to act on,
   dropping purchase orders or recoveries depending on which group won the race. The listener lives in
   a neutral `internal.command.service` slice and dispatches by event type.

8. **Completeness is checkable, not assumed.** pos-catalog keeps an applied-chunk log, so its counter
   counts distinct chunks rather than deliveries — it can neither be inflated by a re-emit nor fall
   short because a redelivery was skipped.

### Review catches worth remembering

Every one of these was a real defect found in review, not a style note.

- **Re-application collapsed multiple origin imports into one manifest.** A profile's quarantine spans
  every import that ever left a line open, so a batch routinely mixes them — while the writer built the
  manifest from the *first* resolved line's document identity and fetch timestamp. Lines from a second
  import would have been attributed to a vendor document that never contained them and stamped with
  another fetch's time. Because fetch time is the last tie-break in latest-selection (ADR-0053 §2),
  this reached **which price a buyer sees**, not just the audit trail.
- **4xx responses typed as the success schema.** Caught twice, on two different PRs, one wave apart.
  Any new endpoint's error responses need explicit `ApiError` content or the generated spec lies.
- **Idempotency on event id alone let re-emits duplicate price rows.** The applied-chunk log exists
  because of this finding.
- **A partial re-emit was diagnosed as a malformed command.** The republisher threw when a manifest
  declared a chunk with no staged lines, and the listener's generic handler logged it as bad input —
  blaming the producer for this module's own inconsistent data, and recording as processed a command
  whose transaction the failure had already marked rollback-only. Now checked before anything is
  queued, comparing the sequence *set* rather than a count.
- **An all-rejected stock-report snapshot was classified `EMPTY`.** "The vendor sent nothing" and "we
  rejected everything the vendor sent" are different facts.

### Patterns to watch (carried forward from CAP-317, confirmed again here)

- **Tests asserted the defect.** Recurring. A test that documents current behaviour is not evidence
  the behaviour is right.
- **Run the FULL `pos-archunit` suite in-reactor**, not a single test class. `-Dtest=ArchitectureTests`
  alone passed while `DomainWallsTest` and `EntityStandardsArchitectureTest` were failing — that is how
  the ADR-0044 R1 violation in decision 1 reached CI.
- **Flyway migrations run against H2 in this module's tests.** Postgres-only syntax (`UPDATE … FROM`,
  multi-column `ADD COLUMN`) fails there. V15 uses a correlated subquery and one `ALTER` per column.
- **Migration numbers collide across stacked PRs.** V10/V11 were reserved for #1228 while #1310 took
  V12; check `main` and every open supplier PR before choosing a number.

### Owed, not delivered

- Deploy prerequisites — [durion#389](https://github.com/louisburroughs/durion/issues/389): Kafka
  enabled in both modules, fleet-coordinated `CATALOG_VERSION` 41 → 43 (bits 448/449),
  `SUPPLIER_AUDIT_ENC_KEY` provisioned, V9's duplicate-EAN check against a production snapshot, and the
  product-fact replay executed **before** the first import. Nothing in this wave runs until these land.
- Chunk-size validation against the first Michelin sandbox pull —
  [durion#392](https://github.com/louisburroughs/durion/issues/392). 500 is an estimate.
- Manufacturer-part matching, ADR-0053 §5's third match step, needs a supplier-to-manufacturer mapping
  no vendor profile carries yet.
- Re-publication accounting (`republish_count`, `last_republished_at`) is visible only in logs and the
  table. An import stuck at the attempt cap is what an operator most needs to see and the admin API
  does not surface it.
- Issue hygiene: #1226, #1228, #1312 and #1318 are merged but their issues are still open.
