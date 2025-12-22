package core;

import java.time.Instant;
import java.util.Map;

/**
 * Result of an agent operation execution
 */
public class AgentResult {
    private final boolean success;
    private final Object data;
    private final String message;
    private final Instant timestamp;
    private final long executionTimeMs;
    private final Map<String, Object> metadata;
    
    public AgentResult(boolean success, Object data, String message, 
                      long executionTimeMs, Map<String, Object> metadata) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.timestamp = Instant.now();
        this.executionTimeMs = executionTimeMs;
        this.metadata = metadata;
    }
    
    public static AgentResult success(Object data, long executionTimeMs) {
        return new AgentResult(true, data, "Success", executionTimeMs, Map.of());
    }
    
    public static AgentResult success(Object data, String message, long executionTimeMs) {
        return new AgentResult(true, data, message, executionTimeMs, Map.of());
    }
    
    public static AgentResult failure(String message, long executionTimeMs) {
        return new AgentResult(false, null, message, executionTimeMs, Map.of());
    }
    
    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public Map<String, Object> getMetadata() { return metadata; }
}
