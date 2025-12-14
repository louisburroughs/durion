package durion.workspace.agents.registry;

import java.util.*;
import java.time.Instant;

/**
 * Registry for managing project information and metadata
 */
public class ProjectRegistry {
    
    private final Map<String, ProjectInfo> projects;
    
    public ProjectRegistry() {
        this.projects = new HashMap<>();
    }
    
    public void registerProject(String projectId, String technology, String type) {
        ProjectInfo info = new ProjectInfo(projectId, technology, type);
        projects.put(projectId, info);
    }
    
    public ProjectInfo getProject(String projectId) {
        return projects.get(projectId);
    }
    
    public List<ProjectInfo> getAllProjects() {
        return new ArrayList<>(projects.values());
    }
    
    public List<ProjectInfo> getProjectsByType(String type) {
        return projects.values().stream()
            .filter(p -> type.equals(p.getType()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public List<ProjectInfo> getProjectsByTechnology(String technology) {
        return projects.values().stream()
            .filter(p -> technology.equals(p.getTechnology()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public static class ProjectInfo {
        private final String projectId;
        private final String technology;
        private final String type;
        private final Instant registeredAt;
        private final Map<String, Object> metadata;
        
        public ProjectInfo(String projectId, String technology, String type) {
            this.projectId = projectId;
            this.technology = technology;
            this.type = type;
            this.registeredAt = Instant.now();
            this.metadata = new HashMap<>();
        }
        
        // Getters
        public String getProjectId() { return projectId; }
        public String getTechnology() { return technology; }
        public String getType() { return type; }
        public Instant getRegisteredAt() { return registeredAt; }
        
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