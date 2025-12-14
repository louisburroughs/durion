package durion.workspace.agents.core;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Response object for agent communication
 */
public class AgentResponse {
    
    private final String requestId;
    private final String guidance;
    private final List<String> recommendations;
    private final Instant timestamp;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, Object> metadata;
    
    // Success response
    public AgentResponse(String requestId, String guidance, 
                        List<String> recommendations, Instant timestamp) {
        this.requestId = requestId;
        this.guidance = guidance;
        this.recommendations = new ArrayList<>(recommendations);
        this.timestamp = timestamp;
        this.success = true;
        this.errorMessage = null;
        this.metadata = new HashMap<>();
    }
    
    // Error response
    public AgentResponse(String requestId, String errorMessage, Instant timestamp) {
        this.requestId = requestId;
        this.guidance = "";
        this.recommendations = new ArrayList<>();
        this.timestamp = timestamp;
        this.success = false;
        this.errorMessage = errorMessage;
        this.metadata = new HashMap<>();
    }
    
    // Getters
    public String getRequestId() { return requestId; }
    public String getGuidance() { return guidance; }
    public List<String> getRecommendations() { return new ArrayList<>(recommendations); }
    public Instant getTimestamp() { return timestamp; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    
    // Metadata management
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Checks if response contains architectural guidance
     */
    public boolean hasArchitecturalGuidance() {
        return guidance.toLowerCase().contains("architecture") ||
               guidance.toLowerCase().contains("design pattern") ||
               guidance.toLowerCase().contains("component");
    }
    
    /**
     * Checks if response contains security recommendations
     */
    public boolean hasSecurityRecommendations() {
        return recommendations.stream()
                .anyMatch(rec -> rec.toLowerCase().contains("security") ||
                               rec.toLowerCase().contains("authentication") ||
                               rec.toLowerCase().contains("authorization"));
    }
    
    /**
     * Checks if response contains performance optimizations
     */
    public boolean hasPerformanceOptimizations() {
        return recommendations.stream()
                .anyMatch(rec -> rec.toLowerCase().contains("performance") ||
                               rec.toLowerCase().contains("optimization") ||
                               rec.toLowerCase().contains("caching"));
    }
    
    /**
     * Gets the response quality score (0-100)
     */
    public int getQualityScore() {
        int score = 0;
        
        // Base score for successful response
        if (success) {
            score += 40;
        }
        
        // Score for guidance content
        if (guidance != null && !guidance.trim().isEmpty()) {
            score += 30;
        }
        
        // Score for recommendations
        score += Math.min(30, recommendations.size() * 5);
        
        return Math.min(100, score);
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("AgentResponse{requestId='%s', success=true, recommendations=%d, quality=%d}",
                    requestId, recommendations.size(), getQualityScore());
        } else {
            return String.format("AgentResponse{requestId='%s', success=false, error='%s'}",
                    requestId, errorMessage);
        }
    }
}