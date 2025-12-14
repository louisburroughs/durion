package durion.workspace.agents.validation;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Result of workspace agent validation
 */
public class ValidationResult {
    
    private final List<ValidationIssue> issues;
    private final boolean valid;
    private final Instant timestamp;
    
    public ValidationResult(List<ValidationIssue> issues) {
        this.issues = new ArrayList<>(issues);
        this.valid = issues.stream().noneMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult(new ArrayList<>());
    }
    
    /**
     * Creates an error validation result
     */
    public static ValidationResult error(String message) {
        List<ValidationIssue> issues = new ArrayList<>();
        issues.add(new ValidationIssue(ValidationSeverity.ERROR, message, "validation-error"));
        return new ValidationResult(issues);
    }
    
    /**
     * Creates a warning validation result
     */
    public static ValidationResult warning(String message) {
        List<ValidationIssue> issues = new ArrayList<>();
        issues.add(new ValidationIssue(ValidationSeverity.WARNING, message, "validation-warning"));
        return new ValidationResult(issues);
    }
    
    // Getters
    public List<ValidationIssue> getIssues() { return new ArrayList<>(issues); }
    public boolean isValid() { return valid; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Gets issues by severity
     */
    public List<ValidationIssue> getIssuesBySeverity(ValidationSeverity severity) {
        return issues.stream()
            .filter(issue -> issue.getSeverity() == severity)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets error issues
     */
    public List<ValidationIssue> getErrors() {
        return getIssuesBySeverity(ValidationSeverity.ERROR);
    }
    
    /**
     * Gets warning issues
     */
    public List<ValidationIssue> getWarnings() {
        return getIssuesBySeverity(ValidationSeverity.WARNING);
    }
    
    /**
     * Gets info issues
     */
    public List<ValidationIssue> getInfos() {
        return getIssuesBySeverity(ValidationSeverity.INFO);
    }
    
    /**
     * Checks if result has errors
     */
    public boolean hasErrors() {
        return !getErrors().isEmpty();
    }
    
    /**
     * Checks if result has warnings
     */
    public boolean hasWarnings() {
        return !getWarnings().isEmpty();
    }
    
    /**
     * Gets the total number of issues
     */
    public int getIssueCount() {
        return issues.size();
    }
    
    /**
     * Gets validation quality score (0-100)
     */
    public int getValidationQuality() {
        if (hasErrors()) {
            return Math.max(0, 50 - (getErrors().size() * 10));
        }
        
        if (hasWarnings()) {
            return Math.max(70, 90 - (getWarnings().size() * 5));
        }
        
        return 100;
    }
    
    /**
     * Gets validation summary
     */
    public String getValidationSummary() {
        if (isValid() && issues.isEmpty()) {
            return "Validation passed with no issues";
        }
        
        if (isValid() && hasWarnings()) {
            return String.format("Validation passed with %d warnings", getWarnings().size());
        }
        
        if (hasErrors()) {
            return String.format("Validation failed with %d errors and %d warnings", 
                getErrors().size(), getWarnings().size());
        }
        
        return "Validation completed";
    }
    
    /**
     * Merges this result with another validation result
     */
    public ValidationResult merge(ValidationResult other) {
        List<ValidationIssue> mergedIssues = new ArrayList<>(this.issues);
        mergedIssues.addAll(other.issues);
        return new ValidationResult(mergedIssues);
    }
    
    @Override
    public String toString() {
        return String.format("ValidationResult{valid=%s, errors=%d, warnings=%d, quality=%d}",
                valid, getErrors().size(), getWarnings().size(), getValidationQuality());
    }
}