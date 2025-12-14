package durion.workspace.agents.validation;

/**
 * Types of consistency violations
 */
public enum ConsistencyViolationType {
    ARCHITECTURAL_PATTERN,      // Conflicting architectural patterns
    COMMUNICATION_PATTERN,      // Conflicting communication patterns
    SECURITY_AUTHENTICATION,    // Conflicting authentication methods
    SECURITY_ENCRYPTION,        // Conflicting encryption standards
    TECHNOLOGY_DATABASE,        // Conflicting database technologies
    TECHNOLOGY_FRONTEND,        // Conflicting frontend frameworks
    PERFORMANCE_CACHING         // Conflicting caching strategies
}