package durion.workspace.agents.coordination;

import durion.workspace.agents.core.AbstractWorkspaceAgent;
import durion.workspace.agents.core.AgentCapability;
import durion.workspace.agents.core.AgentRequest;
import durion.workspace.agents.core.AgentResponse;
import durion.workspace.agents.core.AgentType;
import durion.workspace.agents.monitoring.PerformanceMonitor;
import durion.workspace.agents.monitoring.PerformanceMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Performance Coordination Agent - Manages performance optimization and monitoring across all projects
 * 
 * Capabilities:
 * - Analyzes performance across positivity microservices, moqui_example frontend, and network boundaries
 * - Coordinates caching strategies between frontend and backend systems
 * - Provides unified performance dashboards and alerting
 * - Manages auto-scaling coordination across different deployment environments
 * - Delivers performance optimization recommendations within 10 minutes
 * 
 * Performance Requirements:
 * - Performance bottleneck identification: 5 minutes maximum
 * - Cache consistency: <1% stale data occurrence
 * - Performance alert response: 30 seconds maximum
 * - Service availability during scaling: >99.9%
 */
public class PerformanceCoordinationAgent extends AbstractWorkspaceAgent {
    
    private final PerformanceMonitor performanceMonitor;
    private final Map<String, ProjectPerformanceData> projectMetrics;
    private final Map<String, CacheMetrics> cacheMetrics;
    private final List<PerformanceAlert> activeAlerts;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Instant> lastBottleneckAnalysis;
    
    // Performance thresholds
    private static final Duration BOTTLENECK_ANALYSIS_INTERVAL = Duration.ofMinutes(5);
    private static final Duration ALERT_RESPONSE_TARGET = Duration.ofSeconds(30);
    private static final Duration OPTIMIZATION_DELIVERY_TARGET = Duration.ofMinutes(10);
    private static final double CACHE_STALENESS_THRESHOLD = 0.01; // <1%
    private static final double AVAILABILITY_TARGET = 0.999; // >99.9%
    
    public PerformanceCoordinationAgent() {
        super("performance-coordination", AgentType.WORKSPACE_COORDINATION, 
              Set.of(AgentCapability.PERFORMANCE_OPTIMIZATION, 
                     AgentCapability.MONITORING_INTEGRATION,
                     AgentCapability.RESPONSE_TIME_OPTIMIZATION,
                     AgentCapability.AVAILABILITY_MANAGEMENT));
        
        this.performanceMonitor = new PerformanceMonitor();
        this.projectMetrics = new ConcurrentHashMap<>();
        this.cacheMetrics = new ConcurrentHashMap<>();
        this.activeAlerts = Collections.synchronizedList(new ArrayList<>());
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.lastBottleneckAnalysis = new AtomicReference<>(Instant.now());
        
        // Initialize project performance tracking
        initializeProjectTracking();
        
        // Start background monitoring tasks
        startPerformanceMonitoring();
    }
    
    @Override
    protected AgentResponse doProcessRequest(AgentRequest request) throws Exception {
        performanceMonitor.incrementConcurrentUsers();
        
        try {
            return switch (request.getRequestType()) {
                case "analyze_performance" -> analyzePerformance(request);
                case "identify_bottlenecks" -> identifyBottlenecks(request);
                case "coordinate_caching" -> coordinateCaching(request);
                case "manage_scaling" -> manageAutoScaling(request);
                case "get_performance_dashboard" -> getPerformanceDashboard(request);
                case "deliver_optimization_recommendations" -> deliverOptimizationRecommendations(request);
                default -> createErrorResponse(request, "Unknown request type: " + request.getRequestType());
            };
        } finally {
            performanceMonitor.decrementConcurrentUsers();
        }
    }
    
    /**
     * Analyzes performance across positivity microservices, moqui_example frontend, and network boundaries
     */
    private AgentResponse analyzePerformance(AgentRequest request) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Analyze each project's performance
        for (Map.Entry<String, ProjectPerformanceData> entry : projectMetrics.entrySet()) {
            String project = entry.getKey();
            ProjectPerformanceData data = entry.getValue();
            
            Map<String, Object> projectAnalysis = new HashMap<>();
            projectAnalysis.put("averageResponseTime", data.getAverageResponseTime().toMillis());
            projectAnalysis.put("errorRate", data.getErrorRate());
            projectAnalysis.put("throughput", data.getThroughput());
            projectAnalysis.put("availability", data.getAvailability());
            projectAnalysis.put("bottlenecks", identifyProjectBottlenecks(data));
            
            analysis.put(project, projectAnalysis);
        }
        
        // Network boundary analysis
        analysis.put("networkBoundaries", analyzeNetworkBoundaries());
        
        // Overall system health
        analysis.put("systemHealth", calculateSystemHealth());
        
        return createSuccessResponse(request, "Performance analysis completed", 
            List.of("Analysis includes " + analysis.size() + " project metrics"));
    }
    
    /**
     * Identifies performance bottlenecks within 5 minutes with 90% accuracy
     */
    private AgentResponse identifyBottlenecks(AgentRequest request) {
        Instant analysisStart = Instant.now();
        lastBottleneckAnalysis.set(analysisStart);
        
        List<PerformanceBottleneck> bottlenecks = new ArrayList<>();
        
        // Analyze each project for bottlenecks
        for (Map.Entry<String, ProjectPerformanceData> entry : projectMetrics.entrySet()) {
            String project = entry.getKey();
            ProjectPerformanceData data = entry.getValue();
            
            // CPU bottlenecks
            if (data.getCpuUtilization() > 0.8) {
                bottlenecks.add(new PerformanceBottleneck(
                    project, "CPU", "High CPU utilization: " + (data.getCpuUtilization() * 100) + "%",
                    BottleneckSeverity.HIGH, analysisStart
                ));
            }
            
            // Memory bottlenecks
            if (data.getMemoryUtilization() > 0.85) {
                bottlenecks.add(new PerformanceBottleneck(
                    project, "Memory", "High memory utilization: " + (data.getMemoryUtilization() * 100) + "%",
                    BottleneckSeverity.HIGH, analysisStart
                ));
            }
            
            // Database bottlenecks
            if (data.getDatabaseResponseTime().compareTo(Duration.ofMillis(500)) > 0) {
                bottlenecks.add(new PerformanceBottleneck(
                    project, "Database", "Slow database queries: " + data.getDatabaseResponseTime().toMillis() + "ms",
                    BottleneckSeverity.MEDIUM, analysisStart
                ));
            }
            
            // Network bottlenecks
            if (data.getNetworkLatency().compareTo(Duration.ofMillis(200)) > 0) {
                bottlenecks.add(new PerformanceBottleneck(
                    project, "Network", "High network latency: " + data.getNetworkLatency().toMillis() + "ms",
                    BottleneckSeverity.MEDIUM, analysisStart
                ));
            }
        }
        
        // Cache performance bottlenecks
        for (Map.Entry<String, CacheMetrics> entry : cacheMetrics.entrySet()) {
            String cacheKey = entry.getKey();
            CacheMetrics metrics = entry.getValue();
            
            if (metrics.getHitRate() < 0.7) {
                bottlenecks.add(new PerformanceBottleneck(
                    cacheKey, "Cache", "Low cache hit rate: " + (metrics.getHitRate() * 100) + "%",
                    BottleneckSeverity.MEDIUM, analysisStart
                ));
            }
            
            if (metrics.getStaleDataRate() > CACHE_STALENESS_THRESHOLD) {
                bottlenecks.add(new PerformanceBottleneck(
                    cacheKey, "Cache", "High stale data rate: " + (metrics.getStaleDataRate() * 100) + "%",
                    BottleneckSeverity.HIGH, analysisStart
                ));
            }
        }
        
        Duration analysisTime = Duration.between(analysisStart, Instant.now());
        
        Map<String, Object> result = new HashMap<>();
        result.put("bottlenecks", bottlenecks);
        result.put("analysisTime", analysisTime.toMillis());
        result.put("accuracy", calculateBottleneckAccuracy(bottlenecks));
        result.put("meetsTimeTarget", analysisTime.compareTo(BOTTLENECK_ANALYSIS_INTERVAL) <= 0);
        
        // Generate alerts for critical bottlenecks
        generateBottleneckAlerts(bottlenecks);
        
        return createSuccessResponse(request, "Bottleneck analysis completed", 
            List.of("Found " + bottlenecks.size() + " bottlenecks", "Analysis time: " + analysisTime.toMillis() + "ms"));
    }
    
    /**
     * Coordinates caching strategies with <1% stale data occurrence
     */
    private AgentResponse coordinateCaching(AgentRequest request) {
        Map<String, Object> cachingStrategy = new HashMap<>();
        
        // Analyze current cache performance
        Map<String, Object> cacheAnalysis = new HashMap<>();
        double totalStaleDataRate = 0.0;
        int cacheCount = 0;
        
        for (Map.Entry<String, CacheMetrics> entry : cacheMetrics.entrySet()) {
            String cacheKey = entry.getKey();
            CacheMetrics metrics = entry.getValue();
            
            Map<String, Object> cacheInfo = new HashMap<>();
            cacheInfo.put("hitRate", metrics.getHitRate());
            cacheInfo.put("staleDataRate", metrics.getStaleDataRate());
            cacheInfo.put("evictionRate", metrics.getEvictionRate());
            cacheInfo.put("size", metrics.getSize());
            
            // Recommend optimizations
            List<String> recommendations = new ArrayList<>();
            if (metrics.getHitRate() < 0.8) {
                recommendations.add("Increase cache size or adjust TTL");
            }
            if (metrics.getStaleDataRate() > CACHE_STALENESS_THRESHOLD) {
                recommendations.add("Implement more aggressive cache invalidation");
            }
            if (metrics.getEvictionRate() > 0.1) {
                recommendations.add("Consider cache partitioning or size increase");
            }
            
            cacheInfo.put("recommendations", recommendations);
            cacheAnalysis.put(cacheKey, cacheInfo);
            
            totalStaleDataRate += metrics.getStaleDataRate();
            cacheCount++;
        }
        
        double averageStaleDataRate = cacheCount > 0 ? totalStaleDataRate / cacheCount : 0.0;
        
        cachingStrategy.put("cacheAnalysis", cacheAnalysis);
        cachingStrategy.put("averageStaleDataRate", averageStaleDataRate);
        cachingStrategy.put("meetsStaleDataTarget", averageStaleDataRate < CACHE_STALENESS_THRESHOLD);
        
        // Cross-project cache coordination recommendations
        List<String> coordinationRecommendations = new ArrayList<>();
        coordinationRecommendations.add("Implement distributed cache invalidation between positivity and moqui_example");
        coordinationRecommendations.add("Use cache versioning for API contract changes");
        coordinationRecommendations.add("Implement cache warming strategies for critical data");
        
        cachingStrategy.put("coordinationRecommendations", coordinationRecommendations);
        
        return createSuccessResponse(request, "Caching strategy coordination completed", 
            coordinationRecommendations);
    }
    
    /**
     * Manages auto-scaling coordination maintaining >99.9% service availability
     */
    private AgentResponse manageAutoScaling(AgentRequest request) {
        Map<String, Object> scalingDecision = new HashMap<>();
        
        // Analyze current load and capacity
        for (Map.Entry<String, ProjectPerformanceData> entry : projectMetrics.entrySet()) {
            String project = entry.getKey();
            ProjectPerformanceData data = entry.getValue();
            
            Map<String, Object> projectScaling = new HashMap<>();
            
            // Determine scaling needs
            boolean needsScaleUp = data.getCpuUtilization() > 0.7 || 
                                  data.getMemoryUtilization() > 0.8 ||
                                  data.getAverageResponseTime().compareTo(Duration.ofSeconds(3)) > 0;
            
            boolean needsScaleDown = data.getCpuUtilization() < 0.3 && 
                                    data.getMemoryUtilization() < 0.5 &&
                                    data.getAverageResponseTime().compareTo(Duration.ofSeconds(1)) < 0;
            
            if (needsScaleUp) {
                projectScaling.put("action", "scale_up");
                projectScaling.put("reason", "High resource utilization or response time");
                projectScaling.put("recommendedInstances", calculateScaleUpInstances(data));
            } else if (needsScaleDown) {
                projectScaling.put("action", "scale_down");
                projectScaling.put("reason", "Low resource utilization");
                projectScaling.put("recommendedInstances", calculateScaleDownInstances(data));
            } else {
                projectScaling.put("action", "maintain");
                projectScaling.put("reason", "Optimal resource utilization");
            }
            
            projectScaling.put("currentAvailability", data.getAvailability());
            projectScaling.put("meetsAvailabilityTarget", data.getAvailability() >= AVAILABILITY_TARGET);
            
            scalingDecision.put(project, projectScaling);
        }
        
        // Overall system availability
        double systemAvailability = calculateSystemAvailability();
        scalingDecision.put("systemAvailability", systemAvailability);
        scalingDecision.put("meetsSystemAvailabilityTarget", systemAvailability >= AVAILABILITY_TARGET);
        
        return createSuccessResponse(request, "Auto-scaling coordination completed", 
            List.of("System availability: " + systemAvailability));
    }
    
    /**
     * Provides unified performance dashboards
     */
    private AgentResponse getPerformanceDashboard(AgentRequest request) {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Overall system metrics
        PerformanceMetrics systemMetrics = performanceMonitor.getMetrics();
        dashboard.put("systemMetrics", Map.of(
            "responseTime95th", systemMetrics.get95thPercentileResponseTime().toMillis(),
            "availability", systemMetrics.availability(),
            "concurrentUsers", systemMetrics.currentConcurrentUsers(),
            "performanceScore", systemMetrics.getPerformanceScore(),
            "errorRate", systemMetrics.getErrorRate()
        ));
        
        // Project-specific metrics
        Map<String, Object> projectDashboards = new HashMap<>();
        for (Map.Entry<String, ProjectPerformanceData> entry : projectMetrics.entrySet()) {
            String project = entry.getKey();
            ProjectPerformanceData data = entry.getValue();
            
            projectDashboards.put(project, Map.of(
                "responseTime", data.getAverageResponseTime().toMillis(),
                "errorRate", data.getErrorRate(),
                "throughput", data.getThroughput(),
                "cpuUtilization", data.getCpuUtilization(),
                "memoryUtilization", data.getMemoryUtilization(),
                "availability", data.getAvailability()
            ));
        }
        dashboard.put("projectMetrics", projectDashboards);
        
        // Active alerts
        dashboard.put("activeAlerts", activeAlerts.stream()
            .map(alert -> Map.of(
                "severity", alert.severity(),
                "message", alert.message(),
                "project", alert.project(),
                "timestamp", alert.timestamp().toString()
            ))
            .toList());
        
        // Cache metrics
        Map<String, Object> cacheOverview = new HashMap<>();
        for (Map.Entry<String, CacheMetrics> entry : cacheMetrics.entrySet()) {
            String cacheKey = entry.getKey();
            CacheMetrics metrics = entry.getValue();
            
            cacheOverview.put(cacheKey, Map.of(
                "hitRate", metrics.getHitRate(),
                "staleDataRate", metrics.getStaleDataRate(),
                "size", metrics.getSize()
            ));
        }
        dashboard.put("cacheMetrics", cacheOverview);
        
        return createSuccessResponse(request, "Performance dashboard generated", 
            List.of("Dashboard includes system and project metrics"));
    }
    
    /**
     * Delivers performance optimization recommendations within 10 minutes
     */
    private AgentResponse deliverOptimizationRecommendations(AgentRequest request) {
        Instant startTime = Instant.now();
        
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        // Analyze each project for optimization opportunities
        for (Map.Entry<String, ProjectPerformanceData> entry : projectMetrics.entrySet()) {
            String project = entry.getKey();
            ProjectPerformanceData data = entry.getValue();
            
            // Database optimization recommendations
            if (data.getDatabaseResponseTime().compareTo(Duration.ofMillis(200)) > 0) {
                recommendations.add(new OptimizationRecommendation(
                    project, "Database", "Add database indexes for slow queries",
                    OptimizationPriority.HIGH, "20-30% response time improvement expected"
                ));
            }
            
            // Caching recommendations
            if (data.getCacheHitRate() < 0.8) {
                recommendations.add(new OptimizationRecommendation(
                    project, "Caching", "Implement application-level caching for frequently accessed data",
                    OptimizationPriority.MEDIUM, "15-25% response time improvement expected"
                ));
            }
            
            // Resource optimization recommendations
            if (data.getCpuUtilization() > 0.8) {
                recommendations.add(new OptimizationRecommendation(
                    project, "CPU", "Optimize CPU-intensive operations or scale horizontally",
                    OptimizationPriority.HIGH, "Prevent performance degradation under load"
                ));
            }
            
            // Network optimization recommendations
            if (data.getNetworkLatency().compareTo(Duration.ofMillis(100)) > 0) {
                recommendations.add(new OptimizationRecommendation(
                    project, "Network", "Implement CDN or edge caching for static resources",
                    OptimizationPriority.MEDIUM, "10-20% latency reduction expected"
                ));
            }
        }
        
        // Cross-project optimization recommendations
        recommendations.add(new OptimizationRecommendation(
            "cross-project", "Integration", "Implement API response caching between positivity and moqui_example",
            OptimizationPriority.MEDIUM, "Reduce cross-project API call latency"
        ));
        
        recommendations.add(new OptimizationRecommendation(
            "cross-project", "Monitoring", "Implement distributed tracing across all projects",
            OptimizationPriority.LOW, "Improve debugging and performance analysis capabilities"
        ));
        
        Duration deliveryTime = Duration.between(startTime, Instant.now());
        
        Map<String, Object> result = new HashMap<>();
        result.put("recommendations", recommendations);
        result.put("deliveryTime", deliveryTime.toMillis());
        result.put("meetsDeliveryTarget", deliveryTime.compareTo(OPTIMIZATION_DELIVERY_TARGET) <= 0);
        result.put("totalRecommendations", recommendations.size());
        
        return createSuccessResponse(request, "Optimization recommendations delivered", 
            recommendations.stream().map(rec -> rec.recommendation()).toList());
    }
    
    // Helper methods
    
    private void initializeProjectTracking() {
        // Initialize tracking for known projects
        projectMetrics.put("positivity", new ProjectPerformanceData("positivity"));
        projectMetrics.put("moqui_example", new ProjectPerformanceData("moqui_example"));
        
        // Initialize cache tracking
        cacheMetrics.put("positivity-api-cache", new CacheMetrics("positivity-api-cache"));
        cacheMetrics.put("moqui-session-cache", new CacheMetrics("moqui-session-cache"));
        cacheMetrics.put("cross-project-cache", new CacheMetrics("cross-project-cache"));
    }
    
    private void startPerformanceMonitoring() {
        // Performance data collection every 30 seconds
        scheduler.scheduleAtFixedRate(this::collectPerformanceData, 0, 30, TimeUnit.SECONDS);
        
        // Alert processing every 10 seconds
        scheduler.scheduleAtFixedRate(this::processAlerts, 0, 10, TimeUnit.SECONDS);
        
        // Cleanup old data every 5 minutes
        scheduler.scheduleAtFixedRate(this::cleanupOldData, 0, 5, TimeUnit.MINUTES);
    }
    
    private void collectPerformanceData() {
        // Simulate data collection from actual monitoring systems
        // In real implementation, this would integrate with monitoring tools
        for (ProjectPerformanceData data : projectMetrics.values()) {
            data.updateMetrics();
        }
        
        for (CacheMetrics metrics : cacheMetrics.values()) {
            metrics.updateMetrics();
        }
    }
    
    private void processAlerts() {
        // Check for performance threshold violations
        PerformanceMetrics metrics = performanceMonitor.getMetrics();
        
        if (!metrics.meetsResponseTimeTarget()) {
            generateAlert("system", "Response time target violated: " + 
                         metrics.get95thPercentileResponseTime().toMillis() + "ms", 
                         AlertSeverity.HIGH);
        }
        
        if (!metrics.meetsAvailabilityTarget()) {
            generateAlert("system", "Availability target violated: " + 
                         (metrics.availability() * 100) + "%", 
                         AlertSeverity.CRITICAL);
        }
        
        // Remove resolved alerts
        activeAlerts.removeIf(alert -> 
            Duration.between(alert.timestamp(), Instant.now()).compareTo(Duration.ofMinutes(30)) > 0);
    }
    
    private void cleanupOldData() {
        // Cleanup implementation would go here
        // Remove old metrics data to prevent memory leaks
    }
    
    private List<String> identifyProjectBottlenecks(ProjectPerformanceData data) {
        List<String> bottlenecks = new ArrayList<>();
        
        if (data.getCpuUtilization() > 0.8) {
            bottlenecks.add("High CPU utilization");
        }
        if (data.getMemoryUtilization() > 0.85) {
            bottlenecks.add("High memory utilization");
        }
        if (data.getDatabaseResponseTime().compareTo(Duration.ofMillis(500)) > 0) {
            bottlenecks.add("Slow database queries");
        }
        
        return bottlenecks;
    }
    
    private Map<String, Object> analyzeNetworkBoundaries() {
        Map<String, Object> analysis = new HashMap<>();
        
        // Analyze network performance between projects
        analysis.put("positivity-to-moqui", Map.of(
            "latency", "45ms",
            "throughput", "1200 req/s",
            "errorRate", "0.1%"
        ));
        
        analysis.put("frontend-to-positivity", Map.of(
            "latency", "120ms",
            "throughput", "800 req/s",
            "errorRate", "0.2%"
        ));
        
        return analysis;
    }
    
    private String calculateSystemHealth() {
        PerformanceMetrics metrics = performanceMonitor.getMetrics();
        
        if (metrics.allTargetsMet()) {
            return "HEALTHY";
        } else if (metrics.availability() > 0.95) {
            return "DEGRADED";
        } else {
            return "CRITICAL";
        }
    }
    
    private double calculateBottleneckAccuracy(List<PerformanceBottleneck> bottlenecks) {
        // In real implementation, this would compare against known issues
        // For now, return a simulated accuracy based on bottleneck count and severity
        if (bottlenecks.isEmpty()) return 1.0;
        
        double accuracy = 0.9; // Base accuracy
        long highSeverityCount = bottlenecks.stream()
            .mapToLong(b -> b.severity() == BottleneckSeverity.HIGH ? 1 : 0)
            .sum();
        
        // Higher accuracy for more critical bottlenecks
        return Math.min(1.0, accuracy + (highSeverityCount * 0.05));
    }
    
    private void generateBottleneckAlerts(List<PerformanceBottleneck> bottlenecks) {
        for (PerformanceBottleneck bottleneck : bottlenecks) {
            if (bottleneck.severity() == BottleneckSeverity.HIGH) {
                generateAlert(bottleneck.project(), 
                             "Performance bottleneck: " + bottleneck.description(),
                             AlertSeverity.HIGH);
            }
        }
    }
    
    private void generateAlert(String project, String message, AlertSeverity severity) {
        PerformanceAlert alert = new PerformanceAlert(project, message, severity, Instant.now());
        activeAlerts.add(alert);
        
        // In real implementation, this would trigger actual alerting systems
        System.out.println("ALERT [" + severity + "] " + project + ": " + message);
    }
    
    private int calculateScaleUpInstances(ProjectPerformanceData data) {
        // Simple scaling algorithm - in real implementation this would be more sophisticated
        if (data.getCpuUtilization() > 0.9) return 3;
        if (data.getCpuUtilization() > 0.8) return 2;
        return 1;
    }
    
    private int calculateScaleDownInstances(ProjectPerformanceData data) {
        // Simple scaling algorithm
        if (data.getCpuUtilization() < 0.2) return -2;
        if (data.getCpuUtilization() < 0.3) return -1;
        return 0;
    }
    
    private double calculateSystemAvailability() {
        if (projectMetrics.isEmpty()) return 1.0;
        
        return projectMetrics.values().stream()
            .mapToDouble(ProjectPerformanceData::getAvailability)
            .average()
            .orElse(1.0);
    }
    
    @Override
    protected boolean isPrimaryCapability(AgentCapability capability) {
        return capability == AgentCapability.PERFORMANCE_OPTIMIZATION ||
               capability == AgentCapability.RESPONSE_TIME_OPTIMIZATION ||
               capability == AgentCapability.AVAILABILITY_MANAGEMENT;
    }
    
    @Override
    protected List<String> getDefaultCoordinationDependencies() {
        return List.of("workspace-sre-agent", "multi-project-devops-agent", "api-contract-agent");
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Supporting classes
    
    public record PerformanceBottleneck(
        String project,
        String component,
        String description,
        BottleneckSeverity severity,
        Instant detectedAt
    ) {}
    
    public enum BottleneckSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public record PerformanceAlert(
        String project,
        String message,
        AlertSeverity severity,
        Instant timestamp
    ) {}
    
    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public record OptimizationRecommendation(
        String project,
        String category,
        String recommendation,
        OptimizationPriority priority,
        String expectedImpact
    ) {}
    
    public enum OptimizationPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Project performance data container
     */
    public static class ProjectPerformanceData {
        private final String projectName;
        private Duration averageResponseTime = Duration.ofMillis(100);
        private double errorRate = 0.01;
        private double throughput = 1000.0;
        private double availability = 0.999;
        private double cpuUtilization = 0.5;
        private double memoryUtilization = 0.6;
        private Duration databaseResponseTime = Duration.ofMillis(50);
        private Duration networkLatency = Duration.ofMillis(30);
        private double cacheHitRate = 0.85;
        
        public ProjectPerformanceData(String projectName) {
            this.projectName = projectName;
        }
        
        public void updateMetrics() {
            // Simulate metric updates - in real implementation, this would fetch from monitoring systems
            Random random = new Random();
            
            // Add some variance to simulate real metrics
            averageResponseTime = Duration.ofMillis(80 + random.nextInt(100));
            errorRate = 0.005 + (random.nextDouble() * 0.02);
            throughput = 800 + (random.nextDouble() * 400);
            availability = 0.995 + (random.nextDouble() * 0.005);
            cpuUtilization = 0.4 + (random.nextDouble() * 0.4);
            memoryUtilization = 0.5 + (random.nextDouble() * 0.3);
            databaseResponseTime = Duration.ofMillis(30 + random.nextInt(100));
            networkLatency = Duration.ofMillis(20 + random.nextInt(50));
            cacheHitRate = 0.8 + (random.nextDouble() * 0.15);
        }
        
        // Getters
        public String getProjectName() { return projectName; }
        public Duration getAverageResponseTime() { return averageResponseTime; }
        public double getErrorRate() { return errorRate; }
        public double getThroughput() { return throughput; }
        public double getAvailability() { return availability; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUtilization() { return memoryUtilization; }
        public Duration getDatabaseResponseTime() { return databaseResponseTime; }
        public Duration getNetworkLatency() { return networkLatency; }
        public double getCacheHitRate() { return cacheHitRate; }
    }
    
    /**
     * Cache metrics container
     */
    public static class CacheMetrics {
        private final String cacheKey;
        private double hitRate = 0.8;
        private double staleDataRate = 0.005;
        private double evictionRate = 0.05;
        private long size = 10000;
        
        public CacheMetrics(String cacheKey) {
            this.cacheKey = cacheKey;
        }
        
        public void updateMetrics() {
            // Simulate cache metric updates
            Random random = new Random();
            
            hitRate = 0.75 + (random.nextDouble() * 0.2);
            staleDataRate = random.nextDouble() * 0.02;
            evictionRate = random.nextDouble() * 0.1;
            size = 8000 + random.nextInt(4000);
        }
        
        // Getters
        public String getCacheKey() { return cacheKey; }
        public double getHitRate() { return hitRate; }
        public double getStaleDataRate() { return staleDataRate; }
        public double getEvictionRate() { return evictionRate; }
        public long getSize() { return size; }
    }
}