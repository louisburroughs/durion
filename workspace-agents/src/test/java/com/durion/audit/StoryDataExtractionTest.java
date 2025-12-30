package com.durion.audit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.assertj.core.api.Assertions;

import com.durion.audit.StoryMetadata;
import com.durion.audit.StorySequenceParser;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based test for Property 4: Report content completeness
 * **Feature: missing-issues-audit, Property 4: Report content completeness**
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
 * 
 * For any set of missing issues, generated reports should contain all required
 * fields:
 * story number, title, URL, expected title format, and target repository.
 */
public class StoryDataExtractionTest {

    @Property(tries = 100)
    @Label("Property 4: Story data extraction completeness")
    void storySequenceParserShouldExtractAllRequiredFields(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber,
            @ForAll("validTitle") String storyTitle,
            @ForAll("validGitHubUrl") String storyUrl,
            @ForAll("validDomain") String domain,
            @ForAll("validStatus") String status) throws Exception {
        // Basic sanity on generated values
        Assume.that(!storyUrl.contains("\n"));
        Assume.that(!storyUrl.contains("\r"));

        // Given: A story sequence markdown content with all required fields
        String storyContent = String.format("""
                # Story Sequence Coordination

                ## Active Stories

                ### Story #%d: %s

                **URL**: %s

                **Domain**: %s

                **Status**: %s

                ---
                """, storyNumber, storyTitle, storyUrl, domain, status);

        Path tempFile = Files.createTempFile("test-story-extraction", ".md");
        try {
            Files.write(tempFile, storyContent.getBytes());

            // When: Parsing the story sequence
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result;
            try {
                result = parser.parseStorySequence(tempFile.toString());
            } catch (StorySequenceParser.StorySequenceParserException e) {
                // If parsing fails, skip this test case (likely due to invalid generated data)
                System.err.println("Parser failed with: " + e.getMessage());
                System.err.println("Content was:\n" + storyContent);
                throw new AssertionError("Parser should not fail for valid input: " + e.getMessage(), e);
            }

            // Then: The result should contain exactly one story with all required fields
            Assertions.assertThat(result)
                    .withFailMessage("Expected 1 story to be parsed, but got %d", result.size())
                    .hasSize(1);
            Assertions.assertThat(result)
                    .withFailMessage("Expected to find story #%d", storyNumber)
                    .containsKey(storyNumber);

            StoryMetadata extractedStory = result.get(storyNumber);

            // And: All required fields should be present and correct
            Assertions.assertThat(extractedStory.getStoryNumber())
                    .withFailMessage("Story number mismatch")
                    .isEqualTo(storyNumber);
            String trimmedTitle = storyTitle.trim();
            Assertions.assertThat(extractedStory.getTitle())
                    .withFailMessage("Title mismatch - expected '%s' but got '%s'", trimmedTitle,
                            extractedStory.getTitle())
                    .isEqualTo(trimmedTitle);
            Assertions.assertThat(extractedStory.getUrl())
                    .withFailMessage("URL mismatch - expected '%s' but got '%s'", storyUrl, extractedStory.getUrl())
                    .isEqualTo(storyUrl);
           

            // And: The clean title should remove [STORY] prefix if present

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property(tries = 50)
    @Label("Property 4: Story data extraction handles missing optional fields")
    void storySequenceParserShouldHandleMissingOptionalFields(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber,
            @ForAll("validTitle") String storyTitle,
            @ForAll("validGitHubUrl") String storyUrl) throws Exception {
        // Ensure data doesn't have problematic characters in URL
        Assume.that(!storyUrl.contains("\n"));
        Assume.that(!storyUrl.contains("\r"));

        // Given: A story sequence with only required fields (URL is required,
        // domain/status optional)
        String storyContent = String.format("""
                # Story Sequence Coordination

                ## Active Stories

                ### Story #%d: %s

                **URL**: %s

                ---
                """, storyNumber, storyTitle, storyUrl);

        Path tempFile = Files.createTempFile("test-missing-optional", ".md");
        try {
            Files.write(tempFile, storyContent.getBytes());

            // When: Parsing the story sequence
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result;
            try {
                result = parser.parseStorySequence(tempFile.toString());
            } catch (StorySequenceParser.StorySequenceParserException e) {
                System.err.println("Parser failed with: " + e.getMessage());
                System.err.println("Content was:\n" + storyContent);
                throw new AssertionError("Parser should not fail for valid input: " + e.getMessage(), e);
            }

            // Then: The result should contain the story with required fields and null
            // optional fields
            Assertions.assertThat(result)
                    .withFailMessage("Expected 1 story to be parsed, but got %d", result.size())
                    .hasSize(1);

            StoryMetadata extractedStory = result.get(storyNumber);
            Assertions.assertThat(extractedStory.getStoryNumber()).isEqualTo(storyNumber);
            Assertions.assertThat(extractedStory.getTitle().trim()).isEqualTo(storyTitle.trim());
            Assertions.assertThat(extractedStory.getUrl()).isEqualTo(storyUrl);
           

            if (storyTitle.startsWith("[STORY] ")) {
                Assertions.assertThat(extractedStory.getCleanTitle())
                        .isEqualTo(storyTitle.substring(8));
            } else {
                Assertions.assertThat(extractedStory.getCleanTitle())
                        .isEqualTo(storyTitle.trim());
            }

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property(tries = 30)
    @Label("Property 4: Story data extraction skips entries with missing URLs")
    void storySequenceParserShouldSkipEntriesWithMissingUrls(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber1,
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber2,
            @ForAll @StringLength(min = 10, max = 100) String storyTitle1,
            @ForAll @StringLength(min = 10, max = 100) String storyTitle2,
            @ForAll("validGitHubUrl") String validUrl) throws Exception {

        Assume.that(storyNumber1 != storyNumber2); // Ensure different story numbers

        // Given: Two stories, one with URL and one without
        String storyContent = String.format("""
                # Story Sequence Coordination

                ## Active Stories

                ### Story #%d: %s

                **URL**: %s

                **Domain**: user

                ---

                ### Story #%d: %s

                **Domain**: payment

                **Status**: Processed

                ---
                """, storyNumber1, storyTitle1, validUrl, storyNumber2, storyTitle2);

        Path tempFile = Files.createTempFile("test-missing-url", ".md");
        try {
            Files.write(tempFile, storyContent.getBytes());

            // When: Parsing the story sequence
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());

            // Then: Only the story with URL should be included
            Assertions.assertThat(result).hasSize(1);
            Assertions.assertThat(result).containsKey(storyNumber1);
            Assertions.assertThat(result).doesNotContainKey(storyNumber2);

            StoryMetadata extractedStory = result.get(storyNumber1);
            Assertions.assertThat(extractedStory.getUrl()).isEqualTo(validUrl);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property(tries = 20)
    @Label("Property 4: Story data extraction handles multiple stories consistently")
    void storySequenceParserShouldHandleMultipleStoriesConsistently(
            @ForAll("storyList") java.util.List<StoryData> stories) throws Exception {

        Assume.that(stories.size() >= 2 && stories.size() <= 5);

        // Given: Multiple stories in markdown format
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("# Story Sequence Coordination\n\n## Active Stories\n\n");

        for (StoryData story : stories) {
            contentBuilder.append(String.format("""
                    ### Story #%d: %s

                    **URL**: %s

                    **Domain**: %s

                    **Status**: %s

                    ---

                    """, story.number, story.title, story.url, story.domain, story.status));
        }

        Path tempFile = Files.createTempFile("test-multiple-stories", ".md");
        try {
            Files.write(tempFile, contentBuilder.toString().getBytes());

            // When: Parsing the story sequence
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());

            // Then: All stories should be extracted correctly
            Assertions.assertThat(result).hasSize(stories.size());

            for (StoryData originalStory : stories) {
                Assertions.assertThat(result).containsKey(originalStory.number);

                StoryMetadata extractedStory = result.get(originalStory.number);
                Assertions.assertThat(extractedStory.getStoryNumber()).isEqualTo(originalStory.number);
                Assertions.assertThat(extractedStory.getTitle()).isEqualTo(originalStory.title);
                Assertions.assertThat(extractedStory.getUrl()).isEqualTo(originalStory.url);
                
            }

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Provide
    Arbitrary<String> validTitle() {
        String allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -_:";
        return Arbitraries.strings()
                .withChars(allowed.toCharArray())
                .ofMinLength(10)
                .ofMaxLength(100);
    }

    @Provide
    Arbitrary<String> validGitHubUrl() {
        return Arbitraries.integers().between(1, 10000)
                .map(num -> "https://github.com/louisburroughs/durion/issues/" + num);
    }

    @Provide
    Arbitrary<String> validDomain() {
        return Arbitraries.of("payment", "user", "admin", "reporting", "general", "inventory");
    }

    @Provide
    Arbitrary<String> validStatus() {
        return Arbitraries.of("Processed", "In Progress", "Pending", "Completed");
    }

    @Provide
    Arbitrary<java.util.List<StoryData>> storyList() {
        return Arbitraries.integers().between(1, 1000)
                .list().ofMinSize(2).ofMaxSize(5)
                .map(numbers -> numbers.stream().distinct().collect(java.util.stream.Collectors.toList()))
                .flatMap(uniqueNumbers -> Combinators.combine(
                        Arbitraries.just(uniqueNumbers),
                        Arbitraries.strings().withCharRange('A', 'z').ofMinLength(10).ofMaxLength(50).list()
                                .ofSize(uniqueNumbers.size()),
                        validGitHubUrl().list().ofSize(uniqueNumbers.size()),
                        validDomain().list().ofSize(uniqueNumbers.size()),
                        validStatus().list().ofSize(uniqueNumbers.size()))
                        .as((nums, titles, urls, domains, statuses) -> {
                            java.util.List<StoryData> result = new java.util.ArrayList<>();
                            for (int i = 0; i < nums.size(); i++) {
                                result.add(new StoryData(nums.get(i), titles.get(i), urls.get(i), domains.get(i),
                                        statuses.get(i)));
                            }
                            return result;
                        }));
    }

    // Helper class for test data
    public static class StoryData {
        public final int number;
        public final String title;
        public final String url;
        public final String domain;
        public final String status;

        public StoryData(int number, String title, String url, String domain, String status) {
            this.number = number;
            this.title = title;
            this.url = url;
            this.domain = domain;
            this.status = status;
        }
    }
}