---
type: Platform Document
title: Backend Preload Tables (Module Crawl)
description: 'Per-module inventory of which backend tables must hold rows before a Durion deployment is usable, in three tiers: bootstrap-critical (pos-security-service users, roles, permissions and role_assignments behind perm_bits; pos-event-receiver event_type; pos-accounting override and refund policy), operationally required master data (pos-location, pos-people, pos-catalog, pos-price reference rows), and what is auto-seeded by module startup or Flyway rather than preloaded — each row stating why the platform fails or is unusable without it, plus the recommended module order for the data load.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/deployment/data-migration/BACKEND_PRELOAD_TABLES.md
path: durion/docs/architecture/deployment/data-migration/BACKEND_PRELOAD_TABLES.md
tags: [platform, architecture]
kind: Reference
status: stable
doc_status: current
sources: [durion/docs/architecture/deployment/data-migration/BACKEND_PRELOAD_TABLES.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-09T18:20:58+00:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/deployment/data-migration/BACKEND_PRELOAD_TABLES.md) — `durion/docs/architecture/deployment/data-migration/BACKEND_PRELOAD_TABLES.md`

**Kind:** Reference
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** current
