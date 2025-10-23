package com.github.istin.dmtools.mcp;

/**
 * Enum defining supported integration types for MCP (Model Context Protocol) tools.
 * Used for MCP tool registry and integration selection.
 */
public enum IntegrationType {
    
    /**
     * Jira integration for issue tracking and project management.
     */
    JIRA("jira"),
    
    /**
     * Confluence integration for document management and collaboration.
     */
    CONFLUENCE("confluence"),
    
    /**
     * Figma integration for design file access and collaboration.
     */
    FIGMA("figma"),
    
    /**
     * Microsoft Teams integration for chat and messaging.
     */
    TEAMS("teams");
    
    private final String value;
    
    IntegrationType(String value) {
        this.value = value;
    }
    
    /**
     * Gets the string value of the integration type.
     * @return the integration type string value
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Returns the string representation of the integration type.
     * @return the integration type string value
     */
    @Override
    public String toString() {
        return value;
    }
    
    /**
     * Gets an IntegrationType from its string value.
     * @param value the string value
     * @return the corresponding IntegrationType
     * @throws IllegalArgumentException if no matching type is found
     */
    public static IntegrationType fromString(String value) {
        for (IntegrationType type : IntegrationType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown integration type: " + value);
    }
} 