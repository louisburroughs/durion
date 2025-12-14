package durion.workspace.agents.deployment;

/**
 * Packaging options for agents
 */
public class PackagingOptions {
    private final boolean compressPackage;
    private final boolean includeDependencies;
    private final boolean signPackage;
    private final String packageFormat;

    public PackagingOptions(boolean compressPackage, boolean includeDependencies, boolean signPackage, String packageFormat) {
        this.compressPackage = compressPackage;
        this.includeDependencies = includeDependencies;
        this.signPackage = signPackage;
        this.packageFormat = packageFormat;
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
