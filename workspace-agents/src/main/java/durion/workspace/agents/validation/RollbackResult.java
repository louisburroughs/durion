package durion.workspace.agents.validation;

import java.time.Instant;

/**
 * Result of a guidance rollback operation
 */
public class RollbackResult {
    
    private final String requestId;
    private final boolean success;
    private final String message;
    private final Instant timestamp;
    
    private RollbackResult(String requestId, boolean success, String message) {
        this.requestId = requestId;
        this.success = success;
        this.message = message;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful rollback result
     */
    public static RollbackResult success(String requestId, String message) {
        return new RollbackResult(requestId, true, message);
    }
    
    /**
     * Creates a failed rollback result
     */
    public static RollbackResult failure(String requestId, String message) {
        return new RollbackResult(requestId, false, message);
    }
    
    // Getters
    public String getRequestId() { return requestId; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("RollbackResult{requestId='%s', success=%s, message='%s'}",
                requestId, success, message);
    }
}