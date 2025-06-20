package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.utils.PropertyReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory class for creating different types of configurations.
 */
public class ConfigurationFactory {
    
    private static final Map<String, ApplicationConfiguration> configurationCache = new ConcurrentHashMap<>();
    
    /**
     * Creates a default configuration using the default PropertyReader
     * @return The default configuration
     */
    public static ApplicationConfiguration createDefault() {
        return new PropertyReaderConfiguration();
    }
    
    /**
     * Creates a configuration using a custom config file path
     * @param configFilePath The path to the configuration file
     * @return The configuration
     */
    public static ApplicationConfiguration createFromFile(String configFilePath) {
        PropertyReader propertyReader = new PropertyReader();
        PropertyReader.setConfigFile(configFilePath);
        return new PropertyReaderConfiguration(propertyReader);
    }
    
    /**
     * Creates or retrieves a cached configuration for a specific job
     * @param jobId The ID of the job
     * @param configFilePath The path to the configuration file
     * @return The job-specific configuration
     */
    public static ApplicationConfiguration createForJob(String jobId, String configFilePath) {
        String cacheKey = "job_" + jobId;
        return configurationCache.computeIfAbsent(cacheKey, k -> createFromFile(configFilePath));
    }
    
    /**
     * Creates or retrieves a cached configuration for a specific agent
     * @param agentId The ID of the agent
     * @param configFilePath The path to the configuration file
     * @return The agent-specific configuration
     */
    public static ApplicationConfiguration createForAgent(String agentId, String configFilePath) {
        String cacheKey = "agent_" + agentId;
        return configurationCache.computeIfAbsent(cacheKey, k -> createFromFile(configFilePath));
    }
    
    /**
     * Removes a configuration from the cache
     * @param cacheKey The cache key
     */
    public static void removeFromCache(String cacheKey) {
        configurationCache.remove(cacheKey);
    }
    
    /**
     * Clears the configuration cache
     */
    public static void clearCache() {
        configurationCache.clear();
    }
} 