# Roles, JWT, and Permissions Implementation Plan

**Version:** 1.0 **Status:** Active **Last Updated:** 2026-04-12 **Related:** ADR-0040 Roles, JWT Claims, and Permission Governance Policy

---

## Purpose

This plan operationalizes ADR-0040 across platform repositories so token contracts, frontend UX gating, and backend authorization behavior remain consistent.

Contract targets:

1. `roles` govern frontend route/view visibility.
2. `perm_bits` + `perm_ver` govern backend API authorization.
3. New token issuance does not emit `authorities`.
4. Refresh tokens do not carry authorization payload claims.

---

## Tracking Model

- `NS` = Not Started
- `IP` = In Progress
- `BL` = Blocked
- `DN` = Done

Update this table in each execution wave.

| ID     | Workstream                                                                              | Repo                        | Owner                | Status | Target Date | Dependency     | Exit Criteria                                                                |
| ------ | --------------------------------------------------------------------------------------- | --------------------------- | -------------------- | ------ | ----------- | -------------- | ---------------------------------------------------------------------------- |
| RJP-01 | Freeze ADR-0040 decisions and link implementation plan                                  | durion                      | Architecture         | DN     | 2026-04-13  | None           | ADR and plan cross-linked in docs                                            |
| RJP-02 | Fix JWT role normalization (`ROLE_` once, uppercase, deduped)                           | pos-security-service        | Security             | DN     | 2026-04-14  | RJP-01         | No `ROLE_ROLE_*` output in any token path                                    |
| RJP-03 | Align access/refresh token claim issuance with ADR-0040                                 | pos-security-service        | Security             | DN     | 2026-04-14  | RJP-02         | Access includes `roles` + PERM claims, refresh excludes authz payload        |
| RJP-04 | Update contract/integration tests that currently assert empty roles                     | pos-security-service        | Security QA          | DN     | 2026-04-15  | RJP-03         | `ContractBehaviorIT` and service tests pass with ADR-consistent assertions   |
| RJP-05 | Validate gateway claim-to-authority derivation + spoofed header stripping               | pos-api-gateway             | Gateway              | DN     | 2026-04-16  | RJP-03         | Gateway tests confirm PERM decoding and stripping/reinjection behavior       |
| RJP-06 | Confirm security-common consumption compatibility (`X-Authorities`, `uid` mapping)      | pos-security-common         | Platform Security    | DN     | 2026-04-16  | RJP-05         | No claim/header mismatch regressions in dependent services                   |
| RJP-07 | Remove frontend dependency on raw `claims.authorities` in accounting/inventory UX logic | frontend                    | Frontend             | DN     | 2026-04-18  | RJP-03         | Role-based UX + backend-403 behavior preserved; no direct authorities gating |
| RJP-08 | Remove client-sent `X-Authorities` surface from workexec service + tests                | frontend                    | Frontend             | DN     | 2026-04-18  | RJP-05         | No outbound `X-Authorities` from browser code; tests updated                 |
| RJP-09 | Update cross-repo READMEs and contract docs to same claim policy                        | durion + backend + frontend | Architecture + Leads | DN     | 2026-04-19  | RJP-04, RJP-08 | Docs are non-contradictory on roles vs permissions                           |
| RJP-10 | Final verification run and sign-off                                                     | all                         | QA/Leads             | DN     | 2026-04-20  | RJP-09         | Affected test suites green and sign-off checklist complete                   |

---

## Acceptance Gates

### Security Service

- Access token includes `roles`, `perm_bits`, `perm_ver`.
- Refresh token excludes `roles`, `authorities`, `perm_bits`, `perm_ver`.
- Role normalization guards against duplicate `ROLE_` prefix and mixed casing.

### Gateway + Security Common

- Gateway decodes PERM claims to downstream `X-Authorities`.
- Inbound spoofed identity headers remain stripped by gateway.
- Downstream user-id extraction remains compatible with current claims contract.

### Frontend

- Role-based route/nav gating remains functional.
- Permission-sensitive actions rely on backend `403` outcomes instead of token `authorities`.
- Browser clients do not emit `X-Authorities` headers.

---

## Execution Log

| Date       | Actor | IDs            | Notes                                                                                                        |
| ---------- | ----- | -------------- | ------------------------------------------------------------------------------------------------------------ |
| 2026-04-12 | Codex | RJP-01..RJP-10 | Plan initialized from ADR-0040 implementation notes                                                          |
| 2026-04-12 | Codex | RJP-02..RJP-10 | Implemented code, docs, and targeted test verification across backend, gateway/security-common, and frontend |
