package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the story-sequence.md file to extract story metadata including titles,
 * URLs, and domains.
 * Creates a mapping from story number to story metadata for use in audit
 * operations.
 */
public class StorySequenceParser {

    private static final String DEFAULT_STORY_SEQUENCE_FILE = ".github/orchestration/story-sequence.md";

    // Regex patterns for parsing story entries
    private static final Pattern STORY_HEADER_PATTERN = Pattern.compile(
            "^### Story #(\\d+): (.+)$", Pattern.MULTILINE);
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^\\*\\*URL\\*\\*: (.+)$", Pattern.MULTILINE);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^\\*\\*Domain\\*\\*: (.+)$", Pattern.MULTILINE);
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "^\\*\\*Status\\*\\*: (.+)$", Pattern.MULTILINE);

    /**
     * Parses story metadata from the default story-sequence.md file location.
     * 
     * @return Map of story number to StoryMetadata
     * @throws StorySequenceParserException if file cannot be read or parsed
     */
    public Map<Integer, StoryMetadata> parseStorySequence() throws StorySequenceParserException {
        return parseStorySequence(DEFAULT_STORY_SEQUENCE_FILE);
    }

    /**
     * Parses story metadata from the specified story-sequence.md file path.
     * 
     * @param filePath Path to the story-sequence.md file
     * @return Map of story number to StoryMetadata
     * @throws StorySequenceParserException if file cannot be read or parsed
     */
    public Map<Integer, StoryMetadata> parseStorySequence(String filePath) throws StorySequenceParserException {
        Path path = Paths.get(filePath);

        try {
            String content = Files.readString(path);
            return parseStoryMetadata(content, filePath);
        } catch (NoSuchFileException e) {
            throw new StorySequenceParserException(
                    String.format("Story sequence file not found: %s", filePath), e);
        } catch (IOException e) {
            throw new StorySequenceParserException(
                    String.format("Failed to read story sequence file: %s. Error: %s",
                            filePath, e.getMessage()),
                    e);
        }
    }

    /**
     * Parses story metadata from the file content.
     * 
     * @param content  Content of the story-sequence.md file
     * @param filePath File path for error reporting
     * @return Map of story number to StoryMetadata
     * @throws StorySequenceParserException if parsing fails
     */
    private Map<Integer, StoryMetadata> parseStoryMetadata(String content, String filePath)
            throws StorySequenceParserException {

        Map<Integer, StoryMetadata> storyMap = new HashMap<>();

        // Find all story headers
        Matcher headerMatcher = STORY_HEADER_PATTERN.matcher(content);

        while (headerMatcher.find()) {
            try {
                int storyNumber = Integer.parseInt(headerMatcher.group(1));
                String title = headerMatcher.group(2).trim();

                // Find the section for this story (from current match to next story or end)
                int sectionStart = headerMatcher.start();
                int sectionEnd = content.length();

                // Look for the next story header to determine section end
                Matcher nextHeaderMatcher = STORY_HEADER_PATTERN.matcher(content);
                nextHeaderMatcher.region(headerMatcher.end(), content.length());
                if (nextHeaderMatcher.find()) {
                    sectionEnd = nextHeaderMatcher.start();
                }

                String storySection = content.substring(sectionStart, sectionEnd);

                // Extract URL, domain, and status from the story section
                String url = extractField(storySection, URL_PATTERN, "URL");
                String domain = extractField(storySection, DOMAIN_PATTERN, "Domain");
                String status = extractField(storySection, STATUS_PATTERN, "Status");

                // Validate required fields
                if (url == null || url.trim().isEmpty()) {
                    System.err.printf("Warning: Story #%d in %s is missing URL, skipping%n",
                            storyNumber, filePath);
                    continue;
                }

                // Create StoryMetadata object (note: StorySequenceParser is deprecated, use
                // StoryMetadataParser)
                // For compatibility, we'll mark as ready for both frontend and backend
                StoryMetadata metadata = new StoryMetadata(storyNumber, title, url.trim(), true, true);

                // Check for duplicates
                if (storyMap.containsKey(storyNumber)) {
                    throw new StorySequenceParserException(
                            String.format("Duplicate story number #%d found in %s", storyNumber, filePath));
                }

                storyMap.put(storyNumber, metadata);

            } catch (NumberFormatException e) {
                throw new StorySequenceParserException(
                        String.format("Invalid story number format in %s: %s",
                                filePath, headerMatcher.group(1)));
            }
        }

        if (storyMap.isEmpty()) {
            throw new StorySequenceParserException(
                    String.format("No valid story entries found in %s", filePath));
        }

        return storyMap;
    }

    /**
     * Extracts a field value using the provided regex pattern.
     * 
     * @param content   Content to search in
     * @param pattern   Regex pattern to match
     * @param fieldName Field name for error reporting
     * @return Extracted field value or null if not found
     */
    private String extractField(String content, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Exception thrown when story sequence file cannot be read or parsed.
     */
    public static class StorySequenceParserException extends Exception {
        public StorySequenceParserException(String message) {
            super(message);
        }

        public StorySequenceParserException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}