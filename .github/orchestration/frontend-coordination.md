## Frontend Coordination – Moqui / DETSMS

- Target agents: Frontend agents working in durion-moqui-frontend and related UI projects
- Inputs: story-sequence.md, durion-moqui-frontend [STORY] issues
- Last updated: <to be filled by agents>

### 1. Ready Frontend Stories (Unblocked)

| Orchestration ID | Frontend Issue | Domain | Classification | Required Backend Status | Notes |
| ---------------- | -------------- | ------ | -------------- | ----------------------- | ----- |
| ORCH-001 | [STORY-UI-XXXX](<link>) | <domain> | Parallel | Backend contract agreed | Ready to start. |

### 2. Blocked Frontend Stories (Waiting on Backend)

| Orchestration ID | Frontend Issue | Blocking Backend Story | Required API/Contract | Stub Allowed? | Stub Rules/Constraints |
| ---------------- | -------------- | ---------------------- | --------------------- | ------------ | --------------------- |
| ORCH-010 | [STORY-UI-YYYY](<link>) | [STORY-BE-ZZZZ](<link>) | /api/example | No | – |

> Frontend agents SHALL NOT start blocked stories unless "Stub Allowed?" is "Yes" and stub rules are explicitly defined.

### 3. Parallel Stories

| Orchestration ID | Frontend Issue | Backend Issue | Agreed Contract Reference | Notes |
| ---------------- | -------------- | ------------- | ------------------------- | ----- |
| ORCH-020 | [STORY-UI-AAAA](<link>) | [STORY-BE-BBBB](<link>) | OpenAPI: <link> | Work can proceed in parallel. |

### 4. Usage Rules for Frontend Agents

1. Always read story-sequence.md and this document before selecting work.
2. Prefer "Ready" and "Parallel" stories over "Blocked" stories.
3. For Parallel stories, implement UI strictly according to referenced contracts.
4. Only create frontend stubs when this document explicitly allows it and defines constraints.
5. Operate in a silo: rely on orchestration documents and issue content, not direct backend communication.
