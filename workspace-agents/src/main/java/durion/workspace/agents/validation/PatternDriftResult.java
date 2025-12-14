package durion.workspace.agents.validation;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Result of pattern drift detection
 */
public class PatternDriftResult {
    
    private final List<PatternDrift> detectedDrifts;
    private final boolean hasDrift;
    private final Instant timestamp;
    
    public PatternDriftResult(List<PatternDrift> detectedDrifts) {
        this.detectedDrifts = new ArrayList<>(detectedDrifts);
        this.hasDrift = !detectedDrifts.isEmpty();
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a result with no drift detected
     */
    public static PatternDriftResult noDrift() {
        return new PatternDriftResult(new ArrayList<>());
    }
    
    // Getters
    public List<PatternDrift> getDetectedDrifts() { return new ArrayList<>(detectedDrifts); }
    public boolean hasDrift() { return hasDrift; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets drifts by severity
     */
    public List<PatternDrift> getDriftsBySeverity(PatternDrift.DriftSeverity severity) {
        return detectedDrifts.stream()
            .filter(drift -> drift.getSeverity() == severity)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets high severity drifts
     */
    public List<PatternDrift> getHighSeverityDrifts() {
        return getDriftsBySeverity(PatternDrift.DriftSeverity.HIGH);
    }
    
    /**
     * Checks if there are high severity drifts
     */
    public boolean hasHighSeverityDrift() {
        return !getHighSeverityDrifts().isEmpty();
    }
    
    /**
     * Gets the drift count
     */
    public int getDriftCount() {
        return detectedDrifts.size();
    }
    
    @Override
    public String toString() {
        return String.format("PatternDriftResult{hasDrift=%s, drifts=%d}", hasDrift, detectedDrifts.size());
    }
}