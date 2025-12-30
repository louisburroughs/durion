package com.durion.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Report;
import net.jqwik.api.Reporting;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for audit resumption consistency.
 * 
 * **Property 13: Audit resumption consistency**
 * **Validates: Requirements 5.5**
 * 
 * For any interrupted audit operation, the system should be able to resume from
 * the last completed checkpoint without duplicating work.
 */
public class AuditResumptionConsistencyTest {

        /**
         * Property: Resumption from specific story point
         * 
         * For any list of stories and a resume point, the filtered result should
         * include only stories from the resume point and earlier (assuming descending
         * order).
         */
        @Property
        @Report(Reporting.GENERATED)
        void resumptionFromSpecificStoryPoint(
                        @ForAll @Size(min = 10, max = 30) List<@IntRange(min = 1, max = 500) Integer> storyNumbers,
                        @ForAll @IntRange(min = 1, max = 500) int resumeFromStory) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Sort stories in descending order (typical processing order)
                List<Integer> sortedStories = storyNumbers.stream()
                                .sorted((a, b) -> Integer.compare(b, a))
                                .collect(Collectors.toList());

                AuditConfiguration config = AuditConfiguration.builder()
                                .githubToken("test-token")
                                .setResumeMode(true)
                                .setResumeFromStory(resumeFromStory)
                                .build();

                // Act
                List<Integer> filteredStories = filter.filterForResumption(
                                sortedStories, Optional.empty(), Optional.of(resumeFromStory));

                // Assert - All filtered stories should be <= resumeFromStory (in descending
                // order processing)
                for (Integer storyNumber : filteredStories) {
                        assertTrue(storyNumber <= resumeFromStory,
                                        "Resumed story #" + storyNumber + " should be <= resume point "
                                                        + resumeFromStory);
                }

                // Assert - All applicable stories should be included
                List<Integer> expectedStories = sortedStories.stream()
                                .filter(story -> story <= resumeFromStory)
                                .collect(Collectors.toList());

                assertEquals(expectedStories.size(), filteredStories.size(),
                                "Resumption should include all stories from resume point and earlier");
        }

        /**
         * Property: Resumption with recent audit results
         * 
         * For any previous audit result that is recent (within last hour),
         * resumption should focus on stories that had missing issues or weren't
         * checked.
         */
        @Property
        @Report(Reporting.GENERATED)
        void resumptionWithRecentAuditResults(
                        @ForAll @Size(min = 5, max = 20) List<@IntRange(min = 1, max = 200) Integer> storyNumbers,
                        @ForAll @Size(min = 1, max = 5) List<@IntRange(min = 1, max = 200) Integer> storiesWithMissingIssues) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create a recent audit result (within last hour)
                LocalDateTime recentTimestamp = LocalDateTime.now().minusMinutes(30);

                // Create missing issues for some stories
                List<MissingIssue> missingFrontendIssues = storiesWithMissingIssues.stream()
                                .map(storyNumber -> new MissingIssue(
                                                storyNumber,
                                                "Test Story #" + storyNumber,
                                                "https://github.com/test/repo/issues/" + storyNumber,
                                                "frontend",
                                                "test-repo",
                                                "[FRONTEND] Test Story #" + storyNumber))
                                .collect(Collectors.toList());

                AuditResult previousResult = new AuditResult(
                                missingFrontendIssues,
                                new ArrayList<>(), // No backend missing issues
                                storyNumbers.size(),
                                recentTimestamp,
                                AuditConfiguration.builder()
                                                .githubToken("test-token")
                                                .build(),
                                new AuditStatistics(storyNumbers.size(), 0, 0,
                                                missingFrontendIssues.size(), 0, recentTimestamp));

                // Act
                List<Integer> filteredStories = filter.filterForResumption(
                                storyNumbers, Optional.of(previousResult), Optional.empty());

                // Assert - Should include stories that had missing issues
                for (Integer storyWithMissingIssue : storiesWithMissingIssues) {
                        if (storyNumbers.contains(storyWithMissingIssue)) {
                                assertTrue(filteredStories.contains(storyWithMissingIssue),
                                                "Story #" + storyWithMissingIssue
                                                                + " with missing issues should be included in resumption");
                        }
                }
        }

        /**
         * Property: Resumption without previous audit includes all stories
         * 
         * For any list of stories when no previous audit result is available,
         * resumption filtering should include all stories.
         */
        @Property
        @Report(Reporting.GENERATED)
        void resumptionWithoutPreviousAuditIncludesAllStories(
                        @ForAll @Size(min = 3, max = 15) List<@IntRange(min = 1, max = 100) Integer> storyNumbers) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Act - No previous audit result
                List<Integer> filteredStories = filter.filterForResumption(
                                storyNumbers, Optional.empty(), Optional.empty());

                // Assert - Should include all stories when no previous audit exists
                assertEquals(storyNumbers.size(), filteredStories.size(),
                                "Resumption without previous audit should include all stories");

                for (Integer storyNumber : storyNumbers) {
                        assertTrue(filteredStories.contains(storyNumber),
                                        "Story #" + storyNumber + " should be included when no previous audit exists");
                }
        }

        /**
         * Property: Resumption with old audit results ignores previous results
         * 
         * For any previous audit result that is old (more than 1 hour ago),
         * resumption should ignore the previous results and include all stories.
         */
        @Property
        @Report(Reporting.GENERATED)
        void resumptionWithOldAuditResultsIgnoresPreviousResults(
                        @ForAll @Size(min = 5, max = 15) List<@IntRange(min = 1, max = 100) Integer> storyNumbers,
                        @ForAll @IntRange(min = 2, max = 24) int hoursAgo) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create an old audit result (more than 1 hour ago)
                LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(hoursAgo);

                AuditResult oldResult = new AuditResult(
                                new ArrayList<>(), // Some missing issues
                                new ArrayList<>(),
                                storyNumbers.size(),
                                oldTimestamp,
                                AuditConfiguration.builder()
                                                .githubToken("test-token")
                                                .build(),
                                new AuditStatistics(storyNumbers.size(), 0, 0, 0, 0, oldTimestamp));

                // Act
                List<Integer> filteredStories = filter.filterForResumption(
                                storyNumbers, Optional.of(oldResult), Optional.empty());

                // Assert - Should include all stories when previous audit is old
                assertEquals(storyNumbers.size(), filteredStories.size(),
                                "Resumption with old audit result should include all stories");

                for (Integer storyNumber : storyNumbers) {
                        assertTrue(filteredStories.contains(storyNumber),
                                        "Story #" + storyNumber + " should be included when previous audit is old");
                }
        }

        /**
         * Property: Combined filtering with resumption
         * 
         * For any configuration with multiple filters and resumption enabled,
         * all filters should be applied in the correct sequence.
         */
        @Property
        @Report(Reporting.GENERATED)
        void combinedFilteringWithResumption(
                        @ForAll @Size(min = 10, max = 25) List<@IntRange(min = 1, max = 300) Integer> storyNumbers,
                        @ForAll @IntRange(min = 1, max = 100) int rangeStart,
                        @ForAll @IntRange(min = 1, max = 100) int rangeEnd,
                        @ForAll @IntRange(min = 1, max = 30) int daysBack) {

                Assume.that(rangeStart <= rangeEnd);

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create story metadata with processing dates
                Map<Integer, StoryMetadata> storyMetadata = new HashMap<>();

                for (Integer storyNumber : storyNumbers) {
                        StoryMetadata metadata = new StoryMetadata(
                                        storyNumber,
                                        "Test Story #" + storyNumber,
                                        "https://github.com/test/repo/issues/" + storyNumber,
                                        true,
                                        true);
                        storyMetadata.put(storyNumber, metadata);
                }

                // Create configuration with multiple filters
                AuditConfiguration config = AuditConfiguration.builder()
                                .githubToken("test-token")
                                .setDaysBack(daysBack)
                                .storyRange(rangeStart, rangeEnd)
                                .setResumeMode(true)
                                .build();

                // Act
                List<Integer> filteredStories = filter.applyFilters(
                                storyNumbers, storyMetadata, config, Optional.empty());

                // Assert - All filtered stories should satisfy all filter conditions
                for (Integer storyNumber : filteredStories) {
                        // Should be within story range
                        assertTrue(storyNumber >= rangeStart && storyNumber <= rangeEnd,
                                        "Story #" + storyNumber + " should be within range " + rangeStart + "-"
                                                        + rangeEnd);
                }

                // Assert - Should not include stories outside the combined filters
                long expectedCount = storyNumbers.stream()
                                .filter(story -> story >= rangeStart && story <= rangeEnd)
                                .count();

                assertEquals(expectedCount, filteredStories.size(),
                                "Combined filtering should produce expected number of stories");
        }

        /**
         * Property: Empty story list with resumption returns empty result
         * 
         * For any resumption configuration applied to an empty story list,
         * the result should be empty.
         */
        @Property
        @Report(Reporting.GENERATED)
        void emptyStoryListWithResumptionReturnsEmptyResult(
                        @ForAll @IntRange(min = 1, max = 100) int resumeFromStory) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                List<Integer> emptyStoryList = List.of();

                // Act
                List<Integer> filteredStories = filter.filterForResumption(
                                emptyStoryList, Optional.empty(), Optional.of(resumeFromStory));

                // Assert
                assertTrue(filteredStories.isEmpty(),
                                "Resumption filtering on empty list should return empty result");
        }

        /**
         * Property: Resumption preserves story order
         * 
         * For any ordered list of stories and resumption configuration,
         * the relative order should be preserved in the filtered result.
         */
        @Property
        @Report(Reporting.GENERATED)
        void resumptionPreservesStoryOrder(
                        @ForAll @IntRange(min = 50, max = 150) int resumeFromStory) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create an ordered list in descending order (typical processing order)
                List<Integer> orderedStories = List.of(
                                resumeFromStory + 20, resumeFromStory + 10, resumeFromStory + 5,
                                resumeFromStory, resumeFromStory - 5, resumeFromStory - 10, resumeFromStory - 20);

                // Act
                List<Integer> filteredStories = filter.filterForResumption(
                                orderedStories, Optional.empty(), Optional.of(resumeFromStory));

                // Assert - Should maintain descending order
                for (int i = 1; i < filteredStories.size(); i++) {
                        assertTrue(filteredStories.get(i - 1) >= filteredStories.get(i),
                                        "Resumption should preserve descending order");
                }

                // Assert - Should include expected stories in order
                List<Integer> expectedStories = orderedStories.stream()
                                .filter(story -> story <= resumeFromStory)
                                .collect(Collectors.toList());

                assertEquals(expectedStories, filteredStories,
                                "Resumption should preserve original order of applicable stories");
        }
}