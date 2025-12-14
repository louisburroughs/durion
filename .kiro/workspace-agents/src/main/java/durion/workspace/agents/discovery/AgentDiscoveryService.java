package durion.workspace.agents.discovery;

import durion.workspace.agents.core.*;
import durion.workspace.agents.monitoring.PerformanceMonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for discovering and routing requests to appropriate workspace agents.
 * Implements intelligent agent selection based on full-stack context and requirements.
 * 
 * Requirements: 1.2, 2.1 - Agent selection and intelligent routing
 */
public class AgentDiscoveryService {
    
    private final Map<String, WorkspaceAgent> registeredAgents;
    private final Map<AgentCapability, List<String>> capabilityIndex;
    private final Map<String, AgentMetadata> agentMetadata;
    private final AgentRoutingEngine routingEngine;
    private final PerformanceMonitor performanceMonitor;
    
    // Discovery statistics
    private final Map<String, DiscoveryMetrics> discoveryMetrics;
    
    public AgentDiscoveryService(PerformanceMonitor performanceMonitor) {
        this.registeredAgents = new ConcurrentHashMap<>();
        this.capabilityIndex = new ConcurrentHashMap<>();
        this.agentMetadata = new ConcurrentHashMap<>();
        this.routingEngine = new AgentRoutingEngine();
        this.performanceMonitor = performanceMonitor;
        this.discoveryMetrics = new ConcurrentHashMap<>();
        
        initializeCapabilityIndex();
    }
    
    /**
     * Registers an agent with the discovery service
     */
    public void registerAgent(WorkspaceAgent agent) {
        String agentId = agent.getAgentId();
        
        // Register the agent
        registeredAgents.put(agentId, agent);
        
        // Create metadata
        AgentMetadata metadata = new AgentMetadata(agent);
        agentMetadata.put(agentId, metadata);
        
        // Update capability index
        updateCapabilityIndex(agent);
        
        // Initialize metrics
        discoveryMetrics.put(agentId, new DiscoveryMetrics(agentId));
        
        // Set performance monitor
        agent.setPerformanceMonitor(performanceMonitor);
    }
    
    /**
     * Unregisters an agent from the discovery service
     */
    public void unregisterAgent(String agentId) {
        WorkspaceAgent agent = registeredAgents.remove(agentId);
        if (agent != null) {
            // Remove from capability index
            removeFromCapabilityIndex(agent);
            
            // Remove metadata and metrics
            agentMetadata.remove(agentId);
            discoveryMetrics.remove(agentId);
        }
    }
    
    /**
     * Discovers suitable agents for a request based on full-stack context
     */
    public AgentDiscoveryResult discoverAgents(AgentRequest request) {
        Instant startTime = Instant.now();
        
        try {
            // Phase 1: Find candidate agents by capability
            List<WorkspaceAgent> candidateAgents = findCandidatesByCapability(request);
            
            if (candidateAgents.isEmpty()) {
                return AgentDiscoveryResult.noAgentsFound(request);
            }
            
            // Phase 2: Filter by context and requirements
            List<WorkspaceAgent> contextFilteredAgents = filterByContext(candidateAgents, request);
            
            // Phase 3: Score and rank agents
            List<ScoredAgent> scoredAgents = scoreAgents(contextFilteredAgents, request);
            
            // Phase 4: Select optimal routing
            AgentRoutingDecision routingDecision = routingEngine.selectOptimalRouting(scoredAgents, request);
            
            // Record discovery metrics
            Duration discoveryTime = Duration.between(startTime, Instant.now());
            recordDiscoveryMetrics(request, discoveryTime, true, scoredAgents.size());
            
            return AgentDiscoveryResult.success(request, routingDecision, scoredAgents);
            
        } catch (Exception e) {
            Duration discoveryTime = Duration.between(startTime, Instant.now());
            recordDiscoveryMetrics(request, discoveryTime, false, 0);
            
            return AgentDiscoveryResult.error(request, "Discovery failed: " + e.getMessage());
        }
    }
    
    /**
     * Finds candidate agents by required capability
     */
    private List<WorkspaceAgent> findCandidatesByCapability(AgentRequest request) {
        AgentCapability requiredCapability = request.getRequiredCapability();
        
        List<String> candidateIds = capabilityIndex.get(requiredCapability);
        if (candidateIds == null) {
            return new ArrayList<>();
        }
        
        return candidateIds.stream()
            .map(registeredAgents::get)
            .filter(Objects::nonNull)
            .filter(agent -> agent.getStatus().healthy())
            .collect(Collectors.toList());
    }
    
    /**
     * Filters agents based on full-stack context and requirements
     */
    private List<WorkspaceAgent> filterByContext(List<WorkspaceAgent> candidates, AgentRequest request) {
        return candidates.stream()
            .filter(agent -> matchesProjectContext(agent, request))
            .filter(agent -> matchesTechnologyStack(agent, request))
            .filter(agent -> meetsPerformanceRequirements(agent))
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if agent matches the project context
     */
    private boolean matchesProjectContext(WorkspaceAgent agent, AgentRequest request) {
        AgentMetadata metadata = agentMetadata.get(agent.getAgentId());
        
        // Cross-project agents should handle cross-project requests
        if (request.isCrossProject()) {
            return metadata.supportsCrossProject();
        }
        
        // Project-specific agents should handle their project types
        if (request.involvesPositivity()) {
            return metadata.supportsPositivity();
        }
        
        if (request.involvesMoqui()) {
            return metadata.supportsMoqui();
        }
        
        // Workspace-level agents can handle any request
        return metadata.isWorkspaceLevel();
    }
    
    /**
     * Checks if agent matches the technology stack requirements
     */
    private boolean matchesTechnologyStack(WorkspaceAgent agent, AgentRequest request) {
        AgentMetadata metadata = agentMetadata.get(agent.getAgentId());
        
        // Check technology stack compatibility
        Set<String> requestTechnologies = extractTechnologies(request);
        return metadata.getSupportedTechnologies().containsAll(requestTechnologies) ||
               metadata.isGeneralPurpose();
    }
    
    /**
     * Checks if agent meets performance requirements
     */
    private boolean meetsPerformanceRequirements(WorkspaceAgent agent) {
        DiscoveryMetrics metrics = discoveryMetrics.get(agent.getAgentId());
        
        // Check success rate (should be > 95%)
        if (metrics.getSuccessRate() < 0.95) {
            return false;
        }
        
        // Check average response time (should be < 5 seconds)
        if (metrics.getAverageDiscoveryTime().toMillis() > 5000) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Scores agents based on suitability for the request
     */
    private List<ScoredAgent> scoreAgents(List<WorkspaceAgent> agents, AgentRequest request) {
        return agents.stream()
            .map(agent -> new ScoredAgent(agent, calculateAgentScore(agent, request)))
            .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());
    }
    
    /**
     * Calculates comprehensive score for agent suitability
     */
    private int calculateAgentScore(WorkspaceAgent agent, AgentRequest request) {
        int score = 0;
        
        // Base capability score (0-40 points)
        score += agent.getCapabilityScore(request.getRequiredCapability()) * 0.4;
        
        // Context matching score (0-25 points)
        score += calculateContextScore(agent, request);
        
        // Performance score (0-20 points)
        score += calculatePerformanceScore(agent);
        
        // Specialization score (0-15 points)
        score += calculateSpecializationScore(agent, request);
        
        return Math.min(100, score);
    }
    
    /**
     * Calculates context matching score
     */
    private int calculateContextScore(WorkspaceAgent agent, AgentRequest request) {
        AgentMetadata metadata = agentMetadata.get(agent.getAgentId());
        int score = 0;
        
        // Perfect match for cross-project requests
        if (request.isCrossProject() && metadata.supportsCrossProject()) {
            score += 25;
        }
        
        // Good match for project-specific requests
        else if (request.involvesPositivity() && metadata.supportsPositivity()) {
            score += 20;
        } else if (request.involvesMoqui() && metadata.supportsMoqui()) {
            score += 20;
        }
        
        // Workspace-level agents are versatile
        else if (metadata.isWorkspaceLevel()) {
            score += 15;
        }
        
        return score;
    }
    
    /**
     * Calculates performance score based on historical metrics
     */
    private int calculatePerformanceScore(WorkspaceAgent agent) {
        DiscoveryMetrics metrics = discoveryMetrics.get(agent.getAgentId());
        
        // Success rate component (0-10 points)
        int successScore = (int) (metrics.getSuccessRate() * 10);
        
        // Response time component (0-10 points)
        long responseTimeMs = metrics.getAverageDiscoveryTime().toMillis();
        int responseScore = Math.max(0, 10 - (int) (responseTimeMs / 500)); // Penalty for slow responses
        
        return successScore + responseScore;
    }
    
    /**
     * Calculates specialization score based on agent type and request
     */
    private int calculateSpecializationScore(WorkspaceAgent agent, AgentRequest request) {
        AgentType agentType = agent.getAgentType();
        String requestType = request.getRequestType();
        
        // Match agent type to request characteristics
        switch (agentType) {
            case WORKSPACE_COORDINATION:
                if (request.isCrossProject() || requestType.contains("architecture")) {
                    return 15;
                }
                break;
                
            case TECHNOLOGY_BRIDGE:
                if (requestType.contains("api") || requestType.contains("integration")) {
                    return 15;
                }
                break;
                
            case OPERATIONAL_COORDINATION:
                if (requestType.contains("deployment") || requestType.contains("monitoring")) {
                    return 15;
                }
                break;
                
            case GOVERNANCE_COMPLIANCE:
                if (requestType.contains("governance") || requestType.contains("documentation")) {
                    return 15;
                }
                break;
        }
        
        return 5; // Base specialization score
    }
    
    /**
     * Extracts technology requirements from request
     */
    private Set<String> extractTechnologies(AgentRequest request) {
        Set<String> technologies = new HashSet<>();
        
        String description = request.getDescription().toLowerCase();
        
        // Backend technologies
        if (description.contains("spring boot") || description.contains("java")) {
            technologies.add("spring-boot");
        }
        if (description.contains("postgresql") || description.contains("database")) {
            technologies.add("postgresql");
        }
        if (description.contains("aws") || description.contains("fargate")) {
            technologies.add("aws");
        }
        
        // Frontend technologies
        if (description.contains("moqui") || description.contains("groovy")) {
            technologies.add("moqui");
        }
        if (description.contains("vue") || description.contains("javascript")) {
            technologies.add("vue");
        }
        
        // Integration technologies
        if (description.contains("rest") || description.contains("api")) {
            technologies.add("rest-api");
        }
        if (description.contains("jwt") || description.contains("authentication")) {
            technologies.add("jwt");
        }
        
        return technologies;
    }
    
    /**
     * Updates the capability index when an agent is registered
     */
    private void updateCapabilityIndex(WorkspaceAgent agent) {
        for (AgentCapability capability : agent.getCapabilities()) {
            capabilityIndex.computeIfAbsent(capability, k -> new ArrayList<>())
                          .add(agent.getAgentId());
        }
    }
    
    /**
     * Removes agent from capability index when unregistered
     */
    private void removeFromCapabilityIndex(WorkspaceAgent agent) {
        for (AgentCapability capability : agent.getCapabilities()) {
            List<String> agentIds = capabilityIndex.get(capability);
            if (agentIds != null) {
                agentIds.remove(agent.getAgentId());
                if (agentIds.isEmpty()) {
                    capabilityIndex.remove(capability);
                }
            }
        }
    }
    
    /**
     * Initializes the capability index
     */
    private void initializeCapabilityIndex() {
        for (AgentCapability capability : AgentCapability.values()) {
            capabilityIndex.put(capability, new ArrayList<>());
        }
    }
    
    /**
     * Records discovery metrics
     */
    private void recordDiscoveryMetrics(AgentRequest request, Duration discoveryTime, 
                                      boolean success, int candidatesFound) {
        // Record in performance monitor
        performanceMonitor.recordRequest(request.getRequestId(), discoveryTime, success);
        
        // Update discovery metrics for capability
        String capabilityKey = request.getRequiredCapability().toString();
        DiscoveryMetrics metrics = discoveryMetrics.computeIfAbsent(capabilityKey, 
            k -> new DiscoveryMetrics(capabilityKey));
        metrics.recordDiscovery(discoveryTime, success, candidatesFound);
    }
    
    /**
     * Gets discovery statistics
     */
    public DiscoveryStatistics getDiscoveryStatistics() {
        return new DiscoveryStatistics(discoveryMetrics.values());
    }
    
    /**
     * Gets all registered agents
     */
    public Map<String, WorkspaceAgent> getRegisteredAgents() {
        return new HashMap<>(registeredAgents);
    }
    
    /**
     * Gets agent metadata
     */
    public AgentMetadata getAgentMetadata(String agentId) {
        return agentMetadata.get(agentId);
    }
    
    /**
     * Gets agents by capability
     */
    public List<WorkspaceAgent> getAgentsByCapability(AgentCapability capability) {
        List<String> agentIds = capabilityIndex.get(capability);
        if (agentIds == null) {
            return new ArrayList<>();
        }
        
        return agentIds.stream()
            .map(registeredAgents::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if discovery service meets performance requirements
     */
    public boolean meetsPerformanceRequirements() {
        DiscoveryStatistics stats = getDiscoveryStatistics();
        return stats.getAverageDiscoveryTime().toMillis() <= 1000 && // 1 second for discovery
               stats.getOverallSuccessRate() >= 0.99; // 99% success rate
    }
}