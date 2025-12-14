package durion.workspace.agents.deployment;

import java.time.Instant;

/**
 * Result of packaging operation
 */
public class PackagingResult {
    private final boolean successful;
    private final AgentPackage agentPackage;
    private final String errorMessage;
    private final Instant timestamp;

    private PackagingResult(boolean successful, AgentPackage agentPackage, String errorMessage) {
        this.successful = successful;
        this.agentPackage = agentPackage;
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful packaging result
     */
    public static PackagingResult success(AgentPackage agentPackage) {
        return new PackagingResult(true, agentPackage, null);
    }
    
    /**
     * Creates a failed packaging result
     */
    public static PackagingResult failure(String errorMessage) {
        return new PackagingResult(false, null, errorMessage);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public AgentPackage getAgentPackage() {
        return agentPackage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
}
