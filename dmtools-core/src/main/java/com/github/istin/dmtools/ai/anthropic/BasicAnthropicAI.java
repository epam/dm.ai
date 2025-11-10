package com.github.istin.dmtools.ai.anthropic;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BasicAnthropicAI extends AnthropicAIClient {
    private static String DEFAULT_BASE_PATH;
    private static String DEFAULT_MODEL;
    private static int DEFAULT_MAX_TOKENS;
    private static Map<String, String> DEFAULT_CUSTOM_HEADERS;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_BASE_PATH = propertyReader.getAnthropicBasePath();
        DEFAULT_MODEL = propertyReader.getAnthropicModel();
        DEFAULT_MAX_TOKENS = propertyReader.getAnthropicMaxTokens();
        DEFAULT_CUSTOM_HEADERS = parseCustomHeaders(
            propertyReader.getAnthropicCustomHeaderNames(),
            propertyReader.getAnthropicCustomHeaderValues()
        );
    }

    public BasicAnthropicAI() throws IOException {
        this(null);
    }

    public BasicAnthropicAI(ConversationObserver conversationObserver) throws IOException {
        super(DEFAULT_BASE_PATH, DEFAULT_MODEL, DEFAULT_MAX_TOKENS, conversationObserver, DEFAULT_CUSTOM_HEADERS);
    }
    
    /**
     * Creates a new BasicAnthropicAI instance with the provided configuration
     * @param conversationObserver The conversation observer
     * @param configuration The application configuration
     * @throws IOException If an I/O error occurs
     */
    public BasicAnthropicAI(ConversationObserver conversationObserver, ApplicationConfiguration configuration) throws IOException {
        super(
            configuration.getAnthropicBasePath(),
            configuration.getAnthropicModel(),
            configuration.getAnthropicMaxTokens(),
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

