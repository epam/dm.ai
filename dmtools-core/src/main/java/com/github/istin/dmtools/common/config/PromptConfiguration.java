package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for prompt-related settings.
 */
public interface PromptConfiguration {
    /**
     * Gets the maximum token limit for AI model
     * @return The maximum token limit
     */
    int getPromptChunkTokenLimit();

    /**
     * Gets the maximum size in bytes for a single file
     * @return The maximum file size in bytes
     */
    long getPromptChunkMaxSingleFileSize();

    /**
     * Gets the maximum total size in bytes for all files in a chunk
     * @return The maximum total files size in bytes
     */
    long getPromptChunkMaxTotalFilesSize();

    /**
     * Gets the maximum number of files allowed per chunk
     * @return The maximum files per chunk
     */
    int getPromptChunkMaxFiles();
} 