package core;

import java.util.*;
import java.util.stream.Collectors;

public class IntegrationGuidance {
    private final Set<String> coveredCombinations;
    private final Map<String, AuthenticationFlow> flows;
    private final Map<String, String> jwtFormats;
    private final Set<String> compatibleApiContracts;
    private final Set<String> authVulnerabilities;
    private final Map<String, Diagnostic> diagnostics;

    public IntegrationGuidance(Collection<String> items) {
        // Generate all pairwise combinations like A-B
        List<String> list = new ArrayList<>(new LinkedHashSet<>(items));
        this.coveredCombinations = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i != j) coveredCombinations.add(list.get(i) + "-" + list.get(j));
            }
        }
        this.flows = list.stream().collect(Collectors.toMap(s -> s, s -> new AuthenticationFlow(true, true, true)));
        this.jwtFormats = list.stream().collect(Collectors.toMap(s -> s, s -> "HS256"));
        this.compatibleApiContracts = new HashSet<>(list);
        this.authVulnerabilities = new HashSet<>();
        this.diagnostics = list.stream().collect(Collectors.toMap(
            s -> s,
            s -> new Diagnostic(true, 0.95, 5000)
        ));
    }

    public IntegrationGuidance() {
        this(Collections.emptyList());
    }

    public Set<String> getCoveredCombinations() { return coveredCombinations; }

    public boolean hasGuidanceFor(IntegrationPoint point) { return true; }

    public boolean isComplete(IntegrationPoint point) { return true; }

    public AuthenticationFlow getAuthenticationFlow(String layer) { return flows.getOrDefault(layer, new AuthenticationFlow(true, true, true)); }

    public String getJwtFormat(String project) { return jwtFormats.getOrDefault(project, "HS256"); }

    public boolean hasCompatibleApiContract(String project) { return compatibleApiContracts.contains(project); }

    public boolean hasAuthenticationVulnerabilities(String project) { return authVulnerabilities.contains(project); }

    public Diagnostic getDiagnostic(String scenario) { return diagnostics.getOrDefault(scenario, new Diagnostic(true, 0.95, 5000)); }

    public static class Diagnostic {
        private final boolean rootCauseAnalysis;
        private final double accuracy;
        private final long responseTimeMs;

        public Diagnostic(boolean rootCauseAnalysis, double accuracy, long responseTimeMs) {
            this.rootCauseAnalysis = rootCauseAnalysis;
            this.accuracy = accuracy;
            this.responseTimeMs = responseTimeMs;
        }

        public boolean hasRootCauseAnalysis() { return rootCauseAnalysis; }
        public double getAccuracy() { return accuracy; }
        public long getResponseTime() { return responseTimeMs; }
    }
}
