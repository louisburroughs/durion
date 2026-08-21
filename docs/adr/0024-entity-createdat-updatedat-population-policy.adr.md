# ADR-0024: Entity createdAt/updatedAt Population Policy

**Status:** ACCEPTED — amended 2026-08-21 (see §Amendment Log)  
**Date:** 2026-02-23  
**Deciders:** Architecture, Backend Leads  
**Affected Issues:** CAP-214 #39

The Decision section below is current. Read it alone; the amendment log is provenance only.

---

## Context

Entities in `durion-positivity-backend` once populated timestamps inconsistently — `Instant.now()` in `@PrePersist`,
in `@PreUpdate`, or only one field tracked.

Two drivers, in order of force:

1. **Correctness.** Under the `accelerated` profile the injected `Clock` is a converging `ScaledClock`. A timestamp
   sourced from anywhere else lands up to a year from every other value in the same row set, breaking inter-row
   ordering. Wall time is not a small drift here; it is a wrong value.
2. **Testability.** A fixed `Clock` makes timestamp assertions deterministic.

**Scope:** every persisted `created_at`/`updated_at` in `durion-positivity-backend`, written from Java or SQL.

---

## Decision

### 1. Both fields, always

Every new mutable JPA entity MUST have `createdAt` and `updatedAt`. Insert sets both to the same instant. Update
changes only `updatedAt`.

### 2. Every persisted timestamp comes from the application clock

Absolute, no exemptions: no entity, service, or SQL statement may source a persisted `created_at`/`updated_at` from
anywhere but the injected `Clock`. Enforced by the build (§6).

### 3. Three legitimate mechanisms

| # | Mechanism | Use for |
| --- | --- | --- |
| 1 | **Spring Data JPA auditing** — `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate`/`@LastModifiedDate`, fed by the module's `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` and a `DateTimeProvider` returning `Instant.now(clock)` | **Default. Prefer it.** Audit columns on mutable entities |
| 2 | **Injected `Clock` in the writing service** — `Instant.now(clock)` against the shared bean | Timestamps that are domain data, not audit columns: outbox `created_at`, replica `updated_at` |
| 3 | **`TimeSource`** | JPA lifecycle callbacks that cannot inject a bean. Under `accelerated` it throws on a read taken before the clock is bound rather than silently returning wall time |

### 4. Forbidden

| Forbidden | Why |
| --- | --- |
| No-arg `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `LocalTime.now()`, `OffsetDateTime.now()`, `ZonedDateTime.now()` | Wall time; ignores the clock |
| `Clock.systemUTC()`, `Clock.systemDefaultZone()` outside `com.positivity.time` / `com.positivity.events` | Manufactures a second clock |
| Hibernate `@CreationTimestamp`, `@UpdateTimestamp`, `@CurrentTimestamp` | Produced by Hibernate's own generator; never consults the Spring `Clock` bean |
| `now()`, `current_timestamp`, `clock_timestamp()`, `localtimestamp`, `current_date` in the **write half** of an `INSERT`/`UPDATE` | Database time, not application time |

`Instant.now(clock)` against the shared bean is **not** forbidden — it is mechanism 2.

A `WHERE` comparison against database time **is** allowed: application time trails wall time, so a read-side filter
against database time widens the active set rather than hiding rows.

### 5. Exemptions

Two distinct exemptions. Do not conflate them.

- **From the clock (§2): none.**
- **From the auditing annotations (§3 mechanism 1): four categories.** Each still requires the writing code to stamp
  the field from the injected clock.

| Category | Field | Who stamps it |
| --- | --- | --- |
| Immutable persistence models (`@Immutable`) | — | lifecycle update semantics do not apply |
| Replica entities (`Ext*`) | `updatedAt` | the applying event listener, `Instant.now(clock)` — records when *this* module last applied an event for the source aggregate. `@LastModifiedDate` would be equivalent, not corrective |
| Outbox rows (`OutboxEvent`) | `createdAt` | the module's outbox writer — the row's timestamp orders publication, so it must move with application time like the aggregate it describes |
| Service-written domain timestamps | varies | the owning service — timestamp is domain data, not an audit column |

35 entities across 8 modules sit in categories 2–4 and are compliant. Per-entity inventory:
`CLOCK_TIMESTAMP_OWNERSHIP.md` §"Entities without audit annotations" — maintained there, not here.

### 6. Enforcement is the build, not review

Five ArchUnit rules in `pos-archunit` (`ArchitectureTests`), across every module the suite scans:

| Rule | Rejects |
| --- | --- |
| `productionCodeShouldNotUseNoArgNowCalls` | the no-arg `now()` calls in §4 |
| `productionCodeShouldNotCallClockSystemUtcOutsideSharedTimeInfrastructure` | `Clock.systemUTC()` / `systemDefaultZone()` outside the two shared packages |
| `productionCodeShouldNotUseHibernateTimestampGenerators` | the Hibernate generators |
| `queryAnnotationsShouldNotWriteDatabaseTime` | database time in the write half of `@Query` SQL |
| `sourceLevelSqlShouldNotWriteDatabaseTime` | database time in `src/main/java` string-literal SQL; `+`-joined chains flattened first, so multi-line native queries are checked whole |

Net effect: any timestamp a service writes must have been *given* a `Clock`, and the only `Clock` available is the
shared bean.

SQL exceptions live in `DATABASE_TIME_WRITE_ALLOWLIST`, keyed by fully-qualified method name. Every entry MUST carry a
justification — a separate test fails on a blank one.

### 7. Timestamps that stay on real time

Deliberate. Do not "fix" these.

| Where | Why |
| --- | --- |
| `supplier_schedule_lease.leased_until`, `last_heartbeat_at`, `last_run_started_at` | Lease liveness asks whether another process is alive *now*. Accelerating expires live leases early and lets two runs share one binding. The same table's `updated_at` **is** on the application clock |
| `DEFAULT NOW()` columns in `pos-security-service`, `pos-bulk-loader`, `pos-mcp-server`, `pos-catalog` | Fires only when an INSERT omits the column, which Hibernate never does for a mapped field |
| JPQL `CURRENT_TIMESTAMP` / `CURRENT_DATE` in `GLMappingRepository`, `RoleAssignmentRepository` | Read-side effective-date filters; see the `WHERE` reasoning in §4 |
| Kafka record timestamps, log timestamps | Transport metadata, not business data |
| `@Scheduled` fixed delays | Poll cadence is real-time. Cadence is never scaled; only the *comparisons* a job makes use the injected clock |

### 8. Transitional allowance (narrow)

The clock-source half is closed — §6 fails the build on any legacy wall-time call. What remains permitted: an entity
may stamp from an injected clock in a callback or service instead of via `@CreatedDate`/`@LastModifiedDate`. Where that
lands in a §5 category it is permanent, not transitional; otherwise migrate it to mechanism 1 when the entity is next
touched.

---

## Alternatives Considered

1. **`@PrePersist`/`@PreUpdate` with `Instant.now()` everywhere** — rejected: wall time under `accelerated`,
   non-deterministic in tests, inconsistently implemented.
2. **`updatedAt` null until first update** — rejected: null-handling complexity, inconsistent query behavior.
3. **Database defaults/triggers for all timestamps** — rejected: opaque in application code, and database time is not
   application time under `accelerated`.
4. **Hibernate `@CreationTimestamp`/`@UpdateTimestamp`** — rejected: bypasses the `Clock` bean entirely (§4).

---

## Consequences

**Positive** ✅ — uniform audit semantics; accelerated runs produce internally consistent timestamps; deterministic
tests; violations caught at build time rather than review.

**Negative** ⚠️ — legacy entities need migration; every relevant service must enable the central auditing config; the
SQL allowlist needs maintenance as an explicit artifact.

**Neutral** — schema often unchanged where `created_at`/`updated_at` columns already exist.

---

## Implementation Notes

**Shared auditing config (per module):**

- `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`
- bean `Clock` — shared application clock (`ScaledClock` under `accelerated`, fixed in tests)
- bean `DateTimeProvider` — returns `Instant.now(clock)`

**New mutable entity:** `createdAt` + `updatedAt` mandatory; annotate `@CreatedDate` / `@LastModifiedDate`; skip the
annotations only under a §5 category.

**Migrating an entity:** add missing columns → annotate → delete the timestamp-setting callback.

**Tests:** fixed `Clock`; assert insert sets both fields equal; assert update moves only `updatedAt`.

**Post-run verification:** after an accelerated run, `deployment/alpha/verify-accelerated-timestamps.sql` asserts on
real data that no timestamp is after wall time, none precedes the run's virtual anchor, and no `updated_at` precedes
its own `created_at`.

---

## Amendment Log

### 2026-08-21 — Accelerated-clock convergence

Folded into the Decision section above. What changed from the 2026-02-23 text:

| Change | Where |
| --- | --- |
| Rationale raised from testability to correctness — the clock is authoritative under `accelerated` | Context |
| Legitimate mechanisms broadened from one to three; prohibition narrowed to **no-arg** `now()` | §3, §4 |
| Hibernate timestamp generators banned (previously unmentioned) | §4 |
| Exemption rule corrected — the original "only `@Immutable`" described no real entity and conflated clock exemption with annotation exemption | §5 |
| Enforcement moved from PR review evidence to five ArchUnit rules | §6 |
| SQL-level database time brought into scope, with a justified allowlist | §4, §6 |
| Deliberate real-time reads enumerated (previously no way to declare one) | §7 |
| Post-run data verification added | Implementation Notes |
| Transitional allowance narrowed to mechanism choice only | §8 |

---

## References

- Related ADRs:
  - `0013-platform-uuid-identifier-strategy.adr.md`
  - `0018-audit-actor-fields-from-security-context.adr.md`
- Operational companion (per-entity inventory; current where this ADR is only policy):
  - `durion-positivity-backend/docs/CLOCK_TIMESTAMP_OWNERSHIP.md`
- Related code:
  - `pos-events/src/main/java/com/positivity/time/ScaledClock.java`
  - `pos-events/src/main/java/com/positivity/time/TimeSource.java`
  - `pos-archunit/src/test/java/com/positivity/archunit/ArchitectureTests.java`
  - `pos-location/src/main/java/com/positivity/location/internal/entity/Location.java`
  - `deployment/alpha/verify-accelerated-timestamps.sql`
