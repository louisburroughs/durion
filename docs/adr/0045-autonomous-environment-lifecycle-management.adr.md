# ADR-0045: Autonomous Environment Lifecycle Management

**Status:** PROPOSED
**Date:** 2026-07-10
**Deciders:** Platform Owner, Chief Architect
**Affected Issues:** implementation stories `docs/stories/devops-*.md`

---

## Context

- **Current State**: Durion tenant cells run at static capacity around the clock. The alpha
  substrate is a single EC2 host running all services as Docker containers; the target
  substrate is ECS Fargate. There is no scheduled hygiene (disk, JVM, datastore), no
  metric-driven resource sizing, and no cache pre-warming — first-of-day requests hit cold
  JVMs and cold caches, and off-peak hours burn peak-sized spend.
- **The Problem**: The platform needs to run as lean as possible during off-peak periods
  while guaranteeing full peak performance, without adding recurring human operations toil
  per cell. As tenant-cell count grows, per-cell manual operation does not scale.
- **Drivers**: cost per tenant cell, peak-hour POS counter latency, single-host disk/memory
  fragility, and the agent-operated delivery model (operations should produce
  machine-readable evidence like everything else in the platform).
- **Scope**: all tenant cells (integration, prototype, production) on both substrates.
  Full specification: [DevOps Framework](../architecture/deployment/devops-framework/DEVOPS_FRAMEWORK.md).

---

## Decision

Adopt an autonomous environment lifecycle management framework, executed per tenant cell by
a control-plane **Cell Operations Agent**, covering off-peak grooming, metric-driven
right-sizing, cache pre-loading, and a lean/peak operating-state machine.

### 1. Autonomy model

**Decision:** ✅ **Resolved** — Fully autonomous execution with mandatory after-action
reporting; no human pre-approval in the loop. Safety comes from a uniform action contract
(pre-checks, health gates, soak, auto-revert, quarantine, cell freeze), not from approval
gates. The single exception: host reboot notifies before executing. EC2 instance-type and
volume-migration changes are recommendation-only (filed in the digest for human execution).

### 2. Objective function and ramp SLO

**Decision:** ✅ **Resolved** — Minimize cost, subject to a hard constraint: a cell must
reach full peak capacity with warm caches within **10 minutes** of a ramp trigger
(scheduled or reactive). When ramps breach the SLO, the framework automatically reduces
shedding depth — cost yields to the SLO, never the reverse.

### 3. Peak model

**Decision:** ✅ **Resolved** — Demand is modeled per cell as four peak classes (POS floor,
back office, batch, integration/external), each with its own calendar windows and service
capacity targets, merged into a per-service plan. The integration/external class defines the
always-on warm floor. Accelerated-time cells schedule business-quiet work against the cell
clock and real-world work against wall clock.

### 4. Metrics authority

**Decision:** ✅ **Resolved** — Prometheus + Micrometer is the authoritative decision-input
source; all decisions read versioned recording rules, not raw series. CloudWatch is used
only for AWS-native signals (EBS, billing, host).

### 5. Substrate strategy

**Decision:** ✅ **Resolved** — Policies are substrate-neutral; execution goes through a
substrate adapter with two profiles: single-host EC2 Docker/Podman (now) and ECS Fargate
(target). No policy, threshold, or catalog entry may encode a substrate primitive.

### 6. Scope of autonomous mutation

**Decision:** ✅ **Resolved** —

- **Grooming**: disk hygiene, rolling JVM process recycling, Postgres maintenance
  (VACUUM/ANALYZE, reindex, WAL/backup pruning behind a verified-restore gate), OS-level
  memory posture ([Grooming Catalog](../architecture/deployment/devops-framework/GROOMING_CATALOG.md)).
- **Right-sizing**: container CPU/memory + JVM heap (changed together), service replica
  counts per peak class, storage volumes — with ±25% step damping, asymmetric hysteresis
  (downsizes need 3 consecutive evaluations; upsizes apply immediately), per-service
  floors/ceilings, and a host-memory invariant on EC2
  ([Right-Sizing Policy](../architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md)).
- **Warm-up**: application caches, JIT paths (synthetic replay), database working set,
  SSR/CDN edge — governed by a measured profitability model that auto-demotes unprofitable
  jobs ([Cache Warm-Up Catalog](../architecture/deployment/devops-framework/CACHE_WARMUP_CATALOG.md)).

### 7. Safety and reporting contract

**Decision:** ✅ **Resolved** — Every action is health-gated with a default 15-minute soak;
gate failure auto-reverts to the snapshotted known-good configuration and quarantines the
(action × target) pair for 7 days; revert failure freezes the cell. Every action emits a
structured after-action record (schema v1), mirrored append-only into the `durion` repo;
daily per-cell digests summarize actions, savings, and exceptions; push notifications fire
only on anomalies and critical events
([Reporting and Rollback Contract](../architecture/deployment/devops-framework/REPORTING_AND_ROLLBACK_CONTRACT.md)).

---

## Alternatives Considered

1. **Tiered autonomy (human approval above a guardrail)**: rejected — approval queues
   reintroduce per-cell toil and delay; guardrails + auto-revert + quarantine bound blast
   radius without a human in the loop.
2. **Recommendation-only framework**: rejected as the end state (kept as per-cell
   calibration mode for the right-sizer's first two cycles) — recommendations that require
   humans to apply them do not scale with cell count.
3. **CloudWatch-centric decision loop**: rejected — weaker query semantics, per-metric cost,
   and substrate coupling; Prometheus/Micrometer is already the platform observability
   direction and works identically on both substrates.
4. **Kubernetes-native tooling (VPA/HPA/CronJobs)**: rejected — the platform is not on
   Kubernetes; ECS is the stated target. The framework reproduces the useful subset
   (vertical sizing, scheduled + reactive scaling) portably.
5. **Static overprovisioning (do nothing)**: rejected — violates the cost objective and
   leaves single-host disk/memory fragility unmanaged.

---

## Consequences

### Positive ✅

- ✅ Off-peak spend drops to warm-floor levels while peak readiness is contractually bounded
  by the 10-minute ramp SLO.
- ✅ Peak periods are protected from hygiene work: disk, JVM, and datastore maintenance are
  scheduled out of business hours, and post-restart warm-up makes maintenance invisible.
- ✅ Operations produce an auditable, machine-readable evidence trail (after-action records,
  digests) consistent with the platform's agent-operated delivery model.
- ✅ Sizing decisions become evidence-based and self-correcting instead of static guesses.

### Negative ⚠️

- ⚠️ Autonomous mutation can cause incidents. Mitigated by health gates, soak, auto-revert,
  quarantine, cell freeze, ±25% damping, and per-service floors.
- ⚠️ Control-plane complexity: the COA, calendar, and substrate adapters are new components
  to build and operate. Mitigated by phased rollout (metrics → grooming → state machine →
  right-sizing → Fargate) and by starting on one cell.
- ⚠️ Warm-up requires per-service work: `POST /internal/warmup` endpoints and synthetic
  fixture routes. Mitigated by a standard contract and per-domain warm-set declarations.
- ⚠️ Reactive ramp from deep shedding risks a degraded first few minutes on surprise load.
  Accepted trade-off of the cost objective; bounded by the SLO and the warm floor.

### Neutral

- Prometheus becomes decision infrastructure, not just observability — recording rules gain
  a compatibility obligation.
- The peak-class calendar is new per-cell configuration that must be maintained as tenants'
  business hours change.

---

## Implementation Notes

- **Components**: Cell Operations Agent (control-plane container/service), peak-class
  calendar + forecaster, decision engine, health-gated action executor, substrate adapters
  (Docker/Podman; ECS), after-action pipeline.
- **Configuration**: per-cell policy file in the release-definition/manifest path (calendar,
  floors/ceilings, warm sets, retention policies).
- **Testing**: action-contract unit tests; integration-cell rehearsal of every catalog task;
  ramp SLO verified per cell before shedding is enabled; chaos check — reactive ramp fired
  mid-grooming must meet the SLO.
- **Rollout**: phases F1–F5 per the master spec, aligned to the phased CI/CD and runtime
  plan; right-sizer runs 2 calibration cycles per cell before autonomous application.
- **Metrics & Monitoring**: ramp phase durations vs. SLO, revert/quarantine rates, monthly
  cost delta per cell, warm-up profitability scores, days-to-full projections.

---

## References

- **Related Documentation**:
  [DevOps Framework master spec](../architecture/deployment/devops-framework/DEVOPS_FRAMEWORK.md),
  [Foundation-First Tenant Cell Deployment Architecture](../architecture/deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md),
  [Phased CI/CD and Runtime Plan](../architecture/deployment/PHASED_CICD_AND_RUNTIME_PLAN.md),
  [Observability](../architecture/observability/OBSERVABILITY.md)
- **Related ADRs**: ADR-0019 (short-lived operational state persistence), ADR-0044
  (event-only domain walls — the COA consumes operational metadata only, never business
  data)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | | | |
| Platform Owner | | | |

---

## Timeline

- **Proposed**: 2026-07-10

---

## Changelog

- **2026-07-10**: Initial draft from platform-owner requirements interview
