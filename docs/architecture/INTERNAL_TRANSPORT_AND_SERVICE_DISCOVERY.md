---
type: Architecture
title: Internal Transport And Service Discovery
description: Governs how a backend service reaches another backend service — the Eureka service-id registry, the gateway route table, the six client categories, and the closed register of clients permitted to bypass discovery.
status: Current
tags: [service-discovery, eureka, api-gateway, restclient, transport, adr-0014, adr-0021, adr-0044]
---

Internal service-to-service traffic in `durion-positivity-backend` resolves through Eureka and Spring Cloud LoadBalancer against a service's `spring.application.name`, and every client that does not is listed in the exception register in §2 of this document.

## Scope And Ownership

This document is the canonical description of backend internal transport: which service ids exist, which of them the gateway exposes, how an internal client is built, and which clients are permitted to bypass discovery.

It governs runtime HTTP between `pos-*` modules. It does not govern:

- **Cross-domain coupling policy.** Whether a domain may call another domain at all is [ADR-0044](../adr/0044-platform-event-only-domain-walls.adr.md), enforced by `pos-archunit`'s `DomainWallsTest`. This document governs transport for calls ADR-0044 already permits (utility modules, scoped REST-edge grants).
- **Authorization semantics.** See [Authorization Model](./AUTHORIZATION_MODEL.md) and [API Security Architecture](./API_SECURITY_ARCHITECTURE.md).
- **Kafka event transport.** See ADR-0044 §4.

Binding decisions: [ADR-0011](../adr/0011-api-gateway-security-architecture.adr.md) (gateway owns the security boundary), [ADR-0014](../adr/0014-gateway-internal-service-security.adr.md) (discovery locator disabled; `pos-tax` internal-only), [ADR-0021](../adr/0021-tax-api-consumption-and-internal-access-policy.adr.md) (tax consumption policy), [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.adr.md) (`X-Authorities` derivation).

Code is the final authority. The two files that define this document's tables are
`pos-api-gateway/src/main/resources/application.yml` (route table) and each module's
`src/main/resources/application.yml` (`spring.application.name`, Eureka flags).

## 1. Service Registry

The `spring.application.name` value is the service id. It is a stable platform contract, not a
Eureka implementation detail: `@LoadBalanced` clients, gateway `lb://` targets, and configured
`*.service-id` properties all key off it. Renaming one is a breaking change across every caller.

Matching against the registry is case-insensitive; configuration values are written lowercase and
gateway `lb://` targets uppercase, following existing convention.

### Gateway-Routed Services

Externally reachable through `pos-api-gateway` on port 8080. Every route applies `StripPrefix=1`,
so `/customer/v1/...` arrives downstream as `/v1/...`.

| Module | Service id | Gateway prefix | Gateway target | Compose hostname |
| --- | --- | --- | --- | --- |
| pos-accounting | `accounting` | `/accounting/**` | `lb://ACCOUNTING` | pos-accounting |
| pos-bulk-loader | `pos-bulk-loader` | `/bulk-loader/**` | `lb://POS-BULK-LOADER` | pos-bulk-loader |
| pos-catalog | `catalog` | `/catalog/**` | `lb://CATALOG` | pos-catalog |
| pos-customer | `customer` | `/customer/**` | `lb://CUSTOMER` | pos-customer |
| pos-event-receiver | `event-receiver` | `/event-receiver/**` | `lb://EVENT-RECEIVER` | pos-event-receiver |
| pos-image | `image` | `/image/**` | `lb://IMAGE` | pos-image |
| pos-inquiry | `inquiry` | `/inquiry/**` | `lb://INQUIRY` | not in `docker-compose.yml` |
| pos-inventory | `inventory` | `/inventory/**` | `lb://INVENTORY` | pos-inventory |
| pos-invoice | `invoice` | `/invoice/**` | `lb://INVOICE` | pos-invoice |
| pos-location | `location` | `/location/**` | `lb://LOCATION` | pos-location |
| pos-marketing | `marketing` | `/marketing/**` | `lb://MARKETING` | pos-marketing |
| pos-mcp-server | `mcp-server` | `/mcp-server/**` | `lb://MCP-SERVER` | pos-mcp-server |
| pos-order | `order` | `/order/**` | `lb://ORDER` | pos-order |
| pos-people | `people` | `/people/**` | `lb://PEOPLE` | pos-people |
| pos-people-contact | `people-contact` | `/people-contact/**` | `lb://PEOPLE-CONTACT` | pos-people-contact |
| pos-price | `price` | `/price/**` | `lb://PRICE` | pos-price |
| pos-security-service | `security-service` | `/security-service/**` | `lb://SECURITY-SERVICE` | pos-security-service |
| pos-shop-manager | `shop-manager` | `/shop-manager/**` | `lb://SHOP-MANAGER` | pos-shop-manager |
| pos-supplier | `supplier` | `/supplier/**` | `lb://SUPPLIER` | pos-supplier |
| pos-tenant | `tenant` | `/tenant/**` | `lb://TENANT` | pos-tenant |
| pos-vehicle-fitment | `vehicle-fitment` | `/vehicle-fitment/**` | `lb://VEHICLE-FITMENT` | not in `docker-compose.yml` |
| pos-vehicle-inventory | `vehicle-inventory` | `/vehicle-inventory/**` | `lb://VEHICLE-INVENTORY` | pos-vehicle-inventory |
| pos-warranty | `warranty` | `/warranty/**` | `lb://WARRANTY` | pos-warranty |
| pos-workorder | `workorder` | `/workorder/**` | `lb://WORKORDER` | pos-workorder |

Two route entries share `lb://SECURITY-SERVICE`. `/security-service/v1/auth/tenants` is declared
ahead of the general `/security-service/**` route because routes match in declaration order and it
is narrower; it carries a per-caller `RequestRateLimiter` because it is the only public route that
answers which tenants exist (ADR-0062 §3).

### Not Gateway-Routed

Reachable only from inside the backend network. The gateway's `discovery.locator` is disabled
(ADR-0014), so a service is externally reachable only if it has an explicit route entry above —
registering with Eureka does not expose it.

| Module | Service id | Eureka | Reached by | Reason |
| --- | --- | --- | --- | --- |
| pos-api-gateway | `pos-api-gateway` | registers | `http://pos-api-gateway` | Is the edge; cannot route to itself |
| pos-documents | `documents` | registers | discovery | No external document surface |
| pos-tax | `pos-tax` | `register-with-eureka: false` | Docker DNS `http://pos-tax:8091` | ADR-0014, ADR-0021: internal-only |
| pos-vehicle-reference-carapi | `pos-vehicle-reference-carapi` | registers | discovery | Reference adapter, no external surface |
| pos-vehicle-reference-nhtsa | `pos-vehicle-reference-nhtsa` | registers | discovery | Reference adapter, no external surface |
| pos-service-discovery | `pos-service-discovery` | is the registry | `http://eureka-server:8761/eureka/` | Eureka server itself |

`pos-events` is a library, not a service; its startup registration traffic targets the
`pos-event-receiver` service endpoint.

## 2. Non-Gateway Exception Register

This register is closed. A client that is neither load-balanced discovery nor listed here is a
defect. Adding a row requires the reason to be recorded both here and in the calling module's
client configuration class.

| Client | Module | Category | Target | Reason |
| --- | --- | --- | --- | --- |
| `{Module}EventTypeInitializer` | 24 modules | startup-infra | `${pos.events.base-url:http://pos-event-receiver:8080}` | Event-type registration must not depend on Eureka convergence at startup |
| `{Module}PermissionRegistration` / `PermissionInitializer` | 25 modules | startup-infra | `${pos.security.base-url:http://pos-security-service:8080}` | Permission bootstrap runs before discovery converges (ADR-0011) |
| `PermissionVersionStartupCheck` | pos-api-gateway | startup-infra | `${pos.security.base-url:http://pos-security-service:8080}` | Gateway validates catalog version before serving; cannot route to itself |
| `DocumentTemplateInitializerSupport` | pos-document-helper | startup-infra | `${pos.documents.base-url}` | Template registration is startup bootstrap |
| `TaxServiceClient` | pos-invoice | tax-exemption | `${invoice.tax.base-url:http://pos-tax:8091/v1/tax}` | ADR-0021: pos-tax is internal-only and does not register |
| `TaxClient` + `TaxClientConfig` | pos-workorder | tax-exemption | `${pos.tax.base-url:http://pos-tax:8091}` | ADR-0021 |
| `TaxClientConfig` | pos-order | tax-exemption | `${pos.tax.base-url:http://pos-tax:8091}` | ADR-0021; ADR-0044 utility-module REST |
| `TaxFacadeTool` (direct leg) | pos-mcp-server | tax-exemption | `${POS_TAX_BASE_URL:http://pos-tax:8091/v1/tax}` | ADR-0021. Its second, load-balanced client covers legs pos-tax does not serve and is gateway-exception, not tax-exemption |
| Facade tools via `loadBalancedRestClientBuilder` | pos-mcp-server | gateway-exception | `http://pos-api-gateway` | Relays end-user bearer tokens that require gateway JWT validation (`BearerTokenRelayInterceptor`) |
| `McpServerConfiguration` transport provider | pos-mcp-server | direct-exception | `mcp.server.base-url` | MCP SSE transport wiring, not downstream business traffic |
| `ExaWebSearchTool` | pos-mcp-server | external | `${exa.base-url:https://api.exa.ai}` | Third-party Exa API |
| Ollama chat / embedding clients | pos-mcp-server | external | `${OLLAMA_BASE_URL:http://localhost:11434}` | Model runtime, local or `ollama.com` on alpha |
| `VehicleFitmentServiceImpl` | pos-vehicle-fitment | external | `https://vpic.nhtsa.dot.gov/v1/vehicles` | Third-party NHTSA vPIC |
| `VehicleReferenceService` | pos-vehicle-reference-nhtsa | external | `https://vpic.nhtsa.dot.gov/api/vehicles` | Third-party NHTSA vPIC |
| `VehicleReferenceService` | pos-vehicle-reference-carapi | external | `${carapi.base-url:https://carapi.app/api}` | Third-party CarAPI |
| `TaxConfiguration` + `ExternalTaxServiceClient` | pos-tax | external | `properties.externalService.baseUrl` | ADR-0021 constrains inbound access to pos-tax, not its outbound provider calls |

Notes:

- **Startup-infra defaults carry no `localhost`.** Every default resolves to a Docker DNS hostname
  on the container port. A `localhost` default in a startup client silently registers nothing when
  containerised, and initializers swallow failures so startup never blocks — there is no log-loud
  failure to notice.
- **Circular calls are direct by necessity.** `pos-security-service` calling `pos-people` or
  `pos-customer` during token issuance cannot route through the gateway, because the gateway would
  require a JWT that does not yet exist. The gateway cannot route to itself.
- **Converting a direct-exception row to gateway-routed** requires an explicit gateway route entry
  and an OpenAPI contract update in the same change.

## 3. Client Categories

Every internal HTTP client falls into exactly one of six categories.

| Category | Definition |
| --- | --- |
| `direct-discovery` | **The default.** `@LoadBalanced RestClient.Builder` against `http://{serviceId}`, resolved via Eureka. The caller injects `X-Authorities` and `X-User` itself. |
| `gateway-exception` | Internal call that must transit `http://pos-api-gateway` as a documented exception. Currently only pos-mcp-server facade tools, which relay end-user bearer tokens requiring gateway JWT validation. |
| `direct-exception` | Plain, non-load-balanced client bypassing both gateway and discovery, where the traffic is not a business-service call at all (protocol transport wiring). |
| `startup-infra` | Registration/bootstrap call issued before discovery converges. Plain client, Docker DNS hostname. |
| `external` | Third-party API. Never uses Eureka; base URL stays explicit. |
| `tax-exemption` | Call to `pos-tax`, which is internal-only and does not register with Eureka (ADR-0021). |

Domain-to-domain synchronous REST is not a category. ADR-0044 retired it in favour of events,
commands, and replicas; `pos-archunit`'s `DomainWallsTest` fails the build on a new domain-to-domain
client. The categories above describe transport for utility-module calls, scoped ADR-0044 REST-edge
grants, and infrastructure traffic.

## 4. Transport Policy

**Direct discovery is the default; gateway routing for internal calls is the exception.** The
gateway is the public edge for browsers and external clients. It is not the internal backend mesh
root and must not be treated as one.

```java
@Bean
@LoadBalanced
public RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
}

@Bean
public RestClient peopleRestClient(@LoadBalanced RestClient.Builder builder) {
    return builder.baseUrl("http://people").build(); // service id, not a hostname
}
```

Consequences:

- **Strip the gateway prefix.** An internal caller targets the downstream service's native path
  (`/v1/people/{id}`), not the gateway-facing `/people/v1/people/{id}`. The prefix exists only for
  the gateway's `StripPrefix=1` filter.
- **Inject authorization at the caller.** A direct call receives no gateway-derived security
  context. The caller sets `X-User` to its own service identity and `X-Authorities` to the minimal
  permission set the specific call needs. `GatewayAuthoritiesFilter`'s CSV fallback accepts it.
  Do not inject `X-Roles` unless the downstream endpoint requires it.
- **`pos-tax` must not be resolved through discovery.** It runs `register-with-eureka: false`; a
  load-balanced client against `http://pos-tax` cannot resolve. Use the explicit host and port.
- **`LoadBalancerClient.choose(serviceId)` is for dynamic targets only** — when the target id is
  computed at request time from input data. A statically configured target uses the load-balanced
  builder.
- **Feature code must not import `com.netflix.eureka.*` or `com.netflix.discovery.*`.**
  `@LoadBalanced RestClient.Builder` and `DiscoveryClient` are registry-agnostic; keeping
  Eureka-specific settings confined to `application.yml` and `pos-service-discovery` leaves the
  infrastructure layer replaceable.

### Eureka In Deployed Environments

Eureka is the platform's discovery standard, and each tenant cell runs its own. AWS-native
alternatives (Cloud Map, App Mesh, Service Connect) are not under consideration. Operating
requirements for a production cell:

- **Two `pos-service-discovery` replicas minimum, in separate Availability Zones**, peer-registered
  via `eureka.client.serviceUrl.defaultZone`. Every application service lists both peers.
- **Registrations are in-memory.** A Eureka restart expires all registrations until services
  re-heartbeat. With the 30s heartbeat and 90s eviction defaults a rolling restart is survivable;
  the deployment pipeline should gate cell health on all expected service ids being visible.
- **Self-preservation can mask real failures.** Tune `eureka.server.renewal-percent-threshold` to
  cell size and scrape Eureka's `/actuator/health` and `/eureka/apps` in the observability stack.

## 5. Client Configuration Convention

Internal client configuration names the service id and the downstream path separately:

```yaml
pos:
  people:
    service-id: ${POS_PEOPLE_SERVICE_ID:people}
    base-path: /v1/people
```

- `service-id` tells Spring Cloud LoadBalancer which registration to resolve. It must equal the
  downstream `spring.application.name` exactly (see §1), written lowercase.
- `base-path` tells the caller which downstream HTTP contract it is using.

This replaces the older single `*.base-url` property, which conflated "which service" with "which
path" and "which environment" in one opaque string. Twelve modules carry `*.service-id` properties
today: `pos-bulk-loader`, `pos-catalog`, `pos-inventory`, `pos-location`, `pos-marketing`,
`pos-order`, `pos-people-contact`, `pos-security-service`, `pos-shop-manager`, `pos-supplier`,
`pos-warranty`, `pos-workorder`. Exception-register clients (§2) keep an explicit `*.base-url`,
because for them the host genuinely is the configuration.

Design rationale for the split, including the per-phase rollout that produced it:
[`../superpowers/specs/2026-06-09-internal-service-discovery-reconciliation-design.md`](../superpowers/specs/2026-06-09-internal-service-discovery-reconciliation-design.md).

## 6. Two Recurring Failure Modes

**Ports: the local port is not the deployed port.** Domain services declare `server.port: 0` and
take an ephemeral port when run from an IDE. Under Compose and on alpha they are pinned by
`SERVER_PORT` — 8080 for most services, but `pos-tax` is 8091, `pos-documents` is 8092,
`pos-mcp-server` is 8086, and `pos-reference-mock` is 8095. A service-to-service URL must use the
**container** port, never the host port published in the `ports:` mapping, which exists only for
the developer's browser. Configuring a call against a host mapping is a recurring production bug;
it works on a laptop and fails inside the network.

**A service id that resolves to nothing fails silently.** `pos-order` once declared
`spring.application.name: order` while carrying no Eureka client dependency. It never registered, so
`lb://ORDER` could not resolve and the gateway answered 503 — with no compile-time signal, no
startup error, and a configuration file that read as correct. When adding or renaming a service,
verify three things together: the `spring.application.name` value, the presence of
`spring-cloud-starter-netflix-eureka-client`, and the service id appearing in the Eureka dashboard
(`http://localhost:8761`) at runtime.

## Related Documentation

- [Authorization Model](./AUTHORIZATION_MODEL.md) — how `X-Authorities`, `X-Perm-Bits`, and
  `@PreAuthorize` interact with the headers §4 requires callers to inject
- [API Security Architecture](./API_SECURITY_ARCHITECTURE.md) — the trust boundary the gateway owns
- [ADR-0014](../adr/0014-gateway-internal-service-security.adr.md) — discovery locator disabled,
  `pos-tax` internal-only
- [ADR-0021](../adr/0021-tax-api-consumption-and-internal-access-policy.adr.md) — tax consumption
  and internal access policy
- [ADR-0044](../adr/0044-platform-event-only-domain-walls.adr.md) — domain walls; whether a call may
  exist at all
- `durion/docs/architecture/BACKEND_ARCHITECTURE_GUIDE.md` — Docker topology and the observability
  stack

---

_Last Updated: 2026-09-20_
