// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.AIProvider;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.anthropic.BasicAnthropicAI;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.ai.ollama.BasicOllamaAI;
import com.github.istin.dmtools.bridge.DMToolsBridge;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.prompt.PromptManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AIComponentsModule.
 * The @Provides methods and static factory helpers are exercised directly with a
 * mocked ApplicationConfiguration. All AI client constructors only store
 * configuration values (no network at construction time), so provider selection
 * branches can be verified safely. When a provider client fails to initialize
 * (e.g. missing model), the module catches the exception and falls back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AIComponentsModuleTest {

    private AIComponentsModule module;
    private ConversationObserver observer;

    @Mock
    private ApplicationConfiguration configuration;

    @BeforeEach
    void setUp() {
        module = new AIComponentsModule();
        observer = new ConversationObserver();
        AIProvider.reset();
    }

    @AfterEach
    void tearDown() {
        AIProvider.reset();
    }

    // ------------------------------------------------------------------
    // Simple @Provides methods
    // ------------------------------------------------------------------

    @Test
    void testProvideConversationObserver() {
        ConversationObserver result = module.provideConversationObserver();

        assertNotNull(result);
    }

    @Test
    void testProvideDMToolsBridge() {
        DMToolsBridge bridge = module.provideDMToolsBridge();

        assertNotNull(bridge);
    }

    @Test
    void testProvidePromptTemplateReader() {
        IPromptTemplateReader reader = module.providePromptTemplateReader();

        assertNotNull(reader);
        assertTrue(reader instanceof PromptManager);
    }

    // ------------------------------------------------------------------
    // provideAI - DEFAULT_LLM explicit provider selection
    // ------------------------------------------------------------------

    @Test
    void testProvideAI_DefaultLLMOllama_WithModel() {
        when(configuration.getDefaultLLM()).thenReturn("ollama");
        when(configuration.getOllamaModel()).thenReturn("llama3");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicOllamaAI);
    }

    @Test
    void testProvideAI_DefaultLLMOllama_WithoutModel_FallsBack() {
        when(configuration.getDefaultLLM()).thenReturn("ollama");
        // OLLAMA_MODEL not configured -> warning branch, then auto-detection, then Dial fallback

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMOllama_PlaceholderModel_FallsBack() {
        when(configuration.getDefaultLLM()).thenReturn("ollama");
        when(configuration.getOllamaModel()).thenReturn("$OLLAMA_MODEL");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMDial() {
        when(configuration.getDefaultLLM()).thenReturn("dial");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMGemini_FailsAndFallsBack() {
        when(configuration.getDefaultLLM()).thenReturn("gemini");
        when(configuration.getGeminiApiKey()).thenReturn("test-gemini-key");
        // GEMINI_DEFAULT_MODEL missing -> BasicGeminiAI.create throws -> caught -> fallback

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMAnthropic_WithModel() {
        when(configuration.getDefaultLLM()).thenReturn("anthropic");
        when(configuration.getAnthropicModel()).thenReturn("claude-3-opus");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicAnthropicAI);
    }

    @Test
    void testProvideAI_DefaultLLMAnthropic_WithoutModel_FallsBack() {
        when(configuration.getDefaultLLM()).thenReturn("anthropic");
        // ANTHROPIC_MODEL not configured -> warning branch, then fallback

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMBedrock_Configured() {
        when(configuration.getDefaultLLM()).thenReturn("aws_bedrock");
        stubBedrockWithBearerToken();

        AI ai = module.provideAI(observer, configuration);

        // Either Bedrock client initializes or module falls back - both are valid,
        // the branch itself must be exercised without throwing.
        assertNotNull(ai);
    }

    @Test
    void testProvideAI_DefaultLLMBedrockAlias_NotConfigured_FallsBack() {
        when(configuration.getDefaultLLM()).thenReturn("bedrock");
        // No Bedrock configuration -> warning branch, then fallback

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMOpenAI_Configured() {
        when(configuration.getDefaultLLM()).thenReturn("openai");
        when(configuration.getOpenAIApiKey()).thenReturn("test-openai-key");
        when(configuration.getOpenAIModel()).thenReturn("gpt-4o");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
    }

    @Test
    void testProvideAI_DefaultLLMOpenAI_NotConfigured_FallsBack() {
        when(configuration.getDefaultLLM()).thenReturn("openai");
        // OPENAI_API_KEY / OPENAI_MODEL missing -> warning branch, then fallback

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMUnknown_FallsBackToAutoDetection() {
        when(configuration.getDefaultLLM()).thenReturn("unknown-provider");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_DefaultLLMBlank_FallsBackToAutoDetection() {
        when(configuration.getDefaultLLM()).thenReturn("   ");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    // ------------------------------------------------------------------
    // provideAI - auto-detection order (no DEFAULT_LLM)
    // ------------------------------------------------------------------

    @Test
    void testProvideAI_AutoDetect_Ollama() {
        when(configuration.getOllamaModel()).thenReturn("mistral");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicOllamaAI);
    }

    @Test
    void testProvideAI_AutoDetect_Anthropic() {
        when(configuration.getAnthropicModel()).thenReturn("claude-3-sonnet");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicAnthropicAI);
    }

    @Test
    void testProvideAI_AutoDetect_Bedrock() {
        stubBedrockWithBearerToken();

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
    }

    @Test
    void testProvideAI_AutoDetect_OpenAI() {
        when(configuration.getOpenAIApiKey()).thenReturn("test-openai-key");
        when(configuration.getOpenAIModel()).thenReturn("gpt-4o");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
    }

    @Test
    void testProvideAI_AutoDetect_DialPreferredOverGemini() {
        when(configuration.getDialApiKey()).thenReturn("test-dial-key");
        when(configuration.getGeminiApiKey()).thenReturn("test-gemini-key");

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_AutoDetect_GeminiFails_FallsBack() {
        when(configuration.getGeminiApiKey()).thenReturn("test-gemini-key");
        // No DIAL_API_KEY, no GEMINI_DEFAULT_MODEL -> Gemini init fails -> caught -> fallback

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_JSScriptPath_FailsAndFallsBack() {
        when(configuration.getJsScriptPath()).thenReturn("js/does-not-exist.js");
        when(configuration.getJsClientName()).thenReturn("test-client");
        when(configuration.getJsSecretsKeys()).thenReturn(new String[]{"SECRET_ONE", " SECRET_TWO "});
        when(configuration.getValue("SECRET_ONE")).thenReturn("secret-value");
        // SECRET_TWO resolves to null -> skipped in the secrets loop

        AI ai = module.provideAI(observer, configuration);

        // JSAIClient cannot load a non-existent script -> exception caught -> fallback
        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_FullFallback_ToBasicDial() {
        // Nothing configured at all and no custom AI registered

        AI ai = module.provideAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testProvideAI_CustomAIProvider() {
        AI customAI = mock(AI.class);
        AIProvider.setCustomAI(customAI);

        AI ai = module.provideAI(observer, configuration);

        assertSame(customAI, ai);
    }

    // ------------------------------------------------------------------
    // Static factory helpers
    // ------------------------------------------------------------------

    @Test
    void testCreateAI_DelegatesToProvideAI() {
        when(configuration.getDefaultLLM()).thenReturn("dial");

        AI ai = AIComponentsModule.createAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testCreateOllamaAI_Configured() {
        when(configuration.getOllamaModel()).thenReturn("llama3");

        AI ai = AIComponentsModule.createOllamaAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicOllamaAI);
    }

    @Test
    void testCreateOllamaAI_NotConfigured() {
        assertNull(AIComponentsModule.createOllamaAI(observer, configuration));
    }

    @Test
    void testCreateOllamaAI_PlaceholderModel() {
        when(configuration.getOllamaModel()).thenReturn("$OLLAMA_MODEL");

        assertNull(AIComponentsModule.createOllamaAI(observer, configuration));
    }

    @Test
    void testCreateAnthropicAI_Configured() {
        when(configuration.getAnthropicModel()).thenReturn("claude-3-haiku");

        AI ai = AIComponentsModule.createAnthropicAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicAnthropicAI);
    }

    @Test
    void testCreateAnthropicAI_NotConfigured() {
        assertNull(AIComponentsModule.createAnthropicAI(observer, configuration));
    }

    @Test
    void testCreateGeminiAI_NoConfig() {
        assertNull(AIComponentsModule.createGeminiAI(observer, configuration));
    }

    @Test
    void testCreateGeminiAI_ApiKeyWithoutModel_ReturnsNull() {
        when(configuration.getGeminiApiKey()).thenReturn("test-gemini-key");
        // GEMINI_DEFAULT_MODEL missing -> BasicGeminiAI.create throws -> caught -> null

        assertNull(AIComponentsModule.createGeminiAI(observer, configuration));
    }

    @Test
    void testCreateGeminiAI_VertexConfigIncomplete_ReturnsNull() {
        when(configuration.isGeminiVertexEnabled()).thenReturn(true);
        // Project ID and location missing -> hasVertexConfig false, no API key -> null

        assertNull(AIComponentsModule.createGeminiAI(observer, configuration));
    }

    @Test
    void testCreateGeminiAI_VertexConfigWithoutCredentials_ReturnsNull() {
        when(configuration.isGeminiVertexEnabled()).thenReturn(true);
        when(configuration.getGeminiVertexProjectId()).thenReturn("test-project");
        when(configuration.getGeminiVertexLocation()).thenReturn("us-central1");
        // No credentials and no model -> creation fails -> caught -> null

        assertNull(AIComponentsModule.createGeminiAI(observer, configuration));
    }

    @Test
    void testCreateDialAI() {
        AI ai = AIComponentsModule.createDialAI(observer, configuration);

        assertNotNull(ai);
        assertTrue(ai instanceof BasicDialAI);
    }

    @Test
    void testCreateBedrockAI_NotConfigured() {
        assertNull(AIComponentsModule.createBedrockAI(observer, configuration));
    }

    @Test
    void testCreateBedrockAI_PlaceholderModelId() {
        when(configuration.getBedrockModelId()).thenReturn("$BEDROCK_MODEL_ID");
        when(configuration.getBedrockRegion()).thenReturn("us-east-1");

        assertNull(AIComponentsModule.createBedrockAI(observer, configuration));
    }

    @Test
    void testCreateBedrockAI_ModelWithoutBasePathOrRegion() {
        when(configuration.getBedrockModelId()).thenReturn("anthropic.claude-3-sonnet");
        when(configuration.getBedrockBearerToken()).thenReturn("test-token");

        assertNull(AIComponentsModule.createBedrockAI(observer, configuration));
    }

    @Test
    void testCreateBedrockAI_BearerToken() {
        stubBedrockWithBearerToken();

        assertDoesNotThrow(() -> AIComponentsModule.createBedrockAI(observer, configuration));
    }

    @Test
    void testCreateBedrockAI_IAMKeys() {
        when(configuration.getBedrockModelId()).thenReturn("anthropic.claude-3-sonnet");
        when(configuration.getBedrockBasePath()).thenReturn("https://bedrock.example.com");
        when(configuration.getBedrockAccessKeyId()).thenReturn("AKIA_TEST");
        when(configuration.getBedrockSecretAccessKey()).thenReturn("secret");

        assertDoesNotThrow(() -> AIComponentsModule.createBedrockAI(observer, configuration));
    }

    @Test
    void testCreateBedrockAI_IncompleteIAMKeys_FallsToDefaultCredentials() {
        when(configuration.getBedrockModelId()).thenReturn("anthropic.claude-3-sonnet");
        when(configuration.getBedrockRegion()).thenReturn("us-east-1");
        when(configuration.getBedrockAccessKeyId()).thenReturn("AKIA_TEST");
        // Secret access key missing -> IAM incomplete, but region allows default credentials

        assertDoesNotThrow(() -> AIComponentsModule.createBedrockAI(observer, configuration));
    }

    @Test
    void testCreateOpenAIAI_NotConfigured() {
        assertNull(AIComponentsModule.createOpenAIAI(observer, configuration));
    }

    @Test
    void testCreateOpenAIAI_MissingModel() {
        when(configuration.getOpenAIApiKey()).thenReturn("test-openai-key");

        assertNull(AIComponentsModule.createOpenAIAI(observer, configuration));
    }

    @Test
    void testCreateOpenAIAI_PlaceholderApiKey() {
        when(configuration.getOpenAIApiKey()).thenReturn("$OPENAI_API_KEY");
        when(configuration.getOpenAIModel()).thenReturn("gpt-4o");

        assertNull(AIComponentsModule.createOpenAIAI(observer, configuration));
    }

    @Test
    void testCreateOpenAIAI_Configured() {
        when(configuration.getOpenAIApiKey()).thenReturn("test-openai-key");
        when(configuration.getOpenAIModel()).thenReturn("gpt-4o");

        AI ai = AIComponentsModule.createOpenAIAI(observer, configuration);

        assertNotNull(ai);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubBedrockWithBearerToken() {
        when(configuration.getBedrockModelId()).thenReturn("anthropic.claude-3-sonnet");
        when(configuration.getBedrockBasePath()).thenReturn("https://bedrock.example.com");
        when(configuration.getBedrockBearerToken()).thenReturn("test-bearer-token");
    }
}
