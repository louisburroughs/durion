package durion.workspace.agents.core;

import java.time.Instant;

/**
 * Status information for workspace agents
 */
public record AgentStatus(
    String agentId,
    boolean available,
    boolean healthy,
    Instant lastActivity,
    String statusMessage,
    double cpuUsage,
    long memoryUsage,
    int activeRequests
) {
    
    /**
     * Creates a healthy status
     */
    public static AgentStatus healthy(String agentId) {
        return new AgentStatus(
            agentId, true, true, Instant.now(),
            "Agent is healthy and available", 0.0, 0L, 0
        );
    }
    
    /**
     * Creates an unavailable status
     */
    public static AgentStatus unavailable(String agentId, String reason) {
        return new AgentStatus(
            agentId, false, false, Instant.now(),
            "Agent unavailable: " + reason, 0.0, 0L, 0
        );
    }
    
    /**
     * Creates a degraded status
     */
    public static AgentStatus degraded(String agentId, String reason) {
        return new AgentStatus(
            agentId, true, false, Instant.now(),
            "Agent degraded: " + reason, 0.0, 0L, 0
        );
    }
}