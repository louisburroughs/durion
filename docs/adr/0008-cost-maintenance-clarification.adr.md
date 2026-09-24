---
type: ADR
title: 'ADR-0008: Inventory/Accounting - Cost Maintenance Architecture - Visual Guide - Clarification Response'
description: Which domain(s) maintain the cost structure for the system?
status: deprecated
adr_status: superseded
created: '2026-01-13'
superseded_by: ADR-0048
related: [ADR-0044, ADR-0048]
tags: [adr, accounting, inventory]
---
# ADR: 0008 - Inventory/Accounting - Cost Maintenance Architecture - Visual Guide - Clarification Response

**Status:** SUPERSEDED BY [ADR-0048](0048-inventory-owned-valuation-configurable-costing-method.adr.md) (2026-09-24)  
**Carried forward by ADR-0048 §6:** the three cost concepts (standard, latest-receipt "last", weighted average), the weighted-average formula, and the
authorization split (standard cost set manually only by an authorized inventory role; last and average cost system-derived and never user-editable;
accounting read-only on item cost).  
**Retired:** the dual-ownership pattern below — accounting as "logic owner" computing last and average cost, reading and writing costs through inventory REST
endpoints, cost fields on the Product entity, and an accounting-owned `ItemCostAudit` table. Valuation is inventory-owned and not configurable (ADR-0048 §1),
accounting is event-only ([ADR-0044](0044-platform-event-only-domain-walls.adr.md) §6), and the audit trail is the inventory ledger, the revaluation and
method-change records, and the `inventory.product-value.changed` fact. The diagrams below are historical and must not be built from.  
**Date:** 2026-01-13  
**Context:** Which domain(s) maintain the cost structure for the system?
**Stakeholders:** Architecture team, Inventory domain owner, Accounting domain owner, Workexec domain owner
---

# Cost Maintenance Architecture - Visual Guide

## 🏗️  Architecture: Dual Ownership Pattern

### High-Level System Overview

```text
┌─────────────────────────────────────────────────────────────────┐
│                         POS System                              │
│                                                                 │
│  ┌──────────────────┐              ┌──────────────────┐       │
│  │                  │              │                  │       │
│  │   Inventory      │◄─────────────┤   Accounting     │       │
│  │   Service        │              │   Service        │       │
│  │                  │──────────────►│                  │       │
│  │  (Data Owner)    │              │ (Logic Owner)    │       │
│  │                  │              │                  │       │
│  └──────────────────┘              └──────────────────┘       │
│           │                                 │                  │
│           │                                 │                  │
│           ▼                                 ▼                  │
│  ┌──────────────────┐              ┌──────────────────┐       │
│  │  Product DB      │              │ ItemCostAudit DB │       │
│  │  - standardCost  │              │ - auditId        │       │
│  │  - lastCost      │              │ - oldValue       │       │
│  │  - averageCost   │              │ - newValue       │       │
│  └──────────────────┘              └──────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Purchase Order Receipt Flow (Detailed)

### Sequence: How Costs Get Updated

```text
┌──────────┐   ┌───────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│    PO    │   │  Message  │   │Accounting│   │Inventory │   │  Audit   │
│  System  │   │   Bus     │   │ Service  │   │ Service  │   │   DB     │
└────┬─────┘   └─────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘
     │               │              │              │              │
     │ 1. Receive PO │              │              │              │
     │───────────────►              │              │              │
     │               │              │              │              │
     │               │ 2. Publish   │              │              │
     │               │ PO Received  │              │              │
     │               │    Event     │              │              │
     │               │──────────────►              │              │
     │               │              │              │              │
     │               │              │ 3. GET Current Costs        │
     │               │              │─────────────►│              │
     │               │              │              │              │
     │               │              │◄─────────────│              │
     │               │              │ 4. Return    │              │
     │               │              │    Costs     │              │
     │               │              │              │              │
     │               │        ┌─────┴─────┐        │              │
     │               │        │ Calculate │        │              │
     │               │        │ Last Cost │        │              │
     │               │        │ Avg  Cost │        │              │
     │               │        └─────┬─────┘        │              │
     │               │              │              │              │
     │               │              │ 5. PUT Update Costs         │
     │               │              │─────────────►│              │
     │               │              │              │              │
     │               │              │              │ 6. Persist   │
     │               │              │              │───────────►  │
     │               │              │              │              │
     │               │              │              │ 7. Event     │
     │               │              │              │ Cost Changed │
     │               │              │◄──────────── │───────────►  │
     │               │              │              │              │
     │               │              │ 8. Create Audit Entries    │
     │               │              │─────────────────────────────►
     │               │              │              │              │
     │               │              │◄─────────────────────────────
     │               │              │ 9. Success   │              │
     │               │◄──────────────              │              │
     │               │ 10. Complete │              │              │
     │◄───────────────              │              │              │
     │ 11. Ack       │              │              │              │
     │               │              │              │              │
```

---

## 📦 Data Ownership Diagram

### Clear Boundaries: What Each Domain Owns

```text
┌─────────────────────────────────────────────────────────────────┐
│                     INVENTORY DOMAIN                            │
│                                                                 │
│  Responsibilities:                                              │
│  ✓ Store cost fields in Product entity                         │
│  ✓ Provide CRUD APIs for cost data                             │
│  ✓ Enforce authorization (who can update Standard Cost)        │
│  ✓ Validate cost values (non-negative, 4 decimal places)       │
│  ✓ Publish ItemCostChanged events for audit                    │
│                                                                 │
│  Does NOT own:                                                  │
│  ✗ Cost calculation logic                                       │
│  ✗ Purchase Order event handling                               │
│  ✗ Weighted average formula                                    │
│                                                                 │
│  APIs Exposed:                                                  │
│  • GET  /api/inventory/items/{id}/costs                        │
│  • PUT  /api/inventory/items/{id}/costs/standard (manual)      │
│  • PUT  /api/inventory/items/{id}/costs/system-update (system) │
│                                                                 │
│  Events Published:                                              │
│  • ItemCostChanged (for audit)                                 │
└─────────────────────────────────────────────────────────────────┘

                                ▲
                                │
                                │ REST API Call
                                │ (System Update)
                                │
                                │
                                │

┌─────────────────────────────────────────────────────────────────┐
│                    ACCOUNTING DOMAIN                            │
│                                                                 │
│  Responsibilities:                                              │
│  ✓ Subscribe to PurchaseOrderReceived events                   │
│  ✓ Implement Last Cost calculation (direct assignment)         │
│  ✓ Implement Average Cost calculation (weighted average)       │
│  ✓ Call Inventory API to update costs                          │
│  ✓ Create audit log entries for all changes                    │
│  ✓ Handle errors and retry logic                               │
│                                                                 │
│  Does NOT own:                                                  │
│  ✗ Cost data storage                                            │
│  ✗ Product entity                                               │
│  ✗ Standard Cost manual updates                                │
│                                                                 │
│  Events Consumed:                                               │
│  • PurchaseOrderReceived (from PO system)                       │
│                                                                 │
│  APIs Called:                                                   │
│  • GET  /api/inventory/items/{id}/costs (to get current)       │
│  • PUT  /api/inventory/items/{id}/costs/system-update          │
│                                                                 │
│  Data Owned:                                                    │
│  • ItemCostAudit table (audit trail)                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Cost Types and Their Update Rules

### Three Cost Types with Different Update Mechanisms

```text
┌────────────────────────────────────────────────────────────────┐
│                        Cost Types                               │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. STANDARD COST                                               │
│     ┌─────────────────────────────────────────────┐            │
│     │ • Manually set by authorized users          │            │
│     │ • Planned/predetermined cost                │            │
│     │ • Updated via Inventory API                 │            │
│     │ • Requires authorization (Inventory Manager)│            │
│     │ • Use case: Budgeting, variance analysis    │            │
│     └─────────────────────────────────────────────┘            │
│                                                                 │
│  2. LAST COST                                                   │
│     ┌─────────────────────────────────────────────┐            │
│     │ • Automatically updated on PO receipt        │            │
│     │ • Most recent purchase unit cost            │            │
│     │ • Formula: lastCost = receivedUnitCost      │            │
│     │ • Updated by Accounting service             │            │
│     │ • Use case: Quick reference, vendor pricing │            │
│     └─────────────────────────────────────────────┘            │
│                                                                 │
│  3. AVERAGE COST                                                │
│     ┌─────────────────────────────────────────────┐            │
│     │ • Automatically updated on PO receipt        │            │
│     │ • Weighted average of all units in stock    │            │
│     │ • Formula: ((oldQty * oldAvg) +             │            │
│     │            (recQty * recCost)) /            │            │
│     │            (oldQty + recQty)                │            │
│     │ • Updated by Accounting service             │            │
│     │ • Use case: COGS, inventory valuation       │            │
│     └─────────────────────────────────────────────┘            │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Authorization and Security

### Who Can Do What?

```text
┌─────────────────────────────────────────────────────────────┐
│                     User Roles & Permissions                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  INVENTORY MANAGER                                          │
│  ✓ Read all cost data                                      │
│  ✓ Manually update Standard Cost                           │
│  ✗ Cannot update Last Cost (system-only)                   │
│  ✗ Cannot update Average Cost (system-only)                │
│                                                             │
│  ACCOUNTING MANAGER                                         │
│  ✓ Read all cost data                                      │
│  ✓ View audit logs                                         │
│  ✓ Run financial reports                                   │
│  ✗ Cannot manually update any costs (read-only)            │
│                                                             │
│  SYSTEM (Accounting Service)                                │
│  ✓ Read all cost data                                      │
│  ✓ Update Last Cost (via PO events)                        │
│  ✓ Update Average Cost (via PO events)                     │
│  ✓ Create audit log entries                                │
│  ✗ Cannot update Standard Cost (user-only)                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Example: Purchase Order Receipt

### Real-World Scenario with Numbers

```text
┌─────────────────────────────────────────────────────────────┐
│  Initial State                                              │
│  ─────────────                                              │
│  Item ID: TIRE-12345                                        │
│  Quantity On Hand: 100 units                                │
│  Standard Cost: $50.00 (set by manager)                     │
│  Last Cost: $45.00 (from previous PO)                       │
│  Average Cost: $47.00 (weighted average)                    │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ PO Received Event
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Purchase Order Received                                    │
│  ──────────────────────                                     │
│  PO Number: PO-2024-001                                     │
│  Item ID: TIRE-12345                                        │
│  Received Quantity: 50 units                                │
│  Received Unit Cost: $48.00                                 │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ Accounting Service
                         │ Calculates New Costs
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Calculations                                               │
│  ────────────                                               │
│                                                             │
│  Last Cost = $48.00 (direct assignment)                     │
│                                                             │
│  Average Cost = ((100 × $47.00) + (50 × $48.00))          │
│                 ─────────────────────────────               │
│                        (100 + 50)                           │
│                                                             │
│               = ($4,700 + $2,400) / 150                    │
│               = $7,100 / 150                               │
│               = $47.3333                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ Update Inventory
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Final State                                                │
│  ───────────                                                │
│  Item ID: TIRE-12345                                        │
│  Quantity On Hand: 150 units (100 + 50)                     │
│  Standard Cost: $50.00 (unchanged)                          │
│  Last Cost: $48.00 (updated) ◄── Changed                   │
│  Average Cost: $47.3333 (updated) ◄── Changed              │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ Audit Logs Created
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Audit Trail                                                │
│  ───────────                                                │
│                                                             │
│  Entry 1:                                                   │
│  - Cost Type: LAST                                          │
│  - Old Value: $45.00                                        │
│  - New Value: $48.00                                        │
│  - Source: PO-2024-001                                      │
│  - Actor: system                                            │
│  - Timestamp: 2026-01-13T10:30:00Z                          │
│                                                             │
│  Entry 2:                                                   │
│  - Cost Type: AVERAGE                                       │
│  - Old Value: $47.00                                        │
│  - New Value: $47.3333                                      │
│  - Source: PO-2024-001                                      │
│  - Actor: system                                            │
│  - Timestamp: 2026-01-13T10:30:00Z                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Benefits of This Architecture

### Why Dual Ownership Works

```text
┌─────────────────────────────────────────────────────────┐
│                    Key Benefits                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ✅ Clear Boundaries                                    │
│     Each domain has a single, well-defined purpose      │
│                                                         │
│  ✅ Independent Testing                                 │
│     Can test data layer separately from business logic  │
│                                                         │
│  ✅ Independent Deployment                              │
│     Can deploy services independently                   │
│                                                         │
│  ✅ Maintainability                                     │
│     Changes to cost logic don't affect Inventory        │
│                                                         │
│  ✅ Scalability                                         │
│     Can scale services based on different needs         │
│                                                         │
│  ✅ Follows DDD Principles                              │
│     Bounded contexts with clear integration             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```
