package durion.workspace.agents.registry;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents version information for a workspace agent
 */
public class AgentVersion {
    
    private final String version;
    private final Instant registrationTime;
    private final String buildInfo;
    private final String commitHash;
    
    public AgentVersion(String version, Instant registrationTime) {
        this(version, registrationTime, null, null);
    }
    
    public AgentVersion(String version, Instant registrationTime, String buildInfo, String commitHash) {
        this.version = Objects.requireNonNull(version, "Version cannot be null");
        this.registrationTime = Objects.requireNonNull(registrationTime, "Registration time cannot be null");
        this.buildInfo = buildInfo;
        this.commitHash = commitHash;
    }
    
    public String getVersion() {
        return version;
    }
    
    public Instant getRegistrationTime() {
        return registrationTime;
    }
    
    public String getBuildInfo() {
        return buildInfo;
    }
    
    public String getCommitHash() {
        return commitHash;
    }
    
    /**
     * Checks if this version is compatible with another version
     */
    public boolean isCompatibleWith(AgentVersion other) {
        if (other == null) return false;
        
        // Simple semantic versioning compatibility
        String[] thisParts = this.version.split("\\.");
        String[] otherParts = other.version.split("\\.");
        
        if (thisParts.length > 0 && otherParts.length > 0) {
            // Major versions must match for compatibility
            return thisParts[0].equals(otherParts[0]);
        }
        
        return this.version.equals(other.version);
    }
    
    /**
     * Compares versions for ordering (newer versions are greater)
     */
    public int compareTo(AgentVersion other) {
        if (other == null) return 1;
        
        String[] thisParts = this.version.split("\\.");
        String[] otherParts = other.version.split("\\.");
        
        int maxLength = Math.max(thisParts.length, otherParts.length);
        
        for (int i = 0; i < maxLength; i++) {
            int thisNum = i < thisParts.length ? parseVersionPart(thisParts[i]) : 0;
            int otherNum = i < otherParts.length ? parseVersionPart(otherParts[i]) : 0;
            
            if (thisNum != otherNum) {
                return Integer.compare(thisNum, otherNum);
            }
        }
        
        return 0;
    }
    
    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        AgentVersion that = (AgentVersion) obj;
        return Objects.equals(version, that.version) &&
               Objects.equals(registrationTime, that.registrationTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(version, registrationTime);
    }
    
    @Override
    public String toString() {
        return String.format("AgentVersion{version='%s', registrationTime=%s}", 
                           version, registrationTime);
    }
}