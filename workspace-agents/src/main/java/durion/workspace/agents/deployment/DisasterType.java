package durion.workspace.agents.deployment;

/**
 * Types of disasters that can occur
 */
public enum DisasterType {
    AGENT_FAILURE,              // Single agent failure
    INFRASTRUCTURE_FAILURE,     // Infrastructure/cluster failure
    DATA_CORRUPTION,           // Data corruption event
    NETWORK_PARTITION,         // Network partition or connectivity loss
    SECURITY_BREACH,           // Security breach event
    RESOURCE_EXHAUSTION        // Resource exhaustion (CPU, memory, disk)
}
