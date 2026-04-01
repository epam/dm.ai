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
@Schema(description = "Individual message in a chat conversation")
public class ChatMessage {
    
    @Schema(
        description = "Role of the message sender",
        allowableValues = {"user", "assistant", "system", "model"},
        example = "user",
        required = true
    )
    private String role;
    
    @Schema(
        description = "Content of the message",
        example = "Can you help me analyze the data from ticket DMC-123?",
        required = true
    )
    private String content;
    
    @Schema(
        description = "List of file names associated with this message (for tracking purposes)",
        example = "[\"document.pdf\", \"data.xlsx\"]",
        required = false
    )
    private List<String> fileNames;
} 