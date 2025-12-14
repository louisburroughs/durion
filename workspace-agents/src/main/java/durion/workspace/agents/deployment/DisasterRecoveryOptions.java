package durion.workspace.agents.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Options for disaster recovery
 */
public class DisasterRecoveryOptions {
    private final DisasterType disasterType;
    private final boolean fullRecovery;
    private final long timeoutMinutes;
    private final List<String> affectedAgents;
    private final String affectedEnvironment;
    private final String affectedWorkspace;

    public DisasterRecoveryOptions(DisasterType disasterType, boolean fullRecovery, long timeoutMinutes) {
        this.disasterType = disasterType;
        this.fullRecovery = fullRecovery;
        this.timeoutMinutes = timeoutMinutes;
        this.affectedAgents = new ArrayList<>();
        this.affectedEnvironment = null;
        this.affectedWorkspace = null;
    }

    public DisasterRecoveryOptions(DisasterType disasterType, boolean fullRecovery, long timeoutMinutes,
                                   List<String> affectedAgents, String affectedEnvironment, String affectedWorkspace) {
        this.disasterType = disasterType;
        this.fullRecovery = fullRecovery;
        this.timeoutMinutes = timeoutMinutes;
        this.affectedAgents = affectedAgents != null ? new ArrayList<>(affectedAgents) : new ArrayList<>();
        this.affectedEnvironment = affectedEnvironment;
        this.affectedWorkspace = affectedWorkspace;
    }

    public DisasterType getDisasterType() {
        return disasterType;
    }

    public boolean isFullRecovery() {
        return fullRecovery;
    }

    public long getTimeoutMinutes() {
        return timeoutMinutes;
    }
    
    public List<String> getAffectedAgents() {
        return new ArrayList<>(affectedAgents);
    }
    
    public String getAffectedEnvironment() {
        return affectedEnvironment;
    }
    
    public String getAffectedWorkspace() {
        return affectedWorkspace;
    }
}
