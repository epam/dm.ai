// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.openai;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;

import java.io.IOException;

public class BasicOpenAI extends OpenAIClient {

    public BasicOpenAI() throws IOException {
        this(null);
    }

    public BasicOpenAI(ConversationObserver conversationObserver) throws IOException {
        this(conversationObserver, new com.github.istin.dmtools.common.config.PropertyReaderConfiguration());
    }

    /**
     * Creates a new BasicOpenAI instance with the provided configuration
     * @param conversationObserver The conversation observer
     * @param configuration The application configuration
     * @throws IOException If an I/O error occurs
     */
    public BasicOpenAI(ConversationObserver conversationObserver, ApplicationConfiguration configuration) throws IOException {
        super(
            configuration.getOpenAIBasePath(),
            configuration.getOpenAIApiKey(),
            configuration.getOpenAIModel(),
            configuration.getOpenAIMaxTokens(),
            configuration.getOpenAITemperature(),
            configuration.getOpenAIMaxTokensParamName(),
            conversationObserver,
            null, // Custom headers not supported in BasicOpenAI for now
            null  // Logger will be created by parent class
        );
    }
}
