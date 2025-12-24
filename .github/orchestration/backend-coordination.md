## Backend Coordination – Positivity / durion-positivity-backend

- Target agents: Backend agents working in durion-positivity-backend and related services
- Inputs: story-sequence.md, frontend-coordination.md, durion-positivity-backend [STORY] issues
- Last updated: <to be filled by agents>

### 1. Backend Stories Prioritized by Frontend Impact

| Orchestration ID | Backend Issue | Domain | Classification | # Frontend Stories Unblocked | Priority | Status |
| ---------------- | ------------- | ------ | -------------- | --------------------------- | -------- | ------ |
| ORCH-001 | [STORY-BE-XXXX](<link>) | <domain> | Backend-First | 3 | High | Open |

### 2. Backend Stories with No Current Frontend Dependencies

| Orchestration ID | Backend Issue | Domain | Reason No Dependency | Suggested Scheduling |
| ---------------- | ------------- | ------ | -------------------- | -------------------- |
| ORCH-050 | [STORY-BE-YYYY](<link>) | <domain> | Internal-only improvement | Defer if capacity limited |

### 3. Frontend-Dependent Requirements for Backend Stories

| Backend Issue | Dependent Frontend Issue(s) | Required Endpoint(s) | Request/Response Schema Reference | Business Rules Highlights |
| ------------- | --------------------------- | -------------------- | --------------------------------- | ------------------------ |
| [STORY-BE-ZZZZ](<link>) | [STORY-UI-AAAA](<link>) | POST /api/example | OpenAPI: <link> | Validate pricing, auth via JWT. |

### 4. Usage Rules for Backend Agents

1. Use this document and story-sequence.md as the primary planning inputs.
2. Prioritize Backend-First stories that unblock the largest number of frontend stories.
3. For Parallel stories, implement APIs according to referenced contracts to avoid breaking the frontend.
4. Avoid creating ad-hoc stub endpoints; only create temporary behavior when explicitly requested and constrained in orchestration documents.
5. Operate in a silo: rely on orchestration documents and issue content, not direct frontend communication.
