package com.github.istin.dmtools.mcp;

/**
 * Enum representing different integration types supported by MCP tools.
 * Used to standardize integration type references across the application.
 */
public enum IntegrationType {
    
    /**
     * Jira integration for issue tracking and project management
     */
    JIRA("jira"),
    
    /**
     * Confluence integration for documentation and knowledge management
     */
    CONFLUENCE("confluence"),
    
    /**
     * GitHub integration for source code management and pull requests
     */
    GITHUB("github"),
    
    /**
     * Bitbucket integration for source code management
     */
    BITBUCKET("bitbucket"),
    
    /**
     * GitLab integration for source code management
     */
    GITLAB("gitlab"),
    
    /**
     * Slack integration for team communication
     */
    SLACK("slack"),
    
    /**
     * Microsoft Teams integration for team communication
     */
    TEAMS("teams"),
    
    /**
     * Email integration for notifications
     */
    EMAIL("email");
    
    private final String value;
    
    IntegrationType(String value) {
        this.value = value;
    }
    
    /**
     * Get the string value of the integration type.
     * 
     * @return the integration type string value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Get the integration type from a string value.
     * 
     * @param value the string value to look up
     * @return the matching IntegrationType or null if not found
     */
    public static IntegrationType fromValue(String value) {
        for (IntegrationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return value;
    }
} 