---
title: Grooming Catalog — Off-Peak Environment Hygiene Tasks
status: Draft specification
created: 2026-07-10
parent: ./DEVOPS_FRAMEWORK.md
---

# Grooming Catalog — Off-Peak Environment Hygiene Tasks

## Purpose

This catalog specifies every recurring hygiene task the Cell Operations Agent (COA) may
execute during a cell's `GROOMING` window. Each task is specified against the framework's
action contract: preconditions, execution, health gates, abort behavior, revert (or
irreversibility conditions), and after-action reporting.

Grooming exists to keep peak periods clean: work that would otherwise steal CPU, IO, memory,
or disk from business traffic is done when the cell is quiet, so peak-time capacity is spent
on customers.

## Window Planning Rules

- All grooming tasks run only in cell state `GROOMING`, inside the window computed from the
  peak-class calendar.
- The window planner allocates grooming tasks and `batch`-class workloads into the same
  window **without resource overlap**: no datastore maintenance concurrent with a batch job
  on the same database; no host-wide IO-heavy pruning concurrent with backup uploads.
- Tasks are ordered by priority (below) and preempted by a reactive `RAMP` trigger according
  to their declared abort class:
  - `instant` — safe to kill at any point.
  - `checkpoint` — stops at the next internal checkpoint (bounded ≤ 60 s).
  - `must-complete` — cannot be safely interrupted; the planner only starts these when the
    remaining window exceeds the task's worst-case duration plus the ramp lead time.
- Every task declares a worst-case duration; the planner never schedules a task that cannot
  finish (or reach a safe abort) before the window closes.
- Accelerated-time cells: business-quiet tasks (G-201, G-3xx) schedule against the cell
  clock; real-world tasks (G-1xx, G-401) schedule against wall clock. See the framework
  spec's accelerated-time section.

## Priority Order

1. Safety-critical disk headroom (a full disk kills the whole cell)
2. Datastore maintenance (protects peak query performance)
3. Process recycling (protects peak memory behavior)
4. Opportunistic cleanup (image pruning, archive tiering)

---

## Group G-1: Disk Hygiene

### G-101 Log rotation and retention enforcement

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window; **emergency trigger** at any time if volume usage > 85% |
| Preconditions | Log shipping (if configured) has caught up past the rotation point |
| Action | Rotate service logs, compress rotated files, delete beyond retention (default: 14 days compressed on-host; long-term retention is the log pipeline's job, not the host's) |
| Abort class | `instant` |
| Revert | Irreversible — permitted only for files past retention and confirmed shipped/aged |
| Health gates | Services still writing logs post-rotation; volume usage decreased or unchanged |
| Report fields | Bytes freed, files removed, per-service breakdown |

### G-102 Temp and scratch cleanup

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window |
| Preconditions | File age > 24 h (cell clock); path on the configured scratch allowlist (`/tmp`, upload staging, report generation scratch); no open file handles |
| Action | Delete matching files |
| Abort class | `instant` |
| Revert | Irreversible — the age + allowlist + no-open-handle preconditions are the safety mechanism |
| Health gates | No service errors referencing missing files during soak |
| Report fields | Bytes freed, oldest/newest deleted, paths summary |

### G-103 Container image, build-cache, and volume pruning

| Field | Specification |
|-------|---------------|
| Trigger | Weekly, or emergency at volume usage > 85% |
| Preconditions | Keep-rules satisfied: retain images referenced by the current release definition **and** the previous known-good release definition (rollback safety); never prune named volumes on the persistence allowlist (e.g., `postgres-data`) |
| Action | `docker image prune` / `podman image prune` with keep-filters; builder-cache prune; dangling anonymous volume removal |
| Abort class | `instant` |
| Revert | Images re-pullable from ECR (registry is the source of truth); named-volume protection makes data loss structurally impossible rather than procedurally avoided |
| Health gates | Current + previous release images still present; all services running |
| Report fields | Bytes freed, images removed (tags), volumes removed |

### G-104 Core dump and heap dump cleanup

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window |
| Preconditions | Dump age > 7 days, or age > 24 h with a recorded acknowledgment in the ops log (someone/something has seen it) |
| Action | Delete dump files; record dump metadata (service, timestamp, size) in the after-action record before deletion |
| Abort class | `instant` |
| Revert | Irreversible — metadata preservation is the mitigation |
| Report fields | Dumps removed with metadata, bytes freed |

### G-105 Disk growth watchdog (monitoring task)

| Field | Specification |
|-------|---------------|
| Trigger | Continuous (Prometheus recording rule), evaluated each grooming window |
| Action | Compute per-volume growth rate; if projected days-to-full < 30, raise a storage right-sizing intent (see Right-Sizing Policy S-3); if < 7, raise an anomaly notification |
| Report fields | Per-volume usage, growth rate, days-to-full projection |

---

## Group G-2: Process Recycling

### G-201 Rolling JVM service recycle

| Field | Specification |
|-------|---------------|
| Trigger | Per-service, when any recycle signal fires — old-gen occupancy after full GC trending up across 3 windows, native/RSS creep > 15% over baseline, connection-pool leak indicators, or max uptime exceeded (default 7 days, cell clock) |
| Preconditions | Cell in `GROOMING`; only one service recycling at a time (cell-wide mutex); service healthy before restart; **warm-floor services** (gateway, security, integration endpoints, datastore) recycle only if a standby/second replica can hold traffic, or in the deepest point of the window with explicit blackout tolerance recorded in cell policy |
| Action | Graceful stop (connection drain, in-flight completion, deregistration from discovery), start, wait healthy, then run the service's post-restart warm-up jobs from the Cache Warm-Up Catalog (a recycled service must not enter `PEAK` cold) |
| Abort class | `must-complete` per service; the rolling sequence itself is `checkpoint` (abort = stop after current service finishes) |
| Health gates | `/actuator/health` UP; discovery re-registration; synthetic transaction through the service; baseline error rate during soak |
| Soak | 10 minutes per service before the next service starts |
| Revert | Restart does not change configuration; failure to come healthy → retry once, then restore from last known-good image/config and fire anomaly |
| Report fields | Services recycled, memory before/after (heap + RSS), restart duration, warm-up duration |

### G-202 Connection pool and stale-resource sweep

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window |
| Action | Query pool metrics (HikariCP via Micrometer); flag pools with leaked/aged connections; where the service exposes a management endpoint for soft eviction, evict idle connections; otherwise mark the service as a G-201 candidate |
| Abort class | `instant` |
| Health gates | Pool re-establishes healthy connection count during soak |
| Report fields | Pools swept, connections evicted, services flagged for recycle |

---

## Group G-3: Datastore Maintenance

### G-301 VACUUM / ANALYZE cycle

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window: ANALYZE on tables with stale statistics; VACUUM on tables above dead-tuple thresholds (informed by `pg_stat_user_tables`) |
| Preconditions | No `batch`-class job scheduled against the same database in the overlapping slot; replication/WAL shipping (if any) healthy |
| Action | Table-by-table VACUUM/ANALYZE, most-bloated first, each table a checkpoint |
| Abort class | `checkpoint` (table granularity) |
| Health gates | No lock-wait spikes; query p95 on hot tables unchanged or improved next peak |
| Report fields | Tables processed, dead tuples reclaimed, duration per table |

### G-302 Index bloat check and reindex

| Field | Specification |
|-------|---------------|
| Trigger | Weekly bloat estimation; reindex intent when estimated bloat > 40% on an index used by hot queries |
| Preconditions | `REINDEX CONCURRENTLY` supported and disk headroom ≥ 2× index size |
| Action | `REINDEX CONCURRENTLY`, one index at a time |
| Abort class | `checkpoint` (index granularity; concurrent reindex is safely cancellable) |
| Revert | Concurrent reindex failure leaves the old index in place (Postgres semantics); invalid leftover indexes are dropped |
| Report fields | Indexes rebuilt, size before/after |

### G-303 WAL and backup archive pruning

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window |
| Preconditions | **Hard gate**: latest backup verified restorable (per the tenant-cell backup policy) before any archive deletion; retention policy satisfied (default: N daily + M weekly per cell class) |
| Action | Prune WAL archives and backup files beyond retention |
| Abort class | `instant` |
| Revert | Irreversible — the verified-restore precondition is the safety mechanism |
| Report fields | Bytes freed, retention state, last verified-restore timestamp |

### G-304 Statistics refresh for planner-critical tables

| Field | Specification |
|-------|---------------|
| Trigger | After any batch job that bulk-modifies > 10% of a table's rows; otherwise folded into G-301 |
| Action | Targeted ANALYZE on affected tables so the first peak-hour queries plan correctly |
| Abort class | `instant` |
| Report fields | Tables analyzed, trigger source |

---

## Group G-4: OS-Level Memory (EC2 profile)

### G-401 Host memory posture check

| Field | Specification |
|-------|---------------|
| Trigger | Every grooming window; continuous alerting via node exporter |
| Action | Verify the host memory invariant (Σ container limits + host reserve ≤ host RAM); check swap activity, slab growth, and OOM-killer events since last window; drop clean page cache only if anonymous-memory pressure is high **and** no batch/backup job is IO-active (page cache is normally a performance asset — dropping it is the exception, not routine) |
| Abort class | `instant` |
| Health gates | No OOM events during soak; swap-in rate at baseline |
| Report fields | Invariant status, swap/slab/OOM summary, action taken (usually none) |

### G-402 Host reboot policy (exception path, not routine)

| Field | Specification |
|-------|---------------|
| Trigger | Only on: kernel memory leak evidence across ≥ 3 windows, required kernel security patch, or unrecoverable slab growth |
| Preconditions | Deepest grooming window; verified backup; expected downtime fits within window with full margin; **notification fired before execution** (this is the one grooming action with pre-notification, given whole-cell blast radius) |
| Action | Graceful full-cell stop, reboot, full-cell start, full ramp choreography |
| Abort class | `must-complete` |
| Health gates | Full smoke gate (the same 8-route smoke + health suite used for deployment promotion) |
| Report fields | Reason, downtime duration, post-reboot health summary |

---

## Emergency Grooming (outside the window)

Disk-headroom emergencies (volume > 85%) may execute G-101, G-102, G-103, and G-104 in any
cell state, restricted to their lowest-risk subset (oldest files first, dangling images
only). Emergency grooming always fires an immediate anomaly notification — running hygiene
during peak means forecasting or right-sizing failed, and the after-action record must feed
back into the storage sizing policy (S-3).
