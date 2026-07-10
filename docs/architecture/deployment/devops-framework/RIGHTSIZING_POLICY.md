---
title: Right-Sizing Policy — Metric-Driven Resource Allocation
status: Draft specification
created: 2026-07-10
parent: ./DEVOPS_FRAMEWORK.md
---

# Right-Sizing Policy — Metric-Driven Resource Allocation

## Purpose

This policy specifies how the Cell Operations Agent continuously recomputes resource
allocations from observed utilization and applies them autonomously within guardrails.
The objective function is fixed by framework decision D4: **minimize cost subject to the
10-minute ramp SLO and peak performance SLOs**.

In scope for autonomous action (D9):

- **S-1** Container CPU/memory limits and JVM heap settings
- **S-2** Service replica counts per peak class
- **S-3** Storage volumes (size, gp3 IOPS/throughput, archive tiering)

Out of scope for autonomous action: EC2 instance type. The engine computes instance-level
recommendations (aggregate host utilization vs. instance capacity) and files them in the
daily digest for human execution.

## Metric Inputs

Authoritative source: Prometheus, scraping Micrometer/Actuator from every service, plus node
exporter (EC2 profile) and cAdvisor/container metrics. CloudWatch supplies only AWS-native
series (EBS metrics, billing). All decisions read from **recording rules**, not raw series,
so the decision inputs are versioned and auditable.

Required per-service series (peak-class labeled):

| Series | Use |
|--------|-----|
| `container_cpu_usage` p50/p95/p99 by window | CPU limit sizing |
| `container_memory_rss` / working set p99 + max | Memory limit sizing |
| `jvm_memory_used{area="heap"}` after full GC (live set) | Heap sizing |
| `jvm_gc_pause` p99 and GC CPU share | Heap sufficiency check |
| `http_server_requests` rate + p95/p99 latency | Replica sizing, SLO burn detection |
| `hikaricp_connections_*` | Pool sizing input, recycle signals |
| Volume usage + growth rate (node exporter / CloudWatch) | Storage sizing |
| Per-service peak-class attribution (route-class mapping recording rule) | Splitting demand across the four peak classes |

Lookback: trailing **14 days** by default (cell-clock days for accelerated cells), weighted
toward same-peak-class windows, i.e., peak limits are computed from peak-window samples,
shoulder targets from shoulder-window samples. A cell needs at least 7 days of history
before autonomous sizing; before that, sizes come from the release definition's declared
defaults.

## Evaluation Cadence and Change Damping

| Asset | Evaluation | Application |
|-------|-----------|-------------|
| S-1 limits/heap | Recomputed daily | Applied at most once per 3 days per service, in the grooming window, rolling one service at a time |
| S-2 replica plan | Recomputed daily | Calendar targets applied on next state transition; reactive triggers apply immediately |
| S-3 storage | Recomputed weekly + growth watchdog (G-105) | Applied when projection thresholds hit |

Damping rules (apply to all assets):

- Maximum step per application: **±25%** of current value (prevents oscillation and bounds
  the blast radius of a bad input).
- Hysteresis: a downsize requires the computed target to hold for 3 consecutive evaluations;
  an upsize applies on the first evaluation (asymmetric on purpose — being too small hurts
  users, being too big hurts only cost).
- Quarantine: an asset whose change was auto-reverted is frozen for 7 days (framework D11).

## S-1: Container CPU/Memory and JVM Heap

Sizing formulas (initial; constants are per-cell policy and tunable):

- **Memory limit** = `max(p99 working set over lookback, current live set × 1.3) × 1.2`,
  rounded up to the substrate's allocation granularity.
- **JVM heap (-Xmx)** = `live set after full GC × 1.5`, bounded so that
  `heap + metaspace + observed native overhead ≤ 85% of container memory limit`.
  Heap and container limit always change together, in one action.
- **CPU limit** = `p95 CPU over same-class windows × 1.4` with a floor that keeps observed
  GC CPU share < 10% and startup time compatible with the ramp SLO. CPU is compressible —
  throttling degrades rather than kills — so CPU sizing may be more aggressive than memory.

Guardrails:

- Per-service floor and ceiling declared in the release definition (e.g., gateway never
  below 512 MiB; no service above 4 GiB without an instance-level recommendation).
- GC health veto: if `jvm_gc_pause` p99 > 200 ms or GC CPU share > 10% at the proposed heap,
  the downsize is rejected regardless of memory headroom.
- EC2 profile invariant: `Σ container memory limits + host reserve (2 GiB) ≤ host RAM` —
  checked before every application; violations convert the action into an instance-type
  recommendation instead.
- Application is always rolling, health-gated, one service at a time, inside the grooming
  window (it implies a restart on both substrates).

## S-2: Service Replica Counts

The replica plan assigns each service a target count per (peak class × time slice), merged
into the calendar targets used by the state machine:

- **Peak target** = capacity to serve p99 same-class demand at SLO latency + one unit of
  headroom (N+1) for `pos-floor`-critical services.
- **Shoulder target** = capacity for p95 shoulder demand; single replica where that suffices.
- **Grooming floor** = warm floor from the `integration-external` class: gateway, security
  service, integration-facing services, datastore, frontend SSR (1 replica). Everything
  else may stop entirely (EC2 profile: container stopped; Fargate: desiredCount 0), because
  a stopped service can restart and warm within the ramp budget.

Reactive scaling (both substrates): sustained latency burn or request-rate breach at current
capacity triggers immediate scale-up to the next plan level without waiting for a calendar
transition; reactive scale-ups are never damped. On Fargate this is implemented as
target-tracking policies parameterized by the COA; on EC2 it is a COA-driven compose scale
with host-capacity checks.

Alpha reality note: most services run one replica on the single host, so S-2 on Profile A
mostly decides *which services are stopped off-peak*, plus 1→2 scaling for the few services
where the host has headroom. Full replica-count optimization becomes meaningful on Fargate.

## S-3: Storage Volumes

- **Expansion**: when the growth watchdog (G-105) projects days-to-full < 30, expand the
  volume to `current used × 1.5` (bounded by the ±25% step where the substrate allows
  arbitrary sizing; EBS expansions are irreversible, so expansions use the projection gate
  rather than a revert path).
- **gp3 tuning**: recompute provisioned IOPS/throughput monthly from p99 EBS metrics
  (CloudWatch); downsize provisioned performance only with 3-evaluation hysteresis.
- **Archive tiering**: files matching the cold-data policy (documents, exports, logs older
  than the on-host retention) move to S3 with lifecycle rules; deletion on-host only after
  checksum-verified upload. Tiering actions are grooming-window work and report bytes moved
  and monthly cost delta.
- **Shrinkage**: EBS volumes cannot shrink in place; when p99 usage < 40% for a full month,
  the engine files a volume-migration recommendation in the digest (human-executed, like
  instance type).

## Cost Accounting

Every applied sizing action records its expected monthly cost delta (from instance/Fargate/EBS
price tables) in its after-action record. The daily digest aggregates: current cell cost
posture, savings applied this period, savings available but blocked (guardrails, quarantine,
pending recommendations). This is the feedback loop that proves the "lean" objective is being
met — and the evidence base for the human-executed recommendations (instance type, volume
migration).

## Calibration Mode

For each cell, the engine's first two evaluation cycles run in **recommendation mode**:
intents are computed, recorded, and reported but not applied. This validates metric coverage
and formula sanity per cell before autonomous application begins. Calibration is per-cell,
automatic, and exits without human action (framework D3 — this is calibration, not approval).
