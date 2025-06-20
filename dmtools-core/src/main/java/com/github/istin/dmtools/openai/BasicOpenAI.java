package com.github.istin.dmtools.openai;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicOpenAI extends OpenAIClient {
    private static String DEFAULT_BASE_PATH;
    private static String DEFAULT_API_KEY;
    private static String DEFAULT_MODEL;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_BASE_PATH = propertyReader.getOpenAIBathPath();
        DEFAULT_API_KEY = propertyReader.getOpenAIApiKey();
        DEFAULT_MODEL = propertyReader.getOpenAIModel();
    }

    public BasicOpenAI() throws IOException {
        this(null);
    }

    public BasicOpenAI(ConversationObserver conversationObserver) throws IOException {
        super(DEFAULT_BASE_PATH, DEFAULT_API_KEY, DEFAULT_MODEL, conversationObserver);
    }
    
    /**
     * Creates a new BasicOpenAI instance with the provided configuration
     * @param conversationObserver The conversation observer
     * @param configuration The application configuration
     * @throws IOException If an I/O error occurs
     */
    public BasicOpenAI(ConversationObserver conversationObserver, ApplicationConfiguration configuration) throws IOException {
        super(
            configuration.getOpenAIBathPath(), 
            configuration.getOpenAIApiKey(), 
            configuration.getOpenAIModel(), 
            conversationObserver
        );
    }
}