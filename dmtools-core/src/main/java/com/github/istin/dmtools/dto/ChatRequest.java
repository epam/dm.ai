package com.github.istin.dmtools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    private List<ChatMessage> messages;
    private String model;
    private String ai; // Optional AI integration UUID for selecting specific AI provider
    private AgentToolsConfig agentTools;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentToolsConfig {
        private boolean enabled;
        private List<String> availableAgents; // specific agent names, null means all agents
        private List<String> availableOrchestrators; // specific orchestrator names, null means all orchestrators
        
        public static AgentToolsConfig allAgents() {
            return new AgentToolsConfig(true, null, null);
        }
        
        public static AgentToolsConfig noAgents() {
            return new AgentToolsConfig(false, null, null);
        }
        
        public static AgentToolsConfig specificAgents(List<String> agents, List<String> orchestrators) {
            return new AgentToolsConfig(true, agents, orchestrators);
        }
    }
} 