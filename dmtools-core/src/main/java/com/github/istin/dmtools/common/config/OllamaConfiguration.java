package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Ollama-specific settings.
 */
public interface OllamaConfiguration {
    /**
     * Gets the Ollama base path URL
     * @return The Ollama base path URL
     */
    String getOllamaBasePath();

    /**
     * Gets the Ollama model name
     * @return The Ollama model name
     */
    String getOllamaModel();

    /**
     * Gets the Ollama context window size
     * @return The context window size in tokens
     */
    int getOllamaNumCtx();

    /**
     * Gets the Ollama prediction token limit
     * @return The prediction token limit
     */
    int getOllamaNumPredict();
}



