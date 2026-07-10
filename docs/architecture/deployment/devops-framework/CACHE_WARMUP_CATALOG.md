---
title: Cache Warm-Up Catalog — Profitable Pre-Loading and Ramp Choreography
status: Draft specification
created: 2026-07-10
parent: ./DEVOPS_FRAMEWORK.md
---

# Cache Warm-Up Catalog — Profitable Pre-Loading and Ramp Choreography

## Purpose

This catalog specifies the warm-up jobs the Cell Operations Agent runs so that a cell enters
`PEAK` with hot caches, compiled code paths, a primed database working set, and warm edges —
and specifies the **ramp choreography** that fits all of it inside the 10-minute ramp SLO.

Warm-up runs in two situations:

1. **Ramp** (`RAMP` state): scheduled pre-peak or reactive, the full choreography.
2. **Post-restart**: after any process recycle (G-201) or resize application (S-1), the
   affected service runs its own warm-up jobs before it is declared healthy-warm.

## Profitability Model

"Pre-load caches when profitable" is made precise: a warm-up job runs only when its expected
benefit exceeds its cost.

Per job, the COA tracks from after-action records and Prometheus:

- **Benefit** = (cold-hit latency − warm-hit latency) × expected hits in the first
  30 peak-minutes. Expected hits come from the same-class historical request profile.
- **Cost** = warm-up execution time (occupies ramp budget) + resources consumed + any load
  imposed on the datastore.

Rules:

- A job with measured benefit/cost below 1.0 for 5 consecutive ramps is automatically
  demoted to `disabled`, with the demotion in the daily digest. Jobs are re-scored monthly
  (a demoted job whose underlying data changed can return).
- Jobs are classed `critical` (always run during ramp; part of the SLO path) or
  `opportunistic` (run only if ramp budget remains; skipped first under time pressure).
- Reactive ramps (surprise load) skip all `opportunistic` jobs — serving real traffic
  slightly cold beats delaying capacity.

## Warm-Up Job Catalog

### W-1: JVM / application data caches (`critical`)

Per-service warm-up of in-process caches (Caffeine/Spring Cache) via a standardized internal
endpoint each service implements: `POST /internal/warmup` (internal network only, no gateway
route). Each service's endpoint loads its declared warm set:

| Service domain | Warm set (initial) |
|----------------|--------------------|
| Pricing | Active price lists, fee schedules, tax config |
| Product/catalog | Hot catalog slice (top-N SKUs by same-class historical access) |
| Security | Role/permission model, JWT validation keys |
| People/roles | Active users and role assignments for the tenant |
| Location | Tenant location tree |
| Order/workorder execution | Reference/status models; today's open workorders index |

Contract requirements: idempotent, bounded duration (declares a budget, default ≤ 60 s),
returns per-cache entry counts + duration (fed to profitability scoring), and safe to call
on an already-warm service.

### W-2: JIT / code-path warm-up (`critical` for pos-floor services)

Data caches don't compile code. After start, hot request paths run interpreted until the JIT
promotes them — first-customer-of-the-day latency suffers even with warm data.

- **Mechanism (initial)**: synthetic transaction driver replays a scripted set of
  representative read-path requests through the gateway against reserved synthetic entities
  (same fixture approach as the deployment smoke suite), tagged with a synthetic-traffic
  header so they are excluded from business metrics and audit streams. Write paths are
  warmed only via internal dry-run endpoints where a service provides them — synthetic
  writes never touch business tables.
- **Volume**: enough iterations to promote hot paths (initial default: 200 iterations per
  route set, tuned by observing first-real-request latency in after-action data).
- **Future optimization**: evaluate AppCDS + (when substrate allows) CRaC checkpoint/restore
  to replace replay-based JIT warm-up with restored pre-warmed JVMs. Tracked as a framework
  enhancement; the choreography treats it as a drop-in replacement for W-2.

### W-3: Database working set (`critical`)

Prime Postgres shared buffers and the OS page cache with the day's working set before open:

- `pg_prewarm` on the hot table/index list (curated per domain, refined from
  `pg_stat_user_tables`/`pg_statio` access stats).
- Day-specific priming queries: today's (cell-clock) appointments, open workorders, active
  inventory locations — parameterized by the cell's effective date.
- Ordering: W-3 starts first in the choreography (it is IO-bound and benefits everything
  downstream); bounded to the datastore's grooming-window IO ceiling so it never competes
  with a still-running backup.

### W-4: SSR / CDN edge (`critical` for frontend entry routes, `opportunistic` beyond)

- Pre-render and prime the Angular SSR cache for the hot route list (the same 8 smoke routes
  plus top-N routes by historical first-hour traffic).
- Prime CDN edge (when CDN is active per the CDN architecture doc) by requesting hot routes
  and static assets through the public edge, cache-warming headers set, after the origin is
  warm.
- Runs last in the choreography — it depends on warm backend + SSR origin.

## Ramp Choreography (the 10-minute budget)

Target internal budget: **8 minutes** end-to-end, leaving 2 minutes of SLO margin.

| Phase | Time budget | Work |
|-------|-------------|------|
| R0 Capacity restore | 0:00–2:00 | Substrate adapter restores peak replica counts/limits (start stopped services, scale up, Fargate desired-count raise). Datastore + warm floor are already up. W-3 starts immediately in parallel (datastore is warm-floor, doesn't wait for R0). |
| R1 Service health | 1:00–4:00 | Services reach `/actuator/health` UP + discovery registration (staggered, dependency-ordered — same ordering as the compose health chain). |
| R2 Data cache warm | 3:00–6:00 | W-1 fan-out: `POST /internal/warmup` to every service as it becomes healthy (per-service, not barrier — a slow service doesn't block warm-up of the rest). |
| R3 JIT warm | 5:00–7:30 | W-2 synthetic replay through the gateway, overlapping the tail of R2. |
| R4 Edge warm + verify | 7:00–8:00 | W-4 SSR/CDN priming; ramp verification probe: synthetic p95 for the pos-floor route set must be within 1.5× steady-state p95. |

Rules:

- Phases overlap per-service (pipeline, not barriers): each service proceeds through
  health → data warm → JIT warm independently; the phase windows above are the budget
  envelope, not synchronization points.
- The choreography is instrumented per phase; every ramp writes an after-action record with
  per-phase durations and the verification-probe result.
- **SLO breach handling**: if a ramp exceeds 10 minutes or fails the verification probe,
  that is an anomaly (immediate notification) and auto-feedback: the calendar's
  `ramp_lead_time` for that cell is increased by the shortfall (up to the 10-minute cap),
  and shedding depth is reduced (fewer services fully stopped in `GROOMING`) until ramps
  meet the SLO again. Chronically failing cells therefore trade savings for reliability
  automatically — cost yields to the SLO, per the objective function.
- Scheduled ramps start at `peak_start − ramp_lead_time` where `ramp_lead_time` is the
  cell's observed p95 ramp duration + 25%, capped at 10 minutes.
- Reactive ramps run the same choreography with `opportunistic` jobs skipped and traffic
  admitted as soon as R1 completes — warm-up then runs concurrently with real traffic
  (real traffic is itself a warmer; the choreography just accelerates it).

## Post-Restart Warm-Up (single service)

After G-201 recycle or S-1 resize of one service: run that service's W-1 job, plus its slice
of W-2 if it is a pos-floor-critical service, inside the action's soak period. A restarted
service is not "successful" (in the action-contract sense) until warm — this is what makes
grooming invisible to the next peak.
