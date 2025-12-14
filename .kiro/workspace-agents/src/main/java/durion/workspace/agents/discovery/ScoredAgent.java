package durion.workspace.agents.discovery;

import durion.workspace.agents.core.WorkspaceAgent;

/**
 * Represents an agent with its calculated suitability score
 */
public class ScoredAgent {
    
    private final WorkspaceAgent agent;
    private final int score;
    
    public ScoredAgent(WorkspaceAgent agent, int score) {
        this.agent = agent;
        this.score = Math.max(0, Math.min(100, score)); // Clamp to 0-100 range
    }
    
    public WorkspaceAgent getAgent() {
        return agent;
    }
    
    public int getScore() {
        return score;
    }
    
    /**
     * Gets the agent ID for convenience
     */
    public String getAgentId() {
        return agent.getAgentId();
    }
    
    /**
     * Checks if this agent has a high suitability score
     */
    public boolean isHighScore() {
        return score >= 80;
    }
    
    /**
     * Checks if this agent has a good suitability score
     */
    public boolean isGoodScore() {
        return score >= 60;
    }
    
    /**
     * Gets score category as string
     */
    public String getScoreCategory() {
        if (score >= 90) return "Excellent";
        if (score >= 80) return "Very Good";
        if (score >= 70) return "Good";
        if (score >= 60) return "Fair";
        if (score >= 40) return "Poor";
        return "Very Poor";
    }
    
    @Override
    public String toString() {
        return String.format("ScoredAgent{id='%s', score=%d (%s)}", 
                agent.getAgentId(), score, getScoreCategory());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ScoredAgent that = (ScoredAgent) obj;
        return agent.getAgentId().equals(that.agent.getAgentId());
    }
    
    @Override
    public int hashCode() {
        return agent.getAgentId().hashCode();
    }
}