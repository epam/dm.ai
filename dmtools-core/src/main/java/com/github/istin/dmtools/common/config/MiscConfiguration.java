package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for miscellaneous settings that don't fit into other categories.
 * This interface extends all specific miscellaneous configuration interfaces.
 */
public interface MiscConfiguration extends 
        RallyConfiguration, 
        AppCenterConfiguration, 
        FigmaConfiguration, 
        FirebaseConfiguration, 
        MetricsConfiguration {
    
    /**
     * Gets the sleep time between requests in milliseconds
     * @return The sleep time between requests
     */
    Long getSleepTimeRequest();
} 