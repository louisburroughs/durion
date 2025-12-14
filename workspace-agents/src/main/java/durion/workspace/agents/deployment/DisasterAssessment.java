package durion.workspace.agents.deployment;

import java.util.*;

/**
 * Assessment of a disaster
 */
public class DisasterAssessment {
    private final DisasterType disasterType;
    private final DisasterSeverity severity;
    private final List<String> affectedAgents;

    public DisasterAssessment(DisasterType disasterType, DisasterSeverity severity, Collection<String> affectedAgents) {
        this.disasterType = disasterType;
        this.severity = severity;
        this.affectedAgents = new ArrayList<>(affectedAgents);
    }

    public DisasterType getDisasterType() {
        return disasterType;
    }

    public DisasterSeverity getSeverity() {
        return severity;
    }

    public List<String> getAffectedAgents() {
        return new ArrayList<>(affectedAgents);
    }
}
