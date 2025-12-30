package com.durion.audit;

/**
 * Enumeration of different audit modes supported by the system.
 */
public enum AuditMode {
    /**
     * Perform a complete audit of all processed stories
     */
    FULL_AUDIT,
    
    /**
     * Audit only stories processed within a specific date range
     */
    INCREMENTAL_DATE,
    
    /**
     * Audit only stories within a specific number range
     */
    INCREMENTAL_RANGE,
    
    /**
     * Resume a previously interrupted audit operation
     */
    RESUME_AUDIT
}