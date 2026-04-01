// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.di.DaggerInstructionGeneratorAgentComponent;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.util.List;

/**
 * Agent for generating instructions based on tracker ticket fields.
 * This agent processes chunks of ticket data and generates clear instructions
 * for specific fields like summary, description, test steps, etc.
 */
public class InstructionGeneratorAgent extends AbstractSimpleAgent<InstructionGeneratorAgent.Params, String> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    public static class Params {
        /**
         * Type of instructions to generate (e.g., "test_cases", "user_story")
         */
        private String instructionType;

        /**
         * List of field names to focus on (e.g., ["summary", "description", "acceptance_criteria"])
         */
        private List<String> targetFields;

        /**
         * The tickets text content (chunked)
         */
        private String ticketsText;

        /**
         * Additional context or rules for instruction generation (optional)
         */
        private String additionalContext;

        /**
         * Platform for which to generate formatting rules (jira, ado, confluence, github, gitlab)
         * Default: jira
         */
        private String platform = "jira";
    }

    public InstructionGeneratorAgent() {
        super("agents/instruction_generator");
        DaggerInstructionGeneratorAgentComponent.create().inject(this);
    }

    @Inject
    public InstructionGeneratorAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/instruction_generator");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        // Return the generated instructions as-is
        // The response should contain the structured instructions for the specified fields
        return response;
    }
}