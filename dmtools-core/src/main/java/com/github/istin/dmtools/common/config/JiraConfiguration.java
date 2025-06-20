package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Jira-related settings.
 */
public interface JiraConfiguration {
    /**
     * Gets the Jira login/password token for authentication
     * @return The Jira login/password token
     */
    String getJiraLoginPassToken();

    /**
     * Gets the Jira base path URL
     * @return The Jira base path URL
     */
    String getJiraBasePath();

    /**
     * Gets the Jira authentication type
     * @return The Jira authentication type
     */
    String getJiraAuthType();

    /**
     * Checks if the system should wait before performing Jira operations
     * @return true if the system should wait, false otherwise
     */
    boolean isJiraWaitBeforePerform();

    /**
     * Checks if Jira logging is enabled
     * @return true if Jira logging is enabled, false otherwise
     */
    boolean isJiraLoggingEnabled();

    /**
     * Checks if Jira cache should be cleared
     * @return true if Jira cache should be cleared, false otherwise
     */
    boolean isJiraClearCache();

    /**
     * Gets the Jira project for extra fields
     * @return The Jira project for extra fields
     */
    String getJiraExtraFieldsProject();

    /**
     * Gets the extra fields for Jira
     * @return The extra fields for Jira
     */
    String[] getJiraExtraFields();
} 