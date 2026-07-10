## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:platform
- status:needs-review

### Recommended

- agent:architecture
- capability:devops-framework
- phase:F3

### Blocking / Risk

- Depends on: devops-cell-operations-agent-core
- Blocks: devops-cache-warmup-and-ramp, devops-rightsizing-engine (replica plans)

**Story Intent**

As the platform operator, I want each cell to carry a peak-class calendar and run the
`PEAK`/`SHOULDER`/`GROOMING`/`RAMP` operating-state machine, so that capacity posture,
grooming windows, and ramp timing are driven by each tenant's actual demand pattern —
including accelerated-clock cells.

**Framework reference**

Peak-class model and cell operating states in
[DEVOPS_FRAMEWORK.md](../architecture/deployment/devops-framework/DEVOPS_FRAMEWORK.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent
- **Secondary:** Cell time authority (effective clock), release definition / cell policy
  (calendar source), grooming window planner, batch scheduler (shared-window consumer)

**Preconditions**

- COA core running; peak-class recording rules (metrics foundation) available for reactive
  triggers and calendar validation.

**Functional Behavior**

1. **Calendar model**: per-cell configuration declaring, per peak class, active windows in
   the cell's effective time zone plus exception dates (holidays, promotions); versioned
   with the cell's release/manifest configuration in the `durion` repo.
2. **Merged capacity plan**: compute per-service targets per time slice as the max across
   class targets; derive the warm floor from `integration-external`; expose the plan to the
   executor and (later) the right-sizer.
3. **State machine**: implement the four states and transitions — scheduled transitions
   from the calendar; `RAMP` at `peak_start − ramp_lead_time` (initialized to the 10-minute
   cap until measured); reactive `RAMP` on sustained latency/rate triggers in any state;
   grooming-task abort/pause on reactive ramp per declared abort class.
4. **Window planner**: allocate grooming tasks and `batch`-class work into the `GROOMING`
   window without same-resource overlap; enforce worst-case-duration fit.
5. **Accelerated-time support**: business-quiet scheduling reads the cell time authority;
   real-world tasks schedule by wall clock; both clocks recorded in every state-transition
   record.
6. **Shedding execution**: on entering `SHOULDER`/`GROOMING`, apply the plan via the
   substrate adapter (stop non-floor services / reduce limits per plan); on `RAMP`, restore
   capacity (full warm-up choreography arrives with devops-cache-warmup-and-ramp; until
   then, ramp = capacity restore + health gates).
7. Every state transition emits an after-action record; the digest shows the day's state
   timeline.

**Acceptance Criteria**

- Integration cell follows a configured calendar through a full day: correct transition
  times, non-floor services stopped during `GROOMING`, warm floor never scaled below plan.
- Synthetic off-peak load burst forces reactive `RAMP` from `GROOMING`: in-flight `instant`
  and `checkpoint` grooming tasks stop per contract, capacity restored, transition records
  complete.
- Capacity restore (pre-warm-up ramp) measured < 5 minutes on the integration cell,
  leaving budget for warm-up phases within the 10-minute SLO.
- An accelerated-clock cell schedules its business-quiet window per cell clock (verified
  against the time authority) while image pruning runs per wall clock.
- Calendar change lands via normal repo review and takes effect at the next evaluation.

**Out of Scope**

- Warm-up choreography and profitability (next story); forecast corrections to the calendar
  (post-F3 enhancement); Fargate scheduled-scaling adapter (F5).
