// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentInfo {
    
    private String name;
    private String description;
    private String category;
    private AgentType type;
    private List<ParameterInfo> parameters;
    private ReturnInfo returnInfo;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterInfo {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
        private List<String> allowedValues; // для enum параметров
        private String example;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnInfo {
        private String type;
        private String description;
        private Map<String, Object> schema; // JSON schema для сложных объектов
        private String example;
    }
    
    public enum AgentType {
        AGENT, ORCHESTRATOR
    }
    
    // Helper methods for creating agent info
    public static AgentInfo agent(String name, String description, String category) {
        AgentInfo info = new AgentInfo();
        info.setName(name);
        info.setDescription(description);
        info.setCategory(category);
        info.setType(AgentType.AGENT);
        return info;
    }
    
    public static AgentInfo orchestrator(String name, String description, String category) {
        AgentInfo info = new AgentInfo();
        info.setName(name);
        info.setDescription(description);
        info.setCategory(category);
        info.setType(AgentType.ORCHESTRATOR);
        return info;
    }
    
    public AgentInfo withParameters(List<ParameterInfo> parameters) {
        this.parameters = parameters;
        return this;
    }
    
    public AgentInfo withReturnInfo(ReturnInfo returnInfo) {
        this.returnInfo = returnInfo;
        return this;
    }
} 