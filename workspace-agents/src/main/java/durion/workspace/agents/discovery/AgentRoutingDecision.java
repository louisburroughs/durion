package durion.workspace.agents.discovery;

import durion.workspace.agents.core.AgentRequest;
import durion.workspace.agents.core.WorkspaceAgent;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a routing decision for an agent request
 */
public class AgentRoutingDecision {
    
    private final AgentRequest request;
    private final RoutingType routingType;
    private final WorkspaceAgent primaryAgent;
    private final List<WorkspaceAgent> additionalAgents;
    private final int confidenceScore;
    private final String reasoning;
    private final Instant timestamp;
    
    private AgentRoutingDecision(AgentRequest request, RoutingType routingType,
                               WorkspaceAgent primaryAgent, List<WorkspaceAgent> additionalAgents,
                               int confidenceScore, String reasoning) {
        this.request = request;
        this.routingType = routingType;
        this.primaryAgent = primaryAgent;
        this.additionalAgents = additionalAgents != null ? new ArrayList<>(additionalAgents) : new ArrayList<>();
        this.confidenceScore = confidenceScore;
        this.reasoning = reasoning;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a single agent routing decision
     */
    public static AgentRoutingDecision singleAgent(AgentRequest request, WorkspaceAgent agent,
                                                 int confidenceScore, String reasoning) {
        return new AgentRoutingDecision(request, RoutingType.SINGLE_AGENT, agent, null, 
                                      confidenceScore, reasoning);
    }
    
    /**
     * Creates a routing decision with fallback agents
     */
    public static AgentRoutingDecision withFallback(AgentRequest request, WorkspaceAgent primaryAgent,
                                                  List<WorkspaceAgent> fallbackAgents,
                                                  int confidenceScore, String reasoning) {
        return new AgentRoutingDecision(request, RoutingType.PRIMARY_WITH_FALLBACK, primaryAgent, 
                                      fallbackAgents, confidenceScore, reasoning);
    }
    
    /**
     * Creates a multi-agent coordination routing decision
     */
    public static AgentRoutingDecision multiAgentCoordination(AgentRequest request,
                                                            List<WorkspaceAgent> coordinatingAgents,
                                                            int confidenceScore, String reasoning) {
        if (coordinatingAgents.isEmpty()) {
            throw new IllegalArgumentException("Coordinating agents list cannot be empty");
        }
        
        WorkspaceAgent primary = coordinatingAgents.get(0);
        List<WorkspaceAgent> additional = coordinatingAgents.subList(1, coordinatingAgents.size());
        
        return new AgentRoutingDecision(request, RoutingType.MULTI_AGENT_COORDINATION, primary, 
                                      additional, confidenceScore, reasoning);
    }
    
    /**
     * Creates a load-balanced routing decision
     */
    public static AgentRoutingDecision loadBalanced(AgentRequest request, WorkspaceAgent agent,
                                                  int confidenceScore, String reasoning) {
        return new AgentRoutingDecision(request, RoutingType.LOAD_BALANCED, agent, null, 
                                      confidenceScore, reasoning);
    }
    
    /**
     * Creates a decision when no agents are available
     */
    public static AgentRoutingDecision noAgentsAvailable(AgentRequest request) {
        return new AgentRoutingDecision(request, RoutingType.NO_AGENTS_AVAILABLE, null, null, 
                                      0, "No suitable agents found for request");
    }
    
    // Getters
    public AgentRequest getRequest() { return request; }
    public RoutingType getRoutingType() { return routingType; }
    public WorkspaceAgent getPrimaryAgent() { return primaryAgent; }
    public List<WorkspaceAgent> getAdditionalAgents() { return new ArrayList<>(additionalAgents); }
    public int getConfidenceScore() { return confidenceScore; }
    public String getReasoning() { return reasoning; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets all agents involved in this routing decision
     */
    public List<WorkspaceAgent> getAllAgents() {
        List<WorkspaceAgent> allAgents = new ArrayList<>();
        if (primaryAgent != null) {
            allAgents.add(primaryAgent);
        }
        allAgents.addAll(additionalAgents);
        return allAgents;
    }
    
    /**
     * Gets the total number of agents involved
     */
    public int getAgentCount() {
        return getAllAgents().size();
    }
    
    /**
     * Checks if routing decision is successful
     */
    public boolean isSuccessful() {
        return routingType != RoutingType.NO_AGENTS_AVAILABLE && primaryAgent != null;
    }
    
    /**
     * Checks if this is a multi-agent routing
     */
    public boolean isMultiAgent() {
        return routingType == RoutingType.MULTI_AGENT_COORDINATION || 
               routingType == RoutingType.PRIMARY_WITH_FALLBACK;
    }
    
    /**
     * Checks if routing has high confidence
     */
    public boolean isHighConfidence() {
        return confidenceScore >= 80;
    }
    
    /**
     * Gets routing quality assessment
     */
    public String getQualityAssessment() {
        if (!isSuccessful()) {
            return "Failed - No agents available";
        }
        
        if (confidenceScore >= 90) {
            return "Excellent - High confidence routing";
        } else if (confidenceScore >= 80) {
            return "Very Good - Good agent match";
        } else if (confidenceScore >= 70) {
            return "Good - Suitable agent found";
        } else if (confidenceScore >= 60) {
            return "Fair - Acceptable agent match";
        } else {
            return "Poor - Low confidence routing";
        }
    }
    
    /**
     * Gets execution strategy based on routing type
     */
    public String getExecutionStrategy() {
        switch (routingType) {
            case SINGLE_AGENT:
                return "Execute request with single agent";
            case PRIMARY_WITH_FALLBACK:
                return "Execute with primary agent, fallback to alternatives if needed";
            case MULTI_AGENT_COORDINATION:
                return "Coordinate execution across multiple agents";
            case LOAD_BALANCED:
                return "Execute with load-balanced agent selection";
            case NO_AGENTS_AVAILABLE:
                return "Cannot execute - no suitable agents";
            default:
                return "Unknown execution strategy";
        }
    }
    
    @Override
    public String toString() {
        if (isSuccessful()) {
            return String.format("AgentRoutingDecision{type=%s, primary='%s', agents=%d, confidence=%d}",
                    routingType, primaryAgent.getAgentId(), getAgentCount(), confidenceScore);
        } else {
            return String.format("AgentRoutingDecision{type=%s, reason='%s'}",
                    routingType, reasoning);
        }
    }
    
    /**
     * Enum for routing types
     */
    public enum RoutingType {
        SINGLE_AGENT,               // Single agent handles the request
        PRIMARY_WITH_FALLBACK,      // Primary agent with fallback options
        MULTI_AGENT_COORDINATION,   // Multiple agents coordinate to handle request
        LOAD_BALANCED,             // Agent selected based on load balancing
        NO_AGENTS_AVAILABLE        // No suitable agents found
    }
}