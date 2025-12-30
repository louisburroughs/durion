package com.durion.interfaces;

import com.durion.core.AgentResult;

/**
 * Interface for agents that coordinate activities across multiple projects
 * in the durion ecosystem (durion-positivity-backend, durion-positivity, durion-moqui-frontend).
 */
public interface CrossProjectCoordinator extends WorkspaceAgent {
    
    /**
     * Coordinate an activity across specified projects.
     * 
     * @param projects Array of project identifiers to coordinate
     * @param activity The activity to coordinate
     * @param parameters Activity-specific parameters
     * @return Result of the coordination
     */
    AgentResult coordinateAcrossProjects(String[] projects, String activity, Object... parameters);
    
    /**
     * Validate consistency across projects for a given aspect.
     * 
     * @param aspect The aspect to validate (e.g., "api-contracts", "security", "data-models")
     * @return Validation result with any inconsistencies found
     */
    AgentResult validateCrossProjectConsistency(String aspect);
    
    /**
     * Synchronize a change across all affected projects.
     * 
     * @param changeType Type of change (e.g., "api-update", "security-policy", "dependency")
     * @param changeDetails Details of the change
     * @return Synchronization result
     */
    AgentResult synchronizeChange(String changeType, Object changeDetails);
}
