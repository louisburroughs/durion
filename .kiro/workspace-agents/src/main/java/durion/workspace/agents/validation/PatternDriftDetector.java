package durion.workspace.agents.validation;

import durion.workspace.agents.core.AgentResponse;

import java.util.List;
import java.util.ArrayList;

/**
 * Detects pattern drift in agent responses over time
 */
public class PatternDriftDetector {
    
    /**
     * Detects pattern drift by comparing current response with historical responses
     */
    public PatternDriftResult detectDrift(AgentResponse currentResponse, List<AgentResponse> historicalResponses) {
        List<PatternDrift> detectedDrifts = new ArrayList<>();
        
        if (historicalResponses.isEmpty()) {
            return new PatternDriftResult(detectedDrifts);
        }
        
        // Check for architectural pattern drift
        detectedDrifts.addAll(detectArchitecturalDrift(currentResponse, historicalResponses));
        
        // Check for security pattern drift
        detectedDrifts.addAll(detectSecurityDrift(currentResponse, historicalResponses));
        
        // Check for technology pattern drift
        detectedDrifts.addAll(detectTechnologyDrift(currentResponse, historicalResponses));
        
        return new PatternDriftResult(detectedDrifts);
    }
    
    /**
     * Detects architectural pattern drift
     */
    private List<PatternDrift> detectArchitecturalDrift(AgentResponse current, List<AgentResponse> historical) {
        List<PatternDrift> drifts = new ArrayList<>();
        
        String currentGuidance = current.getGuidance().toLowerCase();
        
        // Check if current response introduces new architectural patterns
        boolean currentHasMicroservices = currentGuidance.contains("microservice");
        boolean currentHasMonolith = currentGuidance.contains("monolith");
        
        // Check historical patterns
        boolean historicalHasMicroservices = historical.stream()
            .anyMatch(r -> r.getGuidance().toLowerCase().contains("microservice"));
        boolean historicalHasMonolith = historical.stream()
            .anyMatch(r -> r.getGuidance().toLowerCase().contains("monolith"));
        
        // Detect drift
        if (currentHasMicroservices && historicalHasMonolith && !historicalHasMicroservices) {
            drifts.add(new PatternDrift(
                PatternDrift.DriftType.ARCHITECTURAL_SHIFT,
                PatternDrift.DriftSeverity.HIGH,
                "Shift from monolithic to microservices architecture detected"
            ));
        } else if (currentHasMonolith && historicalHasMicroservices && !historicalHasMonolith) {
            drifts.add(new PatternDrift(
                PatternDrift.DriftType.ARCHITECTURAL_SHIFT,
                PatternDrift.DriftSeverity.HIGH,
                "Shift from microservices to monolithic architecture detected"
            ));
        }
        
        return drifts;
    }
    
    /**
     * Detects security pattern drift
     */
    private List<PatternDrift> detectSecurityDrift(AgentResponse current, List<AgentResponse> historical) {
        List<PatternDrift> drifts = new ArrayList<>();
        
        String currentText = (current.getGuidance() + " " + String.join(" ", current.getRecommendations())).toLowerCase();
        
        // Check current security methods
        boolean currentHasJWT = currentText.contains("jwt");
        boolean currentHasSession = currentText.contains("session");
        
        // Check historical security methods
        boolean historicalHasJWT = historical.stream()
            .anyMatch(r -> (r.getGuidance() + " " + String.join(" ", r.getRecommendations())).toLowerCase().contains("jwt"));
        boolean historicalHasSession = historical.stream()
            .anyMatch(r -> (r.getGuidance() + " " + String.join(" ", r.getRecommendations())).toLowerCase().contains("session"));
        
        // Detect security method drift
        if (currentHasSession && historicalHasJWT && !historicalHasSession) {
            drifts.add(new PatternDrift(
                PatternDrift.DriftType.SECURITY_METHOD,
                PatternDrift.DriftSeverity.MEDIUM,
                "Shift from JWT to session-based authentication detected"
            ));
        } else if (currentHasJWT && historicalHasSession && !historicalHasJWT) {
            drifts.add(new PatternDrift(
                PatternDrift.DriftType.SECURITY_METHOD,
                PatternDrift.DriftSeverity.LOW,
                "Shift from session to JWT authentication detected (positive change)"
            ));
        }
        
        return drifts;
    }
    
    /**
     * Detects technology pattern drift
     */
    private List<PatternDrift> detectTechnologyDrift(AgentResponse current, List<AgentResponse> historical) {
        List<PatternDrift> drifts = new ArrayList<>();
        
        String currentGuidance = current.getGuidance().toLowerCase();
        
        // Check database technology drift
        boolean currentHasPostgreSQL = currentGuidance.contains("postgresql");
        boolean currentHasMySQL = currentGuidance.contains("mysql");
        
        boolean historicalHasPostgreSQL = historical.stream()
            .anyMatch(r -> r.getGuidance().toLowerCase().contains("postgresql"));
        boolean historicalHasMySQL = historical.stream()
            .anyMatch(r -> r.getGuidance().toLowerCase().contains("mysql"));
        
        if (currentHasMySQL && historicalHasPostgreSQL && !historicalHasMySQL) {
            drifts.add(new PatternDrift(
                PatternDrift.DriftType.TECHNOLOGY_CHANGE,
                PatternDrift.DriftSeverity.MEDIUM,
                "Database technology drift from PostgreSQL to MySQL detected"
            ));
        }
        
        return drifts;
    }
}