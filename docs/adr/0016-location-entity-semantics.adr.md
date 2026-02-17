# ADR-0016: Location Entity Semantics and Definitions

**Status:** PROPOSED  
**Date:** 2026-02-17  
**Deciders:** Architecture, Backend Lead, Domain Lead  
**Affected Issues:** N/A

---

## Context

The Durion platform references the concept of "Location" in multiple domains, including inventory, service, logistics, and CRM. The term "Location" is overloaded and used to mean:

- A geographical place (e.g., city, coordinates, postal address)
- A physical site (e.g., building, parcel of land)
- A storage area within a site (e.g., bin, rack, shelf, floor)
- A service area within a site (e.g., bay, lift, work area)
- A mobile asset (e.g., wrecker, service vehicle)

This ambiguity leads to confusion in data modeling, API contracts, and business logic. A clear, system-wide definition and taxonomy is required to ensure consistent handling of all types of locations.

---

## Decision

### 1. Location (Canonical Definition)

A **Location** is any entity that can be referenced as a place for storing, servicing, or tracking assets, people, or activities. The `pos-location` service is the authoritative source for all location data.

### 2. Data Model and Hierarchy

**Decision:** ✅ **Resolved** - A hierarchical data model using a self-referencing adjacency list will be implemented. This provides flexibility and infinite nesting depth.

The core `Location` entity will be structured as follows:

```java
// In pos-location module

/**
 * Represents the type of a location (e.g., "Building", "Service Bay", "Warehouse").
 * Managed as a database entity to allow for dynamic, runtime-configurable types.
 */
@Entity
public class LocationType {
    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    // Standard getters/setters
}

/**
 * Defines the nature of a hierarchical relationship between two locations.
 * For example, a "Service Bay" might have a PHYSICAL parent ("Shop") and
 * an ORGANIZATIONAL parent ("Service Department").
 */
public enum ParentType {
    PHYSICAL,       // Structural containment (e.g., a room inside a building)
    ORGANIZATIONAL, // Logical grouping for reporting/management (e.g., part of a department)
    FINANCIAL,      // Grouping for financial roll-ups
    SHIPPING        // Related to a shipping or logistics route
}

/**
 * A Location is any entity that can be referenced as a place for storing,
 * servicing, or tracking assets, people, or activities.
 */
@Entity
public class Location {
    @Id
    private UUID id; // UUIDv7

    private String name;

    @ManyToOne
    @JoinColumn(name = "location_type_id", nullable = false)
    private LocationType type;

    /**
     * A map of parent relationships, keyed by the type of relationship.
     * This allows a location to have multiple parents, but only one for each ParentType.
     * Example: {PHYSICAL -> warehouseLocation, ORGANIZATIONAL -> serviceDeptLocation}
     */
    @ManyToMany
    @JoinTable(
        name = "location_parent",
        joinColumns = @JoinColumn(name = "child_location_id"),
        inverseJoinColumns = @JoinColumn(name = "parent_location_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKey(name = "parentType") // This assumes a 'parentType' field on the relationship entity if we were using one.
                                 // For a direct Map<ParentType, Location> mapping, JPA needs an ElementCollection.
    @ElementCollection
    @CollectionTable(name = "location_parents", joinColumns = @JoinColumn(name = "location_id"))
    @MapKeyColumn(name = "parent_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "parent_location_id")
    private Map<ParentType, UUID> parents = new HashMap<>();


    private UUID geographicalLocationId; // FK to a GeographicalLocation entity
}
```

- **GeographicalLocation**: This will be a separate entity to hold address and coordinate data, managed within `pos-location`. This decouples the physical address from the operational location.

### 3. Cross-Module Integration

**Decision:** ✅ **Resolved** - Services that need to reference a location will only store the `locationId`. They will query the `pos-location` service for details or hierarchy information.

- **Example**: The `pos-inventory` service, when recording stock, will associate an `InventoryLevel` record with a `locationId`. It will not store the location's name, type, or parent.
- To find all inventory in a building, a client would first query `pos-location` to get all child locations of that building, then query `pos-inventory` with that list of `locationId`s.

### 4. Location Classifications

Locations are classified as follows:

#### a. Geographical Location

- Represents a real-world place defined by coordinates, postal address, or region (e.g., 123 Main St, Springfield, GPS: 40.7128,-74.0060).
- Used for mapping, routing, and regulatory compliance.

#### b. Physical Location

- A tangible site or structure (e.g., building, parcel of land, warehouse).
- May have one or more Geographical Locations (e.g., a building with a street address and GPS coordinates).

#### c. Storage Location

- A sub-area within a Physical Location used for storing items (e.g., bin, rack, shelf, floor, cold room).
- Hierarchical: Storage Locations are always contained within a Physical Location.

#### d. Service Location

- A designated area within a Physical Location where services are performed (e.g., service bay, lift, workbench).
- May overlap with Storage Locations but is primarily for operational activities.

#### e. Mobile Location

- A movable asset that can act as a location (e.g., wrecker, service van, mobile tool cart).
- Has a current Geographical Location and may be assigned to a Physical Location for tracking.

---

## Consequences

### Positive ✅

- ✅ Clear taxonomy for all location types
- ✅ Enables precise modeling and querying of locations
- ✅ Reduces ambiguity in API contracts and business logic
- ✅ Supports hierarchical and mobile location tracking

### Negative ⚠️

- ⚠️ Increased complexity in location management and UI
- ⚠️ Migration required for legacy location data

### Neutral

- Neutral impact on existing API contracts if backward compatibility is maintained

---

## Implementation Notes

- All modules referencing locations must use the canonical taxonomy
- Location entities should support type, parent-child, and reference to physical/geographical data
- Document location usage and mapping in module-level READMEs
- Add integration tests for location hierarchy and type enforcement

---

## References

- [ADR-0009: Backend Domain responsibilities](0009-backend-domain-responsibilities-guide.adr.md)
- Inventory, Service, and CRM module docs

---

## Sign-Off

| Role            | Name | Date       | Notes |
|-----------------|------|------------|-------|
| Architecture    | LMB  | 2026-02-17 |       |
| Backend Lead    | LMB  | 2026-02-17 |       |
| Domain Lead     | LMB  | 2026-02-17 |       |

---

## Timeline

- **Proposed**: 2026-02-17

---

## Changelog

- **2026-02-17**: Initial draft
