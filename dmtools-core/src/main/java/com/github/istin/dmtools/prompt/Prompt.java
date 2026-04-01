// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Prompt {

    private IPromptTemplateReader promptTemplateReader;

    private String promptName;

    private PromptContext context;

    public String prepare() {
        try {
            return promptTemplateReader.read(promptName, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}