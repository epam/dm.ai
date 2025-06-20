package com.github.istin.dmtools.common.config;

import java.util.Map;

/**
 * Base interface for all configuration providers in the application.
 * This interface defines the core methods for accessing configuration values.
 */
public interface Configuration {
    /**
     * Gets a configuration value by key
     * @param propertyKey The key of the property to retrieve
     * @return The value of the property, or null if not found
     */
    String getValue(String propertyKey);

    /**
     * Gets a configuration value by key with a default fallback value
     * @param propertyKey The key of the property to retrieve
     * @param defaultValue The default value to return if the property is not found or empty
     * @return The value of the property, or the default value if not found or empty
     */
    String getValue(String propertyKey, String defaultValue);
    
    /**
     * Gets all properties as a map
     * @return A map of all properties
     */
    Map<String, String> getAllProperties();
} 