package properties;

import agents.RequirementsDecompositionAgent;
import core.AgentResult;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Requirements Decomposition Agent
 * 
 * Property 1: Requirements decomposition completeness
 * Validates: All requirements produce frontend + backend specs with API contracts
 * Invariants: No business logic in frontend, complete integration point definition
 */
public class RequirementsDecompositionPropertyTest {

    private final RequirementsDecompositionAgent agent = new RequirementsDecompositionAgent();

    @Property
    @Report(Reporting.GENERATED)
    void requirementsDecompositionCompleteness(@ForAll("businessRequirements") String requirement) {
        // Execute decomposition
        AgentResult result = agent.decomposeRequirements(requirement);
        
        // Property 1: Completeness - All requirements produce both frontend and backend specs
        assertNotNull(result, "Decomposition result must not be null");
        assertTrue(result.isSuccess(), "Decomposition must succeed");
        
        RequirementsDecompositionAgent.DecompositionResult decomp = 
            (RequirementsDecompositionAgent.DecompositionResult) result.getData();
        
        assertNotNull(decomp.frontendWork(), "Frontend work must be generated");
        assertNotNull(decomp.backendWork(), "Backend work must be generated");
        assertNotNull(decomp.apiContracts(), "API contracts must be generated");
        
        // Invariant: No business logic in frontend
        RequirementsDecompositionAgent.FrontendWork frontend = decomp.frontendWork();
        for (String component : frontend.components()) {
            assertFalse(component.toLowerCase().contains("business logic"), 
                       "Frontend components must not contain business logic: " + component);
        }
        
        // Invariant: Complete integration point definition
        if (!decomp.apiContracts().isEmpty()) {
            for (RequirementsDecompositionAgent.ApiContract contract : decomp.apiContracts()) {
                assertNotNull(contract.endpoint(), "API contract must define endpoint");
                assertNotNull(contract.method(), "API contract must define method");
                assertNotNull(contract.schema(), "API contract must define schema");
            }
        }
        
        // Invariant: Backend contains business logic when required
        RequirementsDecompositionAgent.BackendWork backend = decomp.backendWork();
        if (requirement.toLowerCase().contains("calculate") || 
            requirement.toLowerCase().contains("process") ||
            requirement.toLowerCase().contains("validate")) {
            assertFalse(backend.businessLogic().isEmpty() && backend.apis().isEmpty(),
                      "Backend must handle business logic operations");
        }
    }

    @Property
    void architecturalBoundaryEnforcement(@ForAll("businessRequirements") String requirement) {
        AgentResult result = agent.decomposeRequirements(requirement);
        
        assertTrue(result.isSuccess(), "Decomposition must succeed");
        RequirementsDecompositionAgent.DecompositionResult decomp = 
            (RequirementsDecompositionAgent.DecompositionResult) result.getData();
        
        // Validate architectural boundaries
        assertTrue(decomp.validation().isValid(), 
                  "Decomposition must respect architectural boundaries");
        
        // No UI logic in backend
        RequirementsDecompositionAgent.BackendWork backend = decomp.backendWork();
        for (String logic : backend.businessLogic()) {
            assertFalse(logic.toLowerCase().contains("screen"), 
                       "Backend must not contain screen logic: " + logic);
            assertFalse(logic.toLowerCase().contains("form"), 
                       "Backend must not contain form logic: " + logic);
            assertFalse(logic.toLowerCase().contains("vue"), 
                       "Backend must not contain Vue.js logic: " + logic);
        }
    }

    @Property
    void integrationPointCompleteness(@ForAll("businessRequirements") String requirement) {
        AgentResult result = agent.decomposeRequirements(requirement);
        
        assertTrue(result.isSuccess(), "Decomposition must succeed");
        RequirementsDecompositionAgent.DecompositionResult decomp = 
            (RequirementsDecompositionAgent.DecompositionResult) result.getData();
        
        // If both frontend and backend work exists, API contracts must exist
        boolean hasFrontendWork = !decomp.frontendWork().components().isEmpty() || 
                                 !decomp.frontendWork().screens().isEmpty() ||
                                 !decomp.frontendWork().forms().isEmpty();
        boolean hasBackendWork = !decomp.backendWork().apis().isEmpty() ||
                                !decomp.backendWork().businessLogic().isEmpty();
        
        if (hasFrontendWork && hasBackendWork) {
            assertFalse(decomp.apiContracts().isEmpty(), 
                       "API contracts required when both frontend and backend work exists");
        }
    }

    @Provide
    Arbitrary<String> businessRequirements() {
        return Arbitraries.oneOf(
            // UI-focused requirements
            Arbitraries.of(
                "Create a user registration form with email validation",
                "Display product catalog with search and filtering",
                "Build dashboard showing sales metrics and charts",
                "Create order management screen with status updates"
            ),
            // Backend-focused requirements  
            Arbitraries.of(
                "Implement payment processing with fraud detection",
                "Create inventory management system with stock tracking",
                "Build user authentication with JWT tokens",
                "Implement order fulfillment workflow with notifications"
            ),
            // Full-stack requirements
            Arbitraries.of(
                "Create complete e-commerce checkout flow",
                "Build customer support ticket system",
                "Implement real-time chat with message history",
                "Create reporting system with data export"
            )
        );
    }

    @Test
    void testSpecificRequirementDecomposition() {
        String requirement = "Create a product management system where users can add, edit, and delete products with inventory tracking";
        
        AgentResult result = agent.decomposeRequirements(requirement);
        
        assertTrue(result.isSuccess(), "Decomposition must succeed");
        RequirementsDecompositionAgent.DecompositionResult decomp = 
            (RequirementsDecompositionAgent.DecompositionResult) result.getData();
        
        // Verify frontend contains UI elements
        RequirementsDecompositionAgent.FrontendWork frontend = decomp.frontendWork();
        boolean hasUIElements = !frontend.forms().isEmpty() || !frontend.screens().isEmpty();
        assertTrue(hasUIElements, "Frontend should contain UI elements");
        
        // Verify backend contains business logic
        RequirementsDecompositionAgent.BackendWork backend = decomp.backendWork();
        boolean hasBusinessLogic = !backend.apis().isEmpty() || !backend.businessLogic().isEmpty();
        assertTrue(hasBusinessLogic, "Backend should contain business logic");
        
        // Verify API contracts exist for integration
        assertFalse(decomp.apiContracts().isEmpty(),
                   "API contracts should be generated for full-stack requirement");
    }
}
