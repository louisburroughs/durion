package durion.workspace.agents.deployment;

/**
 * Options for failover operations
 */
public class FailoverOptions {
    private final boolean automaticRollback;
    private final long rollbackTimeoutMinutes;
    private final boolean notifyStakeholders;

    public FailoverOptions() {
        this.automaticRollback = true;
        this.rollbackTimeoutMinutes = 30;
        this.notifyStakeholders = true;
    }

    public FailoverOptions(boolean automaticRollback, long rollbackTimeoutMinutes, boolean notifyStakeholders) {
        this.automaticRollback = automaticRollback;
        this.rollbackTimeoutMinutes = rollbackTimeoutMinutes;
        this.notifyStakeholders = notifyStakeholders;
    }

    public boolean isAutomaticRollback() {
        return automaticRollback;
    }

    public long getRollbackTimeoutMinutes() {
        return rollbackTimeoutMinutes;
    }

    public boolean isNotifyStakeholders() {
        return notifyStakeholders;
    }
}
