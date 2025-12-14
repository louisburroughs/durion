package durion.workspace.agents.deployment;

/**
 * Severity levels for disasters
 */
public enum DisasterSeverity {
    LOW(1),         // Minor impact, few agents affected
    MEDIUM(2),      // Moderate impact, several agents affected
    HIGH(3),        // Severe impact, many agents affected
    CRITICAL(4)     // Critical impact, most/all agents affected
    ;
    
    private final int level;
    
    DisasterSeverity(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
    
    public boolean isMoreSevereThan(DisasterSeverity other) {
        return this.level > other.level;
    }
}
