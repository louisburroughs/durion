# ADR-0009: Backend Domain Responsibilities Guide

**Status:** ACCEPTED  
**Date:** 2026-01-29  
**Deciders:** Backend Architecture Team, Platform Lead  
**Affected Issues:** Modularization initiative, ArchUnit architecture enforcement

---

## Context

The durion-positivity-backend project has grown to include multiple Spring Boot microservices (pos-accounting, pos-inventory, pos-people, pos-workorder, pos-customer, etc.). Each service handles distinct business domains with responsibilities that need clear definition to:

- Prevent overlap and confusion between services
- Establish clear API contracts and boundaries
- Facilitate independent deployment and scaling
- Enable teams to work in parallel with minimal coordination
- Enforce architectural boundaries using ArchUnit compile-time verification

**Current State**: Services exist but domain boundaries are implicit, leading to potential misalignment and integration issues. ArchUnit testing framework now validates architecture at compile time.

**The Problem**: Without explicit responsibility definitions, developers may incorrectly place business logic, create circular dependencies, or integrate services in unintended ways.

**Drivers**:

- Growing complexity of the system
- Need for independent scalability per domain
- Compile-time architecture validation via ArchUnit
- Clear documentation for onboarding new team members

---

## Decision

Establish and document explicit domain responsibilities for each pos-* module in durion-positivity-backend, with compile-time architecture enforcement via ArchUnit to validate modularity and integration patterns.

### 1. Domain Responsibility Matrix

**Decision:** ✅ **Resolved** - Define clear ownership boundaries for each domain with documented APIs, internal services, and integration points.

| Module | Domain | Primary Responsibility | Key Entities | Authoritative For | Integrates With |
| -------- | -------- | ---------------------- | -------------- | ------------------- | ----------------- |
| pos-accounting | Accounting | Financial transaction tracking, audit trails, GL posting, invoice management | Transaction, JournalEntry, GLAccount, AuditTrail | Price overrides, refunds, cost tracking | pos-order, pos-inventory |
| pos-invoice | Invoice Management | Invoice status tracking, invoice generation from workorders and orders, dispute management, invoice corrections | Invoice, InvoiceStatus, InvoiceDispute, InvoiceCorrection | Invoice tracking, dispute resolution, correction handling | pos-workorder, pos-order, pos-accounting |
| pos-catalog | Catalog | Product catalog maintenance, SKU management, product attributes, category organization | Product, SKU, Category, ProductAttribute | Product information, catalog structure | pos-workorder, pos-inventory, pos-order, pos-pricing |
| pos-inventory | Inventory | Stock levels, location management, cycle counts, availability calculation | InventoryLevel, Location, CycleCount, AdjustmentRequest | ATP calculations, stock allocations, cycle counts | pos-order, pos-workorder |
| pos-location | Location Management | Location hierarchy management, headquarters to shop bays to mobile locations, location metadata and configuration | Location, LocationHierarchy, Bay, MobileLocation | Location data, location hierarchy, location configuration | pos-inventory, pos-workorder, pos-people |
| pos-people | Human Resources | Employee records, time entries, work sessions, exceptions, payroll | Person, TimeEntry, WorkSession, TimeEntryException | Employee data, time tracking, attendance | pos-workorder |
| pos-workorder | Workorder | Task/workorder lifecycle, job assignments, status tracking, job costing | Workorder, JobTask, Estimate, WorkorderStatus | Job scheduling, assignments, execution | pos-people, pos-inventory, pos-accounting |
| pos-shop-manager | Shop Operations | Shop floor operations management, mechanic scheduling, work assignment, shop capacity planning, resource allocation | Shop, ShopSchedule, MechanicAssignment, ShopCapacity, WorkAssignment | Mechanic schedules, shop capacity, work assignments, shop resource allocation | pos-workorder, pos-people, pos-location |
| pos-customer | Customer Relations | Customer data, contact info, preferences, relationship tracking, customer to vehicle relationships | Customer, Contact, CustomerPreferences | Customer identity, relationship data | pos-order, pos-workorder, pos-vehicle-inventory |
| pos-vehicle-inventory | Vehicle Management | Master vehicle reference, vehicle registration and tracking for organization fleet, service customers, and purchasing customers | Vehicle, VehicleRegistration, VIN, VehicleOwnership, VehicleMaintenance | Vehicle data, vehicle registration, vehicle ownership tracking | pos-customer, pos-workorder, pos-order |
| pos-vehicle-fitment | Vehicle Fitment & Parts Compatibility | Mapping vehicle year/make/model to compatible parts and services, external manufacturer/supplier API integration for fitment data | VehicleFitment, PartCompatibility, VehicleApplication, ManufacturerAPI, SupplierCatalog | Vehicle-to-part mappings, fitment data, manufacturer/supplier integration | pos-vehicle-inventory, pos-catalog, pos-inquiry, pos-order |
| pos-vehicle-reference-* | Vehicle Reference Data Integration | External vehicle research site integration for historical and reference vehicle data lookup by VIN or license plate, provider-specific adapters | VehicleReferenceData, VINLookup, LicensePlateLookup, VehicleHistory, ProviderAdapter | Historical vehicle data, VIN decoding, license plate lookup, vehicle reference integration | pos-vehicle-inventory, pos-vehicle-fitment, pos-customer |
| pos-inquiry | Vendor Integration | Vendor API catalog, vendor connectivity, replenishment inquiry processing, shop-order inquiry management | VendorAPI, VendorOrder, Inquiry, VendorConnection | Vendor orders, inquiry data, replenishment coordination | pos-inventory, pos-order |
| pos-order | Order Management | Order creation, processing, fulfillment, shipping (if implemented) | Order, OrderItem, OrderStatus, Shipment | Order lifecycle, item tracking | All other services |
| pos-image | Image Management | Image storage, serving, optimization, and caching for efficient performance | Image, ImageMetadata, ImageVariant | Image serving, image storage, image optimization | Frontend components, pos-catalog |
| pos-events | Event Publishing | Event annotation classes, event schema definition, event emission utilities | DomainEvent, EventPublisher, EventAnnotations | Event schema, event routing configuration | All other services |
| pos-event-receiver | Event Receiving | Event subscription, event handler routing, event consumption and processing | EventSubscription, EventHandler, EventProcessor | Event handling, async message consumption | All other services |
| pos-mcp-server | AI Integration | Model Context Protocol server interfacing LLM APIs with backend services, tool orchestration, configurable LLM provider integration | MCPTool, LLMRequest, ToolInvocation, LLMConfig | LLM-to-service orchestration, AI-driven workflows | All backend services (as tools) |
| pos-security-service | Security | User and role management, authentication token generation and validation, permission enforcement | User, Role, Permission, AuthToken | User identity, authentication, authorization decisions | pos-api-gateway, all other services |
| pos-api-gateway | API Gateway | Request routing, authentication, rate limiting, service discovery coordination | RouteConfig, AuthToken | API access, security, service routing | All backend services |

### 2. ArchUnit Architecture Enforcement

**Decision:** ✅ **Resolved** - Use ArchUnit to enforce architecture boundaries and validate module isolation at compile time.

**Implementation Approach:**

- Each pos-* service has `ArchitectureTest.java` with 7 configurable architecture rules
- Internal packages (`com.positivity.{domain}.internal.*`) strictly isolated from other modules
- Service layer packages (`com.positivity.{domain}.service.*`) exposed as public API
- Controllers must access repositories through service layer only
- Entities must not be used directly by controllers (DTOs required)
- Repositories accessible only from service/config layers
- @SpringBootApplication class must be at root package for component scanning

**Benefits:**

- Compile-time architecture validation (fails build on violations)
- Prevents tight coupling between modules
- Enforces layering: Controller → Service → Repository → Entity
- Automatic cycle detection
- Clear visibility of public vs. internal APIs
- Test failures prevent deployment of architecture violations

### 3. Cross-Domain Integration Patterns

**Decision:** ✅ **Resolved** - Use async event-driven patterns for cross-domain communication where possible; synchronous REST calls only for critical read operations or immediate acknowledgments.

**Preferred Patterns:**

- **Async Events** (Primary): Use pos-events domain to publish domain events (OrderCreated, InventoryAdjusted, etc.)
  - Decouples services
  - Enables independent scaling
  - Supports eventual consistency
  
- **Synchronous REST** (Secondary): Use for:
  - Critical inventory availability checks (before order confirmation)
  - Immediate authorization decisions
  - Data lookups where consistency is required
  
- **Service-to-Service Authentication**: Use X-Service-Id headers or OAuth2 tokens (coordinated via API Gateway)

### 4. Data Ownership Rules

**Decision:** ✅ **Resolved** - Each domain owns its primary entities; read-only replicas or denormalization permitted only with documented justification.

**Rules:**

- **Authoritative Owner**: Only the domain owning an entity can create, update, or delete it
- **Cross-Domain Reads**: Other domains may query via REST endpoints or event-driven projections
- **Denormalization**: Cache frequently-accessed data from other domains locally if latency is critical (documented in METRICS.md)
- **No Direct Database Access**: Services communicate via APIs or events, never via direct DB queries across domains

### 5. API Contract Documentation

**Decision:** ✅ **Resolved** - Every public REST API must be documented with OpenAPI/Swagger annotations; internal services documented with JavaDoc.

**Enforcement:**

- All controllers must have `@Tag`, `@Operation`, `@ApiResponse` annotations
- Global tags defined in `OpenApiConfig` to satisfy lint validation
- OpenAPI generation via Maven `openapi` profile for each service
- Backward compatibility maintained: deprecated endpoints marked with `@Deprecated` and removed after one release cycle

---

## Alternatives Considered

1. **Monolithic Database**: Single shared database for all services
   - **Rejected**: Creates tight coupling, difficult to scale independently, distributed transaction complexity

2. **Database Per Instance**: Each service instance has its own database copy
   - **Rejected**: Data consistency challenges, synchronization complexity, hard to debug

3. **Event Sourcing Everywhere**: Use event sourcing for all state changes
   - **Rejected**: Overkill for read-heavy operations, adds complexity; deferred to specific high-value domains

4. **Synchronous-Only Communication**: All cross-domain calls via REST
   - **Rejected**: Poor scalability, tight coupling, cascading failures

---

## Consequences

### Positive ✅

- **Clear Ownership**: Each team knows exactly what they are responsible for
- **Independent Scaling**: Services can be scaled based on actual demand patterns
- **Reduced Coupling**: Async events prevent tight dependencies
- **Easier Testing**: Module boundaries enable unit and integration testing without mocking entire services
- **Compile-Time Safety**: Architecture violations caught before code is committed
- **Onboarding**: New developers have explicit documentation of domain boundaries via ArchUnit rules
- **Performance**: Event-driven patterns allow for batch processing, backpressure handling, and eventual consistency

### Negative ⚠️

- **Operational Complexity**: Multiple services to deploy, monitor, and debug
  - *Mitigation*: Docker Compose for local development, centralized logging and tracing via OTLP
  
- **Eventual Consistency**: Cross-domain operations are not immediately consistent
  - *Mitigation*: Document eventual consistency windows; UI polls for updates; events include correlation IDs for tracing
  
- **Distributed Transaction Challenges**: Saga patterns needed for multi-domain workflows
  - *Mitigation*: Implement compensating transactions; use idempotency keys to handle retries
  
- **API Changes Break Clients**: Incompatible API changes require coordinated deployment
  - *Mitigation*: Use API versioning; maintain backward compatibility for one release; deprecate early
  
- **Event Latency**: Async events may be delayed due to queue processing
  - *Mitigation*: Critical operations use synchronous calls; non-critical updates via events; monitor event lag

### Neutral

- Documentation burden for domain boundaries (offset by reduced future confusion)
- Initial Spring Modulith setup effort (one-time investment with long-term payoff)

---

## 6. Location Terminology & Hierarchy

**Decision:** ✅ **Resolved** - Define a unified location taxonomy for use across all services (pos-location, pos-inventory, pos-workorder, etc.) to prevent ambiguity and enable consistent API contracts.

### Location Type Hierarchy

The term **"Location"** in Durion refers to any managed physical or logical space. The pos-location module is the authoritative source for all location data and must define a hierarchical taxonomy:

| Location Type | Definition | Industry Standard | Use Cases | Entity Level |
| --- | --- | --- | --- | --- |
| **Organization / Headquarters** | Top-level administrative entity representing the entire company or corporate headquarters | Organization (ISO 20022) | Reporting, financial rollup, policy enforcement | Level 0 (Root) |
| **Facility / Site** | A geographic physical facility (building, complex, or campus location) | Site (OAGI/ACES) | Address registration, regulatory compliance, primary operating unit | Level 1 |
| **Department / Functional Area** | Named area or department within a facility (e.g., Service Department, Retail Floor, Warehouse) | Functional Area | Operational segregation, cost center allocation, work authorization | Level 2 |
| **Storage Location / Bin** | Specific inventory storage space, shelf, bin, pallet position, or demarcated storage area | Storage Bin (OAGI) | Inventory tracking, ATP calculation, pick/pack operations | Level 3+ (Leaf) |
| **Service Bay / Work Area** | Specific work space within a facility for service, repair, or specialized work (e.g., "Bay 1", "Tire Service", "Detail Room") | Work Center / Shop Floor Location | Workorder assignment, job costing, capacity planning | Level 3+ (Leaf) |
| **Mobile Unit / Route Location** | Mobile service unit, technician vehicle, or roaming service location | Mobile Asset Location | Field service routing, technician dispatch, mobile inventory | Special (Non-Hierarchical) |
| **Mailing Address** | Physical or postal address for mail delivery, billing, or correspondence | Address (UPU, ISO 3166) | Billing, shipping, customer communication | Metadata (Attached to Facility or Organization) |

### Location Hierarchy Rules

**Hierarchical Constraints:**

- Locations form a tree structure: Organization → Facility → Department → Storage/Bay/Work Areas
- Mobile Units at a special "Mobile" root node
- Mailing Addresses are metadata attributes attached to a Location (Except when no physical location exists i.e:PO Box)
- Each location (except root) has exactly one parent. Enable a second free-form relationship structure.
- Locations at the same level must have unique names within their parent

**Examples:**

```
Acme Corp (Organization)
├── NYC Headquarters (Facility)
│   ├── Service Department (Department)
│   │   ├── Bay 1 (Service Bay)
│   │   ├── Bay 2 (Service Bay)
│   │   └── Waiting Area (Functional Area)
│   └── Warehouse (Department)
│       ├── Shelf A (Storage Location)
│       ├── Shelf B (Storage Location)
│       └── Receiving Area (Functional Area)
├── Los Angeles Service Center (Facility)
│   └── Field Operations (Department)
│       └── Route 1 Mobile Units (Mobile Unit)
├── PO Box 1234 (Virtual Location - No Physical Facility)
│   └── [Mailing Address] (Metadata: PO Box 1234, Denver, CO 80202)
└── [Mailing Address] (Metadata - HQ: 123 Main St, NYC, NY 10001)
```

### API & Data Model Implications

**pos-location Service Responsibilities:**

- Maintain the complete location hierarchy
- Provide CRUD operations for all location types
- Enforce hierarchy constraints and validate parent-child relationships
- Track location metadata (address, capacity, operational hours, etc.)

**Cross-Service Location References:**

- **pos-inventory**: References locations for stock storage (Storage Locations / Bins)
- **pos-workorder**: References locations for job assignment and service bays
- **pos-people**: References locations for employee assignments and time entry tracking
- All services query pos-location via REST API; no direct DB access

**Data Structure (Proposed):**

```java
Location {
  id: UUID,
  parentId: UUID (nullable for root),
  name: String,
  type: LocationType (enum: ORGANIZATION, FACILITY, DEPARTMENT, STORAGE_BIN, SERVICE_BAY, MOBILE_UNIT),
  hierarchy_path: String (e.g., "/Acme Corp/NYC Headquarters/Service Department/Bay 1"),
  capacity: Integer (optional, for storage locations),
  mailing_address: Address (optional),
  metadata: Map<String, Object> (operational hours, contact info, etc.),
  active: Boolean,
  created_at: Instant,
  updated_at: Instant
}
```

### Clarifying Questions for Stakeholders

Before finalizing this taxonomy, confirm the following:

1. **Mobile Units**: Should mobile units be treated as a separate hierarchy or nodes within the main tree?
2. **Mailing Address Cardinality**: Can a location have multiple mailing addresses (e.g., billing vs. shipping address)?
3. **Hierarchy Depth**: Are there practical limits on nesting depth, or can departments have sub-departments?
4. **Location Renaming**: When a location is renamed, should dependent references (workorders, inventory) automatically track the change?
5. **Deprecation**: How should deprecated or closed locations be handled (soft delete, archival, etc.)?

---

## 6.1 Multiple Hierarchy Types: Management vs. Accounting vs. Reporting

**Decision:** ✅ **Resolved** - Use **Option D (Metadata + Cost Allocation Model)** to support multiple hierarchy types without creating graph complexity.

### The Problem

Real-world operations often require different organizational hierarchies:

- **Management Hierarchy**: Operational supervisory structure (who manages the location)
- **Accounting Hierarchy**: Cost center rollup for P&L and financial reporting
- **Reporting Hierarchy**: Performance metrics and KPI aggregation

**Common Scenarios:**

- A service bay operationally reports to the Service Manager but its costs roll up to a different profit center
- A shared warehouse is managed by Facility Ops but costs are split across multiple departments  
- A mobile technician is assigned to Field Operations (management) but hours are billed to multiple service lines (accounting)

### Options Considered

| Option | Approach | Pros | Cons | Complexity |
|--------|----------|------|------|-----------|
| **A: Metadata-Based** | Single hierarchy + `costCenterId`, `reportingParentId` as fields | Simple schema, avoids graph complexity | Limited to 1:1 relationships; inflexible | Low |
| **B: Multi-Parent Graph** | Allow multiple parents tagged by type (MANAGEMENT, ACCOUNTING, REPORTING) | Most flexible; supports any relationship | Graph queries complex; risk of cycles; difficult to enforce constraints | High |
| **C: Separate Models** | Maintain location hierarchy + separate CostCenterHierarchy + ReportingHierarchy | Clean separation of concerns | Multiple trees to maintain; sync issues; data duplication | Medium-High |
| **D: Metadata + Cost Allocation** | Single location hierarchy + CostAllocation table for many-to-many mappings | Supports many-to-many relationships; clean tree; easy to audit; retroactive changes possible | Requires join logic for rollup queries; additional table | Medium |

### Selected Approach: Option D

**Decision:** ✅ **Resolved** - Maintain a single operational location hierarchy (tree structure); support management, accounting, and reporting concerns through supplementary models.

**Implementation:**

**1. Single Location Hierarchy** (unchanged from above)

- Primary parent-child tree: Organization → Facility → Department → Storage/Bay
- Represents **operational/management hierarchy** by default
- Enforced as a strict tree (no cycles)

**2. Cost Allocation Model** (new)

- Maps locations to cost centers for accounting rollup
- Supports many-to-many relationships (one location may split costs across multiple cost centers)
- Tracks allocation percentage/amount for split scenarios

**3. Reporting Hierarchy** (optional, deferred)

- If needed, can be modeled similarly as a separate hierarchy with its own parent-child relationships
- Initially defer; address only if operational hierarchy doesn't align with reporting needs

### Data Models

**CostAllocation (for accounting hierarchy):**

```java
CostAllocation {
  id: UUID,
  locationId: UUID (reference to Location),
  costCenterId: UUID (reference to CostCenter in pos-accounting),
  allocation_percentage: BigDecimal (0-100, sum across all allocations = 100),
  start_date: LocalDate,
  end_date: LocalDate (nullable for ongoing),
  effective_from: Instant,
  created_by: String,
  notes: String (e.g., "Shared warehouse: 40% Service, 30% Retail, 30% Admin")
}
```

**Example Cost Split:**

```
Warehouse (Location)
├── CostAllocation to Service Department Cost Center: 40%
├── CostAllocation to Retail Department Cost Center: 30%
└── CostAllocation to Admin Overhead Cost Center: 30%
```

### Query Patterns

**Get operational parent (management hierarchy):**

```sql
SELECT parent FROM Location WHERE id = ?
```

**Get cost center allocation (accounting hierarchy):**

```sql
SELECT ca.cost_center_id, ca.allocation_percentage 
FROM CostAllocation ca 
WHERE ca.location_id = ? AND ca.start_date <= TODAY() AND (ca.end_date IS NULL OR ca.end_date > TODAY())
```

**Rollup costs by location (accounting hierarchy):**

```sql
SELECT l.hierarchy_path, SUM(expense * ca.allocation_percentage) as allocated_cost
FROM Location l
  JOIN CostAllocation ca ON l.id = ca.location_id
  JOIN Expense e ON e.location_id = l.id
WHERE ca.cost_center_id = ?
GROUP BY l.hierarchy_path
```

### Advantages

✅ **Maintains clean tree structure** for operational hierarchy (no graph complexity)  
✅ **Supports many-to-many cost allocations** (one location → multiple cost centers)  
✅ **Audit trail** available via CostAllocation history  
✅ **Retroactive changes** possible (update allocation percentages with effective dates)  
✅ **Extensible** to reporting hierarchy if needed without major refactoring  
✅ **Performance** queries are straightforward (tree join + allocation lookup)  

### Disadvantages

⚠️ **Additional table** for cost allocation management  
⚠️ **Join logic** required for cost rollup queries (vs. single hierarchy traversal)  
⚠️ **Validation needed** to ensure allocation percentages sum to 100% per location per time period  

### Service Responsibilities

**pos-location** (extended):

- Manage location hierarchy (tree structure)
- Provide CRUD for CostAllocation records
- Validate cost allocation constraints (percentages, date ranges, effective periods)

**pos-accounting** (cross-domain integration):

- Define and manage cost centers
- Query cost allocations via pos-location API
- Use CostAllocation data for GL posting and expense rollup
- Accept cost allocation changes and recompute historical allocations if needed (via events)

---

## Implementation Roadmap

### Phase 1: Documentation & Validation (Current)

- Document domain responsibilities in this ADR
- Update each service's README with domain focus
- Reference durion/domains/{domain}/.business-rules for domain-specific business rules

### Phase 2: API Documentation (In Progress)

- Add OpenAPI annotations to all public REST controllers
- Generate OpenAPI specs for each service
- Publish API docs via Swagger UI on each service

### Phase 3: Archunit Integration Tesing (In Progress)

- Create integration tests using `ArchUnit`
- Validate cross-module event flows
- Document contract tests

---

## References

- **ArchUnit Documentation**: <https://www.archunit.org/>
- **OWASP API Security**: <https://owasp.org/www-project-api-security/>
- **Domain-Driven Design**: Eric Evans, "Domain-Driven Design" (DDD principles applied here)
- **Saga Pattern**: Chris Richardson, Microservices Patterns — "Managing Data Consistency"
- **Related ADRs**:
  - [ADR-0001: Inventory Ledger ATP Computation](0001-inventory-atp-computation.adr.md)
  - [ADR-0008: Cost Maintenance Clarification Diagram](0008-cost-maintenance-clarification-diagram.adr.md)
- **Related Documentation**:
  - `/durion/docs/` — Architecture, governance, and design docs
  - `/durion/domains/` — Domain-specific business rules (by domain)
  - AGENTS.md files in each service repo for developer guidance
