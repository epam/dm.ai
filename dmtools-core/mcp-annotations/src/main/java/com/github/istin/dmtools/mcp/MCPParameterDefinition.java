package com.github.istin.dmtools.mcp;

import java.util.Objects;

/**
 * Definition of an MCP tool parameter including metadata and type information.
 * Used for MCP schema generation and parameter validation.
 */
public class MCPParameterDefinition {
    
    private final String name;
    private final String description;
    private final boolean required;
    private final String example;
    private final String type;
    private final String javaType;
    private final int parameterIndex;
    
    public MCPParameterDefinition(String name, String description, boolean required, 
                                String example, String type, String javaType, int parameterIndex) {
        this.name = name;
        this.description = description;
        this.required = required;
        this.example = example;
        this.type = type;
        this.javaType = javaType;
        this.parameterIndex = parameterIndex;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public String getExample() {
        return example;
    }
    
    public String getType() {
        return type;
    }
    
    public String getJavaType() {
        return javaType;
    }
    
    public int getParameterIndex() {
        return parameterIndex;
    }
    
    /**
     * Infer MCP type from Java type if not explicitly specified.
     */
    public String getEffectiveType() {
        if (type != null && !type.isEmpty()) {
            return type;
        }
        return inferMCPType(javaType);
    }
    
    private String inferMCPType(String javaType) {
        if (javaType == null) return "string";
        
        return switch (javaType) {
            case "String", "java.lang.String" -> "string";
            case "int", "Integer", "java.lang.Integer" -> "number";
            case "long", "Long", "java.lang.Long" -> "number";
            case "double", "Double", "java.lang.Double" -> "number";
            case "float", "Float", "java.lang.Float" -> "number";
            case "boolean", "Boolean", "java.lang.Boolean" -> "boolean";
            case "String[]", "java.lang.String[]" -> "array";
            case "List", "java.util.List" -> "array";
            default -> {
                if (javaType.endsWith("[]")) {
                    yield "array";
                } else if (javaType.startsWith("java.util.List") || javaType.startsWith("List")) {
                    yield "array";
                }
                yield "object";
            }
        };
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCPParameterDefinition that = (MCPParameterDefinition) o;
        return Objects.equals(name, that.name) && Objects.equals(javaType, that.javaType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, javaType);
    }
    
    @Override
    public String toString() {
        return "MCPParameterDefinition{" +
                "name='" + name + '\'' +
                ", type='" + getEffectiveType() + '\'' +
                ", required=" + required +
                '}';
    }
} 