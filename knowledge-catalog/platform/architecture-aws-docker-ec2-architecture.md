---
type: Platform Document
title: Durion Positivity — Docker on EC2 Architecture
description: 'Single-host runtime architecture actually in force for the alpha tenant cell: Route 53 and an Elastic IP onto one Amazon Linux EC2 instance where Nginx terminates TLS in front of a Docker Compose stack carrying every pos-* service, Eureka, the Angular SSR frontend, Postgres, Kafka, Ollama and the OTEL/Prometheus/Grafana/Jaeger observability set from ECR images — instance sizing, security-group rules, port map, backup and the managed-services variant that moves Postgres, Kafka, Ollama and Grafana off-host.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/DOCKER_EC2_ARCHITECTURE.md
path: durion/docs/architecture/AWS/DOCKER_EC2_ARCHITECTURE.md
tags: [platform, architecture]
kind: Architecture
status: stable
doc_status: current
sources: [durion/docs/architecture/AWS/DOCKER_EC2_ARCHITECTURE.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-20T07:56:04-04:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/DOCKER_EC2_ARCHITECTURE.md) — `durion/docs/architecture/AWS/DOCKER_EC2_ARCHITECTURE.md`

**Kind:** Architecture
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** current
