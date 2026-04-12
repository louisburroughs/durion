# 0014 - Internal Service Security via Gateway Route Control

## Context

The Durion Positivity Backend uses a microservices architecture where some services are intended for public access (e.g., `pos-order`, `pos-catalog`) while others are strictly internal (e.g., `pos-tax`, `pos-events`).

The default behavior of Spring Cloud Gateway with service discovery (Eureka) integration is to automatically create routes for all registered services (`discovery.locator.enabled: true`). This presents a security risk where internal services could be inadvertently exposed to the internet without specific route configuration, potentially bypassing security controls or exposing internal logic.

We needed a mechanism to ensure that internal services are "secure by default" and cannot be accessed through the gateway unless explicitly authorized.

## Decision

We have decided to enforce a "whitelist only" routing strategy for the API Gateway:

1. **Disable Discovery Locator**: We will set `spring.cloud.gateway.discovery.locator.enabled: false` in the API Gateway configuration. This disables the automatic creation of routes based on Eureka service registration.

2. **Explicit Routing Only**: All public-facing routes must be explicitly defined in the API Gateway's `application.yml`. If a service is not listed in the routes configuration, it is inaccessible from the outside.

3. **Internal Service Classification**:
    * **Internal-Only Services** (e.g., `pos-tax`, `pos-events`) **MUST NOT** have routes defined in the gateway.
    * These services should default to `eureka.client.register-with-eureka: false` when used as libraries or sidecars, or use specific metadata if they must register.

4. **Deployment Guidelines**:
    * Internal services should be deployed in private subnets where possible.
    * Access to internal services should occur via:
        * Direct dependency injection (for shared libraries like `pos-tax` in library mode).
        * Internal REST calls using the sidecar/service mesh or direct container-to-container networking.
        * Asynchronous event messaging.

## Consequences

### Positive

* **Security by Default**: New services are not exposed freely. An engineer must consciously add a route to expose a service.
* **Granular Control**: We have full control over the path predicates and filters for every exposed route.
* **Reduced Attack Surface**: Internal utilities and helper services are completely hidden from the external network.

### Negative

* **Configuration Overhead**: Every new public service or endpoint group requires a configuration change in the API Gateway `application.yml` and a redeployment of the gateway.
* **Discovery Complexity**: Developers cannot simply spin up a service and reach it via the gateway ID without configuration.

## Implementation Details

### Gateway Configuration (`pos-api-gateway`)

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false # Explicitly disable dynamic routing
      # routes: ... (only public services listed here)
```

### Affected Services

#### `pos-tax`

* **Status**: Internal Only
* **Configuration**: `register-with-eureka: false` (default)
* **Access**: Library usage or internal REST.

#### `pos-events`

* **Status**: Internal Only
* **Access**: ApplicationEventPublisher (internal).

## References

* [Spring Cloud Gateway Documentation](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
