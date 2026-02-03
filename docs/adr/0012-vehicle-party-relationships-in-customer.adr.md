# ADR 0012: Vehicle-Party Relationships Belong in pos-customer

**Date**: 2026-02-03  
**Status**: Accepted  
**Context**: CAP:091 Vehicle Registry Implementation  
**Decision Makers**: Architecture Team

## Context

During implementation of CAP:091 (Vehicle Registry), a decision arose about where to place vehicle-party association logic:
- **Vehicle-Party Association** entities represent relationships between vehicles and parties (customers, drivers, owners)
- **pos-vehicle-inventory** module owns vehicle records (VIN, mileage, specs)
- **pos-customer** module owns party (person/organization) data and their relationships

The question: Should vehicle-party associations live in pos-vehicle-inventory or pos-customer?

## Decision

**Vehicle-party relationships MUST reside in pos-customer, not pos-vehicle-inventory.**

### Rationale

1. **Bounded Context Alignment**: pos-customer is the authoritative source for party data and their relationships to other domains. Adding party relationships to pos-customer keeps the bounded context coherent.

2. **Single Responsibility**: 
   - pos-vehicle-inventory: manages vehicle inventory (CRUD, VIN validation, care preferences)
   - pos-customer: manages customer master data and their associations (to orders, to vehicles, to locations, etc.)

3. **Dependency Direction**: pos-vehicle-inventory should NOT depend on pos-customer relationships; instead, pos-customer can reference vehicle IDs for relationships.

4. **Cross-Cutting Queries**: Queries like "list all drivers for a customer" or "list all vehicles owned by a party" naturally fit pos-customer's domain, not vehicle inventory.

5. **Event Sourcing**: Party-related events (owner changed, driver assigned) should originate from pos-customer, not pos-vehicle-inventory.

## Implications

### For CAP:091 Story #104 (Associations)

The `VehiclePartyAssociation` entity and associated service/controller currently in pos-vehicle-inventory should be **moved to pos-customer** and refactored to:

```
pos-customer/
├── service/
│   └── VehicleAssociationService.java (moved from pos-vehicle-inventory)
└── internal/
    ├── controller/
    │   └── VehicleAssociationController.java (moved to pos-customer)
    ├── entity/
    │   ├── VehiclePartyAssociation.java (moved)
    │   └── AssociationType.java (enum, moved)
    └── repository/
        └── VehiclePartyAssociationRepository.java (moved)
```

### REST Endpoint Change

**Current** (incorrect):
```
PUT /v1/vehicles/{vehicleId}/associations/owner
```

**Correct**:
```
PUT /v1/parties/{partyId}/vehicles/{vehicleId}/associations/owner
PUT /v1/customers/{customerId}/vehicles/{vehicleId}/associations
```

Or expose via a customer's vehicle associations resource.

### API Contract

pos-customer exposes party-vehicle associations as a public API that pos-vehicle-inventory can reference but does not directly depend on.

## Consequences

### Positive

✅ Clear separation of concerns  
✅ pos-customer as authoritative source for relationships  
✅ Easier to query party-centric association views  
✅ Events flow naturally from pos-customer  
✅ No circular dependencies between modules

### Negative

⚠️ pos-vehicle-inventory loses direct association query capability (must call pos-customer service via gateway)  
⚠️ Refactoring required: move Story #104 code from pos-vehicle-inventory to pos-customer  
⚠️ API contract change for Story #104 endpoints

## Alternatives Considered

1. **Keep in pos-vehicle-inventory**: Violates SRP, creates ambiguous ownership.
2. **Duplicate in both modules**: Increases coupling, schema duplication.
3. **Create separate pos-associations module**: Over-engineering for current scope.

**Decision**: Move to pos-customer ✅

## Related ADRs

- ADR 0010: Frontend Domain Responsibilities Guide (defines bounded contexts)
- ADR 0011: Microservice Boundaries and API Gateways

## Next Steps

1. Move VehiclePartyAssociation and related classes to pos-customer
2. Update Story #104 endpoints to customer resource namespace
3. Remove association classes from pos-vehicle-inventory
4. Update API Gateway routing
5. Update CAP:091 implementation guide

---

**Version**: 1.0  
**Last Updated**: 2026-02-03
