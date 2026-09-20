---
type: Platform Document
title: Durion Positivity — Podman on EC2 Architecture
description: 'Evaluated but unadopted alternative to the Docker on EC2 host: the same single-instance topology run under rootless daemonless Podman and podman-compose — daemon and CVE-surface comparison against Docker, host setup (enable-linger, podman generate systemd units, unprivileged-port sysctl), rootless networking and podman login to ECR, and the deploy-script substitutions it would require. No Durion environment or workflow uses Podman.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/PODMAN_EC2_ARCHITECTURE.md
path: durion/docs/architecture/AWS/PODMAN_EC2_ARCHITECTURE.md
tags: [platform, architecture]
kind: Architecture
status: draft
doc_status: proposed
sources: [durion/docs/architecture/AWS/PODMAN_EC2_ARCHITECTURE.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-04-26T09:19:14-04:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/PODMAN_EC2_ARCHITECTURE.md) — `durion/docs/architecture/AWS/PODMAN_EC2_ARCHITECTURE.md`

**Kind:** Architecture
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** proposed
