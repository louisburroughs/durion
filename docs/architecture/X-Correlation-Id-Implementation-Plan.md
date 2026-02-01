# X-Correlation-Id Implementation Plan

**Version:** 1.0  
**Status:** Active  
**Last Updated:** 2026-02-01  
**Related:** ADR-0011 Gateway-based Security Architecture  

---

## Purpose

This document provides implementation guidance for the `X-Correlation-Id` header across the Durion platform. Correlation IDs enable distributed tracing, request tracking, and troubleshooting across microservices by linking all operations related to a single client request.

---

## Benefits

### 1. Distributed Tracing

- Track requests across gateway → backend service → database → external APIs
- Visualize request flow in observability tools (Jaeger, Zipkin, Grafana)
- Identify bottlenecks and latency sources

### 2. Troubleshooting

- Correlate logs across multiple services for a single user action
- Search logs by correlation ID to reconstruct complete request timeline
- Debug production issues without reproducing locally

### 3. Audit & Compliance

- Link security events to originating requests
- Track data access patterns across services
- Satisfy regulatory requirements for request traceability

### 4. Performance Analysis

- Measure end-to-end request latency
- Identify slow services in the request chain
- Track p50/p95/p99 latency per correlation ID

---

## Header Specification

### Format

```
X-Correlation-Id: <UUID-or-string>
```

**Recommended Format:** UUID v4 (e.g., `550e8400-e29b-41d4-a716-446655440000`)

**Alternative Format:** Hierarchical string (e.g., `req-2026-02-01-12345`, `moqui-user123-1738416000`)

### Character Set

- Alphanumeric characters: `a-z`, `A-Z`, `0-9`
- Hyphens: `-`
- Underscores: `_`
- Maximum length: 128 characters

### Case Sensitivity

Header name is **case-insensitive** per HTTP/1.1 spec:

- `X-Correlation-Id`, `x-correlation-id`, `X-CORRELATION-ID` are equivalent

Header value is **case-sensitive** and must be preserved exactly.

---

## Implementation Stages

### Stage 1: Gateway Generation & Propagation

**Status:** ✅ Completed (pos-api-gateway)

**Implementation:**

1. Gateway checks incoming requests for `X-Correlation-Id` header
2. If present, preserve and propagate the client-provided value
3. If missing, generate new UUID v4 and inject header
4. Include `X-Correlation-Id` in all downstream service calls
5. Echo `X-Correlation-Id` in response headers

**Code Example (Spring Cloud Gateway):**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements GlobalFilter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Get or generate correlation ID
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Add to request (downstream propagation)
        ServerHttpRequest mutatedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build();
        
        // Add to response (client echo)
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        
        // Add to MDC for logging
        MDC.put("correlationId", correlationId);
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .doFinally(signalType -> MDC.clear());
    }
}
```

### Stage 2: Backend Service Integration

**Status:** 🔄 In Progress (pos-* services)

**Implementation:**

1. All services read `X-Correlation-Id` from incoming requests
2. Store in MDC (Mapped Diagnostic Context) for logging
3. Include in all outbound REST calls (RestClient, WebClient, Feign)
4. Include in all error responses
5. Tag traces/spans with correlation ID

**Code Example (Backend Service Filter):**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     FilterChain filterChain) throws ServletException, IOException {
        
        // Extract correlation ID from request
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.isEmpty()) {
            // Fallback: generate if missing (should not happen with gateway)
            correlationId = UUID.randomUUID().toString();
        }
        
        // Add to MDC for logging
        MDC.put("correlationId", correlationId);
        
        // Echo in response
        response.addHeader(CORRELATION_ID_HEADER, correlationId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**RestClient Configuration:**

```java
@Bean
public RestClient.Builder restClientBuilder() {
    return RestClient.builder()
        .requestInterceptor((request, body, execution) -> {
            // Propagate correlation ID to downstream calls
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                request.getHeaders().add("X-Correlation-Id", correlationId);
            }
            return execution.execute(request, body);
        });
}
```

### Stage 3: Logging Integration

**Status:** 🔄 In Progress

**Implementation:**

1. Configure Logback/Log4j2 to include correlation ID in all log statements
2. Use MDC (SLF4J) to store correlation ID per thread
3. Include correlation ID in structured logs (JSON format)
4. Configure log aggregation (CloudWatch, ELK) to index by correlation ID

**Logback Configuration:**

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} [correlationId=%X{correlationId}] - %msg%n</pattern>
        </encoder>
    </appender>
    
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>correlationId</includeMdcKeyName>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="JSON" />
    </root>
</configuration>
```

**Structured Log Example:**

```json
{
  "timestamp": "2026-02-01T14:30:00.123Z",
  "level": "INFO",
  "logger": "com.positivity.order.service.OrderService",
  "thread": "http-nio-8086-exec-1",
  "message": "Creating order for customer cust-456",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "service": "pos-order",
  "version": "1.2.3"
}
```

### Stage 4: OpenTelemetry Integration

**Status:** 📋 Planned

**Implementation:**

1. Configure OpenTelemetry to tag spans with correlation ID
2. Link correlation ID to trace ID for cross-reference
3. Include correlation ID in span attributes
4. Export to observability backend (Jaeger, Grafana Tempo)

**OpenTelemetry Configuration:**

```java
@Component
public class CorrelationIdSpanProcessor implements SpanProcessor {
    
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            span.setAttribute("http.correlation_id", correlationId);
            span.setAttribute("correlation.id", correlationId);
        }
    }
    
    @Override
    public void onEnd(ReadableSpan span) {
        // No-op
    }
}
```

**Span Attributes:**

```json
{
  "traceId": "7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c",
  "spanId": "1a2b3c4d5e6f7a8b",
  "name": "POST /v1/orders",
  "attributes": {
    "http.method": "POST",
    "http.route": "/v1/orders",
    "http.status_code": 201,
    "http.correlation_id": "550e8400-e29b-41d4-a716-446655440000",
    "correlation.id": "550e8400-e29b-41d4-a716-446655440000",
    "service.name": "pos-order",
    "service.version": "1.2.3"
  }
}
```

### Stage 5: Error Response Standardization

**Status:** ✅ Completed

**Implementation:**

1. All error responses MUST include `correlationId` field in body
2. Use `@ControllerAdvice` to ensure consistent error format
3. Extract correlation ID from MDC or request header

**Error Response Format:**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid order request",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "errors": [
    {
      "field": "customerId",
      "message": "Customer ID is required"
    }
  ]
}
```

**Global Exception Handler:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = request.getHeader("X-Correlation-Id");
        }
        
        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## Usage Guidelines

### For Client Applications (Moqui Frontend)

**Best Practice:**

1. Generate correlation ID for each user action (button click, form submit)
2. Include `X-Correlation-Id` in all API requests
3. Display correlation ID to user on error screens for support tickets
4. Log correlation ID in browser console for debugging

**Example (JavaScript/TypeScript):**

```typescript
import { v4 as uuidv4 } from 'uuid';

async function createOrder(orderData: OrderRequest): Promise<Order> {
  const correlationId = uuidv4();
  
  const response = await fetch('/security/orders', {
    method: 'POST',
    headers: {
      'X-API-Version': '1',
      'X-Correlation-Id': correlationId,
      'Authorization': `Bearer ${getJwtToken()}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(orderData)
  });
  
  if (!response.ok) {
    const error = await response.json();
    console.error('Order creation failed', {
      correlationId: error.correlationId || correlationId,
      error
    });
    throw new Error(`Failed to create order. Correlation ID: ${error.correlationId}`);
  }
  
  return response.json();
}
```

### For Backend Services

**Best Practice:**

1. Always read `X-Correlation-Id` from incoming requests
2. Store in MDC immediately upon request entry
3. Clear MDC on request exit (use try-finally)
4. Propagate to all downstream calls (RestClient, messaging, async tasks)
5. Include in all log statements automatically via MDC
6. Include in all error responses

**Anti-Patterns to Avoid:**

❌ **Don't generate new correlation ID in backend service** (unless missing from gateway)
❌ **Don't modify existing correlation ID** (preserve exact value)
❌ **Don't forget to clear MDC** (causes correlation ID leakage across requests)
❌ **Don't log correlation ID separately** (redundant with MDC pattern)

### For Observability

**Query Examples:**

**Find all logs for a request:**

```
CloudWatch Logs Insights:
fields @timestamp, service, logger, message
| filter correlationId = "550e8400-e29b-41d4-a716-446655440000"
| sort @timestamp asc
```

**Find all traces for a request:**

```
Jaeger/Grafana Tempo:
http.correlation_id = "550e8400-e29b-41d4-a716-446655440000"
```

**Aggregate latency by correlation ID:**

```
SELECT correlationId, 
       AVG(duration_ms) as avg_latency,
       MAX(duration_ms) as max_latency
FROM request_metrics
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY correlationId
ORDER BY max_latency DESC
LIMIT 10;
```

---

## Testing Guidelines

### Unit Tests

**Test correlation ID filter:**

```java
@Test
public void testCorrelationIdFilter_generatesIdWhenMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    
    CorrelationIdFilter filter = new CorrelationIdFilter();
    filter.doFilter(request, response, filterChain);
    
    String correlationId = response.getHeader("X-Correlation-Id");
    assertNotNull(correlationId);
    assertTrue(correlationId.matches("^[0-9a-f-]{36}$")); // UUID format
}

@Test
public void testCorrelationIdFilter_preservesExistingId() throws Exception {
    String existingId = "existing-correlation-id-123";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", existingId);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    
    CorrelationIdFilter filter = new CorrelationIdFilter();
    filter.doFilter(request, response, filterChain);
    
    String correlationId = response.getHeader("X-Correlation-Id");
    assertEquals(existingId, correlationId);
}
```

### Integration Tests

**Test end-to-end propagation:**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CorrelationIdIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    public void testCorrelationIdPropagation() {
        String correlationId = UUID.randomUUID().toString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", correlationId);
        headers.set("X-API-Version", "1");
        headers.set("Authorization", "Bearer " + getTestJwt());
        
        HttpEntity<OrderRequest> request = new HttpEntity<>(
            new OrderRequest("cust-123"), headers);
        
        ResponseEntity<Order> response = restTemplate.exchange(
            "/security/orders",
            HttpMethod.POST,
            request,
            Order.class
        );
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(correlationId, response.getHeaders().getFirst("X-Correlation-Id"));
    }
}
```

### Manual Testing

**Using curl:**

```bash
# Test with custom correlation ID
curl -X POST http://localhost:8080/security/orders \
  -H "X-API-Version: 1" \
  -H "X-Correlation-Id: manual-test-12345" \
  -H "Authorization: Bearer eyJhbGci..." \
  -H "Content-Type: application/json" \
  -d '{"customerId": "cust-456"}' \
  -v

# Verify correlation ID in response headers:
# < X-Correlation-Id: manual-test-12345
```

---

## Rollout Plan

### Phase 1: Gateway (✅ Completed)

- Implement correlation ID generation/propagation in pos-api-gateway
- Configure response header echo
- Deploy to all environments

### Phase 2: Backend Services (🔄 In Progress)

- Add CorrelationIdFilter to pos-security-common
- Configure all pos-* services to import filter
- Add MDC logging configuration
- Update error handlers to include correlation ID
- Deploy incrementally: security → order → inventory → catalog

### Phase 3: Frontend (📋 Planned)

- Add correlation ID generation to Moqui REST client
- Display correlation ID on error screens
- Include in browser console logs
- Deploy to production

### Phase 4: Observability (📋 Planned)

- Configure OpenTelemetry span tagging
- Update Grafana dashboards to show correlation ID
- Create CloudWatch Logs Insights saved queries
- Train support team on correlation ID usage

---

## Monitoring & Metrics

### Key Metrics

1. **Correlation ID Coverage:** % of requests with correlation ID
   - Target: 100% (gateway should always inject)

2. **Correlation ID Propagation:** % of downstream calls with correlation ID
   - Target: 100% (all RestClient/WebClient calls)

3. **Error Response Inclusion:** % of errors with correlationId field
   - Target: 100% (all error handlers)

### Alerts

**Alert: Missing Correlation ID**

```
Condition: log entry without correlationId field in structured logs
Threshold: > 1% of requests
Action: Page on-call engineer
```

**Alert: Correlation ID Not Propagated**

```
Condition: downstream service logs without correlationId
Threshold: > 5% of inter-service calls
Action: Slack notification to dev channel
```

---

## References

- **Architecture:** ADR-0011 Gateway-based Security Architecture
- **Backend Contract:** `domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **Error Format:** `domains/security/.business-rules/ERROR_CODES.md`
- **Observability:** `docs/architecture/observability/OBSERVABILITY.md`
- **Related Issues:**
  - [durion-positivity-backend#417](https://github.com/louisburroughs/durion-positivity-backend/issues/417) - Gateway-based authentication
  - [durion-moqui-frontend#280](https://github.com/louisburroughs/durion-moqui-frontend/issues/280) - Moqui assertion issuance

---

## Appendix: Standards Compliance

### HTTP/1.1 Compliance

- Header name follows RFC 2616 token syntax
- Header value is quoted-string or token
- Case-insensitive header name matching

### OpenTelemetry Semantic Conventions

- Use `http.correlation_id` for span attribute
- Use `correlation.id` as secondary attribute for custom queries
- Link correlation ID to trace ID in span context

### Cloud-Native Best Practices

- Generate correlation ID at edge (gateway/ingress)
- Propagate via request headers (not in request body)
- Include in response headers for client verification
- Store in MDC for thread-local access
- Clear MDC on request completion to prevent leakage

---

**Last Updated:** 2026-02-01 14:30:00 UTC  
**Maintained By:** Durion Platform Team
