package com.durion.audit;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import com.durion.audit.ProcessedIssuesReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Property-based test for Property 1: File parsing consistency
 * **Feature: missing-issues-audit, Property 1: File parsing consistency**
 * **Validates: Requirements 1.1**
 * 
 * For any valid processed-issues.txt file format, the system should successfully 
 * parse and return the correct list of issue numbers.
 */
public class FileParsingConsistencyTest {

    @Property(tries = 100)
    @Label("Property 1: File parsing consistency")
    void processedIssuesReaderShouldParseValidFilesConsistently(
            @ForAll("validIssueNumberList") List<Integer> originalIssueNumbers) throws Exception {
        
        // Given: A temporary file with valid issue numbers
        Path tempFile = Files.createTempFile("test-processed-issues", ".txt");
        try {
            // Write issue numbers to file (one per line)
            String fileContent = originalIssueNumbers.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n"));
            Files.write(tempFile, fileContent.getBytes());
            
            // When: Reading the file with ProcessedIssuesReader
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            List<Integer> parsedIssueNumbers = reader.readProcessedIssues(tempFile.toString());
            
            // Then: The parsed numbers should match the original numbers exactly
            Assertions.assertThat(parsedIssueNumbers)
                    .containsExactlyElementsOf(originalIssueNumbers);
            
            // And: The size should be preserved
            Assertions.assertThat(parsedIssueNumbers.size())
                    .isEqualTo(originalIssueNumbers.size());
            
            // And: All numbers should be positive
            Assertions.assertThat(parsedIssueNumbers)
                    .allMatch(num -> num > 0);
            
        } finally {
            // Clean up temporary file
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Property(tries = 50)
    @Label("Property 1: File parsing handles empty lines consistently")
    void processedIssuesReaderShouldIgnoreEmptyLines(
            @ForAll("validIssueNumberList") List<Integer> originalIssueNumbers) throws Exception {
        
        // Given: A temporary file with valid issue numbers and empty lines
        Path tempFile = Files.createTempFile("test-processed-issues-empty", ".txt");
        try {
            // Write issue numbers with empty lines interspersed
            StringBuilder fileContent = new StringBuilder();
            for (int i = 0; i < originalIssueNumbers.size(); i++) {
                fileContent.append(originalIssueNumbers.get(i)).append("\n");
                // Add empty lines occasionally
                if (i % 3 == 0) {
                    fileContent.append("\n");
                }
            }
            Files.write(tempFile, fileContent.toString().getBytes());
            
            // When: Reading the file with ProcessedIssuesReader
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            List<Integer> parsedIssueNumbers = reader.readProcessedIssues(tempFile.toString());
            
            // Then: The parsed numbers should match the original numbers (empty lines ignored)
            Assertions.assertThat(parsedIssueNumbers)
                    .containsExactlyElementsOf(originalIssueNumbers);
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Property(tries = 30)
    @Label("Property 1: File parsing rejects invalid formats consistently")
    void processedIssuesReaderShouldRejectInvalidFormats(
            @ForAll("invalidFileContent") String invalidContent) throws Exception {
        
        // Given: A temporary file with invalid content
        Path tempFile = Files.createTempFile("test-invalid-processed-issues", ".txt");
        try {
            Files.write(tempFile, invalidContent.getBytes());
            
            // When/Then: Reading the file should throw ProcessedIssuesReaderException
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            Assertions.assertThatThrownBy(() -> reader.readProcessedIssues(tempFile.toString()))
                    .isInstanceOf(ProcessedIssuesReader.ProcessedIssuesReaderException.class);
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Property(tries = 20)
    @Label("Property 1: File parsing detects duplicates consistently")
    void processedIssuesReaderShouldDetectDuplicates(
            @ForAll @IntRange(min = 1, max = 500) int duplicateNumber,
            @ForAll("validIssueNumberList") List<Integer> originalNumbers) throws Exception {
        
        // Given: A list with a duplicate number
        List<Integer> numbersWithDuplicate = originalNumbers.stream()
                .filter(num -> !num.equals(duplicateNumber)) // Remove if already present
                .collect(Collectors.toList());
        numbersWithDuplicate.add(duplicateNumber);
        numbersWithDuplicate.add(duplicateNumber); // Add duplicate
        
        Path tempFile = Files.createTempFile("test-duplicate-processed-issues", ".txt");
        try {
            String fileContent = numbersWithDuplicate.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n"));
            Files.write(tempFile, fileContent.getBytes());
            
            // When/Then: Reading the file should throw ProcessedIssuesReaderException for duplicates
            ProcessedIssuesReader reader = new ProcessedIssuesReader();
            Assertions.assertThatThrownBy(() -> reader.readProcessedIssues(tempFile.toString()))
                    .isInstanceOf(ProcessedIssuesReader.ProcessedIssuesReaderException.class)
                    .hasMessageContaining("Duplicate issue number");
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Provide
    Arbitrary<List<Integer>> validIssueNumberList() {
        return Arbitraries.integers()
                .between(1, 1000)
                .list()
                .ofMinSize(1)
                .ofMaxSize(50)
                .map(list -> list.stream().distinct().collect(Collectors.toList())); // Ensure no duplicates
    }
    
    @Provide
    Arbitrary<String> invalidFileContent() {
        return Arbitraries.oneOf(
                // Non-numeric content
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                // Negative numbers
                Arbitraries.just("-1\n-5\n-10"),
                // Zero
                Arbitraries.just("0\n1\n2"),
                // Mixed valid and invalid
                Arbitraries.just("123\nabc\n456"),
                // Decimal numbers
                Arbitraries.just("123.45\n678"),
                // Empty file
                Arbitraries.just("")
        );
    }
}