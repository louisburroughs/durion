package core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all workspace agents with performance targets:
 * - <5s response time for 95% of requests
 * - 99.9% availability during business hours
 * - Support for 100 concurrent users
 */
public interface WorkspaceAgent {
    
    /**
     * Get the unique identifier for this agent
     */
    String getAgentId();
    
    /**
     * Get the agent's capabilities and supported operations
     */
    AgentCapabilities getCapabilities();
    
    /**
     * Execute an operation with performance monitoring
     * @param operation The operation to execute
     * @param parameters Operation parameters
     * @return Future containing the operation result
     */
    CompletableFuture<AgentResult> execute(String operation, Map<String, Object> parameters);
    
    /**
     * Get current health status of the agent
     */
    AgentHealth getHealth();
    
    /**
     * Get performance metrics for monitoring
     */
    AgentMetrics getMetrics();
    
    /**
     * Initialize the agent with configuration
     */
    void initialize(AgentConfiguration config);
    
    /**
     * Shutdown the agent gracefully
     */
    void shutdown();
    
    /**
     * Check if agent is ready to handle requests
     */
    boolean isReady();
    
    /**
     * Get the maximum response time target (5 seconds)
     */
    default Duration getResponseTimeTarget() {
        return Duration.ofSeconds(5);
    }
    
    /**
     * Get the availability target (99.9%)
     */
    default double getAvailabilityTarget() {
        return 0.999;
    }
}
