package com.github.istin.dmtools.mcp;

import java.util.List;
import java.util.Objects;

/**
 * Definition of an MCP tool including metadata and execution information.
 * Used by the generated tool registry and executor.
 */
public class MCPToolDefinition {
    
    private final String name;
    private final String description;
    private final String integration;
    private final String category;
    private final String className;
    private final String methodName;
    private final String returnType;
    private final List<MCPParameterDefinition> parameters;
    
    public MCPToolDefinition(String name, String description, String integration, String category,
                           String className, String methodName, String returnType, List<MCPParameterDefinition> parameters) {
        this.name = name;
        this.description = description;
        this.integration = integration;
        this.category = category;
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
        this.parameters = parameters;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getIntegration() {
        return integration;
    }
    
    public String getCategory() {
        return category;
    }
    
    public String getClassName() {
        return className;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public String getReturnType() {
        return returnType;
    }
    
    public List<MCPParameterDefinition> getParameters() {
        return parameters;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPToolDefinition that = (MCPToolDefinition) o;
        return Objects.equals(name, that.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
    
    @Override
    public String toString() {
        return "MCPToolDefinition{" +
                "name='" + name + '\'' +
                ", integration='" + integration + '\'' +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                '}';
    }
} 