package durion.workspace.agents.deployment;

import java.time.Duration;

/**
 * Result of failover operation
 */
public class FailoverResult {
    private final boolean successful;
    private final String agentId;
    private final String newInstanceId;
    private final Duration failoverTime;
    private final String errorMessage;

    public FailoverResult(boolean successful, String agentId, String newInstanceId, Duration failoverTime, String errorMessage) {
        this.successful = successful;
        this.agentId = agentId;
        this.newInstanceId = newInstanceId;
        this.failoverTime = failoverTime;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getNewInstanceId() {
        return newInstanceId;
    }

    public Duration getFailoverTime() {
        return failoverTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
