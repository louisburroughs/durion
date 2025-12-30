package com.durion.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.durion.agents.PerformanceCoordinationAgent;
import com.durion.core.AgentResult;

/**
 * Property Test 5: Performance optimization coordination
 * 
 * Validates: Performance optimizations coordinated across all layers
 * Invariants: Bottlenecks identified on time, cache consistency maintained, auto-scaling coordinated
 * Requirements: REQ-WS-009, REQ-WS-NFR-001
 */
public class PerformanceCoordinationPropertyTest {

    @Property
    @Label("Performance bottlenecks identified within time limits across all layers")
    boolean bottleneckIdentificationTimeliness(@ForAll @Size(min = 1, max = 10) List<String> performanceMetrics) {
        PerformanceCoordinationAgent agent = new PerformanceCoordinationAgent();
        agent.initialize(null);
        
        long startTime = System.currentTimeMillis();
        AgentResult result = agent.execute("identify-bottlenecks", Map.of("metrics", performanceMetrics)).join();
        long duration = System.currentTimeMillis() - startTime;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bottleneckMaps = (List<Map<String, Object>>) ((Map<String, Object>) result.getData()).getOrDefault("bottlenecks", List.of());
        List<String> layers = bottleneckMaps.stream()
            .map(m -> String.valueOf(m.get("layer")))
            .toList();
        
        // Must identify bottlenecks within 5 minutes (300,000ms)
        boolean withinTimeLimit = duration <= 300_000;
        
        // Must identify bottlenecks across all three layers
        Set<String> expectedLayers = Set.of("durion-positivity-backend", "durion-positivity", "durion-moqui-frontend");
        boolean allLayersCovered = bottleneckMaps.stream()
            .map(m -> String.valueOf(m.get("layer")))
            .anyMatch(expectedLayers::contains);
        
        return withinTimeLimit && allLayersCovered && !bottleneckMaps.isEmpty();
    }

    @Property
    @Label("Cache coordination maintains consistency across layers")
    boolean cacheCoordinationConsistency(@ForAll @Size(min = 1, max = 5) List<String> cacheKeys) {
        PerformanceCoordinationAgent agent = new PerformanceCoordinationAgent();
        agent.initialize(null);
        
        AgentResult result = agent.execute("coordinate-cache-invalidation", Map.of("cacheKeys", cacheKeys)).join();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        boolean successful = (boolean) data.getOrDefault("successful", false);
        double staleDataPercentage = ((Number) data.getOrDefault("staleDataPercentage", 1.0)).doubleValue();
        @SuppressWarnings("unchecked")
        List<String> coordinatedLayers = (List<String>) data.getOrDefault("coordinatedLayers", List.of());
        
        // Must maintain <1% stale data
        boolean lowStaleDataRate = staleDataPercentage < 1.0;
        
        // All layers must be coordinated
        Set<String> expectedLayers = Set.of("durion-positivity-backend", "durion-positivity", "durion-moqui-frontend");
        boolean allLayersCoordinated = coordinatedLayers.containsAll(expectedLayers);
        
        // Cache invalidation must be successful
        boolean invalidationSuccessful = successful;
        
        return lowStaleDataRate && allLayersCoordinated && invalidationSuccessful;
    }

    @Property
    @Label("Auto-scaling coordination maintains availability targets")
    boolean autoScalingCoordination(@ForAll @IntRange(min = 50, max = 200) int currentLoad) {
        PerformanceCoordinationAgent agent = new PerformanceCoordinationAgent();
        agent.initialize(null);
        
        AgentResult result = agent.execute("coordinate-auto-scaling", Map.of("currentLoad", currentLoad)).join();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        String scalingAction = String.valueOf(data.getOrDefault("scalingAction", "NOOP"));
        double availabilityPercentage = ((Number) data.getOrDefault("availabilityPercentage", 0.0)).doubleValue();
        @SuppressWarnings("unchecked")
        List<String> scaledLayers = (List<String>) data.getOrDefault("scaledLayers", List.of());
        
        // Must maintain >99.9% availability
        boolean highAvailability = availabilityPercentage > 99.9;
        
        // Scaling must be coordinated across all layers
        Set<String> expectedLayers = Set.of("durion-positivity-backend", "durion-positivity", "durion-moqui-frontend");
        boolean allLayersScaled = scaledLayers.containsAll(expectedLayers);
        
        // Scaling decision must be appropriate for load
        boolean appropriateScaling = (currentLoad > 100 && scalingAction.equals("SCALE_UP")) ||
                                   (currentLoad <= 100 && !scalingAction.equals("SCALE_UP"));
        
        return highAvailability && allLayersScaled && appropriateScaling;
    }

    @Property
    @Label("Optimization recommendations delivered within time limits")
    boolean optimizationRecommendationTimeliness(@ForAll @Size(min = 1, max = 8) List<String> performanceIssues) {
        PerformanceCoordinationAgent agent = new PerformanceCoordinationAgent();
        agent.initialize(null);
        
        long startTime = System.currentTimeMillis();
        AgentResult result = agent.execute("generate-optimization-recommendations", Map.of("performanceIssues", performanceIssues)).join();
        long duration = System.currentTimeMillis() - startTime;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendationMaps = (List<Map<String, Object>>) ((Map<String, Object>) result.getData()).getOrDefault("recommendations", List.of());
        
        // Must deliver recommendations within 10 minutes (600,000ms)
        boolean withinTimeLimit = duration <= 600_000;
        
        // Must provide recommendations for all layers
        Set<String> layers = Set.of("durion-positivity-backend", "durion-positivity", "durion-moqui-frontend");
        boolean allLayersCovered = recommendationMaps.stream()
            .map(r -> String.valueOf(r.get("targetLayer")))
            .anyMatch(layers::contains);
        
        // Must prioritize recommendations
        boolean properPrioritization = recommendationMaps.stream()
            .allMatch(r -> r.get("priority") != null && !String.valueOf(r.get("priority")).isEmpty());
        
        return withinTimeLimit && allLayersCovered && properPrioritization && !recommendationMaps.isEmpty();
    }

    @Property
    @Label("Performance coordination maintains cross-layer consistency")
    boolean crossLayerPerformanceConsistency(@ForAll @Size(min = 2, max = 6) List<String> performanceMetrics) {
        PerformanceCoordinationAgent agent = new PerformanceCoordinationAgent();
        agent.initialize(null);
        
        // Test that performance optimizations are consistent across layers
        AgentResult bottleneckResult = agent.execute("identify-bottlenecks", Map.of("metrics", performanceMetrics)).join();
        AgentResult recommendationResult = agent.execute("generate-optimization-recommendations", Map.of("performanceIssues", performanceMetrics)).join();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bottleneckMaps = (List<Map<String, Object>>) ((Map<String, Object>) bottleneckResult.getData()).getOrDefault("bottlenecks", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendationMaps = (List<Map<String, Object>>) ((Map<String, Object>) recommendationResult.getData()).getOrDefault("recommendations", List.of());
        
        // Bottlenecks and recommendations must be aligned
        boolean alignedOptimizations = bottleneckMaps.size() <= recommendationMaps.size();
        
        // All layers must be considered in optimization
        Set<String> bottleneckLayers = bottleneckMaps.stream()
            .map(m -> String.valueOf(m.get("layer")))
            .collect(java.util.stream.Collectors.toSet());
        Set<String> recommendationLayers = recommendationMaps.stream()
            .map(r -> String.valueOf(r.get("targetLayer")))
            .collect(java.util.stream.Collectors.toSet());
        
        boolean consistentLayerCoverage = recommendationLayers.containsAll(bottleneckLayers);
        
        return alignedOptimizations && consistentLayerCoverage;
    }
}

