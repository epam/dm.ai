// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.di.DaggerToolSelectorAgentComponent;
import com.github.istin.dmtools.dto.ToolCallRequest;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class ToolSelectorAgent extends AbstractSimpleAgent<ToolSelectorAgent.Params, List<ToolCallRequest>> {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Params {
        private String userMessage;
        private String availableTools;
    }

    // Default constructor - dependencies will be injected by Dagger
    public ToolSelectorAgent() {
        super("agents/tool-selector");
        DaggerToolSelectorAgentComponent.create().inject(this);
    }

    // Constructor for server-managed mode with injected dependencies
    public ToolSelectorAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/tool-selector");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public List<ToolCallRequest> transformAIResponse(Params params, String aiResponse) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(AIResponseParser.parseResponseAsJSONArray(aiResponse).toString(), new TypeReference<>() {});
    }
} 