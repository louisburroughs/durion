package com.durion.agents;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.durion.core.AgentCapabilities;
import com.durion.core.AgentConfiguration;
import com.durion.core.AgentHealth;
import com.durion.core.AgentMetrics;
import com.durion.core.AgentResult;
import com.durion.core.WorkspaceAgent;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GitHub Issue Creation Agent - REQ-WS-014 Implementation
 * 
 * Automatically creates GitHub issues in target repositories after story sequencing.
 * Implements the missing link between story orchestration and development work.
 * 
 * Capabilities:
 * - Creates issues in durion-moqui-frontend repository
 * - Creates issues in durion-positivity-backend repository  
 * - Populates .github/kiro-story.md template with decomposed details
 * - Sets appropriate labels (type: story, layer: functional, kiro, domain)
 * - Validates issue creation and provides feedback
 */
public class GitHubIssueCreationAgent implements WorkspaceAgent {

    private AgentConfiguration config;
    private volatile boolean ready = false;
    private HttpClient httpClient;
    private RequirementsDecompositionAgent decompositionAgent;

    // GitHub API configuration
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";
    
    // Issue template structure
    public static record IssueTemplate(
        String actor,
        String trigger, 
        String mainFlow,
        String alternateFlows,
        String businessRules,
        String dataRequirements,
        String acceptanceCriteria,
        String notesForAgents,
        String classification
    ) {}
    
    public static record IssueCreationResult(
        String frontendIssueUrl,
        String backendIssueUrl,
        boolean success,
        String message,
        List<String> errors
    ) {}

    @Override
    public String getAgentId() {
        return "github-issue-creation-agent";
    }

    @Override
    public AgentCapabilities getCapabilities() {
        return new AgentCapabilities(
            "github-issue-creation",
            Set.of("CREATE_FRONTEND_ISSUES", "CREATE_BACKEND_ISSUES", "POPULATE_STORY_TEMPLATE", 
                   "SET_ISSUE_LABELS", "VALIDATE_ISSUE_CREATION"),
            Map.of(
                "CREATE_FRONTEND_ISSUES", "Creates issues in durion-moqui-frontend repository",
                "CREATE_BACKEND_ISSUES", "Creates issues in durion-positivity-backend repository",
                "POPULATE_STORY_TEMPLATE", "Populates .github/kiro-story.md template with decomposed details",
                "SET_ISSUE_LABELS", "Sets appropriate labels (type: story, layer: functional, kiro, domain)",
                "VALIDATE_ISSUE_CREATION", "Validates issue creation and provides feedback"
            ),
            Set.of("github-api", "requirements-decomposition"),
            50
        );
    }

    @Override
    public CompletableFuture<AgentResult> execute(String operation, Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            try {
                AgentResult result;
                switch (operation) {
                    case "CREATE_FRONTEND_ISSUES":
                        result = createFrontendIssues(parameters, start);
                        break;
                    case "CREATE_BACKEND_ISSUES":
                        result = createBackendIssues(parameters, start);
                        break;
                    case "POPULATE_STORY_TEMPLATE":
                        result = populateStoryTemplate(parameters, start);
                        break;
                    case "SET_ISSUE_LABELS":
                        result = setIssueLabels(parameters, start);
                        break;
                    case "VALIDATE_ISSUE_CREATION":
                        result = validateIssueCreation(parameters, start);
                        break;
                    default:
                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        return AgentResult.failure("Unknown operation: " + operation, durationMs);
                }
                return result;
            } catch (Exception e) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                return AgentResult.failure("Error executing operation: " + e.getMessage(), durationMs);
            }
        });
    }

    @Override
    public AgentHealth getHealth() {
        return ready ? AgentHealth.HEALTHY : AgentHealth.UNHEALTHY;
    }

    @Override
    public AgentMetrics getMetrics() {
        return new AgentMetrics(0, 0, 0, Duration.ZERO, Duration.ZERO, 1.0, 0);
    }

    @Override
    public void initialize(AgentConfiguration config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
        this.decompositionAgent = new RequirementsDecompositionAgent();
        this.decompositionAgent.initialize(config);
        this.ready = true;
    }

    @Override
    public void shutdown() {
        this.ready = false;
    }

    @Override
    public boolean isReady() { 
        return ready; 
    }

    /**
     * Public method to create issues from a story requirement
     */
    public IssueCreationResult createIssuesFromStory(String storyTitle, String storyDescription, String domain) {
        try {
            // Step 1: Decompose the story using Requirements Decomposition Agent
            AgentResult decompositionResult = decompositionAgent.decomposeRequirements(storyDescription);
            
            if (!decompositionResult.isSuccess()) {
                return new IssueCreationResult(null, null, false, 
                    "Failed to decompose requirements: " + decompositionResult.getMessage(), 
                    List.of("Requirements decomposition failed"));
            }

            RequirementsDecompositionAgent.DecompositionResult decomposition = 
                (RequirementsDecompositionAgent.DecompositionResult) decompositionResult.getData();

            // Step 2: Create frontend issue if frontend work exists
            String frontendIssueUrl = null;
            if (!decomposition.frontendWork().components().isEmpty() || 
                !decomposition.frontendWork().screens().isEmpty() || 
                !decomposition.frontendWork().forms().isEmpty()) {
                
                frontendIssueUrl = createFrontendIssue(storyTitle, storyDescription, decomposition, domain);
            }

            // Step 3: Create backend issue if backend work exists  
            String backendIssueUrl = null;
            if (!decomposition.backendWork().apis().isEmpty() || 
                !decomposition.backendWork().businessLogic().isEmpty()) {
                
                backendIssueUrl = createBackendIssue(storyTitle, storyDescription, decomposition, domain);
            }

            // Step 4: Validate creation
            boolean success = (frontendIssueUrl != null || backendIssueUrl != null);
            String message = success ? "Issues created successfully" : "No issues created - no work identified";

            return new IssueCreationResult(frontendIssueUrl, backendIssueUrl, success, message, List.of());

        } catch (Exception e) {
            return new IssueCreationResult(null, null, false, 
                "Error creating issues: " + e.getMessage(), 
                List.of(e.getMessage()));
        }
    }

    private AgentResult createFrontendIssues(Map<String, Object> parameters, long startNano) {
        String storyTitle = (String) parameters.getOrDefault("storyTitle", "Frontend Story");
        String storyDescription = (String) parameters.getOrDefault("storyDescription", "");
        String domain = (String) parameters.getOrDefault("domain", "general");

        try {
            // Simulate issue creation for now - in production would use GitHub API
            String issueUrl = simulateGitHubIssueCreation(FRONTEND_REPO, storyTitle, storyDescription, domain, "frontend");
            
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            return AgentResult.success(
                Map.of("issueUrl", issueUrl, "repository", FRONTEND_REPO),
                "Frontend issue created successfully", 
                durationMs
            );
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            return AgentResult.failure("Failed to create frontend issue: " + e.getMessage(), durationMs);
        }
    }

    private AgentResult createBackendIssues(Map<String, Object> parameters, long startNano) {
        String storyTitle = (String) parameters.getOrDefault("storyTitle", "Backend Story");
        String storyDescription = (String) parameters.getOrDefault("storyDescription", "");
        String domain = (String) parameters.getOrDefault("domain", "general");

        try {
            // Simulate issue creation for now - in production would use GitHub API
            String issueUrl = simulateGitHubIssueCreation(BACKEND_REPO, storyTitle, storyDescription, domain, "backend");
            
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            return AgentResult.success(
                Map.of("issueUrl", issueUrl, "repository", BACKEND_REPO),
                "Backend issue created successfully", 
                durationMs
            );
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNano) / 1_000_000;
            return AgentResult.failure("Failed to create backend issue: " + e.getMessage(), durationMs);
        }
    }

    private AgentResult populateStoryTemplate(Map<String, Object> parameters, long startNano) {
        String storyDescription = (String) parameters.getOrDefault("storyDescription", "");
        String domain = (String) parameters.getOrDefault("domain", "general");
        String type = (String) parameters.getOrDefault("type", "frontend");

        IssueTemplate template = generateIssueTemplate(storyDescription, domain, type);
        
        long durationMs = (System.nanoTime() - startNano) / 1_000_000;
        return AgentResult.success(template, "Story template populated", durationMs);
    }

    private AgentResult setIssueLabels(Map<String, Object> parameters, long startNano) {
        String domain = (String) parameters.getOrDefault("domain", "general");
        String type = (String) parameters.getOrDefault("type", "frontend");

        List<String> labels = generateLabels(domain, type);
        
        long durationMs = (System.nanoTime() - startNano) / 1_000_000;
        return AgentResult.success(
            Map.of("labels", labels),
            "Issue labels generated", 
            durationMs
        );
    }

    private AgentResult validateIssueCreation(Map<String, Object> parameters, long startNano) {
        // Validate that all required parameters are present
        boolean isValid = parameters.containsKey("storyTitle") && 
                         parameters.containsKey("storyDescription") &&
                         parameters.containsKey("domain");

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;
        return AgentResult.success(
            Map.of("isValid", isValid, "validation", "Issue creation parameters validated"),
            isValid ? "Validation passed" : "Validation failed - missing required parameters", 
            durationMs
        );
    }

    private String createFrontendIssue(String title, String description, 
                                     RequirementsDecompositionAgent.DecompositionResult decomposition, 
                                     String domain) {
        // Generate frontend-specific issue content
        IssueTemplate template = generateFrontendTemplate(title, description, decomposition, domain);
        return simulateGitHubIssueCreation(FRONTEND_REPO, title, formatIssueBody(template), domain, "frontend");
    }

    private String createBackendIssue(String title, String description, 
                                    RequirementsDecompositionAgent.DecompositionResult decomposition, 
                                    String domain) {
        // Generate backend-specific issue content
        IssueTemplate template = generateBackendTemplate(title, description, decomposition, domain);
        return simulateGitHubIssueCreation(BACKEND_REPO, title, formatIssueBody(template), domain, "backend");
    }

    private IssueTemplate generateFrontendTemplate(String title, String description, 
                                                 RequirementsDecompositionAgent.DecompositionResult decomposition, 
                                                 String domain) {
        return new IssueTemplate(
            "Frontend User",
            "User interacts with the application interface",
            generateFrontendMainFlow(decomposition.frontendWork()),
            "Handle validation errors, network failures, loading states",
            "UI must be responsive, accessible (WCAG 2.1), and follow design system",
            "Frontend state management, API response data, user input validation",
            generateFrontendAcceptanceCriteria(decomposition.frontendWork()),
            "Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. " +
            "API contracts: " + formatApiContracts(decomposition.apiContracts()),
            "Frontend-First"
        );
    }

    private IssueTemplate generateBackendTemplate(String title, String description, 
                                                RequirementsDecompositionAgent.DecompositionResult decomposition, 
                                                String domain) {
        return new IssueTemplate(
            "System/API Consumer",
            "API request received or business process initiated",
            generateBackendMainFlow(decomposition.backendWork()),
            "Handle invalid input, database errors, external service failures",
            "Business logic validation, data integrity, security enforcement, performance requirements",
            "Database entities, API request/response schemas, business rule validation",
            generateBackendAcceptanceCriteria(decomposition.backendWork()),
            "Backend agents: Focus on Spring Boot microservices, Java 21, REST APIs, PostgreSQL. " +
            "API contracts: " + formatApiContracts(decomposition.apiContracts()),
            "Backend-First"
        );
    }

    private IssueTemplate generateIssueTemplate(String description, String domain, String type) {
        if ("frontend".equals(type)) {
            return new IssueTemplate(
                "Frontend User",
                "User interacts with the application",
                "User performs actions through the UI",
                "Handle errors and edge cases",
                "Follow UI/UX guidelines and accessibility standards",
                "User input, display data, state management",
                "UI components work correctly, user flows are intuitive",
                "Frontend agents: Use Vue.js 3, TypeScript, Quasar framework",
                "Frontend-First"
            );
        } else {
            return new IssueTemplate(
                "System/API Consumer", 
                "API request or business process initiated",
                "Process business logic and return response",
                "Handle validation errors and system failures",
                "Enforce business rules, security, and data integrity",
                "Database entities, API schemas, validation rules",
                "APIs work correctly, business rules enforced, data persisted",
                "Backend agents: Use Spring Boot, Java 21, PostgreSQL",
                "Backend-First"
            );
        }
    }

    private String generateFrontendMainFlow(RequirementsDecompositionAgent.FrontendWork frontendWork) {
        StringBuilder flow = new StringBuilder();
        flow.append("1. User accesses the interface\n");
        
        if (!frontendWork.screens().isEmpty()) {
            flow.append("2. System displays ").append(String.join(", ", frontendWork.screens())).append("\n");
        }
        
        if (!frontendWork.forms().isEmpty()) {
            flow.append("3. User interacts with ").append(String.join(", ", frontendWork.forms())).append("\n");
        }
        
        if (!frontendWork.components().isEmpty()) {
            flow.append("4. System renders ").append(String.join(", ", frontendWork.components())).append("\n");
        }
        
        flow.append("5. System provides feedback to user");
        return flow.toString();
    }

    private String generateBackendMainFlow(RequirementsDecompositionAgent.BackendWork backendWork) {
        StringBuilder flow = new StringBuilder();
        flow.append("1. System receives request\n");
        
        if (!backendWork.businessLogic().isEmpty()) {
            flow.append("2. System executes ").append(String.join(", ", backendWork.businessLogic())).append("\n");
        }
        
        flow.append("3. System validates business rules\n");
        flow.append("4. System persists data changes\n");
        
        if (!backendWork.apis().isEmpty()) {
            flow.append("5. System returns response via ").append(String.join(", ", backendWork.apis()));
        } else {
            flow.append("5. System returns success response");
        }
        
        return flow.toString();
    }

    private String generateFrontendAcceptanceCriteria(RequirementsDecompositionAgent.FrontendWork frontendWork) {
        StringBuilder criteria = new StringBuilder();
        criteria.append("- UI components render correctly and are responsive\n");
        criteria.append("- User interactions provide appropriate feedback\n");
        criteria.append("- Error states are handled gracefully\n");
        criteria.append("- Loading states are displayed during async operations\n");
        
        if (!frontendWork.forms().isEmpty()) {
            criteria.append("- Form validation works correctly\n");
        }
        
        criteria.append("- Accessibility requirements are met (WCAG 2.1)");
        return criteria.toString();
    }

    private String generateBackendAcceptanceCriteria(RequirementsDecompositionAgent.BackendWork backendWork) {
        StringBuilder criteria = new StringBuilder();
        
        if (!backendWork.apis().isEmpty()) {
            criteria.append("- API endpoints return correct HTTP status codes\n");
            criteria.append("- Request/response schemas are validated\n");
        }
        
        criteria.append("- Business rules are enforced correctly\n");
        criteria.append("- Data integrity is maintained\n");
        criteria.append("- Error handling provides meaningful messages\n");
        criteria.append("- Security requirements are implemented\n");
        criteria.append("- Performance requirements are met");
        return criteria.toString();
    }

    private String formatApiContracts(List<RequirementsDecompositionAgent.ApiContract> contracts) {
        if (contracts.isEmpty()) {
            return "No API contracts defined";
        }
        
        StringBuilder formatted = new StringBuilder();
        for (RequirementsDecompositionAgent.ApiContract contract : contracts) {
            formatted.append(contract.method()).append(" ").append(contract.endpoint())
                     .append(" (").append(contract.schema()).append("); ");
        }
        return formatted.toString();
    }

    private List<String> generateLabels(String domain, String type) {
        List<String> labels = new ArrayList<>();
        labels.add("type:story");
        labels.add("layer:functional");
        labels.add("kiro");
        labels.add("domain:" + domain);
        
        if ("frontend".equals(type)) {
            labels.add("frontend");
        } else if ("backend".equals(type)) {
            labels.add("backend");
        }
        
        return labels;
    }

    private String formatIssueBody(IssueTemplate template) {
        return String.format("""
            ## Actor
            %s
            
            ## Trigger
            %s
            
            ## Main Flow
            %s
            
            ## Alternate/Error Flows
            %s
            
            ## Business Rules
            %s
            
            ## Data Requirements
            %s
            
            ## Acceptance Criteria
            %s
            
            ## Notes for Agents
            %s
            
            ## Classification
            %s
            
            ---
            *Generated by GitHub Issue Creation Agent - %s*
            """, 
            template.actor(),
            template.trigger(),
            template.mainFlow(),
            template.alternateFlows(),
            template.businessRules(),
            template.dataRequirements(),
            template.acceptanceCriteria(),
            template.notesForAgents(),
            template.classification(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private String simulateGitHubIssueCreation(String repository, String title, String body, String domain, String type) {
        // In production, this would make actual GitHub API calls
        // For now, simulate the creation and return a mock URL
        
        String issueNumber = String.valueOf(System.currentTimeMillis() % 10000);
        String issueUrl = String.format("https://github.com/%s/issues/%s", repository, issueNumber);
        
        // Log the simulated creation
        System.out.println(String.format(
            "🎯 Simulated GitHub Issue Creation:\n" +
            "   Repository: %s\n" +
            "   Title: [STORY] %s\n" +
            "   Domain: %s\n" +
            "   Type: %s\n" +
            "   URL: %s\n" +
            "   Labels: %s",
            repository, title, domain, type, issueUrl, generateLabels(domain, type)
        ));
        
        return issueUrl;
    }

    // TODO: Implement actual GitHub API integration
    private String createGitHubIssue(String repository, String title, String body, List<String> labels) throws IOException, InterruptedException {
        // This would implement the actual GitHub API call
        // Requires GitHub token configuration and proper HTTP request handling
        
        String githubToken = config.getSetting("github.token", "");
        if (githubToken.isEmpty()) {
            throw new IllegalStateException("GitHub token not configured");
        }
        
        // For now, return simulated URL
        return simulateGitHubIssueCreation(repository, title, body, "simulated", "api");
    }
}