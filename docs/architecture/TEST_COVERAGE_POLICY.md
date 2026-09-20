---
type: Architecture
title: Test Coverage Policy
description: The standing rule for backend coverage — per-module JaCoCo floors enforced at verify, how a floor is derived and re-derived, what may never lower one, and the record of how the current floors were arrived at.
status: Current
tags: [testing, coverage, jacoco, ratchet, ci, maven, quality-gate]
---

Backend coverage is gated by a **per-module JaCoCo floor** enforced at `verify`, derived three
points below that module's own unit-only measurement and raised whenever coverage rises — never a
repo-wide percentage, and never a number measured a different way from the gate.

**This is a policy, not a plan awaiting execution.** Phases 0–4 of the original improvement plan
landed; floors are live in 38 module poms and `jacoco.check.haltOnFailure` is `true` at the root.
What remains below is the standing rule (§6.2, §8), the derivation method (§6.1), the open items
(§7), and the delivery record that explains why each rule exists.

Read it in this order:

| If you are | Read |
|---|---|
| Adding tests, or asking what a test must look like | §8 |
| Wondering why CI failed on coverage | §6.2 |
| Setting or changing a module's floor | §6.1, then §6.2's working rules |
| Adding a new module | §6.1 (floors), §7 (what an unguarded module owes) |
| Wondering whether a coverage figure is comparable | §6.1 — if it was not measured `-DskipITs`, it is not |

**One caveat governs every number in this document.** A figure measured with Failsafe ITs included
is not comparable to one measured `-DskipITs`, and the floors come only from the latter. Where
older sections carry IT-inclusive figures they say so; the module poms are the only coverage
numbers here that need no caveat.

Method of the original work: `.agents/skills/test-coverage-improver`, adapted from its pnpm/JS
assumptions to this Maven reactor; test authoring follows `.agents/skills/java-testing`.

> **Reading paths in this document.** Bare paths in backticks — `pom.xml`, `pos-*/pom.xml`,
> `scripts/update-coverage-floors.sh`, `.github/workflows/ci.yml`, `target/site/jacoco/…` — and
> every `./mvnw` command are relative to the
> [`durion-positivity-backend`](../../../durion-positivity-backend) checkout, not to this repo.

## 1. Baseline

### 1.1-1.3 (removed)

Three sections here described the stale and partial coverage reports that motivated the original
plan: a `pos-coverage-aggregate` report dated 2026-07-30 showing 0.0% across all 29 groups because
it was generated from a reactor run carrying no JaCoCo exec data, per-module reports for 6 of ~36
modules, and a test-class-per-main-class density proxy used where no coverage data existed. All
three were superseded by the real measurement in §1.5, and every conclusion drawn from them was
wrong in a specific, recorded way — see the corrections note at the end of §1.5. They are removed
so nobody quotes a figure from them again.

The one durable finding from that era is the archetype list, kept as §1.4.

### 1.4 Recurring zero-coverage archetypes

The same class shapes went uncovered in every module that had data. They were cheap, high-count
wins because one test template applies across ~30 modules — and the list is still the right first
place to look when a new module or a `THIN` floor needs coverage, because these shapes look tested
and are not:

1. **`{Module}EventTypes` registries** — `customer` 208 missed lines (0%),
   `marketing` 55 (0%). Pure static data; a single "registry is well-formed"
   test covers all of it.
2. **`{Module}EventTypeInitializer` / permission registrars** — startup
   `ApplicationRunner`s that swallow failures. Untested failure-swallow paths.
3. **`OutboxPublisher` / `ManifestPublisher`** — `catalog` 53 + 88 missed (0%),
   `marketing` 53 (0%). Scheduled/transactional publishers.
4. **Kafka/event listeners** — `InventoryEventsListener` 72 (0%),
   `InventoryManifestListener` 49 (0%), `VehicleEventsListener` 119 (0%),
   `CustomerEventsListener` 145 (0%). This shape keeps recurring: §6.7 records a
   third consecutive nightly whose `THIN` floor was one event consumer merged
   without a test. Boilerplate enough to look tested, branchy enough that it is not.
5. **`ServiceImpl` classes at 0–1%** — `SegmentResolutionService` 238,
   `SegmentServiceImpl` 151, `PersonServiceImpl` 137,
   `PartyRelationshipServiceImpl` 129, `PartyTagServiceImpl` 116,
   `MarketingConsentServiceImpl` 115, `CampaignServiceImpl` 161,
   `CampaignSendServiceImpl` 68, `MessageTemplateServiceImpl` 61.
6. **Config classes** — `FlywayConfig`, `GlobalExceptionHandler` partials.

The per-class line counts are from the 2026-08-11 measurement and are historical; the *shapes* are
not.

### 1.5 MEASURED BASELINE (2026-08-11, full reactor `verify`, BUILD SUCCESS in 44:21)

> **These figures include Failsafe ITs and are therefore NOT comparable to any floor.** They are
> the historical starting point, retained because §§6–7 argue against them. No floor may be
> derived from this table — see §6.1.

This supersedes §1.1–1.3. Produced by the Phase 0 command below, including
Failsafe ITs. The aggregate now reports 36 module groups instead of 29.

| | Baseline |
|---|---:|
| Total lines | 73,833 |
| **Line coverage** | **72.6%** |
| **Branch coverage** | **59.5%** |
| Missed lines | 20,265 |

Per module, ordered by missed lines:

| Module | Lines | Line% | Branch% | Missed |
| --- | ---: | ---: | ---: | ---: |
| pos-customer | 5,364 | 61.5 | 48.6 | 2,067 |
| pos-workorder | 7,465 | 73.0 | 55.0 | 2,012 |
| pos-inventory | 10,055 | 83.2 | 66.9 | 1,686 |
| pos-order | 3,241 | 53.4 | 43.2 | 1,510 |
| pos-accounting | 10,510 | 85.7 | 72.5 | 1,498 |
| pos-mcp-server | 6,152 | 78.5 | 67.4 | 1,324 |
| pos-invoice | 3,308 | 63.4 | 52.5 | 1,210 |
| pos-people | 2,859 | 61.5 | 47.1 | 1,101 |
| pos-catalog | 2,807 | 68.3 | 46.3 | 891 |
| pos-marketing | 1,236 | 28.1 | 29.8 | 889 |
| pos-people-contact | 1,477 | 45.6 | 26.9 | 803 |
| pos-vehicle-inventory | 976 | 28.8 | 25.9 | 695 |
| pos-security-service | 3,501 | 80.4 | 69.6 | 687 |
| pos-shop-manager | 1,933 | 67.3 | 60.2 | 633 |
| pos-location | 2,167 | 75.1 | 64.5 | 539 |
| pos-bulk-loader | 1,770 | 73.9 | 64.9 | 462 |
| pos-event-receiver | 437 | 3.0 | 0.0 | 424 |
| pos-warranty | 2,561 | 85.7 | 79.5 | 367 |
| pos-tax | 984 | 74.8 | 66.3 | 248 |
| pos-document-helper | 374 | 44.4 | 25.9 | 208 |
| pos-vehicle-reference-nhtsa | 189 | 0.0 | 0.0 | 189 |
| pos-domain-events | 511 | 65.8 | 56.1 | 175 |
| pos-vehicle-fitment | 542 | 77.9 | 62.9 | 120 |
| pos-documents | 383 | 74.4 | 51.9 | 98 |
| pos-security-common | 406 | 81.3 | 62.0 | 76 |
| pos-vehicle-reference-carapi | 69 | 0.0 | 0.0 | 69 |
| pos-api-gateway | 938 | 92.9 | 72.2 | 67 |
| pos-image | 61 | 0.0 | 0.0 | 61 |
| pos-price | 1,053 | 94.5 | 81.3 | 58 |
| pos-events | 174 | 81.0 | 64.3 | 33 |
| pos-tax-common | 100 | 79.0 | 46.4 | 21 |
| pos-inquiry | 16 | 0.0 | — | 16 |
| pos-shared-dtos | 34 | 61.8 | 8.8 | 13 |
| pos-openapi-validation | 173 | 93.6 | 82.3 | 11 |
| pos-service-discovery | 4 | 0.0 | — | 4 |
| pos-bulk-ingest-lib | 3 | 100.0 | — | 0 |

**Corrections to the assumptions in §1.2–1.3.** The three largest modules are
not the worst covered — `pos-accounting` (85.7%), `pos-inventory` (83.2%), and
`pos-warranty` (85.7%) are among the best. `pos-customer` is 61.5%, not the
39.0% its stale per-module report showed. The genuinely thin modules are
`pos-event-receiver` (3.0%), `pos-marketing` (28.1%), `pos-vehicle-inventory`
(28.8%), `pos-document-helper` (44.4%), `pos-people-contact` (45.6%), and
`pos-order` (53.4%).

**Aggregate vs per-module numbers differ for shared libraries, and the
difference matters for Phase 4.** `report-aggregate` merges exec data from every
module, so a shared library gets credit for the coverage its consumers' tests
produce: `pos-security-common` reads 81.3% in the aggregate but 29.7% in its own
report; `pos-events` 81.0% vs 31.0%; `pos-shared-dtos` 61.8% vs 0%. A per-module
JaCoCo `check` ratchet reads the **per-module** report, so its thresholds must be
set from those lower numbers, not from this table.

## 2. Phase 0 — Restore a trustworthy baseline (blocking, no test code)

Nothing downstream is worth doing until the numbers are real.

1. Regenerate full-reactor coverage with the CI-equivalent command:

   ```bash
   ./mvnw -DskipTests=false verify \
     org.jacoco:jacoco-maven-plugin:0.8.14:report \
     -Darchunit.skipTests=true \
     -DlowResourceTests=true \
     -T 1C -B -ntp
   ```

   This runs unit tests and Failsafe ITs, writes each module's
   `target/site/jacoco/jacoco.{xml,csv}`, and — because `pos-coverage-aggregate`
   builds last in the same reactor — produces a real
   `jacoco-aggregate/jacoco.xml`. Expect a long run; ITs dominate.

   A faster unit-only variant, if IT runtime is prohibitive on this machine:
   append `-DskipITs`. It undercounts modules whose coverage comes mostly from
   ITs (`pos-inventory`, `pos-workorder`, `pos-location`, `pos-people`), so
   treat those numbers as a floor, not a measurement.

2. Confirm the aggregate is no longer 0% and that all 37 declared modules appear
   as groups. `./scripts/check-coverage-aggregate-drift.sh` already guards module
   membership in CI; run it locally too.

3. Snapshot the resulting per-module table into this document as the **baseline**
   so every later change has a before/after.

**Deliverable of Phase 0:** a real baseline table. No production or test code
changes.

**Decision point:** review the baseline together before Phase 1. The module
ordering below is derived from stale/partial data and will be re-ranked against
the real numbers.

## 3. Phase 1 — Cross-cutting archetype tests (highest lines-per-effort)

One test pattern authored once, then replicated per module. These target the
§1.4 archetypes and are pure unit tests — no Spring context, fast, non-flaky.

| Wave | Target                                  | Pattern                                                                                                                                                          | Modules                                            |
| ---- | --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| 1a   | `{Module}EventTypes`                    | Assert registry non-empty, ids unique, ids match `UPPER_SNAKE`, every id carries a valid threshold preset, ids match the `@EmitEvent` ids present in controllers | every module using `@EmitEvent`                    |
| 1b   | `{Module}EventTypeInitializer`          | MockRestServiceServer/`RestClient` stub: success path PUTs each type; failure path swallows and does not throw (the startup-safety contract)                     | same set                                           |
| 1c   | `{Module}PermissionRegistry`            | Registry well-formed; names match `domain:resource:action` snake_case; registration failure swallowed                                                            | every module with a registry                       |
| 1d   | `OutboxPublisher` / `ManifestPublisher` | Publishes pending rows, marks sent, retries/skips on failure, no-op on empty                                                                                     | catalog, marketing, and any module with an outbox  |
| 1e   | Kafka `*EventsListener`                 | Handle happy path, unknown/malformed payload, idempotent replay                                                                                                  | catalog, customer, marketing, inventory, workorder |

Rationale: in `pos-customer` alone, waves 1a + 1e cover ~327 currently-missed
lines; in `pos-marketing`, ~253. Replicated across ~30 modules this is the single
largest coverage delta available, and the tests are genuinely useful — they lock
in the event/permission registration contract that `CLAUDE.md` marks
non-negotiable.

### 3.1 Phase 1 outcome (delivered 2026-08-11)

**64 test files across 24 modules, all passing.** Waves 1a, 1b, and 1d are done;
waves 1c and 1e were not attempted and remain open (see §3.3).

Coverage delta measured on the first 11 modules verified: **395 missed lines
recovered**. Largest movers:

| Module | Before | After |
| --- | ---: | ---: |
| pos-event-receiver | 3.0% | 19.0% |
| pos-security-common | 29.7% | 46.7% |
| pos-events | 31.0% | 59.8% |
| pos-tax | 74.8% | 78.5% |
| pos-bulk-loader | 73.9% | 76.8% |
| pos-catalog | 68.3% | 70.1% |
| pos-invoice | 63.4% | 65.0% |

The remaining 13 modules' contribution is **not measured** — the confirming
full-reactor run was interrupted. Re-run the Phase 0 command to obtain the true
post-Phase-1 repo-wide figure before setting any Phase 4 thresholds.

The highest-value files are the two shared-library tests, since every module's
startup registration delegates to them: `pos-events`
`EventTypeInitializerSupportTest` (26 lines, previously 0%) and
`pos-security-common` `PermissionRegistrationSupportTest` (77 lines, previously
0%).

### 3.2 Findings surfaced by the new tests

1. **`WORKEXEC_DASHBOARD_TODAY_GET` uses hand-tuned thresholds** (1s/2s/3.5s)
   rather than one of the four presets. Legitimate — it aggregates across every
   open workorder for a location — so it is recorded as a documented exemption in
   `pos-workorder` `EventTypesTest`, paired with a guard test that fails if the
   exemption ever goes stale. Any *new* ad-hoc threshold in any module still
   fails the build.
2. **`pos-vehicle-inventory`'s `EventTypeInitializer` registers on a virtual
   thread**, so `run()` returns before any PUT is issued. It is the only async
   initializer in the reactor. Its tests await completion via Mockito
   `timeout()`; a naive "did not throw" assertion is vacuous there and was
   replaced with one that proves every type was still attempted.
3. **`pos-order`'s `OutboxPublisher` diverges from its 12 siblings**: no
   Micrometer counters, no `attempts` reset on success, and no class-name
   fallback or length cap on `last_error`. The divergences are safe —
   `last_error` is an unbounded `TEXT` column in both schemas — but they are easy
   to erase by copying a sibling over it, so its bespoke test pins each one
   explicitly.

### 3.3 Phase 1 remainder

- **Wave 1c (`{Module}PermissionRegistry`)** — not started. Only three modules
  define a registry class (`pos-inventory`, `pos-customer`, `pos-marketing`); the
  shared `PermissionRegistrationSupport` they extend is now covered, so the
  remaining value here is small.
- **Wave 1e (Kafka `*EventsListener`)** — closed for `pos-customer`,
  `pos-marketing`, and `pos-order` (see §4.1 and §4.2); open elsewhere. It was
  originally recorded as not started, and it is the largest
  remaining archetype: **78 listener classes** across the reactor, many at 0%
  (`CustomerEventsListener` 145 missed lines, `VehicleEventsListener` 119,
  `InventoryEventsListener` 72, `InventoryManifestListener` 49). Unlike the other
  waves these are not uniform enough for one template, so expect per-module work.
- **`ManifestPublisher`** (10 classes) was folded into wave 1d's scope in the
  table above but not implemented; only `OutboxPublisher` was covered.

## 4. Phase 2 — worst-covered domain services (delivered)

Phase 2 re-ranked the modules by missed lines against the §1.5 measurement and worked down the
list. It is complete: `pos-event-receiver`, `pos-marketing`, `pos-customer`, `pos-order`,
`pos-people`, `pos-invoice`, `pos-people-contact`, `pos-workorder`, `pos-vehicle-inventory`,
`pos-catalog`, `pos-document-helper`, `pos-shop-manager` and `pos-mcp-server` all now carry floors
derived from their post-Phase-2 coverage (§6.1).

The per-module delivery figures this section used to carry have been removed rather than kept as
history. They were measured unit-only (`-DskipITs`) against §1.5 baselines that included Failsafe
ITs, so no two numbers on the page could be compared without a footnote — and readers compared them
anyway. **The floors in the module poms are the only coverage figures in this repository that mean
anything without a measurement caveat.** Read those.

## 5. Phase 3 — merged into Phase 2 (delivered)

Every module now has real coverage data, so the "no data" distinction that defined this phase is
gone. It is kept for the two **structural** gaps it recorded — gaps a coverage percentage cannot
show, because a module can read high on unit tests alone while its persistence and transaction
boundaries go entirely unexercised. **Both are closed**, and the reasoning is the durable part:

- `pos-invoice` (178 main classes) and `pos-warranty` (144) had **zero integration tests**.
  `pos-warranty` reaching 85.7% on unit tests alone was the tell: a high unit-only figure on a
  module that persists is evidence of a missing IT layer, not of a well-tested module. Both now
  have Failsafe IT layers.
- `pos-tax-common` had **zero test classes** of its own; its 79.0% aggregate reading came entirely
  from consumers' tests. It now has its own suite and its own floor (§7 item 5, §7.6).

The general rule survives both: **a module's own report, not the aggregate, is the only honest
reading of whether that module is tested**, and a shared library is where the two diverge most.

Original provisional ordering, superseded by §4:

1. `pos-inventory` (584 main) — largest surface in the repo.
2. `pos-accounting` (505 main) — financial posting semantics; highest blast
   radius per defect.
3. `pos-workorder` (351 main).
4. `pos-order` (197 main, 20 tests, 1 IT) — worst density among large modules.
5. `pos-invoice` (178 main, **0 ITs**) — add a Failsafe IT layer, not just units.
6. `pos-warranty` (144 main, **0 ITs**) — same.
7. `pos-shop-manager`, `pos-people`, `pos-location`, `pos-security-service`.
8. Small modules: `pos-event-receiver` (2 tests / 31 classes),
   `pos-tax-common` (zero tests), `pos-vehicle-fitment`, `pos-vehicle-inventory`.

## 6. Phase 4 — the ratchet (delivered 2026-08-12)

### 6.1 Where the floors are, and how each one is derived

Floors were first derived on `main` at `5269162` (2026-08-16) and last re-derived across the
reactor on 2026-09-06 from `main` at `61cbbd28` via `scripts/update-coverage-floors.sh --apply`.
Individual floors have moved since (§§6.6–6.7), which is the point of a ratchet.

**The floors themselves live in the module poms, not here.** Each module's
`pos-<module>/pom.xml` sets `<jacoco.line.min>` and `<jacoco.branch.min>` in its `<properties>`,
with a comment recording the measurement they were derived from. Thirty-eight modules carry
floors today. Read them there:

```bash
grep -A2 'jacoco.line.min' pos-*/pom.xml            # every floor, with its derivation comment
grep -L 'jacoco.line.min' pos-*/pom.xml             # the unguarded modules
```

A table of floors in this document would be a second copy of live build configuration, updated by
hand, wrong within a release. `scripts/update-coverage-floors.sh --apply` rewrites the poms; nothing
rewrites prose.

**How a floor is derived.** Three points below the module's measured coverage, rounded down to two
decimals, measured by `./mvnw verify -DskipITs -Darchunit.skipTests=true` — the same command the
gate runs. Each part of that is load-bearing:

| Choice | Why |
|---|---|
| `-DskipITs` | The binding CI gates run `-DskipITs`, so only Surefire contributes to the JaCoCo bundle. A baseline measured any other way describes coverage the gate cannot see. |
| Per-module report, never the aggregate | `report-aggregate` credits a shared library with its consumers' coverage — `pos-security-common` once read 81.3% aggregate against 29.7% own. `jacoco:check` reads the per-module report. |
| Three points, not one | Run-to-run variation between a parallel (`-T 1C`) CI build and a serial local one was measured at roughly 1.7 points on `pos-order`. A cushion thinner than about two points is a coin toss, not a gate. |
| Per-module, not a repo-wide bar | At ~81% repo-wide, a uniform 85% fails a third of the reactor on day one and a uniform 70% lets the best modules rot 15 points unnoticed. |

**The failure this rule exists to prevent.** Floors were once derived from a full `verify`
*including* Failsafe ITs while the gate ran without them. For modules whose IT-only coverage was
around five points — `pos-order`, `pos-accounting`, `pos-inventory`, `pos-security-service` — the
mismatch consumed the entire cushion, parking them on the boundary. `pos-order` failed at 704/1122
branches, eight above its floor, reporting 0.61 in CI and 0.6275 locally on the same commit, with
neither measurement wrong.

**Re-deriving the whole reactor.** Run the command above, then read each module's own
`target/site/jacoco/jacoco.csv` (summing `LINE_MISSED`/`LINE_COVERED` and
`BRANCH_MISSED`/`BRANCH_COVERED`), or let `scripts/update-coverage-floors.sh --apply` do it. Both
`update-coverage-floors.sh` and `check-coverage-floor-drift.sh` refuse to run when they find
Failsafe reports, for the reason above; see `scripts/README.md`.

**Unguarded modules.** `pos-shared-dtos`, `pos-inquiry` and `pos-service-discovery` carry no floor
— a handful of lines each, or all-zero coverage. A `0.00` floor is not a gate, and pretending
otherwise would misrepresent them as guarded. They need first tests, not thresholds (§7).

**Generated code needs no `jacoco.excludes`.** `com.positivity.shared.annotation.@CoverageGenerated`
(`@Retention(CLASS)`, added in `8dd877415`) has a name ending in `Generated`, so JaCoCo's built-in
generated-code filter excludes annotated types automatically. It is applied to the `Pos*Application`
mains only, matching the annotation's own instruction not to apply it to handwritten business
behaviour. Extend it case by case if genuinely mechanical code shows up in a coverage gap; **do not
use it to hide untested logic** — an exclusion is indistinguishable from coverage in every report
this document cites.

### 6.2 How the ratchet works

Root `pom.xml` gains a `check-ratchet` execution on the `verify` phase asserting
`LINE` and `BRANCH` `COVEREDRATIO` against two properties, defaulted to `0.00`:

```xml
<jacoco.line.min>0.00</jacoco.line.min>
<jacoco.branch.min>0.00</jacoco.branch.min>
```

Each module overrides them in its own `pom.xml`, set **3 points below its
measured unit-only baseline** (§6.1), rounded down to two decimals — enough to
catch a real regression, loose enough to absorb both a refactor moving a few
lines and the run-to-run variation that comes from a parallel (`-T 1C`) CI build
exercising a slightly different set of paths than a serial local one. That
variation was measured at roughly 1.7 points on `pos-order`, so a cushion any
thinner than about two points is not a gate, it is a coin toss.

The cushion is measured against the **same command the gate runs**
(`verify -DskipITs`). Deriving it from any other measurement is what broke this
section before — see §6.1. `haltOnFailure` is true, so a breach fails the build:

```
Rule violated for bundle pos-tax-common: lines covered ratio is 0.34,
but expected minimum is 0.95
```

**`-DskipTests` is safe, and no `skip` guard is wired.** JaCoCo's `check` goal
no-ops when there is no execution data — `Skipping JaCoCo execution due to
missing execution data file` — so `clean install -DskipTests` passes, and an
`install -DskipTests` over a stale `jacoco.exec` evaluates that prior data and
also passes. Both were verified on this tree. A `<skip>${skipTests}</skip>` guard
was considered and rejected: it would buy nothing over the goal's own behaviour
while turning `-DskipTests` into a documented switch for bypassing the ratchet.
The one narrow edge is a *partial* `jacoco.exec` left by an interrupted test run,
where a later `install -DskipTests` would judge incomplete data — `clean` fixes
that, and CI always runs tests.

**Where the gate actually runs.** Three CI steps touch coverage, and only two of
them are binding:

| step | measurement | binding? |
|---|---|---|
| PR — "Test changed modules and dependents" | `test` + `jacoco:check`, `-DskipITs` | **yes** — pre-merge gate |
| main — "…incl. ITs" | `verify`, ITs included | no — `haltOnFailure=false` |
| nightly — "Build aggregate coverage report" | `verify -DskipITs`, full reactor | **yes** — job fails on any violation |
| nightly — "Enforce coverage floor drift" | the same `-DskipITs` reports, read by `scripts/check-coverage-floor-drift.sh` | **yes** — job fails on a stale, thin, or missing floor |

The PR step invokes `jacoco:check` directly instead of running `verify`. The
`check-ratchet` execution is bound to `verify`, so a build that stops at `test`
never evaluates it — which is why every PR merged ungated until 2026-08-16, and the
nightly caught regressions a day late. The rules therefore live in a **plugin-level**
`<configuration>` in the root pom, not inside the execution: execution configuration
is invisible to CLI-invoked goals.

The main step is deliberately **not** binding. It runs the ITs, so its coverage is
higher than the floors describe; letting it decide would produce false greens, never
honest failures. That is precisely how `pos-image` and `pos-supplier` reached main
below their floors. Both binding gates measure `-DskipITs`, the same way the floors
are derived (§6.1).

**Working rules.**

- Raise a module's floor when its coverage rises — that is what makes it a
  ratchet rather than a fixed bar. `scripts/update-coverage-floors.sh --apply`
  does this for the whole reactor; `scripts/check-coverage-floor-drift.sh` fails
  the nightly when a floor has been left behind. Both are described in
  `scripts/README.md`, and §6.5 records why they exist.
- Never lower a floor without saying why in the commit message. The updater
  refuses to lower one at all unless `--allow-lower` is passed.
- Re-derive floors only from a `-DskipITs` run. If you find yourself reading a
  coverage number that included the ITs, stop: that number cannot be turned into
  a floor, because the gate will never reproduce it. Both scripts refuse to run
  when they find Failsafe reports, for exactly this reason.
- A floor sitting within ~2 points of measured coverage is not "tight", it is
  unstable, and it will fail on a night when nothing changed. Treat a
  thin-margin module as a bug in the floor or a gap in the tests, not as a
  module doing well.
- The three all-zero modules (`pos-inquiry`, `pos-service-discovery`,
  `pos-shared-dtos`) carry **no** floor — each is under the `--min-lines`
  threshold (§6.5) with no coverage of its own to gate. A `0.00` floor is not a
  gate, and pretending otherwise would misrepresent them as guarded. They need
  first tests, not thresholds — see §7.
  (`pos-image`, `pos-vehicle-reference-carapi` and `pos-bulk-ingest-lib` are no
  longer in this category: all three now measure real, non-zero coverage and
  carry real floors — see their poms (§6.1). `pos-bulk-ingest-lib` left the group
  on 2026-09-06 and is the subject of §6.6. Earlier versions of this list still
  named them from when they were genuinely all-zero.)
- Deliberately **not** a single repo-wide bar. At 81.1% a uniform 85% would fail
  a third of the reactor on day one, and a uniform 70% would let the best modules
  rot 15 points before anyone noticed.

### 6.3 SonarCloud — already wired (correction)

An earlier draft of this section claimed the Sonar step still needed wiring. That
was wrong: `.github/workflows/ci.yml` already uploads every module's
`**/target/site/jacoco/jacoco.xml` as the `jacoco-coverage-reactor` artifact,
downloads it in the Sonar job, and imports it via
`-Dsonar.coverage.jacoco.xmlReportPaths=${{ github.workspace }}/sonar-coverage/**/jacoco.xml`
(absolute by necessity — the scanner resolves a relative value against each
module's own base directory). The aggregate XML is uploaded separately as
`jacoco-coverage-aggregate`.

Importing the **per-module** reports is also the right choice, and matches the
ratchet: Sonar sees each module's own coverage rather than the aggregate's
consumer-credited figure for shared libraries.

### 6.4 One branch analysis, and only one (2026-08-18)

§6.3's closing claim that "no work is outstanding here" was wrong, and the way it
was wrong cost the project its headline coverage number for three months.

Two jobs in `ci.yml` were publishing to the same SonarCloud project:

- `code-quality-full` (nightly, `schedule`) — imports the aggregate report from
  `pos-coverage-aggregate` and therefore sees all 37 modules.
- `code-quality` (`if: github.event_name != 'schedule'`, so also every push to
  `main`) — indexes the **whole** reactor for analysis but imports coverage only
  from the artifacts the selective `build-test` run produced, i.e. the changed
  modules. Sonar's Zero Coverage Sensor scores every other module 0%.

Whichever ran last won, and pushes are far more frequent than the nightly, so
`main` spent nearly all of its time showing the changed modules' share of the
reactor:

```
2026-08-18T06:25  78.0   <- nightly aggregate
2026-08-18T11:25  14.9   <- push (merge of #1363: pos-order + pos-workorder)
```

The 14.9% was arithmetically exact, not a glitch: 10,622 covered lines out of
84,981, which is pos-order (4,267 lines at 84.6%) plus pos-workorder (7,888 at
77.7%) plus their `-amd` dependents. The run's Sonar log says so directly —
`Importing 2 report(s).` for a 37-module reactor.

The fix, applied in `ci.yml`:

- The coverage download and the Sonar scan in `code-quality` are now gated on
  `github.event_name == 'pull_request'`. A PR analysis is stored against the PR,
  scores `Coverage on New Code` from the changed files only, and never writes
  branch measures — so partial coverage is correct there. Checkstyle and SpotBugs
  still run on push, unchanged.
- The scan's push/branch fallback branch was deleted. PR mode is now forced
  unconditionally via the four `sonar.pullrequest.*` properties; there is no path
  left that can auto-detect a branch and publish over it.
- `code-quality-full` also accepts `workflow_dispatch`, so `main` can be
  re-measured on demand instead of waiting for 06:00 UTC.

Branch-level coverage for `main` now has exactly one source: the aggregate report.
Keep it that way — any new job that runs `sonar-maven-plugin` outside PR mode must
import the aggregate XML, never per-module reports.

### 6.5 The ratchet is now enforced in both directions (2026-08-24)

§6.2 has said "raise a module's floor when its coverage rises" since the ratchet
landed. Nothing implemented it. Floors were hand-edited, so they moved only when
someone remembered, and `jacoco:check` is blind in that direction by
construction: it fails a module that falls **below** its floor and says nothing
about a floor sitting far **under** a module that improved.

Measured on `main` at the time of writing, summing every guarded module's
distance from its own floor:

```
3,437 covered lines + 971 covered conditions may go uncovered before any floor trips
implied overall floor: 74.2%, against 78.2% measured
```

Four points of overall coverage could be given back without one build turning
red — on a project whose gate target is 80%. The individual findings were small
and specific: `pos-supplier` sat 1.0 point over both its floors (the sub-2-point
margin this section calls a coin toss), `pos-domain-events` more than 10 points
over its line floor, and `pos-web-common` carried no floor at all despite having
real code and 188 lines to cover — it is absent from §6.1's table entirely.

Two scripts close this, sharing `scripts/coverage_floors.py`:

| script | what it does |
|---|---|
| `scripts/update-coverage-floors.sh` | re-derives every floor from the last `-DskipITs` build (measured coverage minus `--cushion`, default 3, rounded down to 2dp — the §6.2 formula) and writes the poms |
| `scripts/check-coverage-floor-drift.sh` | fails on `BREACH`, `THIN` (cushion < 2), `STALE` (cushion > 6), or `UNGUARDED` (a module over 50 lines with no floor) |

Both inherit this section's constraints rather than restating them: they read
each module's own `jacoco.csv` and not the aggregate (§6.1), they refuse to
score a build whose `target/failsafe-reports` shows Failsafe ran, and the
updater raises but never lowers unless `--allow-lower` is passed. The drift
check runs in the nightly `code-quality-full` job immediately after the ratchet.

`STALE` at 6 points is deliberately two full cushions: at 3 points the check
would fire on the ordinary drift of a module that gained a test since the last
re-derivation, and a gate that fires on healthy movement gets switched off.

**First run.** The floors in §6.1 were derived on 2026-08-16 and the check has
never been run against a fresh measurement, so the first nightly after this
lands will report the modules that have drifted since. The fix is one command
against that build's reports:

```bash
./mvnw -pl pos-coverage-aggregate -am verify -DskipITs -Darchunit.skipTests=true -T 1C
scripts/update-coverage-floors.sh --apply
```

Then regenerate §6.1's table from the same reports, so the document and the poms
describe one measurement rather than two.

### 6.6 The ratchet has no ceiling (2026-09-15)

**There is none, and that is the design.** `floor_for()` in
`scripts/coverage_floors.py` is measured coverage minus `--cushion`, clamped
only at the bottom (`max(0.0, ...)`); nothing caps it from above. A module at
99% would carry a 0.96 floor. Combined with `proposed_floors()`, which drops any
candidate below the standing floor unless `--allow-lower`, the floor tracks each
module's **best measurement ever taken**, and `STALE` at +6 forces the
re-derivation that moves that peak up.

The consequence is worth stating plainly, because it is the thing that fails a
nightly: **a guarded module fails as soon as its measured cushion over the
standing floor falls under `--min-cushion` (2 points).** That is what a ratchet
is. But the failure does not look like a coverage regression when it arrives —
it names a *floor* (`THIN`), on a module nobody touched that night, in a job
that runs long after the commit responsible merged green.

Stated as a fall from the peak it is tighter than the 3-point cushion suggests,
because `floor_for()` rounds the floor **down** to whole percentage points: the
floor is `floor(peak − 3)`, so `THIN` arrives after a fall of anywhere between
1.0 and 2.0 points, depending on where the peak sat inside its own point. Both
modules below are examples. `pos-bulk-ingest-lib` had 1.9 points of room and
used 3.0; `pos-tenancy-common`'s line counter had only 1.1.

**Why the PR gate lets it through.** The two gates fail on different things
(§6.2's table). The PR gate invokes `jacoco:check`, which only knows `BREACH`:
89.9% against a 0.89 floor passes, because it is above it. `THIN` is computed
only by `scripts/check-coverage-floor-drift.sh` in the nightly. So a change that
eats most of a module's cushion merges green by design, and the nightly reports
it afterwards. Nothing is misconfigured; the cost is that the diagnosis lands a
day after the diff.

**Run 34931240182** (nightly, `main` at `d3a2311`) failed on two modules, and
they are not the same kind of failure:

| module | counter | floor | floor derived from | measured 2026-09-15 | cushion |
|---|---|---:|---:|---:|---:|
| `pos-bulk-ingest-lib` | line | 0.89 | 92.9% | 89.9% | +0.9 |
| `pos-bulk-ingest-lib` | branch | 0.89 | 92.9% | 89.5% | +0.5 |
| `pos-tenancy-common` | line | 0.91 | 94.1% | 92.0% | +1.0 |
| `pos-tenancy-common` | branch | 0.78 | 81.0–81.8% | 79.8% | +1.8 |

`pos-tenancy-common` is the coin toss §6.2 already describes: its branch counter
is 0.2 points short of the threshold. Its floor was re-derived in `ec47975` from
a stack measuring 81.0–81.8%, so 79.8% is a point or so of real decline sitting
inside exactly the range §6.2 says no single measurement can separate from
parallel-CI variation. It needs no code change, only the re-derivation below.

Its pom comment is separately wrong and worth fixing while there: it reads
"measured 94.1% line / 78.1% branch" beside a 0.78 branch floor, which would be
a zero-point cushion and was never true. `ec47975` edited the property by hand,
and `refresh_comment()` only runs when `update-coverage-floors.sh` writes the
file — so a hand-edited floor leaves the recorded measurement behind. Prefer the
script even for a one-property change.

`pos-bulk-ingest-lib` is a real regression, and it has one cause.

**What dropped, and why.** Commit `986fc3f` (#1987) taught the library to tell a
replication lag apart from a refusal: an exception declaring `503` is now
reported as `REPLICATION_PENDING` with the module's own message, rather than
being swept into `INTERNAL_ERROR`. Rebuilding each side with the gate's own
command (`verify -DskipITs`) gives:

```
986fc3f~1   line 52/56 = 92.9%   branch 26/28 = 92.9%
HEAD        line 62/69 = 89.9%   branch 34/38 = 89.5%
```

The "before" reproduces the pom comment exactly, so the floors were honest when
written. The change added 13 countable lines and 10 branches; 3 lines and 2
branches of that arrived with no test:

| file | line | uncovered |
|---|---:|---|
| `AbstractBulkIngestController` | 70 | `if (BulkIngestFailures.isRetryable(exception))` — true arm never taken |
| `AbstractBulkIngestController` | 71 | `log.warn("Deferred record at row {}: {}", ...)` |
| `AbstractBulkIngestController` | 72 | `return BulkIngestFailures.retryable(...)` |
| `AbstractBulkIngestController` | 130 | `rowRetryableFallbackMessage()`'s default return |
| `BulkIngestFailures` | 151 | `message == null` half of `retryable()`'s fallback condition |

The split is instructive: the *library* half of the change is covered
thoroughly. `BulkIngestFailures` gained 9 lines and all 9 are exercised —
`BulkIngestFailuresTest` has a whole `IsRetryable` nest, including a
`ResponseStatusException`, an annotated type, a rejection, an unclassified
exception, and the blank-message fallback. What nobody wrote is the test that
drives the same behaviour through `AbstractBulkIngestController#rowFailure`,
which is the method every bulk-ingest endpoint on the platform actually calls.
`AbstractBulkIngestControllerTest` covers the rejection arm and the
server-fault arm and stops there. Line 151 is the same omission one layer down:
`retryable()` is tested with a message and with `""`, never with `null`,
mirroring the identical half-covered condition `rejected()` has carried at line
166 since before this change.

`82a410a` and `c75833b` also touched the module in this window and moved nothing:
the first added only Lombok annotations, the second only comments.

**Why three lines was enough to do it.** The bundle is two classes and 69
countable lines. The three Lombok DTOs (`BulkIngestRequest`, `BulkIngestResponse`,
`BulkIngestResult`) carry `@lombok.Generated` via the root `lombok.config` and
JaCoCo filters them out entirely, so the whole floor rests on
`AbstractBulkIngestController` and `BulkIngestFailures`. One missed line is
about 1.4 points; three is the entire 3-point cushion. §6.2 sets `--min-lines`
at 50 on the reasoning that below it "a handful of lines swings the ratio by
whole points" — at 56 lines this module cleared that bar by six lines, and it
behaves exactly like the modules the threshold was meant to exclude.

**The rule this adds.** In a module under ~100 countable lines, a new branch and
its test belong in the same commit. There is no cushion to spend: the first
untested branch is the whole margin, the PR gate will not say so, and the
nightly will blame the floor.

**Remediation.** Two different fixes for the two modules:

1. `pos-bulk-ingest-lib` needs the tests, not a lower floor. **Done** — see below.
2. `pos-tenancy-common` needs only a re-derivation from a fresh `-DskipITs`
   build; its branch floor is 0.2 points from the threshold on coverage that has
   not meaningfully moved. **Done, and the diagnosis was wrong** — see below.

**Outcome for `pos-bulk-ingest-lib` (2026-09-15).** A `Deferred` nest in
`AbstractBulkIngestControllerTest` now drives the 503 arm through `rowFailure`
itself: the message passthrough, the annotated-type form, WARN-without-a-stack-trace,
the `rowRetryableFallbackMessage()` default, the rejection-outranks-retryable
ordering the source only commented, and the allowlist still holding for an
unclassified failure. Two one-line additions in `BulkIngestFailuresTest` close
the fallback conditions from both sides — `retryable()` had no `null` case,
`rejected()` no blank-but-present case — and one covers a blank inbound
`X-Correlation-Id`, which a caller can really send.

```
before   line 62/69 = 89.9%   branch 34/38 = 89.5%   THIN
after    line 65/69 = 94.2%   branch 38/38 = 100.0%  OK
```

Coverage came back above the 92.9% peak the floors were derived from, so the
ratchet went from `THIN` straight to `STALE` and the floors were re-derived to
0.91/0.97. Three mutation checks back the new tests
(`.claude/hooks/mutation-check-hook.sh`): disabling the `isRetryable` guard,
changing the fallback string, and disabling the rejection guard each fail the
test that claims to defend them.

**Outcome for `pos-tenancy-common` (2026-09-16).** The re-derivation was never
run, and the next nightly (`35057994389`, `main` at `e5d7ec8`) reported the same
two `THIN` counters at the same numbers: 92.0% line, 79.8% branch. Reproducing
with the gate's own command gave the same figures to the decimal, so this was
not parallel-CI variation, and ranking the module's classes by missed lines
named the cause at once: `replica.TenantDisplayName`, added on 2026-09-13
(`11420bbc`, bounded in `3eeeff17`), with **0 of 11 lines and 0 of 4 branches
covered**. Eleven lines of 502 is 2.2 points — the whole fall from the 94.1%
peak. Its four branches are the two in `truncate()` (over the bound or not,
high surrogate at the cut or not), and nothing exercised either arm.

That makes it the `pos-bulk-ingest-lib` case again, not the noise case the
table above filed it under: a small utility with fully specified behaviour
(NFKC, whitespace collapse, case fold, surrogate-safe truncation at 200) that
every login lookup keys on, merged without a test. `TenantDisplayNameTest` now
pins each rule the class's javadoc states — the javadoc's own two-spelling
example, NFKC of fullwidth letters and a ligature, `displayForm` keeping the
operator's casing, both sides of the length bound, a cut that lands on
whitespace, a cut that would split a surrogate pair, a pair that just fits,
and the two lengthening cases the javadoc calls out (200 U+FB03 ligatures
becoming 600 characters, 200 U+0130 becoming 400 after folding).

```
before   line 462/502 = 92.0%   branch 142/178 = 79.8%   THIN
after    line 473/502 = 94.2%   branch 146/178 = 82.0%   OK
```

Coverage came back over the 94.1% peak, the branch floor rose 0.78 → 0.79 by
`update-coverage-floors.sh --apply`, and the same run rewrote the pom comment
that §6.6 flagged as wrong (it now records 94.2% / 82.0%). Three mutation checks
back the new tests (`.claude/hooks/mutation-check-hook.sh`): disabling the
surrogate guard, flipping the case fold to upper, and downgrading NFKC to NFC
each fail the test that claims to defend it.

**The rule this adds.** When a nightly reports `THIN`, rank the module's
classes by missed lines before calling it variation. A class at 0/N covered is
a test that was not written, and the ratchet found it; re-deriving the floor
over it is the write-off the paragraph above warns against.

The four lines still uncovered are all pre-existing and none is new: the
`bulkIngest` endpoint body, which no test posts through, and the
`rowRejectionCode()` / `rowRejectionFallbackMessage()` defaults, which every test
controller overrides.

**And the ceiling question, answered concretely.** A 0.97 branch floor on a
38-branch bundle leaves about one branch of headroom: a single uncovered
condition reads 97.4%, which is `THIN` again. That is not a misconfiguration —
in a bundle this size one branch genuinely is a real regression — but it is what
"no ceiling" means in practice for a small module, and it is the strongest
argument yet that `--min-lines` at 50 is set too low. A module whose whole gate
turns on one condition is being measured more precisely than the measurement
supports. Raising the threshold is a change to §6.2's contract and is left open
rather than made here.

```bash
./mvnw -pl pos-coverage-aggregate -am verify -DskipITs -Darchunit.skipTests=true -T 1C
scripts/update-coverage-floors.sh --apply
```

A floor that has to come **down** needs `--allow-lower` and its reason in the
commit message (§6.2). Reach for it only once the coverage question is settled:
lowering a floor to clear a `THIN` caused by a genuine regression buys a green
nightly by writing off the points that caused it.


### 6.7 Two stale floors and one regression (2026-09-17)

**Run 35184260406** (nightly, `main` at `9e37280`) failed `Enforce coverage floor
drift` on five counters across three modules, and — as in §6.6 — they are not
the same kind of failure:

| module | counter | floor | floor derived from | measured 2026-09-17 | cushion | status |
|---|---|---:|---:|---:|---:|---|
| `pos-shop-manager` | line | 0.83 | 86.5% | 89.1% | +6.1 | `STALE` |
| `pos-shop-manager` | branch | 0.69 | 72.3% | 77.4% | +8.4 | `STALE` |
| `pos-vehicle-inventory` | line | 0.75 | 78.5% | 83.7% | +8.7 | `STALE` |
| `pos-vehicle-inventory` | branch | 0.70 | 73.3% | 79.5% | +9.5 | `STALE` |
| `pos-location` | line | 0.79 | 82.1% | 80.2% | +1.2 | `THIN` |

The two `STALE` modules are the ratchet working as designed and nothing else.
Both took a wave of CAP-325/326 work with its tests over 2026-09-15..16 —
shop-manager gained 2.6 line and 5.1 branch points, vehicle-inventory 5.2 and
6.2 — and nothing raised the floors behind the gains. Re-deriving them is the
whole fix: 0.83/0.69 → 0.86/0.74 and 0.75/0.70 → 0.80/0.76.

`pos-location` is the §6.6 case again, and §6.6's rule found it in one step:
rank the module's classes by missed lines before calling a `THIN` variation.
`internal.service.CatalogEventsListener`, added 2026-09-16 (`3a41e4f4`, put to
work in `00699d3b`), had **0 of 51 lines and 0 of 12 branches covered**. Fifty-one
lines of 2,921 is 1.7 points — the whole fall from the 82.1% peak the floor was
derived from. It is the third consecutive nightly whose `THIN` was one
event-consumer or replica class merged without a test, which is now a pattern
rather than a coincidence: the listener shape is boilerplate enough to look
tested and branchy enough that it is not.

`CatalogEventsListenerTest` pins the consumer contract the class's javadoc
states: only `catalog.service.updated` is applied and a product fact on the same
topic is skipped without a dedup row; a missing, blank or already-seen `eventId`
is dropped; an unparsable message and a malformed payload are logged and dropped
rather than poisoning the partition, and neither leaves a `processed_events` row
behind to hide it; a transient DB error reaches the container for retry/DLQ; the
stale guard discards an older fact but still records it, and **applies on an
equal version** so `facts/replay` repairs rather than no-ops (#1486); and the
delete tombstone lands as `active = false` rather than removing the row, which is
what keeps a retired operation code distinguishable from an unknown one.

```
before   line 2343/2921 = 80.2%   branch 751/1079 = 69.6%   THIN
after    line 2394/2921 = 82.0%   branch 763/1079 = 70.7%   OK
```

The floors do not move: 82.0% is a tenth under the 82.1% peak 0.79 was derived
from, so `proposed_floors()` drops the 0.78 candidate, which is the ratchet
refusing to write off the point it just recovered. Three mutation checks back the
new tests (`.claude/hooks/mutation-check-hook.sh`): inverting the stale-guard
argument order, swallowing the `TransientDataAccessException` rethrow, and
dropping the blank-`eventId` half of the guard each fail the test that claims to
defend them.

**A measurement note on `pos-vehicle-inventory`.** Its floors were derived from
the percentages this nightly published rather than a local re-run:
`VehicleFactReplayRepositoryTest` needs a Postgres container, and the environment
the re-derivation ran in cannot reach the registry, which costs that module 1.1
line and 1.7 branch points. Deriving from the local figure would have written
0.79/0.74 — floors a point under what the gate actually measures, i.e. the slack
this check exists to remove. `pos-shop-manager` and `pos-location` were derived
from a local `verify -DskipITs` that reproduces the nightly to the decimal.


## 7. What is still open

**All six items from the original list are closed.** They are kept here, struck through, because
each one's subsection below records a decision that still binds.

1. ~~**Wave 1c** (`{Module}PermissionRegistry`, §3.3)~~ — closed 2026-08-12, see §7.1.
2. ~~**Controller `@WebMvcTest` slices**~~ — closed 2026-08-12, see §7.3.
3. ~~**The four all-zero modules**~~ — closed 2026-08-12, see §7.2.
4. ~~**Branch-coverage tails**~~ — closed 2026-08-12 over two passes, see §7.4 and
   §7.5. `pos-document-helper` is resolved by deletion rather than by tests: its
   35.2% was the untested duplicate under `com.positivity.documents.helper`,
   removed in #1274, leaving the module at 90.0% branch on the surviving
   `com.positivity.documents` copy.
5. ~~**Low-coverage shared libraries**~~ — closed, see §7.6. The figures this item
   carried (`pos-tax-common` 34.0%, `pos-domain-events` 39.3%, `pos-security-common` 46.8% on
   their own tests) were superseded and are **no longer true**. Verified 2026-09-20 from the
   derivation comments in the module poms: `pos-tax-common` 89.6% line / 74.1% branch (floors
   0.86 / 0.71), `pos-domain-events` 85.3 / 80.3 (0.82 / 0.77), `pos-security-common` 86.3 / 86.4
   (0.83 / 0.83). All three are guarded by real floors on their own unit tests, not on the
   aggregate's consumer-credited figure.
6. ~~SonarCloud wiring~~ — already in place; see the §6.3 correction.

**What remains.** No item on the list above. The two standing obligations are structural rather than a backlog:

- **Three modules carry no floor** — `pos-shared-dtos`, `pos-inquiry`, `pos-service-discovery`.
  Each is under the `--min-lines` threshold with no coverage of its own, so a `0.00` floor would
  misrepresent them as guarded (§6.1, §6.2). They earn a floor when they earn behaviour;
  `pos-inquiry` in particular is 16 lines of application scaffolding (§7.2).
- **Floor drift is continuous, not a phase.** The nightly `Enforce coverage floor drift` job is the
  mechanism, and §§6.5–6.7 are three worked instances of it: a `STALE` floor means the ratchet
  needs raising, a `THIN` one usually means an untested class merged. Both are ordinary
  maintenance, and §6.6's rule — rank the module's classes by missed lines before calling a `THIN`
  variation — is how to tell them apart.

### 7.1 Wave 1c closed (2026-08-12)

`pos-marketing` and `pos-inventory` now have a `{Module}PermissionRegistry` test
each. Both read `permissions.yaml` directly (no YAML dependency — the file is
scanned for `- name:` lines) and assert three things about the registry: every
constant matches `domain:resource:action` in snake_case, no name is declared
twice, and the constants and the catalog agree with each other.

The third assertion could not take the same form in both modules, and the
difference is worth recording:

- **pos-marketing** — constants and catalog correspond one-to-one, so the test
  is bidirectional: no constant missing from the catalog, no catalog entry
  without a constant.
- **pos-inventory** — the catalog holds 54 entries against 29 constants, so a
  bidirectional test fails by design. The orphan half was replaced with
  `everyEnforcedAuthorityIsRegistered`, which scans `src/main/java` for
  `hasAuthority("…")` string literals and asserts each one is in the catalog.
  That is the assertion that actually protects production: a `@PreAuthorize`
  naming an authority nobody registered is an endpoint no role can ever reach.
  It found 46 literal-enforced authorities, all present.

The 25 catalog entries with no constant are not a defect — they are permissions
declared ahead of the code that will enforce them. A test that failed on them
would be a test that punishes planning.

### 7.2 The all-zero modules closed (2026-08-12)

| Module | Line before | Line after | Branch after | Floor set |
|---|---|---|---|---|
| `pos-vehicle-reference-carapi` | 0% | 78.3% | 88.9% | 0.73 / 0.83 |
| `pos-vehicle-reference-nhtsa` | 0% | 49.7% | 50.0% | 0.44 / 0.45 |
| `pos-image` | 0% | 31.1% | 100.0% | 0.26 / 0.95 |
| `pos-inquiry` | 0% | 0% | — | none |

`pos-inquiry` was deliberately left alone. It is 16 lines of application
scaffolding with no behaviour of its own — no controller, no service logic. A
test dependency was added and then reverted rather than shipping a test that
asserts the Spring context can start, which pins nothing. It gets a floor when
it gets behaviour.

`pos-image`'s line figure is held down by configuration classes; the controller
itself — the only part with logic — is fully covered, which is why its branch
floor is high and its line floor is low. The tests pin that a database row whose
file is missing from disk returns 404 rather than a 500 or a stream that dies
mid-response. That is the normal state after a restore or a volume remount, not
an exotic one.

The two vehicle-reference modules are 24-hour read-through caches over
third-party APIs (CarAPI, and NHTSA's public vPIC). The tests pin the cache
contract in both directions — fresh cache serves without any outbound call,
stale cache refetches and replaces — and, for NHTSA, the derivation
`UUID.nameUUIDFromBytes("make-" + vpicId)` that makes a refetch update rows
instead of duplicating them.

Writing them surfaced a live defect, filed as
louisburroughs/durion-positivity-backend#1265 and since fixed — see below.

### 7.3 Controller web slices closed (2026-08-12)

| Module | Line before | Line after | Branch after | Floor |
|---|---|---|---|---|
| `pos-vehicle-inventory` | 66.4% | 76.7% | 74.1% | 0.71 / 0.69 |
| `pos-marketing` | 87.4% | 89.7% | 82.9% | 0.84 / 0.77 |
| `pos-people-contact` | 64.1% | 70.1% | 55.9% | 0.65 / 0.50 |
| `pos-customer` | 85.4% | 86.3% | 71.5% | 0.81 / 0.66 |

The slices reuse each module's existing web-slice security config where one
existed; `pos-marketing` needed its first, modelled on `pos-inventory`'s.
Authorities arrive on `X-Authorities` exactly as the gateway supplies them
(ADR-0011/0014), which is what makes per-endpoint permission assertions possible
without standing up the gateway.

**What these tests are for.** Three recurring shapes turned out to carry the risk,
and they are worth naming because they recur across the reactor:

1. *Sibling permissions on adjacent methods.* `pos-marketing`'s campaign
   lifecycle splits across five authorities (create, edit, schedule, manage,
   send) on methods that differ by one word. Nothing structural enforces the
   split — a copy-paste between two of them is invisible after the fact. Each
   action is therefore checked against a neighbouring authority that must *not*
   open it, rather than only against the one that should.
2. *Two implementations of one interface.* `pos-customer`'s `CustomerController`
   holds two `CustomerService` fields, commercial and individual, assigned in the
   opposite order to the constructor's parameters. Correct today; still compiles
   if swapped, and a swap answers every question about the wrong kind of
   customer. Same shape in `pos-people-contact`'s `PostalAddressController`,
   where person and organization endpoints differ by one word in the path, one in
   the `@PreAuthorize`, and one enum constant.
3. *Verbs that disagree about null.* `pos-vehicle-inventory`'s preferences PUT
   and PATCH both accept a null `serviceIntervalMonths` and mean opposite things
   by it (#1175) — clear the override versus leave it alone. Both arrive as an
   absent JSON field, so the verb is the only thing carrying the distinction.

`pos-people-contact`'s `PeopleExceptionHandler` was the largest single uncovered
class in the whole wave (58 missed lines) and is now covered exhaustively. It is
the module's entire error contract — twenty near-identical four-line handlers
where a wrong `HttpStatus` still compiles and still returns a well-formed
ProblemDetail.

Two defects were found and filed by this wave (#1269 and #1270); both have since
been fixed — see below. The two tests that pinned them were written to fail
loudly once the behaviour changed, and are now ordinary assertions.

### 7.4 Branch tails, first pass (2026-08-12)

| Module | Branch before | Branch after | Line after | Floor |
|---|---|---|---|---|
| `pos-catalog` | 53.5% | 57.6% | 78.6% | 0.73 / 0.52 |
| `pos-workorder` | 56.7% | 58.5% | 78.2% | 0.73 / 0.53 |
| `pos-document-helper` | 35.2% | 35.2% (90.0% after #1277) | 74.0% | 0.90 / 0.80 after #1277 |

Attacked the two worst individual classes rather than spreading thinly, on the
view that a branch tail is not uniform — it concentrates in the places where the
code makes a choice the caller cannot see it make.

**`PriceBookServiceImpl.resolvePrice`** (47.1% → 68.8% branch, 90 → 53 missed).
This is the read that decides what a product costs, and almost every branch in it
is silent. It walks two independent precedence chains — which price book applies,
then which rule inside it wins — then falls back to MSRP and finally to "no
price". Every step produces a well-formed answer, so an error does not fail, it
quotes a different number. Only `source` and `fallbackReason` distinguish "this
is the contract price" from "this is list price because nothing matched", and
nothing downstream re-derives them. The tests assert the winning rule and the
source, not that a price came back. Specifics worth keeping:

- Target specificity (SKU > CATEGORY > GLOBAL) is scored before priority, so
  priorities in the fixtures are set to contradict the expected winner.
- `nullsLast` on a reversed comparator decides whether an unprioritised rule
  outranks every deliberately prioritised one; both directions are asserted.
- A fully tied pair is broken by rule id, asserted with the inputs supplied in
  reverse — a tie that resolved by input order would have two servers quoting
  different prices for the same basket.
- Currency selection refuses to guess: an unconfigured requested currency and an
  ambiguous multi-currency rule are both errors, because substituting either
  would hand the caller a number in the wrong denomination.
- Zero and negative amounts are rejected. A rule resolving to nothing or to a
  credit is a data error, and letting it through prices the product at that
  value.

**`WorkorderPickFacadeServiceImpl`** (0% → 100% branch). Turns warehouse scans
into asynchronous inventory commands (ADR-0044, #901). Two families of branch:
the four-way scan grade (MATCHED / SKU_MISMATCH / LOCATION_MISMATCH / NO_MATCH),
whose two mismatch cases are decided by opposite comparisons and are trivially
swappable — a picker acts on that word, and it tells them whether they are at the
wrong bin or holding the wrong part; and the refusal to publish half-formed
commands, which fails closed twice over with a 409 for an incomplete replica and
a 503 for an absent publisher or a broker nack. Those two must stay
distinguishable: a 409 cannot be fixed by retrying, a 503 is exactly what should
be retried.

**`pos-document-helper` is deliberately untouched.** Its entire branch tail — 32
of 35 missed branches — sits in a duplicated copy of the library that no module
consumes (#1274). Testing it would cement a deletion candidate and make the
number look healthy while the duplication stayed. Its floor is unchanged pending
that decision. **Resolved since:** the decision on #1274 kept
`com.positivity.documents` and deleted the `helper` copy, which took the whole
branch tail with it — the module now measures 95.7% line / 90.0% branch and its
floors are 0.90 / 0.80.

### 7.5 Branch tails, second pass (2026-08-12)

| Module | Branch (start of §7) | After pass 1 | After pass 2 | Line | Floor |
|---|---|---|---|---|---|
| `pos-catalog` | 53.5% | 57.6% | **59.7%** | 80.3% | 0.75 / 0.54 |
| `pos-workorder` | 56.7% | 58.5% | **60.4%** | 78.7% | 0.73 / 0.55 |

Same approach as pass 1: the next-worst class in each module, chosen for what its
branches decide rather than for how many there are.

**`ChangeRequestServiceImpl`** (29.5% → 62.1% branch, 93 → 50 missed). A change
request is how extra work gets added to a job the customer already agreed to, so
these branches decide whether someone can be billed for work they never
approved. Two rules carry that:

- *Emergency items must carry evidence.* An emergency/safety item skips the
  normal approval wait, so it needs a photo, or an explicit "photo not possible"
  plus notes. The two checks overlap — the second catches an item that claimed
  photo-not-possible and then supplied nothing — and collapsing them into one
  would let a shop mark anything as an emergency with no record of why. The full
  evidence truth table is asserted, whitespace-only values included on both
  sides.
- *A declined emergency blocks closing the workorder* until the customer has
  acknowledged the denial, on every emergency item, across services **and**
  parts. That is the paper trail for "we told them it was unsafe and they said
  no". The services and parts checks are separate methods with identical bodies,
  so both halves are asserted independently: dropping the parts one leaves the
  services test green.

Also pinned: only a workorder actually in `WORK_IN_PROGRESS` accepts a change
request, checked against every other status via `@EnumSource` exclusion rather
than one sample, because adding work to a job that is finished, invoiced or
cancelled is exactly the case that produces an unagreed bill.

**`ProductDetailServiceImpl`** (37.7% → 54.4% branch, 71 → 52 missed). This view
stitches the catalog row together with live pricing from pos-price and live
availability from pos-inventory, and its whole design is graceful degradation:
when a remote call fails the endpoint still answers, with the parts it could get
and a `confidence` saying how much of it is real. That makes the failure branches
the *normal* operating mode during any partial outage, and invisible from a happy
path because the response still looks complete. The full confidence matrix is
asserted (both up → HIGH, either → MEDIUM, neither → LOW), along with:

- A remote answering successfully with no data is treated exactly like an
  outage — not as a price of zero and not as an error to propagate.
- Null amounts stay null. The BigDecimal-to-double conversions are null-guarded
  on both fields; without the guards this is a NullPointerException, and with a
  naive default it is a free product.
- Unparseable enum values from the wire (`source`, `confidence`) degrade to a
  default rather than throwing, so a new constant added upstream cannot take this
  endpoint down — and an unreadable confidence degrades to MEDIUM, never HIGH.
- A lead-time lookup that throws falls back to the catalog hint while leaving
  availability itself reported as OK, since letting that exception escape would
  turn a working stock read into an outage.

Both modules still have tails below their new floors — the next candidates are
`SupplierItemCostServiceImpl` (44 missed) and `EstimateServiceImpl` (82) — but
item 4's stated goal, moving the three named modules off their branch floors with
parameterized tests over real decisions, is met.

### 7.6 Shared libraries closed (2026-08-13)

| Module | Line before | Line after | Branch before | Branch after | Floor |
|---|---|---|---|---|---|
| `pos-tax-common` | 34.0% | **90.0%** | 46.4% | **75.0%** | 0.85 / 0.70 |
| `pos-security-common` | 46.8% | **79.6%** | 36.5% | **80.7%** | 0.74 / 0.75 |
| `pos-domain-events` | 39.3% | **87.1%** | 37.2% | **82.9%** | 0.82 / 0.77 |

**`pos-security-common` is the trust boundary**, so it went first.
`SecurityContextHelper` (65 lines, 0%) is how every downstream service learns who
the caller is; the tests hold down that it *fails loudly* rather than falling
back — absent, unauthenticated and anonymous contexts all throw, because a helper
returning an empty authority set would make "no security context" look identical
to "this user lacks a permission". Anonymous is checked explicitly: Spring marks
those tokens authenticated, so without the principal check an unauthenticated
request would be written into audit rows as a user named "anonymous".
`PermissionManifestLoader` (44 lines, 0%) fails startup on every malformed input,
which is the right outcome — a partially registered manifest brings the service
up with a few endpoints unreachable for everyone. And the JWT half of
`GatewayAuthoritiesFilter` had no tests at all: it parses the payload without
verifying the signature (the gateway already did), so every malformed token has
to be handled defensively there, and its failure policy is asymmetric — an
unreadable token drops the userId but keeps the authorities, so a regression
produces audit rows with no author rather than a failed request.

**`pos-domain-events` was closed with one sweep instead of sixty test classes.**
Around sixty near-identical records, thirteen with tests. `DomainEventContractTest`
enumerates them off the classpath and asserts what must hold for all: EVENT_TYPE
shape, EVENT_TYPE uniqueness across the module (only visible from the whole set —
a duplicate delivers one fact to another's listener), a positive SCHEMA_VERSION,
and that every record constructs. Four records with cross-field invariants are
named and excluded rather than filtered by a heuristic, and the two of those that
had no test now have one.

Two things the sweep corrected in my own assumptions, worth recording because
they change what the test can claim:

- `@NonNull` in this module is **jspecify** — static analysis, no runtime effect.
  Only 37 of 64 records add explicit guards. An earlier draft asserted universal
  null rejection and produced 62 failures; that was asserting a rule that does
  not exist. The test now verifies guards where they exist and holds a floor on
  how many records have them.
- There are **two** `BillingRulesUpdatedV1` classes, in `invoice` and `customer`.
  Any allowlist here has to key on the fully-qualified name.

**`pos-tax-common`** is enums and one DTO — the wire contract between pos-tax and
its callers. Deliberately kept dependency-free: the module ships
`jackson-annotations` only, so rather than adding databind to a shared library the
serialization contract is asserted through the annotations themselves plus
`fromValue`. Jurisdiction resolution is pinned as case-insensitive but exact:
`"state "` and `"STATES"` are rejected rather than resolving to a neighbouring
level of government.

One defect filed: #1279.

### Defects found by this work, still open

- louisburroughs/durion-positivity-backend#1245 — a partial customer update
  silently deletes every VIN on the party.
- louisburroughs/durion-positivity-backend#1246 — `CommercialPartyServiceImpl`
  transposes `legalName` and `displayName` between read and write.
- louisburroughs/durion-positivity-backend#1254 — `SubdivisionForCountryValidator`
  rejects every real Canadian province.
- louisburroughs/durion-positivity-backend#1255 — replica listeners silently drop
  events whose payload omits a primitive field (Jackson 3).
- ~~louisburroughs/durion-positivity-backend#1265~~ — closed 2026-08-13.
  `pos-vehicle-reference-nhtsa` inverted its own 24h cache: `isCacheExpired`
  computed "is still fresh", and three of six call sites negated it, so those
  methods called vPIC on every request while the cache was warm and then served
  permanently frozen rows once it was not. The helper is now `isCacheFresh` in
  both vehicle-reference modules, with every call site unnegated — including
  `pos-vehicle-reference-carapi`, where the two inversions had cancelled out and
  the behaviour was correct only by accident.
- ~~louisburroughs/durion-positivity-backend#1269~~ — closed 2026-08-13.
  `pos-vehicle-inventory` had no `@ControllerAdvice` at all, so a wrong-length VIN
  on `@Validated` `GET /vin/{vin}` raised `ConstraintViolationException` and
  surfaced as 500, and no error from the module carried the `ApiError` envelope
  required by `docs/ERROR_ENVELOPE.md`. `VehicleExceptionHandler` now maps
  constraint violations, body validation, `EntityNotFoundException` and
  `IllegalArgumentException` into the envelope, and the hand-rolled `try/catch`
  blocks in `VehicleRegistryController` and `VehicleController` are gone.
- ~~louisburroughs/durion-positivity-backend#1270~~ — closed 2026-08-13.
  `POST /v1/vehicles/search` was broken for every request: `SearchVehiclesRequest`
  was `@Builder` with final fields and no `@Jacksonized`, so Jackson had no
  creator and message conversion failed before the controller was entered. The GET
  route builds the object in Java, which is why nothing caught it. `@Jacksonized`
  now wires the builder up as the creator. A sweep for the same shape — a
  `@Builder` class with no Jackson creator used as a `@RequestBody` — found no
  other instance in the reactor.
- ~~louisburroughs/durion-positivity-backend#1274~~ — **fixed** by PR #1277, which
  deleted the duplicate `com.positivity.documents.helper` tree. The module went
  from 35.2% to 90.0% branch coverage by deletion rather than by testing; see
  §7.4.
- louisburroughs/durion-positivity-backend#1279 — 16 of 64 event records in
  `pos-domain-events` declare no `SCHEMA_VERSION`, so their publishers hardcode
  the envelope version in another module. Correct today, but a schema bump now
  requires editing a numeric literal nowhere near the record it describes.
- louisburroughs/durion-positivity-backend#1267 — `pos-vehicle-reference-carapi`
  conflates its own primary key with CarAPI's make id inside one method, so no
  argument to `GET /models/{makeId}` is correct for all three of its uses; the
  `CarApiModelResponse.makeId` field also contradicts its own schema. Raised by
  Copilot on PR #1266, which spotted the DTO half.


## 8. Standing conventions for every test in this repository

These bind every test written in `durion-positivity-backend`, not only tests written to raise
coverage. Per `.agents/skills/java-testing` and repo conventions:

- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) + AssertJ.
- `@ParameterizedTest` for branch coverage rather than repeated near-identical
  `@Test` methods.
- Unit tests named `*Test.java` (Surefire, `test` phase); anything needing a
  database or Spring context named `*IT.java` (Failsafe, `verify` phase).
- `@WebMvcTest` slices for controllers, not full `@SpringBootTest`.
- Run `./mvnw spotless:apply` before commit; Checkstyle/SpotBugs gate the build.
- Run `./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test` if any package
  layout is touched.
- No coverage-inflating assertion-free tests. Every added test must assert a
  behavior that could plausibly regress.
