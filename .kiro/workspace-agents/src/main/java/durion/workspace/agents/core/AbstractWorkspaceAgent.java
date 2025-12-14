package durion.workspace.agents.core;

import durion.workspace.agents.monitoring.PerformanceMonitor;
import durion.workspace.agents.registry.AgentMetrics;

import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for workspace agents providing common functionality
 */
public abstract class AbstractWorkspaceAgent implements WorkspaceAgent {
    
    protected final String agentId;
    protected final AgentType agentType;
    protected final Set<AgentCapability> capabilities;
    protected final AgentConfiguration configuration;
    protected PerformanceMonitor performanceMonitor;
    protected AgentMetrics metrics;
    
    protected AbstractWorkspaceAgent(String agentId, AgentType agentType, 
                                   Set<AgentCapability> capabilities) {
        this.agentId = agentId;
        this.agentType = agentType;
        this.capabilities = new HashSet<>(capabilities);
        this.configuration = new AgentConfiguration(agentId);
        this.metrics = new AgentMetrics(agentId);
    }
    
    @Override
    public String getAgentId() {
        return agentId;
    }
    
    @Override
    public AgentType getAgentType() {
        return agentType;
    }
    
    @Override
    public Set<AgentCapability> getCapabilities() {
        return new HashSet<>(capabilities);
    }
    
    @Override
    public boolean hasCapability(AgentCapability capability) {
        return capabilities.contains(capability);
    }
    
    @Override
    public int getCapabilityScore(AgentCapability capability) {
        if (!hasCapability(capability)) {
            return 0;
        }
        
        // Base score for having the capability
        int baseScore = 50;
        
        // Bonus based on agent performance
        double successRate = metrics.getSuccessRate();
        int performanceBonus = (int) (30 * successRate);
        
        // Bonus based on specialization (primary capabilities get higher scores)
        int specializationBonus = isPrimaryCapability(capability) ? 20 : 10;
        
        return Math.min(100, baseScore + performanceBonus + specializationBonus);
    }
    
    @Override
    public AgentResponse processRequest(AgentRequest request) throws AgentException {
        Instant startTime = Instant.now();
        
        try {
            // Validate request
            if (!canHandleRequest(request)) {
                throw new AgentException(agentId, AgentException.AgentErrorType.CAPABILITY_MISMATCH,
                        "Agent cannot handle request: " + request.getRequestType());
            }
            
            // Process with timeout
            CompletableFuture<AgentResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return doProcessRequest(request);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
            AgentResponse response = future.get(
                configuration.getResponseTimeout().toMillis(), 
                TimeUnit.MILLISECONDS
            );
            
            // Record successful request
            Duration responseTime = Duration.between(startTime, Instant.now());
            recordRequestMetrics(responseTime, true);
            
            return response;
            
        } catch (AgentException e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            recordRequestMetrics(responseTime, false);
            throw e;
        } catch (Exception e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            recordRequestMetrics(responseTime, false);
            throw new AgentException(agentId, AgentException.AgentErrorType.TIMEOUT,
                    "Request processing failed", e);
        }
    }
    
    @Override
    public AgentStatus getStatus() {
        if (!metrics.isAvailable()) {
            return AgentStatus.unavailable(agentId, "Agent has been inactive");
        } else if (!metrics.isHealthy()) {
            return AgentStatus.degraded(agentId, 
                    String.format("Success rate: %.1f%%", metrics.getSuccessRate() * 100));
        } else {
            return AgentStatus.healthy(agentId);
        }
    }
    
    @Override
    public void setPerformanceMonitor(PerformanceMonitor monitor) {
        this.performanceMonitor = monitor;
    }
    
    @Override
    public List<String> getCoordinationDependencies() {
        return getDefaultCoordinationDependencies();
    }
    
    @Override
    public boolean canHandleRequest(AgentRequest request) {
        // Check if agent has required capability
        if (!hasCapability(request.getRequiredCapability())) {
            return false;
        }
        
        // Check if agent supports the project types involved
        return supportsProjectTypes(request);
    }
    
    @Override
    public AgentConfiguration getConfiguration() {
        return configuration;
    }
    
    @Override
    public void updateConfiguration(AgentConfiguration config) {
        if (!config.getAgentId().equals(this.agentId)) {
            throw new IllegalArgumentException("Configuration agent ID mismatch");
        }
        
        this.configuration.setResponseTimeout(config.getResponseTimeout());
        this.configuration.setMaxConcurrentRequests(config.getMaxConcurrentRequests());
        this.configuration.setPerformanceMonitoringEnabled(config.isPerformanceMonitoringEnabled());
        
        // Copy all properties
        for (Map.Entry<String, Object> entry : config.getAllProperties().entrySet()) {
            this.configuration.setProperty(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Abstract method for actual request processing - to be implemented by subclasses
     */
    protected abstract AgentResponse doProcessRequest(AgentRequest request) throws Exception;
    
    /**
     * Checks if the capability is a primary capability for this agent
     */
    protected abstract boolean isPrimaryCapability(AgentCapability capability);
    
    /**
     * Gets default coordination dependencies for this agent type
     */
    protected abstract List<String> getDefaultCoordinationDependencies();
    
    /**
     * Checks if agent supports the project types in the request
     */
    protected boolean supportsProjectTypes(AgentRequest request) {
        // By default, workspace agents support all project types
        // Subclasses can override for specific restrictions
        return true;
    }
    
    /**
     * Records request metrics
     */
    protected void recordRequestMetrics(Duration responseTime, boolean success) {
        metrics.recordRequest(responseTime, success);
        
        if (performanceMonitor != null && configuration.isPerformanceMonitoringEnabled()) {
            performanceMonitor.recordRequest(UUID.randomUUID().toString(), responseTime, success);
        }
    }
    
    /**
     * Helper method to create successful response
     */
    protected AgentResponse createSuccessResponse(AgentRequest request, String guidance, 
                                                List<String> recommendations) {
        return new AgentResponse(request.getRequestId(), guidance, recommendations, Instant.now());
    }
    
    /**
     * Helper method to create error response
     */
    protected AgentResponse createErrorResponse(AgentRequest request, String errorMessage) {
        return new AgentResponse(request.getRequestId(), errorMessage, Instant.now());
    }
}