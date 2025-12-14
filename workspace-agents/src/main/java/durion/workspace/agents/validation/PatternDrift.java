package durion.workspace.agents.validation;

import java.time.Instant;

/**
 * Represents a detected pattern drift in agent responses
 */
public class PatternDrift {
    
    private final DriftType type;
    private final DriftSeverity severity;
    private final String description;
    private final Instant timestamp;
    
    public PatternDrift(DriftType type, DriftSeverity severity, String description) {
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.timestamp = Instant.now();
    }
    
    // Getters
    public DriftType getType() { return type; }
    public DriftSeverity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("[%s-%s] %s", type, severity, description);
    }
    
    /**
     * Types of pattern drift
     */
    public enum DriftType {
        ARCHITECTURAL_SHIFT,    // Changes in architectural patterns
        SECURITY_METHOD,        // Changes in security approaches
        TECHNOLOGY_CHANGE,      // Changes in technology choices
        COMMUNICATION_PATTERN   // Changes in communication patterns
    }
    
    /**
     * Severity levels for pattern drift
     */
    public enum DriftSeverity {
        HIGH,    // Significant drift that requires immediate attention
        MEDIUM,  // Moderate drift that should be reviewed
        LOW      // Minor drift for awareness
    }
}