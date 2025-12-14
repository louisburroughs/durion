package durion.workspace.agents.coordination;

import durion.workspace.agents.core.AgentResponse;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Resolves conflicts between competing agent recommendations
 */
public class ConflictResolver {
    
    /**
     * Resolves conflicts between multiple agent responses
     */
    public ConflictResolutionResult resolveConflicts(List<AgentResponse> responses, 
                                                   CoordinationWorkflow workflow) {
        
        // Filter successful responses
        List<AgentResponse> successfulResponses = responses.stream()
            .filter(AgentResponse::isSuccess)
            .collect(Collectors.toList());
        
        if (successfulResponses.isEmpty()) {
            return ConflictResolutionResult.noValidResponses(workflow.getCoordinationId());
        }
        
        if (successfulResponses.size() == 1) {
            return ConflictResolutionResult.noConflicts(successfulResponses.get(0));
        }
        
        // Detect conflicts
        List<Conflict> conflicts = detectConflicts(successfulResponses);
        
        if (conflicts.isEmpty()) {
            // No conflicts - merge responses
            AgentResponse mergedResponse = mergeCompatibleResponses(successfulResponses, workflow);
            return ConflictResolutionResult.merged(mergedResponse, successfulResponses);
        }
        
        // Resolve conflicts
        AgentResponse resolvedResponse = resolveDetectedConflicts(conflicts, successfulResponses, workflow);
        return ConflictResolutionResult.resolved(resolvedResponse, conflicts);
    }
    
    /**
     * Detects conflicts between agent responses
     */
    private List<Conflict> detectConflicts(List<AgentResponse> responses) {
        List<Conflict> conflicts = new ArrayList<>();
        
        // Check for architectural conflicts
        conflicts.addAll(detectArchitecturalConflicts(responses));
        
        // Check for security conflicts
        conflicts.addAll(detectSecurityConflicts(responses));
        
        // Check for performance conflicts
        conflicts.addAll(detectPerformanceConflicts(responses));
        
        // Check for technology stack conflicts
        conflicts.addAll(detectTechnologyConflicts(responses));
        
        return conflicts;
    }
    
    /**
     * Detects architectural conflicts between responses
     */
    private List<Conflict> detectArchitecturalConflicts(List<AgentResponse> responses) {
        List<Conflict> conflicts = new ArrayList<>();
        
        // Look for conflicting architectural patterns
        Map<String, List<AgentResponse>> patternGroups = new HashMap<>();
        
        for (AgentResponse response : responses) {
            String guidance = response.getGuidance().toLowerCase();
            
            // Detect microservices vs monolith recommendations
            if (guidance.contains("microservice")) {
                patternGroups.computeIfAbsent("microservices", k -> new ArrayList<>()).add(response);
            } else if (guidance.contains("monolith")) {
                patternGroups.computeIfAbsent("monolith", k -> new ArrayList<>()).add(response);
            }
            
            // Detect synchronous vs asynchronous patterns
            if (guidance.contains("synchronous") || guidance.contains("rest api")) {
                patternGroups.computeIfAbsent("synchronous", k -> new ArrayList<>()).add(response);
            } else if (guidance.contains("asynchronous") || guidance.contains("event")) {
                patternGroups.computeIfAbsent("asynchronous", k -> new ArrayList<>()).add(response);
            }
        }
        
        // Check for conflicts
        if (patternGroups.containsKey("microservices") && patternGroups.containsKey("monolith")) {
            conflicts.add(new Conflict(ConflictType.ARCHITECTURAL_PATTERN,
                "Conflicting architectural patterns: microservices vs monolith",
                patternGroups.get("microservices"), patternGroups.get("monolith")));
        }
        
        if (patternGroups.containsKey("synchronous") && patternGroups.containsKey("asynchronous")) {
            conflicts.add(new Conflict(ConflictType.COMMUNICATION_PATTERN,
                "Conflicting communication patterns: synchronous vs asynchronous",
                patternGroups.get("synchronous"), patternGroups.get("asynchronous")));
        }
        
        return conflicts;
    }
    
    /**
     * Detects security conflicts between responses
     */
    private List<Conflict> detectSecurityConflicts(List<AgentResponse> responses) {
        List<Conflict> conflicts = new ArrayList<>();
        
        Map<String, List<AgentResponse>> securityGroups = new HashMap<>();
        
        for (AgentResponse response : responses) {
            List<String> recommendations = response.getRecommendations();
            
            for (String rec : recommendations) {
                String lowerRec = rec.toLowerCase();
                
                // Detect authentication method conflicts
                if (lowerRec.contains("jwt")) {
                    securityGroups.computeIfAbsent("jwt", k -> new ArrayList<>()).add(response);
                } else if (lowerRec.contains("session")) {
                    securityGroups.computeIfAbsent("session", k -> new ArrayList<>()).add(response);
                } else if (lowerRec.contains("oauth")) {
                    securityGroups.computeIfAbsent("oauth", k -> new ArrayList<>()).add(response);
                }
                
                // Detect encryption conflicts
                if (lowerRec.contains("aes-256")) {
                    securityGroups.computeIfAbsent("aes256", k -> new ArrayList<>()).add(response);
                } else if (lowerRec.contains("aes-128")) {
                    securityGroups.computeIfAbsent("aes128", k -> new ArrayList<>()).add(response);
                }
            }
        }
        
        // Check for authentication conflicts
        int authMethods = 0;
        if (securityGroups.containsKey("jwt")) authMethods++;
        if (securityGroups.containsKey("session")) authMethods++;
        if (securityGroups.containsKey("oauth")) authMethods++;
        
        if (authMethods > 1) {
            conflicts.add(new Conflict(ConflictType.SECURITY_AUTHENTICATION,
                "Multiple authentication methods recommended",
                responses, new ArrayList<>()));
        }
        
        // Check for encryption conflicts
        if (securityGroups.containsKey("aes256") && securityGroups.containsKey("aes128")) {
            conflicts.add(new Conflict(ConflictType.SECURITY_ENCRYPTION,
                "Conflicting encryption standards: AES-256 vs AES-128",
                securityGroups.get("aes256"), securityGroups.get("aes128")));
        }
        
        return conflicts;
    }
    
    /**
     * Detects performance conflicts between responses
     */
    private List<Conflict> detectPerformanceConflicts(List<AgentResponse> responses) {
        List<Conflict> conflicts = new ArrayList<>();
        
        Map<String, List<AgentResponse>> performanceGroups = new HashMap<>();
        
        for (AgentResponse response : responses) {
            String guidance = response.getGuidance().toLowerCase();
            List<String> recommendations = response.getRecommendations();
            
            // Check for caching strategy conflicts
            if (guidance.contains("redis") || recommendations.stream().anyMatch(r -> r.toLowerCase().contains("redis"))) {
                performanceGroups.computeIfAbsent("redis", k -> new ArrayList<>()).add(response);
            }
            if (guidance.contains("memcached") || recommendations.stream().anyMatch(r -> r.toLowerCase().contains("memcached"))) {
                performanceGroups.computeIfAbsent("memcached", k -> new ArrayList<>()).add(response);
            }
            if (guidance.contains("in-memory") || recommendations.stream().anyMatch(r -> r.toLowerCase().contains("in-memory"))) {
                performanceGroups.computeIfAbsent("inmemory", k -> new ArrayList<>()).add(response);
            }
        }
        
        // Check for caching conflicts
        int cachingStrategies = performanceGroups.size();
        if (cachingStrategies > 1) {
            conflicts.add(new Conflict(ConflictType.PERFORMANCE_CACHING,
                "Multiple caching strategies recommended",
                responses, new ArrayList<>()));
        }
        
        return conflicts;
    }
    
    /**
     * Detects technology stack conflicts between responses
     */
    private List<Conflict> detectTechnologyConflicts(List<AgentResponse> responses) {
        List<Conflict> conflicts = new ArrayList<>();
        
        Map<String, List<AgentResponse>> techGroups = new HashMap<>();
        
        for (AgentResponse response : responses) {
            String guidance = response.getGuidance().toLowerCase();
            
            // Database technology conflicts
            if (guidance.contains("postgresql")) {
                techGroups.computeIfAbsent("postgresql", k -> new ArrayList<>()).add(response);
            } else if (guidance.contains("mysql")) {
                techGroups.computeIfAbsent("mysql", k -> new ArrayList<>()).add(response);
            } else if (guidance.contains("mongodb")) {
                techGroups.computeIfAbsent("mongodb", k -> new ArrayList<>()).add(response);
            }
            
            // Frontend framework conflicts
            if (guidance.contains("react")) {
                techGroups.computeIfAbsent("react", k -> new ArrayList<>()).add(response);
            } else if (guidance.contains("vue")) {
                techGroups.computeIfAbsent("vue", k -> new ArrayList<>()).add(response);
            } else if (guidance.contains("angular")) {
                techGroups.computeIfAbsent("angular", k -> new ArrayList<>()).add(response);
            }
        }
        
        // Check for database conflicts
        List<String> databases = List.of("postgresql", "mysql", "mongodb");
        long dbCount = databases.stream().mapToLong(db -> techGroups.containsKey(db) ? 1 : 0).sum();
        if (dbCount > 1) {
            conflicts.add(new Conflict(ConflictType.TECHNOLOGY_DATABASE,
                "Multiple database technologies recommended",
                responses, new ArrayList<>()));
        }
        
        // Check for frontend conflicts
        List<String> frontendFrameworks = List.of("react", "vue", "angular");
        long frontendCount = frontendFrameworks.stream().mapToLong(fw -> techGroups.containsKey(fw) ? 1 : 0).sum();
        if (frontendCount > 1) {
            conflicts.add(new Conflict(ConflictType.TECHNOLOGY_FRONTEND,
                "Multiple frontend frameworks recommended",
                responses, new ArrayList<>()));
        }
        
        return conflicts;
    }
    
    /**
     * Merges compatible responses when no conflicts exist
     */
    private AgentResponse mergeCompatibleResponses(List<AgentResponse> responses, CoordinationWorkflow workflow) {
        StringBuilder mergedGuidance = new StringBuilder();
        List<String> mergedRecommendations = new ArrayList<>();
        
        // Combine guidance from all responses
        for (int i = 0; i < responses.size(); i++) {
            AgentResponse response = responses.get(i);
            if (i > 0) {
                mergedGuidance.append("\n\n");
            }
            mergedGuidance.append(response.getGuidance());
            mergedRecommendations.addAll(response.getRecommendations());
        }
        
        AgentResponse mergedResponse = new AgentResponse(
            workflow.getRequest().getRequestId(),
            mergedGuidance.toString(),
            mergedRecommendations,
            Instant.now()
        );
        
        mergedResponse.addMetadata("coordination-type", "merged");
        mergedResponse.addMetadata("source-responses", responses.size());
        
        return mergedResponse;
    }
    
    /**
     * Resolves detected conflicts and creates a unified response
     */
    private AgentResponse resolveDetectedConflicts(List<Conflict> conflicts, 
                                                 List<AgentResponse> responses, 
                                                 CoordinationWorkflow workflow) {
        
        StringBuilder resolvedGuidance = new StringBuilder();
        List<String> resolvedRecommendations = new ArrayList<>();
        
        resolvedGuidance.append("Coordination resolved the following conflicts:\n\n");
        
        for (Conflict conflict : conflicts) {
            String resolution = resolveConflict(conflict);
            resolvedGuidance.append("- ").append(conflict.getDescription()).append("\n");
            resolvedGuidance.append("  Resolution: ").append(resolution).append("\n\n");
            
            // Add resolution as recommendation
            resolvedRecommendations.add(resolution);
        }
        
        // Add non-conflicting recommendations
        for (AgentResponse response : responses) {
            for (String rec : response.getRecommendations()) {
                if (!isConflictingRecommendation(rec, conflicts)) {
                    resolvedRecommendations.add(rec);
                }
            }
        }
        
        AgentResponse resolvedResponse = new AgentResponse(
            workflow.getRequest().getRequestId(),
            resolvedGuidance.toString(),
            resolvedRecommendations,
            Instant.now()
        );
        
        resolvedResponse.addMetadata("coordination-type", "conflict-resolved");
        resolvedResponse.addMetadata("conflicts-resolved", conflicts.size());
        
        return resolvedResponse;
    }
    
    /**
     * Resolves a specific conflict
     */
    private String resolveConflict(Conflict conflict) {
        switch (conflict.getType()) {
            case ARCHITECTURAL_PATTERN:
                return "Use microservices for scalability with monolithic deployment for simplicity in development";
            
            case COMMUNICATION_PATTERN:
                return "Use synchronous APIs for user-facing operations and asynchronous events for background processing";
            
            case SECURITY_AUTHENTICATION:
                return "Implement JWT tokens as primary authentication with session fallback for legacy systems";
            
            case SECURITY_ENCRYPTION:
                return "Use AES-256 encryption for all new implementations";
            
            case PERFORMANCE_CACHING:
                return "Implement Redis for distributed caching with in-memory fallback for local operations";
            
            case TECHNOLOGY_DATABASE:
                return "Use PostgreSQL as primary database with appropriate data modeling for requirements";
            
            case TECHNOLOGY_FRONTEND:
                return "Standardize on Vue.js for consistency with existing Moqui framework integration";
            
            default:
                return "Apply workspace-level architectural standards and consult with architecture team";
        }
    }
    
    /**
     * Checks if a recommendation conflicts with resolved conflicts
     */
    private boolean isConflictingRecommendation(String recommendation, List<Conflict> conflicts) {
        String lowerRec = recommendation.toLowerCase();
        
        for (Conflict conflict : conflicts) {
            switch (conflict.getType()) {
                case SECURITY_AUTHENTICATION:
                    if (lowerRec.contains("session") && !lowerRec.contains("jwt")) {
                        return true;
                    }
                    break;
                case SECURITY_ENCRYPTION:
                    if (lowerRec.contains("aes-128")) {
                        return true;
                    }
                    break;
                case TECHNOLOGY_DATABASE:
                    if (lowerRec.contains("mysql") || lowerRec.contains("mongodb")) {
                        return true;
                    }
                    break;
                case TECHNOLOGY_FRONTEND:
                    if (lowerRec.contains("react") || lowerRec.contains("angular")) {
                        return true;
                    }
                    break;
            }
        }
        
        return false;
    }
    
    /**
     * Enum for conflict types
     */
    public enum ConflictType {
        ARCHITECTURAL_PATTERN,
        COMMUNICATION_PATTERN,
        SECURITY_AUTHENTICATION,
        SECURITY_ENCRYPTION,
        PERFORMANCE_CACHING,
        TECHNOLOGY_DATABASE,
        TECHNOLOGY_FRONTEND
    }
    
    /**
     * Inner class representing a conflict
     */
    public static class Conflict {
        private final ConflictType type;
        private final String description;
        private final List<AgentResponse> conflictingResponses1;
        private final List<AgentResponse> conflictingResponses2;
        
        public Conflict(ConflictType type, String description,
                       List<AgentResponse> conflictingResponses1,
                       List<AgentResponse> conflictingResponses2) {
            this.type = type;
            this.description = description;
            this.conflictingResponses1 = new ArrayList<>(conflictingResponses1);
            this.conflictingResponses2 = new ArrayList<>(conflictingResponses2);
        }
        
        public ConflictType getType() { return type; }
        public String getDescription() { return description; }
        public List<AgentResponse> getConflictingResponses1() { return new ArrayList<>(conflictingResponses1); }
        public List<AgentResponse> getConflictingResponses2() { return new ArrayList<>(conflictingResponses2); }
    }
}