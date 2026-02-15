package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Gemini-specific settings.
 */
public interface GeminiConfiguration {
    /**
     * Gets the Gemini API key
     * @return The Gemini API key
     */
    String getGeminiApiKey();

    /**
     * Gets the default Gemini model name
     * @return The default Gemini model name
     */
    String getGeminiDefaultModel();

    /**
     * Gets the Gemini base path URL
     * @return The Gemini base path URL
     */
    String getGeminiBasePath();

    /**
     * Checks if Vertex AI mode is enabled
     * @return true if GEMINI_VERTEX_ENABLED=true
     */
    boolean isGeminiVertexEnabled();

    /**
     * Gets the Vertex AI GCP project ID
     * @return The project ID (GEMINI_VERTEX_PROJECT_ID)
     */
    String getGeminiVertexProjectId();

    /**
     * Gets the Vertex AI GCP location/region
     * @return The location (e.g., "us-central1") (GEMINI_VERTEX_LOCATION)
     */
    String getGeminiVertexLocation();

    /**
     * Gets the Vertex AI service account credentials file path
     * @return The credentials file path (GEMINI_VERTEX_CREDENTIALS_PATH)
     */
    String getGeminiVertexCredentialsPath();

    /**
     * Gets the Vertex AI service account credentials as JSON string
     * @return The credentials JSON (GEMINI_VERTEX_CREDENTIALS_JSON)
     */
    String getGeminiVertexCredentialsJson();

    /**
     * Gets the Vertex AI API version (v1 or v1beta1)
     * @return The API version (GEMINI_VERTEX_API_VERSION), defaults to "v1"
     */
    String getGeminiVertexApiVersion();
} 