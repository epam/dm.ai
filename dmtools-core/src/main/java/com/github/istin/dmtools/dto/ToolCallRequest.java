// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class ToolCallRequest {
    private final String toolName;
    private final String reason;
    private final Map<String, Object> arguments;

    @JsonCreator
    public ToolCallRequest(@JsonProperty("toolName") String toolName, 
                          @JsonProperty("reason") String reason,
                          @JsonProperty("arguments") Map<String, Object> arguments) {
        this.toolName = toolName;
        this.reason = reason;
        this.arguments = arguments;
    }

    public String getToolName() {
        return toolName;
    }
    
    public String getReason() {
        return reason;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ToolCallRequest that = (ToolCallRequest) o;
        
        if (toolName != null ? !toolName.equals(that.toolName) : that.toolName != null) return false;
        if (reason != null ? !reason.equals(that.reason) : that.reason != null) return false;
        return arguments != null ? arguments.equals(that.arguments) : that.arguments == null;
    }

    @Override
    public int hashCode() {
        int result = toolName != null ? toolName.hashCode() : 0;
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ToolCallRequest{" +
                "toolName='" + toolName + '\'' +
                ", reason='" + reason + '\'' +
                ", arguments=" + arguments +
                '}';
    }
} 