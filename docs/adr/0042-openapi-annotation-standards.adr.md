# ADR: OpenAPI Annotation Standards for Backend Services

**Status:** ACCEPTED  
**Date:** 2026-05-12  
**Context:** MCP Server OpenAPI-driven tool discovery + backend service documentation consistency

---

## Decision

All Durion backend REST APIs MUST follow comprehensive OpenAPI annotation standards to ensure:

1. **Discoverability:** MCP Server can auto-discover and register tools
2. **Documentation:** Clear, consistent API documentation for developers and LLMs
3. **Security:** Permission-based tool filtering and authorization
4. **Quality:** Validation and enforcement via CI/CD pipeline

## Amendment (2026-07-08 — [ADR-0044](0044-platform-event-only-domain-walls.adr.md))

- **Scope note:** this ADR covers synchronous REST contracts only. Asynchronous event contracts (topics, envelopes, payloads per ADR-0044 §3) are out of OpenAPI scope; they live as versioned DTOs in the `pos-domain-events` library. AsyncAPI adoption may be proposed as a separate ADR.
- **Enforcement inventory additions:** `pos-people-contact` (new module) and pos-vehicle-inventory's newly frontend-facing registry write endpoints must join the OpenAPI enforcement waves (mirror this in `durion-positivity-backend/docs/adr-0042-openapi-rollout-baseline.md`).

---

## Required OpenAPI Annotations

### 1. Operation-Level Annotations (MANDATORY)

Every REST endpoint MUST include:

#### `summary` (REQUIRED)

- **Format:** Brief action description (5-10 words, title case)
- **Purpose:** Tool name generation, quick reference
- **Examples:**
  - ✅ "Create a new invoice"
  - ✅ "List customer orders"
  - ✅ "Update GL account details"
  - ❌ "createInvoice" (too technical)
  - ❌ "This endpoint creates invoices" (too verbose)

#### `description` (REQUIRED)

- **Format:** Structured tool invocation guide (4-8 sentences)
- **Purpose:** Unambiguous LLM tool selection and invocation — the description MUST be self-contained enough that an agent can call this tool correctly without reading any other documentation
- **Must Include ALL of the following:**
  1. **Primary action** — what the operation does in one declarative sentence
  2. **When to use** — the exact situation or trigger that should cause an agent to invoke this tool (disambiguate from similar operations)
  3. **Required preconditions** — system state or data that must exist before calling (e.g., the customer must be active, the order must be in SUBMITTED status)
  4. **Key input expectations** — which fields are mandatory, what formats/constraints apply, and any non-obvious default behaviours
  5. **Side effects** — what other state changes or events occur as a result (events emitted, related records updated)
  6. **Error conditions** — specific business rule violations that cause 422/409 with enough detail for the caller to self-correct
  7. **What NOT to do** — when to use a different tool instead (avoids mis-invocation of similar endpoints)
- **Examples:**

```java
@Operation(
    summary = "Create a new invoice",
    description = """
        Creates a new invoice for the specified customer and order.
        Use this tool when converting a completed, uninvoiced order into a billable invoice;
        do NOT use createCreditMemo or createDraftInvoice for this purpose.
        Preconditions: the customer must be active with valid billing terms, and the order
        must be in COMPLETED status and not already linked to an existing invoice.
        Required inputs: customerId (UUID) and orderId (UUID); dueDate defaults to customer
        billing terms if omitted and cannot be set to a past date.
        Emits an INVOICE_CREATED accounting event that triggers journal entry generation;
        the journal entry is created asynchronously — poll getJournalEntryByInvoiceId to confirm.
        Returns 409 if the order is already invoiced, 422 if credit limit is exceeded or
        billing terms are invalid — correct the customer record before retrying.
        """
)
```

```java
@Operation(
    summary = "Update GL account details",
    description = """
        Updates the name, description, or parent account of an existing general ledger account.
        Use this tool only to correct metadata on an existing account; to change account type
        or account number, the account must be closed and a new one created instead.
        Preconditions: the account must exist and its accountId must be known; use
        listGlAccounts or searchGlAccounts first if the ID is not available.
        Account number and type are immutable after creation and will be silently ignored
        if supplied in the request body.
        No events are emitted; the change takes effect immediately and is reflected in
        subsequent getGlAccount calls.
        Returns 409 if a circular hierarchy would result from a parent account change,
        and 422 if the account has posted journal entries that prevent the requested modification.
        """
)
```

#### `operationId` (REQUIRED)

- **Format:** Unique camelCase identifier within service
- **Naming Convention:** `{verb}{Resource}[Qualifier]`
- **Examples:**
  - ✅ `createInvoice`
  - ✅ `getOrderById`
  - ✅ `listCustomers`
  - ✅ `updateGlAccount`
  - ✅ `deletePaymentById`
  - ❌ `create` (too vague)
  - ❌ `invoiceCreation` (not verb-first)
  - ❌ `POST_/v1/invoices` (not human-readable)

**Verb Conventions:**

- `create` → POST (new resource)
- `get` → GET (single resource by ID)
- `list` → GET (collection)
- `search` → GET (filtered collection)
- `update` → PUT/PATCH
- `delete` → DELETE
- `activate`, `deactivate`, `post`, `reverse` → POST (state changes)

#### `security` (REQUIRED for all operations)

- **Format:** Array of security requirements with scopes
- **Purpose:** Permission-based tool filtering
- **See Section 3: Security Annotations**

### 2. Parameter Annotations (REQUIRED)

Every parameter MUST include:

```java
@Parameter(
    name = "orderId",
    description = "Unique order identifier in UUID format. Must be an existing active order.",
    required = true,
    schema = @Schema(
        type = "string",
        format = "uuid",
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
)
@PathVariable UUID orderId
```

**Required fields:**

- `name`: Parameter name (matches code)
- `description`: Clear explanation including format, constraints, business rules
- `required`: Boolean (explicit)
- `schema.type`: Data type (string, integer, number, boolean, array, object)
- `schema.format`: Specific format (uuid, date, date-time, email, etc.)
- `schema.example`: Representative example value

**Description Standards:**

- State format/type clearly
- Include validation constraints (min, max, pattern)
- Mention business rules or references
- 1-2 sentences maximum

**Query Parameter Examples:**

```java
@Parameter(
    name = "status",
    description = "Filter by invoice status. Accepts: DRAFT, POSTED, PAID, VOID. Defaults to all statuses if omitted.",
    required = false,
    schema = @Schema(type = "string", allowableValues = {"DRAFT", "POSTED", "PAID", "VOID"})
)
```

### 3. Request Body Annotations (REQUIRED)

```java
@RequestBody(
    description = "Invoice creation request with line items and billing details",
    required = true,
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = CreateInvoiceRequest.class),
        examples = @ExampleObject(
            name = "Basic invoice",
            value = """
                {
                  "customerId": "123e4567-e89b-12d3-a456-426614174000",
                  "orderId": "234e5678-e89b-12d3-a456-426614174001",
                  "lineItems": [
                    {
                      "productId": "345e6789-e89b-12d3-a456-426614174002",
                      "quantity": 2,
                      "unitPrice": 49.99
                    }
                  ]
                }
                """
        )
    )
)
```

**Required:**

- `description`: What the request body represents
- `required`: Boolean
- `content.schema.implementation`: DTO class reference
- `examples`: At least one realistic example

### 4. Response Annotations (REQUIRED)

Document ALL response codes:

```java
@ApiResponses(value = {
    @ApiResponse(
        responseCode = "201",
        description = "Invoice created successfully",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = InvoiceResponse.class)
        )
    ),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request payload. Check field validation errors.",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class)
        )
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Customer or order not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @ApiResponse(
        responseCode = "422",
        description = "Business rule violation: credit limit exceeded or invalid billing terms",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
```

**Required Response Codes:** | Code | When to Document | |------|------------------| | 200 | Successful GET/PUT/PATCH with body | | 201 | Successful POST (resource created) |
| 204 | Successful DELETE or POST with no body | | 400 | Malformed request, validation errors | | 401 | Authentication required/failed | | 403 | Authorized but insufficient
permissions | | 404 | Resource not found | | 409 | Conflict (duplicate, version mismatch) | | 422 | Valid request, business rule violation | | 500 | Internal server error
(optional) |

**Response Description Standards:**

- 200-level: State what was successful
- 400-level: Explain why request failed and how to fix
- Include specific error scenarios (not generic "bad request")

### 5. Schema/DTO Annotations (REQUIRED)

Document all DTO fields:

```java
@Schema(description = "Invoice creation request with customer, order, and line item details")
public class CreateInvoiceRequest {

    @Schema(
        description = "Customer identifier. Must be an active customer with valid billing terms.",
        required = true,
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    private UUID customerId;

    @Schema(
        description = "Order identifier. Must be a completed order not already invoiced.",
        required = true,
        example = "234e5678-e89b-12d3-a456-426614174001"
    )
    private UUID orderId;

    @Schema(
        description = "Invoice line items. Must contain at least one item.",
        required = true,
        minItems = 1
    )
    private List<InvoiceLineItem> lineItems;

    @Schema(
        description = "Invoice due date. Defaults to customer billing terms if omitted. Cannot be in the past.",
        required = false,
        example = "2026-06-15"
    )
    private LocalDate dueDate;
}
```

**Field-level requirements:**

- `description`: Clear explanation with business context
- `required`: Boolean
- `example`: Representative value
- Constraints: `minItems`, `maxItems`, `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`

## 6. Security Annotations (REQUIRED)

### Global Security Scheme

Every service MUST define security schemes:

```java
@OpenAPIDefinition(
    info = @Info(
        title = "Accounting Service API",
        version = "1.0.0",
        description = "Invoice, journal entry, and GL account management"
    ),
    security = @SecurityRequirement(name = "oauth2"),  // Global default
    servers = @Server(url = "/", description = "API Gateway")
)
@SecurityScheme(
    name = "oauth2",
    type = SecuritySchemeType.OAUTH2,
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "https://auth.positivity.com/oauth/authorize",
            tokenUrl = "https://auth.positivity.com/oauth/token",
            scopes = {
                @OAuthScope(name = "accounting:read", description = "Read accounting data"),
                @OAuthScope(name = "accounting:write", description = "Modify accounting records"),
                @OAuthScope(name = "accounting:delete", description = "Delete accounting records"),
                @OAuthScope(name = "admin:all", description = "Administrative access")
            }
        )
    )
)
public class OpenApiConfig { }
```

### Operation-Level Security

```java
@Operation(
    summary = "Create invoice",
    security = @SecurityRequirement(name = "oauth2", scopes = {"accounting:write"})
)
@PreAuthorize("hasAuthority('accounting:write')")  // Backend enforcement
@PostMapping("/v1/accounting/invoices")
public ResponseEntity<Invoice> createInvoice(...) { }
```

**Permission Scope Convention:** `{domain}:{action}`

- **Domains:** accounting, order, customer, inventory, billing, people, product, pricing, crm, location, workexec, vehicle, admin
- **Actions:** read, write, delete, admin, all

**Required by HTTP Method:** | Method | Required Scope | |--------|---------------| | GET | `{domain}:read` | | POST | `{domain}:write` | | PUT/PATCH | `{domain}:write` | |
DELETE | `{domain}:delete` | | Admin endpoints | `admin:all` |

## Complete Example

```java
@RestController
@RequestMapping("/v1/accounting/invoices")
@Tag(name = "Invoices", description = "Invoice creation and management")
public class InvoiceController {

    @Operation(
        summary = "Create a new invoice",
        description = """
            Creates a new invoice for the specified customer and order.
            Use this tool when converting a completed, uninvoiced order into a billable invoice;
            do NOT use createCreditMemo or createDraftInvoice for this purpose.
            Preconditions: the customer must be active with valid billing terms, and the order
            must be in COMPLETED status and not already linked to an existing invoice.
            Required inputs: customerId (UUID) and orderId (UUID); dueDate defaults to customer
            billing terms if omitted and cannot be set to a past date.
            Emits an INVOICE_CREATED accounting event that triggers journal entry generation;
            the journal entry is created asynchronously — poll getJournalEntryByInvoiceId to confirm.
            Returns 409 if the order is already invoiced, 422 if credit limit is exceeded or
            billing terms are invalid — correct the customer record before retrying.
            """,
        operationId = "createInvoice",
        security = @SecurityRequirement(name = "oauth2", scopes = {"accounting:write"})
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Invoice created successfully",
            content = @Content(schema = @Schema(implementation = InvoiceResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request payload. Check field validation errors.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Customer or order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Business rule violation: credit limit exceeded or invalid billing terms",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PreAuthorize("hasAuthority('accounting:write')")
    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
        @Parameter(
            description = "Invoice creation request with customer, order, and line items",
            required = true
        )
        @RequestBody @Valid CreateInvoiceRequest request
    ) {
        // Implementation
    }
}
```

## Validation and Enforcement

### CI/CD Pipeline Validation

**Automated checks (Maven/Gradle plugin or script):**

```bash
#!/bin/bash
# validate-openapi.sh

echo "Validating OpenAPI specifications..."

# 1. Check all operations have summary
missing_summary=$(yq eval '.paths.*.*.summary' openapi.yaml | grep -c '^null$')
if [ "$missing_summary" -gt 0 ]; then
    echo "ERROR: $missing_summary operations missing summary"
    exit 1
fi

# 2. Check all operations have description
missing_description=$(yq eval '.paths.*.*.description' openapi.yaml | grep -c '^null$')
if [ "$missing_description" -gt 0 ]; then
    echo "ERROR: $missing_description operations missing description"
    exit 1
fi

# 3. Check all operations have operationId
missing_operationId=$(yq eval '.paths.*.*.operationId' openapi.yaml | grep -c '^null$')
if [ "$missing_operationId" -gt 0 ]; then
    echo "ERROR: $missing_operationId operations missing operationId"
    exit 1
fi

# 4. Check all POST/PUT/PATCH/DELETE have security
insecure_mutations=$(yq eval '.paths.*.[post,put,patch,delete].security' openapi.yaml | grep -c '^null$')
if [ "$insecure_mutations" -gt 0 ]; then
    echo "ERROR: $insecure_mutations mutation operations missing security requirements"
    exit 1
fi

echo "✅ OpenAPI validation passed"
```

**Maven/Gradle Integration:**

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <configuration>
        <validateSpec>true</validateSpec>
        <strictSpec>true</strictSpec>
    </configuration>
</plugin>
```

### Backend Team Audit Checklist

For each service:

- [ ] All operations have `summary` (5-10 words, title case)
- [ ] All operations have `description` with all 7 required elements: primary action, when-to-use, preconditions, input expectations, side effects, error conditions, and when-not-to-use
- [ ] All operations have unique `operationId` (camelCase, verb-first)
- [ ] All operations have `security` requirements
- [ ] All parameters have `description` and `schema`
- [ ] All request bodies documented with examples
- [ ] All response codes documented (200-level, 400-level)
- [ ] All DTO fields have `@Schema` annotations
- [ ] Security scopes follow naming convention: `{domain}:{action}`
- [ ] Backend code includes `@PreAuthorize` checks (defense in depth)

### Aggregate Spec Validation

**Gateway team responsibilities:**

- Validate aggregate references resolve correctly
- Ensure no duplicate operationIds across services
- Verify all services follow security conventions
- Test aggregate spec parses without errors

## Migration Strategy

**Phase 1: Immediate (New APIs)**

- All new endpoints MUST follow standards from Day 1
- Code review blocks PRs without proper annotations

**Phase 2: Backend Sprint (Existing APIs)**

- Backend teams audit all existing operations (541 ops across 20 services)
- Fix missing summary/description/security annotations
- Target: 2-week sprint per team

**Phase 3: CI Enforcement**

- Enable validation in CI pipeline
- Block builds/PRs with missing annotations
- Weekly reports on compliance status

**Phase 4: MCP Integration**

- MCP Server validates required fields during tool registration
- Log warnings for operations with missing fields
- Skip registration for operations without security annotations
- Reject tool registration for descriptions that do not contain all 7 required elements
  (primary action, when-to-use, preconditions, input expectations, side effects, error conditions, when-not-to-use)
- Surface description quality score in the developer portal and MCP tool manifest

## Documentation Quality Standards

### Summary Guidelines

- **Length:** 5-10 words
- **Style:** Title case, action-oriented
- **Format:** Verb + object/resource
- **Examples:**
  - ✅ "Create a New Invoice"
  - ✅ "List Customer Orders"
  - ✅ "Update GL Account Details"
  - ❌ "createInvoice" (code-style)
  - ❌ "This is an endpoint that creates invoices" (too wordy)

### Description Guidelines

The `description` field is the **primary contract between the API and any agent invoking it**.
An agent MUST be able to call the endpoint correctly using only the `summary`, `description`,
and parameter/schema annotations — no other context should be required.

- **Length:** 4-8 sentences (concise but complete)
- **Tone:** Imperative and precise — written for an agent executor, not just a human reader
- **Audience:** LLM tool selection and invocation + human developers

**Mandatory Structure (all 7 elements REQUIRED):**

| # | Element | Instruction |
|---|---------|-------------|
| 1 | **Primary action** | One declarative sentence stating exactly what the operation does |
| 2 | **When to use** | The specific trigger or situation for invoking this tool; distinguish it from similar operations |
| 3 | **Preconditions** | Required system/data state before calling — if a prerequisite lookup is needed, name the tool |
| 4 | **Input expectations** | Mandatory vs. optional fields, formats, constraints, and non-obvious defaults |
| 5 | **Side effects** | All emitted events, async operations, or related record changes the caller must account for |
| 6 | **Error conditions** | Specific 4xx causes with enough detail that the agent can self-correct without further guidance |
| 7 | **When NOT to use** | The alternative tool(s) to invoke for adjacent operations; prevents mis-invocation |

**Template:**

```
[Primary action in 1 declarative sentence].
Use this tool when [specific trigger/situation]; do NOT use [alternative tool] for [contrast].
Preconditions: [required resource/state]; use [lookupTool] first if [resource ID] is unknown.
Required inputs: [field] ([type/format])[; optional: [field] defaults to [value] if omitted].
[Side effect description]; [async note — name follow-up tool if polling is needed].
Returns [HTTP code] if [specific condition] — [self-correction guidance for caller].
```

**Quality Examples:**

```java
// ✅ GOOD: Full invocation contract
description = """
    Creates a new invoice for the specified customer and order.
    Use this tool when converting a completed, uninvoiced order into a billable invoice;
    do NOT use createCreditMemo or createDraftInvoice for this purpose.
    Preconditions: the customer must be active with valid billing terms, and the order
    must be in COMPLETED status and not already linked to an existing invoice.
    Required inputs: customerId (UUID) and orderId (UUID); dueDate defaults to customer
    billing terms if omitted and cannot be set to a past date.
    Emits an INVOICE_CREATED accounting event that triggers journal entry generation;
    the journal entry is created asynchronously — poll getJournalEntryByInvoiceId to confirm.
    Returns 409 if the order is already invoiced, 422 if credit limit is exceeded or
    billing terms are invalid — correct the customer record before retrying.
    """

// ✅ GOOD: Full invocation contract with disambiguation
description = """
    Updates the name, description, or parent account of an existing general ledger account.
    Use this tool only to correct metadata on an existing account; to reclassify or renumber
    an account, close it and create a new one via createGlAccount instead.
    Preconditions: the account must exist; use listGlAccounts or searchGlAccounts first
    if the accountId is not already known.
    Required inputs: accountId (UUID); all other fields are optional and unchanged if omitted.
    Account number and type are immutable after creation — supplying them has no effect.
    No events are emitted; the change takes effect immediately.
    Returns 409 if a circular hierarchy would result, 422 if posted journal entries block
    the requested change — resolve outstanding entries before retrying.
    """

// ❌ BAD: Too vague — agent cannot determine when to invoke or how to self-correct
description = "Creates an invoice"

// ❌ BAD: Missing when-to-use, preconditions, and self-correction guidance
description = """
    Creates a new invoice for the specified customer and order.
    Validates billing terms and customer credit status before creation.
    Returns 422 if credit limit exceeded or billing terms invalid.
    """

// ❌ BAD: Implementation details instead of invocation contract
description = """
    Calls InvoiceService.createInvoice() which validates the request,
    checks credit limits in the database, and publishes an InvoiceCreatedEvent
    to Kafka topic accounting.invoice.created...
    """
```

## Consequences

**Positive:**

- ✅ Consistent, high-quality API documentation across all services
- ✅ MCP Server can auto-discover and document 541 operations
- ✅ LLM can invoke tools correctly without external context using structured descriptions
- ✅ Agents can self-correct on error without human intervention via explicit 4xx guidance
- ✅ Permission-based filtering prevents unauthorized actions
- ✅ Developers have comprehensive API reference
- ✅ CI validation catches missing annotations early
- ✅ Ambiguity between similar operations is eliminated by mandatory when-not-to-use clause

**Negative:**

- ⚠️ Backend teams must audit and annotate 541 existing operations
- ⚠️ Description requirements are more demanding (4-8 sentences with 7 required elements vs. 2-5 sentences)
- ⚠️ Additional maintenance overhead for annotations
- ⚠️ Learning curve for annotation standards
- ⚠️ Build failures if standards not met

**Mitigation:**

- Provide templates and copy-paste examples
- Offer tooling to generate skeleton annotations
- Phase rollout (new APIs first, existing APIs in sprint)
- Clear documentation and training
- Automated CI validation with helpful error messages

## Related ADRs

- ADR-0014: Gateway Internal Service Security
- ADR-0017: API Controller HTTP Response Codes
- ADR-0018: Audit Actor Fields from Security Context

## References

- OpenAPI Specification 3.1: https://spec.openapis.org/oas/v3.1.0
- Swagger Annotations: https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations
- Spring Boot OpenAPI: https://springdoc.org/
- OpenAPI Security: https://swagger.io/docs/specification/authentication/

---

**Status:** Proposed - Requires backend team approval  
**Implementation:** Week 1 of MCP tool discovery project  
**Owner:** Backend Architecture Team
