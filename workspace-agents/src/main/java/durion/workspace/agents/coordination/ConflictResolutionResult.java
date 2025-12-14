package durion.workspace.agents.coordination;

import durion.workspace.agents.core.AgentResponse;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Result of conflict resolution between agent responses
 */
public class ConflictResolutionResult {
    
    private final String coordinationId;
    private final ResolutionType resolutionType;
    private final AgentResponse resolvedResponse;
    private final List<ConflictResolver.Conflict> resolvedConflicts;
    private final List<AgentResponse> sourceResponses;
    private final Instant timestamp;
    
    private ConflictResolutionResult(String coordinationId, ResolutionType resolutionType,
                                   AgentResponse resolvedResponse, 
                                   List<ConflictResolver.Conflict> resolvedConflicts,
                                   List<AgentResponse> sourceResponses) {
        this.coordinationId = coordinationId;
        this.resolutionType = resolutionType;
        this.resolvedResponse = resolvedResponse;
        this.resolvedConflicts = resolvedConflicts != null ? new ArrayList<>(resolvedConflicts) : new ArrayList<>();
        this.sourceResponses = sourceResponses != null ? new ArrayList<>(sourceResponses) : new ArrayList<>();
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates result when no conflicts were found
     */
    public static ConflictResolutionResult noConflicts(AgentResponse response) {
        return new ConflictResolutionResult(null, ResolutionType.NO_CONFLICTS, response, null, List.of(response));
    }
    
    /**
     * Creates result when responses were merged without conflicts
     */
    public static ConflictResolutionResult merged(AgentResponse mergedResponse, List<AgentResponse> sourceResponses) {
        return new ConflictResolutionResult(null, ResolutionType.MERGED, mergedResponse, null, sourceResponses);
    }
    
    /**
     * Creates result when conflicts were resolved
     */
    public static ConflictResolutionResult resolved(AgentResponse resolvedResponse, 
                                                  List<ConflictResolver.Conflict> conflicts) {
        return new ConflictResolutionResult(null, ResolutionType.CONFLICTS_RESOLVED, 
                                          resolvedResponse, conflicts, null);
    }
    
    /**
     * Creates result when no valid responses were available
     */
    public static ConflictResolutionResult noValidResponses(String coordinationId) {
        return new ConflictResolutionResult(coordinationId, ResolutionType.NO_VALID_RESPONSES, 
                                          null, null, null);
    }
    
    // Getters
    public String getCoordinationId() { return coordinationId; }
    public ResolutionType getResolutionType() { return resolutionType; }
    public AgentResponse getResolvedResponse() { return resolvedResponse; }
    public List<ConflictResolver.Conflict> getResolvedConflicts() { return new ArrayList<>(resolvedConflicts); }
    public List<AgentResponse> getSourceResponses() { return new ArrayList<>(sourceResponses); }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Checks if resolution was successful
     */
    public boolean isSuccessful() {
        return resolutionType != ResolutionType.NO_VALID_RESPONSES && resolvedResponse != null;
    }
    
    /**
     * Gets the number of conflicts that were resolved
     */
    public int getConflictCount() {
        return resolvedConflicts.size();
    }
    
    /**
     * Checks if this resolution involved conflict resolution
     */
    public boolean hadConflicts() {
        return resolutionType == ResolutionType.CONFLICTS_RESOLVED && !resolvedConflicts.isEmpty();
    }
    
    /**
     * Gets the resolution quality score (0-100)
     */
    public int getResolutionQuality() {
        if (!isSuccessful()) {
            return 0;
        }
        
        int score = 0;
        
        // Base score for successful resolution
        score += 50;
        
        // Bonus for response quality
        if (resolvedResponse != null) {
            score += resolvedResponse.getQualityScore() * 0.3; // 30% weight
        }
        
        // Bonus for conflict resolution
        if (hadConflicts()) {
            score += Math.min(20, resolvedConflicts.size() * 5);
        }
        
        return Math.min(100, score);
    }
    
    /**
     * Gets a summary of the resolution
     */
    public String getResolutionSummary() {
        switch (resolutionType) {
            case NO_CONFLICTS:
                return "Single response, no conflicts detected";
            case MERGED:
                return String.format("Merged %d compatible responses", sourceResponses.size());
            case CONFLICTS_RESOLVED:
                return String.format("Resolved %d conflicts between agent responses", resolvedConflicts.size());
            case NO_VALID_RESPONSES:
                return "No valid responses available for conflict resolution";
            default:
                return "Unknown resolution type";
        }
    }
    
    @Override
    public String toString() {
        return String.format("ConflictResolutionResult{type=%s, conflicts=%d, quality=%d}",
                resolutionType, getConflictCount(), getResolutionQuality());
    }
    
    /**
     * Enum for resolution types
     */
    public enum ResolutionType {
        NO_CONFLICTS,           // Single response or no conflicts found
        MERGED,                 // Multiple compatible responses merged
        CONFLICTS_RESOLVED,     // Conflicts detected and resolved
        NO_VALID_RESPONSES      // No valid responses to resolve
    }
}