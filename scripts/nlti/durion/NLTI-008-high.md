---
repo: louisburroughs/durion
title: "[STORY] NLTI Domain Tool Adapters v1 (WorkExec, Accounting, Inventory, CRM)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to perform real work across core domains using approved tool adapters so I can complete common tasks without switching between systems.

## Functional Behavior
Implement an initial set of NLTI tools/actions for:
- WorkExec: list/close completed work orders, daily summary
- Accounting: list unpaid invoices, explain suspense status, reprocess failed payments (if authorized)
- Inventory: low stock report, adjust counts (with confirmation)
- CRM: customer lookup, contact details, notes

## Acceptance Criteria
- Given a supported task, when invoked via NLTI, then the correct domain adapter is called and a structured result is returned.
- Given an unauthorized action, when invoked, then NLTI blocks it and explains why.