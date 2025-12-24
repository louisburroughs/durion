## Story Orchestration – Global Sequence

- Source repository: durion (workspace)
- Managed by: Workspace Story Orchestration System
- Last updated: <to be filled by agents>

### 1. Classification Legend

- Backend-First: Backend must be implemented before dependent frontend work
- Frontend-First: Frontend structure/workflow must be defined before backend
- Parallel: Frontend and backend can proceed in parallel using agreed contracts

### 2. Global Story List and Ordering

| Orchestration ID | GitHub Issue | Repository | Domain | Classification | Depends On | Blocks | Priority | Status | Sprint Target |
| ---------------- | ------------ | ---------- | ------ | -------------- | ---------- | ------ | -------- | ------ | ------------- |
| ORCH-001 | [STORY-XXXX](<link>) | durion | <domain> | Backend-First | – | – | High | Open | Sprint-01 |

> NOTE: This table is the single source of truth for story ordering across frontend and backend. Agents SHALL update it whenever sequencing changes.

### 3. Dependency Overview

- High-level description of major dependency chains
- Example: "All pricing stories depend on ORCH-010 (core pricing API)."

### 4. Sequencing Rules

1. Backend-First stories MUST be scheduled and completed before dependent frontend stories begin.
2. Parallel stories MAY be implemented on both frontend and backend in the same sprint if contracts are stable.
3. Frontend-First stories MUST include clear UI/UX and API requirements before backend implementation starts.
4. Stories that would require stubs SHOULD be delayed in favor of stories that can be implemented without stubs whenever possible.

### 5. Change Log (Optional)

| Date | Author/Agent | Summary of Change |
| ---- | ------------ | ----------------- |
| YYYY-MM-DD | <agent> | Initial orchestration skeleton created. |
