package durion.workspace.agents.deployment;

/**
 * Options for failover operations
 */
public class FailoverOptions {
    private final String targetEnvironment;
    private final boolean preserveState;
    private final int maxFailoverTime;
    private final boolean notifyStakeholders;

    public FailoverOptions(String targetEnvironment, boolean preserveState,
            int maxFailoverTime, boolean notifyStakeholders) {
        this.targetEnvironment = targetEnvironment;
        this.preserveState = preserveState;
        this.maxFailoverTime = maxFailoverTime;
        this.notifyStakeholders = notifyStakeholders;
    }

    public FailoverOptions() {
        this(null, true, 300, true); // 5 minutes max failover time
    }

    public String getTargetEnvironment() {
        return targetEnvironment;
    }

    public boolean isPreserveState() {
        return preserveState;
    }

    public int getMaxFailoverTime() {
        return maxFailoverTime;
    }

    public boolean isNotifyStakeholders() {
        return notifyStakeholders;
    }
}
