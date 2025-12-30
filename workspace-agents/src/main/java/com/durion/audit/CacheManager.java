package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Cache manager for repository query result caching to avoid duplicate API
 * calls.
 * 
 * Implements file-based caching with expiration and validation logic to ensure
 * cache consistency across audit sessions.
 * 
 * Requirements: 5.3 - Repository query result caching
 */
public class CacheManager {

    private static final String CACHE_DIR = ".github/orchestration/audit-cache";
    private static final String FRONTEND_CACHE_PREFIX = "frontend-issues-";
    private static final String BACKEND_CACHE_PREFIX = "backend-issues-";
    private static final String CACHE_FILE_EXTENSION = ".json";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;
    private final Path cacheDirectory;
    private final int cacheExpirationHours;
    private final AuditLogger logger;

    /**
     * Creates a new cache manager with default settings.
     * 
     * @param logger Audit logger for tracking cache operations
     */
    public CacheManager(AuditLogger logger) {
        this(logger, 24); // Default 24-hour cache expiration
    }

    /**
     * Creates a new cache manager with custom expiration time.
     * 
     * @param logger               Audit logger for tracking cache operations
     * @param cacheExpirationHours Number of hours before cache expires
     */
    public CacheManager(AuditLogger logger, int cacheExpirationHours) {
        this(logger, cacheExpirationHours, null);
    }

    /**
     * Creates a new cache manager with custom expiration time and ObjectMapper.
     * 
     * @param logger               Audit logger for tracking cache operations
     * @param cacheExpirationHours Number of hours before cache expires
     * @param customObjectMapper   Custom ObjectMapper for serialization (null to
     *                             use default)
     */
    public CacheManager(AuditLogger logger, int cacheExpirationHours, ObjectMapper customObjectMapper) {
        this.logger = logger;
        this.cacheExpirationHours = cacheExpirationHours;

        if (customObjectMapper != null) {
            this.objectMapper = customObjectMapper;
        } else {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        this.cacheDirectory = Paths.get(CACHE_DIR);

        initializeCacheDirectory();
    }

    /**
     * Initializes the cache directory if it doesn't exist.
     */
    private void initializeCacheDirectory() {
        try {
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
                logger.logProgress("Cache directory created", 1, 1);
            }
        } catch (IOException e) {
            logger.logError("Cache Initialization", e, "Failed to create cache directory: " + cacheDirectory);
            throw new RuntimeException("Failed to initialize cache directory", e);
        }
    }

    /**
     * Caches frontend repository issues.
     * 
     * @param issues     List of frontend issues to cache
     * @param repository Repository name for cache key
     */
    public void cacheFrontendIssues(List<GitHubIssue> issues, String repository) {
        String cacheKey = generateCacheKey(FRONTEND_CACHE_PREFIX, repository);
        logger.logProgress("Saving with cache key: " + cacheKey, 1, 1);
        CacheEntry<List<GitHubIssue>> entry = new CacheEntry<>(issues, LocalDateTime.now(), repository);
        saveCacheEntry(cacheKey, entry);

        logger.logProgress("Frontend issues cached", issues.size(), issues.size());
    }

    /**
     * Caches backend repository issues.
     * 
     * @param issues     List of backend issues to cache
     * @param repository Repository name for cache key
     */
    public void cacheBackendIssues(List<GitHubIssue> issues, String repository) {
        String cacheKey = generateCacheKey(BACKEND_CACHE_PREFIX, repository);
        CacheEntry<List<GitHubIssue>> entry = new CacheEntry<>(issues, LocalDateTime.now(), repository);
        saveCacheEntry(cacheKey, entry);

        logger.logProgress("Backend issues cached", issues.size(), issues.size());
    }

    /**
     * Retrieves cached frontend issues if available and not expired.
     * 
     * @param repository Repository name for cache key
     * @return Optional containing cached issues if valid, empty otherwise
     */
    public Optional<List<GitHubIssue>> getCachedFrontendIssues(String repository) {
        String cacheKey = generateCacheKey(FRONTEND_CACHE_PREFIX, repository);
        logger.logProgress("Looking for cache key: " + cacheKey, 1, 1);
        Optional<CacheEntry<List<GitHubIssue>>> entry = loadCacheEntry(cacheKey,
                new TypeReference<CacheEntry<List<GitHubIssue>>>() {
                });

        if (entry.isPresent() && isCacheValid(entry.get())) {
            logger.logProgress("Frontend cache hit", 1, 1);
            return Optional.of(entry.get().getData());
        }

        logger.logProgress("Frontend cache miss", 0, 1);
        return Optional.empty();
    }

    /**
     * Retrieves cached backend issues if available and not expired.
     * 
     * @param repository Repository name for cache key
     * @return Optional containing cached issues if valid, empty otherwise
     */
    public Optional<List<GitHubIssue>> getCachedBackendIssues(String repository) {
        String cacheKey = generateCacheKey(BACKEND_CACHE_PREFIX, repository);
        Optional<CacheEntry<List<GitHubIssue>>> entry = loadCacheEntry(cacheKey,
                new TypeReference<CacheEntry<List<GitHubIssue>>>() {
                });

        if (entry.isPresent() && isCacheValid(entry.get())) {
            logger.logProgress("Backend cache hit", 1, 1);
            return Optional.of(entry.get().getData());
        }

        logger.logProgress("Backend cache miss", 0, 1);
        return Optional.empty();
    }

    /**
     * Invalidates all cached data by deleting cache files.
     */
    public void invalidateCache() {
        try {
            if (Files.exists(cacheDirectory)) {
                Files.walk(cacheDirectory)
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(CACHE_FILE_EXTENSION))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.logError("Cache Invalidation", e, "Failed to delete cache file: " + path);
                            }
                        });
                logger.logProgress("Cache invalidated", 1, 1);
            }
        } catch (IOException e) {
            logger.logError("Cache Invalidation", e, "Failed to walk cache directory");
            throw new RuntimeException("Failed to invalidate cache", e);
        }
    }

    /**
     * Invalidates cache for a specific repository.
     * 
     * @param repository     Repository name
     * @param repositoryType "frontend" or "backend"
     */
    public void invalidateRepositoryCache(String repository, String repositoryType) {
        String prefix = "frontend".equals(repositoryType) ? FRONTEND_CACHE_PREFIX : BACKEND_CACHE_PREFIX;
        String cacheKey = generateCacheKey(prefix, repository);
        Path cacheFile = cacheDirectory.resolve(cacheKey + CACHE_FILE_EXTENSION);

        try {
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
                logger.logProgress("Repository cache invalidated: " + repository, 1, 1);
            }
        } catch (IOException e) {
            logger.logError("Repository Cache Invalidation", e,
                    "Failed to delete cache file for repository: " + repository);
        }
    }

    /**
     * Gets cache statistics including file count and total size.
     * 
     * @return Cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        try {
            if (!Files.exists(cacheDirectory)) {
                return new CacheStatistics(0, 0, LocalDateTime.now());
            }

            List<Path> cacheFiles = new ArrayList<>();
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(CACHE_FILE_EXTENSION))
                    .forEach(cacheFiles::add);

            long totalSize = cacheFiles.stream()
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();

            return new CacheStatistics(cacheFiles.size(), totalSize, LocalDateTime.now());

        } catch (IOException e) {
            logger.logError("Cache Statistics", e, "Failed to calculate cache statistics");
            return new CacheStatistics(0, 0, LocalDateTime.now());
        }
    }

    /**
     * Cleans up expired cache entries.
     */
    public void cleanupExpiredEntries() {
        try {
            if (!Files.exists(cacheDirectory)) {
                return;
            }

            int deletedCount = 0;
            List<Path> cacheFiles = new ArrayList<>();
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(CACHE_FILE_EXTENSION))
                    .forEach(cacheFiles::add);

            for (Path cacheFile : cacheFiles) {
                try {
                    // Try to load the cache entry to check if it's expired
                    Optional<CacheEntry<Object>> entry = loadCacheEntry(
                            cacheFile.getFileName().toString().replace(CACHE_FILE_EXTENSION, ""),
                            new TypeReference<CacheEntry<Object>>() {
                            });

                    if (entry.isPresent() && !isCacheValid(entry.get())) {
                        Files.delete(cacheFile);
                        deletedCount++;
                    }
                } catch (Exception e) {
                    // If we can't read the cache file, it's probably corrupted, so delete it
                    Files.delete(cacheFile);
                    deletedCount++;
                }
            }

            if (deletedCount > 0) {
                logger.logProgress("Expired cache entries cleaned up", deletedCount, deletedCount);
            }

        } catch (IOException e) {
            logger.logError("Cache Cleanup", e, "Failed to cleanup expired cache entries");
        }
    }

    /**
     * Generates a cache key for the given prefix and repository.
     * Sanitizes all special characters to ensure valid file names.
     * Format: prefix-sanitized-repo-YYYY-MM-DD (guarantees uniqueness per repo per
     * day)
     */
    private String generateCacheKey(String prefix, String repository) {
        // Sanitize repository name: replace all special characters with hyphens
        String sanitizedRepo = repository
                .replaceAll("[^a-zA-Z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens

        if (sanitizedRepo.isEmpty()) {
            sanitizedRepo = "default";
        }

        // Format date safely to ensure no null characters
        String dateStr = LocalDate.now().format(DATE_FORMAT);

        // Add hash of repository name to ensure uniqueness even if multiple repos
        // have the same sanitized name (e.g., "my-repo" and "my_repo" both become
        // "my-repo")
        // This prevents cache collisions when different repository names sanitize to
        // identical strings
        String repoHash = Integer.toHexString(Math.abs(repository.hashCode()));

        // Create cache key: prefix-sanitized-repo-hash-YYYY-MM-DD
        // This ensures per-repository uniqueness per type per day
        String cacheKey = prefix + sanitizedRepo + "-" + repoHash + "-" + dateStr;

        return cacheKey;
    }

    /**
     * Saves a cache entry to disk.
     */
    private <T> void saveCacheEntry(String cacheKey, CacheEntry<T> entry) {
        Path cacheFile = cacheDirectory.resolve(cacheKey + CACHE_FILE_EXTENSION);

        try {
            objectMapper.writeValue(cacheFile.toFile(), entry);
        } catch (IOException e) {
            logger.logError("Cache Save", e, "Failed to save cache entry: " + cacheKey);
        }
    }

    /**
     * Loads a cache entry from disk.
     */
    private <T> Optional<CacheEntry<T>> loadCacheEntry(String cacheKey, TypeReference<CacheEntry<T>> typeRef) {
        Path cacheFile = cacheDirectory.resolve(cacheKey + CACHE_FILE_EXTENSION);

        if (!Files.exists(cacheFile)) {
            return Optional.empty();
        }

        try {
            CacheEntry<T> entry = objectMapper.readValue(cacheFile.toFile(), typeRef);
            return Optional.of(entry);
        } catch (IOException e) {
            logger.logError("Cache Load", e, "Failed to load cache entry: " + cacheKey);
            return Optional.empty();
        }
    }

    /**
     * Checks if a cache entry is still valid (not expired).
     */
    private <T> boolean isCacheValid(CacheEntry<T> entry) {
        LocalDateTime expirationTime = entry.getTimestamp().plusHours(cacheExpirationHours);
        return LocalDateTime.now().isBefore(expirationTime);
    }

    /**
     * Cache entry wrapper that includes metadata.
     */
    public static class CacheEntry<T> {
        private T data;
        private LocalDateTime timestamp;
        private String repository;

        // Default constructor for Jackson
        public CacheEntry() {
        }

        public CacheEntry(T data, LocalDateTime timestamp, String repository) {
            this.data = data;
            this.timestamp = timestamp;
            this.repository = repository;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getRepository() {
            return repository;
        }

        public void setRepository(String repository) {
            this.repository = repository;
        }
    }

    /**
     * Cache statistics information.
     */
    public static class CacheStatistics {
        private final int fileCount;
        private final long totalSizeBytes;
        private final LocalDateTime calculatedAt;

        public CacheStatistics(int fileCount, long totalSizeBytes, LocalDateTime calculatedAt) {
            this.fileCount = fileCount;
            this.totalSizeBytes = totalSizeBytes;
            this.calculatedAt = calculatedAt;
        }

        public int getFileCount() {
            return fileCount;
        }

        public long getTotalSizeBytes() {
            return totalSizeBytes;
        }

        public double getTotalSizeMB() {
            return totalSizeBytes / (1024.0 * 1024.0);
        }

        public LocalDateTime getCalculatedAt() {
            return calculatedAt;
        }
    }
}