package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for JavaScript AI client settings.
 */
public interface JSAIConfiguration {
    /**
     * Gets the path to the JavaScript AI script
     * @return The path to the JavaScript AI script
     */
    String getJsScriptPath();

    /**
     * Gets the content of the JavaScript AI script
     * @return The content of the JavaScript AI script
     */
    String getJsScriptContent();

    /**
     * Gets the name of the JavaScript AI client
     * @return The name of the JavaScript AI client
     */
    String getJsClientName();

    /**
     * Gets the default model for the JavaScript AI client
     * @return The default model for the JavaScript AI client
     */
    String getJsDefaultModel();

    /**
     * Gets the base path for the JavaScript AI client
     * @return The base path for the JavaScript AI client
     */
    String getJsBasePath();

    /**
     * Gets the keys for secrets used by the JavaScript AI client
     * @return The keys for secrets used by the JavaScript AI client
     */
    String[] getJsSecretsKeys();
} 