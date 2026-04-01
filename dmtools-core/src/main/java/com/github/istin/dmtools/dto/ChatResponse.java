// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from the chat API containing the AI's response and execution status")
public class ChatResponse {
    
    @Schema(
        description = "The AI's response content, potentially including tool execution results formatted in markdown",
        example = "Based on the Jira ticket DMC-123, here are the details:\n\n## Tools Used\n- **jira-get-ticket**: Retrieved ticket information\n\n```json\n{\"key\": \"DMC-123\", \"summary\": \"Fix login bug\"}\n```\n\nThe ticket shows that...",
        required = false
    )
    private String content;
    
    @Schema(
        description = "Indicates whether the chat request was processed successfully",
        example = "true",
        required = true
    )
    private boolean success;
    
    @Schema(
        description = "Error message if the request failed (null if successful)",
        example = "AI service temporarily unavailable",
        required = false
    )
    private String error;
    
    public static ChatResponse success(String content) {
        return new ChatResponse(content, true, null);
    }
    
    public static ChatResponse error(String error) {
        return new ChatResponse(null, false, error);
    }
} 