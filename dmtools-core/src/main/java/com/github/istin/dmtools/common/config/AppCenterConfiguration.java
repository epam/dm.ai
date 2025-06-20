package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for AppCenter settings.
 */
public interface AppCenterConfiguration {
    /**
     * Gets the AppCenter token
     * @return The AppCenter token
     */
    String getAppCenterToken();

    /**
     * Gets the AppCenter organization
     * @return The AppCenter organization
     */
    String getAppCenterOrganization();
} 