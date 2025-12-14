package durion.workspace.agents.deployment;

import java.time.Duration;
import java.util.*;

/**
 * Result of disaster recovery
 */
public class DisasterRecoveryResult {
    private final boolean successful;
    private final Duration recoveryTime;
    private final List<String> recoveredAgents;
    private final String errorMessage;

    private DisasterRecoveryResult(boolean successful, Duration recoveryTime, List<String> recoveredAgents, String errorMessage) {
        this.successful = successful;
        this.recoveryTime = recoveryTime;
        this.recoveredAgents = recoveredAgents != null ? new ArrayList<>(recoveredAgents) : new ArrayList<>();
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Duration getRecoveryTime() {
        return recoveryTime;
    }

    public List<String> getRecoveredAgents() {
        return new ArrayList<>(recoveredAgents);
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Creates a successful disaster recovery result
     */
    public static DisasterRecoveryResult success(Duration recoveryTime, List<String> recoveredAgents) {
        return new DisasterRecoveryResult(true, recoveryTime, recoveredAgents, null);
    }
    
    /**
     * Creates a failed disaster recovery result
     */
    public static DisasterRecoveryResult failure(String errorMessage) {
        return new DisasterRecoveryResult(false, Duration.ZERO, new ArrayList<>(), errorMessage);
    }
}
