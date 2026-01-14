---
name: 'Java MCP Server Development Guidelines'
description: 'Best practices and patterns for building Model Context Protocol (MCP) servers in Java using the official MCP Java SDK with reactive streams, integrated with Spring Boot microservices.'
applyTo: "**/*.java"
---

# Java MCP Server Development Guidelines for Spring Boot Microservices

When building MCP servers in Java for the durion-positivity-backend Spring Boot microservices, follow these best practices and patterns using the official Java SDK integrated with Spring Boot architecture.

## Dependencies

Add the MCP Java SDK to your Spring Boot module's `pom.xml`:

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.14.1</version>
</dependency>
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>0.14.1</version>
</dependency>
```

This project uses Maven for build management. See the parent [pom.xml](../../../pom.xml) for dependency management.

## Spring Boot Module Structure

Organize your MCP server within a `pos-*` Spring Boot module following the project conventions:

```
pos-mcp-server/
├── src/main/java/
│   └── com/durion/pos/mcp/
│       ├── McpServerApplication.java
│       ├── controller/       # REST endpoints
│       │   └── McpController.java
│       ├── service/         # Business logic
│       │   ├── McpServerService.java
│       │   └── ToolHandlerService.java
│       ├── repository/      # Spring Data JPA
│       │   └── McpResourceRepository.java
│       ├── model/           # JPA entities
│       │   └── McpResource.java
│       ├── config/          # Spring configuration
│       │   ├── McpServerConfig.java
│       │   └── SecurityConfig.java
│       └── tools/           # MCP tool implementations
│           ├── SearchTool.java
│           └── QueryTool.java
├── src/main/resources/
│   ├── application.yml
│   └── application-dev.yml
├── src/test/java/
│   └── com/durion/pos/mcp/
│       └── McpServerTest.java
└── pom.xml
```

## Server Setup with Spring Boot

Create an MCP server as a Spring Boot service:

```java
import io.mcp.server.McpServer;
import io.mcp.server.McpServerBuilder;
import io.mcp.server.transport.StdioServerTransport;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;

@Service
public class McpServerService {
    private final McpServer server;
    private final ToolHandlerService toolHandlerService;
    
    @Autowired
    public McpServerService(ToolHandlerService toolHandlerService) {
        this.toolHandlerService = toolHandlerService;
        this.server = McpServerBuilder.builder()
            .serverInfo("pos-mcp-server", "1.0.0")
            .capabilities(capabilities -> capabilities
                .tools(true)
                .resources(true)
                .prompts(true))
            .build();
    }
    
    @PostConstruct
    public void registerTools() {
        toolHandlerService.registerTools(server);
    }
    
    public void start() {
        StdioServerTransport transport = new StdioServerTransport();
        server.start(transport).subscribe();
    }
}
```

## Adding Repository Query Tools

Query Spring Data JPA repositories as tools:

```java
import io.mcp.server.tool.Tool;
import io.mcp.json.JsonSchema;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class QueryToolHandler {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    
    @Autowired
    public QueryToolHandler(ProductRepository productRepository, 
                           OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }
    
    public Tool buildQueryTool() {
        return Tool.builder()
            .name("query_repository")
            .description("Query Spring Data JPA repositories")
            .inputSchema(JsonSchema.object()
                .property("entityType", JsonSchema.string()
                    .description("Entity type (e.g., Product, Order)")
                    .required(true))
                .property("filters", JsonSchema.object()
                    .description("Filter criteria as key-value pairs")
                    .additionalProperties(true))
                .property("limit", JsonSchema.integer()
                    .description("Maximum results")
                    .defaultValue(10)))
            .build();
    }
    
    public Mono<ToolResponse> handleQuery(JsonNode arguments) {
        String entityType = arguments.get("entityType").asText();
        int limit = arguments.has("limit") 
            ? arguments.get("limit").asInt() 
            : 10;
        
        return Mono.fromCallable(() -> {
            List<?> results;
            
            switch (entityType) {
                case "Product":
                    results = productRepository.findAll().stream()
                        .limit(limit)
                        .collect(Collectors.toList());
                    break;
                case "Order":
                    results = orderRepository.findAll().stream()
                        .limit(limit)
                        .collect(Collectors.toList());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown entity type: " + entityType);
            }
            
            return ToolResponse.success()
                .addTextContent("Found " + results.size() + " records of " + entityType)
                .addTextContent(results.toString())
                .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

## Adding Spring Service Tools

Expose Spring services as MCP tools:

```java
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
public class ServiceToolHandler {
    private final ProductService productService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    
    @Autowired
    public ServiceToolHandler(ProductService productService,
                             OrderService orderService,
                             InventoryService inventoryService) {
        this.productService = productService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }
    
    public Tool buildServiceCallTool() {
        return Tool.builder()
            .name("call_service")
            .description("Call a Spring service method")
            .inputSchema(JsonSchema.object()
                .property("serviceName", JsonSchema.string()
                    .description("Service name (e.g., ProductService, OrderService)")
                    .required(true))
                .property("methodName", JsonSchema.string()
                    .description("Method name (e.g., findById, create)")
                    .required(true))
                .property("parameters", JsonSchema.object()
                    .description("Method parameters as key-value pairs")
                    .additionalProperties(true)))
            .build();
    }
    
    public Mono<ToolResponse> handleServiceCall(JsonNode arguments) {
        String serviceName = arguments.get("serviceName").asText();
        String methodName = arguments.get("methodName").asText();
        
        return Mono.fromCallable(() -> {
            Object result;
            
            switch (serviceName) {
                case "ProductService":
                    result = invokeProductService(methodName, arguments);
                    break;
                case "OrderService":
                    result = invokeOrderService(methodName, arguments);
                    break;
                case "InventoryService":
                    result = invokeInventoryService(methodName, arguments);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown service: " + serviceName);
            }
            
            return ToolResponse.success()
                .addTextContent("Service executed successfully")
                .addTextContent(result.toString())
                .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    private Object invokeProductService(String method, JsonNode args) {
        JsonNode params = args.get("parameters");
        switch (method) {
            case "findById":
                return productService.findById(params.get("id").asText());
            case "findAll":
                return productService.findAll();
            default:
                throw new IllegalArgumentException("Unknown method: " + method);
        }
    }
}
```

## Adding JPA Entity Resources

Expose Spring Data JPA entities as resources:

```java
import io.mcp.server.resource.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;

@Component
public class EntityResourceHandler {
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public EntityResourceHandler(EntityManager entityManager,
                                ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }
    
    // Register resource list handler - list all JPA entities
    public Mono<List<Resource>> listEntityResources() {
        return Mono.fromCallable(() -> {
            List<Resource> resources = new ArrayList<>();
            
            // List available JPA entities
            Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
            entities.forEach(entity -> {
                String entityName = entity.getName();
                resources.add(Resource.builder()
                    .name(entityName)
                    .uri("entity://" + entityName)
                    .description("JPA Entity: " + entityName)
                    .mimeType("application/json")
                    .build());
            });
            
            return resources;
        });
    }
    
    // Register resource read handler - get entity metadata
    public Mono<ResourceContent> readEntityResource(String uri) {
        if (uri.startsWith("entity://")) {
            String entityName = uri.substring(9);
            
            return Mono.fromCallable(() -> {
                EntityType<?> entityType = entityManager.getMetamodel()
                    .getEntities()
                    .stream()
                    .filter(e -> e.getName().equals(entityName))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(uri));
                
                Map<String, Object> entityDef = new HashMap<>();
                entityDef.put("name", entityType.getName());
                entityDef.put("javaType", entityType.getJavaType().getName());
                entityDef.put("attributes", entityType.getAttributes().stream()
                    .map(attr -> Map.of(
                        "name", attr.getName(),
                        "type", attr.getJavaType().getSimpleName()))
                    .collect(Collectors.toList()));
                
                return ResourceContent.json(
                    objectMapper.writeValueAsString(entityDef), 
                    uri);
            });
        }
        throw new ResourceNotFoundException(uri);
    }
}
```

## Adding Spring Service Prompts

Define prompts for common Spring Boot operations:

```java
import io.mcp.server.prompt.Prompt;
import io.mcp.server.prompt.PromptMessage;
import org.springframework.stereotype.Component;

@Component
public class ServicePromptHandler {
    
    // Register service usage prompts
    public Mono<List<Prompt>> listPrompts() {
        List<Prompt> prompts = List.of(
            Prompt.builder()
                .name("query_repository")
                .description("Query a Spring Data JPA repository")
                .argument(PromptArgument.builder()
                    .name("entityType")
                    .description("Entity type (e.g., Product, Order, Customer)")
                    .required(true)
                    .build())
                .argument(PromptArgument.builder()
                    .name("filters")
                    .description("Filter criteria")
                    .required(false)
                    .build())
                .build(),
            Prompt.builder()
                .name("call_service")
                .description("Call a Spring service method")
                .argument(PromptArgument.builder()
                    .name("serviceName")
                    .description("Service name (e.g., ProductService, OrderService)")
                    .required(true)
                    .build())
                .argument(PromptArgument.builder()
                    .name("methodName")
                    .description("Method name (e.g., findById, create)")
                    .required(true)
                    .build())
                .argument(PromptArgument.builder()
                    .name("parameters")
                    .description("Method parameters")
                    .required(false)
                    .build())
                .build(),
            Prompt.builder()
                .name("agent_framework_query")
                .description("Query the pos-agent-framework for guidance")
                .argument(PromptArgument.builder()
                    .name("agentType")
                    .description("Agent type (architecture, implementation, testing, etc.)")
                    .required(true)
                    .build())
                .argument(PromptArgument.builder()
                    .name("context")
                    .description("Context for the agent query")
                    .required(true)
                    .build())
                .build()
        );
        return Mono.just(prompts);
    }
    
    // Implement prompt handlers
    public Mono<PromptResult> handlePrompt(String name, Map<String, String> arguments) {
        if (name.equals("query_repository")) {
            String entityType = arguments.get("entityType");
            String filters = arguments.getOrDefault("filters", "");
            
            String prompt = String.format(
                "Query the %s repository with filters: %s\n\n" +
                "Use the query_repository tool to search for records.\n" +
                "Available modules: pos-inventory, pos-order, pos-customer, pos-accounting",
                entityType, filters);
            
            List<PromptMessage> messages = List.of(
                PromptMessage.user(prompt)
            );
            
            return Mono.just(PromptResult.builder()
                .description("Query " + entityType + " repository")
                .messages(messages)
                .build());
        }
        throw new PromptNotFoundException(name);
    }
}
```

## Integration with pos-agent-framework

Integrate your MCP server with the existing pos-agent-framework:

```java
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AgentFrameworkIntegration {
    private final AgentManager agentManager;
    private final AgentRegistry agentRegistry;
    
    @Autowired
    public AgentFrameworkIntegration(AgentManager agentManager,
                                    AgentRegistry agentRegistry) {
        this.agentManager = agentManager;
        this.agentRegistry = agentRegistry;
    }
    
    public Tool buildAgentQueryTool() {
        return Tool.builder()
            .name("query_agent")
            .description("Query the pos-agent-framework for guidance")
            .inputSchema(JsonSchema.object()
                .property("agentType", JsonSchema.string()
                    .description("Agent type: architecture, implementation, testing, security, etc.")
                    .required(true))
                .property("context", JsonSchema.object()
                    .description("Context for the agent query")
                    .additionalProperties(true)
                    .required(true)))
            .build();
    }
    
    public Mono<ToolResponse> handleAgentQuery(JsonNode arguments) {
        String agentType = arguments.get("agentType").asText();
        JsonNode context = arguments.get("context");
        
        return Mono.fromCallable(() -> {
            AgentRequest request = AgentRequest.builder()
                .type(agentType)
                .context(context)
                .build();
            
            AgentResponse response = agentManager.processRequest(request);
            
            return ToolResponse.success()
                .addTextContent("Agent guidance: " + response.getGuidance())
                .addTextContent("Recommendations: " + response.getRecommendations())
                .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * Register MCP tools with available agents
     * Available agents: architecture, implementation, deployment, testing,
     * security, observability, documentation, business-domain, integration-gateway,
     * pair-programming-navigator, event-driven-architecture, cicd-pipeline,
     * configuration-management, resilience-engineering
     */
    public void registerAgentTools(McpServer server) {
        agentRegistry.getAvailableAgents().forEach(agent -> {
            Tool tool = Tool.builder()
                .name("agent_" + agent.getName())
                .description(agent.getDescription())
                .inputSchema(agent.getInputSchema())
                .build();
            
            server.addToolHandler("agent_" + agent.getName(), 
                args -> handleAgentQuery(agent.getName(), args));
        });
    }
}
```

## REST API Integration

Expose MCP tools via Spring Boot REST controller:

```java
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/mcp")
public class McpController {
    
    private final McpServerService mcpServerService;
    private final ToolHandlerService toolHandlerService;
    
    @Autowired
    public McpController(McpServerService mcpServerService,
                        ToolHandlerService toolHandlerService) {
        this.mcpServerService = mcpServerService;
        this.toolHandlerService = toolHandlerService;
    }
    
    @PostMapping("/tool")
    public ResponseEntity<Map<String, Object>> callTool(
            @RequestBody ToolRequest request) {
        
        String toolName = request.getToolName();
        JsonNode parameters = request.getParameters();
        
        ToolResponse response = toolHandlerService
            .handleTool(toolName, parameters)
            .block();
        
        return ResponseEntity.ok(Map.of(
            "success", !response.isError(),
            "content", response.getContent()
        ));
    }
    
    @GetMapping("/tools")
    public ResponseEntity<List<Tool>> listTools() {
        List<Tool> tools = toolHandlerService.getAvailableTools();
        return ResponseEntity.ok(tools);
    }
    
    @GetMapping("/resources")
    public ResponseEntity<List<Resource>> listResources() {
        List<Resource> resources = mcpServerService
            .listResources()
            .block();
        return ResponseEntity.ok(resources);
    }
}

@Data
class ToolRequest {
    private String toolName;
    private JsonNode parameters;
}
```

## JPA Entity Metadata Integration

Expose JPA entity metadata through your MCP server:

```java
import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import org.springframework.stereotype.Component;

@Component
public class EntityMetadataService {
    private final EntityManager entityManager;
    
    @Autowired
    public EntityMetadataService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    public void discoverEntitiesAsMcpResources(List<Resource> resources) {
        // Get all JPA entity types from the metamodel
        Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
        
        entities.forEach(entity -> {
            String entityName = entity.getName();
            
            // Register as MCP resource
            Resource resource = Resource.builder()
                .name(entityName)
                .uri("entity://" + entityName)
                .description(String.format(
                    "JPA Entity: %s (Module: %s)",
                    entityName,
                    getModuleForEntity(entityName)))
                .mimeType("application/json")
                .build();
            
            resources.add(resource);
        });
    }
    
    private String getModuleForEntity(String entityName) {
        // Map entity to its parent module based on package
        if (entityName.contains("Product") || entityName.contains("Catalog")) {
            return "pos-catalog";
        } else if (entityName.contains("Order")) {
            return "pos-order";
        } else if (entityName.contains("Inventory")) {
            return "pos-inventory";
        } else if (entityName.contains("Customer")) {
            return "pos-customer";
        } else if (entityName.contains("Accounting") || entityName.contains("Audit")) {
            return "pos-accounting";
        }
        return "unknown";
    }
}
```

## Testing with Spring Boot Test

Write tests using Spring Boot Test framework:

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class McpServerTest {
    
    @Autowired
    private McpServerService mcpServerService;
    
    @Autowired
    private ToolHandlerService toolHandlerService;
    
    @Test
    void testRepositoryQueryTool() {
        // Verify tool exists
        assertThat(toolHandlerService.getAvailableTools())
            .extracting(Tool::getName)
            .contains("query_repository");
        
        // Test repository query
        JsonNode args = objectMapper.createObjectNode()
            .put("entityType", "Product")
            .put("limit", 5);
        
        ToolResponse response = toolHandlerService
            .handleTool("query_repository", args)
            .block();
        
        assertThat(response.isError()).isFalse();
        assertThat(response.getContent()).isNotEmpty();
    }
    
    @Test
    void testServiceCallTool() {
        JsonNode args = objectMapper.createObjectNode()
            .put("serviceName", "ProductService")
            .put("methodName", "findAll");
        
        ToolResponse response = toolHandlerService
            .handleTool("call_service", args)
            .block();
        
        assertThat(response.isError()).isFalse();
    }
    
    @Test
    void testAgentFrameworkIntegration() {
        JsonNode args = objectMapper.createObjectNode()
            .put("agentType", "architecture")
            .set("context", objectMapper.createObjectNode()
                .put("domain", "inventory"));
        
        ToolResponse response = toolHandlerService
            .handleTool("query_agent", args)
            .block();
        
        assertThat(response.isError()).isFalse();
        assertThat(response.getContent()).isNotEmpty();
    }
}
```

## Spring Boot Configuration

Add MCP server configuration to your application.yml:

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: pos-mcp-server
  datasource:
    url: jdbc:postgresql://localhost:5432/pos_mcp
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

mcp:
  server:
    enabled: true
    name: pos-mcp-server
    version: 1.0.0
    capabilities:
      tools: true
      resources: true
      prompts: true
    transport:
      type: stdio  # or http for REST

# Integration with pos-agent-framework
agent:
  framework:
    enabled: true
    discovery:
      enabled: true
      url: http://localhost:8080/api/agents

server:
  port: 8090

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

## Common Spring Boot MCP Patterns

### Access Spring Services

```java
// Call a Spring service from MCP tool
server.addToolHandler("create_order", (args) -> {
    return Mono.fromCallable(() -> {
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(args.get("customerId").asText())
            .productId(args.get("productId").asText())
            .quantity(args.get("quantity").asInt())
            .build();
        
        Order order = orderService.createOrder(request);
        
        return ToolResponse.success()
            .addTextContent("Order created: " + order.getId())
            .addTextContent("Total: " + order.getTotal())
            .build();
    }).subscribeOn(Schedulers.boundedElastic());
});
```

### Query with Spring Data JPA

```java
// Use Spring Data JPA repositories
Page<Product> results = productRepository.findAll(
    ProductSpecifications.statusEquals("ACTIVE"),
    PageRequest.of(0, limit, Sort.by("name")));

// Or use @Query methods
List<Product> products = productRepository
    .findByStatusAndCategory("ACTIVE", category, PageRequest.of(0, limit));
```

### Access Spring Cache

```java
// Use Spring's caching abstraction
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Cacheable(value = "mcp-tool-cache", key = "#cacheKey")
public ToolResponse getCachedResult(String cacheKey) {
    return expensiveOperation();
}

@CacheEvict(value = "mcp-tool-cache", key = "#cacheKey")
public void invalidateCache(String cacheKey) {
    // Cache will be cleared
}
```

### Logging with Spring Boot

```java
// Use SLF4J with Spring Boot's logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

log.info("MCP Tool called: {}", toolName);
log.debug("Arguments: {}", arguments);
log.error("Error processing tool", exception);
```

## Error Handling for Spring Boot

Handle Spring Boot-specific exceptions:

```java
import org.springframework.dao.DataAccessException;
import org.springframework.web.client.RestClientException;
import javax.persistence.EntityNotFoundException;

server.addToolHandler("safe_operation", (args) -> {
    return Mono.fromCallable(() -> {
        try {
            String entityType = args.get("entityType").asText();
            List<?> results = repositoryService.findAll(entityType);
            
            return ToolResponse.success()
                .addTextContent("Success: " + results.size() + " records")
                .build();
        } catch (EntityNotFoundException e) {
            log.error("Entity not found", e);
            return ToolResponse.error()
                .message("Entity not found: " + e.getMessage())
                .build();
        } catch (DataAccessException e) {
            log.error("Database operation failed", e);
            return ToolResponse.error()
                .message("Database error: " + e.getMessage())
                .build();
        } catch (RestClientException e) {
            log.error("External service call failed", e);
            return ToolResponse.error()
                .message("Service communication error: " + e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ToolResponse.error()
                .message("Internal error occurred")
                .build();
        }
    }).subscribeOn(Schedulers.boundedElastic());
});
```

## Best Practices for Spring Boot MCP

1. **Follow Module Conventions** - Place code in controller/, service/, repository/, model/, config/ as per project standards
2. **Use Spring Dependency Injection** - Leverage @Autowired and constructor injection
3. **Implement Service Layer** - Keep controllers thin, business logic in services
4. **Cache with Spring Cache** - Use @Cacheable, @CacheEvict for performance
5. **Security** - Integrate with pos-security-service via API Gateway for authentication/authorization
6. **Logging** - Use SLF4J with Spring Boot's logging configuration
7. **Transactions** - Use @Transactional for proper transaction management
8. **Entity Relationships** - Expose JPA entities and relationships as MCP resources
9. **Async Operations** - Use @Async or bounded elastic scheduler for non-blocking operations
10. **Documentation** - Document service methods and REST endpoints with JavaDoc and OpenAPI
11. **Testing** - Write comprehensive tests using Spring Boot Test, MockMvc, and TestContainers
12. **Configuration** - Use application.yml for environment-specific settings
13. **Agent Framework Integration** - Leverage pos-agent-framework for intelligent guidance
14. **Module Independence** - Each pos-* module should be independently deployable
15. **Event Emission** - Follow pos-accounting patterns for emitting domain events

## Additional Maven Dependencies

Ensure your Spring Boot module's `pom.xml` includes these additional MCP dependencies if not already present:

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-core</artifactId>
    <version>0.14.1</version>
</dependency>
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-transport-stdio</artifactId>
    <version>0.14.1</version>
</dependency>
```

See the parent [pom.xml](../../../pom.xml) for centralized dependency management.

## Server Setup

Create an MCP server using the builder pattern:

```java
import io.mcp.server.McpServer;
import io.mcp.server.McpServerBuilder;
import io.mcp.server.transport.StdioServerTransport;

McpServer server = McpServerBuilder.builder()
    .serverInfo("my-server", "1.0.0")
    .capabilities(capabilities -> capabilities
        .tools(true)
        .resources(true)
        .prompts(true))
    .build();

// Start with stdio transport
StdioServerTransport transport = new StdioServerTransport();
server.start(transport).subscribe();
```

## Adding Tools

Register tool handlers with the server:

```java
import io.mcp.server.tool.Tool;
import io.mcp.server.tool.ToolHandler;
import reactor.core.publisher.Mono;

// Define a tool
Tool searchTool = Tool.builder()
    .name("search")
    .description("Search for information")
    .inputSchema(JsonSchema.object()
        .property("query", JsonSchema.string()
            .description("Search query")
            .required(true))
        .property("limit", JsonSchema.integer()
            .description("Maximum results")
            .defaultValue(10)))
    .build();

// Register tool handler
server.addToolHandler("search", (arguments) -> {
    String query = arguments.get("query").asText();
    int limit = arguments.has("limit") 
        ? arguments.get("limit").asInt() 
        : 10;
    
    // Perform search
    List<String> results = performSearch(query, limit);
    
    return Mono.just(ToolResponse.success()
        .addTextContent("Found " + results.size() + " results")
        .build());
});
```

## Adding Resources

Implement resource handlers for data access:

```java
import io.mcp.server.resource.Resource;
import io.mcp.server.resource.ResourceHandler;

// Register resource list handler
server.addResourceListHandler(() -> {
    List<Resource> resources = List.of(
        Resource.builder()
            .name("Data File")
            .uri("resource://data/example.txt")
            .description("Example data file")
            .mimeType("text/plain")
            .build()
    );
    return Mono.just(resources);
});

// Register resource read handler
server.addResourceReadHandler((uri) -> {
    if (uri.equals("resource://data/example.txt")) {
        String content = loadResourceContent(uri);
        return Mono.just(ResourceContent.text(content, uri));
    }
    throw new ResourceNotFoundException(uri);
});

// Register resource subscribe handler
server.addResourceSubscribeHandler((uri) -> {
    subscriptions.add(uri);
    log.info("Client subscribed to {}", uri);
    return Mono.empty();
});
```

## Adding Prompts

Implement prompt handlers for templated conversations:

```java
import io.mcp.server.prompt.Prompt;
import io.mcp.server.prompt.PromptMessage;
import io.mcp.server.prompt.PromptArgument;

// Register prompt list handler
server.addPromptListHandler(() -> {
    List<Prompt> prompts = List.of(
        Prompt.builder()
            .name("analyze")
            .description("Analyze a topic")
            .argument(PromptArgument.builder()
                .name("topic")
                .description("Topic to analyze")
                .required(true)
                .build())
            .argument(PromptArgument.builder()
                .name("depth")
                .description("Analysis depth")
                .required(false)
                .build())
            .build()
    );
    return Mono.just(prompts);
});

// Register prompt get handler
server.addPromptGetHandler((name, arguments) -> {
    if (name.equals("analyze")) {
        String topic = arguments.getOrDefault("topic", "general");
        String depth = arguments.getOrDefault("depth", "basic");
        
        List<PromptMessage> messages = List.of(
            PromptMessage.user("Please analyze this topic: " + topic),
            PromptMessage.assistant("I'll provide a " + depth + " analysis of " + topic)
        );
        
        return Mono.just(PromptResult.builder()
            .description("Analysis of " + topic + " at " + depth + " level")
            .messages(messages)
            .build());
    }
    throw new PromptNotFoundException(name);
});
```

## Reactive Streams Pattern

The Java SDK uses Reactive Streams (Project Reactor) for asynchronous processing:

```java
// Return Mono for single results
server.addToolHandler("process", (args) -> {
    return Mono.fromCallable(() -> {
        String result = expensiveOperation(args);
        return ToolResponse.success()
            .addTextContent(result)
            .build();
    }).subscribeOn(Schedulers.boundedElastic());
});

// Return Flux for streaming results
server.addResourceListHandler(() -> {
    return Flux.fromIterable(getResources())
        .map(r -> Resource.builder()
            .uri(r.getUri())
            .name(r.getName())
            .build())
        .collectList();
});
```

## Synchronous Facade

For blocking use cases, use the synchronous API:

```java
import io.mcp.server.McpSyncServer;

McpSyncServer syncServer = server.toSyncServer();

// Blocking tool handler
syncServer.addToolHandler("greet", (args) -> {
    String name = args.get("name").asText();
    return ToolResponse.success()
        .addTextContent("Hello, " + name + "!")
        .build();
});
```

## Transport Configuration

### Stdio Transport

For local subprocess communication:

```java
import io.mcp.server.transport.StdioServerTransport;

StdioServerTransport transport = new StdioServerTransport();
server.start(transport).block();
```

### HTTP Transport (Servlet)

For HTTP-based servers:

```java
import io.mcp.server.transport.ServletServerTransport;
import jakarta.servlet.http.HttpServlet;

public class McpServlet extends HttpServlet {
    private final McpServer server;
    private final ServletServerTransport transport;
    
    public McpServlet() {
        this.server = createMcpServer();
        this.transport = new ServletServerTransport();
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        transport.handleRequest(server, req, resp).block();
    }
}
```

## Spring Boot Integration

Use the Spring Boot starter for seamless integration:

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>0.14.1</version>
</dependency>
```

Configure the server with Spring:

```java
import org.springframework.context.annotation.Configuration;
import io.mcp.spring.McpServerConfigurer;

@Configuration
public class McpConfiguration {
    
    @Bean
    public McpServerConfigurer mcpServerConfigurer() {
        return server -> server
            .serverInfo("spring-server", "1.0.0")
            .capabilities(cap -> cap
                .tools(true)
                .resources(true)
                .prompts(true));
    }
}
```

Register handlers as Spring beans:

```java
import org.springframework.stereotype.Component;
import io.mcp.spring.ToolHandler;

@Component
public class SearchToolHandler implements ToolHandler {
    
    @Override
    public String getName() {
        return "search";
    }
    
    @Override
    public Tool getTool() {
        return Tool.builder()
            .name("search")
            .description("Search for information")
            .inputSchema(JsonSchema.object()
                .property("query", JsonSchema.string().required(true)))
            .build();
    }
    
    @Override
    public Mono<ToolResponse> handle(JsonNode arguments) {
        String query = arguments.get("query").asText();
        return Mono.just(ToolResponse.success()
            .addTextContent("Search results for: " + query)
            .build());
    }
}
```

## Error Handling

Use proper error handling with MCP exceptions:

```java
server.addToolHandler("risky", (args) -> {
    return Mono.fromCallable(() -> {
        try {
            String result = riskyOperation(args);
            return ToolResponse.success()
                .addTextContent(result)
                .build();
        } catch (ValidationException e) {
            return ToolResponse.error()
                .message("Invalid input: " + e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ToolResponse.error()
                .message("Internal error occurred")
                .build();
        }
    });
});
```

## JSON Schema Construction

Use the fluent schema builder:

```java
import io.mcp.json.JsonSchema;

JsonSchema schema = JsonSchema.object()
    .property("name", JsonSchema.string()
        .description("User's name")
        .minLength(1)
        .maxLength(100)
        .required(true))
    .property("age", JsonSchema.integer()
        .description("User's age")
        .minimum(0)
        .maximum(150))
    .property("email", JsonSchema.string()
        .description("Email address")
        .format("email")
        .required(true))
    .property("tags", JsonSchema.array()
        .items(JsonSchema.string())
        .uniqueItems(true))
    .additionalProperties(false)
    .build();
```

## Logging and Observability

Use SLF4J for logging:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(MyMcpServer.class);

server.addToolHandler("process", (args) -> {
    log.info("Tool called: process, args: {}", args);
    
    return Mono.fromCallable(() -> {
        String result = process(args);
        log.debug("Processing completed successfully");
        return ToolResponse.success()
            .addTextContent(result)
            .build();
    }).doOnError(error -> {
        log.error("Processing failed", error);
    });
});
```

Propagate context with Reactor:

```java
import reactor.util.context.Context;

server.addToolHandler("traced", (args) -> {
    return Mono.deferContextual(ctx -> {
        String traceId = ctx.get("traceId");
        log.info("Processing with traceId: {}", traceId);
        
        return Mono.just(ToolResponse.success()
            .addTextContent("Processed")
            .build());
    });
});
```

## Testing

Write tests using the synchronous API:

```java
import org.junit.jupiter.api.Test;
import static org.assertj.core.Assertions.assertThat;

class McpServerTest {
    
    @Test
    void testToolHandler() {
        McpServer server = createTestServer();
        McpSyncServer syncServer = server.toSyncServer();
        
        JsonNode args = objectMapper.createObjectNode()
            .put("query", "test");
        
        ToolResponse response = syncServer.callTool("search", args);
        
        assertThat(response.isError()).isFalse();
        assertThat(response.getContent()).hasSize(1);
    }
}
```

## Jackson Integration

The SDK uses Jackson for JSON serialization. Customize as needed:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());

// Use custom mapper with server
McpServer server = McpServerBuilder.builder()
    .objectMapper(mapper)
    .build();
```

## Content Types

Support multiple content types in responses:

```java
import io.mcp.server.content.Content;

server.addToolHandler("multi", (args) -> {
    return Mono.just(ToolResponse.success()
        .addTextContent("Plain text response")
        .addImageContent(imageBytes, "image/png")
        .addResourceContent("resource://data", "application/json", jsonData)
        .build());
});
```

## Server Lifecycle

Properly manage server lifecycle:

```java
import reactor.core.Disposable;

Disposable serverDisposable = server.start(transport).subscribe();

// Graceful shutdown
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    log.info("Shutting down MCP server");
    serverDisposable.dispose();
    server.stop().block();
}));
```

## Common Patterns

### Request Validation

```java
server.addToolHandler("validate", (args) -> {
    if (!args.has("required_field")) {
        return Mono.just(ToolResponse.error()
            .message("Missing required_field")
            .build());
    }
    
    return processRequest(args);
});
```

### Async Operations

```java
server.addToolHandler("async", (args) -> {
    return Mono.fromCallable(() -> callExternalApi(args))
        .timeout(Duration.ofSeconds(30))
        .onErrorResume(TimeoutException.class, e -> 
            Mono.just(ToolResponse.error()
                .message("Operation timed out")
                .build()))
        .subscribeOn(Schedulers.boundedElastic());
});
```

### Resource Caching

```java
private final Map<String, String> cache = new ConcurrentHashMap<>();

server.addResourceReadHandler((uri) -> {
    return Mono.fromCallable(() -> 
        cache.computeIfAbsent(uri, this::loadResource))
        .map(content -> ResourceContent.text(content, uri));
});
```

## Best Practices

1. **Use Reactive Streams** for async operations and backpressure
2. **Leverage Spring Boot** starter for enterprise applications
3. **Implement proper error handling** with specific error messages
4. **Use SLF4J** for logging, not System.out
5. **Validate inputs** in tool and prompt handlers
6. **Support graceful shutdown** with proper resource cleanup
7. **Use bounded elastic scheduler** for blocking operations
8. **Propagate context** for observability in reactive chains
9. **Test with synchronous API** for simplicity
10. **Follow Java naming conventions** (camelCase for methods, PascalCase for classes)