package core;

/**
 * Health status of a workspace agent
 */
public enum AgentHealth {
    HEALTHY("Agent is operating normally"),
    DEGRADED("Agent is operating with reduced performance"),
    UNHEALTHY("Agent is not functioning properly"),
    UNKNOWN("Agent health status is unknown");
    
    private final String description;
    
    AgentHealth(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isOperational() {
        return this == HEALTHY || this == DEGRADED;
    }
}
