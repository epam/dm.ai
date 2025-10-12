package com.github.istin.dmtools.ai.ollama;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicOllamaAI extends OllamaAIClient {
    private static String DEFAULT_BASE_PATH;
    private static String DEFAULT_MODEL;
    private static int DEFAULT_NUM_CTX;
    private static int DEFAULT_NUM_PREDICT;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_BASE_PATH = propertyReader.getOllamaBasePath();
        DEFAULT_MODEL = propertyReader.getOllamaModel();
        DEFAULT_NUM_CTX = propertyReader.getOllamaNumCtx();
        DEFAULT_NUM_PREDICT = propertyReader.getOllamaNumPredict();
    }

    public BasicOllamaAI() throws IOException {
        this(null);
    }

    public BasicOllamaAI(ConversationObserver conversationObserver) throws IOException {
        super(DEFAULT_BASE_PATH, DEFAULT_MODEL, DEFAULT_NUM_CTX, DEFAULT_NUM_PREDICT, conversationObserver);
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
            configuration.getOllamaNumCtx(),
            configuration.getOllamaNumPredict(),
            conversationObserver
        );
    }
}



