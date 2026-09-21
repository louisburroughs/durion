---
type: Architecture
title: Entity Relationship Migration Ledger
description: The per-field record of which backend entity scalar ids became JPA relationships, which must stay scalar, and which are deferred — plus the two-step migration recipe and checklist for converting one.
status: Current
tags: [jpa, entities, data-migration, hibernate, relationships, ledger]
---

A scalar `UUID` field on a backend entity is either a deliberate cross-service reference or an
unconverted same-module relationship, and this ledger records which, field by field, together with
the recipe for converting one.

Last Updated: 2026-03-10 (queue); 2026-09-20 (recipe and checklist folded in from the retired
execution plans).

## Status Legend

- `CONVERT_NOW`: same-module relationship candidate ready for migration
- `DONE`: converted and validated
- `KEEP_SCALAR`: cross-service/external reference, must remain scalar
- `DEFER`: postponed due lifecycle/cycle/ownership risk

The `CONVERT_NOW` queue is drained: every approved candidate is `DONE`. What remains is
`KEEP_SCALAR` (settled, do not revisit) and `DEFER` (open, and the reason each was deferred is the
work item). Picking up a `DEFER` row means following
[Converting a deferred row](#converting-a-deferred-row) below.

## Queue

| Module | Entity | Field | Classification | Status | Notes |
|---|---|---|---|---|---|
| `pos-price` | `PromotionEligibilityRule` | `promotionId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne PromotionOffer` on 2026-03-10. |
| `pos-accounting` | `GLMapping` | `postingCategoryId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne PostingCategory` on 2026-03-10 with scalar compatibility accessor methods. |
| `pos-accounting` | `GLMapping` | `glAccountId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne GLAccount` on 2026-03-10. |
| `pos-accounting` | `GLMapping` | `mappingKeyId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne MappingKey` on 2026-03-10. |
| `pos-accounting` | `Reconciliation` | `glAccountId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne GLAccount` on 2026-03-10. |
| `pos-accounting` | `StatementLineMapping` | `glAccountId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne GLAccount` on 2026-03-10. |
| `pos-accounting` | `DefaultGLMapping` | `debitAccountId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne GLAccount debitAccount` on 2026-03-10. |
| `pos-accounting` | `DefaultGLMapping` | `creditAccountId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne GLAccount creditAccount` on 2026-03-10. |
| `pos-accounting` | `JournalEntryLine` | `glAccountId` | `CONVERT_NOW` | `DONE` | Dual-mapped fix: removed scalar, made `@ManyToOne GLAccount` owning on 2026-03-10. |
| `pos-accounting` | `JournalEntry` | `postingRuleSetId` | `CONVERT_NOW` | `DONE` | Dual-mapped fix: removed scalar, made `@ManyToOne PostingRuleSet` owning on 2026-03-10. |
| `pos-accounting` | `JournalEntry` | `postingRuleVersionId` | `CONVERT_NOW` | `DONE` | Dual-mapped fix: removed scalar, made `@ManyToOne PostingRuleVersion` owning on 2026-03-10. |
| `pos-accounting` | `GLAccount` | `parentAccountId` | `CONVERT_NOW` | `DONE` | Self-ref: converted to `@ManyToOne GLAccount parentAccount` on 2026-03-10. |
| `pos-accounting` | `JournalEntry` | `reversalJournalEntryId` | `CONVERT_NOW` | `DONE` | Self-ref: converted to `@ManyToOne JournalEntry reversalJournalEntry` on 2026-03-10. |
| `pos-accounting` | `JournalEntry` | `reversedByJournalEntryId` | `CONVERT_NOW` | `DONE` | Self-ref: converted to `@ManyToOne JournalEntry reversedByJournalEntry` on 2026-03-10. |
| `pos-accounting` | `VendorBill` | `journalEntryId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne JournalEntry` on 2026-03-10. |
| `pos-accounting` | `APPayment` | `glJournalEntryId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne JournalEntry glJournalEntry` on 2026-03-10. |
| `pos-workorder` | `EstimateSnapshot` | `estimateId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Estimate estimate` on 2026-03-10. |
| `pos-workorder` | `WorkorderSnapshot` | `workorderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workorder` on 2026-03-10. |
| `pos-workorder` | `WorkorderStateTransition` | `workorderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workorder` on 2026-03-10. |
| `pos-workorder` | `ApprovalRecord` | `changeRequestId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne ChangeRequest changeRequest` on 2026-03-10. |
| `pos-workorder` | `ApprovalRecord` | `workorderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workorder` on 2026-03-10. |
| `pos-workorder` | `Estimate` | `approvalConfigurationId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne ApprovalConfiguration approvalConfiguration` on 2026-03-10. |
| `pos-workorder` | `WorkorderPart` | `originEstimateItemId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne EstimateItem originEstimateItem` on 2026-03-10. |
| `pos-workorder` | `WorkorderPart` | `changeRequestId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne ChangeRequest changeRequest` on 2026-03-10. |
| `pos-workorder` | `WorkorderService` | `originEstimateItemId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne EstimateItem originEstimateItem` on 2026-03-10. |
| `pos-workorder` | `WorkorderService` | `changeRequestId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne ChangeRequest changeRequest` on 2026-03-10. |
| `pos-workorder` | `TimeEntryAdjustment` | `timeEntryId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne TimeEntry timeEntry` on 2026-03-10. Non-standard PK: `timeEntryId`. |
| `pos-workorder` | `WorkOrderPartSubstitution` | `workorderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workorder` on 2026-03-10. |
| `pos-workorder` | `WorkOrderPartSubstitution` | `workorderLineItemId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne WorkorderPart workorderLineItem` on 2026-03-10. |
| `pos-workorder` | `TravelSegment` | `workOrderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workOrder` (nullable) on 2026-03-10. |
| `pos-workorder` | `TravelSegmentAdjustment` | `travelSegmentId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne TravelSegment travelSegment` on 2026-03-10. Non-standard PK: `travelSegmentId`. |
| `pos-workorder` | `SubstituteAudit` | `linkId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne SubstituteLink link` on 2026-03-10. |
| `pos-workorder` | `WorkorderLaborEntry` | `workorderServiceId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne WorkorderService workorderService` on 2026-03-10. |
| `pos-workorder` | `TimeEntry` | `workOrderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workOrder` on 2026-03-10. |
| `pos-workorder` | `WorkSession` | `workOrderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workOrder` on 2026-03-10. |
| `pos-workorder` | `TechnicianAssignment` | `workorderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workorder` on 2026-03-10. |
| `pos-workorder` | `WorkorderPartAdjustmentEvent` | `workorderId` | `DEFER` | `DEFER` | Event entity; should not have JPA relationships. |
| `pos-workorder` | `WorkorderPartAdjustmentEvent` | `substitutedWithPartId` | `DEFER` | `DEFER` | Event entity; should not have JPA relationships. |
| `pos-workorder` | `WorkorderPartUsageEvent` | `workorderId` | `DEFER` | `DEFER` | Event entity; should not have JPA relationships. |
| `pos-workorder` | `ApprovalConfiguration` | `customerId` | `KEEP_SCALAR` | `DONE` | Cross-service: customer owned by pos-customer. |
| `pos-workorder` | `Estimate` | `customerId` | `KEEP_SCALAR` | `DONE` | Cross-service: customer owned by pos-customer. |
| `pos-workorder` | `Estimate` | `vehicleId` | `KEEP_SCALAR` | `DONE` | Cross-service: vehicle owned by pos-vehicle-*. |
| `pos-workorder` | `Workorder` | `customerId` | `KEEP_SCALAR` | `DONE` | Cross-service: customer owned by pos-customer. |
| `pos-workorder` | `Workorder` | `vehicleId` | `KEEP_SCALAR` | `DONE` | Cross-service: vehicle owned by pos-vehicle-*. |
| `pos-workorder` | `IdempotencyKey` | `invoiceId` | `KEEP_SCALAR` | `DONE` | Cross-service: invoice owned by pos-invoice. |
| `pos-workorder` | `IdempotencyKey` | `partUsageEventId` | `DEFER` | `DEFER` | Event entity target; requires lifecycle strategy. |
| `pos-workorder` | `IdempotencyKey` | `partAdjustmentEventId` | `DEFER` | `DEFER` | Event entity target; requires lifecycle strategy. |
| `pos-price` | `RestrictionRule` | `productId` | `KEEP_SCALAR` | `DONE` | Product owned by another service/module. |
| `pos-price` | `ProductBasePrice` | `productId` | `KEEP_SCALAR` | `DONE` | Product owned by another service/module. |
| `pos-price` | `LocationPriceOverride` | `productId` | `KEEP_SCALAR` | `DONE` | Product owned by another service/module. |
| `pos-price` | `LocationPriceOverride` | `locationId` | `KEEP_SCALAR` | `DONE` | Location owned by another service/module. |
| `pos-price` | `CustomerTierPricingRule` | `productId` | `KEEP_SCALAR` | `DONE` | Product owned by another service/module. |
| `pos-price` | `CustomerTierPricingRule` | `customerTierId` | `KEEP_SCALAR` | `DONE` | Customer tier owned by another service/module. |
| `pos-inventory` | `CycleCountTask` | `latestCountEntryId` | `DEFER` | `DEFER` | Cyclic persistence/teardown risk; requires explicit lifecycle strategy. |
| `pos-workorder` | `IdempotencyKey` | `workorderId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Workorder workorder` on 2026-03-10. |
| `pos-workorder` | `IdempotencyKey` | `changeRequestId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne ChangeRequest changeRequest` on 2026-03-10. |
| `pos-workorder` | `IdempotencyKey` | `laborEntryId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne WorkorderLaborEntry laborEntry` on 2026-03-10. |
| `pos-workorder` | `Workorder` | `estimateId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Estimate estimate` on 2026-03-10. |
| `pos-workorder` | `EstimateItem` | `estimateId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Estimate estimate` on 2026-03-10. High impact: 1 entity, 4 repo methods, 11 service calls, 15+ test file updates. |

| `pos-location` | `BayEntity` | `locationId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Location` on 2026-03-10. |
| `pos-location` | `StorageLocationEntity` | `siteId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Location site` on 2026-03-10. |
| `pos-location` | `StorageLocationEntity` | `parentStorageLocationId` | `CONVERT_NOW` | `DONE` | Self-ref: converted to `@ManyToOne StorageLocationEntity parentStorageLocation` on 2026-03-10. |
| `pos-location` | `Location` | `defaultStagingLocationId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne StorageLocationEntity defaultStagingLocation` on 2026-03-10. |
| `pos-location` | `Location` | `defaultQuarantineLocationId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne StorageLocationEntity defaultQuarantineLocation` on 2026-03-10. |
| `pos-location` | `MobileUnitEntity` | `baseLocationId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Location baseLocation` (nullable) on 2026-03-10. |
| `pos-shop-manager` | `OverrideRecord` | `appointmentId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Appointment` on 2026-03-10. Entity retired by CAP-326 (`conflict_override` replaces `override_record`). |
| `pos-shop-manager` | `RescheduleHistory` | `appointmentId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Appointment` on 2026-03-10. |
| `pos-shop-manager` | `WorkOrderAppointmentMapping` | `appointmentId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Appointment` on 2026-03-10. |
| `pos-shop-manager` | `AppointmentAudit` | `appointmentId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Appointment` on 2026-03-10. |
| `pos-shop-manager` | `AssignmentMechanic` | `mechanicId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Mechanic` on 2026-03-10. |
| `pos-shop-manager` | `MechanicSkill` | `mechanicId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Mechanic` on 2026-03-10. |
| `pos-people` | `TimeEntryAdjustment` | `timeEntryId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne TimeEntry timeEntry` on 2026-03-10. |
| `pos-people` | `WorkSession` | `personId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Person person` on 2026-03-10. |
| `pos-people` | `WorkSessionBreak` | `sessionId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne WorkSession session` on 2026-03-10. |
| `pos-people` | `UserPersonLink` | `personId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Person person` on 2026-03-10. |
| `pos-people` | `TimekeepingEntry` | `sourceSessionId` | `KEEP_SCALAR` | `DONE` | Not a true FK: correction flow sets to correctionId, used as dedup key per unique constraint (tenant_id, source_system, source_session_id). |
| `pos-people` | `PersonLocationAssignment` | `personId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Person person` on 2026-03-10. |
| `pos-people` | `EmployeeOffboardingRetry` | `employeeId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne Person employee` on 2026-03-10. |
| `pos-vehicle-reference-carapi` | `CarApiModel` | `makeId` | `CONVERT_NOW` | `DONE` | Already had `@ManyToOne CarApiMake make` relationship; scanner false positive from compatibility getter. |
| `pos-vehicle-fitment` | `VehicleVariableValue` | `variableId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne VehicleVariable variable` on 2026-03-10. |
| `pos-vehicle-reference-nhtsa` | `VehicleVariableValue` | `variableId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne VehicleVariable variable` on 2026-03-10. |
| `pos-vehicle-inventory` | `VehicleCarePreference` | `vehicleId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne VehicleRecord vehicle` on 2026-03-10. Non-standard PK: `vehicleId`. |
| `pos-inventory` | `CountEntry` | `recountOfCountEntryId` | `CONVERT_NOW` | `DONE` | Self-ref: converted to `@ManyToOne CountEntry recountOfCountEntry` (nullable) on 2026-03-10. Non-standard PK: `countEntryId`. |
| `pos-accounting` | `PaymentApplication` | `paymentId` | `CONVERT_NOW` | `DONE` | Converted to `@ManyToOne ReceivablePayment payment` on 2026-03-10. Immutable entity. |
| `pos-customer` | `CommunicationPreference` | `partyId` | `DEFER` | `DEFER` | AbstractParty uses TABLE_PER_CLASS inheritance; no shared AbstractPartyRepository; cross-table proxy resolution fragile. |
| `pos-customer` | `PartyNote` | `partyId` | `DEFER` | `DEFER` | Same as CommunicationPreference — AbstractParty TABLE_PER_CLASS inheritance complexity. |
| `pos-customer` | `ContactRoleAssignment` | `contactId` | `DEFER` | `DEFER` | Composite @IdClass (contactId+customerAccountId+roleName); FK-in-composite-key requires @MapsId strategy. |
| `pos-customer` | `PartyAlias` | `sourcePartyId` | `DEFER` | `DEFER` | @Id field in composite key; requires @MapsId strategy. |
| `pos-customer` | `PartyAlias` | `targetPartyId` | `DEFER` | `DEFER` | Same entity with composite @Id; requires @MapsId strategy. |
| `pos-accounting` | `PaymentApplication` | `invoiceId` | `KEEP_SCALAR` | `DONE` | Cross-service: invoice owned by pos-invoice. |
| `pos-accounting` | `PaymentApplication` | `customerId` | `KEEP_SCALAR` | `DONE` | Cross-service: customer owned by pos-customer. |
| `pos-accounting` | `ReceivablePayment` | `customerId` | `KEEP_SCALAR` | `DONE` | Cross-service: customer owned by pos-customer. |

## Next Candidates

All approved `CONVERT_NOW` candidates are complete. Remaining items are `DEFER` or `KEEP_SCALAR`.

### Open DEFER rows

Eleven fields remain deferred. Five of them are in `pos-workorder` and are **not** candidates: they
are event entities (`WorkorderPartAdjustmentEvent`, `WorkorderPartUsageEvent`) or point at one
(`IdempotencyKey.partUsageEventId`, `.partAdjustmentEventId`). An event row is an immutable record
of something that happened; giving it a managed relationship couples its lifetime to the
aggregate's. Treat those as `KEEP_SCALAR` in practice.

The six genuine candidates, each re-verified still scalar on 2026-09-20:

| Module | Entity | Field | Verified at | Why deferred |
|---|---|---|---|---|
| `pos-inventory` | `CycleCountTask` | `latestCountEntryId` | `CycleCountTask.java:107` | Cyclic persistence/teardown risk: `CountEntry` already points back at the task, so a managed both-ways link needs an explicit lifecycle and delete order |
| `pos-customer` | `CommunicationPreference` | `partyId` | `CommunicationPreference.java:80` | `AbstractParty` is `TABLE_PER_CLASS`; there is no shared `AbstractPartyRepository`, and cross-table proxy resolution is fragile |
| `pos-customer` | `PartyNote` | `partyId` | `PartyNote.java:45` | Same `AbstractParty` `TABLE_PER_CLASS` problem |
| `pos-customer` | `ContactRoleAssignment` | `contactId` | `ContactRoleAssignment.java:64` | Composite `@IdClass` (`contactId` + `customerAccountId` + `roleName`); an FK inside a composite key needs `@MapsId` |
| `pos-customer` | `PartyAlias` | `sourcePartyId` | `PartyAlias.java:44` | `@Id` field in a composite key; needs `@MapsId` |
| `pos-customer` | `PartyAlias` | `targetPartyId` | `PartyAlias.java:49` | Same entity, same composite `@Id` constraint |

None has converted since the queue was written. The two blockers are therefore still the real
ones: `TABLE_PER_CLASS` inheritance on `AbstractParty`, and `@MapsId` on composite keys.

## Converting a deferred row

The two-step recipe and the per-entity checklist below are the surviving operative content of the
retired `standalone-id-jpa-relationship-plan.md` and `-autonomous-plan.md`. Everything else in
those files was an execution log of work now recorded as `DONE` in the queue above.

### Guardrails

- **Never create a cross-service JPA relationship.** If the target entity is owned by another
  module, the field stays scalar — that is what `KEEP_SCALAR` means, and no amount of convenience
  overrides it.
- **Keep the existing column name** with `@JoinColumn(name = "…")`. The conversion is a mapping
  change, not a schema change.
- **Prefer `@ManyToOne`.** Use `@OneToOne` only where uniqueness is enforced by the data model,
  not merely expected by the code.

### The two-step dual-mapping migration

Where legacy read/write compatibility is needed, split the change across two commits. Mapping the
same column twice in one step is what produced the `JournalEntryLine` / `JournalEntry` defects the
queue records as "Dual-mapped fix".

- **Step A — add the relationship.** Map it to the *existing* FK column. Keep the old scalar field
  if consumers still need it, but make it read-only/derived so only one of the two mappings is
  the owning side. Two writable mappings on one column is the bug.
- **Step B — remove the scalar.** Once tests and every consumer are updated, delete the deprecated
  scalar field and make the relationship the sole owning mapping.

A single-step conversion is correct and preferable when nothing outside the module reads the
scalar; use the two-step form only when something does.

### Conversion checklist per entity

- [ ] Confirm the target entity is in the **same module** (otherwise keep the scalar id).
- [ ] Add the relationship field with `@ManyToOne`/`@OneToOne` and `@JoinColumn` naming the
      existing column.
- [ ] Decide optional vs nullable, and enforce it with both annotations and DB constraints.
- [ ] Update `equals`/`hashCode`/`toString` so none of them traverses a lazy relationship.
- [ ] Update repositories, specifications, and derived query methods for the navigation path.
- [ ] Update DTOs and serializers to avoid recursive graph serialization.
- [ ] Update tests in the same PR: entity mapping and nullability, mapper round-trips that preserve
      the API contract, service behaviour that previously branched on a scalar id check, repository
      query and fetch-behaviour tests, FK-integrity and delete-behaviour tests, and integration
      flows including FK-safe teardown ordering.
- [ ] Add a query-count check on the hot path to catch an N+1 regression, and a compatibility test
      if a temporary dual mapping is in play.
- [ ] Validate the module build and tests before merge.
