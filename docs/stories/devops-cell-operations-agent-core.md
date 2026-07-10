## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:platform
- status:needs-review

### Recommended

- agent:architecture
- capability:devops-framework
- phase:F2

### Blocking / Risk

- Depends on: devops-metrics-foundation
- Blocks: all grooming, right-sizing, and warm-up stories (they plug into this runtime)

**Story Intent**

As the platform operator, I want a Cell Operations Agent (COA) runtime that executes
framework actions through the mandatory action contract — pre-checks, health gates, soak,
auto-revert, quarantine, after-action records — so that all subsequent autonomous
capabilities inherit one uniform safety and reporting mechanism.

**Framework reference**

Action contract in [DEVOPS_FRAMEWORK.md](../architecture/deployment/devops-framework/DEVOPS_FRAMEWORK.md);
full mechanics in [REPORTING_AND_ROLLBACK_CONTRACT.md](../architecture/deployment/devops-framework/REPORTING_AND_ROLLBACK_CONTRACT.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent (control-plane container on the EC2 profile)
- **Secondary:** Docker/Podman API (substrate adapter A), Prometheus (probes), the `durion`
  repo ops-log mirror, notification channel

**Preconditions**

- devops-metrics-foundation complete on the target cell.
- COA container has Docker socket access and a restricted host-task allowlist; no access to
  business databases or service credentials beyond operational endpoints.

**Functional Behavior**

1. **Action framework**: implement the action lifecycle — declared identity, preconditions
   and mutex claims, bounded execution, probe-set evaluation (liveness, functional,
   statistical, invariant), soak timer, verdict.
2. **Snapshot/revert**: capture target configuration before execution (container resource
   spec, JVM flags, replica count); implement restore + re-probe; classify irreversible
   actions and enforce their precondition-verification path instead.
3. **Quarantine and freeze**: implement (action type × target) quarantine (7 days, extended
   to 30 on repeat), and cell-level freeze on revert failure or ≥ 3 reverts / 24 h;
   freeze/unfreeze recorded in the ops log.
4. **After-action records**: emit schema-v1 records for every terminal outcome; write to the
   per-cell ops log; mirror append-only into `docs/operations/cells/<cell-id>/` with one
   batched commit per grooming window.
5. **Digest and notifications**: generate the daily per-cell digest; implement notification
   levels (`info`/`warning`/`anomaly`/`critical`) with push on `anomaly`+ and repeated
   `critical` until acknowledged.
6. **Substrate adapter A (EC2)**: implement the abstract operations used by later stories —
   stop/start/recreate container with limits, compose scale, host task execution from the
   allowlist.
7. **Scheduler**: systemd-timer (or cron) entry point that wakes the COA for window
   evaluation; idempotent on overlapping invocations.
8. Ship one **reference action** end-to-end to prove the contract: a no-op-risk grooming
   task (G-102 temp cleanup) running through preconditions → execution → probes → record →
   digest.

**Acceptance Criteria**

- A contrived failing action (probe forced to fail) demonstrably auto-reverts, quarantines
  the pair, and fires an `anomaly` notification with a complete record.
- Revert-failure path demonstrably freezes the cell and fires `critical`.
- G-102 runs on the integration cell and produces a record and digest entry; re-running it
  is idempotent.
- After-action records validate against the schema; repo mirror commits are batched.
- COA has no route to business data: verified by inspection of its network/mount/credential
  surface.

**Out of Scope**

- Calendar/state machine (separate story); all other catalog tasks; Fargate adapter (F5).
