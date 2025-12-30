package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and parses the processed-issues.txt file containing story issue numbers.
 * Handles validation, error cases, and duplicate detection.
 */
public class ProcessedIssuesReader {
    
    private static final String DEFAULT_PROCESSED_ISSUES_FILE = ".github/orchestration/processed-issues.txt";
    
    /**
     * Reads processed issue numbers from the default file location.
     * 
     * @return List of processed issue numbers
     * @throws ProcessedIssuesReaderException if file cannot be read or parsed
     */
    public List<Integer> readProcessedIssues() throws ProcessedIssuesReaderException {
        return readProcessedIssues(DEFAULT_PROCESSED_ISSUES_FILE);
    }
    
    /**
     * Reads processed issue numbers from the specified file path.
     * 
     * @param filePath Path to the processed-issues.txt file
     * @return List of processed issue numbers
     * @throws ProcessedIssuesReaderException if file cannot be read or parsed
     */
    public List<Integer> readProcessedIssues(String filePath) throws ProcessedIssuesReaderException {
        Path path = Paths.get(filePath);
        
        try {
            List<String> lines = Files.readAllLines(path);
            return parseIssueNumbers(lines, filePath);
        } catch (NoSuchFileException e) {
            throw new ProcessedIssuesReaderException(
                String.format("Processed issues file not found: %s", filePath), e);
        } catch (IOException e) {
            throw new ProcessedIssuesReaderException(
                String.format("Failed to read processed issues file: %s. Error: %s", 
                             filePath, e.getMessage()), e);
        }
    }
    
    /**
     * Parses issue numbers from file lines with validation and duplicate detection.
     * 
     * @param lines Lines from the processed-issues.txt file
     * @param filePath File path for error reporting
     * @return List of valid issue numbers
     * @throws ProcessedIssuesReaderException if parsing fails or duplicates found
     */
    private List<Integer> parseIssueNumbers(List<String> lines, String filePath) 
            throws ProcessedIssuesReaderException {
        
        Set<Integer> seenNumbers = new HashSet<>();
        List<Integer> issueNumbers = new ArrayList<>();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue; // Skip empty lines
            }
            
            try {
                int issueNumber = Integer.parseInt(trimmedLine);
                if (issueNumber <= 0) {
                    throw new ProcessedIssuesReaderException(
                        String.format("Invalid issue number '%d' in %s. Issue numbers must be positive.", 
                                     issueNumber, filePath));
                }
                
                // Check for duplicates
                if (seenNumbers.contains(issueNumber)) {
                    throw new ProcessedIssuesReaderException(
                        String.format("Duplicate issue number '%d' found in %s", 
                                     issueNumber, filePath));
                }
                seenNumbers.add(issueNumber);
                issueNumbers.add(issueNumber);
                
            } catch (NumberFormatException e) {
                throw new ProcessedIssuesReaderException(
                    String.format("Invalid number format '%s' in %s. Expected integer.", 
                                 trimmedLine, filePath));
            }
        }
        
        if (issueNumbers.isEmpty()) {
            throw new ProcessedIssuesReaderException(
                String.format("No valid issue numbers found in %s", filePath));
        }
        
        return issueNumbers;
    }
    
    /**
     * Exception thrown when processed issues file cannot be read or parsed.
     */
    public static class ProcessedIssuesReaderException extends Exception {
        public ProcessedIssuesReaderException(String message) {
            super(message);
        }
        
        public ProcessedIssuesReaderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}