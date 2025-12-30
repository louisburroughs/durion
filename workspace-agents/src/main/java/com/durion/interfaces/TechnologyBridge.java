package com.durion.interfaces;

import com.durion.core.AgentResult;

/**
 * Interface for bridging technology stacks across the durion ecosystem:
 * Java 21 (durion-positivity-backend) ↔ Java 11 (moqui framework) ↔ 
 * Groovy (moqui services) ↔ TypeScript 5.x (Vue.js 3 frontend).
 */
public interface TechnologyBridge extends WorkspaceAgent {
    
    /**
     * Bridge between different technology stacks.
     * 
     * @param sourceStack Source technology stack identifier
     * @param targetStack Target technology stack identifier
     * @param bridgeType Type of bridge needed (e.g., "api", "data", "auth")
     * @return Bridge configuration and implementation guidance
     */
    AgentResult bridgeStacks(String sourceStack, String targetStack, String bridgeType);
    
    /**
     * Manage impedance mismatch between Spring Boot 3.x and Moqui Framework 3.x patterns.
     * 
     * @param springBootPattern Spring Boot pattern specification
     * @param moquiPattern Moqui pattern specification
     * @return Integration pattern guidance
     */
    AgentResult bridgeFrameworkPatterns(Object springBootPattern, Object moquiPattern);
    
    /**
     * Resolve dependency conflicts between Java versions and frameworks.
     * Must achieve 100% detection of conflicts.
     * 
     * @param dependencies Array of dependency specifications
     * @return Conflict resolution recommendations
     */
    AgentResult resolveDependencyConflicts(Object[] dependencies);
    
    /**
     * Generate integration code for cross-stack communication.
     * 
     * @param sourceStack Source technology stack
     * @param targetStack Target technology stack
     * @param integrationSpec Integration specification
     * @return Generated integration code and configuration
     */
    AgentResult generateIntegrationCode(String sourceStack, String targetStack, Object integrationSpec);
}
