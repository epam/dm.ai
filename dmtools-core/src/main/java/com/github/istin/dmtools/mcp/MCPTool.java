package com.github.istin.dmtools.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking methods as MCP (Model Context Protocol) tools.
 * Methods annotated with this will be automatically registered as available MCP tools
 * and can be called through the MCP protocol.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface MCPTool {
    
    /**
     * The name of the MCP tool. This will be used as the tool identifier in MCP protocol.
     * Should be unique across all tools and follow naming convention: integration_action_resource
     * 
     * @return the tool name
     */
    String name();
    
    /**
     * Description of what this tool does. Used in MCP tool schema generation.
     * 
     * @return the tool description
     */
    String description();
    
    /**
     * The integration type this tool belongs to (e.g., "jira", "confluence", "github").
     * Must match an existing integration type in the system.
     * 
     * @return the integration type
     */
    String integration();
    
    /**
     * Optional category for grouping related tools.
     * 
     * @return the tool category
     */
    String category() default "";
} 