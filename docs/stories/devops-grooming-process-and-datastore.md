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

- Depends on: devops-cell-operations-agent-core, devops-grooming-disk-hygiene
- Risk: touches running services and the datastore; WAL/backup pruning gated on verified
  restore

**Story Intent**

As the platform operator, I want automated JVM process recycling, datastore maintenance,
and OS memory posture checks in the grooming window, so that memory creep, table bloat, and
stale statistics are corrected off-peak instead of degrading peak performance.

**Framework reference**

Groups G-2, G-3, G-4 in
[GROOMING_CATALOG.md](../architecture/deployment/devops-framework/GROOMING_CATALOG.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent
- **Secondary:** Backend services (graceful stop/start, Eureka deregistration), Postgres,
  node exporter, backup pipeline (restore-verification evidence)

**Preconditions**

- COA core + disk hygiene running; recycle-signal recording rules available (post-GC
  occupancy trend, RSS creep, pool leak indicators); backup policy with a verified-restore
  marker the COA can query.

**Functional Behavior**

1. **G-201 rolling JVM recycle**: signal evaluation per service; cell-wide recycle mutex;
   graceful drain → restart → health → post-restart warm-up (calls the service's warm-up
   endpoint once devops-cache-warmup-and-ramp lands; until then, health-gate only with a
   recorded warm-up-pending flag); 10-minute per-service soak; warm-floor services only
   with standby or recorded blackout tolerance.
2. **G-202 pool sweep**: Hikari metrics sweep, soft eviction where exposed, flag services
   as recycle candidates otherwise.
3. **G-301 VACUUM/ANALYZE**: dead-tuple/stale-stats driven, table-granular checkpoints, no
   overlap with batch jobs on the same database.
4. **G-302 reindex**: bloat estimation weekly; `REINDEX CONCURRENTLY` with 2× disk headroom
   precondition; invalid-index cleanup on failure.
5. **G-303 WAL/backup pruning**: hard-gated on latest verified restore; retention per cell
   class.
6. **G-304 targeted ANALYZE** after bulk batch mutations.
7. **G-401 host memory posture**: invariant check, swap/slab/OOM review, exceptional page
   cache drop rule.
8. **G-402 host reboot** implemented as the exception path with pre-notification, full
   smoke gate, and `must-complete` semantics.

**Acceptance Criteria**

- A service exceeding max uptime is recycled in the window: records show memory
  before/after, restart duration, health-gate results; no synthetic-probe errors during
  soak; only one service recycles at a time.
- Reactive `RAMP` during a rolling recycle stops the sequence at the current service
  (checkpoint abort) — demonstrated in an integration-cell rehearsal.
- G-301 reduces dead tuples on a bloat-seeded table and never runs concurrently with a
  scheduled batch job on the same database (window-planner evidence in records).
- G-303 refuses to prune when the verified-restore marker is absent/stale, and records the
  refusal as `skipped-preconditions`.
- G-402 rehearsed once on the integration cell: pre-notification fired, full smoke gate
  passed, downtime within window.

**Out of Scope**

- Warm-up job implementations (devops-cache-warmup-and-ramp); recycle-triggered sizing
  changes (right-sizing story).
