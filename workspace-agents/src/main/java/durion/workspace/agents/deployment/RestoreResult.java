package durion.workspace.agents.deployment;

import java.time.Instant;

/**
 * Result of a restore operation from backup
 */
public class RestoreResult {
    private final boolean successful;
    private final String errorMessage;
    private final DeployedAgent restoredAgent;
    private final Instant timestamp;
    
    private RestoreResult(boolean successful, String errorMessage, DeployedAgent restoredAgent) {
        this.successful = successful;
        this.errorMessage = errorMessage;
        this.restoredAgent = restoredAgent;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful restore result
     */
    public static RestoreResult success(DeployedAgent restoredAgent) {
        return new RestoreResult(true, null, restoredAgent);
    }
    
    /**
     * Creates a failed restore result
     */
    public static RestoreResult failure(String errorMessage) {
        return new RestoreResult(false, errorMessage, null);
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public DeployedAgent getRestoredAgent() {
        return restoredAgent;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
}
