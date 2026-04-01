// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.ollama;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BasicOllamaAI extends OllamaAIClient {
    private static String DEFAULT_BASE_PATH;
    private static String DEFAULT_MODEL;
    private static String DEFAULT_API_KEY;
    private static int DEFAULT_NUM_CTX;
    private static int DEFAULT_NUM_PREDICT;
    private static Map<String, String> DEFAULT_CUSTOM_HEADERS;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_BASE_PATH = propertyReader.getOllamaBasePath();
        DEFAULT_MODEL = propertyReader.getOllamaModel();
        DEFAULT_API_KEY = propertyReader.getValue("OLLAMA_API_KEY");
        DEFAULT_NUM_CTX = propertyReader.getOllamaNumCtx();
        DEFAULT_NUM_PREDICT = propertyReader.getOllamaNumPredict();
        DEFAULT_CUSTOM_HEADERS = parseCustomHeaders(
            propertyReader.getOllamaCustomHeaderNames(),
            propertyReader.getOllamaCustomHeaderValues()
        );
    }

    public BasicOllamaAI() throws IOException {
        this(null);
    }

    public BasicOllamaAI(ConversationObserver conversationObserver) throws IOException {
        super(DEFAULT_BASE_PATH, DEFAULT_MODEL, DEFAULT_API_KEY, DEFAULT_NUM_CTX, DEFAULT_NUM_PREDICT, conversationObserver, DEFAULT_CUSTOM_HEADERS);
    }
    
    /**
     * Creates a new BasicOllamaAI instance with the provided configuration
     * @param conversationObserver The conversation observer
     * @param configuration The application configuration
     * @throws IOException If an I/O error occurs
     */
    public BasicOllamaAI(ConversationObserver conversationObserver, ApplicationConfiguration configuration) throws IOException {
        super(
            configuration.getOllamaBasePath(),
            configuration.getOllamaModel(),
            configuration.getOllamaApiKey(),
            configuration.getOllamaNumCtx(),
            configuration.getOllamaNumPredict(),
            conversationObserver,
            DEFAULT_CUSTOM_HEADERS
        );
    }
    
    /**
     * Parses comma-separated header names and values into a Map.
     * If names and values count don't match, returns null.
     * 
     * @param headerNames Comma-separated header names
     * @param headerValues Comma-separated header values (must match names by index)
     * @return Map of header names to values, or null if parsing fails
     */
    private static Map<String, String> parseCustomHeaders(String headerNames, String headerValues) {
        if (headerNames == null || headerNames.trim().isEmpty() || 
            headerValues == null || headerValues.trim().isEmpty()) {
            return null;
        }
        
        String[] names = headerNames.split(",");
        String[] values = headerValues.split(",");
        
        // Trim whitespace from all elements
        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].trim();
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }
        
        if (names.length != values.length) {
            return null;
        }
        
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (!names[i].isEmpty() && !values[i].isEmpty()) {
                headers.put(names[i], values[i]);
            }
        }
        
        return headers.isEmpty() ? null : headers;
    }
}



