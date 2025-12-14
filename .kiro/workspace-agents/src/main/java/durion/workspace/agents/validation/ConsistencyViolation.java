package durion.workspace.agents.validation;

import java.time.Instant;

/**
 * Represents a consistency violation between agent responses
 */
public class ConsistencyViolation {
    
    private final ConsistencyViolationType type;
    private final String description;
    private final String resolution;
    private final Instant timestamp;
    
    public ConsistencyViolation(ConsistencyViolationType type, String description, String resolution) {
        this.type = type;
        this.description = description;
        this.resolution = resolution;
        this.timestamp = Instant.now();
    }
    
    // Getters
    public ConsistencyViolationType getType() { return type; }
    public String getDescription() { return description; }
    public String getResolution() { return resolution; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets the severity of this violation
     */
    public ViolationSeverity getSeverity() {
        switch (type) {
            case SECURITY_AUTHENTICATION:
            case SECURITY_ENCRYPTION:
                return ViolationSeverity.HIGH;
            case ARCHITECTURAL_PATTERN:
            case COMMUNICATION_PATTERN:
                return ViolationSeverity.MEDIUM;
            default:
                return ViolationSeverity.LOW;
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - Resolution: %s", type, description, resolution);
    }
    
    /**
     * Enum for violation severity
     */
    public enum ViolationSeverity {
        HIGH,    // Critical violations that must be resolved
        MEDIUM,  // Important violations that should be resolved
        LOW      // Minor violations that can be addressed later
    }
}

