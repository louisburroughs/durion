package durion.workspace.agents.discovery;

import durion.workspace.agents.core.AgentRequest;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Result of agent discovery process
 */
public class AgentDiscoveryResult {
    
    private final AgentRequest request;
    private final boolean success;
    private final AgentRoutingDecision routingDecision;
    private final List<ScoredAgent> candidateAgents;
    private final String errorMessage;
    private final Instant timestamp;
    
    private AgentDiscoveryResult(AgentRequest request, boolean success,
                               AgentRoutingDecision routingDecision,
                               List<ScoredAgent> candidateAgents,
                               String errorMessage) {
        this.request = request;
        this.success = success;
        this.routingDecision = routingDecision;
        this.candidateAgents = candidateAgents != null ? new ArrayList<>(candidateAgents) : new ArrayList<>();
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful discovery result
     */
    public static AgentDiscoveryResult success(AgentRequest request, AgentRoutingDecision routingDecision,
                                             List<ScoredAgent> candidateAgents) {
        return new AgentDiscoveryResult(request, true, routingDecision, candidateAgents, null);
    }
    
    /**
     * Creates a result when no agents are found
     */
    public static AgentDiscoveryResult noAgentsFound(AgentRequest request) {
        return new AgentDiscoveryResult(request, false, null, null, 
                "No agents found with required capability: " + request.getRequiredCapability());
    }
    
    /**
     * Creates an error result
     */
    public static AgentDiscoveryResult error(AgentRequest request, String errorMessage) {
        return new AgentDiscoveryResult(request, false, null, null, errorMessage);
    }
    
    // Getters
    public AgentRequest getRequest() { return request; }
    public boolean isSuccess() { return success; }
    public AgentRoutingDecision getRoutingDecision() { return routingDecision; }
    public List<ScoredAgent> getCandidateAgents() { return new ArrayList<>(candidateAgents); }
    public String getErrorMessage() { return errorMessage; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets the number of candidate agents found
     */
    public int getCandidateCount() {
        return candidateAgents.size();
    }
    
    /**
     * Checks if discovery found multiple suitable agents
     */
    public boolean hasMultipleCandidates() {
        return candidateAgents.size() > 1;
    }
    
    /**
     * Gets the highest scoring candidate
     */
    public ScoredAgent getBestCandidate() {
        return candidateAgents.stream()
            .max((a, b) -> Integer.compare(a.getScore(), b.getScore()))
            .orElse(null);
    }
    
    /**
     * Gets candidates with high scores (80+)
     */
    public List<ScoredAgent> getHighScoringCandidates() {
        return candidateAgents.stream()
            .filter(ScoredAgent::isHighScore)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Gets the discovery quality score (0-100)
     */
    public int getDiscoveryQuality() {
        if (!success) {
            return 0;
        }
        
        int score = 0;
        
        // Base score for successful discovery
        score += 40;
        
        // Score for routing decision quality
        if (routingDecision != null && routingDecision.isSuccessful()) {
            score += routingDecision.getConfidenceScore() * 0.4; // 40% weight
        }
        
        // Bonus for multiple good candidates
        if (hasMultipleCandidates()) {
            long goodCandidates = candidateAgents.stream()
                .mapToInt(ScoredAgent::getScore)
                .filter(s -> s >= 70)
                .count();
            score += Math.min(20, goodCandidates * 5);
        }
        
        return Math.min(100, score);
    }
    
    /**
     * Gets discovery summary
     */
    public String getDiscoverySummary() {
        if (!success) {
            return "Discovery failed: " + errorMessage;
        }
        
        if (routingDecision == null) {
            return "Discovery completed but no routing decision made";
        }
        
        return String.format("Found %d candidates, selected %s routing with %d%% confidence",
                getCandidateCount(), 
                routingDecision.getRoutingType().toString().toLowerCase().replace('_', ' '),
                routingDecision.getConfidenceScore());
    }
    
    /**
     * Checks if discovery meets performance requirements
     */
    public boolean meetsPerformanceRequirements() {
        if (!success) {
            return false;
        }
        
        // Should find at least one suitable agent
        if (candidateAgents.isEmpty()) {
            return false;
        }
        
        // Best candidate should have good score
        ScoredAgent best = getBestCandidate();
        return best != null && best.getScore() >= 70;
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("AgentDiscoveryResult{success=true, candidates=%d, quality=%d, routing=%s}",
                    getCandidateCount(), getDiscoveryQuality(), 
                    routingDecision != null ? routingDecision.getRoutingType() : "none");
        } else {
            return String.format("AgentDiscoveryResult{success=false, error='%s'}", errorMessage);
        }
    }
}