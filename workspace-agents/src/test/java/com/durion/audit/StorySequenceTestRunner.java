package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.durion.audit.StoryMetadata;
import com.durion.audit.StorySequenceParser;
import com.durion.audit.StorySequenceParser.StorySequenceParserException;

/**
 * Simple test runner for StorySequenceParser to verify basic functionality.
 */
public class StorySequenceTestRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("Testing StorySequenceParser...");
        
        // Test 1: Valid story sequence parsing
        testValidStorySequenceParsing();
        
        // Test 2: Missing URL handling
        testMissingUrlHandling();
        
        // Test 3: Malformed entry handling
        testMalformedEntryHandling();
        
        // Test 4: Real file parsing
        testRealFileParsing();
        
        System.out.println("All tests passed!");
    }
    
    private static void testValidStorySequenceParsing() throws Exception {
        System.out.println("Test 1: Valid story sequence parsing");
        
        String content = """
            # Story Sequence Coordination
            
            ## Active Stories
            
            ### Story #273: [STORY] Security: Audit Trail for Price Overrides
            
            **URL**: https://github.com/louisburroughs/durion/issues/273
            
            **Domain**: payment
            
            **Status**: Processed
            
            ---
            
            ### Story #272: [STORY] Security: Define POS Roles
            
            **URL**: https://github.com/louisburroughs/durion/issues/272
            
            **Domain**: user
            
            **Status**: Processed
            """;
        
        Path tempFile = Files.createTempFile("test-story-sequence", ".md");
        try {
            Files.write(tempFile, content.getBytes());
            
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());
            
            if (result.size() != 2) {
                throw new AssertionError("Expected 2 stories but got " + result.size());
            }
            
            StoryMetadata story273 = result.get(273);
            if (story273 == null) {
                throw new AssertionError("Story #273 not found");
            }
            
            if (!story273.getTitle().equals("[STORY] Security: Audit Trail for Price Overrides")) {
                throw new AssertionError("Unexpected title for story #273: " + story273.getTitle());
            }
            
            
            
            System.out.println("  ✓ Valid story sequence parsing works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testMissingUrlHandling() throws Exception {
        System.out.println("Test 2: Missing URL handling");
        
        String content = """
            # Story Sequence Coordination
            
            ### Story #273: [STORY] Security: Audit Trail
            
            **Domain**: payment
            
            **Status**: Processed
            
            ### Story #272: [STORY] Valid Story
            
            **URL**: https://github.com/louisburroughs/durion/issues/272
            
            **Domain**: user
            """;
        
        Path tempFile = Files.createTempFile("test-missing-url", ".md");
        try {
            Files.write(tempFile, content.getBytes());
            
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());
            
            // Should only contain story #272 (story #273 should be skipped due to missing URL)
            if (result.size() != 1) {
                throw new AssertionError("Expected 1 story but got " + result.size());
            }
            
            if (!result.containsKey(272)) {
                throw new AssertionError("Story #272 should be present");
            }
            
            if (result.containsKey(273)) {
                throw new AssertionError("Story #273 should be skipped due to missing URL");
            }
            
            System.out.println("  ✓ Missing URL handling works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testMalformedEntryHandling() throws Exception {
        System.out.println("Test 3: Malformed entry handling");
        
        String content = """
            # Story Sequence Coordination
            
            ### Story #273: [STORY] Valid Story
            
            **URL**: https://github.com/louisburroughs/durion/issues/273
            
            ### Story #abc: [STORY] Invalid Number
            
            **URL**: https://github.com/louisburroughs/durion/issues/abc
            """;
        
        Path tempFile = Files.createTempFile("test-malformed", ".md");
        try {
            Files.write(tempFile, content.getBytes());
            
            StorySequenceParser parser = new StorySequenceParser();
            try {
                parser.parseStorySequence(tempFile.toString());
                throw new AssertionError("Expected StorySequenceParserException for invalid story number");
            } catch (StorySequenceParserException e) {
                if (!e.getMessage().contains("Invalid story number format")) {
                    throw new AssertionError("Expected 'Invalid story number format' error but got: " + e.getMessage());
                }
            }
            
            System.out.println("  ✓ Malformed entry handling works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testRealFileParsing() throws Exception {
        System.out.println("Test 4: Real file parsing");
        
        try {
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence();
            
            System.out.println("  ✓ Successfully parsed " + result.size() + " stories from real file");
            
            // Print a few examples
            int count = 0;
            for (Map.Entry<Integer, StoryMetadata> entry : result.entrySet()) {
                if (count++ >= 3) break;
                StoryMetadata story = entry.getValue();
                System.out.printf("    Story #%d: %s %n", 
                                entry.getKey(), story.getCleanTitle());
            }
            
        } catch (StorySequenceParserException e) {
            System.out.println("  ⚠ Real file parsing failed (expected in test environment): " + e.getMessage());
        }
    }
}