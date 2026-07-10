---
title: Durion DevOps Framework — Autonomous Environment Lifecycle Management
status: Draft specification
created: 2026-07-10
scope: All Durion tenant cells (integration, prototype, production) on both runtime substrates
depends-on:
  - ../FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md
  - ../PHASED_CICD_AND_RUNTIME_PLAN.md
---

# Durion DevOps Framework — Autonomous Environment Lifecycle Management

## Purpose

This framework specifies how Durion tenant cells are autonomously groomed, right-sized, and
pre-warmed so that each cell runs as lean as possible off-peak while guaranteeing full peak
performance within a bounded ramp time.

The framework covers four capabilities:

1. **Environment grooming** — recurring hygiene work (disk, process, datastore, OS memory)
   executed during off-peak windows so it never competes with business traffic.
2. **Metric-driven right-sizing** — continuous recomputation of resource allocations
   (container CPU/memory, JVM heap, replica counts, storage volumes) from observed
   utilization, applied autonomously within guardrails.
3. **Cache pre-loading** — warming application caches, JIT-compiled code paths, database
   working sets, and SSR/CDN edges ahead of predicted demand, when profitable.
4. **Lean/peak state management** — a per-cell operating-state machine that sheds capacity
   off-peak and restores peak readiness within the ramp SLO.

## Governing Decisions (decision record)

These decisions were made by the platform owner and are binding on all companion specs.
The rationale is captured in
[ADR-0045](../../../adr/0045-autonomous-environment-lifecycle-management.adr.md).

| # | Decision | Value |
|---|----------|-------|
| D1 | Runtime substrates | Both, phased: single-host EC2 Docker/Podman profile now, ECS Fargate profile later. Policies are substrate-neutral; execution adapters are per-substrate. |
| D2 | Peak model | Per-tenant business hours, decomposed into four peak classes: POS floor, back office, batch, integration/external. |
| D3 | Autonomy | Fully autonomous execution with mandatory after-action reports and notifications. No human pre-approval in the loop. |
| D4 | Objective function | Minimize cost, subject to the ramp SLO (D5) as a hard constraint. |
| D5 | Ramp SLO | A cell must reach full peak capacity with warm caches within **10 minutes** of a ramp trigger. |
| D6 | Metrics authority | Prometheus + Micrometer is the authoritative decision-input source. CloudWatch is used only for AWS-native signals (billing, EBS, host-level). |
| D7 | Cache warm-up catalog | JVM/application caches, JIT/class warm-up, database working set, SSR/CDN edge. |
| D8 | Grooming scope | Disk hygiene, JVM process recycling, datastore maintenance, OS-level memory management. |
| D9 | Right-sizing scope | Container CPU/memory + JVM heap, service replica counts, storage volumes. EC2 instance type changes are **out of scope** for autonomous action (recommendation-only). |
| D10 | Reporting | Structured after-action records in the `durion` repo ops log, daily digest, immediate push notification only on anomalies, rollbacks, or guardrail hits. |
| D11 | Safety contract | Every action is health-gated with a soak period; failures auto-revert to last known-good and quarantine the action for 7 days. |

## Architecture Overview

### The Cell Operations Agent

Each tenant cell is managed by one **Cell Operations Agent (COA)** — the autonomous control
loop that owns grooming, right-sizing, warm-up, and state transitions for that cell. The COA
is part of the control plane (per the tenant-cell architecture): it operates the cell but does
not serve business traffic and has no access to business data beyond operational metadata.

```text
                 ┌────────────────────────────────────────────┐
                 │              Control Plane                 │
                 │                                            │
  Prometheus ───▶│  ┌──────────────┐   ┌──────────────────┐  │
  (per cell)     │  │ Peak-Class    │   │  Decision Engine │  │
                 │  │ Calendar +    │──▶│  (policies from  │  │
  CloudWatch ───▶│  │ Forecaster    │   │  companion specs)│  │
  (AWS-native)   │  └──────────────┘   └────────┬─────────┘  │
                 │                               │            │
                 │                      ┌────────▼─────────┐  │
                 │                      │ Action Executor  │  │
                 │                      │ (health-gated,   │  │
                 │                      │  revertible)     │  │
                 │                      └────────┬─────────┘  │
                 │                               │            │
                 │  ┌──────────────────┐         │            │
                 │  │ After-Action Log │◀────────┘            │
                 │  │ + Notifications  │                      │
                 │  └──────────────────┘                      │
                 └───────────────┬────────────────────────────┘
                                 │ substrate adapter
              ┌──────────────────┴──────────────────┐
              ▼                                     ▼
   EC2 profile: Docker/Podman API,        Fargate profile: ECS APIs,
   compose scale, systemd timers,         Application Auto Scaling,
   host shell tasks                       EventBridge Scheduler
```

Components:

- **Peak-Class Calendar + Forecaster** — knows each cell's expected demand per peak class
  (see below) and produces the schedule of state transitions. Static calendar first;
  forecast corrections from observed history later.
- **Decision Engine** — evaluates policies from the companion specs against Prometheus
  queries and emits *action intents*.
- **Action Executor** — runs intents through the mandatory action contract (pre-checks,
  execution, soak, verdict, revert). It is the only component allowed to mutate a cell.
- **After-Action Log + Notifications** — every executed intent produces a structured record
  per the [Reporting and Rollback Contract](./REPORTING_AND_ROLLBACK_CONTRACT.md).
- **Substrate adapter** — translates abstract actions ("set service X memory to 768Mi",
  "scale service Y to 2") into Docker/compose operations (alpha) or ECS/Auto Scaling
  operations (target). Policies never reference substrate primitives directly.

### Peak-class model

Load on a cell is not one curve; it is four overlapping curves with different shapes,
different services, and different SLO sensitivity. All scheduling, sizing, and warm-up
decisions are made **per peak class**, then merged into a per-service capacity plan.

| Peak class | Typical window (tenant-local) | Dominant services | Sensitivity |
|------------|-------------------------------|-------------------|-------------|
| `pos-floor` | Shop business hours | gateway, order, workorder execution, pricing, inventory, frontend SSR | Highest — user-facing counter latency |
| `back-office` | Business hours ± administrative shoulder (early morning, post-close) | accounting, billing, people, reporting paths | High — interactive but tolerant of brief degradation |
| `batch` | Off-peak by design (overnight) | accounting close, inventory recalculation, document generation, backups | Throughput-sensitive, latency-insensitive; **competes with grooming** and must be coordinated in the same window planner |
| `integration-external` | Potentially any time (partner APIs, positivity integrations, webhooks) | api gateway, positivity/integration services | Sets the floor: these services can never scale to zero |

Rules:

- Each cell carries a **peak-class calendar**: per class, the expected active windows in the
  cell's effective time zone, plus exceptions (holidays, promotions, seasonal overrides).
- The merged plan gives each service a target capacity per time slice. A service active in
  multiple classes gets the max of its class targets.
- The `integration-external` class defines each cell's **warm floor** — the minimum capacity
  that is always running (gateway, security service, integration endpoints, datastore).
- The `batch` class is a *scheduled consumer* of the off-peak window: the window planner
  allocates batch work and grooming work into the same window without overlap on the same
  resource (e.g., no VACUUM during the accounting close batch on the same database).

### Cell operating states

Each cell is always in exactly one operating state. The COA owns the transitions.

| State | Meaning | Capacity posture |
|-------|---------|------------------|
| `PEAK` | One or more interactive peak classes active | Full merged capacity plan, caches warm |
| `SHOULDER` | Low interactive demand expected, but not a maintenance window | Reduced replicas/limits down to per-service shoulder targets; warm floor maintained |
| `GROOMING` | Off-peak maintenance window | Warm floor only + whatever the active grooming/batch tasks need; grooming catalog executes here |
| `RAMP` | Transition into `PEAK` | Restore capacity + execute warm-up choreography; must complete within the 10-minute SLO |

Transition triggers:

- **Scheduled**: the peak-class calendar drives the normal daily cycle. `RAMP` starts at
  `peak_start − ramp_lead_time`, where `ramp_lead_time` is measured per cell (observed ramp
  duration + 25% margin, capped at 10 minutes).
- **Reactive**: a metric trigger (sustained request-rate or latency burn against shoulder
  capacity) forces `RAMP` immediately at any time. Reactive ramp is the reason the SLO is a
  hard constraint: surprise load during `SHOULDER`/`GROOMING` must reach full capacity in
  ≤ 10 minutes.
- **Abort rule**: a reactive ramp trigger during `GROOMING` aborts or pauses in-flight
  grooming tasks at their next safe checkpoint. Every grooming task in the catalog must
  declare its abort behavior (instant, checkpoint, or must-complete).

### Accelerated-time cells

Cells with accelerated or simulated clocks (per the tenant-cell time model) do not follow
wall-clock business hours. For those cells:

- The peak-class calendar is expressed against the **effective cell clock**, and the COA
  queries the cell's time authority to map calendar windows to wall-clock execution times.
- Grooming that depends on real-world quiet (host disk pruning, backups) still schedules by
  wall clock; grooming that depends on business quiet (datastore maintenance, process
  recycling) schedules by cell clock.
- Right-sizing lookback windows for accelerated cells use cell-clock durations so that
  "14 days of history" means 14 business days of simulated activity.

## The Action Contract

Every mutation the framework performs — grooming task, resize, scale change, warm-up — is an
**action** and must implement this contract. This is what makes full autonomy (D3) safe.

Each action declares:

1. **Identity** — action type, target cell, target resource, parameters, initiating policy.
2. **Preconditions** — cell state requirements, resource health requirements, mutex claims
   (e.g., only one process-recycle at a time per cell; no resize during `RAMP`).
3. **Execution** — the substrate-adapter operations, with a bounded execution time.
4. **Health gates** — the probe set that must pass after execution (service `/actuator/health`,
   synthetic transaction checks, error-rate and latency queries against Prometheus).
5. **Soak period** — how long the health gates are watched before the action is declared
   successful (default 15 minutes; per-action overrides allowed).
6. **Revert procedure** — how to restore the previous known-good configuration. Every action
   must be revertible or explicitly declared irreversible (irreversible actions — e.g., file
   deletion — must instead satisfy stricter preconditions, such as verified backup or
   age/retention proof).
7. **After-action record** — emitted on every terminal outcome (success, reverted, aborted),
   per the reporting contract.

Failure handling is uniform: health-gate failure or SLO burn during soak → automatic revert →
action type quarantined for that cell for 7 days → anomaly notification. Details in the
[Reporting and Rollback Contract](./REPORTING_AND_ROLLBACK_CONTRACT.md).

## Substrate Profiles

Policies are identical across substrates; only the executor differs.

### Profile A — single-host EC2 (alpha, current)

- **Scheduler**: systemd timers (or cron) on the host invoke the COA runner; the COA runs as
  a control-plane container with access to the Docker/Podman socket and a restricted host
  task allowlist.
- **Replica scaling**: `docker compose up --scale`; most alpha services run one replica, so
  the dominant levers are per-container CPU/memory limits, JVM flags, and stop/start of
  non-floor services during `GROOMING`.
- **Resize**: recreate container with updated resource limits (rolling, one service at a
  time, health-gated).
- **Storage**: EBS volume metrics via CloudWatch; gp3 size/throughput changes via AWS API
  within guardrails.
- **Constraint**: host memory is a shared pool — the right-sizer must maintain the invariant
  `Σ container memory limits + host reserve ≤ host RAM` at all times.

### Profile B — ECS Fargate (target)

- **Scheduler**: EventBridge Scheduler drives state transitions; the COA runs as a
  control-plane ECS service.
- **Replica scaling**: Application Auto Scaling scheduled actions (calendar) + target-tracking
  policies (reactive), parameterized by the COA per peak class.
- **Resize**: new task definition revision with updated CPU/memory; rolling deployment,
  health-gated by target-group health.
- **Warm floor**: minimum `desiredCount` per service derived from the `integration-external`
  class; scale-to-zero allowed only for services with no external-facing routes.
- **Storage**: EBS→EFS/S3 equivalents per service; same growth-based policy.

Portability rule: no policy, threshold, calendar, or catalog entry may encode a substrate
primitive. Anything that would only make sense on one substrate lives in the executor
adapter.

## Companion Specifications

| Spec | Contents |
|------|----------|
| [Grooming Catalog](./GROOMING_CATALOG.md) | The full catalog of grooming tasks (disk, process, datastore, OS memory), each specified against the action contract |
| [Right-Sizing Policy](./RIGHTSIZING_POLICY.md) | Metric inputs, per-asset sizing algorithms, guardrails, evaluation cadences, cost accounting |
| [Cache Warm-Up Catalog](./CACHE_WARMUP_CATALOG.md) | Warm-up job catalog, profitability model, and the 10-minute ramp choreography |
| [Reporting and Rollback Contract](./REPORTING_AND_ROLLBACK_CONTRACT.md) | After-action record schema, digest and notification rules, health-gate/soak/revert/quarantine mechanics |

## Rollout Phasing

Aligned with the [Phased CI/CD and Runtime Plan](../PHASED_CICD_AND_RUNTIME_PLAN.md):

| Framework phase | Contents | Runtime-plan alignment |
|-----------------|----------|------------------------|
| F1 | Metrics foundation: Micrometer coverage audit, Prometheus per-cell scrape, peak-class recording rules, utilization dashboards | Phase 1 (integration runtime) |
| F2 | Grooming catalog on the EC2 profile: disk hygiene first, then process recycling and datastore maintenance, all with after-action records | Phase 1–3 |
| F3 | State machine + calendar: `PEAK`/`SHOULDER`/`GROOMING`/`RAMP` transitions and cache warm-up choreography on prototype cells | Phase 3–4 |
| F4 | Right-sizing engine: recommendation mode for two observation cycles, then autonomous within guardrails | Phase 3–5 |
| F5 | Fargate profile: substrate adapter for ECS, scheduled+target-tracking scaling, migration of policies unchanged | Post-Phase 5 / ECS migration |

Note on F4: even under full autonomy (D3), the right-sizer's first two evaluation cycles per
cell run in recommendation mode to establish a baseline and validate its inputs — this is a
calibration step, not a human-approval gate.

## Out of Scope

- EC2 instance-type changes (recommendation-only; requires human execution).
- CI/CD pipeline behavior, release promotion, and deployment mechanics (owned by the phased
  runtime plan).
- Application-level performance optimization (query tuning, code changes) — the framework
  reports hotspots but does not change code.
- Cross-cell fleet optimization (bin-packing multiple cells onto shared hosts) — deferred
  until multi-cell density becomes a cost driver.
