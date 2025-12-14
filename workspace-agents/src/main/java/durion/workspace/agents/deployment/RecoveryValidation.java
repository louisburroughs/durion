package durion.workspace.agents.deployment;

import java.util.*;

/**
 * Validation results from recovery
 */
public class RecoveryValidation {
    private final boolean successful;
    private final List<String> validatedAgents;
    private final List<String> validationFailures;
    private final String errorMessage;

    public RecoveryValidation(boolean successful, List<String> validatedAgents, List<String> validationFailures, String errorMessage) {
        this.successful = successful;
        this.validatedAgents = new ArrayList<>(validatedAgents);
        this.validationFailures = new ArrayList<>(validationFailures);
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<String> getValidatedAgents() {
        return new ArrayList<>(validatedAgents);
    }

    public List<String> getValidationFailures() {
        return new ArrayList<>(validationFailures);
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
