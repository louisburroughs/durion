package com.durion.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

import com.durion.agents.WorkspaceArchitectureAgent;
import com.durion.core.WorkspaceAgentRegistry;
import com.durion.interfaces.ApiContract;
import com.durion.interfaces.ArchitecturalDecision;
import com.durion.interfaces.DependencyConflict;
import com.durion.interfaces.JwtFormat;

/**
 * Property Test 3: Cross-project architectural consistency
 * 
 * Validates: Architectural decisions consistent across durion-positivity-backend, 
 * durion-positivity, durion-moqui-frontend
 * 
 * Invariants: 
 * - API contracts compatible across all projects
 * - JWT format identical across Spring Boot, durion-positivity, Moqui
 * - No dependency conflicts between Java 21, Java 11, Groovy
 * 
 * Requirements: REQ-WS-002
 */
public class ArchitecturalConsistencyPropertyTest {

    private WorkspaceArchitectureAgent agent = new WorkspaceArchitectureAgent(new WorkspaceAgentRegistry());

    @Property
    @Report(Reporting.GENERATED)
    void architecturalDecisionsAreConsistentAcrossAllProjects(
            @ForAll("architecturalDecisions") List<ArchitecturalDecision> decisions) {
        
        // Property: All architectural decisions must be consistent across projects
        var consistencyResult = agent.validateArchitecturalConsistency(decisions);
        
        assertThat(consistencyResult.isConsistent())
            .as("Architectural decisions must be consistent across durion-positivity-backend, durion-positivity, durion-moqui-frontend")
            .isTrue();
        
        assertThat(consistencyResult.getInconsistencies())
            .as("No architectural inconsistencies should exist")
            .isEmpty();
    }

    @Property
    @Report(Reporting.GENERATED)
    void apiContractsAreCompatibleAcrossAllLayers(
            @ForAll("apiContracts") List<ApiContract> contracts) {
        
        // Property: API contracts must be compatible across all integration layers
        var compatibilityResult = agent.validateApiContractCompatibility(contracts);
        
        assertThat(compatibilityResult.areAllCompatible())
            .as("API contracts must be compatible across Spring Boot → durion-positivity → Moqui flows")
            .isTrue();
        
        assertThat(compatibilityResult.getIncompatibilities())
            .as("No API contract incompatibilities should exist")
            .isEmpty();
    }

    @Property
    @Report(Reporting.GENERATED)
    void jwtFormatIsIdenticalAcrossAllProjects(
            @ForAll("jwtFormats") List<JwtFormat> formats) {
        
        // Property: JWT format must be identical across all projects
        var jwtConsistencyResult = agent.validateJwtFormatConsistency(formats);
        
        assertThat(jwtConsistencyResult.isConsistent())
            .as("JWT format must be identical across Spring Boot, durion-positivity, Moqui")
            .isTrue();
        
        assertThat(jwtConsistencyResult.getFormatDifferences())
            .as("No JWT format differences should exist")
            .isEmpty();
    }

    @Property
    @Report(Reporting.GENERATED)
    void noDependencyConflictsExistBetweenTechnologyStacks(
            @ForAll("dependencyConflicts") List<DependencyConflict> conflicts) {
        
        // Property: No dependency conflicts between Java 21, Java 11, Groovy
        var conflictResult = agent.validateDependencyConflicts(conflicts);
        
        assertThat(conflictResult.hasConflicts())
            .as("No dependency conflicts should exist between Java 21, Java 11, Groovy")
            .isFalse();
        
        assertThat(conflictResult.getConflicts())
            .as("Conflict list should be empty")
            .isEmpty();
    }

    @Property
    @Report(Reporting.GENERATED)
    void integrationPatternsAreConsistentAcrossAllTechnologyCombinations(
            @ForAll("integrationPatterns") List<String> patterns) {
        
        // Property: Integration patterns must be consistent across all technology combinations
        var patternResult = agent.validateIntegrationPatternConsistency(patterns);
        
        assertThat(patternResult.areConsistent())
            .as("Integration patterns must be consistent across all technology combinations")
            .isTrue();
        
        assertThat(patternResult.getInconsistentPatterns())
            .as("No inconsistent integration patterns should exist")
            .isEmpty();
    }

    // Generators for test data

    @Provide
    Arbitrary<List<ArchitecturalDecision>> architecturalDecisions() {
        return Arbitraries.of(
            new ArchitecturalDecision("spring-boot", "REST_API", "durion-positivity-backend"),
            new ArchitecturalDecision("durion-positivity", "GROOVY_SERVICE", "durion-positivity"),
            new ArchitecturalDecision("moqui", "XML_SCREEN", "durion-moqui-frontend"),
            new ArchitecturalDecision("vue", "COMPONENT", "durion-moqui-frontend")
        ).list().ofMinSize(2).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<ApiContract>> apiContracts() {
        return Arbitraries.of(
            new ApiContract("durion-positivity-backend", "/api/v1/pos", "POST", "application/json"),
            new ApiContract("durion-positivity", "PosService.createTransaction", "GROOVY", "Map"),
            new ApiContract("moqui", "pos#CreateTransaction", "XML", "entity-auto")
        ).list().ofMinSize(2).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<JwtFormat>> jwtFormats() {
        return Arbitraries.of(
            new JwtFormat("durion-positivity-backend", "HS256", Set.of("sub", "iat", "exp", "roles")),
            new JwtFormat("durion-positivity", "HS256", Set.of("sub", "iat", "exp", "roles")),
            new JwtFormat("moqui", "HS256", Set.of("sub", "iat", "exp", "roles"))
        ).list().ofMinSize(2).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<DependencyConflict>> dependencyConflicts() {
        return Arbitraries.of(
            new DependencyConflict("jackson", "2.15.0", "2.14.0", false),
            new DependencyConflict("slf4j", "2.0.0", "1.7.36", false),
            new DependencyConflict("groovy", "4.0.0", "3.0.9", false)
        ).list().ofMinSize(1).ofMaxSize(6);
    }

    @Provide
    Arbitrary<List<String>> integrationPatterns() {
        return Arbitraries.of(
            "SPRING_BOOT_TO_DURION_POSITIVITY",
            "DURION_POSITIVITY_TO_MOQUI",
            "MOQUI_TO_VUE_JS",
            "JWT_AUTHENTICATION_FLOW",
            "ERROR_HANDLING_PATTERN"
        ).list().ofMinSize(2).ofMaxSize(8);
    }
}
