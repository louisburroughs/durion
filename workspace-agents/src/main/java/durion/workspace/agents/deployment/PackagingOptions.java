package durion.workspace.agents.deployment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Packaging options for agents
 */
public class PackagingOptions {
    private final Set<String> targetEnvironments;
    private final boolean includeDebugInfo;
    private final boolean optimizeForSize;
    private final Map<String, Object> metadata;
    private final boolean compressPackage;
    private final boolean includeDependencies;
    private final boolean signPackage;
    private final String packageFormat;

    public PackagingOptions(Set<String> targetEnvironments, boolean includeDebugInfo,
            boolean optimizeForSize, Map<String, Object> metadata,
            boolean compressPackage, boolean includeDependencies,
            boolean signPackage, String packageFormat) {
        this.targetEnvironments = new HashSet<>(targetEnvironments);
        this.includeDebugInfo = includeDebugInfo;
        this.optimizeForSize = optimizeForSize;
        this.metadata = new HashMap<>(metadata);
        this.compressPackage = compressPackage;
        this.includeDependencies = includeDependencies;
        this.signPackage = signPackage;
        this.packageFormat = packageFormat;
    }

    public static PackagingOptions defaultOptions() {
        return new PackagingOptions(
                Set.of("development", "staging", "production"),
                false,
                true,
                new HashMap<>(),
                true,
                true,
                false,
                "jar");
    }

    public Set<String> getTargetEnvironments() {
        return new HashSet<>(targetEnvironments);
    }

    public boolean isIncludeDebugInfo() {
        return includeDebugInfo;
    }

    public boolean isOptimizeForSize() {
        return optimizeForSize;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public boolean isCompressPackage() {
        return compressPackage;
    }

    public boolean isIncludeDependencies() {
        return includeDependencies;
    }

    public boolean isSignPackage() {
        return signPackage;
    }

    public String getPackageFormat() {
        return packageFormat;
    }
}
