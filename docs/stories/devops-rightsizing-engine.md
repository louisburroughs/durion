## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:platform
- status:needs-review

### Recommended

- agent:architecture
- capability:devops-framework
- phase:F4

### Blocking / Risk

- Depends on: devops-metrics-foundation, devops-cell-operations-agent-core,
  devops-peak-class-calendar-and-state-machine
- Risk: autonomous resource mutation — bounded by damping, hysteresis, floors/ceilings,
  host invariant, calibration mode, and the COA revert/quarantine mechanics

**Story Intent**

As the platform operator, I want an engine that continuously recomputes container
CPU/memory + JVM heap, per-peak-class replica plans, and storage volume sizing from observed
utilization, and applies changes autonomously within guardrails — so cells track their real
demand instead of static guesses, minimizing cost under the ramp SLO.

**Framework reference**

[RIGHTSIZING_POLICY.md](../architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent (decision engine + executor)
- **Secondary:** Prometheus recording rules (inputs), release definitions (floors/ceilings,
  defaults), CloudWatch/EBS APIs (storage), S3 (archive tiering), daily digest (human
  recommendations)

**Preconditions**

- ≥ 7 days of peak-class-labeled history on the cell; per-service floors/ceilings declared
  in the release definition; price tables for cost accounting configured.

**Functional Behavior**

1. **S-1 container/JVM sizing**: implement the memory, heap, and CPU formulas with the GC
   health veto; heap and container limit changed together in one rolling, health-gated
   action per service inside the grooming window; EC2 host-memory invariant checked before
   every application (violation → instance-type recommendation instead).
2. **S-2 replica plans**: compute peak/shoulder/floor targets per service per peak class;
   feed calendar targets to the state machine; implement reactive scale-up (undamped) on
   latency/rate breach.
3. **S-3 storage**: growth-projection-driven expansion (used × 1.5 at < 30 days-to-full),
   gp3 IOPS/throughput tuning monthly, archive tiering with checksum-verified upload before
   local delete, shrink recommendations (never autonomous).
4. **Damping and hysteresis**: ±25% max step; downsizes require 3 consecutive evaluations,
   upsizes apply immediately; per-asset application cadences per the policy table.
5. **Calibration mode**: first two evaluation cycles per cell compute and record intents
   without applying; automatic exit.
6. **Cost accounting**: signed monthly cost delta on every applied action; digest aggregates
   applied savings, available-but-blocked savings, and pending human recommendations
   (instance type, volume migration) with evidence.

**Acceptance Criteria**

- Calibration: two cycles of recorded-not-applied intents on the integration cell, then
  autonomous application begins without human action.
- An overprovisioned service (seeded) is downsized only after 3 consecutive evaluations, by
  ≤ 25%, via rolling restart in the window, with GC health verified during soak; records
  show formula inputs.
- An undersized service under synthetic load is upsized on the first evaluation.
- A downsize that trips the GC veto is rejected with the veto evidenced in the record.
- A forced-bad sizing (test hook) is auto-reverted by the COA and the (S-1 × service) pair
  quarantined 7 days.
- Sum-of-limits invariant: an application that would exceed host RAM converts to an
  instance-type recommendation in the digest.
- Storage: growth projection < 30 days triggers expansion; tiering deletes locally only
  after verified upload.
- Digest shows cost posture and cumulative savings for the cell.

**Out of Scope**

- EC2 instance-type execution and volume migration (human-executed from recommendations);
  Fargate task-definition adapter (F5); cross-cell bin-packing.
