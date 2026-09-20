---
type: Platform Document
title: Right-Sizing Policy — Metric-Driven Resource Allocation
description: 'Governs how the Cell Operations Agent recomputes and autonomously applies resource allocations from observed utilization under the objective of minimum cost subject to the ramp and peak SLOs: what it may change without a human (S-1 container CPU/memory limits and JVM heap, S-2 replica counts per peak class, S-3 EBS gp3 volume size, IOPS, throughput and archive tiering), what it may only recommend (EC2 instance type), the Prometheus recording rules and percentile windows it reads, and the guardrails, change budgets and headroom floors that bound each adjustment.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md
path: durion/docs/architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md
tags: [platform, architecture]
kind: Policy
status: draft
doc_status: Draft specification
sources: [durion/docs/architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-07-10T06:13:04+00:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md) — `durion/docs/architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md`

**Kind:** Policy
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** Draft specification
