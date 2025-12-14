package durion.workspace.agents.coordination;

import durion.workspace.agents.core.*;
import durion.workspace.agents.monitoring.PerformanceMonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Manages coordination workflows between workspace agents and layer-specific agents.
 * Implements conflict resolution and performance monitoring for agent interactions.
 * 
 * Requirements: 1.1, 1.3 - Agent coordination and cross-project guidance
 */
public class AgentCoordinationMatrix {
    
    private final Map<String, WorkspaceAgent> registeredAgents;
    private final Map<String, Set<String>> coordinationDependencies;
    private final Map<String, AgentCoordinationRule> coordinationRules;
    private final ConflictResolver conflictResolver;
    private final PerformanceMonitor performanceMonitor;
    private final ExecutorService coordinationExecutor;
    
    // Performance tracking
    private final Map<String, CoordinationMetrics> coordinationMetrics;
    
    public AgentCoordinationMatrix(PerformanceMonitor performanceMonitor) {
        this.registeredAgents = new ConcurrentHashMap<>();
        this.coordinationDependencies = new ConcurrentHashMap<>();
        this.coordinationRules = new ConcurrentHashMap<>();
        this.conflictResolver = new ConflictResolver();
        this.performanceMonitor = performanceMonitor;
        this.coordinationExecutor = Executors.newFixedThreadPool(10);
        this.coordinationMetrics = new ConcurrentHashMap<>();
        
        initializeDefaultCoordinationRules();
    }
    
    /**
     * Registers an agent with the coordination matrix
     */
    public void registerAgent(WorkspaceAgent agent) {
        String agentId = agent.getAgentId();
        registeredAgents.put(agentId, agent);
        coordinationDependencies.put(agentId, new HashSet<>(agent.getCoordinationDependencies()));
        coordinationMetrics.put(agentId, new CoordinationMetrics(agentId));
        
        // Set performance monitor for the agent
        agent.setPerformanceMonitor(performanceMonitor);
    }
    
    /**
     * Processes a request through the coordination matrix, handling dependencies and conflicts
     */
    public CompletableFuture<CoordinationResult> processCoordinatedRequest(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Instant startTime = Instant.now();
            String coordinationId = UUID.randomUUID().toString();
            
            try {
                // Find suitable agents for the request
                List<WorkspaceAgent> candidateAgents = findCandidateAgents(request);
                
                if (candidateAgents.isEmpty()) {
                    return CoordinationResult.noAgentsAvailable(coordinationId, request);
                }
                
                // Select primary agent based on capability scores
                WorkspaceAgent primaryAgent = selectPrimaryAgent(candidateAgents, request);
                
                // Identify coordination dependencies
                Set<WorkspaceAgent> dependentAgents = identifyDependentAgents(primaryAgent, request);
                
                // Execute coordination workflow
                CoordinationWorkflow workflow = new CoordinationWorkflow(
                    coordinationId, primaryAgent, dependentAgents, request);
                
                CoordinationResult result = executeCoordinationWorkflow(workflow);
                
                // Record metrics
                Duration coordinationTime = Duration.between(startTime, Instant.now());
                recordCoordinationMetrics(coordinationId, coordinationTime, result.isSuccess());
                
                return result;
                
            } catch (Exception e) {
                Duration coordinationTime = Duration.between(startTime, Instant.now());
                recordCoordinationMetrics(coordinationId, coordinationTime, false);
                
                return CoordinationResult.error(coordinationId, request, 
                    "Coordination failed: " + e.getMessage());
            }
        }, coordinationExecutor);
    }
    
    /**
     * Finds candidate agents that can handle the request
     */
    private List<WorkspaceAgent> findCandidateAgents(AgentRequest request) {
        return registeredAgents.values().stream()
            .filter(agent -> agent.canHandleRequest(request))
            .filter(agent -> agent.getStatus().healthy())
            .collect(Collectors.toList());
    }
    
    /**
     * Selects the primary agent based on capability scores and performance
     */
    private WorkspaceAgent selectPrimaryAgent(List<WorkspaceAgent> candidates, AgentRequest request) {
        return candidates.stream()
            .max(Comparator.comparingInt(agent -> 
                calculateAgentScore(agent, request)))
            .orElseThrow(() -> new IllegalStateException("No suitable agent found"));
    }
    
    /**
     * Calculates agent score for request handling
     */
    private int calculateAgentScore(WorkspaceAgent agent, AgentRequest request) {
        int capabilityScore = agent.getCapabilityScore(request.getRequiredCapability());
        
        // Bonus for cross-project experience
        int crossProjectBonus = 0;
        if (request.isCrossProject()) {
            if (agent.getAgentType() == AgentType.WORKSPACE_COORDINATION ||
                agent.getAgentType() == AgentType.TECHNOLOGY_BRIDGE) {
                crossProjectBonus = 20;
            }
        }
        
        // Performance bonus based on recent success rate
        CoordinationMetrics metrics = coordinationMetrics.get(agent.getAgentId());
        int performanceBonus = (int) (metrics.getSuccessRate() * 10);
        
        return capabilityScore + crossProjectBonus + performanceBonus;
    }
    
    /**
     * Identifies dependent agents that should be involved in coordination
     */
    private Set<WorkspaceAgent> identifyDependentAgents(WorkspaceAgent primaryAgent, AgentRequest request) {
        Set<WorkspaceAgent> dependentAgents = new HashSet<>();
        
        // Get direct dependencies
        Set<String> dependencies = coordinationDependencies.get(primaryAgent.getAgentId());
        if (dependencies != null) {
            for (String depId : dependencies) {
                WorkspaceAgent depAgent = registeredAgents.get(depId);
                if (depAgent != null && depAgent.getStatus().healthy()) {
                    dependentAgents.add(depAgent);
                }
            }
        }
        
        // Add agents based on coordination rules
        for (AgentCoordinationRule rule : coordinationRules.values()) {
            if (rule.applies(primaryAgent, request)) {
                for (String requiredAgentId : rule.getRequiredAgents()) {
                    WorkspaceAgent requiredAgent = registeredAgents.get(requiredAgentId);
                    if (requiredAgent != null && requiredAgent.getStatus().healthy()) {
                        dependentAgents.add(requiredAgent);
                    }
                }
            }
        }
        
        return dependentAgents;
    }
    
    /**
     * Executes the coordination workflow
     */
    private CoordinationResult executeCoordinationWorkflow(CoordinationWorkflow workflow) {
        try {
            // Phase 1: Prepare dependent agents
            List<CompletableFuture<AgentResponse>> preparationFutures = new ArrayList<>();
            for (WorkspaceAgent agent : workflow.getDependentAgents()) {
                CompletableFuture<AgentResponse> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return agent.processRequest(workflow.createPreparationRequest());
                    } catch (AgentException e) {
                        return new AgentResponse(workflow.getRequest().getRequestId(), 
                            "Preparation failed: " + e.getMessage(), Instant.now());
                    }
                });
                preparationFutures.add(future);
            }
            
            // Wait for preparation to complete
            List<AgentResponse> preparationResponses = preparationFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
            
            // Phase 2: Execute primary agent request
            AgentResponse primaryResponse = workflow.getPrimaryAgent().processRequest(workflow.getRequest());
            
            // Phase 3: Resolve conflicts if any
            List<AgentResponse> allResponses = new ArrayList<>(preparationResponses);
            allResponses.add(primaryResponse);
            
            ConflictResolutionResult conflictResult = conflictResolver.resolveConflicts(allResponses, workflow);
            
            // Phase 4: Create coordinated result
            return CoordinationResult.success(workflow.getCoordinationId(), workflow.getRequest(),
                conflictResult.getResolvedResponse(), allResponses);
                
        } catch (Exception e) {
            return CoordinationResult.error(workflow.getCoordinationId(), workflow.getRequest(),
                "Workflow execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Records coordination metrics for performance monitoring
     */
    private void recordCoordinationMetrics(String coordinationId, Duration coordinationTime, boolean success) {
        // Record in performance monitor
        performanceMonitor.recordRequest(coordinationId, coordinationTime, success);
        
        // Update coordination metrics for involved agents
        // This would be implemented based on the specific workflow
    }
    
    /**
     * Gets coordination statistics
     */
    public CoordinationStatistics getCoordinationStatistics() {
        return new CoordinationStatistics(coordinationMetrics.values());
    }
    
    /**
     * Initializes default coordination rules
     */
    private void initializeDefaultCoordinationRules() {
        // Rule: Security requests require unified security agent
        coordinationRules.put("security-coordination", 
            new AgentCoordinationRule("security-coordination")
                .whenCapability(AgentCapability.SECURITY_COORDINATION)
                .requireAgent("unified-security-agent"));
        
        // Rule: Cross-project requests require architecture agent
        coordinationRules.put("cross-project-architecture",
            new AgentCoordinationRule("cross-project-architecture")
                .whenCrossProject()
                .requireAgent("workspace-architecture-agent"));
        
        // Rule: API changes require contract agent
        coordinationRules.put("api-contract-coordination",
            new AgentCoordinationRule("api-contract-coordination")
                .whenRequestType("api-change")
                .requireAgent("api-contract-agent"));
        
        // Rule: Performance requests require performance coordination
        coordinationRules.put("performance-coordination",
            new AgentCoordinationRule("performance-coordination")
                .whenCapability(AgentCapability.PERFORMANCE_OPTIMIZATION)
                .requireAgent("performance-coordination-agent"));
    }
    
    /**
     * Adds a custom coordination rule
     */
    public void addCoordinationRule(AgentCoordinationRule rule) {
        coordinationRules.put(rule.getRuleId(), rule);
    }
    
    /**
     * Removes a coordination rule
     */
    public void removeCoordinationRule(String ruleId) {
        coordinationRules.remove(ruleId);
    }
    
    /**
     * Gets all registered agents
     */
    public Map<String, WorkspaceAgent> getRegisteredAgents() {
        return new HashMap<>(registeredAgents);
    }
    
    /**
     * Shuts down the coordination matrix
     */
    public void shutdown() {
        coordinationExecutor.shutdown();
    }
    

}