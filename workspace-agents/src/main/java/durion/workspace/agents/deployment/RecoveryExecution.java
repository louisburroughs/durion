package durion.workspace.agents.deployment;

import java.time.Instant;
import java.util.*;

/**
 * Results from recovery execution
 */
public class RecoveryExecution {
    private final RecoveryPlan plan;
    private final List<String> recoveredAgents;
    private final List<String> failedRecoveries;
    private final Instant executionTime;

    public RecoveryExecution(RecoveryPlan plan, List<String> recoveredAgents, List<String> failedRecoveries) {
        this.plan = plan;
        this.recoveredAgents = new ArrayList<>(recoveredAgents);
        this.failedRecoveries = new ArrayList<>(failedRecoveries);
        this.executionTime = Instant.now();
    }

    public RecoveryPlan getPlan() {
        return plan;
    }

    public List<String> getRecoveredAgents() {
        return new ArrayList<>(recoveredAgents);
    }

    public List<String> getFailedRecoveries() {
        return new ArrayList<>(failedRecoveries);
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

    public boolean isSuccessful() {
        return failedRecoveries.isEmpty();
    }
}
