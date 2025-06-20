package com.github.istin.dmtools.common.ai.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AIPromptConfigTest {

    @Test
    void testAIPromptConfigBuilder() {
        AIPromptConfig config = AIPromptConfig.builder()
                .modelName("gpt-4")
                .modelProvider(AIPromptConfig.ModelProvider.OPENAI)
                .apiKey("sk-1234567890abcdef")
                .promptChunkTokenLimit(8000)
                .promptChunkMaxSingleFileSize(1024L * 1024L) // 1MB
                .promptChunkMaxTotalFilesSize(4L * 1024L * 1024L) // 4MB
                .promptChunkMaxFiles(10)
                .build();

        // Test model configuration
        assertEquals("gpt-4", config.getModelName());
        assertEquals(AIPromptConfig.ModelProvider.OPENAI, config.getModelProvider());
        assertEquals("sk-1234567890abcdef", config.getApiKey());
        
        // Test chunk configuration
        assertEquals(8000, config.getPromptChunkTokenLimit());
        assertEquals(1024L * 1024L, config.getPromptChunkMaxSingleFileSize());
        assertEquals(4L * 1024L * 1024L, config.getPromptChunkMaxTotalFilesSize());
        assertEquals(10, config.getPromptChunkMaxFiles());

        assertTrue(config.isModelConfigured());
        assertTrue(config.isChunkConfigured());
    }

    @Test
    void testIsModelConfigured() {
        // Only model name, missing provider
        AIPromptConfig configOnlyName = AIPromptConfig.builder()
                .modelName("gpt-4")
                .build();
        assertFalse(configOnlyName.isModelConfigured());

        // Only provider, missing name
        AIPromptConfig configOnlyProvider = AIPromptConfig.builder()
                .modelProvider(AIPromptConfig.ModelProvider.OPENAI)
                .build();
        assertFalse(configOnlyProvider.isModelConfigured());

        // Both name and provider
        AIPromptConfig configComplete = AIPromptConfig.builder()
                .modelName("gpt-4")
                .modelProvider(AIPromptConfig.ModelProvider.OPENAI)
                .build();
        assertTrue(configComplete.isModelConfigured());
        
        // With API key
        AIPromptConfig configWithApiKey = AIPromptConfig.builder()
                .modelName("gpt-4")
                .modelProvider(AIPromptConfig.ModelProvider.OPENAI)
                .apiKey("sk-1234567890abcdef")
                .build();
        assertTrue(configWithApiKey.isModelConfigured());
    }

    @Test
    void testIsChunkConfigured() {
        // No chunk configuration
        AIPromptConfig emptyConfig = AIPromptConfig.builder().build();
        assertFalse(emptyConfig.isChunkConfigured());
        
        // With token limit only
        AIPromptConfig tokenLimitConfig = AIPromptConfig.builder()
                .promptChunkTokenLimit(8000)
                .build();
        assertTrue(tokenLimitConfig.isChunkConfigured());
        
        // With max single file size only
        AIPromptConfig maxSingleFileSizeConfig = AIPromptConfig.builder()
                .promptChunkMaxSingleFileSize(1024L * 1024L)
                .build();
        assertTrue(maxSingleFileSizeConfig.isChunkConfigured());
        
        // With max total files size only
        AIPromptConfig maxTotalFilesSizeConfig = AIPromptConfig.builder()
                .promptChunkMaxTotalFilesSize(4L * 1024L * 1024L)
                .build();
        assertTrue(maxTotalFilesSizeConfig.isChunkConfigured());
        
        // With max files only
        AIPromptConfig maxFilesConfig = AIPromptConfig.builder()
                .promptChunkMaxFiles(10)
                .build();
        assertTrue(maxFilesConfig.isChunkConfigured());
        
        // With all chunk configuration
        AIPromptConfig completeChunkConfig = AIPromptConfig.builder()
                .promptChunkTokenLimit(8000)
                .promptChunkMaxSingleFileSize(1024L * 1024L)
                .promptChunkMaxTotalFilesSize(4L * 1024L * 1024L)
                .promptChunkMaxFiles(10)
                .build();
        assertTrue(completeChunkConfig.isChunkConfigured());
    }
} 