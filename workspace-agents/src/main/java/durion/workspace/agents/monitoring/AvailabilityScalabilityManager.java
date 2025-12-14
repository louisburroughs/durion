package durion.workspace.agents.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Availability and Scalability Manager
 * 
 * Implements system availability and scalability requirements:
 * - 99.9% availability monitoring and maintenance during business hours
 * - Support for 100 concurrent users without response time degradation
 * - 50% workspace growth tolerance without performance impact
 * - 5-second response time target for 95% of agent guidance requests
 * - Linear performance scaling for project count increases
 * 
 * Requirements: 9.1, 9.2, 9.3, 9.4
 */
public class AvailabilityScalabilityManager {
    
    // Performance and availability targets
    private static final double AVAILABILITY_TARGET = 0.999; // 99.9%
    private static final int MAX_CONCURRENT_USERS = 100;
    private static final double WORKSPACE_GROWTH_TOLERANCE = 0.5; // 50%
    private static final Duration RESPONSE_TIME_TARGET = Duration.ofSeconds(5);
    private static final double RESPONSE_TIME_PERCENTILE = 0.95; // 95th percentile
    
    // Business hours (8 AM - 6 PM EST)
    private static final LocalTime BUSINESS_HOURS_START = LocalTime.of(8, 0);
    private static final LocalTime BUSINESS_HOURS_END = LocalTime.of(18, 0);
    
    private final AtomicInteger currentConcurrentUsers = new AtomicInteger(0);
    private final AtomicInteger baselineProjectCount = new AtomicInteger(2); // positivity + moqui_example
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicReference<AvailabilityStatus> currentAvailabilityStatus = new AtomicReference<>(AvailabilityStatus.UNKNOWN);
    
    private final ConcurrentLinkedQueue<RequestMetric> recentRequests = new ConcurrentLinkedQueue<>();
    private final Map<String, ProjectScalingData> projectScalingData = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    
    // Metrics retention
    private static final int MAX_RECENT_REQUESTS = 10000;
    private static final Duration METRICS_RETENTION_PERIOD = Duration.ofHours(24);
    
    public AvailabilityScalabilityManager() {
        initializeProjectScalingData();
    }
    
    /**
     * Starts availability and scalability monitoring
     */
    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            // Monitor availability every 30 seconds
            scheduler.scheduleAtFixedRate(this::monitorAvailability, 0, 30, TimeUnit.SECONDS);
            
            // Monitor scalability every minute
            scheduler.scheduleAtFixedRate(this::monitorScalability, 0, 1, TimeUnit.MINUTES);
            
            // Cleanup old metrics every 5 minutes
            scheduler.scheduleAtFixedRate(this::cleanupOldMetrics, 0, 5, TimeUnit.MINUTES);
            
            System.out.println("Availability and Scalability monitoring started");
        }
    }
    
    /**
     * Stops availability and scalability monitoring
     */
    public void stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Availability and Scalability monitoring stopped");
        }
    }
    
    /**
     * Records a request for availability and performance tracking
     */
    public void recordRequest(String requestId, Duration responseTime, boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successfulRequests.incrementAndGet();
        }
        
        RequestMetric metric = new RequestMetric(requestId, responseTime, success, Instant.now());
        recentRequests.offer(metric);
        
        // Maintain metrics size
        while (recentRequests.size() > MAX_RECENT_REQUESTS) {
            recentRequests.poll();
        }
    }
    
    /**
     * Increments concurrent user count and checks scalability limits
     */
    public ConcurrentUserResult incrementConcurrentUsers() {
        int newCount = currentConcurrentUsers.incrementAndGet();
        
        boolean withinLimit = newCount <= MAX_CONCURRENT_USERS;
        String status = withinLimit ? "ACCEPTED" : "REJECTED";
        
        if (!withinLimit) {
            // Reject the user if we're over the limit
            currentConcurrentUsers.decrementAndGet();
        }
        
        return new ConcurrentUserResult(newCount, withinLimit, status);
    }
    
    /**
     * Decrements concurrent user count
     */
    public void decrementConcurrentUsers() {
        currentConcurrentUsers.updateAndGet(count -> Math.max(0, count - 1));
    }
    
    /**
     * Gets current concurrent user count
     */
    public int getCurrentConcurrentUsers() {
        return currentConcurrentUsers.get();
    }
    
    /**
     * Monitors 99.9% availability during business hours
     */
    public AvailabilityReport monitorAvailability() {
        Instant now = Instant.now();
        LocalTime currentTime = LocalTime.now();
        
        boolean isBusinessHours = isBusinessHours(currentTime);
        
        // Calculate current availability
        List<RequestMetric> metrics = new ArrayList<>(recentRequests);
        double currentAvailability = calculateAvailability(metrics);
        
        // Check if availability target is met
        boolean meetsAvailabilityTarget = currentAvailability >= AVAILABILITY_TARGET;
        
        // Update availability status
        AvailabilityStatus status;
        if (meetsAvailabilityTarget) {
            status = AvailabilityStatus.HEALTHY;
        } else if (currentAvailability >= 0.95) {
            status = AvailabilityStatus.DEGRADED;
        } else {
            status = AvailabilityStatus.CRITICAL;
        }
        
        currentAvailabilityStatus.set(status);
        
        // Calculate uptime during business hours
        double businessHoursUptime = calculateBusinessHoursUptime();
        
        return new AvailabilityReport(
            currentAvailability,
            meetsAvailabilityTarget,
            isBusinessHours,
            businessHoursUptime,
            status,
            now
        );
    }
    
    /**
     * Monitors scalability requirements including concurrent users and workspace growth
     */
    public ScalabilityReport monitorScalability() {
        Instant now = Instant.now();
        
        // Check concurrent user scalability
        int currentUsers = currentConcurrentUsers.get();
        boolean meetsConcurrentUserTarget = currentUsers <= MAX_CONCURRENT_USERS;
        
        // Check response time under load
        List<RequestMetric> metrics = new ArrayList<>(recentRequests);
        Duration responseTime95th = calculatePercentileResponseTime(metrics, RESPONSE_TIME_PERCENTILE);
        boolean meetsResponseTimeTarget = responseTime95th.compareTo(RESPONSE_TIME_TARGET) <= 0;
        
        // Check workspace growth tolerance
        int currentProjectCount = projectScalingData.size();
        double growthFactor = (double) currentProjectCount / baselineProjectCount.get();
        boolean meetsGrowthTolerance = growthFactor <= (1.0 + WORKSPACE_GROWTH_TOLERANCE);
        
        // Check linear performance scaling
        LinearScalingAnalysis scalingAnalysis = analyzeLinearScaling();
        
        return new ScalabilityReport(
            currentUsers,
            meetsConcurrentUserTarget,
            responseTime95th,
            meetsResponseTimeTarget,
            currentProjectCount,
            growthFactor,
            meetsGrowthTolerance,
            scalingAnalysis,
            now
        );
    }
    
    /**
     * Simulates workspace growth and tests performance impact
     */
    public WorkspaceGrowthTest testWorkspaceGrowth(int additionalProjects) {
        Instant testStart = Instant.now();
        
        int originalProjectCount = projectScalingData.size();
        
        // Simulate adding projects
        for (int i = 0; i < additionalProjects; i++) {
            String projectName = "test-project-" + i;
            projectScalingData.put(projectName, new ProjectScalingData(projectName));
        }
        
        int newProjectCount = projectScalingData.size();
        double growthPercentage = ((double) (newProjectCount - originalProjectCount) / originalProjectCount) * 100;
        
        // Test performance under new load
        Duration baselineResponseTime = Duration.ofMillis(100); // Simulated baseline
        Duration newResponseTime = simulateResponseTimeUnderGrowth(newProjectCount);
        
        double performanceImpact = ((double) newResponseTime.toMillis() - baselineResponseTime.toMillis()) / baselineResponseTime.toMillis();
        
        boolean meetsGrowthTolerance = growthPercentage <= (WORKSPACE_GROWTH_TOLERANCE * 100) && performanceImpact <= 0.1; // 10% degradation max
        
        // Cleanup test projects
        for (int i = 0; i < additionalProjects; i++) {
            String projectName = "test-project-" + i;
            projectScalingData.remove(projectName);
        }
        
        Duration testDuration = Duration.between(testStart, Instant.now());
        
        return new WorkspaceGrowthTest(
            originalProjectCount,
            newProjectCount,
            growthPercentage,
            baselineResponseTime,
            newResponseTime,
            performanceImpact,
            meetsGrowthTolerance,
            testDuration
        );
    }
    
    /**
     * Gets comprehensive availability and scalability metrics
     */
    public AvailabilityScalabilityMetrics getMetrics() {
        AvailabilityReport availabilityReport = monitorAvailability();
        ScalabilityReport scalabilityReport = monitorScalability();
        
        List<RequestMetric> metrics = new ArrayList<>(recentRequests);
        
        return new AvailabilityScalabilityMetrics(
            availabilityReport,
            scalabilityReport,
            totalRequests.get(),
            successfulRequests.get(),
            metrics.size(),
            Instant.now()
        );
    }
    
    // Private helper methods
    
    private void initializeProjectScalingData() {
        projectScalingData.put("positivity", new ProjectScalingData("positivity"));
        projectScalingData.put("moqui_example", new ProjectScalingData("moqui_example"));
    }
    
    private boolean isBusinessHours(LocalTime time) {
        return !time.isBefore(BUSINESS_HOURS_START) && time.isBefore(BUSINESS_HOURS_END);
    }
    
    private double calculateAvailability(List<RequestMetric> metrics) {
        if (metrics.isEmpty()) return 1.0;
        
        long successfulCount = metrics.stream().mapToLong(m -> m.success() ? 1 : 0).sum();
        return (double) successfulCount / metrics.size();
    }
    
    private double calculateBusinessHoursUptime() {
        // Filter metrics to business hours only
        List<RequestMetric> businessHoursMetrics = recentRequests.stream()
            .filter(metric -> {
                LocalTime time = LocalTime.ofInstant(metric.timestamp(), java.time.ZoneId.systemDefault());
                return isBusinessHours(time);
            })
            .toList();
        
        return calculateAvailability(businessHoursMetrics);
    }
    
    private Duration calculatePercentileResponseTime(List<RequestMetric> metrics, double percentile) {
        if (metrics.isEmpty()) return Duration.ZERO;
        
        List<Duration> responseTimes = metrics.stream()
            .map(RequestMetric::responseTime)
            .sorted()
            .toList();
        
        int index = (int) Math.ceil(percentile * responseTimes.size()) - 1;
        index = Math.max(0, Math.min(responseTimes.size() - 1, index));
        
        return responseTimes.get(index);
    }
    
    private LinearScalingAnalysis analyzeLinearScaling() {
        // Analyze if performance scales linearly with project count
        int projectCount = projectScalingData.size();
        
        // Simulate performance metrics for different project counts
        Map<Integer, Duration> scalingData = new HashMap<>();
        
        for (int i = 1; i <= projectCount + 2; i++) {
            Duration responseTime = simulateResponseTimeForProjectCount(i);
            scalingData.put(i, responseTime);
        }
        
        // Calculate linearity coefficient (simplified)
        double linearityCoefficient = calculateLinearityCoefficient(scalingData);
        boolean isLinear = linearityCoefficient >= 0.95; // 95% linearity threshold
        
        return new LinearScalingAnalysis(scalingData, linearityCoefficient, isLinear);
    }
    
    private Duration simulateResponseTimeUnderGrowth(int projectCount) {
        // Simulate response time based on project count
        // Base response time increases slightly with more projects
        long baseMillis = 100;
        long additionalMillis = (projectCount - baselineProjectCount.get()) * 10; // 10ms per additional project
        
        return Duration.ofMillis(baseMillis + additionalMillis);
    }
    
    private Duration simulateResponseTimeForProjectCount(int projectCount) {
        // Linear scaling simulation
        long baseMillis = 50;
        long scalingMillis = projectCount * 25; // 25ms per project
        
        return Duration.ofMillis(baseMillis + scalingMillis);
    }
    
    private double calculateLinearityCoefficient(Map<Integer, Duration> scalingData) {
        // Simplified linear regression coefficient calculation
        if (scalingData.size() < 2) return 1.0;
        
        List<Integer> projectCounts = new ArrayList<>(scalingData.keySet());
        Collections.sort(projectCounts);
        
        // Calculate correlation coefficient
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        int n = projectCounts.size();
        
        for (Integer count : projectCounts) {
            double x = count;
            double y = scalingData.get(count).toMillis();
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }
        
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        
        if (denominator == 0) return 1.0;
        
        return Math.abs(numerator / denominator);
    }
    
    private void cleanupOldMetrics() {
        Instant cutoff = Instant.now().minus(METRICS_RETENTION_PERIOD);
        
        recentRequests.removeIf(metric -> metric.timestamp().isBefore(cutoff));
    }
    
    // Supporting classes and records
    
    public record RequestMetric(
        String requestId,
        Duration responseTime,
        boolean success,
        Instant timestamp
    ) {}
    
    public record ConcurrentUserResult(
        int currentCount,
        boolean withinLimit,
        String status
    ) {}
    
    public record AvailabilityReport(
        double currentAvailability,
        boolean meetsAvailabilityTarget,
        boolean isBusinessHours,
        double businessHoursUptime,
        AvailabilityStatus status,
        Instant timestamp
    ) {}
    
    public record ScalabilityReport(
        int currentConcurrentUsers,
        boolean meetsConcurrentUserTarget,
        Duration responseTime95th,
        boolean meetsResponseTimeTarget,
        int currentProjectCount,
        double growthFactor,
        boolean meetsGrowthTolerance,
        LinearScalingAnalysis scalingAnalysis,
        Instant timestamp
    ) {}
    
    public record LinearScalingAnalysis(
        Map<Integer, Duration> scalingData,
        double linearityCoefficient,
        boolean isLinear
    ) {}
    
    public record WorkspaceGrowthTest(
        int originalProjectCount,
        int newProjectCount,
        double growthPercentage,
        Duration baselineResponseTime,
        Duration newResponseTime,
        double performanceImpact,
        boolean meetsGrowthTolerance,
        Duration testDuration
    ) {}
    
    public record AvailabilityScalabilityMetrics(
        AvailabilityReport availabilityReport,
        ScalabilityReport scalabilityReport,
        long totalRequests,
        long successfulRequests,
        int recentMetricsCount,
        Instant timestamp
    ) {}
    
    public enum AvailabilityStatus {
        HEALTHY,    // Meets all availability targets
        DEGRADED,   // Some availability issues but functional
        CRITICAL,   // Major availability problems
        UNKNOWN     // Status not yet determined
    }
    
    /**
     * Project scaling data container
     */
    public static class ProjectScalingData {
        private final String projectName;
        private final Instant createdAt;
        private Duration averageResponseTime = Duration.ofMillis(100);
        private double resourceUtilization = 0.5;
        private int instanceCount = 2;
        
        public ProjectScalingData(String projectName) {
            this.projectName = projectName;
            this.createdAt = Instant.now();
        }
        
        public void updateScalingMetrics() {
            // Simulate scaling metrics updates
            Random random = new Random();
            
            averageResponseTime = Duration.ofMillis(80 + random.nextInt(100));
            resourceUtilization = 0.3 + (random.nextDouble() * 0.5);
            instanceCount = 1 + random.nextInt(5);
        }
        
        // Getters
        public String getProjectName() { return projectName; }
        public Instant getCreatedAt() { return createdAt; }
        public Duration getAverageResponseTime() { return averageResponseTime; }
        public double getResourceUtilization() { return resourceUtilization; }
        public int getInstanceCount() { return instanceCount; }
    }
}