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

- Depends on: devops-cell-operations-agent-core
- Risk: file deletion is irreversible — precondition verification is the safety mechanism

**Story Intent**

As the platform operator, I want automated disk hygiene (log rotation, scratch cleanup,
image/volume pruning, dump cleanup, growth watchdog) running in each cell's grooming window,
so that peak periods never lose capacity or availability to a filling disk.

**Framework reference**

Group G-1 in [GROOMING_CATALOG.md](../architecture/deployment/devops-framework/GROOMING_CATALOG.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent
- **Secondary:** Host filesystem, Docker/Podman storage, ECR (image source of truth),
  release definitions (image keep-rules)

**Preconditions**

- COA core running on the cell; scratch-path allowlist and persistence-volume allowlist
  declared in cell policy; retention constants configured (log 14 d, dumps 7 d).

**Functional Behavior**

1. Implement catalog tasks **G-101** (log rotation + retention), **G-102** (temp/scratch
   cleanup — extend the reference implementation from the COA core story to the full spec),
   **G-103** (image/build-cache/volume pruning with current + previous release keep-rules
   and named-volume protection), **G-104** (core/heap dump cleanup with metadata
   preservation), **G-105** (growth watchdog emitting days-to-full projections and S-3
   sizing intents).
2. Implement the **emergency path**: volume > 85% triggers the low-risk subset in any cell
   state with an immediate `anomaly` notification.
3. Each task implements its declared abort class and reports bytes freed per the record
   schema.

**Acceptance Criteria**

- On a cell seeded with expired logs, aged scratch files, dangling images, and an old heap
  dump: one grooming window removes all of them, and every deletion's preconditions (age,
  allowlist, keep-rule) are evidenced in the records.
- G-103 provably retains images for both the current and previous release definitions and
  never touches `postgres-data` or other allowlisted volumes.
- Emergency trigger at 85% usage runs within 5 minutes and fires an anomaly notification.
- G-105 projection appears in the daily digest; a < 30-day projection produces a storage
  sizing intent.
- No service errors attributable to hygiene during the soak of any task (statistical probes
  pass).

**Out of Scope**

- Storage volume expansion itself (right-sizing story); archive tiering to S3 (S-3, part of
  the right-sizing story).
