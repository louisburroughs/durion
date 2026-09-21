---
type: Platform Document
title: Durion Positivity — AWS Fargate Architecture
description: 'Target production substrate, not yet built: each pos-* service as an ECS Fargate task behind WAF, ALB and Route 53 across multiple AZs, with Cloud Map service discovery replacing Eureka, RDS Postgres, Amazon MSK for Kafka, Ollama on a dedicated g4dn GPU EC2 instance, Amazon Managed Prometheus and Grafana for observability, per-service application auto-scaling, task execution and task IAM roles, the per-tenant cell model, and a single-cell cost estimate plus the migration path off the single-host EC2 model.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/FARGATE_ARCHITECTURE.md
path: durion/docs/architecture/AWS/FARGATE_ARCHITECTURE.md
tags: [platform, architecture]
kind: Architecture
status: draft
doc_status: proposed
sources: [durion/docs/architecture/AWS/FARGATE_ARCHITECTURE.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-20T07:56:04-04:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/FARGATE_ARCHITECTURE.md) — `durion/docs/architecture/AWS/FARGATE_ARCHITECTURE.md`

**Kind:** Architecture
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** proposed
