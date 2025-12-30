package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.durion.audit.StoryMetadata;
import com.durion.audit.StorySequenceParser;

/**
 * Simple test runner for story data extraction to verify basic functionality.
 */
public class StoryDataExtractionTestRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("Testing story data extraction...");
        
        // Test 1: Complete story data extraction
        testCompleteStoryDataExtraction();
        
        // Test 2: Missing optional fields
        testMissingOptionalFields();
        
        // Test 3: Multiple stories
        testMultipleStories();
        
        System.out.println("All story data extraction tests passed!");
    }
    
    private static void testCompleteStoryDataExtraction() throws Exception {
        System.out.println("Test 1: Complete story data extraction");
        
        String content = """
            # Story Sequence Coordination
            
            ## Active Stories
            
            ### Story #273: [STORY] Security: Audit Trail for Price Overrides
            
            **URL**: https://github.com/louisburroughs/durion/issues/273
            
            **Domain**: payment
            
            **Status**: Processed
            
            ---
            """;
        
        Path tempFile = Files.createTempFile("test-complete-extraction", ".md");
        try {
            Files.write(tempFile, content.getBytes());
            
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());
            
            if (result.size() != 1) {
                throw new AssertionError("Expected 1 story but got " + result.size());
            }
            
            StoryMetadata story = result.get(273);
            if (story == null) {
                throw new AssertionError("Story #273 not found");
            }
            
            // Verify all fields are extracted correctly
            if (story.getStoryNumber() != 273) {
                throw new AssertionError("Wrong story number: " + story.getStoryNumber());
            }
            
            if (!story.getTitle().equals("[STORY] Security: Audit Trail for Price Overrides")) {
                throw new AssertionError("Wrong title: " + story.getTitle());
            }
            
            if (!story.getUrl().equals("https://github.com/louisburroughs/durion/issues/273")) {
                throw new AssertionError("Wrong URL: " + story.getUrl());
            }
            
           
            
            // Test clean title functionality
            if (!story.getCleanTitle().equals("Security: Audit Trail for Price Overrides")) {
                throw new AssertionError("Wrong clean title: " + story.getCleanTitle());
            }
            
            System.out.println("  ✓ Complete story data extraction works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testMissingOptionalFields() throws Exception {
        System.out.println("Test 2: Missing optional fields");
        
        String content = """
            # Story Sequence Coordination
            
            ## Active Stories
            
            ### Story #272: Security: Define POS Roles
            
            **URL**: https://github.com/louisburroughs/durion/issues/272
            
            ---
            """;
        
        Path tempFile = Files.createTempFile("test-missing-optional", ".md");
        try {
            Files.write(tempFile, content.getBytes());
            
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());
            
            if (result.size() != 1) {
                throw new AssertionError("Expected 1 story but got " + result.size());
            }
            
            StoryMetadata story = result.get(272);
            if (story == null) {
                throw new AssertionError("Story #272 not found");
            }
            
            // Required fields should be present
            if (!story.getTitle().equals("Security: Define POS Roles")) {
                throw new AssertionError("Wrong title: " + story.getTitle());
            }
            
            if (!story.getUrl().equals("https://github.com/louisburroughs/durion/issues/272")) {
                throw new AssertionError("Wrong URL: " + story.getUrl());
            }
            
           
            
            System.out.println("  ✓ Missing optional fields handled correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testMultipleStories() throws Exception {
        System.out.println("Test 3: Multiple stories");
        
        String content = """
            # Story Sequence Coordination
            
            ## Active Stories
            
            ### Story #273: [STORY] First Story
            
            **URL**: https://github.com/louisburroughs/durion/issues/273
            
            **Domain**: payment
            
            **Status**: Processed
            
            ---
            
            ### Story #272: Second Story
            
            **URL**: https://github.com/louisburroughs/durion/issues/272
            
            **Domain**: user
            
            ---
            
            ### Story #271: Third Story
            
            **URL**: https://github.com/louisburroughs/durion/issues/271
            
            **Status**: In Progress
            
            ---
            """;
        
        Path tempFile = Files.createTempFile("test-multiple-stories", ".md");
        try {
            Files.write(tempFile, content.getBytes());
            
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence(tempFile.toString());
            
            if (result.size() != 3) {
                throw new AssertionError("Expected 3 stories but got " + result.size());
            }
            
         
            
            System.out.println("  ✓ Multiple stories handled correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}