// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentListResponse {
    
    private List<String> agents;
    private List<String> orchestrators;
    private List<AgentInfo> detailedAgents;
    private List<AgentInfo> detailedOrchestrators;
    
    // Factory methods
    public static AgentListResponse simple(List<String> agents, List<String> orchestrators) {
        AgentListResponse response = new AgentListResponse();
        response.setAgents(agents);
        response.setOrchestrators(orchestrators);
        return response;
    }
    
    public static AgentListResponse detailed(List<AgentInfo> agents, List<AgentInfo> orchestrators) {
        AgentListResponse response = new AgentListResponse();
        response.setDetailedAgents(agents);
        response.setDetailedOrchestrators(orchestrators);
        return response;
    }
    
    public boolean isDetailed() {
        return detailedAgents != null || detailedOrchestrators != null;
    }
} 