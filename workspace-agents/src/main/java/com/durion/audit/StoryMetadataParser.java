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
 * Parses coordination files (frontend-coordination.md and
 * backend-coordination.md)
 * to extract story metadata including titles and URLs.
 * Creates a mapping from story number to story metadata for use in audit
 * operations.
 */
public class StoryMetadataParser {

    private static final String DEFAULT_FRONTEND_COORDINATION_FILE = ".github/orchestration/frontend-coordination.md";
    private static final String DEFAULT_BACKEND_COORDINATION_FILE = ".github/orchestration/backend-coordination.md";

    // Regex pattern for parsing story entries from coordination files
    // Format: - ✅ **Story #273**: [STORY] Security: Audit Trail for Price
    // Overrides, Refunds, and Cancellations
    private static final Pattern STORY_PATTERN = Pattern.compile(
            "^-\\s+✅\\s+\\*\\*Story\\s+#(\\d+)\\*\\*:\\s+\\[STORY\\]\\s+(.+)$", Pattern.MULTILINE);

    /**
     * Parses story metadata from the default coordination files.
     * 
     * @return Map of story number to StoryMetadata
     * @throws StoryMetadataParserException if files cannot be read or parsed
     */
    public Map<Integer, StoryMetadata> parseCoordinationFiles() throws StoryMetadataParserException {
        Map<Integer, StoryMetadata> storyMap = new HashMap<>();

        // Parse frontend coordination file
        Map<Integer, StoryMetadata> frontendStories = parseCoordinationFile(DEFAULT_FRONTEND_COORDINATION_FILE, true);
        storyMap.putAll(frontendStories);

        // Parse backend coordination file to supplement with backend readiness info
        Map<Integer, StoryMetadata> backendStories = parseCoordinationFile(DEFAULT_BACKEND_COORDINATION_FILE, false);

        // Merge backend readiness information into existing stories
        for (Map.Entry<Integer, StoryMetadata> entry : backendStories.entrySet()) {
            Integer storyNumber = entry.getKey();
            if (storyMap.containsKey(storyNumber)) {
                // Story already exists from frontend file, update with backend readiness
                StoryMetadata existing = storyMap.get(storyNumber);
                StoryMetadata updated = new StoryMetadata(
                        existing.getStoryNumber(),
                        existing.getTitle(),
                        existing.getUrl(),
                        existing.isReadyForFrontend(),
                        true // Backend ready
                );
                storyMap.put(storyNumber, updated);
            } else {
                // Story only in backend file, add it
                storyMap.put(storyNumber, entry.getValue());
            }
        }

        return storyMap;
    }

    /**
     * Parses story metadata from a specific coordination file.
     * 
     * @param filePath       Path to the coordination file
     * @param isFrontendFile Whether this is the frontend coordination file
     * @return Map of story number to StoryMetadata
     * @throws StoryMetadataParserException if file cannot be read or parsed
     */
    private Map<Integer, StoryMetadata> parseCoordinationFile(String filePath, boolean isFrontendFile)
            throws StoryMetadataParserException {
        Path path = Paths.get(filePath);

        try {
            String content = Files.readString(path);
            return parseStoryMetadata(content, filePath, isFrontendFile);
        } catch (NoSuchFileException e) {
            throw new StoryMetadataParserException(
                    String.format("Coordination file not found: %s", filePath), e);
        } catch (IOException e) {
            throw new StoryMetadataParserException(
                    String.format("Failed to read coordination file: %s. Error: %s",
                            filePath, e.getMessage()),
                    e);
        }
    }

    /**
     * Parses story metadata from the file content.
     * 
     * @param content        Content of the coordination file
     * @param filePath       File path for error reporting
     * @param isFrontendFile Whether this is the frontend coordination file
     * @return Map of story number to StoryMetadata
     */
    private Map<Integer, StoryMetadata> parseStoryMetadata(String content, String filePath, boolean isFrontendFile) {
        Map<Integer, StoryMetadata> storyMap = new HashMap<>();

        Matcher matcher = STORY_PATTERN.matcher(content);

        while (matcher.find()) {
            try {
                int storyNumber = Integer.parseInt(matcher.group(1));
                String title = matcher.group(2).trim();

                // Construct GitHub issue URL
                String url = String.format("https://github.com/louisburroughs/durion/issues/%d", storyNumber);

                // Create metadata with readiness flags
                StoryMetadata metadata = new StoryMetadata(
                        storyNumber,
                        title,
                        url,
                        isFrontendFile, // readyForFrontend
                        !isFrontendFile // readyForBackend (set true if this is backend file)
                );

                storyMap.put(storyNumber, metadata);
            } catch (NumberFormatException e) {
                // Skip invalid story numbers
                System.err.println("Warning: Invalid story number in " + filePath + ": " + matcher.group(1));
            }
        }

        return storyMap;
    }

    /**
     * Exception thrown when story metadata parsing fails.
     */
    public static class StoryMetadataParserException extends Exception {
        public StoryMetadataParserException(String message) {
            super(message);
        }

        public StoryMetadataParserException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
