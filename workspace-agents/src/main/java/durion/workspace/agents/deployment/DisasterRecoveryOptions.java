package durion.workspace.agents.deployment;

import java.util.HashSet;
import java.util.Set;

/**
 * Options for disaster recovery operations
 */
public class DisasterRecoveryOptions {
    private final DisasterType disasterType;
    private final Set<String> affectedAgents;
    private final String affectedWorkspace;
    private final String affectedEnvironment;
    private final boolean useLatestBackup;
    private final String specificBackupId;

    public DisasterRecoveryOptions(DisasterType disasterType,
            Set<String> affectedAgents, String affectedWorkspace,
            String affectedEnvironment, boolean useLatestBackup,
            String specificBackupId) {
        this.disasterType = disasterType;
        this.affectedAgents = new HashSet<>(affectedAgents);
        this.affectedWorkspace = affectedWorkspace;
        this.affectedEnvironment = affectedEnvironment;
        this.useLatestBackup = useLatestBackup;
        this.specificBackupId = specificBackupId;
    }

    public DisasterType getDisasterType() {
        return disasterType;
    }

    public Set<String> getAffectedAgents() {
        return new HashSet<>(affectedAgents);
    }

    public String getAffectedWorkspace() {
        return affectedWorkspace;
    }

    public String getAffectedEnvironment() {
        return affectedEnvironment;
    }

    public boolean isUseLatestBackup() {
        return useLatestBackup;
    }

    public String getSpecificBackupId() {
        return specificBackupId;
    }
}