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

## Moqui Core Terminology (Frontend Platform)

- Moqui Framework: Enterprise application framework for building database-backed business applications with XML entity/service definitions and Groovy scripting.
- Entity (Moqui): Data model definition in Moqui (XML); represents database tables and relationships.
- Service (Moqui): Business logic definition in Moqui (XML); can be implemented in Groovy, Java, or script.
- Screen: UI definition in Moqui (XML); renders web pages using FreeMarker templates and Vue.js components.
- Component (Moqui): Modular Moqui application package containing entities, services, screens, and resources.
- Runtime: Directory containing Moqui components, configuration, database, and runtime data.
- mantle-udm: Universal Data Model; core entity definitions for parties, products, orders, accounting.
- mantle-usl: Universal Service Library; reusable service patterns and business logic.
- FreeMarker: Template engine used by Moqui for server-side rendering (.ftl files).
- Groovy: JVM scripting language used for Moqui service implementations and business logic.
- Entity Facade: Moqui API for database operations; provides abstraction over JDBC.
- Service Facade: Moqui API for invoking services; handles transactions, authorization, and execution.
- Screen Facade: Moqui API for rendering screens and managing UI state.

## Durion Application Components (Moqui)

- durion-accounting: Durion component for financial accounting, GL, AR/AP.
- durion-common: Shared UI components, styles, utilities, and libraries for Durion.
- durion-crm: Customer relationship management component for Durion.
- durion-demo-data: Sample/demo datasets for Durion components.
- durion-experience: Frontend experience layer (themes, layouts, navigation).
- durion-inventory: Inventory management component (stock, movements, locations).
- durion-product: Product master data and catalog management.
- durion-theme: Theme assets (CSS, images, fonts) and branding configuration.
- durion-workexec: Work execution and shop operations management.
- durion-positivity: Moqui component providing integration layer for connecting Durion frontend to the Positivity backend services.
- durion-positivity-backend: Separate Spring Boot microservices backend repository providing business logic and data persistence (Java 21).
- durion-positivity Integration: Component bridging Durion frontend with Positivity services via REST.
- Positivity Backend: Java 21 Spring Boot microservices providing backend business logic.

## Standard Moqui Components

- PopCommerce: E-commerce component (orders, products, pricing, customers).
- HiveMind: Project management and collaboration component (tasks, wikis, time tracking).
- MarbleERP: Manufacturing and MRP component (production, BOM, work centers).
- SimpleScreens: Reusable dashboard and UI component library.
- moqui-fop: Apache FOP integration for PDF/document rendering.

## Frontend Technology

- Vue.js 3: Progressive JavaScript framework for building user interfaces.
- Composition API: Vue.js 3 API style for organizing component logic with setup(), ref(), reactive(), and composables.
- Quasar v2: Vue.js 3 component framework with Material Design for responsive web/mobile applications.
- TypeScript: Typed superset of JavaScript for type-safe frontend development.
- Single File Component (SFC): Vue.js component format (.vue files) containing template, script, and style.

## Testing Frameworks

- Spock Framework: Groovy-based BDD testing framework for Moqui backend services and business logic.
- Jest: JavaScript testing framework for Vue.js frontend components and TypeScript code.
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

## Agent Framework Terminology

- Agent: A specialized AI assistant with domain-specific expertise.
- Agent Response: Guidance provided by agents to developers.
- Agent Framework: Infrastructure and libraries enabling automation agents to run tasks, integrate with services, and orchestrate workflows.
- Agent Structure System: Framework of specialized AI agents for Positivity POS development.

### Agent Roles

- Architecture Agent: Core agent providing system-wide architectural guidance and design patterns for microservice boundaries and integration.
- Implementation Agent: Core agent specializing in Spring Boot microservice development, business logic, and data access patterns.
- Deployment Agent: Core agent focused on Docker containerization, AWS Fargate deployment, and CI/CD pipeline design.
- Testing Agent: Core agent providing comprehensive testing strategies including unit, integration, and contract testing.
- Architectural Governance Agent: Specialized agent enforcing domain-driven design principles, preventing circular dependencies, and managing technical debt.
- Integration & Gateway Agent: Specialized agent for API Gateway integration, REST API design, OpenAPI specifications, and external service patterns.
- Security Agent: Specialized agent ensuring comprehensive security across microservices including JWT authentication, OWASP compliance, and secrets management.
- Observability Agent: Specialized agent for OpenTelemetry instrumentation, Grafana dashboards, Prometheus metrics, and distributed tracing.
- Documentation Agent: Specialized agent for technical documentation standards, API documentation, and documentation synchronization with code.
- Business Domain Agent: Specialized agent providing POS-specific domain knowledge, payment processor integration, and business workflow patterns.
- Pair Programming Navigator Agent: Specialized agent for real-time collaboration, implementation loop detection, and mandatory stop-phrase interruption.

## Context Management Terminology

- Context Integrity: Validation that all required project information is available before providing guidance or making decisions.
- Session Context: Temporary working document (`.ai/session.md`) maintaining continuity across multi-step development tasks.
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
