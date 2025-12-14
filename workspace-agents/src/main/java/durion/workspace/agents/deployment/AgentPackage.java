package durion.workspace.agents.deployment;

import durion.workspace.agents.core.AgentCapability;
import durion.workspace.agents.core.AgentType;

import java.time.Instant;
import java.util.*;

/**
 * Represents a packaged workspace agent ready for distribution and deployment
 */
public class AgentPackage {
    
    private final String packageId;
    private final String agentId;
    private final String version;
    private final AgentType agentType;
    private final Set<AgentCapability> capabilities;
    private final List<String> dependencies;
    private final Set<String> targetEnvironments;
    private final Instant packagedAt;
    private final Map<String, Object> metadata;
    
    public AgentPackage(String packageId, String agentId, String version, AgentType agentType,
                       Set<AgentCapability> capabilities, List<String> dependencies,
                       Set<String> targetEnvironments, Instant packagedAt) {
        this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
        this.agentId = Objects.requireNonNull(agentId, "Agent ID cannot be null");
        this.version = Objects.requireNonNull(version, "Version cannot be null");
        this.agentType = Objects.requireNonNull(agentType, "Agent type cannot be null");
        this.capabilities = new HashSet<>(Objects.requireNonNull(capabilities, "Capabilities cannot be null"));
        this.dependencies = new ArrayList<>(Objects.requireNonNull(dependencies, "Dependencies cannot be null"));
        this.targetEnvironments = new HashSet<>(Objects.requireNonNull(targetEnvironments, "Target environments cannot be null"));
        this.packagedAt = Objects.requireNonNull(packagedAt, "Packaged time cannot be null");
        this.metadata = new HashMap<>();
        
        initializeMetadata();
    }
    
    public String getPackageId() {
        return packageId;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public AgentType getAgentType() {
        return agentType;
    }
    
    public Set<AgentCapability> getCapabilities() {
        return new HashSet<>(capabilities);
    }
    
    public List<String> getDependencies() {
        return new ArrayList<>(dependencies);
    }
    
    public Set<String> getTargetEnvironments() {
        return new HashSet<>(targetEnvironments);
    }
    
    public Instant getPackagedAt() {
        return packagedAt;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Checks if package is compatible with a specific environment
     */
    public boolean isCompatibleWith(String environment) {
        return targetEnvironments.isEmpty() || targetEnvironments.contains(environment);
    }
    
    /**
     * Checks if package has a specific capability
     */
    public boolean hasCapability(AgentCapability capability) {
        return capabilities.contains(capability);
    }
    
    /**
     * Checks if package has all required capabilities
     */
    public boolean hasAllCapabilities(Set<AgentCapability> requiredCapabilities) {
        return capabilities.containsAll(requiredCapabilities);
    }
    
    /**
     * Gets package size in bytes (simulated)
     */
    public long getPackageSize() {
        return (Long) metadata.getOrDefault("package.size", 1024L * 1024L); // Default 1MB
    }
    
    /**
     * Gets package checksum for integrity verification
     */
    public String getChecksum() {
        return (String) metadata.getOrDefault("package.checksum", generateChecksum());
    }
    
    /**
     * Gets package description
     */
    public String getDescription() {
        return (String) metadata.getOrDefault("package.description", 
            "Workspace agent package for " + agentId);
    }
    
    /**
     * Gets minimum system requirements
     */
    public SystemRequirements getSystemRequirements() {
        return new SystemRequirements(
            (Integer) metadata.getOrDefault("requirements.cpu.cores", 1),
            (Long) metadata.getOrDefault("requirements.memory.mb", 512L),
            (Long) metadata.getOrDefault("requirements.disk.mb", 100L)
        );
    }
    
    /**
     * Validates package integrity
     */
    public ValidationResult validate() {
        // Check required fields
        if (packageId == null || packageId.trim().isEmpty()) {
            return ValidationResult.invalid("Package ID is required");
        }
        
        if (agentId == null || agentId.trim().isEmpty()) {
            return ValidationResult.invalid("Agent ID is required");
        }
        
        if (version == null || version.trim().isEmpty()) {
            return ValidationResult.invalid("Version is required");
        }
        
        // Validate version format (simple semantic versioning)
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) {
            return ValidationResult.invalid("Invalid version format: " + version);
        }
        
        // Check capabilities
        if (capabilities.isEmpty()) {
            return ValidationResult.invalid("Package must have at least one capability");
        }
        
        // Validate dependencies (no circular dependencies)
        if (dependencies.contains(agentId)) {
            return ValidationResult.invalid("Agent cannot depend on itself");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Creates a deployment manifest for this package
     */
    public DeploymentManifest createDeploymentManifest() {
        return new DeploymentManifest(
            packageId,
            agentId,
            version,
            agentType,
            capabilities,
            dependencies,
            targetEnvironments,
            getSystemRequirements(),
            getChecksum()
        );
    }
    
    /**
     * Initializes package metadata
     */
    private void initializeMetadata() {
        metadata.put("package.size", calculatePackageSize());
        metadata.put("package.checksum", generateChecksum());
        metadata.put("package.description", "Workspace agent package for " + agentId);
        metadata.put("package.format.version", "1.0");
        
        // System requirements based on agent type
        switch (agentType) {
            case WORKSPACE_COORDINATION:
                metadata.put("requirements.cpu.cores", 2);
                metadata.put("requirements.memory.mb", 1024L);
                metadata.put("requirements.disk.mb", 200L);
                break;
                
            case TECHNOLOGY_BRIDGE:
                metadata.put("requirements.cpu.cores", 1);
                metadata.put("requirements.memory.mb", 512L);
                metadata.put("requirements.disk.mb", 100L);
                break;
                
            case OPERATIONAL_COORDINATION:
                metadata.put("requirements.cpu.cores", 2);
                metadata.put("requirements.memory.mb", 2048L);
                metadata.put("requirements.disk.mb", 500L);
                break;
                
            case GOVERNANCE_COMPLIANCE:
                metadata.put("requirements.cpu.cores", 1);
                metadata.put("requirements.memory.mb", 512L);
                metadata.put("requirements.disk.mb", 100L);
                break;
                
            default:
                metadata.put("requirements.cpu.cores", 1);
                metadata.put("requirements.memory.mb", 512L);
                metadata.put("requirements.disk.mb", 100L);
        }
    }
    
    /**
     * Calculates package size based on agent complexity
     */
    private long calculatePackageSize() {
        long baseSize = 1024L * 1024L; // 1MB base
        
        // Add size based on capabilities
        baseSize += capabilities.size() * 100L * 1024L; // 100KB per capability
        
        // Add size based on dependencies
        baseSize += dependencies.size() * 50L * 1024L; // 50KB per dependency
        
        return baseSize;
    }
    
    /**
     * Generates a checksum for package integrity
     */
    private String generateChecksum() {
        // Simple checksum based on package contents
        int hash = Objects.hash(packageId, agentId, version, agentType, capabilities, dependencies);
        return String.format("%08x", hash);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        AgentPackage that = (AgentPackage) obj;
        return Objects.equals(packageId, that.packageId) &&
               Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(packageId, version);
    }
    
    @Override
    public String toString() {
        return String.format("AgentPackage{id='%s', agent='%s', version='%s', type=%s, capabilities=%d}",
                           packageId, agentId, version, agentType, capabilities.size());
    }
    
    // Inner classes
    
    public static class SystemRequirements {
        private final int cpuCores;
        private final long memoryMB;
        private final long diskMB;
        
        public SystemRequirements(int cpuCores, long memoryMB, long diskMB) {
            this.cpuCores = cpuCores;
            this.memoryMB = memoryMB;
            this.diskMB = diskMB;
        }
        
        public int getCpuCores() { return cpuCores; }
        public long getMemoryMB() { return memoryMB; }
        public long getDiskMB() { return diskMB; }
        
        public boolean isSatisfiedBy(SystemRequirements available) {
            return available.cpuCores >= this.cpuCores &&
                   available.memoryMB >= this.memoryMB &&
                   available.diskMB >= this.diskMB;
        }
        
        @Override
        public String toString() {
            return String.format("SystemReq{cpu=%d, memory=%dMB, disk=%dMB}", 
                               cpuCores, memoryMB, diskMB);
        }
    }
    
    public static class DeploymentManifest {
        private final String packageId;
        private final String agentId;
        private final String version;
        private final AgentType agentType;
        private final Set<AgentCapability> capabilities;
        private final List<String> dependencies;
        private final Set<String> targetEnvironments;
        private final SystemRequirements systemRequirements;
        private final String checksum;
        
        public DeploymentManifest(String packageId, String agentId, String version, AgentType agentType,
                                Set<AgentCapability> capabilities, List<String> dependencies,
                                Set<String> targetEnvironments, SystemRequirements systemRequirements,
                                String checksum) {
            this.packageId = packageId;
            this.agentId = agentId;
            this.version = version;
            this.agentType = agentType;
            this.capabilities = new HashSet<>(capabilities);
            this.dependencies = new ArrayList<>(dependencies);
            this.targetEnvironments = new HashSet<>(targetEnvironments);
            this.systemRequirements = systemRequirements;
            this.checksum = checksum;
        }
        
        // Getters
        public String getPackageId() { return packageId; }
        public String getAgentId() { return agentId; }
        public String getVersion() { return version; }
        public AgentType getAgentType() { return agentType; }
        public Set<AgentCapability> getCapabilities() { return new HashSet<>(capabilities); }
        public List<String> getDependencies() { return new ArrayList<>(dependencies); }
        public Set<String> getTargetEnvironments() { return new HashSet<>(targetEnvironments); }
        public SystemRequirements getSystemRequirements() { return systemRequirements; }
        public String getChecksum() { return checksum; }
        
        @Override
        public String toString() {
            return String.format("DeploymentManifest{package='%s', agent='%s', version='%s'}",
                               packageId, agentId, version);
        }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}