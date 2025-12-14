package durion.workspace.agents.deployment;

import java.util.List;

/**
 * Step in a recovery plan
 */
public class RecoveryStep {
    private final RecoveryStepType stepType;
    private final List<String> targetAgents;
    private final List<String> parameters;

    public RecoveryStep(RecoveryStepType stepType, List<String> targetAgents) {
        this.stepType = stepType;
        this.targetAgents = targetAgents;
        this.parameters = targetAgents;
    }

    public RecoveryStepType getStepType() {
        return stepType;
    }

    public List<String> getParameters() {
        return parameters;
    }
    
    public List<String> getTargetAgents() {
        return targetAgents;
    }

    public enum RecoveryStepType {
        ASSESS_DISASTER,
        STOP_AGENTS,
        VALIDATE_HEALTH,
        RESTORE_FROM_BACKUP,
        REDEPLOY_AGENTS,
        NOTIFY_STAKEHOLDERS,
        COMPLETE_RECOVERY
    }
}
