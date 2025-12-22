package properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import agents.FullStackIntegrationAgent;
import core.IntegrationGuidance;
import core.TechnologyStack;
import core.AuthenticationFlow;
import core.IntegrationPoint;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Property Test 2: Cross-layer integration guidance completeness
 * 
 * Validates: Integration guidance covers all technology combinations
 * Invariants: All integration points have guidance, authentication flows complete
 * Requirements: REQ-WS-003
 */
class CrossLayerIntegrationPropertyTest {

    private final FullStackIntegrationAgent agent = new FullStackIntegrationAgent();

    @Property
    @Label("Integration guidance covers all technology stack combinations")
    boolean integrationGuidanceCoversAllTechnologyCombinations(
            @ForAll @NotEmpty List<TechnologyStack> stacks) {
        
        IntegrationGuidance guidance = agent.generateIntegrationGuidance(stacks);
        
        // Property: All technology combinations must have guidance
        Set<String> stackCombinations = generateStackCombinations(stacks);
        Set<String> guidanceCombinations = guidance.getCoveredCombinations();
        
        return guidanceCombinations.containsAll(stackCombinations);
    }

    @Property
    @Label("All integration points have complete guidance")
    boolean allIntegrationPointsHaveCompleteGuidance(
            @ForAll @NotEmpty List<IntegrationPoint> integrationPoints) {
        
        IntegrationGuidance guidance = agent.generateIntegrationGuidance(integrationPoints);
        
        // Property: Every integration point must have guidance
        return integrationPoints.stream()
                .allMatch(point -> guidance.hasGuidanceFor(point) && 
                                 guidance.isComplete(point));
    }

    @Property
    @Label("Authentication flows are complete across all layers")
    boolean authenticationFlowsCompleteAcrossAllLayers(
            @ForAll @NotEmpty List<String> layers) {
        
        IntegrationGuidance guidance = agent.generateIntegrationGuidance(layers);
        
        // Property: Authentication flows must be complete for all layer combinations
        return layers.stream()
                .allMatch(layer -> {
                    AuthenticationFlow flow = guidance.getAuthenticationFlow(layer);
                    return flow != null && 
                           flow.hasJwtValidation() && 
                           flow.hasTokenConsistency() &&
                           flow.isSecure();
                });
    }

    @Property
    @Label("Integration guidance maintains consistency invariants")
    boolean integrationGuidanceMaintainsConsistencyInvariants(
            @ForAll @NotEmpty List<String> projects) {
        
        IntegrationGuidance guidance = agent.generateIntegrationGuidance(projects);
        
        // Invariant: JWT format must be identical across all projects
        Set<String> jwtFormats = projects.stream()
                .map(guidance::getJwtFormat)
                .collect(Collectors.toSet());
        
        // Invariant: API contracts must be compatible
        boolean apiContractsCompatible = projects.stream()
                .allMatch(project -> guidance.hasCompatibleApiContract(project));
        
        // Invariant: No authentication vulnerabilities
        boolean noAuthVulnerabilities = projects.stream()
                .noneMatch(project -> guidance.hasAuthenticationVulnerabilities(project));
        
        return jwtFormats.size() == 1 && 
               apiContractsCompatible && 
               noAuthVulnerabilities;
    }

    @Property
    @Label("Cross-project diagnostics provide complete coverage")
    boolean crossProjectDiagnosticsProvideCompleteCoverage(
            @ForAll @NotEmpty List<String> diagnosticScenarios) {
        
        IntegrationGuidance guidance = agent.generateIntegrationGuidance(diagnosticScenarios);
        
        // Property: Diagnostics must cover all integration scenarios
        return diagnosticScenarios.stream()
                .allMatch(scenario -> {
                    var diagnostic = guidance.getDiagnostic(scenario);
                    return diagnostic != null && 
                           diagnostic.hasRootCauseAnalysis() &&
                           diagnostic.getAccuracy() >= 0.90 &&
                           diagnostic.getResponseTime() <= 15000; // 15 seconds
                });
    }

    // Generators for test data
    @Provide
    Arbitrary<TechnologyStack> technologyStacks() {
        return Arbitraries.of(
            TechnologyStack.SPRING_BOOT_3X,
            TechnologyStack.MOQUI_3X,
            TechnologyStack.VUE_JS_3,
            TechnologyStack.JAVA_21,
            TechnologyStack.JAVA_11,
            TechnologyStack.GROOVY,
            TechnologyStack.TYPESCRIPT_5X
        );
    }

    @Provide
    Arbitrary<IntegrationPoint> integrationPoints() {
        return Arbitraries.of(
            IntegrationPoint.REST_API,
            IntegrationPoint.JWT_AUTH,
            IntegrationPoint.DATABASE_SYNC,
            IntegrationPoint.CACHE_COORDINATION,
            IntegrationPoint.ERROR_HANDLING
        );
    }

    @Provide
    Arbitrary<String> layers() {
        return Arbitraries.of(
            "durion-positivity-backend",
            "durion-positivity",
            "durion-moqui-frontend"
        );
    }

    @Provide
    Arbitrary<String> projects() {
        return Arbitraries.of(
            "durion-positivity-backend",
            "durion-positivity",
            "durion-moqui-frontend"
        );
    }

    @Provide
    Arbitrary<String> diagnosticScenarios() {
        return Arbitraries.of(
            "api_contract_mismatch",
            "jwt_token_inconsistency",
            "data_sync_failure",
            "performance_bottleneck",
            "authentication_failure"
        );
    }

    private Set<String> generateStackCombinations(List<TechnologyStack> stacks) {
        return stacks.stream()
                .flatMap(stack1 -> stacks.stream()
                        .filter(stack2 -> !stack1.equals(stack2))
                        .map(stack2 -> stack1.name() + "-" + stack2.name()))
                .collect(Collectors.toSet());
    }
}
