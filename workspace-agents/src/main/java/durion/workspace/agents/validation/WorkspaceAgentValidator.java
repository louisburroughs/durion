package durion.workspace.agents.validation;

import durion.workspace.agents.core.*;
import durion.workspace.agents.monitoring.PerformanceMonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates workspace agents for cross-layer consistency and pattern drift detection.
 * Implements guidance rollback and error recovery mechanisms for workspace-level decisions.
 * 
 * Requirements: 1.4, 1.5 - Cross-layer validation and pattern drift detection
 */
public class WorkspaceAgentValidator {
    
    private final Map<String, WorkspaceAgent> registeredAgents;
    private final Map<String, ValidationHistory> validationHistory;
    private final ConsistencyChecker consistencyChecker;
    private final PatternDriftDetector patternDriftDetector;
    private final GuidanceRollbackManager rollbackManager;
    private final PerformanceMonitor performanceMonitor;
    
    // Validation rules and thresholds
    private final Map<String, ValidationRule> validationRules;
    private final ValidationThresholds thresholds;
    
    public WorkspaceAgentValidator(PerformanceMonitor performanceMonitor) {
        this.registeredAgents = new ConcurrentHashMap<>();
        this.validationHistory = new ConcurrentHashMap<>();
        this.consistencyChecker = new ConsistencyChecker();
        this.patternDriftDetector = new PatternDriftDetector();
        this.rollbackManager = new GuidanceRollbackManager();
        this.performanceMonitor = performanceMonitor;
        this.validationRules = new ConcurrentHashMap<>();
        this.thresholds = new ValidationThresholds();
        
        initializeValidationRules();
    }
    
    /**
     * Registers an agent for validation
     */
    public void registerAgent(WorkspaceAgent agent) {
        String agentId = agent.getAgentId();
        registeredAgents.put(agentId, agent);
        validationHistory.put(agentId, new ValidationHistory(agentId));
    }
    
    /**
     * Validates cross-layer consistency for agent recommendations
     */
    public ValidationResult validateCrossLayerConsistency(AgentResponse response, 
                                                        List<AgentResponse> relatedResponses) {
        Instant startTime = Instant.now();
        
        try {
            // Phase 1: Validate individual response
            ValidationResult individualResult = validateIndividualResponse(response);
            if (!individualResult.isValid()) {
                return individualResult;
            }
            
            // Phase 2: Check consistency with related responses
            ConsistencyCheckResult consistencyResult = consistencyChecker.checkConsistency(
                response, relatedResponses);
            
            // Phase 3: Detect pattern drift
            PatternDriftResult driftResult = patternDriftDetector.detectDrift(
                response, getHistoricalResponses(response.getRequestId()));
            
            // Phase 4: Combine results
            ValidationResult combinedResult = combineValidationResults(
                individualResult, consistencyResult, driftResult);
            
            // Record validation metrics
            Duration validationTime = Duration.between(startTime, Instant.now());
            recordValidationMetrics(response, validationTime, combinedResult.isValid());
            
            return combinedResult;
            
        } catch (Exception e) {
            Duration validationTime = Duration.between(startTime, Instant.now());
            recordValidationMetrics(response, validationTime, false);
            
            return ValidationResult.error("Validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validates an individual agent response
     */
    private ValidationResult validateIndividualResponse(AgentResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Check response completeness
        if (response.getGuidance() == null || response.getGuidance().trim().isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, 
                "Response guidance is empty", "guidance-completeness"));
        }
        
        // Check response quality
        if (response.getQualityScore() < thresholds.getMinimumQualityScore()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, 
                String.format("Response quality score (%d) below threshold (%d)", 
                    response.getQualityScore(), thresholds.getMinimumQualityScore()),
                "quality-threshold"));
        }
        
        // Check for security recommendations if needed
        if (requiresSecurityValidation(response) && !response.hasSecurityRecommendations()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, 
                "Security-related request missing security recommendations", "security-missing"));
        }
        
        // Check for architectural guidance if needed
        if (requiresArchitecturalValidation(response) && !response.hasArchitecturalGuidance()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, 
                "Architectural request missing architectural guidance", "architecture-missing"));
        }
        
        // Apply custom validation rules
        for (ValidationRule rule : validationRules.values()) {
            if (rule.applies(response)) {
                ValidationIssue issue = rule.validate(response);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }
        
        return new ValidationResult(issues);
    }
    
    /**
     * Checks if response requires security validation
     */
    private boolean requiresSecurityValidation(AgentResponse response) {
        String guidance = response.getGuidance().toLowerCase();
        return guidance.contains("security") || 
               guidance.contains("authentication") || 
               guidance.contains("authorization") ||
               guidance.contains("jwt") ||
               guidance.contains("encryption");
    }
    
    /**
     * Checks if response requires architectural validation
     */
    private boolean requiresArchitecturalValidation(AgentResponse response) {
        String guidance = response.getGuidance().toLowerCase();
        return guidance.contains("architecture") || 
               guidance.contains("design") || 
               guidance.contains("pattern") ||
               guidance.contains("component") ||
               guidance.contains("integration");
    }
    
    /**
     * Gets historical responses for pattern drift detection
     */
    private List<AgentResponse> getHistoricalResponses(String requestId) {
        // This would retrieve historical responses from storage
        // For now, return empty list
        return new ArrayList<>();
    }
    
    /**
     * Combines validation results from different checks
     */
    private ValidationResult combineValidationResults(ValidationResult individualResult,
                                                    ConsistencyCheckResult consistencyResult,
                                                    PatternDriftResult driftResult) {
        List<ValidationIssue> allIssues = new ArrayList<>(individualResult.getIssues());
        
        // Add consistency issues
        if (!consistencyResult.isConsistent()) {
            for (ConsistencyViolation violation : consistencyResult.getViolations()) {
                allIssues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "Consistency violation: " + violation.getDescription(),
                    "consistency-" + violation.getType().toString().toLowerCase()
                ));
            }
        }
        
        // Add pattern drift issues
        if (driftResult.hasDrift()) {
            for (PatternDrift drift : driftResult.getDetectedDrifts()) {
                ValidationSeverity severity = drift.getSeverity() == PatternDrift.DriftSeverity.HIGH ? 
                    ValidationSeverity.ERROR : ValidationSeverity.WARNING;
                
                allIssues.add(new ValidationIssue(
                    severity,
                    "Pattern drift detected: " + drift.getDescription(),
                    "drift-" + drift.getType().toString().toLowerCase()
                ));
            }
        }
        
        return new ValidationResult(allIssues);
    }
    
    /**
     * Records validation metrics
     */
    private void recordValidationMetrics(AgentResponse response, Duration validationTime, boolean valid) {
        // Record in performance monitor
        performanceMonitor.recordRequest(response.getRequestId(), validationTime, valid);
        
        // Update validation history
        String agentId = extractAgentId(response);
        if (agentId != null) {
            ValidationHistory history = validationHistory.get(agentId);
            if (history != null) {
                history.recordValidation(validationTime, valid);
            }
        }
    }
    
    /**
     * Extracts agent ID from response metadata
     */
    private String extractAgentId(AgentResponse response) {
        Object agentId = response.getMetadata("agent-id");
        return agentId != null ? agentId.toString() : null;
    }
    
    /**
     * Performs rollback of guidance when validation fails
     */
    public RollbackResult rollbackGuidance(String requestId, String reason) {
        return rollbackManager.rollbackGuidance(requestId, reason);
    }
    
    /**
     * Recovers from validation errors
     */
    public RecoveryResult recoverFromValidationError(ValidationResult validationResult, 
                                                   AgentRequest originalRequest) {
        List<ValidationIssue> criticalIssues = validationResult.getIssues().stream()
            .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR)
            .collect(Collectors.toList());
        
        if (criticalIssues.isEmpty()) {
            return RecoveryResult.noRecoveryNeeded();
        }
        
        // Determine recovery strategy
        RecoveryStrategy strategy = determineRecoveryStrategy(criticalIssues, originalRequest);
        
        switch (strategy) {
            case RETRY_WITH_DIFFERENT_AGENT:
                return RecoveryResult.retryWithDifferentAgent(
                    "Validation failed, retrying with alternative agent");
                
            case MODIFY_REQUEST:
                return RecoveryResult.modifyRequest(
                    "Modifying request to address validation issues",
                    createModifiedRequest(originalRequest, criticalIssues));
                
            case ESCALATE_TO_HUMAN:
                return RecoveryResult.escalateToHuman(
                    "Critical validation issues require human intervention",
                    criticalIssues);
                
            case APPLY_FALLBACK_GUIDANCE:
                return RecoveryResult.applyFallbackGuidance(
                    "Applying fallback guidance due to validation failures",
                    createFallbackGuidance(originalRequest));
                
            default:
                return RecoveryResult.unrecoverable(
                    "No suitable recovery strategy available");
        }
    }
    
    /**
     * Determines recovery strategy based on validation issues
     */
    private RecoveryStrategy determineRecoveryStrategy(List<ValidationIssue> criticalIssues, 
                                                     AgentRequest originalRequest) {
        // Check for consistency issues
        boolean hasConsistencyIssues = criticalIssues.stream()
            .anyMatch(issue -> issue.getType().startsWith("consistency-"));
        
        // Check for security issues
        boolean hasSecurityIssues = criticalIssues.stream()
            .anyMatch(issue -> issue.getType().startsWith("security-"));
        
        // Check for pattern drift
        boolean hasPatternDrift = criticalIssues.stream()
            .anyMatch(issue -> issue.getType().startsWith("drift-"));
        
        // Security issues require human escalation
        if (hasSecurityIssues) {
            return RecoveryStrategy.ESCALATE_TO_HUMAN;
        }
        
        // Consistency issues can often be resolved by trying different agent
        if (hasConsistencyIssues && !hasPatternDrift) {
            return RecoveryStrategy.RETRY_WITH_DIFFERENT_AGENT;
        }
        
        // Pattern drift might require request modification
        if (hasPatternDrift) {
            return RecoveryStrategy.MODIFY_REQUEST;
        }
        
        // Default to fallback guidance
        return RecoveryStrategy.APPLY_FALLBACK_GUIDANCE;
    }
    
    /**
     * Creates a modified request to address validation issues
     */
    private AgentRequest createModifiedRequest(AgentRequest originalRequest, 
                                             List<ValidationIssue> issues) {
        // Create a new request with modifications based on issues
        AgentRequest modifiedRequest = new AgentRequest(
            UUID.randomUUID().toString(),
            originalRequest.getRequestType(),
            "Modified: " + originalRequest.getDescription(),
            originalRequest.getRequiredCapability(),
            originalRequest.getSourceProject(),
            originalRequest.getTargetProject(),
            originalRequest.getPriority()
        );
        
        // Copy original parameters
        for (Map.Entry<String, Object> entry : originalRequest.getParameters().entrySet()) {
            modifiedRequest.addParameter(entry.getKey(), entry.getValue());
        }
        
        // Add validation context
        modifiedRequest.addParameter("validation-issues", issues);
        modifiedRequest.addParameter("original-request-id", originalRequest.getRequestId());
        
        return modifiedRequest;
    }
    
    /**
     * Creates fallback guidance for failed validation
     */
    private String createFallbackGuidance(AgentRequest originalRequest) {
        StringBuilder fallback = new StringBuilder();
        fallback.append("Fallback guidance for request: ").append(originalRequest.getDescription()).append("\n\n");
        
        // Provide generic guidance based on request type
        String requestType = originalRequest.getRequestType().toLowerCase();
        
        if (requestType.contains("architecture")) {
            fallback.append("- Follow established architectural patterns\n");
            fallback.append("- Ensure consistency with existing system design\n");
            fallback.append("- Consider scalability and maintainability\n");
        } else if (requestType.contains("security")) {
            fallback.append("- Apply security best practices\n");
            fallback.append("- Use established authentication mechanisms\n");
            fallback.append("- Ensure proper authorization controls\n");
        } else if (requestType.contains("integration")) {
            fallback.append("- Use standard integration patterns\n");
            fallback.append("- Ensure API contract compatibility\n");
            fallback.append("- Implement proper error handling\n");
        } else {
            fallback.append("- Follow workspace-level standards\n");
            fallback.append("- Ensure cross-project compatibility\n");
            fallback.append("- Consider performance implications\n");
        }
        
        fallback.append("\nNote: This is fallback guidance. Consider consulting with domain experts for specific requirements.");
        
        return fallback.toString();
    }
    
    /**
     * Initializes validation rules
     */
    private void initializeValidationRules() {
        // Rule: Security responses must include specific security measures
        validationRules.put("security-completeness", new ValidationRule() {
            @Override
            public boolean applies(AgentResponse response) {
                return requiresSecurityValidation(response);
            }
            
            @Override
            public ValidationIssue validate(AgentResponse response) {
                String guidance = response.getGuidance().toLowerCase();
                List<String> recommendations = response.getRecommendations();
                
                boolean hasEncryption = guidance.contains("encryption") || 
                    recommendations.stream().anyMatch(r -> r.toLowerCase().contains("encryption"));
                boolean hasAuthentication = guidance.contains("authentication") || 
                    recommendations.stream().anyMatch(r -> r.toLowerCase().contains("authentication"));
                
                if (!hasEncryption && !hasAuthentication) {
                    return new ValidationIssue(ValidationSeverity.ERROR,
                        "Security response missing encryption or authentication details",
                        "security-completeness");
                }
                
                return null;
            }
        });
        
        // Rule: Cross-project responses must address both projects
        validationRules.put("cross-project-coverage", new ValidationRule() {
            @Override
            public boolean applies(AgentResponse response) {
                Object crossProject = response.getMetadata("cross-project");
                return crossProject != null && Boolean.parseBoolean(crossProject.toString());
            }
            
            @Override
            public ValidationIssue validate(AgentResponse response) {
                String guidance = response.getGuidance().toLowerCase();
                
                boolean mentionsPositivity = guidance.contains("positivity") || guidance.contains("spring boot");
                boolean mentionsMoqui = guidance.contains("moqui") || guidance.contains("frontend");
                
                if (!mentionsPositivity || !mentionsMoqui) {
                    return new ValidationIssue(ValidationSeverity.WARNING,
                        "Cross-project response should address both positivity and moqui_example",
                        "cross-project-coverage");
                }
                
                return null;
            }
        });
    }
    
    /**
     * Adds a custom validation rule
     */
    public void addValidationRule(String ruleId, ValidationRule rule) {
        validationRules.put(ruleId, rule);
    }
    
    /**
     * Gets validation statistics
     */
    public ValidationStatistics getValidationStatistics() {
        return new ValidationStatistics(validationHistory.values());
    }
    
    /**
     * Checks if validator meets performance requirements
     */
    public boolean meetsPerformanceRequirements() {
        ValidationStatistics stats = getValidationStatistics();
        return stats.getAverageValidationTime().toMillis() <= 2000 && // 2 seconds for validation
               stats.getOverallValidationRate() >= 0.95; // 95% validation success rate
    }
    
    /**
     * Enum for recovery strategies
     */
    public enum RecoveryStrategy {
        RETRY_WITH_DIFFERENT_AGENT,
        MODIFY_REQUEST,
        ESCALATE_TO_HUMAN,
        APPLY_FALLBACK_GUIDANCE
    }
}