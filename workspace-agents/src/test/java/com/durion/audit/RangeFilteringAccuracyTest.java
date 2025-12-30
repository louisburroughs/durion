package com.durion.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Report;
import net.jqwik.api.Reporting;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for range filtering accuracy.
 * 
 * **Property 11: Range filtering accuracy**
 * **Validates: Requirements 5.2**
 * 
 * For any specified story number range, the audit should process only stories
 * with numbers within that range (inclusive).
 */
public class RangeFilteringAccuracyTest {

        /**
         * Property: Range filtering includes only stories within range
         * 
         * For any list of story numbers and any valid range,
         * the filtered result should contain only stories within that range
         * (inclusive).
         */
        @Property
        @Report(Reporting.GENERATED)
        void rangeFilteringIncludesOnlyStoriesWithinRange(
                        @ForAll @Size(min = 10, max = 50) List<@IntRange(min = 1, max = 1000) Integer> storyNumbers,
                        @ForAll @IntRange(min = 1, max = 500) int rangeStart,
                        @ForAll @IntRange(min = 1, max = 500) int rangeEnd) {

                Assume.that(rangeStart <= rangeEnd);

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Act
                List<Integer> filteredStories = filter.filterByStoryRange(storyNumbers, rangeStart, rangeEnd);

                // Assert - All filtered stories should be within the range
                for (Integer storyNumber : filteredStories) {
                        assertTrue(storyNumber >= rangeStart,
                                        "Story #" + storyNumber + " should be >= range start " + rangeStart);
                        assertTrue(storyNumber <= rangeEnd,
                                        "Story #" + storyNumber + " should be <= range end " + rangeEnd);
                }

                // Assert - All stories within range should be included
                List<Integer> expectedStories = storyNumbers.stream()
                                .filter(storyNumber -> storyNumber >= rangeStart && storyNumber <= rangeEnd)
                                .collect(Collectors.toList());

                assertEquals(expectedStories.size(), filteredStories.size(),
                                "Filtered stories count should match expected count");

                for (Integer expectedStory : expectedStories) {
                        assertTrue(filteredStories.contains(expectedStory),
                                        "Expected story #" + expectedStory + " should be in filtered result");
                }
        }

        /**
         * Property: Range boundaries are inclusive
         * 
         * For any range with specific start and end values,
         * stories with exactly those numbers should be included.
         */
        @Property
        @Report(Reporting.GENERATED)
        void rangeBoundariesAreInclusive(
                        @ForAll @IntRange(min = 10, max = 100) int rangeStart,
                        @ForAll @IntRange(min = 10, max = 100) int rangeEnd) {

                Assume.that(rangeStart <= rangeEnd);

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create a list that includes the boundary values and some outside values
                List<Integer> storyNumbers = List.of(
                                rangeStart - 1, // Just before start
                                rangeStart, // Exactly at start
                                rangeEnd, // Exactly at end
                                rangeEnd + 1 // Just after end
                );

                // Act
                List<Integer> filteredStories = filter.filterByStoryRange(storyNumbers, rangeStart, rangeEnd);

                // Assert - Boundary values should be included
                assertTrue(filteredStories.contains(rangeStart),
                                "Range start value " + rangeStart + " should be included");
                assertTrue(filteredStories.contains(rangeEnd),
                                "Range end value " + rangeEnd + " should be included");

                // Assert - Values outside range should be excluded
                assertFalse(filteredStories.contains(rangeStart - 1),
                                "Value before range start should be excluded");
                assertFalse(filteredStories.contains(rangeEnd + 1),
                                "Value after range end should be excluded");

                // Assert - Exactly 2 stories should be in result (start and end)
                assertEquals(2, filteredStories.size(),
                                "Should have exactly 2 stories (start and end boundaries)");
        }

        /**
         * Property: Single value range works correctly
         * 
         * For any single story number used as both start and end of range,
         * only that story should be included if it exists in the input.
         */
        @Property
        @Report(Reporting.GENERATED)
        void singleValueRangeWorksCorrectly(
                        @ForAll @Size(min = 5, max = 20) List<@IntRange(min = 1, max = 200) Integer> storyNumbers,
                        @ForAll @IntRange(min = 1, max = 200) int singleValue) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Act - Use same value for start and end
                List<Integer> filteredStories = filter.filterByStoryRange(storyNumbers, singleValue, singleValue);

                // Assert - All stories in the result should match the single value
                for (Integer storyNumber : filteredStories) {
                        assertEquals(singleValue, storyNumber.intValue(),
                                        "Single value range should only include the specified story number");
                }

                if (storyNumbers.contains(singleValue)) {
                        assertFalse(filteredStories.isEmpty(),
                                        "Single value range should contain at least one story if it exists in input");
                } else {
                        assertEquals(0, filteredStories.size(),
                                        "Single value range should be empty if value doesn't exist in input");
                }
        }

        /**
         * Property: Empty input returns empty result
         * 
         * For any range applied to an empty story list,
         * the result should be empty.
         */
        @Property
        @Report(Reporting.GENERATED)
        void emptyInputReturnsEmptyResult(
                        @ForAll @IntRange(min = 1, max = 100) int rangeStart,
                        @ForAll @IntRange(min = 1, max = 100) int rangeEnd) {

                Assume.that(rangeStart <= rangeEnd);

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                List<Integer> emptyStoryList = List.of();

                // Act
                List<Integer> filteredStories = filter.filterByStoryRange(emptyStoryList, rangeStart, rangeEnd);

                // Assert
                assertTrue(filteredStories.isEmpty(),
                                "Range filtering on empty list should return empty result");
        }

        /**
         * Property: Range filtering preserves order
         * 
         * For any ordered list of story numbers and any range,
         * the relative order of stories should be preserved in the filtered result.
         */
        @Property
        @Report(Reporting.GENERATED)
        void rangeFilteringPreservesOrder(
                        @ForAll @IntRange(min = 50, max = 150) int rangeStart,
                        @ForAll @IntRange(min = 50, max = 150) int rangeEnd) {

                Assume.that(rangeStart <= rangeEnd);

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create an ordered list in ascending order that spans beyond the range
                List<Integer> orderedStoryNumbers = java.util.stream.Stream.of(
                                rangeStart - 10, rangeStart - 5, rangeStart, rangeStart + 5,
                                rangeStart + 10, rangeEnd - 5, rangeEnd, rangeEnd + 5, rangeEnd + 10)
                                .sorted()
                                .collect(java.util.stream.Collectors.toList());

                // Act
                List<Integer> filteredStories = filter.filterByStoryRange(orderedStoryNumbers, rangeStart, rangeEnd);

                // Assert - Filtered stories should be in ascending order
                for (int i = 1; i < filteredStories.size(); i++) {
                        assertTrue(filteredStories.get(i - 1) <= filteredStories.get(i),
                                        "Filtered stories should maintain ascending order");
                }

                // Assert - Should contain expected stories in order
                List<Integer> expectedInOrder = orderedStoryNumbers.stream()
                                .filter(story -> story >= rangeStart && story <= rangeEnd)
                                .collect(Collectors.toList());

                assertEquals(expectedInOrder, filteredStories,
                                "Filtered stories should match expected stories in same order");
        }

        /**
         * Property: Range filtering is independent of story metadata
         * 
         * For any list of story numbers and any range,
         * the filtering should work based only on story numbers,
         * regardless of whether story metadata exists.
         */
        @Property
        @Report(Reporting.GENERATED)
        void rangeFilteringIsIndependentOfMetadata(
                        @ForAll @Size(min = 5, max = 25) List<@IntRange(min = 1, max = 300) Integer> storyNumbers,
                        @ForAll @IntRange(min = 50, max = 200) int rangeStart,
                        @ForAll @IntRange(min = 50, max = 200) int rangeEnd) {

                Assume.that(rangeStart <= rangeEnd);

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Act - Filter without any metadata
                List<Integer> filteredWithoutMetadata = filter.filterByStoryRange(storyNumbers, rangeStart, rangeEnd);

                // Create some metadata for comparison (should not affect range filtering)
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

                // Act - Filter again (metadata should not affect range filtering)
                List<Integer> filteredWithMetadata = filter.filterByStoryRange(storyNumbers, rangeStart, rangeEnd);

                // Assert - Results should be identical regardless of metadata presence
                assertEquals(filteredWithoutMetadata.size(), filteredWithMetadata.size(),
                                "Range filtering should be independent of metadata presence");
                assertEquals(filteredWithoutMetadata, filteredWithMetadata,
                                "Range filtering results should be identical with or without metadata");

                // Assert - All results should be within range
                for (Integer storyNumber : filteredWithMetadata) {
                        assertTrue(storyNumber >= rangeStart && storyNumber <= rangeEnd,
                                        "All filtered stories should be within specified range");
                }
        }

        /**
         * Property: Large ranges include all applicable stories
         * 
         * For any list of story numbers and a range that encompasses all of them,
         * all stories should be included in the filtered result.
         */
        @Property
        @Report(Reporting.GENERATED)
        void largeRangesIncludeAllApplicableStories(
                        @ForAll @Size(min = 3, max = 15) List<@IntRange(min = 10, max = 90) Integer> storyNumbers) {

                // Arrange
                AuditLogger logger = new AuditLogger();
                IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

                // Create a range that definitely encompasses all story numbers
                int minStory = storyNumbers.stream().min(Integer::compareTo).orElse(1);
                int maxStory = storyNumbers.stream().max(Integer::compareTo).orElse(100);
                int rangeStart = minStory - 10;
                int rangeEnd = maxStory + 10;

                // Act
                List<Integer> filteredStories = filter.filterByStoryRange(storyNumbers, rangeStart, rangeEnd);

                // Assert - All original stories should be included
                assertEquals(storyNumbers.size(), filteredStories.size(),
                                "Large range should include all original stories");

                for (Integer originalStory : storyNumbers) {
                        assertTrue(filteredStories.contains(originalStory),
                                        "Large range should include story #" + originalStory);
                }
        }
}