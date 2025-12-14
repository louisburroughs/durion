package durion.workspace.agents.validation;

import durion.workspace.agents.core.AgentRequest;

import java.time.Instant;
import java.util.List;

/**
 * Result of validation error recovery
 */
public class RecoveryResult {
    
    private final RecoveryAction action;
    private final String message;
    private final AgentRequest modifiedRequest;
    private final String fallbackGuidance;
    private final List<ValidationIssue> escalationIssues;
    private final Instant timestamp;
    
    private RecoveryResult(RecoveryAction action, String message, AgentRequest modifiedRequest,
                          String fallbackGuidance, List<ValidationIssue> escalationIssues) {
        this.action = action;
        this.message = message;
        this.modifiedRequest = modifiedRequest;
        this.fallbackGuidance = fallbackGuidance;
        this.escalationIssues = escalationIssues;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a recovery result for retrying with a different agent
     */
    public static RecoveryResult retryWithDifferentAgent(String message) {
        return new RecoveryResult(RecoveryAction.RETRY_WITH_DIFFERENT_AGENT, message, null, null, null);
    }
    
    /**
     * Creates a recovery result for modifying the request
     */
    public static RecoveryResult modifyRequest(String message, AgentRequest modifiedRequest) {
        return new RecoveryResult(RecoveryAction.MODIFY_REQUEST, message, modifiedRequest, null, null);
    }
    
    /**
     * Creates a recovery result for escalating to human
     */
    public static RecoveryResult escalateToHuman(String message, List<ValidationIssue> issues) {
        return new RecoveryResult(RecoveryAction.ESCALATE_TO_HUMAN, message, null, null, issues);
    }
    
    /**
     * Creates a recovery result for applying fallback guidance
     */
    public static RecoveryResult applyFallbackGuidance(String message, String fallbackGuidance) {
        return new RecoveryResult(RecoveryAction.APPLY_FALLBACK_GUIDANCE, message, null, fallbackGuidance, null);
    }
    
    /**
     * Creates a recovery result when no recovery is needed
     */
    public static RecoveryResult noRecoveryNeeded() {
        return new RecoveryResult(RecoveryAction.NO_RECOVERY_NEEDED, "No recovery needed", null, null, null);
    }
    
    /**
     * Creates a recovery result when recovery is not possible
     */
    public static RecoveryResult unrecoverable(String message) {
        return new RecoveryResult(RecoveryAction.UNRECOVERABLE, message, null, null, null);
    }
    
    // Getters
    public RecoveryAction getAction() { return action; }
    public String getMessage() { return message; }
    public AgentRequest getModifiedRequest() { return modifiedRequest; }
    public String getFallbackGuidance() { return fallbackGuidance; }
    public List<ValidationIssue> getEscalationIssues() { return escalationIssues; }
    public Instant getTimestamp() { return timestamp; }
    
    /**
     * Checks if recovery is possible
     */
    public boolean isRecoverable() {
        return action != RecoveryAction.UNRECOVERABLE;
    }
    
    /**
     * Checks if human intervention is required
     */
    public boolean requiresHumanIntervention() {
        return action == RecoveryAction.ESCALATE_TO_HUMAN;
    }
    
    @Override
    public String toString() {
        return String.format("RecoveryResult{action=%s, message='%s'}", action, message);
    }
    
    /**
     * Enum for recovery actions
     */
    public enum RecoveryAction {
        NO_RECOVERY_NEEDED,         // No recovery action required
        RETRY_WITH_DIFFERENT_AGENT, // Try a different agent
        MODIFY_REQUEST,             // Modify the original request
        ESCALATE_TO_HUMAN,          // Escalate to human intervention
        APPLY_FALLBACK_GUIDANCE,    // Use fallback guidance
        UNRECOVERABLE              // Cannot recover from the error
    }
}