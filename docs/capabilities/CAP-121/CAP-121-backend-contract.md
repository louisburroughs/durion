---
title: CAP-121 Backend Contract
capability_id: CAP:121
capability_name: "[CAP] Job Time Tracking (Workexec Linkage)"
domain: workexec
doc_type: capability_contract_detail
contract_guide: domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md
template_ref: domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md
last_updated: 2026-02-19
---

# CAP-121 Backend Contract: Job Time Tracking (Workexec Linkage)

## Overview

Implements job time tracking endpoints in the `pos-workorder` service for the WorkExec domain. 
Covers timer start/stop workflows (Issue #82), labor performed submission (Issue #81), 
and job time totals query (Issue #80).

## Backend Issues

- [#80 Attendance vs Job Time Discrepancy Report](https://github.com/louisburroughs/durion-positivity-backend/issues/80)
- [#81 Submit Job Time as Labor Performed (Idempotent)](https://github.com/louisburroughs/durion-positivity-backend/issues/81)
- [#82 Start/Stop Timer Against Assigned Workorder Task](https://github.com/louisburroughs/durion-positivity-backend/issues/82)

## OpenAPI Source

`pos-workorder/openapi.json` — tag: `workexec-time-tracking-controller`

## Endpoints

All paths are through the API gateway: `http://localhost:8080`

| Method | Path | Story | Description |
|--------|------|-------|-------------|
| GET | /v1/workexec/time-entries/timer/active | #82 | Get active timer for authenticated mechanic |
| POST | /v1/workexec/time-entries/timer/start | #82 | Start a timer against an assigned workorder |
| POST | /v1/workexec/time-entries/timer/stop | #82 | Stop active timer(s) for authenticated mechanic |
| POST | /v1/workexec/labor-performed | #81 | Submit finalized job time as labor performed |
| GET | /v1/workexec/job-time-totals | #80 | Get daily job time totals by technician+location |

## Story Fulfillment Handoff

Use this block with `backend-story-fulfillment.prompt.md`:

```yaml
capability_label: "CAP:121"
capability_id: "121"
domain: "workexec"
parent_capability_number: 121
parent_capability_url: "https://github.com/louisburroughs/durion/issues/121"
parent_capability_title: "[CAP] Job Time Tracking (Workexec Linkage)"
parent_stories_list: |
  - #80: Attendance vs Job Time Discrepancy Report
  - #81: Submit Job Time to workexec as Labor Performed (Idempotent)
  - #82: Start/Stop Timer Against Assigned Workorder Task
backend_child_issues: |
  - [#80](https://github.com/louisburroughs/durion-positivity-backend/issues/80)
  - [#81](https://github.com/louisburroughs/durion-positivity-backend/issues/81)
  - [#82](https://github.com/louisburroughs/durion-positivity-backend/issues/82)
OPENAPI_PATH: pos-workorder/openapi.json
backend_repo: louisburroughs/durion-positivity-backend
```
