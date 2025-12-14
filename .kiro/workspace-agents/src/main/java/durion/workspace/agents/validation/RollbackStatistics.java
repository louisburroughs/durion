package durion.workspace.agents.validation;

import java.util.Collection;

/**
 * Statistics for rollback operations
 */
public class RollbackStatistics {
    
    private final int totalRollbacks;
    
    public RollbackStatistics(Collection<?> rollbackEntries) {
        this.totalRollbacks = rollbackEntries.size();
    }
    
    public int getTotalRollbacks() {
        return totalRollbacks;
    }
    
    @Override
    public String toString() {
        return String.format("RollbackStatistics{totalRollbacks=%d}", totalRollbacks);
    }
}