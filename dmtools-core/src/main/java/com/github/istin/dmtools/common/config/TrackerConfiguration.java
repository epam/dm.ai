package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for tracker selection settings.
 */
public interface TrackerConfiguration {
    /**
     * Gets the default tracker provider to use (e.g., "jira", "ado", "rally")
     * @return The default tracker provider name
     */
    String getDefaultTracker();
}



