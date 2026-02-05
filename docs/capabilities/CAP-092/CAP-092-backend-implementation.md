# CAP:092 Backend Implementation

**Capability:** [CAP:092] Preferences & Billing Rules  
**Branch:** `cap/CAP092`  
**Status:** In Progress  
**Date:** 2026-02-05

## Overview

This document tracks the backend implementation for CAP:092 across three microservices:
- **pos-invoice**: Billing Rules CRUD (Story #100)
- **pos-customer**: CRM Snapshot facade (Story #99)
- **pos-workorder**: PO enforcement (Story #98)

## Implementation Checklist

### ✅ pos-invoice (Story #100 - Define Billing Rules)

**Completed:**
- [x] Created entity `BillingRules` with JPA annotations
- [x] Created enums `InvoiceDeliveryMethod`, `InvoiceGroupingStrategy`
- [x] Created DTO `BillingRulesDTO`
- [x] Created repository `BillingRulesRepository`
- [x] Created service interface `BillingRulesService` (public API)
- [x] Created service implementation `BillingRulesServiceImpl`
- [x] Created REST controller `BillingRulesController`
  - `GET /v1/billing/rules/{partyId}`
  - `PUT /v1/billing/rules/{partyId}`
- [x] Added event type registration (`InvoiceEventTypes`, `InvoiceEventTypeInitializer`)
- [x] Updated `pom.xml` with required dependencies (JPA, validation, pos-events, etc.)
- [x] Updated `PosInvoiceApplication` with `@EnableJpaRepositories`, `@EnableDiscoveryClient`
- [x] Added billing defaults to `application.yml`

**Pending:**
- [ ] Create `ContractBehaviorIT` tests (AC-1 through AC-4)
- [ ] Add OpenAPI annotations review
- [ ] Test event-driven default provisioning (AC-3)

### 🔨 pos-customer (Story #99 - CRM Snapshot)

**Completed:**
- [x] Created DTOs: `CrmSnapshotDTO`, `SnapshotMetadata`, `AccountSummary`, `ContactSummary`

**Remaining Tasks:**
- [ ] Create `VehicleSummary` DTO
- [ ] Create `BillingPreferences` DTO
- [ ] Create service `CrmSnapshotService` with:
  - Snapshot by `partyId`
  - Snapshot by `vehicleId` with deterministic party resolution
- [ ] Create REST controller `CrmSnapshotController`
  - `GET /v1/crm-snapshot?partyId={id}` OR `?vehicleId={id}`
- [ ] Implement vehicle→party resolution logic (AC-2 precedence rules)
- [ ] Add authorization checks (service allowlist + scope)
- [ ] Call pos-invoice facade for billing rules
- [ ] Add event type registration
- [ ] Create `ContractBehaviorIT` tests (AC-1 through AC-6)

### 🔨 pos-workorder (Story #98 - PO Enforcement)

**Remaining Tasks:**
- [ ] Add `PurchaseOrderReference` entity/DTO:
  - `poNumber` (String, validated)
  - `attachmentId` (UUID, optional)
- [ ] Update `ApproveEstimate` command handler:
  - Fetch billing rules from CRM facade
  - Validate PO if required
  - Persist PO reference on approval
- [ ] Add PO validation:
  - Length 3–64 characters
  - Regex: `^[A-Za-z0-9][A-Za-z0-9._-]*$`
  - Error codes: `MISSING_PO_NUMBER`, `INVALID_PO_NUMBER`
- [ ] Add fail-safe behavior (block if billing rules unavailable)
- [ ] Update `EstimateApproved` event to include `purchaseOrderReference`
- [ ] Add event type for PO enforcement
- [ ] Create `ContractBehaviorIT` tests (AC-1 through AC-5)

## Contract Alignment

All implementations follow the backend contract guides:
- **Billing Rules**: `/durion/domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **CRM Snapshot**: `/durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md` (CAP:092 addendum)
- **PO Enforcement**: `/durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md` (CAP:092 addendum)

## API Gateway Routing

All endpoints use full gateway URLs:
- Billing: `http://localhost:8080/v1/billing/rules/{partyId}`
- CRM Snapshot: `http://localhost:8080/v1/crm-snapshot`
- CRM Billing Facade: `http://localhost:8080/v1/crm/accounts/parties/{partyId}/billingRules`
- Workorder: `http://localhost:8080/v1/workexec/workorders/estimates/{estimateId}/approval`

## Event Types

### pos-invoice
- `BILLING_RULES_GET` (fastRead, p50=50ms)
- `BILLING_RULES_UPSERT` (write, p50=200ms)

### pos-customer (to be added)
- `CRM_SNAPSHOT_GET` (search, p50=100ms)
- `CRM_BILLING_RULES_FACADE_GET` (fastRead, p50=50ms)

### pos-workorder (to be added)
- `WORKORDER_ESTIMATE_APPROVE` (approval, p50=500ms) - update to include PO validation

## Testing Requirements

Each module requires `ContractBehaviorIT` tests covering:
1. Happy path scenarios
2. Validation errors
3. Authorization failures
4. Idempotency
5. Concurrency invariants
6. Error code contracts

## Files Created/Modified

### pos-invoice
- `internal/entity/BillingRules.java` (NEW)
- `internal/enums/InvoiceDeliveryMethod.java` (NEW)
- `internal/enums/InvoiceGroupingStrategy.java` (NEW)
- `internal/dto/BillingRulesDTO.java` (NEW)
- `internal/repository/BillingRulesRepository.java` (NEW)
- `service/BillingRulesService.java` (NEW)
- `internal/service/BillingRulesServiceImpl.java` (NEW)
- `internal/controller/BillingRulesController.java` (NEW)
- `internal/config/InvoiceEventTypes.java` (NEW)
- `internal/config/InvoiceEventTypeInitializer.java` (NEW)
- `PosInvoiceApplication.java` (MODIFIED)
- `pom.xml` (MODIFIED)
- `application.yml` (MODIFIED)

### pos-customer
- `internal/dto/snapshot/CrmSnapshotDTO.java` (NEW)
- `internal/dto/snapshot/SnapshotMetadata.java` (NEW)
- `internal/dto/snapshot/AccountSummary.java` (NEW)
- `internal/dto/snapshot/ContactSummary.java` (NEW)

### pos-workorder
- (Pending implementation)

## Next Steps

1. **Complete pos-customer implementation:**
   - Remaining DTOs
   - Service layer with party resolution logic
   - Controller with authorization
   - Tests
2. **Implement pos-workorder PO enforcement:**
   - PurchaseOrderReference entity
   - Update ApproveEstimate handler
   - Tests
3. **Integration testing:**
   - Test full flow: workorder → CRM snapshot → billing rules facade → pos-invoice
4. **Create pull request**

## Notes

- All implementations follow null-safety conventions (`@NonNull` / `@Nullable`)
- All mutations emit events with `@EmitEvent` annotations
- All modules follow internal package structure (only `service/` is public)
- OpenAPI annotations added for REST endpoints
- Actuator endpoints exposed for observability

