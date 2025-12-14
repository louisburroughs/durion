package durion.workspace.agents.coordination;

import durion.workspace.agents.core.AgentRequest;
import durion.workspace.agents.core.AgentResponse;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Result of a coordination workflow execution
 */
public class CoordinationResult {
    
    private final String coordinationId;
    private final AgentRequest originalRequest;
    private final boolean success;
    private final AgentResponse finalResponse;
    private final List<AgentResponse> allResponses;
    private final String errorMessage;
    private final Instant timestamp;
    
    private CoordinationResult(String coordinationId, AgentRequest originalRequest, boolean success,
                              AgentResponse finalResponse, List<AgentResponse> allResponses, 
                              String errorMessage) {
        this.coordinationId = coordinationId;
        this.originalRequest = originalRequest;
        this.success = success;
        this.finalResponse = finalResponse;
        this.allResponses = allResponses != null ? new ArrayList<>(allResponses) : new ArrayList<>();
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful coordination result
     */
    public static CoordinationResult success(String coordinationId, AgentRequest request,
                                           AgentResponse finalResponse, List<AgentResponse> allResponses) {
        return new CoordinationResult(coordinationId, request, true, finalResponse, allResponses, null);
    }
    
    /**
     * Creates an error coordination result
     */
    public static CoordinationResult error(String coordinationId, AgentRequest request, String errorMessage) {
        return new CoordinationResult(coordinationId, request, false, null, null, errorMessage);
    }
    
    /**
     * Creates a result when no agents are available
     */
    public static CoordinationResult noAgentsAvailable(String coordinationId, AgentRequest request) {
        return new CoordinationResult(coordinationId, request, false, null, null, 
            "No agents available to handle request: " + request.getRequiredCapability());
    }
    
    // Getters
    public String getCoordinationId() { return coordinationId; }
    public AgentRequest getOriginalRequest() { return originalRequest; }
    public boolean isSuccess() { return success; }
    public AgentResponse getFinalResponse() { return finalResponse; }
    public List<AgentResponse> getAllResponses() { return new ArrayList<>(allResponses); }
    public String getErrorMessage() { return errorMessage; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets the number of agents that participated
     */
    public int getParticipantCount() {
        return allResponses.size();
    }
    
    /**
     * Checks if coordination involved multiple agents
     */
    public boolean isMultiAgentCoordination() {
        return allResponses.size() > 1;
    }
    
    /**
     * Gets the coordination quality score (0-100)
     */
    public int getCoordinationQuality() {
        if (!success) {
            return 0;
        }
        
        int score = 0;
        
        // Base score for successful coordination
        score += 40;
        
        // Score for final response quality
        if (finalResponse != null) {
            score += finalResponse.getQualityScore() * 0.4; // 40% weight
        }
        
        // Score for multi-agent coordination
        if (isMultiAgentCoordination()) {
            score += 20;
        }
        
        return Math.min(100, score);
    }
    
    /**
     * Checks if any conflicts were resolved during coordination
     */
    public boolean hadConflicts() {
        // This would be set by the conflict resolver
        return allResponses.size() > 1 && 
               allResponses.stream().anyMatch(response -> 
                   response.getMetadata("conflict-resolved") != null);
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("CoordinationResult{id='%s', success=true, participants=%d, quality=%d}",
                    coordinationId, getParticipantCount(), getCoordinationQuality());
        } else {
            return String.format("CoordinationResult{id='%s', success=false, error='%s'}",
                    coordinationId, errorMessage);
        }
    }
}