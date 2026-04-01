// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicDialAI extends DialAIClient {
    private static String DEFAULT_BASE_PATH;
    private static String DEFAULT_API_KEY;
    private static String DEFAULT_MODEL;
    private static String DEFAULT_API_VERSION;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_BASE_PATH = propertyReader.getDialBathPath();
        DEFAULT_API_KEY = propertyReader.getDialIApiKey();
        DEFAULT_MODEL = propertyReader.getDialModel();
        DEFAULT_API_VERSION = propertyReader.getDialApiVersion();
    }

    public BasicDialAI() throws IOException {
        this(null);
    }

    public BasicDialAI(ConversationObserver conversationObserver) throws IOException {
        super(DEFAULT_BASE_PATH, DEFAULT_API_KEY, DEFAULT_MODEL, DEFAULT_API_VERSION, conversationObserver);
    }
    
    /**
     * Creates a new BasicDialAI instance with the provided configuration
     * @param conversationObserver The conversation observer
     * @param configuration The application configuration
     * @throws IOException If an I/O error occurs
     */
    public BasicDialAI(ConversationObserver conversationObserver, ApplicationConfiguration configuration) throws IOException {
        super(
            configuration.getDialBathPath(),
            configuration.getDialApiKey(),
            configuration.getDialModel(),
            configuration.getDialApiVersion(),
            conversationObserver
        );
    }
}