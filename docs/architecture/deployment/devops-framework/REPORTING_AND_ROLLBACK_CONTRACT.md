---
title: Reporting and Rollback Contract — Autonomy Safety Mechanics
status: Draft specification
created: 2026-07-10
parent: ./DEVOPS_FRAMEWORK.md
---

# Reporting and Rollback Contract — Autonomy Safety Mechanics

## Purpose

The framework operates fully autonomously (framework decision D3). This contract specifies
the two things that make that acceptable: the **safety mechanics** every action runs under
(health gates, soak, auto-revert, quarantine — D11) and the **reporting surface** that keeps
humans informed after the fact (after-action records, digests, notifications — D10).

Under full autonomy, the reporting channel *is* the human interface. It must be complete
enough that a human reading only the digest can reconstruct what the framework did, why,
what it cost or saved, and what went wrong.

## Safety Mechanics

### Health gates

Every action declares its probe set before execution. Standard probe classes:

| Probe class | Contents |
|-------------|----------|
| Liveness | `/actuator/health` UP for affected services; container running; discovery registration present |
| Functional | Synthetic transaction(s) through the affected path (subset of the smoke route set, or the service's synthetic warm-up routes) |
| Statistical | Prometheus queries: error rate ≤ baseline × 1.5, p95 latency ≤ baseline × 1.5, no restart loops, for affected services during soak |
| Invariant | Substrate invariants (e.g., host memory invariant on EC2 profile; current + previous release images present after pruning) |

Pre-checks run the same probes *before* execution: an action never starts against an
already-unhealthy target (an unhealthy target converts the action into an anomaly report
instead — the framework must not mask an existing incident with a config change).

### Soak and verdict

- Default soak: **15 minutes** of statistical-probe observation after execution. Per-action
  overrides are declared in the catalog (e.g., 10 minutes per service for G-201).
- Verdict at soak end: `success`, `reverted` (gates failed → revert executed), or
  `revert-failed` (escalation — see below).
- SLO burn during soak counts as gate failure even if individual probes pass.

### Auto-revert

- Every revertible action snapshots its target's configuration (the "known-good state")
  before execution: container resource spec, JVM flags, replica count, task definition
  revision, scaling policy parameters.
- On gate failure: restore the snapshot, re-run the probe set, and record the revert in the
  after-action record. Revert execution has its own bounded time and its own probes.
- **Irreversible actions** (deletions) do not revert; their safety lives in strict
  preconditions (age, retention, verified backup, checksum-verified upload), per the
  grooming catalog. An irreversible action whose preconditions cannot be verified does not
  run.
- **Revert failure** is the worst case: target unhealthy and snapshot restore failed. The
  COA escalates: freeze all autonomous actions on the cell, fire a `critical` notification,
  and — if the affected service is warm-floor — attempt last-known-good release-definition
  redeploy of that service as a final automatic remedy.

### Quarantine

- A reverted action quarantines the (action type × target resource) pair on that cell for
  **7 days**: the decision engine may recompute intents but the executor refuses them.
- Two reverts of the same pair within 30 days extend quarantine to 30 days and flag the pair
  in the digest as needing human attention (policy bug, bad formula constant, or workload
  change).
- Quarantine is per-cell: a revert on one cell does not block the fleet, but the digest
  aggregates cross-cell revert patterns so a systemic bad policy is visible.

### Cell action freeze

Distinct from quarantine: a **freeze** stops all autonomous mutation on a cell (monitoring
and reporting continue). Triggers: revert failure, operator command, or ≥ 3 reverts on one
cell within 24 hours (something is wrong with the cell or the policies; stop touching it).
Unfreeze is an operator action recorded in the ops log.

## After-Action Records

Every action emits exactly one record per terminal outcome. Records are the framework's
audit trail and its learning input (profitability scoring, ramp-lead-time tuning, sizing
feedback all read from records).

### Record schema (v1)

```yaml
record_version: 1
record_id: <uuid>
cell_id: <tenant-cell id>
action:
  type: <catalog id, e.g. G-201, S-1, W-2, RAMP>
  target: <service/volume/cell-scope>
  parameters: { ... }            # what was done, exactly
  initiating_policy: <policy/trigger reference>
  intent_computed_at: <cell-clock and wall-clock timestamps>
timing:
  started_at: <wall clock>
  executed_duration_s: <n>
  soak_duration_s: <n>
state_context:
  cell_state: <PEAK|SHOULDER|GROOMING|RAMP>
  cell_clock_mode: <realtime|accelerated|fixed>
  release_definition: <version the cell was running>
evidence:
  pre_metrics: { ... }           # probe/metric values before
  post_metrics: { ... }          # probe/metric values after soak
  probes: [ {probe, result}, ... ]
outcome:
  verdict: success | reverted | revert-failed | aborted | skipped-preconditions
  revert_detail: { ... }         # present when reverted
  cost_delta_monthly_usd: <n>    # signed; sizing actions only
  resources_freed: { disk_bytes, memory_bytes }   # grooming actions
  warmup_benefit: { ... }        # warm-up actions: durations, hit latencies
notes: <free text, bounded>
```

### Storage

- Records are written as YAML/JSON-lines to a per-cell ops log under the control-plane data
  path, and mirrored into the `durion` repo under `docs/operations/cells/<cell-id>/`
  (append-only, batched commit once per grooming window — not one commit per record). The
  repo mirror is what makes the trail reviewable in PRs and consumable by agents.
- Retention: full records 90 days in the ops log; the repo mirror keeps daily digests
  indefinitely and prunes raw records past 90 days (a grooming task, naturally: G-1xx
  applies to the framework's own exhaust too).

## Digests and Notifications

### Daily digest (per cell, plus fleet rollup)

Generated at the end of each cell's grooming window; committed with the record batch and
posted as the daily ops digest. Contents:

- Actions executed: count by type, verdicts, total duration.
- Resources: disk/memory freed, cost delta applied, cumulative monthly savings posture.
- Right-sizing: current vs. yesterday's allocations; pending human recommendations
  (instance type, volume migration) with evidence links.
- Ramp report: last ramp durations per phase vs. SLO, `ramp_lead_time` adjustments.
- Exceptions: reverts, quarantines, demoted warm-up jobs, anomalies.
- Watchlist: days-to-full projections, GC health flags, services approaching recycle
  signals — what the framework expects to act on next.

### Notification levels

| Level | Trigger | Channel behavior |
|-------|---------|------------------|
| `info` | Routine success | Record + digest only; no push |
| `warning` | Guardrail hit (intent clipped by ±25% cap, floor/ceiling, host invariant), emergency grooming run, warm-up job demoted | Digest highlight + ops-channel message |
| `anomaly` | Any revert, ramp SLO breach, quarantine trigger, days-to-full < 7, OOM event | Immediate push notification with record link |
| `critical` | Revert failure, cell freeze, warm-floor service down and not self-recovering | Immediate push, repeated until acknowledged |

Pre-notification exists for exactly one action class: host reboot (G-402) notifies before
execution, given whole-cell blast radius. Everything else reports after the fact, per D3.

### Acknowledgment

`critical` notifications require operator acknowledgment (recorded in the ops log).
`anomaly` notifications do not block anything but are listed in the digest until the
underlying quarantine or flag clears.

## Learning Loop

The reporting pipeline is also the framework's feedback path; these couplings are mandatory,
not optional analytics:

- Ramp records → `ramp_lead_time` and shedding-depth adjustment (Cache Warm-Up Catalog).
- Warm-up records → profitability scoring and job demotion (Cache Warm-Up Catalog).
- Emergency grooming records → storage sizing feedback (Right-Sizing Policy S-3).
- Revert/quarantine patterns → digest flags for policy-constant review (the constants in
  the sizing formulas are expected to be tuned from this evidence).
