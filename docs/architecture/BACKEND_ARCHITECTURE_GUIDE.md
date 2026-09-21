---
type: Architecture
title: Backend Architecture And Infrastructure Guide
description: Governs backend runtime shape — container ports, Dockerfile template, database-per-service, peer-call strategy, event ordering, and the observability stack.
status: Current
tags: [backend, docker, ports, postgresql, observability, kafka, aggregate-version, adr-0011, adr-0017, adr-0024]
---

A `pos-*` service is a Spring Boot container that binds a port its callers never address directly,
registers itself with Eureka, owns exactly one PostgreSQL database, and reaches every peer through
the gateway or a load-balanced `RestClient`; this document is the operative description of each of
those four facts for `durion-positivity-backend`.

Companion documents in this directory own the boundaries this one only summarises:
[Internal Transport And Service Discovery](./INTERNAL_TRANSPORT_AND_SERVICE_DISCOVERY.md) for the
service-id and gateway-route registry, [Error Envelope](./api/ERROR_ENVELOPE.md) for the error
contract, [Observability Architecture](./observability/OBSERVABILITY.md) for the telemetry pipeline.

## Table of Contents

1. [Port Strategy](#port-strategy)
2. [Docker Configuration](#docker-configuration)
3. [Inter-Service Communication](#inter-service-communication)
4. [Event Replication: `aggregateVersion` Semantics](#event-replication-aggregateversion-semantics)
5. [Observability](#observability)
6. [Correlation ID Implementation](#correlation-id-implementation)
7. [PostgreSQL Setup](#postgresql-setup)

---

## Port Strategy

### Overview

The port a service listens on **depends on how it is run**, and the two cases are routinely
confused. Read this section as two independent regimes, not one:

| Regime | How a service is started | Listening port |
| --- | --- | --- |
| Local JVM | `./mvnw -pl pos-order spring-boot:run`, IDE run configurations, `dev` profile | Ephemeral — `server.port: 0`, the OS picks |
| Container | Docker Compose, alpha, any image built from the module `Dockerfile` | **8080**, set by `SERVER_PORT: 8080` in the Compose environment block |

Fixed in both regimes:

- **Gateway (`pos-api-gateway`)**: **8080** — the single external entry point.
- **Eureka (`pos-service-discovery`)**: **8761** — the service registry.

### Container ports: every service listens on 8080

Under Compose and on alpha, 21 of the 25 `SERVER_PORT` declarations in `docker-compose.yml` are
`8080` (with `MANAGEMENT_SERVER_PORT` matching). The four exceptions pin a different container
port: `pos-tax` 8091, `pos-documents` 8092, `pos-mcp-server` 8086, `pos-reference-mock` 8095.

The container port is **not** derived from the host mapping. `ports: - "8090:8080"` on
`pos-workorder` (`docker-compose.yml:1735`) publishes host 8090 onto container 8080, and the
service is reachable *from another container* only on 8080.

> **The trap this closes.** A service-to-service base URL must name the **container** port, never
> the host mapping. `http://pos-workorder:8090` fails inside the Compose network even though
> `http://localhost:8090` works from the host. When in doubt read the service's `SERVER_PORT`, not
> its `ports:` entry.

Prefer not to hardcode either: resolve peers by Eureka service id
(see [Internal Transport And Service Discovery](./INTERNAL_TRANSPORT_AND_SERVICE_DISCOVERY.md)).

### Ephemeral ports (local JVM only)

Outside containers every service but the gateway and Eureka runs `server.port: 0`, including
`pos-bulk-loader` (`pos-bulk-loader/src/main/resources/application.yml:38`) — it has **no** fixed
8090 port of its own; host 8090 in Compose belongs to `pos-workorder`.

**How it works:**

1. Service starts with `server.port: 0`
2. OS assigns available ephemeral port
3. Service registers with Eureka using actual assigned port
4. Gateway discovers service via Eureka (`lb://SERVICE_NAME`)

### Benefits of the ephemeral default

- No port conflicts on dev machines or CI/CD parallel runs
- Services auto-discover each other via Eureka
- Multiple instances of the same service can run simultaneously

### Profile Configuration

#### Local Development (`application-dev.yml`)

```yaml
server:
  port: 0

management:
  server:
    port: 0
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

eureka:
  client:
    fetch-registry: true
    register-with-eureka: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.uuid}
```

#### Production (`application-prod.yml`)

```yaml
server:
  port: 8080
  shutdown: graceful

management:
  server:
    port: 9000  # Internal-only

eureka:
  client:
    enabled: false  # Use cloud registry
```

---

## Docker Configuration

### Standard Dockerfile Template

All services use this pattern (see
[`pos-order/Dockerfile`](../../../durion-positivity-backend/pos-order/Dockerfile) for the live copy):

```dockerfile
FROM eclipse-temurin:25-jdk-alpine
VOLUME /tmp

# Grafana OpenTelemetry Java Agent — pinned by ARG so the version is overridable at build time
ARG GRAFANA_OTEL_AGENT_VERSION=v2.9.0
ADD https://github.com/grafana/grafana-opentelemetry-java/releases/download/${GRAFANA_OTEL_AGENT_VERSION}/grafana-opentelemetry-java.jar /opt/grafana-opentelemetry-java.jar

ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS
COPY target/{service-name}-*.jar {service-name}.jar
ENTRYPOINT ["sh", "-c", "exec java -javaagent:/opt/grafana-opentelemetry-java.jar $JAVA_OPTS -jar {service-name}.jar"]
```

`ADD` with a remote URL, not `RUN curl`: it needs no shell layer and Docker caches the fetch
against the resolved `ARG`, so bumping `GRAFANA_OTEL_AGENT_VERSION` is a one-line change per module.

### Docker Compose Service Pattern

**Gateway (fixed port):**

```yaml
pos-api-gateway:
  build:
    context: ./pos-api-gateway
  ports:
    - "8080:8080"
  environment:
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
  depends_on:
    eureka-server:
      condition: service_healthy
```

**Dynamic port service:**

```yaml
pos-catalog:
  build:
    context: ./pos-catalog
  # No ports exposed; uses dynamic port
  environment:
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
  depends_on:
    eureka-server:
      condition: service_healthy
```

### Key Features

- **Lightweight Base Image**: `eclipse-temurin:25-jdk-alpine`
- **JAVA_OPTS Support**: Runtime JVM tuning
- **Health Checks**: Configured per service
- **Service Discovery**: Eureka for all internal communication

---

## Inter-Service Communication

### Architecture

```text
┌──────────────────────────────────────────────────────────────┐
│                    External Clients / Frontend                │
└──────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────┐
│                    API Gateway (port 8080)                    │
│  • ApiVersionHeaderToPathFilter                              │
│  • Explicit route whitelist (discovery locator disabled)      │
└──────────────────────────────────────────────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         ▼                      ▼                      ▼
   ┌───────────┐          ┌───────────┐          ┌───────────┐
   │ inventory │◄────────►│ customer  │◄────────►│ security  │
   │ (Eureka)  │          │ (Eureka)  │          │ (Eureka)  │
   └───────────┘          └───────────┘          └───────────┘
```

### Security Ownership Model

- `pos-security-service` is the source of truth for identities, roles, permissions, and assignments.
- API Gateway is the authentication enforcement boundary for external traffic.
- Backend services perform authorization with `@PreAuthorize` using gateway-established authority context.
- See [ADR-0011](../adr/0011-api-gateway-security-architecture.adr.md) for the canonical trust model.

### Strategy 1: Through API Gateway

Use the gateway for client-facing traffic and for calls that must pass through gateway-level controls:

```yaml
gateway:
  url: ${GATEWAY_URL:http://localhost:8080}
```

```java
@Configuration
public class ServiceClientConfig {
    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Bean
    public RestClient gatewayRestClient() {
        return RestClient.builder()
            .baseUrl(gatewayUrl)
            .defaultHeader("X-API-Version", "1")
            .build();
    }
}
```

### Strategy 2: Direct Eureka Load-Balanced Calls

For performance-critical internal service-to-service calls:

```java
@Configuration
public class LoadBalancedClientConfig {
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient securityServiceClient(@LoadBalanced RestClient.Builder builder) {
        return builder.baseUrl("http://security-service").build();
    }
}
```

### Service Name Registry

**The registry lives in
[Internal Transport And Service Discovery §1](./INTERNAL_TRANSPORT_AND_SERVICE_DISCOVERY.md#1-service-registry).**
It is derived from
[`pos-api-gateway/src/main/resources/application.yml`](../../../durion-positivity-backend/pos-api-gateway/src/main/resources/application.yml),
which is the only authority: `spring.cloud.gateway.server.webflux.discovery.locator.enabled` is
`false`, so a service is externally reachable if and only if it has an explicit route there. An
earlier copy of the table in this document listed 9 of the 25 routes and was wrong by omission;
maintaining a second copy here is what made it wrong.

Two facts worth carrying at the point of use, because they are the ones that bite:

- **Eureka service ids drop the `pos-` prefix** — `pos-order` registers as `ORDER`, routed
  `lb://ORDER`. Every route follows this except one.
- **`pos-bulk-loader` is the exception**: it registers as `POS-BULK-LOADER` and is routed
  `lb://POS-BULK-LOADER` behind path `/bulk-loader/**`
  (`pos-api-gateway/src/main/resources/application.yml:66-70`). A client that guesses
  `lb://BULK-LOADER` gets no instance.

---

## Event Replication: `aggregateVersion` Semantics

Replica-maintaining consumers guard against out-of-order delivery by comparing the envelope's
`aggregateVersion` with the version their replica row already holds. #1486 found the six
`catalog.events.v1` consumers split between `>` and `>=` guards, and the epoch-millis version
convention able to tie inside one millisecond — a combination that made replays silently
repair some replicas and no-op on others. This section states the one rule; the split must
not come back.

### The rule

**An equal `aggregateVersion` applies. A consumer skips a fact only when the version it
already holds is *strictly greater* than the incoming one.**

The canonical implementation is `com.positivity.domainevents.ReplicaVersionGuard.isStale(held,
incoming)` in `pos-domain-events`, whose unit test pins these semantics once for every
consumer. Listeners must delegate to it rather than hand-rolling the comparison — the `>` /
`>=` split is exactly what hand-rolling produced. A replica row that does not exist holds
nothing to protect; the fact lands.

The rule covers both cases an equal version can arise:

- **Live traffic**: publishers must emit *strictly advancing* versions (see below), so an
  equal version means the fact describes state the consumer already holds — applying it is an
  idempotent no-op, never a regression.
- **Replay**: a replayed fact deliberately carries the *same* version as the state it
  describes (that is what makes it indistinguishable from the live fact). Applying on equal
  is what lets `POST /v1/products/facts/replay` (#1309) and
  `POST /v1/catalog-items/services/facts/replay` (#1306) repair a replica whose rows are
  wrong or missing even though it holds the right version number. A `>=` guard turns that
  replay into a `200` that repairs nothing — the operational trap #1486 closed.

### The publisher contract

The rule above is only safe because publishers guarantee the version *strictly advances* on
every committed mutation of an aggregate. Wall-clock timestamps do not satisfy this: two
mutations in one millisecond tie, and a tie plus "equal applies" lets an older snapshot
overwrite a newer one when outbox rows drain out of order.

pos-catalog (the `catalog.events.v1` owner) satisfies the contract with a JPA optimistic-lock
`@Version` column on `ProductEntity`, `ServiceEntity`, and `SupplierArticleCodeEntity`
(#1486). Migration `V15` seeds the column from each row's legacy `updatedAt` epoch millis, so
the published sequence continues monotonically from the versions consumers already hold — no
consumer-side migration was needed. Delete tombstones publish `version + 1`, deterministically
past every fact the aggregate ever emitted.

### Other domains' topics

Every fact topic was surveyed (#1486) for the same guard split and for whether its publisher
already satisfies the strictly-advancing contract:

| Topic | Publisher version source | Strictly advancing? | Consumers on the rule? |
|---|---|---|---|
| `catalog.events.v1` | pos-catalog `@Version`, flushed before emit (#1486) | Yes | Yes — all seven use `ReplicaVersionGuard` |
| `vehicle.events.v1` (`VehicleUpdatedV1`) | pos-vehicle-inventory `VehicleRecord` `@Version`, flushed before emit | Yes | Yes — former `>=` guards (pos-order, pos-customer) flipped in #1486 |
| `vehicle.events.v1` (`VehicleCarePreferenceUpdatedV1`) | Epoch millis by design (rows are hard-deleted and re-created, so an entity version would restart at 0) | No | Its one consumer already applies on equal; acceptable for a last-writer-wins preference fact |
| `invoice.events.v1` | pos-invoice `Invoice`/`BillingRules` `@Version`, flushed before emit | Yes | Yes — pos-accounting's `>=` flipped in #1486 |
| `warranty.events.v1` | pos-warranty `WarrantyClaim` `@Version`, flushed and force-incremented on otherwise-clean mutations | Yes | Yes — pos-accounting's `>=` flipped in #1486 |
| `workorder.events.v1` (`WorkorderUpdatedV1`, `EstimateUpdatedV1`) | pos-workorder `Workorder.version` / `Estimate.aggregateVersion` `@Version`, flushed before emit, seeded at migration time from wall-clock millis (#1486 follow-up; `Estimate` already had an unrelated manual `version` revision counter, hence the distinct column) | Yes | Yes — pos-order's `>=` flipped |
| `workorder.events.v1` (service-completion, fleet-auth, time/work-session facts) | Emission-timestamp epoch millis via `OutboxEventWriter`'s 4-arg form | No | No consumer version-guards these facts — acceptable until one does |
| `customer.events.v1` (`CustomerPartyUpdatedV1`, `BillingRulesUpdatedV1`) | pos-customer `AbstractParty` `@Version` (TABLE_PER_CLASS root: `person_party` and `commercial_party` each carry the seeded column), flushed before emit; party deletes tombstone `version + 1` (#1486 follow-up) | Yes | Yes — pos-order's and pos-accounting's `>=` flipped (the legacy `aggregateVersion > 0` carve-out for versionless envelopes is retired) |
| `customer.events.v1` (tag/segment/suppression/consent/redemption facts) | Emission-timestamp epoch millis | No | No consumer version-guards these facts — acceptable until one does |
| `location.events.v1` | pos-location `Location`/`StorageLocationEntity` `@Version`, flushed before emit, seeded at migration time from wall-clock millis (#1486 follow-up) | Yes | Yes — pos-order's `>=` flipped |

**Nothing is scoped out any more.** Every fact a consumer version-guards now rides a
strictly-advancing JPA `@Version`, and every former `>=` guard has moved to
`ReplicaVersionGuard`. The emission-timestamp rows above survive only on facts no consumer
version-guards (and on `VehicleCarePreferenceUpdatedV1`, where it is by design); the moment a
consumer wants to version-guard one of them, its publisher must first adopt the
`@Version`-flush pattern — flipping the guard first would reintroduce the same-millisecond
race #1486 closed. Note that each domain's `OutboxReplayServiceImpl` re-sends existing outbox
rows under their original `eventId`s (dropped by consumer idempotency); a repair that must
re-apply state needs a regenerate-from-current-state replay like catalog's, which the
equal-applies rule is what makes effective.

---

## Observability

### Stack Overview

Images and ports are as declared in
[`docker-compose.yml`](../../../durion-positivity-backend/docker-compose.yml); line numbers below
are from that file.

| Component | Image | Port | Purpose |
| ----------- | ------- | ------ | --------- |
| Jaeger | `jaegertracing/all-in-one:1.54` (L25) | 16686 | Distributed tracing UI |
| Prometheus | `prom/prometheus:v2.49.1` (L45) | 9090 | Metrics collection (pull, see below) |
| Grafana | `grafana/grafana:10.3.3` (L64) | 3000 | Visualization; queries Prometheus, Jaeger and Loki |
| OTEL Collector | `otel/opentelemetry-collector-contrib:0.93.0` (L85) | 4317/4318 OTLP, 8888 self-metrics, 13133 health | Traces and logs only — **no** metrics pipeline |
| Loki | `grafana/loki:3.1.1` (L104) | `127.0.0.1:3100` | Log store; also runs the NLTI LogQL alert rules (#1424) |
| Promtail | `grafana/promtail:3.1.1` (L181) | — | Ships container logs into Loki |

The collector image is the **`-contrib`** distribution, not core `otel/opentelemetry-collector`;
substituting the core image is not a safe swap. Loki's port is bound to `127.0.0.1` on purpose:
Grafana and Promtail reach it over the `pos-network` bridge, so container logs are never published
on a shared or remote host interface.

### Quick Start

```bash
# Start observability stack
docker-compose up -d jaeger prometheus grafana otel-collector loki promtail

# Access dashboards
# Grafana:     http://localhost:3000  (admin/admin)
# Jaeger UI:   http://localhost:16686
# Prometheus:  http://localhost:9090
```

### Metrics path — single authority (#867)

Application metrics are **pull-only**: Prometheus scrapes each service's
`/actuator/prometheus` endpoint under a per-service `job` label (see
[`observability/prometheus.yml`](../../../durion-positivity-backend/observability/prometheus.yml)).
The OTel collector carries traces and logs only — it has no metrics pipeline, and services run with
`OTEL_METRICS_EXPORTER=none` so the Java agent does not push OTLP metrics. See
[`observability/README.md`](../../../durion-positivity-backend/observability/README.md) for details.

### OpenTelemetry Configuration

Every module `Dockerfile` bakes in the Grafana OpenTelemetry Java Agent, pinned by build argument
and attached on the entrypoint (`pos-order/Dockerfile:5-6`):

```dockerfile
ARG GRAFANA_OTEL_AGENT_VERSION=v2.9.0
ADD https://github.com/grafana/grafana-opentelemetry-java/releases/download/${GRAFANA_OTEL_AGENT_VERSION}/grafana-opentelemetry-java.jar /opt/grafana-opentelemetry-java.jar

ENV OTEL_SERVICE_NAME=pos-{service}
ARG OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
ENV OTEL_EXPORTER_OTLP_ENDPOINT=$OTEL_EXPORTER_OTLP_ENDPOINT
ENV OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf

ENTRYPOINT ["sh", "-c", "exec java -javaagent:/opt/grafana-opentelemetry-java.jar $JAVA_OPTS -jar {service}.jar"]
```

The default export is OTLP **HTTP on 4318** (`http/protobuf`), not gRPC on 4317; the collector
accepts both, but a hand-set `OTEL_EXPORTER_OTLP_ENDPOINT` must match the protocol its port speaks.

### Shared Configuration (`application-observability.yml`)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, 10% in prod

otel:
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
```

---

## Correlation ID Implementation

### Standard: `X-Correlation-Id` Header

All requests should include correlation IDs for distributed tracing.

### Backend Pattern

```java
@PostMapping("/resource")
public ResponseEntity<?> createResource(
    @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody ResourceRequest request) {

    log.info("Create resource requested. correlationId={}", correlationId);
    return ResponseEntity.ok(resourceService.create(request, correlationId));
}
```

### Error Response Pattern

**Do not define a per-module error type.** The platform envelope is
`com.positivity.shared.error.ApiError` in `pos-shared-dtos` — a record whose machine-readable field
is `code` (not `errorCode`), mandated for every 4xx/5xx body by ADR-0017 §3. A module's
`@RestControllerAdvice` builds it through one of the four factories; it never constructs a parallel
shape:

```java
@RestControllerAdvice
class OrderExceptionHandler {

    private final Clock clock;   // ADR-0024 §2: the injected application clock, never Instant.now()

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        "ORDER_NOT_FOUND",
                        ex.getMessage(),
                        HttpStatus.NOT_FOUND.value(),
                        Instant.now(clock).toString(),
                        CorrelationIdHolder.get()));
    }
}
```

Two rules the previous version of this snippet broke, both machine-enforced:

| Rule | Source | Enforcement |
| --- | --- | --- |
| The envelope is `ApiError` with field `code` | ADR-0017 §3; `pos-shared-dtos/src/main/java/com/positivity/shared/error/ApiError.java` | `pos-openapi-validation` `errorSchema: STRICT` for every module publishing a spec |
| No no-arg `Instant.now()` in production code | ADR-0024 §2, §4 | ArchUnit `productionCodeShouldNotUseNoArgNowCalls` (`pos-archunit` `ArchitectureTests`) |

Most modules need no handler at all: `pos-web-common`'s auto-configured `GlobalApiExceptionHandler`
already answers the platform fallback codes. Field semantics, per-module codes and payload examples
live in [Error Envelope](./api/ERROR_ENVELOPE.md).

### Frontend Pattern

```typescript
import { fetchWithCorrelation, parseErrorResponse } from '@/utils/correlationId';

const response = await fetchWithCorrelation('/api/v1/resource', {
  method: 'POST',
  body: JSON.stringify(data)
});

if (!response.ok) {
  const error = await parseErrorResponse(response);
  console.error(`Error: ${error.message} (Correlation ID: ${error.correlationId})`);
}
```

---

## PostgreSQL Setup

### Database per service

**There is one PostgreSQL server and one database per service, not one shared database.**
[`postgres/init-databases.sql`](../../../durion-positivity-backend/postgres/init-databases.sql)
issues 27 `CREATE DATABASE` statements — `pos_order_db`, `pos_catalog_db`, `pos_workorder_db` and
so on — and each service's `SPRING_DATASOURCE_URL` in `docker-compose.yml` names its own. No
service reads another's database; cross-service data arrives over REST or Kafka
(see [Inter-Service Communication](#inter-service-communication)).

`${POSTGRES_DB}` is the **server's** maintenance database, created by the container image itself.
It is what `psql` connects to with no `-d`, and what the postgres exporter scrapes. It is not any
service's schema, and pointing a service at it produces a service with no tables.

### Connection Details

| Property | Value |
| ---------- | ------- |
| Host | `postgres` (Docker) / `localhost` (host) |
| Port | 5432 |
| Application username | `pos_app` (`SPRING_DATASOURCE_USERNAME`) — non-owner, `NOBYPASSRLS`, per ADR-0062 |
| Application password | `${POS_APP_PASSWORD}`, defaulting to `${POSTGRES_PASSWORD}` |
| Flyway username | `${POSTGRES_USER}` (`SPRING_FLYWAY_USER`) — the owner role; migrations only |
| Database | `pos_<module>_db`, one per service |

The split credential is load-bearing under ADR-0062 row-level multitenancy: the pool must not be
able to bypass RLS, while Flyway must own the schema it migrates. See
[Tenancy Schema Conventions](./deployment/TENANCY_SCHEMA.md).

### JDBC Connection String

```text
jdbc:postgresql://postgres:5432/pos_catalog_db      # pos-catalog
jdbc:postgresql://postgres:5432/pos_workorder_db    # pos-workorder
```

### Docker Compose Configuration

```yaml
postgres:
  image: timescale/timescaledb:2.17.2-pg16
  container_name: postgres-positivity
  command: [ "postgres", "-c", "shared_preload_libraries=timescaledb" ]
  ports:
    - "127.0.0.1:5432:5432"       # localhost only; services reach it over pos-network
  environment:
    POSTGRES_USER: ${POSTGRES_USER}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    POS_APP_PASSWORD: ${POS_APP_PASSWORD:-}
    PGDATA: /var/lib/postgresql/data/pgdata
  volumes:
    - postgres-data:/var/lib/postgresql/data
    # init-databases.sql and init-tenancy.sh; run once, on an empty data volume only
    - ./postgres:/docker-entrypoint-initdb.d:ro
  healthcheck:
    test: [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USER}" ]
    interval: 5s
    timeout: 3s
    retries: 20
    start_period: 60s
```

Three things about this block are load-bearing:

- **No `POSTGRES_DB` is set.** The image therefore names the maintenance database after
  `${POSTGRES_USER}`. Every real schema is a `pos_<module>_db` created by the init scripts.
- **The init directory runs once**, against an empty data volume. Adding a service's database to
  `init-databases.sql` has no effect on an existing volume — drop the volume, or issue the
  `CREATE DATABASE` by hand. This is the usual cause of a new service crash-looping on startup.
- **`shared_preload_libraries=timescaledb`** is a server *startup* setting, needed by
  Timescale-backed modules such as `pos-event-receiver`. It belongs in the container command, never
  in a Flyway migration.

### Useful Commands

```bash
# Connect to a service's own database (maintenance DB is named after $POSTGRES_USER)
docker compose exec postgres psql -U "$POSTGRES_USER" -d pos_catalog_db

# List the per-service databases that actually exist on this volume
docker compose exec postgres psql -U "$POSTGRES_USER" -Atc \
  "SELECT datname FROM pg_database WHERE datname LIKE 'pos\_%' ORDER BY 1"

# Back up one service's database
docker compose exec postgres pg_dump -U "$POSTGRES_USER" pos_catalog_db > pos_catalog_db.sql

# Check readiness
docker compose exec postgres pg_isready -U "$POSTGRES_USER"
```

---

## References

- [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
- [Netflix Eureka](https://github.com/Netflix/eureka)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)
- [Grafana OTEL Java Agent](https://github.com/grafana/grafana-opentelemetry-java)
