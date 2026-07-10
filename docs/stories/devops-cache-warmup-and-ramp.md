## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:platform
- status:needs-review

### Recommended

- agent:architecture
- capability:devops-framework
- phase:F3
- impacts:all-backend-services
- impacts:frontend-ssr

### Blocking / Risk

- Depends on: devops-peak-class-calendar-and-state-machine
- Cross-cutting: every backend service gains a warm-up endpoint; synthetic traffic must be
  excluded from business metrics and audit

**Story Intent**

As the platform operator, I want cells to enter `PEAK` with warm application caches,
JIT-compiled hot paths, a primed database working set, and warm SSR/CDN edges — via a
choreographed ramp that fits the 10-minute SLO and a profitability model that disables
unprofitable warm-up work automatically.

**Framework reference**

[CACHE_WARMUP_CATALOG.md](../architecture/deployment/devops-framework/CACHE_WARMUP_CATALOG.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent (choreography driver)
- **Secondary:** All backend services (warm-up endpoints), API gateway (synthetic replay
  path), Postgres (`pg_prewarm`), Angular SSR frontend, CDN (when active), smoke-fixture
  dataset (synthetic entities)

**Preconditions**

- State machine live on the cell; reference dataset with reserved synthetic entities loaded
  (same fixtures as the deployment smoke gate); synthetic-traffic header convention agreed
  so audit streams and business metrics exclude replay requests.

**Functional Behavior**

1. **W-1 warm-up endpoint contract**: add `POST /internal/warmup` (internal network only) to
   each backend service per the catalog's warm-set table — idempotent, bounded (default
   ≤ 60 s), returns entry counts + duration.
2. **W-2 JIT replay driver**: scripted representative read-path requests through the
   gateway against synthetic entities, tagged synthetic, volume tunable; write paths only
   via internal dry-run endpoints where available.
3. **W-3 database priming**: `pg_prewarm` hot-list + day-specific priming queries
   parameterized by the cell's effective date; bounded by the window IO ceiling.
4. **W-4 SSR/CDN priming**: hot-route pre-render (smoke routes + top-N by first-hour
   traffic) and edge priming when CDN is active.
5. **Ramp choreography**: implement phases R0–R4 as a per-service pipeline (no barriers)
   within the 8-minute internal budget; per-phase instrumentation; verification probe
   (synthetic pos-floor p95 ≤ 1.5× steady state) as the ramp's health gate.
6. **Profitability engine**: score each job from record evidence (benefit vs. cost);
   auto-demote below 1.0 for 5 consecutive ramps; monthly re-score; `critical` vs.
   `opportunistic` classes with reactive ramps skipping `opportunistic`.
7. **SLO feedback**: breach → anomaly notification + automatic `ramp_lead_time` increase
   and shedding-depth reduction for the cell.
8. **Post-restart integration**: wire G-201/S-1 soak to invoke the affected service's W-1
   (and W-2 slice for pos-floor services), closing the warm-up-pending gap left by the
   grooming story.

**Acceptance Criteria**

- Full scheduled ramp on the integration cell from deepest shedding completes ≤ 8 minutes
  with all `critical` jobs run and the verification probe passing; per-phase durations in
  the ramp record.
- First synthetic "customer" request after ramp shows p95 within 1.5× steady state on the
  pos-floor route set (vs. a measured cold-start baseline demonstrating improvement).
- Reactive ramp admits traffic at R1 completion and skips `opportunistic` jobs (records
  prove it).
- Synthetic replay traffic is absent from business dashboards and audit streams.
- A deliberately unprofitable warm-up job is auto-demoted after 5 ramps and appears in the
  digest.
- A recycled service completes W-1 within its soak before the next service recycles.

**Out of Scope**

- CRaC/AppCDS adoption (tracked as a framework enhancement); CDN provisioning itself.
