package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.durion.audit.ProcessedIssuesReader;
import com.durion.audit.ProcessedIssuesReader.ProcessedIssuesReaderException;

/**
 * Simple test runner for ProcessedIssuesReader to verify basic functionality.
 */
public class FileParsingTestRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("Testing ProcessedIssuesReader...");
        
        // Test 1: Valid file parsing
        testValidFileParsing();
        
        // Test 2: Empty lines handling
        testEmptyLinesHandling();
        
        // Test 3: Invalid format detection
        testInvalidFormatDetection();
        
        // Test 4: Duplicate detection
        testDuplicateDetection();
        
        System.out.println("All tests passed!");
    }
    
    private static void testValidFileParsing() throws Exception {
        System.out.println("Test 1: Valid file parsing");
        
        Path tempFile = Files.createTempFile("test-valid", ".txt");
        try {
            Files.write(tempFile, "123\n456\n789".getBytes());
            
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            List<Integer> result = reader.readProcessedIssues(tempFile.toString());
            
            List<Integer> expected = Arrays.asList(123, 456, 789);
            if (!result.equals(expected)) {
                throw new AssertionError("Expected " + expected + " but got " + result);
            }
            
            System.out.println("  ✓ Valid file parsing works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testEmptyLinesHandling() throws Exception {
        System.out.println("Test 2: Empty lines handling");
        
        Path tempFile = Files.createTempFile("test-empty-lines", ".txt");
        try {
            Files.write(tempFile, "123\n\n456\n\n\n789\n".getBytes());
            
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            List<Integer> result = reader.readProcessedIssues(tempFile.toString());
            
            List<Integer> expected = Arrays.asList(123, 456, 789);
            if (!result.equals(expected)) {
                throw new AssertionError("Expected " + expected + " but got " + result);
            }
            
            System.out.println("  ✓ Empty lines are handled correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testInvalidFormatDetection() throws Exception {
        System.out.println("Test 3: Invalid format detection");
        
        Path tempFile = Files.createTempFile("test-invalid", ".txt");
        try {
            Files.write(tempFile, "123\nabc\n456".getBytes());
            
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            try {
                reader.readProcessedIssues(tempFile.toString());
                throw new AssertionError("Expected ProcessedIssuesReaderException for invalid format");
            } catch (ProcessedIssuesReaderException e) {
                if (!e.getMessage().contains("Invalid number format")) {
                    throw new AssertionError("Expected 'Invalid number format' error but got: " + e.getMessage());
                }
            }
            
            System.out.println("  ✓ Invalid format detection works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private static void testDuplicateDetection() throws Exception {
        System.out.println("Test 4: Duplicate detection");
        
        Path tempFile = Files.createTempFile("test-duplicates", ".txt");
        try {
            Files.write(tempFile, "123\n456\n123".getBytes());
            
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            try {
                reader.readProcessedIssues(tempFile.toString());
                throw new AssertionError("Expected ProcessedIssuesReaderException for duplicates");
            } catch (ProcessedIssuesReaderException e) {
                if (!e.getMessage().contains("Duplicate issue number")) {
                    throw new AssertionError("Expected 'Duplicate issue number' error but got: " + e.getMessage());
                }
            }
            
            System.out.println("  ✓ Duplicate detection works correctly");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}