package durion.workspace.agents.validation;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a validation issue found during agent validation
 */
public class ValidationIssue {
    
    private final ValidationSeverity severity;
    private final String message;
    private final String type;
    private final Instant timestamp;
    
    public ValidationIssue(ValidationSeverity severity, String message, String type) {
        this.severity = severity;
        this.message = message;
        this.type = type;
        this.timestamp = Instant.now();
    }
    
    // Getters
    public ValidationSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Checks if this is a critical issue
     */
    public boolean isCritical() {
        return severity == ValidationSeverity.ERROR;
    }
    
    /**
     * Gets the severity level as integer (higher = more severe)
     */
    public int getSeverityLevel() {
        switch (severity) {
            case ERROR: return 3;
            case WARNING: return 2;
            case INFO: return 1;
            default: return 0;
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s (type: %s)", severity, message, type);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ValidationIssue that = (ValidationIssue) obj;
        return severity == that.severity &&
               message.equals(that.message) &&
               type.equals(that.type);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(severity, message, type);
    }
}

