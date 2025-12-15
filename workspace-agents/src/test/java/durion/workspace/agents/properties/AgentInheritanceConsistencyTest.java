package durion.workspace.agents.properties;

import durion.workspace.agents.core.AbstractWorkspaceAgent;
import durion.workspace.agents.core.AgentCapability;
import durion.workspace.agents.core.AgentType;
import durion.workspace.agents.coordination.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.junit.jupiter.api.Tag;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * **Feature: workspace-agents-structure-fix, Property 5: Agent inheritance
 * consistency**
 * **Validates: Requirements 4.1**
 * 
 * Property-based test to verify that all agent classes in the system extend
 * AbstractWorkspaceAgent
 * and follow the correct inheritance pattern.
 */
@Tag("property-test")
public class AgentInheritanceConsistencyTest {

    /**
     * Property 5: Agent inheritance consistency
     * For any agent class in the system, it should extend AbstractWorkspaceAgent
     * and follow the correct inheritance pattern
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentInheritanceConsistency(@ForAll("agentClasses") Class<?> agentClass) {
        // Verify the agent class extends AbstractWorkspaceAgent
        boolean extendsAbstractWorkspaceAgent = AbstractWorkspaceAgent.class.isAssignableFrom(agentClass)
                && !agentClass.equals(AbstractWorkspaceAgent.class);

        if (extendsAbstractWorkspaceAgent) {
            // Verify the class is not abstract (concrete agents should be instantiable)
            boolean isConcreteClass = !Modifier.isAbstract(agentClass.getModifiers());

            // Verify the class has proper constructor pattern
            boolean hasProperConstructor = hasValidConstructorPattern(agentClass);

            // Verify the class implements required abstract methods
            boolean implementsRequiredMethods = implementsRequiredAbstractMethods(agentClass);

            // All inheritance requirements must be met
            assert isConcreteClass : "Agent class " + agentClass.getSimpleName() + " should be concrete (not abstract)";
            assert hasProperConstructor
                    : "Agent class " + agentClass.getSimpleName() + " should have proper constructor pattern";
            assert implementsRequiredMethods
                    : "Agent class " + agentClass.getSimpleName() + " should implement all required abstract methods";
        }

        // The main assertion: all agent classes must extend AbstractWorkspaceAgent
        assert extendsAbstractWorkspaceAgent
                : "Agent class " + agentClass.getSimpleName() + " must extend AbstractWorkspaceAgent";
    }

    /**
     * Provides all agent classes found in the coordination package
     */
    @Provide
    Arbitrary<Class<?>> agentClasses() {
        List<Class<?>> agentClasses = Arrays.asList(
                DataGovernanceAgent.class,
                CrossProjectTestingAgent.class,
                DisasterRecoveryAgent.class,
                DocumentationCoordinationAgent.class,
                PerformanceCoordinationAgent.class,
                WorkflowCoordinationAgent.class,
                WorkspaceFeatureDevelopmentAgent.class,
                WorkspaceReleaseCoordinationAgent.class);

        return Arbitraries.of(agentClasses);
    }

    /**
     * Checks if the agent class has a valid constructor pattern
     */
    private boolean hasValidConstructorPattern(Class<?> agentClass) {
        try {
            // Check for default constructor (most agents use this pattern)
            return agentClass.getDeclaredConstructor() != null;
        } catch (NoSuchMethodException e) {
            // If no default constructor, check for constructor with standard parameters
            try {
                // Check for constructor with String, AgentType, Set<AgentCapability>
                return agentClass.getDeclaredConstructor(String.class, AgentType.class, Set.class) != null;
            } catch (NoSuchMethodException e2) {
                return false;
            }
        }
    }

    /**
     * Checks if the agent class implements all required abstract methods from
     * AbstractWorkspaceAgent
     */
    private boolean implementsRequiredAbstractMethods(Class<?> agentClass) {
        try {
            // Check for doProcessRequest method
            agentClass.getDeclaredMethod("doProcessRequest",
                    durion.workspace.agents.core.AgentRequest.class);

            // Check for isPrimaryCapability method
            agentClass.getDeclaredMethod("isPrimaryCapability", AgentCapability.class);

            // Check for getDefaultCoordinationDependencies method
            agentClass.getDeclaredMethod("getDefaultCoordinationDependencies");

            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Additional property to verify agent class naming conventions
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentNamingConventions(@ForAll("agentClasses") Class<?> agentClass) {
        String className = agentClass.getSimpleName();

        // Agent classes should end with "Agent"
        assert className.endsWith("Agent") : "Agent class " + className + " should end with 'Agent'";

        // Agent classes should be in the coordination package (for coordination agents)
        String packageName = agentClass.getPackage().getName();
        boolean isInCoordinationPackage = packageName.contains("coordination");

        if (isInCoordinationPackage) {
            assert packageName.equals("durion.workspace.agents.coordination")
                    : "Coordination agent " + className + " should be in durion.workspace.agents.coordination package";
        }
    }

    /**
     * Property to verify agent type consistency with package structure
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentPackageTypeConsistency(@ForAll("agentClasses") Class<?> agentClass) {
        String packageName = agentClass.getPackage().getName();

        // Coordination agents should be in coordination package
        if (packageName.contains("coordination")) {
            assert packageName.equals("durion.workspace.agents.coordination") : "Agent " + agentClass.getSimpleName()
                    + " in coordination package should have correct package path";
        }

        // Registry agents should be in registry package
        if (packageName.contains("registry")) {
            assert packageName.equals("durion.workspace.agents.registry")
                    : "Agent " + agentClass.getSimpleName() + " in registry package should have correct package path";
        }

        // Monitoring agents should be in monitoring package
        if (packageName.contains("monitoring")) {
            assert packageName.equals("durion.workspace.agents.monitoring")
                    : "Agent " + agentClass.getSimpleName() + " in monitoring package should have correct package path";
        }
    }
}