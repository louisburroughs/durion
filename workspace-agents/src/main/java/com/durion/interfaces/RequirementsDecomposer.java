package com.durion.interfaces;

import com.durion.core.AgentResult;

/**
 * Interface for analyzing complete business requirements and intelligently 
 * splitting implementation between moqui frontend and durion-positivity-backend.
 */
public interface RequirementsDecomposer extends WorkspaceAgent {
    
    /**
     * Decompose business requirements into frontend and backend components.
     * 
     * @param requirements Complete business requirements
     * @return Decomposition result with frontend and backend specifications
     */
    AgentResult decomposeRequirements(String requirements);
    
    /**
     * Identify work for durion-moqui-frontend (screens, forms, Vue.js components, UI state).
     * Must complete within 30 seconds with 95% accuracy.
     * 
     * @param requirements Business requirements
     * @return Frontend work specification
     */
    AgentResult identifyFrontendWork(String requirements);
    
    /**
     * Identify work for durion-positivity-backend (APIs, business logic, data persistence).
     * Must complete within 30 seconds with 98% accuracy.
     * 
     * @param requirements Business requirements
     * @return Backend work specification
     */
    AgentResult identifyBackendWork(String requirements);
    
    /**
     * Generate OpenAPI specifications for all integration points.
     * Must achieve 100% completeness.
     * 
     * @param frontendSpec Frontend work specification
     * @param backendSpec Backend work specification
     * @return OpenAPI specifications
     */
    AgentResult generateApiSpecs(Object frontendSpec, Object backendSpec);
    
    /**
     * Enforce architectural boundaries ensuring no business logic in frontend.
     * Must achieve 100% accuracy.
     * 
     * @param decomposition Requirements decomposition
     * @return Boundary validation result
     */
    AgentResult enforceArchitecturalBoundaries(Object decomposition);
}
