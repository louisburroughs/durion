package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Manages file output operations for the audit system.
 * 
 * Handles:
 * - Creating missing-issues directory structure automatically
 * - Timestamp-based file naming for reports
 * - Proper file permissions and error handling
 * 
 * Requirements: 2.5 - Report output location consistency
 */
public class FileOutputManager {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String MISSING_ISSUES_DIR = "missing-issues";
    private static final String AUDIT_CACHE_DIR = "audit-cache";
    
    private final String baseOutputDirectory;
    private final AuditLogger logger;
    
    public FileOutputManager(String baseOutputDirectory) {
        this(baseOutputDirectory, new AuditLogger());
    }
    
    public FileOutputManager(String baseOutputDirectory, AuditLogger logger) {
        this.baseOutputDirectory = baseOutputDirectory;
        this.logger = logger;
    }
    
    /**
     * Creates the complete directory structure for audit outputs.
     * Creates both missing-issues and audit-cache directories.
     * 
     * @param timestamp Timestamp for organizing outputs
     * @return Path to the missing-issues directory
     * @throws IOException if directory creation fails
     */
    public Path createOutputDirectoryStructure(LocalDateTime timestamp) throws IOException {
        logger.logProgress("Creating output directory structure", 0, 3);
        
        // Create base output directory
        Path baseDir = Paths.get(baseOutputDirectory);
        createDirectoryWithPermissions(baseDir);
        
        logger.logProgress("Created base directory: " + baseDir, 1, 3);
        
        // Create missing-issues subdirectory
        Path missingIssuesDir = baseDir.resolve(MISSING_ISSUES_DIR);
        createDirectoryWithPermissions(missingIssuesDir);
        
        logger.logProgress("Created missing-issues directory: " + missingIssuesDir, 2, 3);
        
        // Create audit-cache subdirectory for caching
        Path auditCacheDir = baseDir.resolve(AUDIT_CACHE_DIR);
        createDirectoryWithPermissions(auditCacheDir);
        
        logger.logProgress("Created audit-cache directory: " + auditCacheDir, 3, 3);
        
        return missingIssuesDir;
    }
    
    /**
     * Generates a timestamped filename for reports.
     * 
     * @param prefix File prefix (e.g., "missing-frontend", "audit", "summary")
     * @param extension File extension (e.g., "csv", "json", "md")
     * @param timestamp Timestamp for the filename
     * @return Timestamped filename
     */
    public String generateTimestampedFilename(String prefix, String extension, LocalDateTime timestamp) {
        String timestampStr = timestamp.format(TIMESTAMP_FORMAT);
        return String.format("%s-%s.%s", prefix, timestampStr, extension);
    }
    
    /**
     * Creates a complete file path within the missing-issues directory.
     * 
     * @param filename The filename to create path for
     * @param timestamp Timestamp for directory organization
     * @return Complete file path
     * @throws IOException if directory creation fails
     */
    public Path createReportFilePath(String filename, LocalDateTime timestamp) throws IOException {
        Path missingIssuesDir = createOutputDirectoryStructure(timestamp);
        return missingIssuesDir.resolve(filename);
    }
    
    /**
     * Creates a complete file path within the audit-cache directory.
     * 
     * @param filename The filename to create path for
     * @param timestamp Timestamp for directory organization
     * @return Complete file path for cache
     * @throws IOException if directory creation fails
     */
    public Path createCacheFilePath(String filename, LocalDateTime timestamp) throws IOException {
        createOutputDirectoryStructure(timestamp); // Ensure directories exist
        Path baseDir = Paths.get(baseOutputDirectory);
        Path auditCacheDir = baseDir.resolve(AUDIT_CACHE_DIR);
        return auditCacheDir.resolve(filename);
    }
    
    /**
     * Validates that a file path is writable and has proper permissions.
     * 
     * @param filePath Path to validate
     * @throws IOException if path is not writable or has permission issues
     */
    public void validateFileWritePermissions(Path filePath) throws IOException {
        Path parentDir = filePath.getParent();
        
        if (parentDir != null && !Files.exists(parentDir)) {
            throw new IOException("Parent directory does not exist: " + parentDir);
        }
        
        if (Files.exists(filePath) && !Files.isWritable(filePath)) {
            throw new IOException("File is not writable: " + filePath);
        }
        
        if (parentDir != null && !Files.isWritable(parentDir)) {
            throw new IOException("Parent directory is not writable: " + parentDir);
        }
    }
    
    /**
     * Ensures a file exists and is writable, creating it if necessary.
     * 
     * @param filePath Path to the file
     * @throws IOException if file cannot be created or is not writable
     */
    public void ensureFileWritable(Path filePath) throws IOException {
        // Create parent directories if they don't exist
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            createDirectoryWithPermissions(parentDir);
        }
        
        // Create file if it doesn't exist
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
            setFilePermissions(filePath);
        }
        
        // Validate write permissions
        validateFileWritePermissions(filePath);
    }
    
    /**
     * Gets the base output directory path.
     * 
     * @return Base output directory as Path
     */
    public Path getBaseOutputDirectory() {
        return Paths.get(baseOutputDirectory);
    }
    
    /**
     * Gets the missing-issues directory path.
     * 
     * @return Missing-issues directory as Path
     */
    public Path getMissingIssuesDirectory() {
        return Paths.get(baseOutputDirectory).resolve(MISSING_ISSUES_DIR);
    }
    
    /**
     * Gets the audit-cache directory path.
     * 
     * @return Audit-cache directory as Path
     */
    public Path getAuditCacheDirectory() {
        return Paths.get(baseOutputDirectory).resolve(AUDIT_CACHE_DIR);
    }
    
    /**
     * Cleans up old report files based on age.
     * Removes files older than the specified number of days.
     * 
     * @param maxAgeInDays Maximum age of files to keep
     * @throws IOException if cleanup fails
     */
    public void cleanupOldReports(int maxAgeInDays) throws IOException {
        Path missingIssuesDir = getMissingIssuesDirectory();
        
        if (!Files.exists(missingIssuesDir)) {
            return; // Nothing to clean up
        }
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(maxAgeInDays);
        
        Files.list(missingIssuesDir)
            .filter(Files::isRegularFile)
            .filter(path -> {
                try {
                    return Files.getLastModifiedTime(path).toInstant()
                        .isBefore(cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
                } catch (IOException e) {
                    logger.logError("File Cleanup", e, "Failed to check file modification time: " + path);
                    return false;
                }
            })
            .forEach(path -> {
                try {
                    Files.delete(path);
                    logger.logProgress("Deleted old report file: " + path.getFileName(), 0, 1);
                } catch (IOException e) {
                    logger.logError("File Cleanup", e, "Failed to delete old report file: " + path);
                }
            });
    }
    
    /**
     * Creates a directory with appropriate permissions.
     * Sets read/write/execute for owner, read/execute for group and others.
     */
    private void createDirectoryWithPermissions(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            setDirectoryPermissions(directory);
        }
    }
    
    /**
     * Sets appropriate permissions for directories.
     * Uses POSIX permissions if supported, otherwise relies on default permissions.
     */
    private void setDirectoryPermissions(Path directory) throws IOException {
        try {
            // Try to set POSIX permissions (rwxr-xr-x)
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(directory, permissions);
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., Windows)
            // Rely on default permissions
            logger.logProgress("POSIX permissions not supported, using default permissions", 0, 1);
        }
    }
    
    /**
     * Sets appropriate permissions for files.
     * Uses POSIX permissions if supported, otherwise relies on default permissions.
     */
    private void setFilePermissions(Path file) throws IOException {
        try {
            // Try to set POSIX permissions (rw-r--r--)
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., Windows)
            // Rely on default permissions
            logger.logProgress("POSIX permissions not supported, using default permissions", 0, 1);
        }
    }
    
    /**
     * Checks available disk space in the output directory.
     * 
     * @return Available space in bytes
     * @throws IOException if unable to check disk space
     */
    public long getAvailableDiskSpace() throws IOException {
        Path baseDir = Paths.get(baseOutputDirectory);
        if (!Files.exists(baseDir)) {
            createDirectoryWithPermissions(baseDir);
        }
        
        return Files.getFileStore(baseDir).getUsableSpace();
    }
    
    /**
     * Validates that there is sufficient disk space for report generation.
     * 
     * @param estimatedSizeBytes Estimated size of reports to be generated
     * @throws IOException if insufficient disk space
     */
    public void validateDiskSpace(long estimatedSizeBytes) throws IOException {
        long availableSpace = getAvailableDiskSpace();
        long requiredSpace = estimatedSizeBytes + (10 * 1024 * 1024); // Add 10MB buffer
        
        if (availableSpace < requiredSpace) {
            throw new IOException(String.format(
                "Insufficient disk space. Available: %d bytes, Required: %d bytes",
                availableSpace, requiredSpace));
        }
    }
    
    /**
     * Creates a backup of an existing report file before overwriting.
     * 
     * @param filePath Path to the file to backup
     * @throws IOException if backup creation fails
     */
    public void createBackup(Path filePath) throws IOException {
        if (Files.exists(filePath)) {
            String backupName = filePath.getFileName().toString() + ".backup";
            Path backupPath = filePath.getParent().resolve(backupName);
            Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.logProgress("Created backup: " + backupPath, 0, 1);
        }
    }
}