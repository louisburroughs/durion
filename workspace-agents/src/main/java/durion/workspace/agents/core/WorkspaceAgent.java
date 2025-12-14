package durion.workspace.agents.core;

import durion.workspace.agents.monitoring.PerformanceMonitor;
import java.util.Set;
import java.util.List;

/**
 * Common interface for all workspace-level agents.
 * Defines coordination protocols and performance requirements.
 */
public interface WorkspaceAgent {
    
    /**
     * Gets the unique identifier for this agent
     */
    String getAgentId();
    
    /**
     * Gets the agent type (coordination, bridge, operational, governance)
     */
    AgentType getAgentType();
    
    /**
     * Gets the capabilities this agent provides
     */
    Set<AgentCapability> getCapabilities();
    
    /**
     * Checks if agent has a specific capability
     */
    boolean hasCapability(AgentCapability capability);
    
    /**
     * Gets capability score (0-100) for prioritization
     */
    int getCapabilityScore(AgentCapability capability);
    
    /**
     * Processes an agent request with 5-second response time target
     */
    AgentResponse processRequest(AgentRequest request) throws AgentException;
    
    /**
     * Gets agent status and health information
     */
    AgentStatus getStatus();
    
    /**
     * Sets performance monitor for tracking metrics
     */
    void setPerformanceMonitor(PerformanceMonitor monitor);
    
    /**
     * Gets coordination dependencies (other agents this agent works with)
     */
    List<String> getCoordinationDependencies();
    
    /**
     * Validates if agent can handle the request
     */
    boolean canHandleRequest(AgentRequest request);
    
    /**
     * Gets agent configuration
     */
    AgentConfiguration getConfiguration();
    
    /**
     * Updates agent configuration
     */
    void updateConfiguration(AgentConfiguration config);
}