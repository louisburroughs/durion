---
type: Platform Document
title: Grooming Catalog — Off-Peak Environment Hygiene Tasks
description: Enumerates every recurring hygiene task the Cell Operations Agent may run inside a tenant cell GROOMING window — log rotation, temp and dump cleanup, container image/build-cache/volume pruning and a disk watchdog (G-1xx); rolling JVM recycle and connection-pool sweep (G-2xx); Postgres VACUUM/ANALYZE, reindex, WAL and backup-archive pruning, planner statistics refresh (G-3xx); host memory posture and reboot policy (G-4xx) — each specified against the framework action contract (preconditions, execution, health gates, abort class, revert or irreversibility, after-action record), plus the window-planning rules that keep grooming off the same resources as batch workloads.
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/deployment/devops-framework/GROOMING_CATALOG.md
path: durion/docs/architecture/deployment/devops-framework/GROOMING_CATALOG.md
tags: [platform, architecture]
kind: Catalog
status: draft
doc_status: Draft specification
sources: [durion/docs/architecture/deployment/devops-framework/GROOMING_CATALOG.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-20T07:56:04-04:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/deployment/devops-framework/GROOMING_CATALOG.md) — `durion/docs/architecture/deployment/devops-framework/GROOMING_CATALOG.md`

**Kind:** Catalog
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** Draft specification
