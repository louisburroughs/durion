package durion.workspace.agents.discovery;

import durion.workspace.agents.core.*;

import java.util.List;
import java.util.ArrayList;

/**
 * Engine for selecting optimal agent routing based on scored candidates
 */
public class AgentRoutingEngine {
    
    /**
     * Selects optimal routing from scored agent candidates
     */
    public AgentRoutingDecision selectOptimalRouting(List<ScoredAgent> scoredAgents, AgentRequest request) {
        if (scoredAgents.isEmpty()) {
            return AgentRoutingDecision.noAgentsAvailable(request);
        }
        
        // Sort by score (highest first)
        scoredAgents.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        
        // Select primary agent (highest scored)
        ScoredAgent primaryAgent = scoredAgents.get(0);
        
        // Determine routing strategy based on request characteristics
        RoutingStrategy strategy = determineRoutingStrategy(request, scoredAgents);
        
        switch (strategy) {
            case SINGLE_AGENT:
                return createSingleAgentRouting(primaryAgent, request);
                
            case PRIMARY_WITH_FALLBACK:
                return createFallbackRouting(scoredAgents, request);
                
            case MULTI_AGENT_COORDINATION:
                return createCoordinationRouting(scoredAgents, request);
                
            case LOAD_BALANCED:
                return createLoadBalancedRouting(scoredAgents, request);
                
            default:
                return createSingleAgentRouting(primaryAgent, request);
        }
    }
    
    /**
     * Determines the optimal routing strategy
     */
    private RoutingStrategy determineRoutingStrategy(AgentRequest request, List<ScoredAgent> scoredAgents) {
        // High priority requests get single best agent
        if (request.getPriority() >= 8) {
            return RoutingStrategy.SINGLE_AGENT;
        }
        
        // Cross-project requests may need coordination
        if (request.isCrossProject() && scoredAgents.size() > 1) {
            // Check if multiple agents have similar high scores
            if (scoredAgents.size() >= 2 && 
                scoredAgents.get(1).getScore() >= scoredAgents.get(0).getScore() - 10) {
                return RoutingStrategy.MULTI_AGENT_COORDINATION;
            }
        }
        
        // Complex requests benefit from fallback options
        if (isComplexRequest(request) && scoredAgents.size() > 1) {
            return RoutingStrategy.PRIMARY_WITH_FALLBACK;
        }
        
        // High-load scenarios use load balancing
        if (shouldUseLoadBalancing(scoredAgents)) {
            return RoutingStrategy.LOAD_BALANCED;
        }
        
        return RoutingStrategy.SINGLE_AGENT;
    }
    
    /**
     * Creates single agent routing decision
     */
    private AgentRoutingDecision createSingleAgentRouting(ScoredAgent primaryAgent, AgentRequest request) {
        return AgentRoutingDecision.singleAgent(
            request,
            primaryAgent.getAgent(),
            primaryAgent.getScore(),
            "Selected highest scoring agent for request"
        );
    }
    
    /**
     * Creates fallback routing decision
     */
    private AgentRoutingDecision createFallbackRouting(List<ScoredAgent> scoredAgents, AgentRequest request) {
        ScoredAgent primary = scoredAgents.get(0);
        List<WorkspaceAgent> fallbackAgents = new ArrayList<>();
        
        // Add up to 2 fallback agents
        for (int i = 1; i < Math.min(3, scoredAgents.size()); i++) {
            fallbackAgents.add(scoredAgents.get(i).getAgent());
        }
        
        return AgentRoutingDecision.withFallback(
            request,
            primary.getAgent(),
            fallbackAgents,
            primary.getScore(),
            "Primary agent with fallback options for reliability"
        );
    }
    
    /**
     * Creates multi-agent coordination routing decision
     */
    private AgentRoutingDecision createCoordinationRouting(List<ScoredAgent> scoredAgents, AgentRequest request) {
        List<WorkspaceAgent> coordinatingAgents = new ArrayList<>();
        
        // Select top agents with similar scores for coordination
        int primaryScore = scoredAgents.get(0).getScore();
        for (ScoredAgent scored : scoredAgents) {
            if (scored.getScore() >= primaryScore - 15 && coordinatingAgents.size() < 3) {
                coordinatingAgents.add(scored.getAgent());
            }
        }
        
        return AgentRoutingDecision.multiAgentCoordination(
            request,
            coordinatingAgents,
            primaryScore,
            "Multi-agent coordination for cross-project request"
        );
    }
    
    /**
     * Creates load-balanced routing decision
     */
    private AgentRoutingDecision createLoadBalancedRouting(List<ScoredAgent> scoredAgents, AgentRequest request) {
        // Select agent with best score-to-load ratio
        ScoredAgent selectedAgent = scoredAgents.stream()
            .min((a, b) -> Double.compare(
                calculateLoadRatio(a.getAgent()),
                calculateLoadRatio(b.getAgent())
            ))
            .orElse(scoredAgents.get(0));
        
        return AgentRoutingDecision.loadBalanced(
            request,
            selectedAgent.getAgent(),
            selectedAgent.getScore(),
            "Selected agent with optimal load balance"
        );
    }
    
    /**
     * Checks if request is complex and benefits from fallback
     */
    private boolean isComplexRequest(AgentRequest request) {
        String description = request.getDescription().toLowerCase();
        
        // Complex requests involve multiple systems or technologies
        return description.contains("integration") ||
               description.contains("migration") ||
               description.contains("architecture") ||
               request.isCrossProject();
    }
    
    /**
     * Checks if load balancing should be used
     */
    private boolean shouldUseLoadBalancing(List<ScoredAgent> scoredAgents) {
        if (scoredAgents.size() < 2) {
            return false;
        }
        
        // Use load balancing if multiple agents have similar scores
        int topScore = scoredAgents.get(0).getScore();
        long similarScoreCount = scoredAgents.stream()
            .mapToInt(ScoredAgent::getScore)
            .filter(score -> score >= topScore - 5)
            .count();
        
        return similarScoreCount >= 2;
    }
    
    /**
     * Calculates load ratio for an agent (lower is better)
     */
    private double calculateLoadRatio(WorkspaceAgent agent) {
        // This would integrate with actual load monitoring
        // For now, return a simple calculation based on agent status
        AgentStatus status = agent.getStatus();
        
        if (status.healthy()) {
            return 1.0;
        } else if (status.available()) {
            return 2.0;
        } else {
            return 10.0; // High load ratio for unhealthy agents
        }
    }
    
    /**
     * Enum for routing strategies
     */
    public enum RoutingStrategy {
        SINGLE_AGENT,               // Route to single best agent
        PRIMARY_WITH_FALLBACK,      // Primary agent with fallback options
        MULTI_AGENT_COORDINATION,   // Multiple agents coordinate
        LOAD_BALANCED              // Select based on current load
    }
}