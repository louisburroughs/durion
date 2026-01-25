# Inventory Permission Taxonomy

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API/Proxy authors  
**Last Updated:** 2026-01-25

---

## Overview

This document standardizes Inventory permission names and scope rules. It follows DECISION-INVENTORY-010 (colon-separated naming) and the RBAC framework used across Durion. All inventory actions are location-scoped unless explicitly global.

Naming pattern:
- `inventory:<resource>:<action>` (all lowercase)
- Scope: location-specific (`locationId`) unless marked global

Registration pattern (pos-security-service):
- Register: `POST /api/permissions/register { name: "inventory:...", description }`
- Assign to role: `PUT /api/roles/permissions { roleId, permissionNames: [] }`
- Check: `GET /api/roles/check-permission?userId=...&permission=...&locationId=...`

---

## Canonical Permissions

| Permission | Scope | Purpose |
| --- | --- | --- |
| `inventory:availability:query` | Location | Read availability/ATP for a product/site/bin |
| `inventory:cyclecount:plan` | Location | Create cycle count plans/tasks |
| `inventory:cyclecount:submit` | Location | Submit count results (blind/standard) |
| `inventory:cyclecount:approve` | Location | Approve cycle count adjustments |
| `inventory:adjustment:create` | Location | Create manual adjustments (append-only) |
| `inventory:adjustment:approve` | Location | Approve adjustments |
| `inventory:adjustment:override` | Location | Override putaway/capacity/validation rules when allowed |
| `inventory:picking:list` | Location | View pick lists/tasks (workorder-linked) |
| `inventory:picking:execute` | Location | Confirm/complete picks |
| `inventory:consume:execute` | Location | Issue/consume picked items to workorder |
| `inventory:return:execute` | Location | Return unused items to stock with reason |
| `inventory:receiving:create` | Location | Create receiving sessions (PO/ASN/direct) |
| `inventory:receiving:execute` | Location | Execute receiving (accept goods) |
| `inventory:location:view` | Location | View storage topology (locations/bins) |
| `inventory:location:manage` | Location | Manage storage locations (with DECISION-INVENTORY-007 restrictions) |
| `inventory:roles:define` | Global | Define/modify inventory roles & permissions (governance) |

---

## Scoping Rules

- Default scope is by `locationId`. Always pass location context to permission checks.
- Use global scope only for governance actions (e.g., `inventory:roles:define`).
- Enforce scope in Moqui proxy and backend services; never trust client-only checks.

---

## Enforcement Guidance

- Perform permission checks at operation entry (service/controller); do not rely on caller.
- Log denials with `userId`, `permission`, `locationId`, and correlationId.
- Combine with status/eligibility checks (e.g., workorder state, location active).

---

## Domain Boundaries

- Product/Catalog owns product master permissions; Inventory consumes product data.
- WorkExecution owns workorder lifecycle; Inventory permissions cover availability, picks/consumption/returns, and ledger impacts.
- Pricing owns cost-tier permissions; Inventory consumes pricing data.

---

## Change Control

- Add new permissions following the naming pattern and register before assignment.
- Avoid reusing permissions across unrelated resources; prefer additive, non-overlapping names.
- Document new permissions in this taxonomy and in BACKEND_CONTRACT_GUIDE.md when adding new operations.
