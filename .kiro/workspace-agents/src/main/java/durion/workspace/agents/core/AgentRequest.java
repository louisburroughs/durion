package durion.workspace.agents.core;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Request object for agent communication
 */
public class AgentRequest {
    
    private final String requestId;
    private final String requestType;
    private final String description;
    private final Map<String, Object> parameters;
    private final AgentCapability requiredCapability;
    private final Instant timestamp;
    private final String sourceProject; // positivity, moqui_example, or workspace
    private final String targetProject;
    private final int priority; // 1-10, higher is more urgent
    
    public AgentRequest(String requestId, String requestType, String description,
                       AgentCapability requiredCapability, String sourceProject) {
        this.requestId = requestId;
        this.requestType = requestType;
        this.description = description;
        this.requiredCapability = requiredCapability;
        this.sourceProject = sourceProject;
        this.targetProject = null;
        this.parameters = new HashMap<>();
        this.timestamp = Instant.now();
        this.priority = 5; // default priority
    }
    
    public AgentRequest(String requestId, String requestType, String description,
                       AgentCapability requiredCapability, String sourceProject,
                       String targetProject, int priority) {
        this.requestId = requestId;
        this.requestType = requestType;
        this.description = description;
        this.requiredCapability = requiredCapability;
        this.sourceProject = sourceProject;
        this.targetProject = targetProject;
        this.parameters = new HashMap<>();
        this.timestamp = Instant.now();
        this.priority = Math.max(1, Math.min(10, priority));
    }
    
    // Getters
    public String getRequestId() { return requestId; }
    public String getRequestType() { return requestType; }
    public String getDescription() { return description; }
    public AgentCapability getRequiredCapability() { return requiredCapability; }
    public String getSourceProject() { return sourceProject; }
    public String getTargetProject() { return targetProject; }
    public int getPriority() { return priority; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
    
    // Parameter management
    public void addParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
    
    /**
     * Checks if this is a cross-project request
     */
    public boolean isCrossProject() {
        return targetProject != null && !sourceProject.equals(targetProject);
    }
    
    /**
     * Checks if this request involves the positivity backend
     */
    public boolean involvesPositivity() {
        return "positivity".equals(sourceProject) || "positivity".equals(targetProject);
    }
    
    /**
     * Checks if this request involves the moqui_example frontend
     */
    public boolean involvesMoqui() {
        return "moqui_example".equals(sourceProject) || "moqui_example".equals(targetProject);
    }
    
    @Override
    public String toString() {
        return String.format("AgentRequest{id='%s', type='%s', capability=%s, source='%s', target='%s', priority=%d}",
                requestId, requestType, requiredCapability, sourceProject, targetProject, priority);
    }
}