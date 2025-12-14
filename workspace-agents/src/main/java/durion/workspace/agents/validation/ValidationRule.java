package durion.workspace.agents.validation;

import durion.workspace.agents.core.AgentResponse;

/**
 * Interface for custom validation rules
 */
public interface ValidationRule {
    
    /**
     * Checks if this rule applies to the given response
     */
    boolean applies(AgentResponse response);
    
    /**
     * Validates the response and returns an issue if validation fails
     * @return ValidationIssue if validation fails, null if validation passes
     */
    ValidationIssue validate(AgentResponse response);
}