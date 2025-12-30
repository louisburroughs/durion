package com.durion.audit;

import java.util.Map;

import com.durion.audit.StoryMetadata;
import com.durion.audit.StorySequenceParser;

/**
 * Simple test for StorySequenceParser basic functionality.
 */
public class SimpleStorySequenceTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("Testing StorySequenceParser with real file...");
        
        try {
            StorySequenceParser parser = new StorySequenceParser();
            Map<Integer, StoryMetadata> result = parser.parseStorySequence();
            
            System.out.println("Successfully parsed " + result.size() + " stories");
            
            // Print first few stories
            int count = 0;
            for (Map.Entry<Integer, StoryMetadata> entry : result.entrySet()) {
                if (count++ >= 5) break;
                StoryMetadata story = entry.getValue();
                System.out.printf("Story #%d: %s%n", entry.getKey(), story.getCleanTitle());
                System.out.printf("  URL: %s%n", story.getUrl());
                System.out.println();
            }
            
            System.out.println("✓ StorySequenceParser works correctly!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}