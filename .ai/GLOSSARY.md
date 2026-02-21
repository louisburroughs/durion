# Glossary

## Durion & Platform

- Durion: Operational subsidiary of TIOTF providing technology solutions for tire industry.
- TIOTF: Tire Industry Open Technology Foundation; 501(c)(6) non-profit for open tire industry technology.
- Positivity: The monorepo containing POS-related microservices for Durion/TIOTF initiatives.

## Common Architecture & Integration

- Domain: Logical grouping of related business capabilities (e.g., accounting, inventory, CRM).
- Domain Boundary: Logical separation between different business capabilities.
- Bounded Context: Explicit boundary within which a domain model is defined and applicable.
- Microservice: Independent Spring Boot application with its own data store.
- POS System: Point of Sale system for retail/automotive service operations.
- API Gateway: Edge service that routes, aggregates, and secures requests to backend microservices.
- Discovery (Service Discovery): Mechanism for services to register and discover each other (e.g., registry/consul/eureka-like patterns).
- REST API: Representational State Transfer; architectural style for web service APIs.
- API Contract: Formal specification of request/response formats and behavior for REST endpoints.
- DTO (Data Transfer Object): Schema for requests/responses between services and clients; defines stable API contracts.

## Testing Frameworks

<!-- - Spock Framework: Groovy-based BDD testing framework for Moqui backend services and business logic.
- Jest: JavaScript testing framework for Vue.js frontend components and TypeScript code. -->
- BDD (Behavior-Driven Development): Testing approach using given-when-then specifications.

## Domain-Driven Design (DDD)

- Entity (DDD): Domain object with unique identity and lifecycle.
- Service (DDD): Stateless operation implementing business logic.
- Repository: Data access abstraction for entity persistence.
- Aggregate: Cluster of entities and value objects treated as a single unit for data changes.

## POS / Backend Domain Terms

- Accounting: Financial postings, ledgers, and reconciliation related to POS operations.
- Customer (CRM-lite): Customer records, contacts, preferences, basic CRM operations.
- Product/Catalog: Master data for products, categories, attributes, and availability.
- Inventory (POS): Stock levels and movements for products and vehicle-specific items.
- Pricing: Rules, discounts, and calculations used to price products and services.
- Order Lifecycle: Stages from quote → order → fulfillment → invoicing → settlement.
- Workorder: Job record detailing tasks, parts, labor, and status within shop operations.
- Shop Manager: Module for operational workflows in service shops (appointments, bays, technicians, tasks).
- People Domain: Non-auth user/party domain (profiles, roles, associations) distinct from security service.
- Security Service: Authentication/authorization microservice providing tokens, roles, and policy enforcement.
- Inquiry: Read-only search/exploration endpoints across catalog, orders, customers, etc.
- Image Service: Storage/transformation endpoints for product and document images.
- Location Service: Store/site/location metadata (addresses, hours, capabilities).

## Events & Messaging

- Event Receiver: Ingress component that accepts external events/webhooks and translates them into internal domain events.
- Events Module: Shared event models and publishing/subscription utilities for asynchronous processing.

## Vehicle & Fitment

- Fitment: Compatibility determination for vehicle/tire/wheel assemblies (e.g., size, bolt pattern, offset).
- Vehicle Inventory: Inventory tied to specific vehicles (e.g., used car lot, incoming/outgoing units).
- Vehicle Reference: External reference datasets used to enrich or validate vehicle attributes.
- NHTSA: U.S. National Highway Traffic Safety Administration; provides vehicle reference data.
- CarAPI: Third-party vehicle data provider used for reference and enrichment.

## Context Management Terminology

- Context Integrity: Validation that all required project information is available before providing guidance or making decisions.
- Session Context: Temporary working document maintaining continuity across multi-step development tasks.
- Context Re-anchoring: Process of returning to authoritative project files when context becomes insufficient or contradictory.
- Stop-Phrase: Mandatory interruption mechanism used by pair programming agents to halt problematic implementation patterns.
- Loop Detection: Automated identification of repetitive or stalled implementation progress requiring intervention.
- Architectural Drift: Deviation from established design patterns and domain boundaries during implementation.

## Property-Based Testing Terminology

- Correctness Property: Formal statement about system behavior that should hold true across all valid executions.
- Property-Based Test (PBT): Automated test that validates correctness properties across randomly generated inputs using frameworks like jqwik.
- jqwik: Property-based testing framework for Java used to validate universal properties with configurable iteration counts.
- Domain Coverage Property: Correctness property ensuring all required agent domains are available for guidance requests.
- Guidance Quality Property: Correctness property validating that agent recommendations follow established patterns and best practices.
- Collaboration Consistency Property: Correctness property ensuring multi-agent recommendations are consistent and conflict-free.


## MISC

- tenantId: Deprecated multi-tenant convention; not implemented in the current platform.
- organizationId: Explicit organization-scope identifier when organization scoping is required.
