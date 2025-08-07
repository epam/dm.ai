package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicDialAI extends DialAIClient {
    private static String DEFAULT_BASE_PATH;
    private static String DEFAULT_API_KEY;
    private static String DEFAULT_MODEL;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_BASE_PATH = propertyReader.getDialBathPath();
        DEFAULT_API_KEY = propertyReader.getDialIApiKey();
        DEFAULT_MODEL = propertyReader.getDialModel();
    }

    public BasicDialAI() throws IOException {
        this(null);
    }

    public BasicDialAI(ConversationObserver conversationObserver) throws IOException {
        super(DEFAULT_BASE_PATH, DEFAULT_API_KEY, DEFAULT_MODEL, conversationObserver);
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
            conversationObserver
        );
    }
}