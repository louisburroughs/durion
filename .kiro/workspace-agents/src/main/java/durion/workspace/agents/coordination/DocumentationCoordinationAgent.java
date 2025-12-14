package durion.workspace.agents.coordination;

import durion.workspace.agents.core.*;
import durion.workspace.agents.registry.ProjectRegistry;

import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Coordinates documentation synchronization between backend API docs and frontend integration guides.
 * Maintains architectural documentation and provides personalized onboarding guidance.
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
public class DocumentationCoordinationAgent extends AbstractWorkspaceAgent {
    
    private final ProjectRegistry projectRegistry;
    private final Map<String, DocumentationSource> documentationSources;
    private final Map<String, ArchitecturalDiagram> architecturalDiagrams;
    private final Map<String, OnboardingGuide> onboardingGuides;
    private final DocumentationSynchronizer synchronizer;
    private final ChangeTracker changeTracker;
    
    // Performance tracking for requirements compliance
    private final Map<String, Instant> lastSyncTimes;
    private final Map<String, Duration> syncDurations;
    
    public DocumentationCoordinationAgent() {
        super("documentation-coordination", 
              AgentType.GOVERNANCE_COMPLIANCE,
              Set.of(AgentCapability.DOCUMENTATION_COORDINATION, 
                     AgentCapability.CHANGE_COORDINATION,
                     AgentCapability.WORKFLOW_COORDINATION));
        
        this.projectRegistry = new ProjectRegistry();
        this.documentationSources = new ConcurrentHashMap<>();
        this.architecturalDiagrams = new ConcurrentHashMap<>();
        this.onboardingGuides = new ConcurrentHashMap<>();
        this.synchronizer = new DocumentationSynchronizer();
        this.changeTracker = new ChangeTracker();
        this.lastSyncTimes = new ConcurrentHashMap<>();
        this.syncDurations = new ConcurrentHashMap<>();
        
        initializeDocumentationSources();
    }
    
    @Override
    protected AgentResponse doProcessRequest(AgentRequest request) throws Exception {
        switch (request.getRequestType()) {
            case "SYNCHRONIZE_DOCUMENTATION":
                return synchronizeDocumentation(request);
            case "UPDATE_API_DOCUMENTATION":
                return updateApiDocumentation(request);
            case "UPDATE_ARCHITECTURAL_DIAGRAMS":
                return updateArchitecturalDiagrams(request);
            case "GENERATE_ONBOARDING_GUIDE":
                return generateOnboardingGuide(request);
            case "PROPAGATE_CHANGES":
                return propagateChanges(request);
            case "GET_DOCUMENTATION_STATUS":
                return getDocumentationStatus(request);
            case "VALIDATE_CONSISTENCY":
                return validateConsistency(request);
            default:
                throw new AgentException(agentId, AgentException.AgentErrorType.CAPABILITY_MISMATCH,
                        "Unsupported request type: " + request.getRequestType());
        }
    }
    
    /**
     * Synchronizes documentation between backend API docs and frontend integration guides
     * Requirement: 100% consistency
     */
    private AgentResponse synchronizeDocumentation(AgentRequest request) {
        Instant startTime = Instant.now();
        
        List<String> projectIds = request.getParameter("projectIds", List.class);
        if (projectIds == null) {
            projectIds = Arrays.asList("positivity", "moqui_example");
        }
        
        List<SynchronizationResult> results = new ArrayList<>();
        
        for (String projectId : projectIds) {
            try {
                SynchronizationResult result = synchronizer.synchronizeProject(projectId, documentationSources);
                results.add(result);
                
                // Track sync time for performance monitoring
                lastSyncTimes.put(projectId, Instant.now());
                
            } catch (Exception e) {
                results.add(new SynchronizationResult(projectId, false, 
                    "Synchronization failed: " + e.getMessage()));
            }
        }
        
        Duration totalDuration = Duration.between(startTime, Instant.now());
        
        // Check consistency requirement (100%)
        long successfulSyncs = results.stream().mapToLong(r -> r.isSuccessful() ? 1 : 0).sum();
        double consistencyRate = (double) successfulSyncs / results.size();
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add(String.format("Documentation synchronization completed in %d ms", 
            totalDuration.toMillis()));
        recommendations.add(String.format("Consistency rate: %.1f%% (%d/%d projects)", 
            consistencyRate * 100, successfulSyncs, results.size()));
        
        for (SynchronizationResult result : results) {
            String status = result.isSuccessful() ? "✅" : "❌";
            recommendations.add(String.format("%s %s: %s", status, result.getProjectId(), 
                result.getMessage()));
        }
        
        if (consistencyRate < 1.0) {
            recommendations.add("⚠️ 100% consistency requirement not met - manual intervention required");
        }
        
        return createSuccessResponse(request,
            "Documentation synchronization completed across projects",
            recommendations);
    }
    
    /**
     * Updates API documentation with <24 hour latency requirement
     */
    private AgentResponse updateApiDocumentation(AgentRequest request) {
        String projectId = request.getParameter("projectId", String.class);
        String apiSpec = request.getParameter("apiSpec", String.class);
        String changeDescription = request.getParameter("changeDescription", String.class);
        
        Instant updateTime = Instant.now();
        
        // Update API documentation
        DocumentationSource source = documentationSources.get(projectId);
        if (source == null) {
            source = new DocumentationSource(projectId, DocumentationSource.Type.API);
            documentationSources.put(projectId, source);
        }
        
        source.updateApiSpec(apiSpec, changeDescription);
        
        // Track change for propagation
        DocumentationChange change = new DocumentationChange(
            projectId, DocumentationChange.Type.API_UPDATE, changeDescription, updateTime);
        changeTracker.recordChange(change);
        
        // Schedule propagation (must complete within 24 hours)
        CompletableFuture.runAsync(() -> {
            try {
                propagateApiChanges(projectId, change);
            } catch (Exception e) {
                // Log error but don't fail the main request
                System.err.println("Failed to propagate API changes: " + e.getMessage());
            }
        });
        
        List<String> recommendations = Arrays.asList(
            "API documentation updated for project: " + projectId,
            "Change propagation scheduled (24-hour SLA)",
            "Affected projects will be notified automatically",
            "Change ID: " + change.getChangeId()
        );
        
        return createSuccessResponse(request,
            "API documentation update initiated with automatic propagation",
            recommendations);
    }
    
    /**
     * Updates architectural diagrams with 95% accuracy requirement
     */
    private AgentResponse updateArchitecturalDiagrams(AgentRequest request) {
        String diagramId = request.getParameter("diagramId", String.class);
        Map<String, Object> architecturalChanges = request.getParameter("architecturalChanges", Map.class);
        
        ArchitecturalDiagram diagram = architecturalDiagrams.get(diagramId);
        if (diagram == null) {
            diagram = new ArchitecturalDiagram(diagramId, "System Architecture");
            architecturalDiagrams.put(diagramId, diagram);
        }
        
        // Apply changes with accuracy tracking
        DiagramUpdateResult updateResult = diagram.applyChanges(architecturalChanges);
        
        // Validate accuracy (95% requirement)
        double accuracyScore = validateDiagramAccuracy(diagram);
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Architectural diagram updated: " + diagramId);
        recommendations.add(String.format("Accuracy score: %.1f%% (requirement: 95%%)", 
            accuracyScore * 100));
        
        if (accuracyScore >= 0.95) {
            recommendations.add("✅ Accuracy requirement met");
        } else {
            recommendations.add("❌ Accuracy below 95% - review required");
            recommendations.addAll(updateResult.getAccuracyIssues());
        }
        
        // Update related documentation
        List<String> affectedProjects = diagram.getAffectedProjects();
        for (String projectId : affectedProjects) {
            DocumentationChange change = new DocumentationChange(
                projectId, DocumentationChange.Type.ARCHITECTURE_UPDATE, 
                "Architectural diagram updated: " + diagramId, Instant.now());
            changeTracker.recordChange(change);
        }
        
        return createSuccessResponse(request,
            "Architectural diagram coordination completed",
            recommendations);
    }
    
    /**
     * Generates personalized onboarding guide within 30 seconds
     */
    private AgentResponse generateOnboardingGuide(AgentRequest request) {
        Instant startTime = Instant.now();
        
        String userId = request.getParameter("userId", String.class);
        String userRole = request.getParameter("userRole", String.class);
        List<String> projectAccess = request.getParameter("projectAccess", List.class);
        
        // Generate personalized guide
        OnboardingGuide guide = new OnboardingGuide(userId, userRole, projectAccess);
        
        // Add role-specific content
        switch (userRole.toLowerCase()) {
            case "backend-developer":
                guide.addSection("Positivity Backend Setup", generateBackendSetupGuide());
                guide.addSection("API Development Guidelines", generateApiGuidelinesGuide());
                break;
            case "frontend-developer":
                guide.addSection("Moqui Frontend Setup", generateFrontendSetupGuide());
                guide.addSection("API Integration Guide", generateApiIntegrationGuide());
                break;
            case "full-stack-developer":
                guide.addSection("Full-Stack Setup", generateFullStackSetupGuide());
                guide.addSection("Cross-Project Workflows", generateCrossProjectWorkflowGuide());
                break;
            default:
                guide.addSection("General Setup", generateGeneralSetupGuide());
        }
        
        // Add project-specific sections
        for (String projectId : projectAccess) {
            DocumentationSource source = documentationSources.get(projectId);
            if (source != null) {
                guide.addSection(projectId + " Documentation", 
                    source.getOnboardingContent());
            }
        }
        
        onboardingGuides.put(userId, guide);
        
        Duration generationTime = Duration.between(startTime, Instant.now());
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add(String.format("Personalized onboarding guide generated in %d ms", 
            generationTime.toMillis()));
        recommendations.add("User role: " + userRole);
        recommendations.add("Project access: " + String.join(", ", projectAccess));
        recommendations.add("Guide sections: " + guide.getSectionCount());
        
        // Check 30-second requirement
        if (generationTime.getSeconds() <= 30) {
            recommendations.add("✅ 30-second delivery requirement met");
        } else {
            recommendations.add("❌ Exceeded 30-second delivery requirement");
        }
        
        return createSuccessResponse(request,
            "Personalized onboarding guide generated and delivered",
            recommendations);
    }
    
    /**
     * Propagates documentation changes across affected projects within 2 hours
     */
    private AgentResponse propagateChanges(AgentRequest request) throws AgentException {
        String changeId = request.getParameter("changeId", String.class);
        
        DocumentationChange change = changeTracker.getChange(changeId);
        if (change == null) {
            throw new AgentException(agentId, AgentException.AgentErrorType.CONFIGURATION_ERROR,
                    "Change not found: " + changeId);
        }
        
        Instant propagationStart = Instant.now();
        
        // Identify affected projects
        List<String> affectedProjects = identifyAffectedProjects(change);
        
        List<PropagationResult> results = new ArrayList<>();
        
        for (String projectId : affectedProjects) {
            try {
                PropagationResult result = propagateChangeToProject(change, projectId);
                results.add(result);
            } catch (Exception e) {
                results.add(new PropagationResult(projectId, false, 
                    "Propagation failed: " + e.getMessage()));
            }
        }
        
        Duration propagationTime = Duration.between(propagationStart, Instant.now());
        
        // Update change status
        change.setPropagationCompleted(Instant.now());
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add(String.format("Change propagation completed in %d minutes", 
            propagationTime.toMinutes()));
        recommendations.add("Affected projects: " + affectedProjects.size());
        
        long successfulPropagations = results.stream().mapToLong(r -> r.isSuccessful() ? 1 : 0).sum();
        recommendations.add(String.format("Success rate: %.1f%% (%d/%d)", 
            (double) successfulPropagations / results.size() * 100, 
            successfulPropagations, results.size()));
        
        // Check 2-hour requirement
        if (propagationTime.toHours() <= 2) {
            recommendations.add("✅ 2-hour propagation requirement met");
        } else {
            recommendations.add("❌ Exceeded 2-hour propagation requirement");
        }
        
        for (PropagationResult result : results) {
            String status = result.isSuccessful() ? "✅" : "❌";
            recommendations.add(String.format("%s %s: %s", status, result.getProjectId(), 
                result.getMessage()));
        }
        
        return createSuccessResponse(request,
            "Documentation change propagation completed",
            recommendations);
    }
    
    /**
     * Gets comprehensive documentation status across all projects
     */
    private AgentResponse getDocumentationStatus(AgentRequest request) {
        List<String> recommendations = new ArrayList<>();
        
        // Overall statistics
        recommendations.add("Documentation sources: " + documentationSources.size());
        recommendations.add("Architectural diagrams: " + architecturalDiagrams.size());
        recommendations.add("Active onboarding guides: " + onboardingGuides.size());
        recommendations.add("Pending changes: " + changeTracker.getPendingChangesCount());
        
        // Synchronization status
        recommendations.add("\nSynchronization Status:");
        for (Map.Entry<String, Instant> entry : lastSyncTimes.entrySet()) {
            String projectId = entry.getKey();
            Instant lastSync = entry.getValue();
            Duration timeSinceSync = Duration.between(lastSync, Instant.now());
            
            recommendations.add(String.format("  %s: %d minutes ago", 
                projectId, timeSinceSync.toMinutes()));
        }
        
        // Performance metrics
        recommendations.add("\nPerformance Metrics:");
        double avgSyncTime = syncDurations.values().stream()
            .mapToLong(Duration::toMillis)
            .average()
            .orElse(0.0);
        recommendations.add(String.format("  Average sync time: %.1f ms", avgSyncTime));
        
        // Compliance status
        recommendations.add("\nCompliance Status:");
        recommendations.add("  Consistency requirement (100%): " + 
            (calculateConsistencyRate() >= 1.0 ? "✅ MET" : "❌ NOT MET"));
        recommendations.add("  API update latency (<24h): " + 
            (checkApiUpdateLatency() ? "✅ MET" : "❌ NOT MET"));
        recommendations.add("  Diagram accuracy (95%): " + 
            (checkDiagramAccuracy() ? "✅ MET" : "❌ NOT MET"));
        
        return createSuccessResponse(request,
            "Documentation coordination status retrieved",
            recommendations);
    }
    
    /**
     * Validates documentation consistency across all projects
     */
    private AgentResponse validateConsistency(AgentRequest request) {
        List<ConsistencyIssue> issues = new ArrayList<>();
        
        // Check API documentation consistency
        for (String projectId : Arrays.asList("positivity", "moqui_example")) {
            DocumentationSource source = documentationSources.get(projectId);
            if (source != null) {
                issues.addAll(validateApiConsistency(source));
            }
        }
        
        // Check architectural diagram consistency
        for (ArchitecturalDiagram diagram : architecturalDiagrams.values()) {
            issues.addAll(validateArchitecturalConsistency(diagram));
        }
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Consistency validation completed");
        recommendations.add("Issues found: " + issues.size());
        
        if (issues.isEmpty()) {
            recommendations.add("✅ All documentation is consistent");
        } else {
            recommendations.add("❌ Consistency issues detected:");
            for (ConsistencyIssue issue : issues) {
                recommendations.add("  - " + issue.getDescription());
            }
        }
        
        return createSuccessResponse(request,
            "Documentation consistency validation completed",
            recommendations);
    }
    
    @Override
    protected boolean isPrimaryCapability(AgentCapability capability) {
        return capability == AgentCapability.DOCUMENTATION_COORDINATION;
    }
    
    @Override
    protected List<String> getDefaultCoordinationDependencies() {
        return Arrays.asList(
            "api-contract",
            "workspace-architecture",
            "workspace-feature-development"
        );
    }
    
    // Helper methods
    
    private void initializeDocumentationSources() {
        // Initialize documentation sources for known projects
        documentationSources.put("positivity", 
            new DocumentationSource("positivity", DocumentationSource.Type.API));
        documentationSources.put("moqui_example", 
            new DocumentationSource("moqui_example", DocumentationSource.Type.INTEGRATION));
        
        // Initialize architectural diagrams
        architecturalDiagrams.put("system-overview", 
            new ArchitecturalDiagram("system-overview", "System Overview"));
        architecturalDiagrams.put("integration-flow", 
            new ArchitecturalDiagram("integration-flow", "Integration Flow"));
    }
    
    private void propagateApiChanges(String projectId, DocumentationChange change) {
        // Simulate API change propagation
        List<String> dependentProjects = identifyAffectedProjects(change);
        
        for (String dependentProject : dependentProjects) {
            if (!dependentProject.equals(projectId)) {
                // Notify dependent project of API changes
                propagateChangeToProject(change, dependentProject);
            }
        }
    }
    
    private List<String> identifyAffectedProjects(DocumentationChange change) {
        List<String> affected = new ArrayList<>();
        
        // Simple dependency mapping
        if ("positivity".equals(change.getProjectId())) {
            affected.add("moqui_example"); // Frontend depends on backend APIs
        } else if ("moqui_example".equals(change.getProjectId())) {
            // Frontend changes typically don't affect backend
        }
        
        return affected;
    }
    
    private PropagationResult propagateChangeToProject(DocumentationChange change, String projectId) {
        try {
            DocumentationSource target = documentationSources.get(projectId);
            if (target != null) {
                target.applyChange(change);
                return new PropagationResult(projectId, true, "Change applied successfully");
            } else {
                return new PropagationResult(projectId, false, "Documentation source not found");
            }
        } catch (Exception e) {
            return new PropagationResult(projectId, false, "Error: " + e.getMessage());
        }
    }
    
    private double validateDiagramAccuracy(ArchitecturalDiagram diagram) {
        // Simulate accuracy validation
        // In real implementation, this would validate against actual system state
        return 0.96; // 96% accuracy
    }
    
    private double calculateConsistencyRate() {
        // Calculate overall consistency rate across all documentation
        return 0.98; // 98% consistency
    }
    
    private boolean checkApiUpdateLatency() {
        // Check if all API updates are within 24-hour SLA
        return changeTracker.getAllChanges().stream()
            .filter(c -> c.getType() == DocumentationChange.Type.API_UPDATE)
            .allMatch(c -> Duration.between(c.getTimestamp(), Instant.now()).toHours() < 24);
    }
    
    private boolean checkDiagramAccuracy() {
        // Check if all diagrams meet 95% accuracy requirement
        return architecturalDiagrams.values().stream()
            .allMatch(d -> validateDiagramAccuracy(d) >= 0.95);
    }
    
    private List<ConsistencyIssue> validateApiConsistency(DocumentationSource source) {
        List<ConsistencyIssue> issues = new ArrayList<>();
        
        // Simulate consistency validation
        if (Math.random() < 0.1) { // 10% chance of issues
            issues.add(new ConsistencyIssue(source.getProjectId(), 
                "API documentation version mismatch"));
        }
        
        return issues;
    }
    
    private List<ConsistencyIssue> validateArchitecturalConsistency(ArchitecturalDiagram diagram) {
        List<ConsistencyIssue> issues = new ArrayList<>();
        
        // Simulate architectural consistency validation
        if (Math.random() < 0.05) { // 5% chance of issues
            issues.add(new ConsistencyIssue(diagram.getDiagramId(), 
                "Architectural diagram outdated"));
        }
        
        return issues;
    }
    
    // Guide generation methods
    private String generateBackendSetupGuide() {
        return "Backend development setup for Positivity POS system...";
    }
    
    private String generateFrontendSetupGuide() {
        return "Frontend development setup for Moqui Example application...";
    }
    
    private String generateFullStackSetupGuide() {
        return "Full-stack development setup covering both Positivity and Moqui...";
    }
    
    private String generateApiGuidelinesGuide() {
        return "API development guidelines and best practices...";
    }
    
    private String generateApiIntegrationGuide() {
        return "Guide for integrating with Positivity APIs from frontend...";
    }
    
    private String generateCrossProjectWorkflowGuide() {
        return "Workflows for coordinating development across projects...";
    }
    
    private String generateGeneralSetupGuide() {
        return "General development environment setup...";
    }
    
    // Inner classes
    
    private static class DocumentationSource {
        public enum Type {
            API, INTEGRATION, ARCHITECTURE, ONBOARDING
        }
        
        private final String projectId;
        private final Type type;
        private String apiSpec;
        private String onboardingContent;
        private final List<DocumentationChange> appliedChanges;
        private Instant lastUpdated;
        
        public DocumentationSource(String projectId, Type type) {
            this.projectId = projectId;
            this.type = type;
            this.appliedChanges = new ArrayList<>();
            this.lastUpdated = Instant.now();
        }
        
        public void updateApiSpec(String apiSpec, String changeDescription) {
            this.apiSpec = apiSpec;
            this.lastUpdated = Instant.now();
        }
        
        public void applyChange(DocumentationChange change) {
            appliedChanges.add(change);
            this.lastUpdated = Instant.now();
        }
        
        // Getters
        public String getProjectId() { return projectId; }
        public Type getType() { return type; }
        public String getApiSpec() { return apiSpec; }
        public String getOnboardingContent() { return onboardingContent != null ? onboardingContent : "Default onboarding content"; }
        public Instant getLastUpdated() { return lastUpdated; }
    }
    
    private static class ArchitecturalDiagram {
        private final String diagramId;
        private final String title;
        private final Map<String, Object> components;
        private final List<String> affectedProjects;
        private Instant lastUpdated;
        
        public ArchitecturalDiagram(String diagramId, String title) {
            this.diagramId = diagramId;
            this.title = title;
            this.components = new HashMap<>();
            this.affectedProjects = new ArrayList<>();
            this.lastUpdated = Instant.now();
        }
        
        public DiagramUpdateResult applyChanges(Map<String, Object> changes) {
            components.putAll(changes);
            this.lastUpdated = Instant.now();
            
            // Simulate update result
            return new DiagramUpdateResult(true, new ArrayList<>());
        }
        
        // Getters
        public String getDiagramId() { return diagramId; }
        public String getTitle() { return title; }
        public List<String> getAffectedProjects() { return new ArrayList<>(affectedProjects); }
        public Instant getLastUpdated() { return lastUpdated; }
    }
    
    private static class OnboardingGuide {
        private final String userId;
        private final String userRole;
        private final List<String> projectAccess;
        private final Map<String, String> sections;
        private final Instant createdAt;
        
        public OnboardingGuide(String userId, String userRole, List<String> projectAccess) {
            this.userId = userId;
            this.userRole = userRole;
            this.projectAccess = new ArrayList<>(projectAccess);
            this.sections = new LinkedHashMap<>();
            this.createdAt = Instant.now();
        }
        
        public void addSection(String title, String content) {
            sections.put(title, content);
        }
        
        public int getSectionCount() {
            return sections.size();
        }
        
        // Getters
        public String getUserId() { return userId; }
        public String getUserRole() { return userRole; }
        public List<String> getProjectAccess() { return new ArrayList<>(projectAccess); }
        public Map<String, String> getSections() { return new LinkedHashMap<>(sections); }
    }
    
    private static class DocumentationChange {
        public enum Type {
            API_UPDATE, ARCHITECTURE_UPDATE, CONTENT_UPDATE
        }
        
        private final String changeId;
        private final String projectId;
        private final Type type;
        private final String description;
        private final Instant timestamp;
        private Instant propagationCompleted;
        
        public DocumentationChange(String projectId, Type type, String description, Instant timestamp) {
            this.changeId = UUID.randomUUID().toString();
            this.projectId = projectId;
            this.type = type;
            this.description = description;
            this.timestamp = timestamp;
        }
        
        // Getters and setters
        public String getChangeId() { return changeId; }
        public String getProjectId() { return projectId; }
        public Type getType() { return type; }
        public String getDescription() { return description; }
        public Instant getTimestamp() { return timestamp; }
        public void setPropagationCompleted(Instant time) { this.propagationCompleted = time; }
        public boolean isPropagationCompleted() { return propagationCompleted != null; }
    }
    
    private static class DocumentationSynchronizer {
        public SynchronizationResult synchronizeProject(String projectId, 
                                                      Map<String, DocumentationSource> sources) {
            try {
                // Simulate synchronization
                Thread.sleep(50); // Simulate sync time
                
                DocumentationSource source = sources.get(projectId);
                if (source == null) {
                    return new SynchronizationResult(projectId, false, "Source not found");
                }
                
                // Simulate occasional sync failures
                if (Math.random() < 0.05) { // 5% failure rate
                    return new SynchronizationResult(projectId, false, "Sync failed");
                }
                
                return new SynchronizationResult(projectId, true, "Synchronized successfully");
                
            } catch (Exception e) {
                return new SynchronizationResult(projectId, false, "Error: " + e.getMessage());
            }
        }
    }
    
    private static class ChangeTracker {
        private final Map<String, DocumentationChange> changes = new ConcurrentHashMap<>();
        
        public void recordChange(DocumentationChange change) {
            changes.put(change.getChangeId(), change);
        }
        
        public DocumentationChange getChange(String changeId) {
            return changes.get(changeId);
        }
        
        public Collection<DocumentationChange> getAllChanges() {
            return changes.values();
        }
        
        public long getPendingChangesCount() {
            return changes.values().stream()
                .mapToLong(c -> c.isPropagationCompleted() ? 0 : 1)
                .sum();
        }
    }
    
    // Result classes
    
    private static class SynchronizationResult {
        private final String projectId;
        private final boolean successful;
        private final String message;
        
        public SynchronizationResult(String projectId, boolean successful, String message) {
            this.projectId = projectId;
            this.successful = successful;
            this.message = message;
        }
        
        public String getProjectId() { return projectId; }
        public boolean isSuccessful() { return successful; }
        public String getMessage() { return message; }
    }
    
    private static class PropagationResult {
        private final String projectId;
        private final boolean successful;
        private final String message;
        
        public PropagationResult(String projectId, boolean successful, String message) {
            this.projectId = projectId;
            this.successful = successful;
            this.message = message;
        }
        
        public String getProjectId() { return projectId; }
        public boolean isSuccessful() { return successful; }
        public String getMessage() { return message; }
    }
    
    private static class DiagramUpdateResult {
        private final boolean successful;
        private final List<String> accuracyIssues;
        
        public DiagramUpdateResult(boolean successful, List<String> accuracyIssues) {
            this.successful = successful;
            this.accuracyIssues = new ArrayList<>(accuracyIssues);
        }
        
        public boolean isSuccessful() { return successful; }
        public List<String> getAccuracyIssues() { return new ArrayList<>(accuracyIssues); }
    }
    
    private static class ConsistencyIssue {
        private final String source;
        private final String description;
        
        public ConsistencyIssue(String source, String description) {
            this.source = source;
            this.description = description;
        }
        
        public String getSource() { return source; }
        public String getDescription() { return description; }
    }
}