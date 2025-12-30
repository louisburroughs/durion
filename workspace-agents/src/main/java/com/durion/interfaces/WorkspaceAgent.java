package com.durion.interfaces;

import com.durion.core.AgentCapabilities;
import com.durion.core.AgentHealth;
import com.durion.core.AgentMetrics;
import com.durion.core.AgentResult;

/**
 * Base interface for all 13 workspace agents in the durion ecosystem.
 * Provides unified coordination across backend and frontend systems.
 */
public interface WorkspaceAgent {
    
    /**
     * Get the unique identifier for this agent.
     */
    String getAgentId();
    
    /**
     * Get the capabilities supported by this agent.
     */
    AgentCapabilities getCapabilities();
    
    /**
     * Execute an operation with the given parameters.
     * Must complete within 5 seconds for 95% of requests.
     */
    AgentResult execute(String operation, Object... parameters);
    
    /**
     * Get current health status of the agent.
     */
    AgentHealth getHealth();
    
    /**
     * Get performance metrics for this agent.
     */
    AgentMetrics getMetrics();
    
    /**
     * Initialize the agent with configuration.
     */
    void initialize();
    
    /**
     * Shutdown the agent gracefully.
     */
    void shutdown();
}
