package durion.workspace.agents.core;

/**
 * Exception for workspace agent operations
 */
public class AgentException extends Exception {
    
    private final String agentId;
    private final AgentErrorType errorType;
    
    public AgentException(String agentId, AgentErrorType errorType, String message) {
        super(message);
        this.agentId = agentId;
        this.errorType = errorType;
    }
    
    public AgentException(String agentId, AgentErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.agentId = agentId;
        this.errorType = errorType;
    }
    
    public String getAgentId() { return agentId; }
    public AgentErrorType getErrorType() { return errorType; }
    
    public enum AgentErrorType {
        TIMEOUT,
        UNAVAILABLE,
        CONFIGURATION_ERROR,
        COORDINATION_FAILURE,
        PERFORMANCE_DEGRADATION,
        CAPABILITY_MISMATCH,
        RESOURCE_EXHAUSTION
    }
}