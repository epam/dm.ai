// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chat request containing conversation messages and configuration")
public class ChatRequest {
    
    @Schema(
        description = "List of chat messages in the conversation", 
        required = true,
        example = "[{\"role\": \"user\", \"content\": \"Hello, how are you?\", \"fileNames\": null}]"
    )
    private List<ChatMessage> messages;
    
    @Schema(
        description = "AI model to use for the conversation",
        example = "gpt-4",
        required = false
    )
    private String model;
    
    @Schema(
        description = "Optional AI integration UUID for selecting specific AI provider",
        example = "openai-integration-uuid",
        required = false
    )
    private String ai; 
    
    @Schema(
        description = "Optional MCP configuration ID for enabling tool access. When provided, the system can automatically select and execute relevant tools based on conversation context.",
        example = "mcp-config-jira-confluence",
        required = false
    )
    private String mcpConfigId;
} 