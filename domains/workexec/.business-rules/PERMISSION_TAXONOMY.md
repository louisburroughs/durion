# WorkExec Domain Permission Taxonomy

**Purpose:** Define explicit permission keys for WorkExec domain operations  
**Domain:** workexec  
**Owner:** WorkExec Domain  
**Version:** 1.0  
**Status:** DRAFT (Phase 2 Planning)  
**Date:** 2026-01-24

## Overview

This document defines the complete permission taxonomy required to implement WorkExec domain stories. All permissions follow the standardized format: `domain:resource:action` per PERMISSION_REGISTRY.md.

These permissions resolve blocking questions from workexec-questions.md and follow the precedent established by Accounting and CRM domains.

## Permission Naming Convention

All WorkExec permissions follow the security domain standard:

- **Format:** `workexec:resource:action`
- **All lowercase**
- **Singular nouns for resources**
- **Three-part structure**

Examples: `workexec:estimate:create`, `workexec:workorder:approve`, `workexec:approval:view`

## WorkExec Permission Groups

### 1. Estimate Management (Issues #233-238, #271)

**Resource:** `estimate`  
**System of Record:** WorkExec (estimate master)  
**Related Decisions:** Backend contract confirmed from pos-workorder module

```yaml
domain: workexec
serviceName: pos-workorder-service
version: "1.0"

permissions:
  # Estimate CRUD
  - key: workexec:estimate:view
    description: View estimate details
    risk_level: LOW
    use_cases:
      - View estimate list with status (DRAFT/APPROVED/DECLINED/EXPIRED)
      - View estimate details including line items, totals, approval config
      - Search estimates by customer, location, date range
    required_for_stories:
      - Issue #233: Submit Estimate for Approval
      - Issue #234: Present Estimate Summary for Review
      - Issue #235: Revise Estimate Prior to Approval
      - Issue #237: Add Labor/Service to Draft Estimate
      - Issue #238: Add Parts to Draft Estimate
      - Issue #271: Capture Digital Customer Approval

  - key: workexec:estimate:create
    description: Create new draft estimates
    risk_level: MEDIUM
    use_cases:
      - Create estimate for customer/vehicle
      - Initialize with location, currency, tax region defaults
    required_for_stories:
      - Issue #233: Submit Estimate for Approval (prerequisite)

  - key: workexec:estimate:edit
    description: Edit draft estimates
    risk_level: MEDIUM
    use_cases:
      - Add/update/remove line items (parts, labor, services)
      - Update quantities, prices, notes
      - Modify estimate metadata
    required_for_stories:
      - Issue #237: Add Labor/Service to Draft Estimate
      - Issue #238: Add Parts to Draft Estimate
    notes: Only allowed when status=DRAFT

  - key: workexec:estimate:submit
    description: Submit estimate for approval
    risk_level: MEDIUM
    use_cases:
      - Transition estimate from DRAFT to approval workflow
      - Trigger approval request creation
      - Validate completeness before submission
    required_for_stories:
      - Issue #233: Submit Estimate for Approval

  - key: workexec:estimate:revise
    description: Revise estimate and create new version
    risk_level: HIGH
    use_cases:
      - Create new version from approved/declined estimate
      - Preserve approval history
      - Increment version number
    required_for_stories:
      - Issue #235: Revise Estimate Prior to Approval

  - key: workexec:estimate:approve
    description: Approve estimate with or without signature
    risk_level: HIGH
    use_cases:
      - Capture digital signature (in-person)
      - Record approval method and approver identity
      - Transition estimate to APPROVED status
    required_for_stories:
      - Issue #271: Capture Digital Customer Approval
    notes: Requires customer validation; signature data stored securely

  - key: workexec:estimate:decline
    description: Decline estimate
    risk_level: MEDIUM
    use_cases:
      - Transition estimate to DECLINED status
      - Record decline reason
    required_for_stories:
      - Issue #268: Handle Approval Expiration

  - key: workexec:estimate:reopen
    description: Reopen declined estimate
    risk_level: MEDIUM
    use_cases:
      - Transition estimate from DECLINED to DRAFT (within expiry window)
      - Resume editing after customer declines
    required_for_stories:
      - Issue #268: Handle Approval Expiration

  - key: workexec:estimate:delete
    description: Delete estimate
    risk_level: HIGH
    use_cases:
      - Remove estimate record
      - Only allowed when status=DRAFT
    notes: Soft delete; audit trail retained

  # Estimate Feature Permissions
  - key: workexec:estimate:override_price
    description: Override catalog part/labor prices
    risk_level: HIGH
    use_cases:
      - Override part unitPrice beyond catalog price
      - Override labor unitRate
      - Provide override reason
    required_for_stories:
      - Issue #238: Add Parts to Draft Estimate (price override)
      - Issue #237: Add Labor/Service (rate override)
    notes: Requires justification; audit logged

  - key: workexec:estimate:add_non_catalog_part
    description: Add non-catalog parts to estimate
    risk_level: MEDIUM
    use_cases:
      - Add part not in catalog
      - Manual entry of description, price, quantity
    required_for_stories:
      - Issue #238: Add Parts to Draft Estimate (non-catalog)

  - key: workexec:estimate:add_custom_labor
    description: Add custom labor items to estimate
    risk_level: MEDIUM
    use_cases:
      - Add labor not in service catalog
      - Manual entry of description, units, rate
    required_for_stories:
      - Issue #237: Add Labor/Service (custom labor)
```

### 2. Work Order Management (Issue #269)

**Resource:** `workorder`  
**System of Record:** WorkExec (workorder master)  
**Related Decisions:** Status machine confirmed from WorkorderStatus enum

```yaml
  # Work Order CRUD
  - key: workexec:workorder:view
    description: View work order details
    risk_level: LOW
    use_cases:
      - View work order list with status
      - View work order details including items, totals
      - Search work orders by customer, location, date range
    required_for_stories:
      - Issue #269: Record Partial Approval

  - key: workexec:workorder:create
    description: Create new work order
    risk_level: MEDIUM
    use_cases:
      - Create work order from estimate (promotion)
      - Create work order directly (without estimate)
    notes: Typically created via estimate promotion

  - key: workexec:workorder:edit
    description: Edit work order details
    risk_level: MEDIUM
    use_cases:
      - Update work order metadata
      - Modify items (subject to status rules)

  - key: workexec:workorder:transition
    description: Transition work order status
    risk_level: HIGH
    use_cases:
      - Move through state machine (DRAFT → APPROVED → ASSIGNED → WORK_IN_PROGRESS → AWAITING_PARTS/AWAITING_APPROVAL → READY_FOR_PICKUP → COMPLETED/CANCELLED)
      - Validate allowed transitions per WorkorderStatus enum
    required_for_stories:
      - Issue #269: Record Partial Approval (transition based on item approvals)

  - key: workexec:workorder:approve_items
    description: Record line-item-level approval decisions
    risk_level: HIGH
    use_cases:
      - Approve/decline individual items
      - Capture approval method and proof per item
      - Update item status (PENDING_APPROVAL → OPEN/CANCELLED)
    required_for_stories:
      - Issue #269: Record Partial Approval

  - key: workexec:workorder:cancel
    description: Cancel work order
    risk_level: HIGH
    use_cases:
      - Transition to CANCELLED status
      - Record cancellation reason
```

### 3. Approval Management (Issues #268, #271, #269)

**Resource:** `approval`  
**System of Record:** WorkExec (approval records and configuration)  
**Related Decisions:** ResolutionStatus confirmed from ApprovalRecord entity

```yaml
  # Approval Records (Audit Trail)
  - key: workexec:approval:view
    description: View approval records and audit trail
    risk_level: LOW
    use_cases:
      - View approval history for estimate/workorder
      - View approval configuration
      - Query approval status (APPROVED/REJECTED/APPROVED_WITH_EXCEPTION)
    required_for_stories:
      - Issue #268: Handle Approval Expiration
      - Issue #271: Capture Digital Customer Approval
      - Issue #269: Record Partial Approval

  - key: workexec:approval:deny
    description: Deny/reject approval request
    risk_level: MEDIUM
    use_cases:
      - Record denial with reason
      - Trigger estimate/workorder status change
    required_for_stories:
      - Issue #268: Handle Approval Expiration

  - key: workexec:approval:request_new
    description: Request new approval after expiration
    risk_level: MEDIUM
    use_cases:
      - Create new approval request for expired approval
      - Reset expiration window
    required_for_stories:
      - Issue #268: Handle Approval Expiration (post-expiration path)

  # Approval Configuration (Admin)
  - key: workexec:approval_config:view
    description: View approval configuration
    risk_level: LOW
    use_cases:
      - View approval methods per location/customer
      - View expiry policy settings

  - key: workexec:approval_config:manage
    description: Manage approval configuration
    risk_level: CRITICAL
    use_cases:
      - Create/update ApprovalConfiguration records
      - Set approval method (CLICK_CONFIRM/SIGNATURE/ELECTRONIC_SIGNATURE/VERBAL_CONFIRMATION)
      - Configure decline expiry days
      - Set signature requirements
    notes: Admin-only; affects approval workflow behavior
```

### 4. Cross-Domain Integration Permissions

```yaml
  # Catalog Search (Read-Only Dependencies)
  - key: workexec:catalog:search_parts
    description: Search parts catalog for estimate item addition
    risk_level: LOW
    use_cases:
      - Query product/parts API
      - Display part picker in estimate editor
    required_for_stories:
      - Issue #238: Add Parts to Draft Estimate
    notes: Delegates to Product/Catalog domain; WorkExec reads only

  - key: workexec:catalog:search_services
    description: Search service catalog for estimate item addition
    risk_level: LOW
    use_cases:
      - Query service catalog API
      - Display service picker in estimate editor
    required_for_stories:
      - Issue #237: Add Labor/Service to Draft Estimate
    notes: Delegates to Product/Catalog domain; WorkExec reads only
```

## Role-Based Permission Bundles

### Service Advisor (Typical Role)

- `workexec:estimate:view`
- `workexec:estimate:create`
- `workexec:estimate:edit`
- `workexec:estimate:submit`
- `workexec:estimate:approve` (in-person signature capture)
- `workexec:estimate:decline`
- `workexec:workorder:view`
- `workexec:workorder:approve_items`
- `workexec:approval:view`
- `workexec:catalog:search_parts`
- `workexec:catalog:search_services`

### Shop Manager (Elevated Role)

All Service Advisor permissions, plus:

- `workexec:estimate:revise`
- `workexec:estimate:override_price`
- `workexec:estimate:add_non_catalog_part`
- `workexec:estimate:add_custom_labor`
- `workexec:workorder:edit`
- `workexec:workorder:transition`
- `workexec:workorder:cancel`
- `workexec:approval_config:view`

### Accounting/Auditor (Read-Only)

- `workexec:estimate:view`
- `workexec:workorder:view`
- `workexec:approval:view`

### System Administrator

- `workexec:approval_config:manage`
- All other permissions (for configuration and troubleshooting)

## Security Enforcement Notes

- **Fail-closed:** All endpoints require explicit permission; deny by default (DECISION-INVENTORY-007)
- **Moqui Artifact Authz:** Screen-level and transition-level authorization enforced server-side
- **Signature Data:** Base64 signature images require `workexec:estimate:approve` and are redacted from logs/telemetry
- **Customer Validation:** Approval actions validate customer identity matches estimate/workorder customer
- **Audit Trail:** All mutations logged with `userId`, `timestamp`, `action`, `resourceId`

## References

- BACKEND_CONTRACT_GUIDE.md (confirmed endpoints and enums)
- DECISION-INVENTORY (auth patterns)
- Accounting PERMISSION_TAXONOMY.md (precedent)
- CRM PERMISSION_TAXONOMY.md (precedent)
