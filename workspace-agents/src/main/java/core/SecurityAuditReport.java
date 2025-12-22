package core;

public class SecurityAuditReport {
    private final double vulnerabilityDetectionAccuracy;

    public SecurityAuditReport(double vulnerabilityDetectionAccuracy) {
        this.vulnerabilityDetectionAccuracy = vulnerabilityDetectionAccuracy;
    }

    public double getVulnerabilityDetectionAccuracy() {
        return vulnerabilityDetectionAccuracy;
    }
}
