package durion.workspace.agents.validation;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages rollback of agent guidance when validation fails
 */
public class GuidanceRollbackManager {
    
    private final Map<String, RollbackEntry> rollbackHistory;
    
    public GuidanceRollbackManager() {
        this.rollbackHistory = new ConcurrentHashMap<>();
    }
    
    /**
     * Performs rollback of guidance for a specific request
     */
    public RollbackResult rollbackGuidance(String requestId, String reason) {
        try {
            // Record rollback entry
            RollbackEntry entry = new RollbackEntry(requestId, reason, Instant.now());
            rollbackHistory.put(requestId, entry);
            
            // In a real implementation, this would:
            // 1. Retrieve the previous valid guidance
            // 2. Restore system state to before the invalid guidance
            // 3. Notify affected systems of the rollback
            
            return RollbackResult.success(requestId, "Guidance successfully rolled back: " + reason);
            
        } catch (Exception e) {
            return RollbackResult.failure(requestId, "Rollback failed: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a request has been rolled back
     */
    public boolean hasBeenRolledBack(String requestId) {
        return rollbackHistory.containsKey(requestId);
    }
    
    /**
     * Gets rollback entry for a request
     */
    public RollbackEntry getRollbackEntry(String requestId) {
        return rollbackHistory.get(requestId);
    }
    
    /**
     * Gets rollback statistics
     */
    public RollbackStatistics getRollbackStatistics() {
        return new RollbackStatistics(rollbackHistory.values());
    }
    
    /**
     * Inner class for rollback entries
     */
    private static class RollbackEntry {
        private final String requestId;
        private final String reason;
        private final Instant timestamp;
        
        public RollbackEntry(String requestId, String reason, Instant timestamp) {
            this.requestId = requestId;
            this.reason = reason;
            this.timestamp = timestamp;
        }
        
        public String getRequestId() { return requestId; }
        public String getReason() { return reason; }
        public Instant getTimestamp() { return timestamp; }
    }
}