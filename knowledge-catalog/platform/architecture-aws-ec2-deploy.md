---
type: Platform Document
title: EC2 SSM Deployment Setup
description: 'One-time AWS setup that lets the deploy-alpha job in build-push-ecr.yml reach the alpha EC2 host over Systems Manager instead of SSH: the S3 staging bucket for compose files, the IAM permissions the GitHub OIDC role and the EC2 instance role each need, the GitHub secrets and variables (AWS_ROLE_ARN, ALPHA_EC2_INSTANCE_ID, ALPHA_DEPLOY_BUCKET, AUTO_DEPLOY_ALPHA), the on-instance deploy-backend.sh contract, and how to verify SSM access and trigger a deploy.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/ec2-deploy.md
path: durion/docs/architecture/AWS/ec2-deploy.md
tags: [platform, architecture]
kind: Runbook
status: stable
doc_status: current
sources: [durion/docs/architecture/AWS/ec2-deploy.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-20T07:56:04-04:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/AWS/ec2-deploy.md) — `durion/docs/architecture/AWS/ec2-deploy.md`

**Kind:** Runbook
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** current
