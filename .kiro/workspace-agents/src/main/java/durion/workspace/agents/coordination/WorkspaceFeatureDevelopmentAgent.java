package durion.workspace.agents.coordination;

import durion.workspace.agents.core.*;
import durion.workspace.agents.registry.ProjectRegistry;
import durion.workspace.agents.registry.FeatureFlag;
import durion.workspace.agents.registry.DependencyTracker;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates feature development workflows between backend and frontend teams.
 * Provides cross-layer dependency tracking, progress visibility, and feature flag coordination.
 * 
 * Requirements: 5.1, 5.3
 */
public class WorkspaceFeatureDevelopmentAgent extends AbstractWorkspaceAgent {
    
    private final ProjectRegistry projectRegistry;
    private final DependencyTracker dependencyTracker;
    private final Map<String, FeatureWorkflow> activeWorkflows;
    private final Map<String, FeatureFlag> featureFlags;
    
    public WorkspaceFeatureDevelopmentAgent() {
        super("workspace-feature-development", 
              AgentType.GOVERNANCE_COMPLIANCE,
              Set.of(AgentCapability.WORKFLOW_COORDINATION, 
                     AgentCapability.CHANGE_COORDINATION,
                     AgentCapability.FULL_STACK_INTEGRATION));
        
        this.projectRegistry = new ProjectRegistry();
        this.dependencyTracker = new DependencyTracker();
        this.activeWorkflows = new HashMap<>();
        this.featureFlags = new HashMap<>();
        
        initializeProjectRegistry();
    }
    
    @Override
    protected AgentResponse doProcessRequest(AgentRequest request) throws Exception {
        switch (request.getRequestType()) {
            case "CREATE_FEATURE_WORKFLOW":
                return createFeatureWorkflow(request);
            case "TRACK_FEATURE_PROGRESS":
                return trackFeatureProgress(request);
            case "COORDINATE_DEPENDENCIES":
                return coordinateDependencies(request);
            case "MANAGE_FEATURE_FLAGS":
                return manageFeatureFlags(request);
            case "GET_CROSS_LAYER_STATUS":
                return getCrossLayerStatus(request);
            default:
                throw new AgentException(agentId, AgentException.AgentErrorType.CAPABILITY_MISMATCH,
                        "Unsupported request type: " + request.getRequestType());
        }
    }
    
    /**
     * Creates coordinated feature development workflow between backend and frontend teams
     */
    private AgentResponse createFeatureWorkflow(AgentRequest request) {
        String featureName = request.getParameter("featureName", String.class);
        List<String> affectedProjects = request.getParameter("affectedProjects", List.class);
        Map<String, Object> requirements = request.getParameter("requirements", Map.class);
        
        // Create workflow with cross-project coordination
        FeatureWorkflow workflow = new FeatureWorkflow(featureName, affectedProjects);
        
        // Analyze cross-project dependencies
        for (String project : affectedProjects) {
            ProjectRegistry.ProjectInfo projectInfo = projectRegistry.getProject(project);
            if (projectInfo != null) {
                workflow.addProjectTasks(project, generateProjectTasks(project, requirements));
                
                // Track dependencies between projects
                List<String> dependencies = dependencyTracker.getProjectDependencies(project);
                workflow.addDependencies(project, dependencies);
            }
        }
        
        // Set up progress tracking
        workflow.initializeProgressTracking();
        
        // Create feature flags for coordinated rollout
        FeatureFlag featureFlag = new FeatureFlag(featureName, affectedProjects);
        featureFlags.put(featureName, featureFlag);
        workflow.setFeatureFlag(featureFlag);
        
        activeWorkflows.put(featureName, workflow);
        
        List<String> recommendations = Arrays.asList(
            "Feature workflow created with cross-project coordination",
            "Dependencies tracked between " + String.join(", ", affectedProjects),
            "Feature flag '" + featureName + "' created for coordinated rollout",
            "Progress tracking initialized for all project teams"
        );
        
        return createSuccessResponse(request, 
            "Coordinated feature development workflow created for: " + featureName, 
            recommendations);
    }
    
    /**
     * Tracks feature progress across all involved projects
     */
    private AgentResponse trackFeatureProgress(AgentRequest request) throws AgentException {
        String featureName = request.getParameter("featureName", String.class);
        
        FeatureWorkflow workflow = activeWorkflows.get(featureName);
        if (workflow == null) {
            throw new AgentException(agentId, AgentException.AgentErrorType.CONFIGURATION_ERROR,
                    "Feature workflow not found: " + featureName);
        }
        
        // Update progress from all projects
        Map<String, Double> projectProgress = new HashMap<>();
        List<String> blockers = new ArrayList<>();
        
        for (String project : workflow.getAffectedProjects()) {
            double progress = calculateProjectProgress(project, featureName);
            projectProgress.put(project, progress);
            
            // Check for blockers
            List<String> projectBlockers = identifyProjectBlockers(project, featureName);
            if (!projectBlockers.isEmpty()) {
                blockers.addAll(projectBlockers.stream()
                    .map(blocker -> project + ": " + blocker)
                    .collect(Collectors.toList()));
            }
        }
        
        workflow.updateProgress(projectProgress);
        
        // Calculate overall progress
        double overallProgress = projectProgress.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add(String.format("Overall feature progress: %.1f%%", overallProgress * 100));
        
        for (Map.Entry<String, Double> entry : projectProgress.entrySet()) {
            recommendations.add(String.format("%s: %.1f%% complete", 
                entry.getKey(), entry.getValue() * 100));
        }
        
        if (!blockers.isEmpty()) {
            recommendations.add("Blockers identified:");
            recommendations.addAll(blockers);
        }
        
        return createSuccessResponse(request,
            "Feature progress tracked across all projects", 
            recommendations);
    }
    
    /**
     * Coordinates cross-layer dependencies and ensures proper sequencing
     */
    private AgentResponse coordinateDependencies(AgentRequest request) throws AgentException {
        String featureName = request.getParameter("featureName", String.class);
        
        FeatureWorkflow workflow = activeWorkflows.get(featureName);
        if (workflow == null) {
            throw new AgentException(agentId, AgentException.AgentErrorType.CONFIGURATION_ERROR,
                    "Feature workflow not found: " + featureName);
        }
        
        // Analyze dependency graph
        Map<String, List<String>> dependencyGraph = workflow.getDependencyGraph();
        List<String> executionOrder = calculateExecutionOrder(dependencyGraph);
        
        // Check for dependency conflicts
        List<String> conflicts = identifyDependencyConflicts(workflow);
        
        // Provide coordination guidance
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Recommended execution order: " + String.join(" → ", executionOrder));
        
        if (!conflicts.isEmpty()) {
            recommendations.add("Dependency conflicts detected:");
            recommendations.addAll(conflicts);
            recommendations.add("Consider breaking down features or adjusting implementation order");
        }
        
        // Update workflow with coordination plan
        workflow.setExecutionOrder(executionOrder);
        workflow.setDependencyConflicts(conflicts);
        
        return createSuccessResponse(request,
            "Cross-layer dependencies coordinated for feature: " + featureName,
            recommendations);
    }
    
    /**
     * Manages feature flags for coordinated rollout across projects
     */
    private AgentResponse manageFeatureFlags(AgentRequest request) throws AgentException {
        String action = request.getParameter("action", String.class);
        String featureName = request.getParameter("featureName", String.class);
        
        FeatureFlag featureFlag = featureFlags.get(featureName);
        if (featureFlag == null) {
            throw new AgentException(agentId, AgentException.AgentErrorType.CONFIGURATION_ERROR,
                    "Feature flag not found: " + featureName);
        }
        
        List<String> recommendations = new ArrayList<>();
        
        switch (action.toLowerCase()) {
            case "enable":
                Double rolloutPercentage = request.getParameter("rolloutPercentage", Double.class);
                if (rolloutPercentage == null) rolloutPercentage = 100.0;
                
                featureFlag.enable(rolloutPercentage);
                recommendations.add("Feature flag enabled for " + rolloutPercentage + "% of users");
                break;
                
            case "disable":
                featureFlag.disable();
                recommendations.add("Feature flag disabled across all projects");
                break;
                
            case "rollout":
                Double targetPercentage = request.getParameter("targetPercentage", Double.class);
                if (targetPercentage == null) targetPercentage = 100.0;
                
                featureFlag.gradualRollout(targetPercentage);
                recommendations.add("Gradual rollout initiated to " + targetPercentage + "%");
                break;
                
            case "status":
                recommendations.add("Feature flag status: " + featureFlag.getStatus());
                recommendations.add("Current rollout: " + featureFlag.getRolloutPercentage() + "%");
                recommendations.add("Affected projects: " + String.join(", ", featureFlag.getAffectedProjects()));
                break;
                
            default:
                throw new AgentException(agentId, AgentException.AgentErrorType.CONFIGURATION_ERROR,
                        "Invalid action: " + action);
        }
        
        return createSuccessResponse(request,
            "Feature flag management completed for: " + featureName,
            recommendations);
    }
    
    /**
     * Gets cross-layer status for all active workflows
     */
    private AgentResponse getCrossLayerStatus(AgentRequest request) {
        List<String> recommendations = new ArrayList<>();
        
        if (activeWorkflows.isEmpty()) {
            recommendations.add("No active feature workflows");
        } else {
            recommendations.add("Active feature workflows: " + activeWorkflows.size());
            
            for (Map.Entry<String, FeatureWorkflow> entry : activeWorkflows.entrySet()) {
                String featureName = entry.getKey();
                FeatureWorkflow workflow = entry.getValue();
                
                double progress = workflow.getOverallProgress();
                recommendations.add(String.format("%s: %.1f%% complete (%d projects)", 
                    featureName, progress * 100, workflow.getAffectedProjects().size()));
                
                if (workflow.hasBlockers()) {
                    recommendations.add("  ⚠️ Has blockers requiring attention");
                }
            }
        }
        
        // Feature flag status
        recommendations.add("Active feature flags: " + featureFlags.size());
        for (Map.Entry<String, FeatureFlag> entry : featureFlags.entrySet()) {
            FeatureFlag flag = entry.getValue();
            recommendations.add(String.format("%s: %s (%.1f%%)", 
                entry.getKey(), flag.getStatus(), flag.getRolloutPercentage()));
        }
        
        return createSuccessResponse(request,
            "Cross-layer workflow status retrieved",
            recommendations);
    }
    
    @Override
    protected boolean isPrimaryCapability(AgentCapability capability) {
        return capability == AgentCapability.WORKFLOW_COORDINATION ||
               capability == AgentCapability.CHANGE_COORDINATION;
    }
    
    @Override
    protected List<String> getDefaultCoordinationDependencies() {
        return Arrays.asList(
            "workspace-architecture",
            "api-contract",
            "multi-project-devops",
            "cross-project-testing"
        );
    }
    
    // Helper methods
    
    private void initializeProjectRegistry() {
        // Register known projects
        projectRegistry.registerProject("positivity", "spring-boot", "backend");
        projectRegistry.registerProject("moqui_example", "moqui-framework", "frontend");
    }
    
    private List<ProjectTask> generateProjectTasks(String project, Map<String, Object> requirements) {
        List<ProjectTask> tasks = new ArrayList<>();
        
        if ("positivity".equals(project)) {
            // Backend tasks
            tasks.add(new ProjectTask("api-implementation", "Implement backend API endpoints"));
            tasks.add(new ProjectTask("data-model", "Update data models and entities"));
            tasks.add(new ProjectTask("business-logic", "Implement business logic services"));
            tasks.add(new ProjectTask("integration-tests", "Create integration tests"));
        } else if ("moqui_example".equals(project)) {
            // Frontend tasks
            tasks.add(new ProjectTask("ui-components", "Create UI components"));
            tasks.add(new ProjectTask("api-integration", "Integrate with backend APIs"));
            tasks.add(new ProjectTask("state-management", "Update state management"));
            tasks.add(new ProjectTask("user-testing", "Conduct user acceptance testing"));
        }
        
        return tasks;
    }
    
    private double calculateProjectProgress(String project, String featureName) {
        // Simulate progress calculation
        // In real implementation, this would query project-specific systems
        return Math.random() * 0.8 + 0.1; // 10-90% progress
    }
    
    private List<String> identifyProjectBlockers(String project, String featureName) {
        List<String> blockers = new ArrayList<>();
        
        // Simulate blocker detection
        if (Math.random() < 0.3) { // 30% chance of blockers
            blockers.add("Waiting for API contract approval");
        }
        if (Math.random() < 0.2) { // 20% chance
            blockers.add("Database migration pending");
        }
        
        return blockers;
    }
    
    private List<String> calculateExecutionOrder(Map<String, List<String>> dependencyGraph) {
        // Topological sort for dependency resolution
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (String node : dependencyGraph.keySet()) {
            if (!visited.contains(node)) {
                topologicalSort(node, dependencyGraph, visited, visiting, order);
            }
        }
        
        Collections.reverse(order);
        return order;
    }
    
    private void topologicalSort(String node, Map<String, List<String>> graph, 
                                Set<String> visited, Set<String> visiting, List<String> order) {
        if (visiting.contains(node)) {
            // Circular dependency detected
            return;
        }
        
        if (visited.contains(node)) {
            return;
        }
        
        visiting.add(node);
        
        List<String> dependencies = graph.get(node);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                topologicalSort(dependency, graph, visited, visiting, order);
            }
        }
        
        visiting.remove(node);
        visited.add(node);
        order.add(node);
    }
    
    private List<String> identifyDependencyConflicts(FeatureWorkflow workflow) {
        List<String> conflicts = new ArrayList<>();
        
        // Check for circular dependencies
        Map<String, List<String>> graph = workflow.getDependencyGraph();
        if (hasCircularDependency(graph)) {
            conflicts.add("Circular dependency detected in project dependencies");
        }
        
        // Check for version conflicts
        // This would integrate with actual dependency management systems
        
        return conflicts;
    }
    
    private boolean hasCircularDependency(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycle(node, graph, visited, visiting)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean hasCycle(String node, Map<String, List<String>> graph, 
                           Set<String> visited, Set<String> visiting) {
        if (visiting.contains(node)) {
            return true;
        }
        
        if (visited.contains(node)) {
            return false;
        }
        
        visiting.add(node);
        
        List<String> dependencies = graph.get(node);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                if (hasCycle(dependency, graph, visited, visiting)) {
                    return true;
                }
            }
        }
        
        visiting.remove(node);
        visited.add(node);
        
        return false;
    }
    
    // Inner classes for workflow management
    
    private static class FeatureWorkflow {
        private final String featureName;
        private final List<String> affectedProjects;
        private final Map<String, List<ProjectTask>> projectTasks;
        private final Map<String, List<String>> dependencies;
        private final Map<String, Double> progress;
        private List<String> executionOrder;
        private List<String> dependencyConflicts;
        private FeatureFlag featureFlag;
        private final Instant createdAt;
        
        public FeatureWorkflow(String featureName, List<String> affectedProjects) {
            this.featureName = featureName;
            this.affectedProjects = new ArrayList<>(affectedProjects);
            this.projectTasks = new HashMap<>();
            this.dependencies = new HashMap<>();
            this.progress = new HashMap<>();
            this.executionOrder = new ArrayList<>();
            this.dependencyConflicts = new ArrayList<>();
            this.createdAt = Instant.now();
        }
        
        public void addProjectTasks(String project, List<ProjectTask> tasks) {
            projectTasks.put(project, new ArrayList<>(tasks));
        }
        
        public void addDependencies(String project, List<String> deps) {
            dependencies.put(project, new ArrayList<>(deps));
        }
        
        public void initializeProgressTracking() {
            for (String project : affectedProjects) {
                progress.put(project, 0.0);
            }
        }
        
        public void updateProgress(Map<String, Double> newProgress) {
            progress.putAll(newProgress);
        }
        
        public double getOverallProgress() {
            return progress.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        }
        
        public boolean hasBlockers() {
            return !dependencyConflicts.isEmpty();
        }
        
        // Getters and setters
        public String getFeatureName() { return featureName; }
        public List<String> getAffectedProjects() { return new ArrayList<>(affectedProjects); }
        public Map<String, List<String>> getDependencyGraph() { return new HashMap<>(dependencies); }
        public void setExecutionOrder(List<String> order) { this.executionOrder = new ArrayList<>(order); }
        public void setDependencyConflicts(List<String> conflicts) { this.dependencyConflicts = new ArrayList<>(conflicts); }
        public void setFeatureFlag(FeatureFlag flag) { this.featureFlag = flag; }
    }
    
    private static class ProjectTask {
        private final String taskId;
        private final String description;
        private double progress;
        private final Instant createdAt;
        
        public ProjectTask(String taskId, String description) {
            this.taskId = taskId;
            this.description = description;
            this.progress = 0.0;
            this.createdAt = Instant.now();
        }
        
        // Getters and setters
        public String getTaskId() { return taskId; }
        public String getDescription() { return description; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = Math.max(0.0, Math.min(1.0, progress)); }
    }
}