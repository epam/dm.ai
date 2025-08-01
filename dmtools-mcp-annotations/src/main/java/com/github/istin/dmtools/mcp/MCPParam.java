package com.github.istin.dmtools.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking method parameters as MCP tool parameters.
 * Used to define parameter metadata for MCP tool schema generation.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
public @interface MCPParam {
    
    /**
     * The name of the parameter as it will appear in the MCP tool schema.
     * 
     * @return the parameter name
     */
    String name();
    
    /**
     * Description of the parameter for documentation and user guidance.
     * 
     * @return the parameter description
     */
    String description();
    
    /**
     * Whether this parameter is required. Default is true.
     * 
     * @return true if the parameter is required
     */
    boolean required() default true;
    
    /**
     * Example value for the parameter to help users understand the expected format.
     * 
     * @return example value
     */
    String example() default "";
    
    /**
     * The expected type of the parameter (e.g., "string", "number", "boolean", "array").
     * If not specified, will be inferred from the Java type.
     * 
     * @return the parameter type
     */
    String type() default "";
} 