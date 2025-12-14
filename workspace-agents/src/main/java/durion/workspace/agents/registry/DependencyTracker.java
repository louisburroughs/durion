package durion.workspace.agents.registry;

import java.util.*;
import java.time.Instant;

/**
 * Tracks dependencies between projects and components
 */
public class DependencyTracker {
    
    private final Map<String, Set<String>> projectDependencies;
    private final Map<String, DependencyInfo> dependencyMetadata;
    
    public DependencyTracker() {
        this.projectDependencies = new HashMap<>();
        this.dependencyMetadata = new HashMap<>();
        initializeKnownDependencies();
    }
    
    public void addDependency(String project, String dependency, DependencyType type) {
        projectDependencies.computeIfAbsent(project, k -> new HashSet<>()).add(dependency);
        
        String key = project + "->" + dependency;
        dependencyMetadata.put(key, new DependencyInfo(project, dependency, type));
    }
    
    public void removeDependency(String project, String dependency) {
        Set<String> deps = projectDependencies.get(project);
        if (deps != null) {
            deps.remove(dependency);
        }
        
        String key = project + "->" + dependency;
        dependencyMetadata.remove(key);
    }
    
    public List<String> getProjectDependencies(String project) {
        Set<String> deps = projectDependencies.get(project);
        return deps != null ? new ArrayList<>(deps) : new ArrayList<>();
    }
    
    public List<String> getDependents(String project) {
        List<String> dependents = new ArrayList<>();
        
        for (Map.Entry<String, Set<String>> entry : projectDependencies.entrySet()) {
            if (entry.getValue().contains(project)) {
                dependents.add(entry.getKey());
            }
        }
        
        return dependents;
    }
    
    public boolean hasDependency(String project, String dependency) {
        Set<String> deps = projectDependencies.get(project);
        return deps != null && deps.contains(dependency);
    }
    
    public List<String> getTransitiveDependencies(String project) {
        Set<String> visited = new HashSet<>();
        List<String> transitive = new ArrayList<>();
        
        collectTransitiveDependencies(project, visited, transitive);
        
        return transitive;
    }
    
    public boolean hasCircularDependency(String project) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        return hasCircularDependencyRecursive(project, visited, visiting);
    }
    
    public Map<String, List<String>> getDependencyGraph() {
        Map<String, List<String>> graph = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : projectDependencies.entrySet()) {
            graph.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        
        return graph;
    }
    
    private void initializeKnownDependencies() {
        // Initialize known dependencies between positivity and moqui_example
        addDependency("moqui_example", "positivity", DependencyType.API_CONSUMER);
        addDependency("positivity", "shared-models", DependencyType.LIBRARY);
        addDependency("moqui_example", "shared-models", DependencyType.LIBRARY);
    }
    
    private void collectTransitiveDependencies(String project, Set<String> visited, List<String> result) {
        if (visited.contains(project)) {
            return;
        }
        
        visited.add(project);
        
        List<String> directDeps = getProjectDependencies(project);
        for (String dep : directDeps) {
            if (!result.contains(dep)) {
                result.add(dep);
            }
            collectTransitiveDependencies(dep, visited, result);
        }
    }
    
    private boolean hasCircularDependencyRecursive(String project, Set<String> visited, Set<String> visiting) {
        if (visiting.contains(project)) {
            return true; // Circular dependency found
        }
        
        if (visited.contains(project)) {
            return false;
        }
        
        visiting.add(project);
        
        List<String> deps = getProjectDependencies(project);
        for (String dep : deps) {
            if (hasCircularDependencyRecursive(dep, visited, visiting)) {
                return true;
            }
        }
        
        visiting.remove(project);
        visited.add(project);
        
        return false;
    }
    
    public enum DependencyType {
        API_CONSUMER,
        API_PROVIDER,
        LIBRARY,
        SERVICE,
        DATABASE,
        CONFIGURATION,
        BUILD_TIME,
        RUNTIME
    }
    
    public static class DependencyInfo {
        private final String source;
        private final String target;
        private final DependencyType type;
        private final Instant createdAt;
        private final Map<String, Object> metadata;
        
        public DependencyInfo(String source, String target, DependencyType type) {
            this.source = source;
            this.target = target;
            this.type = type;
            this.createdAt = Instant.now();
            this.metadata = new HashMap<>();
        }
        
        // Getters
        public String getSource() { return source; }
        public String getTarget() { return target; }
        public DependencyType getType() { return type; }
        public Instant getCreatedAt() { return createdAt; }
        
        public void setMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        public Object getMetadata(String key) {
            return metadata.get(key);
        }
        
        public Map<String, Object> getAllMetadata() {
            return new HashMap<>(metadata);
        }
    }
}