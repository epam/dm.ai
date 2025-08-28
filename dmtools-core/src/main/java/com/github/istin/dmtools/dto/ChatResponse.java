package com.github.istin.dmtools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    
    private String content;
    private String model;
    private String ai; // AI integration ID used for the response
    private boolean success;
    private String error;
    private ResponseSource source;
    private List<AgentExecutionResponse> agentExecutions; // Results from agents if any were used
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseSource {
        private String type; // "llm", "agent", "orchestrator", "hybrid"
        private String name; // name of agent/orchestrator if applicable
        
        public static ResponseSource llm() {
            return new ResponseSource("llm", null);
        }
        
        public static ResponseSource agent(String agentName) {
            return new ResponseSource("agent", agentName);
        }
        
        public static ResponseSource orchestrator(String orchestratorName) {
            return new ResponseSource("orchestrator", orchestratorName);
        }
        
        public static ResponseSource hybrid() {
            return new ResponseSource("hybrid", null);
        }
    }
    
    public static ChatResponse success(String content, String model) {
        return new ChatResponse(content, model, null, true, null, ResponseSource.llm(), null);
    }
    
    public static ChatResponse success(String content, String model, ResponseSource source, List<AgentExecutionResponse> agentExecutions) {
        return new ChatResponse(content, model, null, true, null, source, agentExecutions);
    }
    
    public static ChatResponse success(String content, String model, String ai) {
        return new ChatResponse(content, model, ai, true, null, ResponseSource.llm(), null);
    }
    
    public static ChatResponse error(String error) {
        return new ChatResponse(error, null, null, false, error, null, null);
    }
} 