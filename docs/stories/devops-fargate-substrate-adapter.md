## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:platform
- status:needs-review

### Recommended

- agent:architecture
- capability:devops-framework
- phase:F5

### Blocking / Risk

- Depends on: all prior devops-* stories; gated on the platform's ECS Fargate migration
  (see FARGATE_ARCHITECTURE.md)

**Story Intent**

As the platform operator, I want the DevOps framework's policies to run unchanged on ECS
Fargate via a second substrate adapter, so the framework survives the substrate migration
and gains true per-service scale-out.

**Framework reference**

Substrate Profile B in
[DEVOPS_FRAMEWORK.md](../architecture/deployment/devops-framework/DEVOPS_FRAMEWORK.md);
Fargate architecture in `docs/architecture/AWS/FARGATE_ARCHITECTURE.md`.

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent (as an ECS control-plane service)
- **Secondary:** ECS APIs, Application Auto Scaling, EventBridge Scheduler, ALB target
  groups (health), CloudWatch

**Preconditions**

- Cell running on ECS Fargate per the target architecture; framework fully operational on
  the EC2 profile (policies proven).

**Functional Behavior**

1. Implement adapter operations: task-definition revision for S-1 resize (rolling,
   target-group health-gated), desired-count changes and scheduled scaling actions for the
   calendar plan, target-tracking policies parameterized by the COA for reactive scaling.
2. Move state-transition scheduling to EventBridge Scheduler; COA runs as an ECS service.
3. Map health gates to Fargate-native signals (target-group health + existing Prometheus
   probes); snapshots become task-definition revision references (revert = redeploy prior
   revision).
4. Re-scope grooming: host-level tasks (G-1 image pruning, G-4) retire or translate
   (ephemeral storage semantics); service and datastore grooming (G-2, G-3) unchanged.
5. Scale-to-zero for non-floor services via desiredCount 0, with ramp choreography verified
   against Fargate cold-start times within the 10-minute SLO.
6. Verify the portability rule: zero policy, calendar, or catalog constant changes required
   by the migration — adapter-only diff.

**Acceptance Criteria**

- A Fargate cell completes a full day cycle (calendar transitions, grooming window, ramp)
  meeting the same SLO and producing the same record/digest formats as the EC2 profile.
- S-1 resize applies as a new task-definition revision with health-gated rollout and
  demonstrated revert to the prior revision.
- Reactive scale-up via target tracking reaches the peak plan level and is recorded by the
  COA.
- Policy diff between profiles is empty (adapter code only) — reviewed as evidence.

**Out of Scope**

- The ECS migration itself; multi-cell fleet optimization.
