repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Tool Adapters v1: Implement initial domain action set + contracts"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:architecture
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:architecture
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As the NLTI platform, I want a first set of domain tool adapters with stable contracts so that NLTI can execute at least 10 common cross-domain tasks end-to-end under authorization control.

## Actors & Stakeholders
- **Primary actor:** NLTI Execution Orchestrator invoking adapters.
- **Secondary actors:** Domain services (workexec, accounting, inventory, crm).
- **Stakeholders:** Security (permissions), QA (contract tests), domain owners.

## Preconditions
- Tool Registry exists and can register actions (Capability 03).
- Execution Orchestrator exists (Capability 05).
- Confirmation gate exists for risky actions (Capability 06).

## Functional Behavior
1. **Define v1 Adapter Interface**
   - Each adapter exposes action handlers:
     - validate inputs
     - invoke underlying domain API
     - return normalized output schema
2. **Register Actions**
   Register (at minimum) the following actions in Tool Registry (IDs illustrative):
   - WorkExec:
     - `workexec.listCompletedWorkOrders`
     - `workexec.closeWorkOrder`
     - `workexec.dailySummary`
   - Accounting:
     - `accounting.listUnpaidInvoices`
     - `accounting.explainSuspense`
     - `accounting.reprocessFailedPayments` (permissioned, potentially HIGH risk)
   - Inventory:
     - `inventory.lowStockReport`
     - `inventory.adjustOnHandCount` (requires confirmation for bulk)
   - CRM:
     - `crm.findCustomer`
     - `crm.getCustomerContacts`
     - `crm.addCustomerNote`
3. **Normalization**
   - Adapter outputs MUST include:
     - `actionId`
     - `status` (OK|ERROR)
     - `summaryText`
     - `data` (action-specific)
4. **Authorization**
   - Ensure every adapter invocation is preceded by authorization check (registry-based).
5. **Contract Tests**
   - Provide contract tests per adapter action for:
     - success responses
     - authz denial
     - validation errors

## Alternate / Error Flows
- Domain API timeout → handled by orchestrator retry policy.
- Domain validation error → adapter returns structured ERROR with details.
- Unknown entity reference → return NOT_FOUND style error.

## Business Rules
- NLTI adapters do not own domain state; domain services remain authoritative.
- Adapter contracts must be versioned and backward compatible within v1.

## Data Requirements
- Define input/output schemas (v1) for each action, minimum fields required for tests.
- Ensure correlationId is propagated to domain calls.

## Acceptance Criteria
- **Given** a supported NLTI task mapped to an actionId, **when** executed, **then** the adapter returns a normalized OK response with summaryText.
- **Given** a missing required input, **when** adapter validates, **then** it returns a structured ERROR without calling downstream domain service.
- **Given** an unauthorized user, **when** action is invoked, **then** NLTI returns NOT_AUTHORIZED and does not call downstream.
- **Given** at least 10 common tasks, **when** executed end-to-end, **then** they succeed across at least 3 domains.

## Audit & Observability
- Adapter invocation logs include correlationId, actionId, downstream latency, outcome.
- Metrics per action: call_count, error_rate, latency_ms.

## Original Story (Unmodified – For Traceability)
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