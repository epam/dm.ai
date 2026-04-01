// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResponse {
    
    private Object result;
    private String agentName;
    private boolean success;
    private String error;
    private String executionType; // "agent" or "orchestrator"
    
    public static AgentExecutionResponse success(String agentName, Object result, String executionType) {
        return new AgentExecutionResponse(result, agentName, true, null, executionType);
    }
    
    public static AgentExecutionResponse error(String agentName, String error, String executionType) {
        return new AgentExecutionResponse(null, agentName, false, error, executionType);
    }
} 