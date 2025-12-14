package durion.workspace.agents.registry;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Metrics tracking for individual agents
 */
public class AgentMetrics {
    
    private final String agentId;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong totalResponseTimeNanos = new AtomicLong(0);
    private final AtomicReference<Instant> lastActivity = new AtomicReference<>(Instant.now());
    private final AtomicReference<Duration> lastResponseTime = new AtomicReference<>(Duration.ZERO);
    
    public AgentMetrics(String agentId) {
        this.agentId = agentId;
    }
    
    public void recordRequest(Duration responseTime, boolean success) {
        requestCount.incrementAndGet();
        if (success) {
            successCount.incrementAndGet();
        }
        totalResponseTimeNanos.addAndGet(responseTime.toNanos());
        lastActivity.set(Instant.now());
        lastResponseTime.set(responseTime);
    }
    
    public String getAgentId() { return agentId; }
    
    public long getRequestCount() { return requestCount.get(); }
    
    public long getSuccessCount() { return successCount.get(); }
    
    public double getSuccessRate() {
        long total = requestCount.get();
        return total > 0 ? (double) successCount.get() / total : 1.0;
    }
    
    public double getAverageResponseTime() {
        long total = requestCount.get();
        return total > 0 ? (double) totalResponseTimeNanos.get() / total / 1_000_000.0 : 0.0; // ms
    }
    
    public Instant getLastActivity() { return lastActivity.get(); }
    
    public Duration getLastResponseTime() { return lastResponseTime.get(); }
    
    public boolean isAvailable() {
        // Consider agent available if it had activity in the last 5 minutes
        return Duration.between(lastActivity.get(), Instant.now()).toMinutes() < 5;
    }
    
    public boolean isHealthy() {
        return isAvailable() && getSuccessRate() > 0.95; // 95% success rate threshold
    }
}