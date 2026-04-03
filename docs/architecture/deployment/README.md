# Deployment Architecture

This directory contains deployment-focused architecture documents for Durion.

## Documents

- [Foundation-First Tenant Cell Deployment Architecture](./FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md) - Reference architecture for isolated per-organization runtime cells, persistent storage, time simulation, and release flow boundaries
- [Phased CI/CD and Runtime Plan](./PHASED_CICD_AND_RUNTIME_PLAN.md) - Sequenced implementation plan for control plane, runtime, artifact pipelines, deployment promotion, time simulation, and tenant operations
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



Name	Size	Digest	
louisburroughs~durion-positivity-backend~62FV1P.dockerbuild
43.8 KB	
sha256:ddd2992ad48b0baaf955d4222cb23c9effdd95ea60d7077bcbff9e903dcbb265
louisburroughs~durion-positivity-backend~7E0TCI.dockerbuild
43.4 KB	
sha256:f4ce16a2781a93c2891c1b17b7d2f77be193bce660c3428bc555b96e707e2366
louisburroughs~durion-positivity-backend~97XJS7.dockerbuild
44.3 KB	
sha256:0037e2b99f902cea1be7844c7b33534344e4827b7a6e1b9aaf9053a6cc879516
louisburroughs~durion-positivity-backend~9GNMA3.dockerbuild
43.2 KB	
sha256:bc0aae8498cb28b8019a217d7cbc8e0771f9efa2fc5929527cd9c9b99cad002c
louisburroughs~durion-positivity-backend~9N0HMB.dockerbuild
42.8 KB	
sha256:f0ff59227572ea3e222095daaf9056c83f9e12e7bbefccc45db1d4de0f1424ba
louisburroughs~durion-positivity-backend~9YVNPN.dockerbuild
44.1 KB	
sha256:b2b342fa35a1e2b8f2bb3c45a7743d3239693c1882e57e548a528d2c587fd120
louisburroughs~durion-positivity-backend~AGWGO5.dockerbuild
44.1 KB	
sha256:f2aac7fe28928ffa1868e333272fd3a81c01e2be26d1b538ed27de8d005253bd
louisburroughs~durion-positivity-backend~CKVR3D.dockerbuild
45.1 KB	
sha256:7fcb829c28a27eb16be3951d05b1161af6278371739966d1ce7e9ade02e19bb7
louisburroughs~durion-positivity-backend~GADXZ3.dockerbuild
42.4 KB	
sha256:13cec72f1b6fa995ea058b6692832842aa861c387660568145f7d48573a011d8
louisburroughs~durion-positivity-backend~HFL3CA.dockerbuild
43.1 KB	
sha256:c56dcf45ccb8e82cd7dceba3be38b29f878083501a053cd638afc463cc262c56
louisburroughs~durion-positivity-backend~K1VK4P.dockerbuild
44 KB	
sha256:a1392b8b71c32360f2ca989569a838f1a86fe852d8367601fc1b5e04c56ad979
louisburroughs~durion-positivity-backend~KKC7Q1.dockerbuild
43.8 KB	
sha256:d840e67587cf655cf97c5d5944c66373697c853092aa80b66853141cb1b54daa
louisburroughs~durion-positivity-backend~KOY4RN.dockerbuild
43.6 KB	
sha256:2c4f2aa219ab6c71cbaeffbc2109a7623d1a86b330f543105752a32aaea2a3f1
louisburroughs~durion-positivity-backend~MZ6PJB.dockerbuild
43.2 KB	
sha256:2f0aa188f1203f09bebf7db666b861868eca6b79cea28d0c0a814c1725eee7d6
louisburroughs~durion-positivity-backend~O67LAT.dockerbuild
43.9 KB	
sha256:0b233781f412c528aecf4b416633a587024c0a94de5d156a27845c557e06dcde
louisburroughs~durion-positivity-backend~VLG0TO.dockerbuild
44.2 KB	
sha256:94f7b1d94bf105b42b2400be6a5365c06c9d6835c42aa7619b5e9800bd16124c
service-jars
3.65 GB	
sha256:3bba4b3c047fe5f1698fc8b6fa2583febeedc6476b1aa43f5203114c7d063184





#6 [builder 1/6] FROM docker.io/library/node:22-alpine@sha256:4d64b49e6c891c8fc821007cb1cdc6c0db7773110ac2c34bf2e6960adef62ed3
#6 resolve docker.io/library/node:22-alpine@sha256:4d64b49e6c891c8fc821007cb1cdc6c0db7773110ac2c34bf2e6960adef62ed3 done
#6 sha256:087b41e5c641ca5ca6e39de79290a61a4f50b3b3a5fbe7db44a0f05c103a6275 447B / 447B 0.0s done
#6 sha256:43fd9b73af2598c327c66de0de2e9ebe61c85bb1924fcfdf77b80055161a4b84 1.26MB / 1.26MB 0.1s done
#6 sha256:589002ba0eaed121a1dbf42f6648f29e5be55d5c8a6ee0f8eaa0285cc21ac153 3.86MB / 3.86MB 0.1s done
#6 extracting sha256:589002ba0eaed121a1dbf42f6648f29e5be55d5c8a6ee0f8eaa0285cc21ac153
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 4.19MB / 51.96MB 0.2s
#6 ...

#7 [internal] load build context
#7 transferring context: 2.50MB 0.2s done
#7 DONE 0.2s

#6 [builder 1/6] FROM docker.io/library/node:22-alpine@sha256:4d64b49e6c891c8fc821007cb1cdc6c0db7773110ac2c34bf2e6960adef62ed3
#6 extracting sha256:589002ba0eaed121a1dbf42f6648f29e5be55d5c8a6ee0f8eaa0285cc21ac153 0.1s done
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 10.49MB / 51.96MB 0.3s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 15.73MB / 51.96MB 0.5s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 27.26MB / 51.96MB 0.6s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 33.55MB / 51.96MB 0.8s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 37.75MB / 51.96MB 0.9s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 41.94MB / 51.96MB 1.1s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 47.19MB / 51.96MB 1.2s
#6 sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 51.96MB / 51.96MB 1.3s done
#6 extracting sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000
#6 extracting sha256:7ac107224afb008bb83b20444cbc051b129c0ccd8b8db1f42662ab3c42eb0000 1.0s done
#6 DONE 2.3s

#6 [builder 1/6] FROM docker.io/library/node:22-alpine@sha256:4d64b49e6c891c8fc821007cb1cdc6c0db7773110ac2c34bf2e6960adef62ed3
#6 extracting sha256:43fd9b73af2598c327c66de0de2e9ebe61c85bb1924fcfdf77b80055161a4b84 0.0s done
#6 extracting sha256:087b41e5c641ca5ca6e39de79290a61a4f50b3b3a5fbe7db44a0f05c103a6275 done
#6 DONE 2.4s

#8 [builder 2/6] WORKDIR /app
#8 DONE 0.2s

#9 [builder 3/6] COPY package*.json ./
#9 DONE 0.0s

#10 [builder 4/6] RUN npm ci
#10 73.64 npm notice
#10 73.64 npm notice New major version of npm available! 10.9.7 -> 11.12.1
#10 73.64 npm notice Changelog: https://github.com/npm/cli/releases/tag/v11.12.1
#10 73.64 npm notice To update run: npm install -g npm@11.12.1
#10 73.64 npm notice
#10 73.64 npm error Exit handler never called!
#10 73.64 npm error This is an error with npm itself. Please report this error at:
#10 73.64 npm error   <https://github.com/npm/cli/issues>
#10 73.64 npm error A complete log of this run can be found in: /root/.npm/_logs/2026-04-03T17_06_37_897Z-debug-0.log
#10 ERROR: process "/bin/sh -c npm ci" did not complete successfully: exit code: 1
------
 > [builder 4/6] RUN npm ci:
73.64 npm notice
73.64 npm notice New major version of npm available! 10.9.7 -> 11.12.1
73.64 npm notice Changelog: https://github.com/npm/cli/releases/tag/v11.12.1
73.64 npm notice To update run: npm install -g npm@11.12.1
73.64 npm notice
73.64 npm error Exit handler never called!
73.64 npm error This is an error with npm itself. Please report this error at:
73.64 npm error   <https://github.com/npm/cli/issues>
73.64 npm error A complete log of this run can be found in: /root/.npm/_logs/2026-04-03T17_06_37_897Z-debug-0.log
------
Dockerfile:5
--------------------
   3 |     WORKDIR /app
   4 |     COPY package*.json ./
   5 | >>> RUN npm ci
   6 |     COPY . .
   7 |     RUN npm run build
--------------------
ERROR: failed to build: failed to solve: process "/bin/sh -c npm ci" did not complete successfully: exit code: 1
Reference
Check build summary support
Error: buildx failed with: ERROR: failed to build: failed to solve: process "/bin/sh -c npm ci" did not complete successfully: exit code: 1