package com.durion.audit;

/**
 * Enumeration of repository types for implementation issues.
 */
public enum RepositoryType {
    FRONTEND("frontend", "louisburroughs/durion-moqui-frontend", "[FRONTEND] [STORY]"),
    BACKEND("backend", "louisburroughs/durion-positivity-backend", "[BACKEND] [STORY]");

    private final String type;
    private final String repository;
    private final String titlePrefix;

    RepositoryType(String type, String repository, String titlePrefix) {
        this.type = type;
        this.repository = repository;
        this.titlePrefix = titlePrefix;
    }

    public String getType() {
        return type;
    }

    public String getRepository() {
        return repository;
    }

    public String getTitlePrefix() {
        return titlePrefix;
    }

    public String formatTitle(String originalTitle) {
        return titlePrefix + " " + originalTitle;
    }

    public static RepositoryType fromString(String type) {
        for (RepositoryType repoType : values()) {
            if (repoType.type.equalsIgnoreCase(type)) {
                return repoType;
            }
        }
        throw new IllegalArgumentException("Unknown repository type: " + type);
    }
}