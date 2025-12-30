package com.durion.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Report;
import net.jqwik.api.Reporting;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for caching effectiveness.
 * 
 * **Property 12: Caching effectiveness**
 * **Validates: Requirements 5.3**
 * 
 * For any repeated repository query within the same audit session, the system
 * should use
 * cached results instead of making duplicate API calls.
 */
public class CachingEffectivenessTest {

    /**
     * Helper method to create a CacheManager with proper ObjectMapper configuration
     * for testing with TestGitHubIssue instances.
     */
    private CacheManager createTestCacheManager(AuditLogger logger, int expirationHours) {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        // Enable default typing to include type information for polymorphic
        // deserialization
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

        return new CacheManager(logger, expirationHours, objectMapper);
    }

    /**
     * Property: Cache hit for repeated queries within expiration time
     * 
     * For any repository and list of issues, if we cache the issues and then
     * immediately query for them again, we should get a cache hit with the same
     * data.
     */
    @Property
    @Report(Reporting.GENERATED)
    void cacheHitForRepeatedQueries(@ForAll @From("validRepositories") String repository,
            @ForAll @Size(min = 1, max = 50) List<@From("githubIssues") TestGitHubIssue> issues) {

        // Arrange
        AuditLogger logger = new AuditLogger();

        CacheManager cacheManager = createTestCacheManager(logger, 24); // 24-hour expiration
        try {
            // Act - Cache the issues
            cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(issues), repository);

            // Act - Immediately query for cached issues
            Optional<List<GitHubIssue>> cachedIssues = cacheManager.getCachedFrontendIssues(repository);

            // Assert - Should get cache hit with same data
            assertTrue(cachedIssues.isPresent(), "Cache should hit for immediate query");
            assertEquals(issues.size(), cachedIssues.get().size(), "Cached issues count should match original");

            // Verify issue details match
            for (int i = 0; i < issues.size(); i++) {
                GitHubIssue original = issues.get(i);
                GitHubIssue cached = cachedIssues.get().get(i);

                assertEquals(original.getNumber(), cached.getNumber(), "Issue numbers should match");
                assertEquals(original.getTitle(), cached.getTitle(), "Issue titles should match");
                assertEquals(original.getUrl(), cached.getUrl(), "Issue URLs should match");
            }

        } finally

        {
            // Cleanup
            cacheManager.invalidateCache();
        }
    }

    /**
     * Property: Cache miss for different repositories
     * 
     * For any two different repositories, caching issues for one repository
     * should not affect queries for the other repository.
     */
    @Property
    @Report(Reporting.GENERATED)
    void cacheMissForDifferentRepositories(@ForAll @From("validRepositories") String repository1,
            @ForAll @From("validRepositories") String repository2,
            @ForAll @Size(min = 1, max = 20) List<@From("githubIssues") TestGitHubIssue> issues) {

        Assume.that(!repository1.equals(repository2));

        // Arrange
        AuditLogger logger = new AuditLogger();
        CacheManager cacheManager = createTestCacheManager(logger, 24);

        try {
            // Act - Cache issues for repository1
            cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(issues), repository1);

            // Act - Query for repository2 (should be cache miss)
            Optional<List<GitHubIssue>> cachedIssues = cacheManager.getCachedFrontendIssues(repository2);

            // Assert - Should get cache miss
            assertFalse(cachedIssues.isPresent(), "Cache should miss for different repository");

        } finally {
            // Cleanup
            cacheManager.invalidateCache();
        }
    }

    /**
     * Property: Cache separation between frontend and backend
     * 
     * For any repository, caching frontend issues should not affect backend issue
     * queries
     * and vice versa.
     */
    @Property
    @Report(Reporting.GENERATED)
    void cacheSeparationBetweenFrontendAndBackend(@ForAll @From("validRepositories") String repository,
            @ForAll @Size(min = 1, max = 20) List<@From("githubIssues") TestGitHubIssue> frontendIssues,
            @ForAll @Size(min = 1, max = 20) List<@From("githubIssues") TestGitHubIssue> backendIssues) {

        // Arrange
        AuditLogger logger = new AuditLogger();
        CacheManager cacheManager = createTestCacheManager(logger, 24);

        try {
            // Act - Cache frontend issues
            cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(frontendIssues), repository);

            // Act - Query for backend issues (should be cache miss)
            Optional<List<GitHubIssue>> cachedBackendIssues = cacheManager.getCachedBackendIssues(repository);

            // Assert - Should get cache miss for backend
            assertFalse(cachedBackendIssues.isPresent(), "Backend cache should miss when only frontend is cached");

            // Act - Cache backend issues
            cacheManager.cacheBackendIssues(new ArrayList<GitHubIssue>(backendIssues), repository);

            // Act - Query for both types
            Optional<List<GitHubIssue>> cachedFrontendIssues = cacheManager.getCachedFrontendIssues(repository);
            cachedBackendIssues = cacheManager.getCachedBackendIssues(repository);

            // Assert - Both should hit now
            assertTrue(cachedFrontendIssues.isPresent(), "Frontend cache should still hit");
            assertTrue(cachedBackendIssues.isPresent(), "Backend cache should hit after caching");

            assertEquals(frontendIssues.size(), cachedFrontendIssues.get().size(), "Frontend cache size should match");
            assertEquals(backendIssues.size(), cachedBackendIssues.get().size(), "Backend cache size should match");

        } finally {
            // Cleanup
            cacheManager.invalidateCache();
        }
    }

    /**
     * Property: Cache invalidation effectiveness
     * 
     * For any cached repository data, invalidating the cache should result in cache
     * misses
     * for subsequent queries.
     */
    @Property
    @Report(Reporting.GENERATED)
    void cacheInvalidationEffectiveness(@ForAll @From("validRepositories") String repository,
            @ForAll @Size(min = 1, max = 30) List<@From("githubIssues") TestGitHubIssue> issues) {

        // Arrange
        AuditLogger logger = new AuditLogger();
        CacheManager cacheManager = createTestCacheManager(logger, 24);

        try {
            // Act - Cache the issues
            cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(issues), repository);

            // Verify cache hit before invalidation
            Optional<List<GitHubIssue>> cachedIssues = cacheManager.getCachedFrontendIssues(repository);
            assertTrue(cachedIssues.isPresent(), "Cache should hit before invalidation");

            // Act - Invalidate cache
            cacheManager.invalidateCache();

            // Act - Query after invalidation
            cachedIssues = cacheManager.getCachedFrontendIssues(repository);

            // Assert - Should get cache miss after invalidation
            assertFalse(cachedIssues.isPresent(), "Cache should miss after invalidation");

        } finally {
            // Cleanup
            cacheManager.invalidateCache();
        }
    }

    /**
     * Property: Repository-specific cache invalidation
     * 
     * For any two repositories with cached data, invalidating one repository's
     * cache
     * should not affect the other repository's cache.
     */
    @Property
    @Report(Reporting.GENERATED)
    void repositorySpecificCacheInvalidation(@ForAll @From("validRepositories") String repository1,
            @ForAll @From("validRepositories") String repository2,
            @ForAll @Size(min = 1, max = 20) List<@From("githubIssues") TestGitHubIssue> issues1,
            @ForAll @Size(min = 1, max = 20) List<@From("githubIssues") TestGitHubIssue> issues2) {

        Assume.that(!repository1.equals(repository2));

        // Arrange
        AuditLogger logger = new AuditLogger();
        CacheManager cacheManager = createTestCacheManager(logger, 24);

        try {
            // Act - Cache issues for both repositories
            cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(issues1), repository1);
            cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(issues2), repository2);

            // Verify both caches hit
            Optional<List<GitHubIssue>> cached1 = cacheManager.getCachedFrontendIssues(repository1);
            Optional<List<GitHubIssue>> cached2 = cacheManager.getCachedFrontendIssues(repository2);

            assertTrue(cached1.isPresent(), "Repository1 cache should hit before invalidation");
            assertTrue(cached2.isPresent(), "Repository2 cache should hit before invalidation");

            // Act - Invalidate only repository1 cache
            cacheManager.invalidateRepositoryCache(repository1, "frontend");

            // Act - Query both repositories after selective invalidation
            cached1 = cacheManager.getCachedFrontendIssues(repository1);
            cached2 = cacheManager.getCachedFrontendIssues(repository2);

            // Assert - Repository1 should miss, repository2 should still hit
            assertFalse(cached1.isPresent(), "Repository1 cache should miss after invalidation");
            assertTrue(cached2.isPresent(), "Repository2 cache should still hit after selective invalidation");

        } finally {
            // Cleanup
            cacheManager.invalidateCache();
        }
    }

    /**
     * Property: Cache statistics accuracy
     * 
     * For any number of cached repositories, the cache statistics should accurately
     * reflect the number of cache files and their total size.
     */
    @Property
    @Report(Reporting.GENERATED)
    void cacheStatisticsAccuracy(@ForAll @Size(min = 1, max = 5) List<@From("validRepositories") String> repositories,
            @ForAll @Size(min = 1, max = 10) List<@From("githubIssues") TestGitHubIssue> issues) {

        // Arrange
        AuditLogger logger = new AuditLogger();
        CacheManager cacheManager = createTestCacheManager(logger, 24);

        try {
            // Act - Cache issues for each repository (both frontend and backend)
            for (String repository : repositories) {
                cacheManager.cacheFrontendIssues(new ArrayList<GitHubIssue>(issues), repository);
                cacheManager.cacheBackendIssues(new ArrayList<GitHubIssue>(issues), repository);
            }

            // Act - Get cache statistics
            CacheManager.CacheStatistics stats = cacheManager.getCacheStatistics();

            // Assert - Statistics should reflect cached data
            // Count unique repositories since duplicates will overwrite the same cache file
            long uniqueRepoCount = repositories.stream().distinct().count();
            int expectedFileCount = (int) (uniqueRepoCount * 2); // frontend + backend for each unique repo
            assertEquals(expectedFileCount, stats.getFileCount(),
                    "Cache file count should match number of unique cached repositories * 2");

            assertTrue(stats.getTotalSizeBytes() > 0, "Total cache size should be greater than 0");
            assertTrue(stats.getTotalSizeMB() >= 0, "Total cache size in MB should be non-negative");
            assertNotNull(stats.getCalculatedAt(), "Statistics calculation time should be set");

        } finally {
            // Cleanup
            cacheManager.invalidateCache();
        }
    }

    /**
     * Generates valid repository names (lowercase alphanumeric with hyphens and
     * underscores).
     * Repository names must be filesystem-safe and follow GitHub naming
     * conventions.
     * GitHub repository names are typically lowercase.
     */
    @Provide
    Arbitrary<String> validRepositories() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_')
                .ofMinLength(4)
                .ofMaxLength(31)
                .filter(s -> Character.isLetterOrDigit(s.charAt(0))); // First char must be alphanumeric
    }

    /**
     * Generates GitHub issues for testing.
     */
    @Provide
    Arbitrary<TestGitHubIssue> githubIssues() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 10000),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(100),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(200))
                .as((number, title, url) -> new TestGitHubIssue(number, title, url));
    }

    /**
     * Concrete implementation of GitHubIssue for testing that Jackson can
     * serialize/deserialize.
     */
    public static class TestGitHubIssue implements GitHubIssue {
        private int number;
        private String title;
        private String body;
        private String url;
        private List<String> labels;

        // Default constructor for Jackson
        public TestGitHubIssue() {
            this.labels = Arrays.asList("test-label");
            this.body = "Test body";
        }

        public TestGitHubIssue(int number, String title, String url) {
            this.number = number;
            this.title = title;
            this.url = url;
            this.body = "Test body";
            this.labels = Arrays.asList("test-label");
        }

        @Override
        public int getNumber() {
            return number;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getBody() {
            return body;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public List<String> getLabels() {
            return labels;
        }

        // Setters for Jackson
        public void setNumber(int number) {
            this.number = number;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setLabels(List<String> labels) {
            this.labels = labels;
        }
    }
}