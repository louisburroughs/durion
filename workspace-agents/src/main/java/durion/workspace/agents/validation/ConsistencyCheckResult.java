package durion.workspace.agents.validation;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Result of consistency checking between agent responses
 */
public class ConsistencyCheckResult {
    
    private final List<ConsistencyViolation> violations;
    private final boolean consistent;
    private final Instant timestamp;
    
    public ConsistencyCheckResult(List<ConsistencyViolation> violations) {
        this.violations = new ArrayList<>(violations);
        this.consistent = violations.isEmpty();
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a consistent result (no violations)
     */
    public static ConsistencyCheckResult consistent() {
        return new ConsistencyCheckResult(new ArrayList<>());
    }
    
    // Getters
    public List<ConsistencyViolation> getViolations() { return new ArrayList<>(violations); }
    public boolean isConsistent() { return consistent; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets violations by type
     */
    public List<ConsistencyViolation> getViolationsByType(ConsistencyViolationType type) {
        return violations.stream()
            .filter(violation -> violation.getType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the number of violations
     */
    public int getViolationCount() {
        return violations.size();
    }
    
    /**
     * Checks if there are architectural violations
     */
    public boolean hasArchitecturalViolations() {
        return violations.stream()
            .anyMatch(v -> v.getType() == ConsistencyViolationType.ARCHITECTURAL_PATTERN ||
                          v.getType() == ConsistencyViolationType.COMMUNICATION_PATTERN);
    }
    
    /**
     * Checks if there are security violations
     */
    public boolean hasSecurityViolations() {
        return violations.stream()
            .anyMatch(v -> v.getType() == ConsistencyViolationType.SECURITY_AUTHENTICATION ||
                          v.getType() == ConsistencyViolationType.SECURITY_ENCRYPTION);
    }
    
    /**
     * Checks if there are technology violations
     */
    public boolean hasTechnologyViolations() {
        return violations.stream()
            .anyMatch(v -> v.getType() == ConsistencyViolationType.TECHNOLOGY_DATABASE ||
                          v.getType() == ConsistencyViolationType.TECHNOLOGY_FRONTEND);
    }
    
    /**
     * Gets consistency quality score (0-100)
     */
    public int getConsistencyQuality() {
        if (consistent) {
            return 100;
        }
        
        // Deduct points based on violation severity and count
        int score = 100;
        
        for (ConsistencyViolation violation : violations) {
            switch (violation.getType()) {
                case SECURITY_AUTHENTICATION:
                case SECURITY_ENCRYPTION:
                    score -= 20; // Security violations are more serious
                    break;
                case ARCHITECTURAL_PATTERN:
                case COMMUNICATION_PATTERN:
                    score -= 15; // Architectural violations are significant
                    break;
                default:
                    score -= 10; // Other violations
                    break;
            }
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Gets consistency summary
     */
    public String getConsistencySummary() {
        if (consistent) {
            return "All responses are consistent with each other";
        }
        
        return String.format("Found %d consistency violations across responses", violations.size());
    }
    
    @Override
    public String toString() {
        return String.format("ConsistencyCheckResult{consistent=%s, violations=%d, quality=%d}",
                consistent, violations.size(), getConsistencyQuality());
    }
}