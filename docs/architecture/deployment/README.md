# Deployment Architecture

This directory contains deployment-focused architecture documents for Durion.

## Documents

- [Foundation-First Tenant Cell Deployment Architecture](./FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md) - Reference architecture for isolated per-organization runtime cells, persistent storage, time simulation, and release flow boundaries
- [Phased CI/CD and Runtime Plan](./PHASED_CICD_AND_RUNTIME_PLAN.md) - Sequenced implementation plan for control plane, runtime, artifact pipelines, deployment promotion, time simulation, and tenant operations
- [Backend Preload Tables](./data-migration/BACKEND_PRELOAD_TABLES.md) - Backend module crawl of bootstrap-critical and operational reference tables that should be preloaded for a usable deployment
- [Flyway Migration Cleanup Plan](./data-migration/FLYWAY_MIGRATION_CLEANUP_PLAN.md) - Module-by-module plan to standardize Flyway usage, repair migration graphs, and baseline persistent databases before seed migrations
- [Flyway vs Entity Gap Report](./data-migration/FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md) - Module-by-module gap analysis comparing JPA entity table mappings with Flyway migration table coverage
- [Seed Input Contract](./data-migration/SEED_INPUT_CONTRACT.md) - Field-level `seed-input.yaml` contract describing what is derived, generated, and user-supplied for preload SQL generation
- [Seed Input Example](./seed-input.example.yaml) - Editable baseline input file matching the seed input contract
- [Deployment Manifest Index](./manifests/README.md) - Reference manifests for tenant-cell environments and runtime targets

## Scope

These documents define the target deployment model and operational boundaries that later implementation plans, CI/CD workflows, and runbooks should follow.

1. Install AWS CLI v2 + configure credentials
2. Register domain name
3. git commit the 4 changed/new files across the 3 repos (package-lock.json + workflows + compose + runbook)
4. git push → triggers build-push-ecr.yml workflows → images land in ECR
   (ECR repos and IAM OIDC role must exist first — chicken-and-egg:
    run Sections 1–3 of the runbook manually before pushing the workflows)
5. Run Sections 1–7 of the runbook (AWS infra provisioning, no SSH yet)
6. SSH in → run Sections 8–9 (docker, nginx, TLS)
7. Run Sections 10–12 (clone, deploy, health check)
8. Run Sections 13–15 (seed, backup cron, smoke)

ssh -i ~/.ssh/durion-alpha-key.pem ec2-user@34.202.151.97

ecrlogin

docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  logs --tail=200 pos-vehicle-inventory

docker inspect backend-pos-frontend-1 --format '{{json .State.Health}}' | jq

curl -i <http://localhost:4200/>
curl -i <https://durionpos.org>

docker compose \
  -f docker-compose.yml \
  -f /opt/durion/alpha/docker-compose.prod.yml \
  --env-file /opt/durion/alpha/.env \
  ps

  What you need to set now:

New repo secret: ALPHA_EC2_INSTANCE_ID (e.g. i-xxxxxxxxxxxx).
GitHub OIDC role (AWS_ROLE_ARN) must allow:
ssm:SendCommand
ssm:GetCommandInvocation
ssm:ListCommandInvocations (optional but useful)
EC2 instance role must include AmazonSSMManagedInstanceCore and be online in Systems Manager.
Next step:

Push this workflow change.
Re-run Build and Push to ECR with deploy_alpha=true.
If it fails, share that job log and I’ll tune IAM/SSM permissions quickly.
