package durion.workspace.agents.validation;

/**
 * Severity levels for validation issues
 */
public enum ValidationSeverity {
    ERROR,      // Critical issues that prevent validation from passing
    WARNING,    // Issues that should be addressed but don't prevent validation
    INFO        // Informational issues for awareness
}