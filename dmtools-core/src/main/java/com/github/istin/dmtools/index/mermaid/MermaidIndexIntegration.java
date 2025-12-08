package com.github.istin.dmtools.index.mermaid;

import java.util.Date;
import java.util.List;

/**
 * Interface for integrations that support content indexing into Mermaid diagrams.
 * Implementations should retrieve content from their respective systems and
 * call the processor for each matching content item.
 */
public interface MermaidIndexIntegration {
    
    /**
     * Retrieves content from the integration and processes each matching item.
     * 
     * @param includePatterns List of patterns to include (exact match or prefix wildcard like "JAI*")
     * @param excludePatterns List of patterns to exclude (exact match or prefix wildcard)
     * @param processor Processor callback to handle each matching content item
     */
    void getContentForIndex(List<String> includePatterns, List<String> excludePatterns, ContentProcessor processor);
    
    /**
     * Functional interface for processing content items during indexing.
     * Called for each content item that matches the include/exclude patterns.
     */
    @FunctionalInterface
    interface ContentProcessor {
        /**
         * Processes a single content item.
         * 
         * @param pathOrId Integration-specific path or ID for the content
         * @param contentName Name/title of the content
         * @param content The actual content text
         * @param metadata Additional metadata (e.g., space key)
         * @param attachments List of attachment files to process
         * @param lastModified Last modification date of the content
         */
        void process(String pathOrId, String contentName, String content, List<String> metadata, List<java.io.File> attachments, Date lastModified);
    }
}
