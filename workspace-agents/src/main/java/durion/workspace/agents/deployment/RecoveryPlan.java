package durion.workspace.agents.deployment;

import java.util.*;

/**
 * Represents a disaster recovery plan
 */
public class RecoveryPlan {
    private final String planId;
    private final String name;
    private final List<RecoveryStep> steps;
    private final int priority;
    private final long estimatedDurationMinutes;

    public RecoveryPlan(String planId, List<RecoveryStep> steps) {
        this.planId = planId;
        this.name = planId;
        this.steps = new ArrayList<>(steps);
        this.priority = 0;
        this.estimatedDurationMinutes = 0;
    }

    public RecoveryPlan(String planId, String name, List<RecoveryStep> steps) {
        this.planId = planId;
        this.name = name;
        this.steps = new ArrayList<>(steps);
        this.priority = 0;
        this.estimatedDurationMinutes = 0;
    }

    public RecoveryPlan(String planId, String name, List<RecoveryStep> steps, int priority, long estimatedDurationMinutes) {
        this.planId = planId;
        this.name = name;
        this.steps = new ArrayList<>(steps);
        this.priority = priority;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public String getPlanId() {
        return planId;
    }

    public String getName() {
        return name;
    }

    public List<RecoveryStep> getSteps() {
        return new ArrayList<>(steps);
    }

    public int getPriority() {
        return priority;
    }

    public long getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }
}
