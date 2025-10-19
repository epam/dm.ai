package com.github.istin.dmtools.common.kb.model;

/**
 * Processing mode for Knowledge Base operations
 */
public enum KBProcessingMode {
    /**
     * Full processing: analyze input + build structure + generate AI descriptions
     */
    FULL,
    
    /**
     * Process only: analyze input + build structure (skip AI descriptions)
     */
    PROCESS_ONLY,
    
    /**
     * Aggregate only: generate AI descriptions for existing KB structure
     */
    AGGREGATE_ONLY
}

