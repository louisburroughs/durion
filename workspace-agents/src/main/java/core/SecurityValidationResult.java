package core;

import java.util.*;

public class SecurityValidationResult {
    private final boolean valid;
    private final double accuracy;
    private final double detectionRate;
    private final int privilegeEscalationCount;

    public SecurityValidationResult(boolean valid, double accuracy, double detectionRate, int privilegeEscalationCount) {
        this.valid = valid;
        this.accuracy = accuracy;
        this.detectionRate = detectionRate;
        this.privilegeEscalationCount = privilegeEscalationCount;
    }

    public boolean isValid() { return valid; }
    public double getAccuracy() { return accuracy; }
    public double getDetectionRate() { return detectionRate; }
    public int getPrivilegeEscalationCount() { return privilegeEscalationCount; }
}
