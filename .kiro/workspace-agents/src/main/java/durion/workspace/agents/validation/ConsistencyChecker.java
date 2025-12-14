package durion.workspace.agents.validation;

import durion.workspace.agents.core.AgentResponse;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Checks consistency between agent responses across different layers
 */
public class ConsistencyChecker {
    
    /**
     * Checks consistency between a response and related responses
     */
    public ConsistencyCheckResult checkConsistency(AgentResponse response, List<AgentResponse> relatedResponses) {
        List<ConsistencyViolation> violations = new ArrayList<>();
        
        // Check architectural consistency
        violations.addAll(checkArchitecturalConsistency(response, relatedResponses));
        
        // Check security consistency
        violations.addAll(checkSecurityConsistency(response, relatedResponses));
        
        // Check technology stack consistency
        violations.addAll(checkTechnologyConsistency(response, relatedResponses));
        
        // Check performance consistency
        violations.addAll(checkPerformanceConsistency(response, relatedResponses));
        
        return new ConsistencyCheckResult(violations);
    }
    
    /**
     * Checks architectural consistency
     */
    private List<ConsistencyViolation> checkArchitecturalConsistency(AgentResponse response, 
                                                                   List<AgentResponse> relatedResponses) {
        List<ConsistencyViolation> violations = new ArrayList<>();
        
        String responseGuidance = response.getGuidance().toLowerCase();
        
        // Check for conflicting architectural patterns
        Map<String, Integer> patternCounts = new HashMap<>();
        
        // Count patterns in current response
        if (responseGuidance.contains("microservice")) {
            patternCounts.put("microservices", patternCounts.getOrDefault("microservices", 0) + 1);
        }
        if (responseGuidance.contains("monolith")) {
            patternCounts.put("monolith", patternCounts.getOrDefault("monolith", 0) + 1);
        }
        if (responseGuidance.contains("event-driven")) {
            patternCounts.put("event-driven", patternCounts.getOrDefault("event-driven", 0) + 1);
        }
        if (responseGuidance.contains("synchronous")) {
            patternCounts.put("synchronous", patternCounts.getOrDefault("synchronous", 0) + 1);
        }
        
        // Count patterns in related responses
        for (AgentResponse related : relatedResponses) {
            String relatedGuidance = related.getGuidance().toLowerCase();
            
            if (relatedGuidance.contains("microservice")) {
                patternCounts.put("microservices", patternCounts.getOrDefault("microservices", 0) + 1);
            }
            if (relatedGuidance.contains("monolith")) {
                patternCounts.put("monolith", patternCounts.getOrDefault("monolith", 0) + 1);
            }
            if (relatedGuidance.contains("event-driven")) {
                patternCounts.put("event-driven", patternCounts.getOrDefault("event-driven", 0) + 1);
            }
            if (relatedGuidance.contains("synchronous")) {
                patternCounts.put("synchronous", patternCounts.getOrDefault("synchronous", 0) + 1);
            }
        }
        
        // Check for conflicts
        if (patternCounts.getOrDefault("microservices", 0) > 0 && 
            patternCounts.getOrDefault("monolith", 0) > 0) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.ARCHITECTURAL_PATTERN,
                "Conflicting architectural patterns: microservices vs monolith",
                "Choose either microservices or monolithic architecture consistently"
            ));
        }
        
        if (patternCounts.getOrDefault("event-driven", 0) > 0 && 
            patternCounts.getOrDefault("synchronous", 0) > 0) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.COMMUNICATION_PATTERN,
                "Conflicting communication patterns: event-driven vs synchronous",
                "Establish consistent communication patterns across components"
            ));
        }
        
        return violations;
    }
    
    /**
     * Checks security consistency
     */
    private List<ConsistencyViolation> checkSecurityConsistency(AgentResponse response, 
                                                              List<AgentResponse> relatedResponses) {
        List<ConsistencyViolation> violations = new ArrayList<>();
        
        // Collect security recommendations
        Map<String, Integer> securityMethods = new HashMap<>();
        
        // Check current response
        collectSecurityMethods(response, securityMethods);
        
        // Check related responses
        for (AgentResponse related : relatedResponses) {
            collectSecurityMethods(related, securityMethods);
        }
        
        // Check for authentication conflicts
        int authMethods = 0;
        if (securityMethods.getOrDefault("jwt", 0) > 0) authMethods++;
        if (securityMethods.getOrDefault("session", 0) > 0) authMethods++;
        if (securityMethods.getOrDefault("oauth", 0) > 0) authMethods++;
        
        if (authMethods > 1) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.SECURITY_AUTHENTICATION,
                "Multiple authentication methods recommended across responses",
                "Standardize on a single authentication method (preferably JWT)"
            ));
        }
        
        // Check for encryption conflicts
        if (securityMethods.getOrDefault("aes-256", 0) > 0 && 
            securityMethods.getOrDefault("aes-128", 0) > 0) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.SECURITY_ENCRYPTION,
                "Conflicting encryption standards: AES-256 vs AES-128",
                "Use AES-256 encryption consistently for all security implementations"
            ));
        }
        
        return violations;
    }
    
    /**
     * Collects security methods from a response
     */
    private void collectSecurityMethods(AgentResponse response, Map<String, Integer> securityMethods) {
        String guidance = response.getGuidance().toLowerCase();
        List<String> recommendations = response.getRecommendations();
        
        // Check guidance and recommendations for security methods
        String combinedText = guidance + " " + String.join(" ", recommendations).toLowerCase();
        
        if (combinedText.contains("jwt")) {
            securityMethods.put("jwt", securityMethods.getOrDefault("jwt", 0) + 1);
        }
        if (combinedText.contains("session")) {
            securityMethods.put("session", securityMethods.getOrDefault("session", 0) + 1);
        }
        if (combinedText.contains("oauth")) {
            securityMethods.put("oauth", securityMethods.getOrDefault("oauth", 0) + 1);
        }
        if (combinedText.contains("aes-256")) {
            securityMethods.put("aes-256", securityMethods.getOrDefault("aes-256", 0) + 1);
        }
        if (combinedText.contains("aes-128")) {
            securityMethods.put("aes-128", securityMethods.getOrDefault("aes-128", 0) + 1);
        }
    }
    
    /**
     * Checks technology stack consistency
     */
    private List<ConsistencyViolation> checkTechnologyConsistency(AgentResponse response, 
                                                                List<AgentResponse> relatedResponses) {
        List<ConsistencyViolation> violations = new ArrayList<>();
        
        Map<String, Integer> technologies = new HashMap<>();
        
        // Collect technologies from all responses
        collectTechnologies(response, technologies);
        for (AgentResponse related : relatedResponses) {
            collectTechnologies(related, technologies);
        }
        
        // Check for database conflicts
        int dbCount = 0;
        if (technologies.getOrDefault("postgresql", 0) > 0) dbCount++;
        if (technologies.getOrDefault("mysql", 0) > 0) dbCount++;
        if (technologies.getOrDefault("mongodb", 0) > 0) dbCount++;
        
        if (dbCount > 1) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.TECHNOLOGY_DATABASE,
                "Multiple database technologies recommended",
                "Standardize on PostgreSQL for consistency with existing architecture"
            ));
        }
        
        // Check for frontend framework conflicts
        int frontendCount = 0;
        if (technologies.getOrDefault("vue", 0) > 0) frontendCount++;
        if (technologies.getOrDefault("react", 0) > 0) frontendCount++;
        if (technologies.getOrDefault("angular", 0) > 0) frontendCount++;
        
        if (frontendCount > 1) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.TECHNOLOGY_FRONTEND,
                "Multiple frontend frameworks recommended",
                "Use Vue.js consistently for frontend development"
            ));
        }
        
        return violations;
    }
    
    /**
     * Collects technologies from a response
     */
    private void collectTechnologies(AgentResponse response, Map<String, Integer> technologies) {
        String guidance = response.getGuidance().toLowerCase();
        
        // Database technologies
        if (guidance.contains("postgresql")) {
            technologies.put("postgresql", technologies.getOrDefault("postgresql", 0) + 1);
        }
        if (guidance.contains("mysql")) {
            technologies.put("mysql", technologies.getOrDefault("mysql", 0) + 1);
        }
        if (guidance.contains("mongodb")) {
            technologies.put("mongodb", technologies.getOrDefault("mongodb", 0) + 1);
        }
        
        // Frontend frameworks
        if (guidance.contains("vue")) {
            technologies.put("vue", technologies.getOrDefault("vue", 0) + 1);
        }
        if (guidance.contains("react")) {
            technologies.put("react", technologies.getOrDefault("react", 0) + 1);
        }
        if (guidance.contains("angular")) {
            technologies.put("angular", technologies.getOrDefault("angular", 0) + 1);
        }
    }
    
    /**
     * Checks performance consistency
     */
    private List<ConsistencyViolation> checkPerformanceConsistency(AgentResponse response, 
                                                                 List<AgentResponse> relatedResponses) {
        List<ConsistencyViolation> violations = new ArrayList<>();
        
        Map<String, Integer> performanceStrategies = new HashMap<>();
        
        // Collect performance strategies
        collectPerformanceStrategies(response, performanceStrategies);
        for (AgentResponse related : relatedResponses) {
            collectPerformanceStrategies(related, performanceStrategies);
        }
        
        // Check for caching conflicts
        int cachingStrategies = 0;
        if (performanceStrategies.getOrDefault("redis", 0) > 0) cachingStrategies++;
        if (performanceStrategies.getOrDefault("memcached", 0) > 0) cachingStrategies++;
        if (performanceStrategies.getOrDefault("in-memory", 0) > 0) cachingStrategies++;
        
        if (cachingStrategies > 1) {
            violations.add(new ConsistencyViolation(
                ConsistencyViolationType.PERFORMANCE_CACHING,
                "Multiple caching strategies recommended",
                "Use Redis for distributed caching with in-memory fallback for local operations"
            ));
        }
        
        return violations;
    }
    
    /**
     * Collects performance strategies from a response
     */
    private void collectPerformanceStrategies(AgentResponse response, Map<String, Integer> strategies) {
        String guidance = response.getGuidance().toLowerCase();
        List<String> recommendations = response.getRecommendations();
        
        String combinedText = guidance + " " + String.join(" ", recommendations).toLowerCase();
        
        if (combinedText.contains("redis")) {
            strategies.put("redis", strategies.getOrDefault("redis", 0) + 1);
        }
        if (combinedText.contains("memcached")) {
            strategies.put("memcached", strategies.getOrDefault("memcached", 0) + 1);
        }
        if (combinedText.contains("in-memory")) {
            strategies.put("in-memory", strategies.getOrDefault("in-memory", 0) + 1);
        }
    }
}