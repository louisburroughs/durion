# ADR-0020: Centralized Document Creation via pos-documents Service

**Status:** ACCEPTED  
**Date:** 2026-02-19  
**Deciders:** Architecture Team, Backend Lead  
**Affected Issues:** [Document creation capability]

---

## Context

As the Durion platform expands, multiple modules need to generate documents (PDFs, reports, invoices, estimates, etc.). Without centralized governance, modules were at risk of:

- Implementing ad-hoc document rendering logic independently (code duplication)
- Creating inconsistent formatting and styling across documents
- Managing conflicting dependencies (different PDF libraries, template engines)
- Difficulty scaling document generation as volume increases
- Lack of centralized audit trail and monitoring for document creation

Multiple domains require document generation capabilities. We needed a clear, scalable pattern that enforces architectural boundaries while allowing modules to customize document appearance.

---

## Decision

✅ **Resolved** — Establish **pos-documents as the single, mandatory service for all document creation** across the Durion platform. No module shall implement its own document creation functionality.

### 1. Document Creation Pattern

**Decision:** ✅ **Resolved** — All document creation must go through pos-documents service via REST API (`POST /api/documents/render`). Modules do NOT create documents directly; they request document generation from pos-documents.

**Details:**

- The `RenderRequest` API accepts:
  - `format`: Document format (PDF, etc.)
  - `templateId`: Identifier for the HTML template
  - `content`: Data/payload for template binding
- pos-documents returns rendered document as binary PDF
- Each document creation emits a `DOCUMENT_RENDER` event for audit and observability

### 2. Template Registration and Management

**Decision:** ✅ **Resolved** — Modules must register HTML templates with pos-documents using the template registration API at startup. Re-register templates on every service restart to ensure freshness.

**Why this pattern:**

- Decouples template definitions from service code
- Allows template updates without module redeployment
- Enables centralized template versioning and control
- Mirrors the event registration pattern already established in the codebase (`pos-events`)

**Implementation approach:**

1. Modules define HTML templates as classpath resources (e.g., `resources/templates/invoice.html`)
2. During service startup (in `@Component` ApplicationRunner), modules invoke the pos-documents template registration API
3. Templates are stored/indexed in pos-documents for lookup by `templateId`
4. When rendering, modules reference the template by `templateId` in the `RenderRequest`

**Template Format:**

- HTML with Handlebars-style template syntax (`${variableName}`, `{{#if}}`, `{{#each}}`)
- Bound dynamically with JSON context data during rendering
- iText HTML2PDF converts final HTML to PDF

### 3. Cross-Module Boundaries

**Decision:** ✅ **Resolved** — Document creation is pos-documents' exclusive responsibility. Modules consuming documents must use the REST API and never:

- Directly invoke PDF libraries (iText, Flying Saucer, etc.)
- Embed template rendering logic
- Manage template storage or caching
- Implement custom format handlers

This ensures architectural encapsulation and allows pos-documents to evolve independently.

### 4. Service Auto-Registration on Startup

**Decision:** ✅ **Resolved** — Implement an application startup initializer pattern (similar to `pos-events` event registration):

```java
@Component
public class DocumentTemplateInitializer implements ApplicationRunner {
    private final RestClient restClient;
    
    @Override
    public void run(ApplicationArguments args) {
        // Load templates defined in this module
        List<TemplateRegistration> templates = DocumentTemplates.all();
        // Register/re-register each template with pos-documents
        initializerSupport.registerTemplates(templates, this::registerTemplate);
    }
    
    private void registerTemplate(TemplateRegistration registration) {
        restClient.put()
            .uri("/api/documents/templates/{id}", registration.getTemplateId())
            .body(registration)
            .retrieve()
            .toBodilessEntity();
    }
}
```

This ensures templates are always in sync with the running service and avoids manual registration overhead.

---

## Alternatives Considered

1. **Each module implements document generation independently**
   - ❌ Rejected: Leads to code duplication, inconsistent styling, harder to maintain and evolve

2. **Shared library for document utilities**
   - ❌ Rejected: Still allows modules to invoke PDF libraries directly; doesn't enforce boundaries

3. **Pre-registration of templates (no re-registration)**
   - ❌ Rejected: Makes templates immutable after deployment; harder to support hot-reloads or updates; inconsistent with the event registration pattern already established

4. **Template files embedded in service pods**
   - ❌ Rejected: Templates become tightly coupled to individual services; difficult to update or share across modules

---

## Consequences

### Positive ✅

- **Single source of truth**: pos-documents is the only service that knows how to render documents
- **Consistent styling**: All modules use the same rendering engine and template syntax
- **Audit trail**: All document creation is logged through `DOCUMENT_RENDER` events with full traceability
- **Scalability**: Document rendering can be scaled/optimized independently without touching other modules
- **Maintainability**: No PDF library dependencies scattered across modules; single place to upgrade/patch
- **Observability**: Centralized metrics for document generation latency, volume, error rates
- **Extensibility**: New formats (HTML, CSV export, email templates) can be added in pos-documents without touching other services
- **Architectural clarity**: Clear, enforceable boundary between document orchestration (modules) and document rendering (pos-documents)

### Negative ⚠️ and Mitigations

- **Additional network hop**: Module → pos-documents adds latency (mitigated by: local network, fast rendering, caching strategies in future)
- **Template versioning complexity**: Updates to templates must be coordinated (mitigated by: semantic versioning of template IDs, re-registration on startup, clear template contracts in documentation)
- **Soft coupling via REST**: Modules depend on pos-documents API stability (mitigated by: versioning, backwards-compatible API changes, circuit breakers)
- **Startup dependency**: If pos-documents is unavailable at module startup, template registration may fail (mitigated by: graceful degradation, retry logic, clear error messages in logs)

### Neutral

- Templates stored in application code (resources/): Modules checkpoint template definitions in git, supporting code review and change tracking

---

## Implementation Notes

### 1. Required Components

- **pos-documents service**: Must expose template registration API (PUT `/api/documents/templates/{templateId}`)
- **pos-documents service**: Must expose document render API (POST `/api/documents/render`)
- **pos-document-helper library**: Shared module providing:
  - `TemplateRegistration` DTO: Structure for registering templates (templateId, content, version, metadata)
  - `RenderRequest` DTO: Structure for document rendering requests
  - `DocumentClient`: RestClient wrapper with retry/circuit breaker
  - `DocumentTemplateInitializer`: Base class for ApplicationRunner that registers templates at startup
  - `DocumentTemplateInitializerSupport`: Helper for batch registration with error handling
  - `TemplateUtils`: Utility methods for template validation and loading
  - Exception hierarchy: `DocumentHelperException`, `TemplateRegistrationException`, `DocumentRenderException`
- **Template definition file**: Per-module registry (e.g., `com.positivity.{module}.DocumentTemplates`) listing all templates

### 2. Configuration

```yaml
# application.yml for each consuming module
pos:
  documents:
    base-url: http://pos-documents:8080  # or from Eureka service discovery
    max-template-size: 1MB
    template-registration-timeout: 10s
```

### 3. Testing Strategy

- **Unit tests**: Verify template syntax and data binding logic locally (mock TemplateService)
- **Integration tests**: Use Testcontainers to spin up pos-documents; verify end-to-end rendering
- **Contract tests**: Ensure modules and pos-documents agree on schema (template request/response)
- **Resilience tests**: Verify graceful handling when pos-documents is unavailable

### 4. Metrics & Monitoring

Register document rendering in observability:

- `DOCUMENT_RENDER` event type with performance thresholds (p50=200ms, p95=1s, p99=3s)
- Trace attributes: `document.template_id`, `document.format`, `document.size_bytes`, `document.rendering_duration_ms`
- Alerting: Page if document rendering p99 latency exceeds 5 seconds or error rate > 1%
- Dashboard: Document rendering volume, latency percentiles, error breakdown by template/format

---

## Implementaion Guide

1. Add dependency on `pos-document-helper` to module pom.xml
2. Put HTML templates on classpath resources (e.g., `src/main/resources/templates/`)
3. Create `{Module}DocumentTemplates` registry class listing all templates
4. Create `{Module}TemplateInitializer` extending `DocumentTemplateInitializer` to register at startup
5. Replace internal PDF generation calls with `DocumentClient.renderDocument()`
6. Remove PDF library dependencies from pom.xml (handled by pos-documents)
7. Update tests to mock `DocumentClient` responses

### Example Implementation

```java
// 1. Add dependency
<dependency>
    <groupId>com.positivity</groupId>
    <artifactId>pos-document-helper</artifactId>
    <version>${project.version}</version>
</dependency>

// 2. Define templates registry
public final class WorkorderDocumentTemplates {
    public static List<TemplateRegistration> all() {
        return List.of(
            TemplateUtils.templateBuilder("WORKORDER_STANDARD", "pos-workorder")
                .content(loadTemplate("templates/workorder.html"))
                .description("Standard work order template")
                .version("1.0.0")
                .build()
        );
    }
}

// 3. Create initializer
@Component
public class WorkorderTemplateInitializer extends DocumentTemplateInitializer {
    public WorkorderTemplateInitializer(
            RestClient.Builder builder,
            @Value("${pos.documents.base-url}") String baseUrl) {
        super(builder, baseUrl, "pos-workorder");
    }
    
    @Override
    protected List<TemplateRegistration> loadTemplates() {
        return WorkorderDocumentTemplates.all();
    }
}

// 4. Use DocumentClient for rendering
@Service
public class WorkorderPdfService {
    private final DocumentClient documentClient;
    
    public byte[] generatePdf(Workorder workorder) {
        RenderRequest request = RenderRequest.builder()
            .format(DocumentFormat.PDF)
            .templateId("WORKORDER_STANDARD")
            .content(toJson(workorder))
            .build();
        return documentClient.renderDocument(request);
    }
}
```

---

## References

- **Related Issues**: Document creation requirements across workorder, accounting, RCIT workflows
- **Related ADRs**:
  - [ADR-0018: Audit Actor Fields from Security Context](0018-audit-actor-fields-from-security-context.adr.md) — Event logging patterns
  - [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md) — Service-to-service communication security
- **Related Documentation**:
  - [pos-documents Service](../../durion-positivity-backend/pos-documents)
  - [pos-document-helper Library](../../durion-positivity-backend/pos-document-helper) — Shared helper library for template registration and rendering
  - [pos-events Event Registration Pattern](../../durion-positivity-backend/pos-events) — Mirrors the template registration approach
  - [Observability Guide](../architecture/observability/OBSERVABILITY.md) — Document rendering telemetry
- **External Resources**:
  - [iText HTML2PDF Documentation](https://itextpdf.com/en/products/itext-html2pdf)
  - [Handlebars.js Template Syntax](https://handlebarsjs.com/)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | [Pending] | 2026-02-19 | Under review |
| Backend Lead | [Pending] | TBD | Awaiting approval |
| SRE/Observability | [Pending] | TBD | Metrics & monitoring plan approved |

---

## Timeline

- **Proposed**: 2026-02-19
- **Under Review**: [Awaiting feedback]
- **Accepted**: [TBD]
- **Implementation Started**: [TBD]
- **Implementation Complete**: [TBD]
- **Deployed to Production**: [TBD]

---

## Changelog

- **2026-02-19**: Initial draft based on pos-documents analysis; template registration pattern aligned with pos-events precedent
