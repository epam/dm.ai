// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Coverage tests for {@link InMemoryConfiguration}.
 * The class is a pure in-memory POJO, so no mocks are required; every test
 * exercises real behavior: parsing, default fallbacks, priority chains and
 * credential encoding.
 */
public class InMemoryConfigurationCoverageTest {

    private InMemoryConfiguration config;

    @Before
    public void setUp() {
        config = new InMemoryConfiguration();
    }

    // ------------------------------------------------------------------
    // Core map behavior
    // ------------------------------------------------------------------

    @Test
    public void testDefaultConstructorCreatesEmptyConfiguration() {
        assertTrue(config.getAllProperties().isEmpty());
        assertNull(config.getValue("SOME_UNSET_KEY_12345"));
    }

    @Test
    public void testConstructorWithProperties() {
        Map<String, String> initial = new HashMap<>();
        initial.put("KEY_A", "valueA");
        initial.put("KEY_B", "valueB");
        InMemoryConfiguration cfg = new InMemoryConfiguration(initial);

        assertEquals("valueA", cfg.getValue("KEY_A"));
        assertEquals("valueB", cfg.getValue("KEY_B"));
        assertEquals(2, cfg.getAllProperties().size());
    }

    @Test
    public void testSetPropertyReturnsThisForChaining() {
        InMemoryConfiguration result = config.setProperty("KEY", "value");
        assertSame(config, result);
        assertEquals("value", config.getValue("KEY"));
    }

    @Test
    public void testSetPropertiesReturnsThisForChaining() {
        Map<String, String> props = new HashMap<>();
        props.put("K1", "v1");
        props.put("K2", "v2");
        InMemoryConfiguration result = config.setProperties(props);

        assertSame(config, result);
        assertEquals("v1", config.getValue("K1"));
        assertEquals("v2", config.getValue("K2"));
    }

    @Test
    public void testSetConfigFileDoesNotThrow() {
        // setConfigFile only stores the path internally; just verify it is accepted
        config.setConfigFile("/custom/config.properties");
        config.setConfigFile(null);
        assertTrue(config.getAllProperties().isEmpty());
    }

    @Test
    public void testGetValueReturnsPropertyValue() {
        config.setProperty("MY_KEY", "myValue");
        assertEquals("myValue", config.getValue("MY_KEY"));
    }

    @Test
    public void testGetValueFallsBackToEnvironmentWhenMissing() {
        // PATH is present in virtually every environment; the in-memory map has no such key
        String envPath = System.getenv("PATH");
        assertEquals(envPath, config.getValue("PATH"));
    }

    @Test
    public void testGetValueFallsBackToEnvironmentWhenEmpty() {
        config.setProperty("PATH", "");
        String envPath = System.getenv("PATH");
        assertEquals(envPath, config.getValue("PATH"));
    }

    @Test
    public void testGetValueWithDefaultReturnsValueWhenSet() {
        config.setProperty("MY_KEY", "myValue");
        assertEquals("myValue", config.getValue("MY_KEY", "fallback"));
    }

    @Test
    public void testGetValueWithDefaultReturnsDefaultWhenMissing() {
        assertEquals("fallback", config.getValue("SOME_UNSET_KEY_12345", "fallback"));
    }

    @Test
    public void testGetAllPropertiesReturnsCopy() {
        config.setProperty("KEY", "value");
        Map<String, String> copy = config.getAllProperties();
        copy.put("OTHER", "other");

        assertEquals(1, config.getAllProperties().size());
        assertNull(config.getValue("OTHER"));
    }

    // ------------------------------------------------------------------
    // JiraConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGetJiraLoginPassTokenFromEmailAndApiToken() {
        config.setProperty(PropertyReader.JIRA_EMAIL, " user@example.com ");
        config.setProperty(PropertyReader.JIRA_API_TOKEN, " secret-token ");
        config.setProperty(PropertyReader.JIRA_LOGIN_PASS_TOKEN, "legacy-token");

        String expected = Base64.getEncoder()
                .encodeToString("user@example.com:secret-token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, config.getJiraLoginPassToken());
    }

    @Test
    public void testGetJiraLoginPassTokenFallsBackToLegacyToken() {
        config.setProperty(PropertyReader.JIRA_LOGIN_PASS_TOKEN, "legacy-token");
        assertEquals("legacy-token", config.getJiraLoginPassToken());
    }

    @Test
    public void testGetJiraLoginPassTokenFallsBackWhenEmailBlank() {
        config.setProperty(PropertyReader.JIRA_EMAIL, "   ");
        config.setProperty(PropertyReader.JIRA_API_TOKEN, "secret-token");
        config.setProperty(PropertyReader.JIRA_LOGIN_PASS_TOKEN, "legacy-token");

        assertEquals("legacy-token", config.getJiraLoginPassToken());
    }

    @Test
    public void testGetJiraLoginPassTokenFallsBackWhenApiTokenMissing() {
        config.setProperty(PropertyReader.JIRA_EMAIL, "user@example.com");
        config.setProperty(PropertyReader.JIRA_LOGIN_PASS_TOKEN, "legacy-token");

        assertEquals("legacy-token", config.getJiraLoginPassToken());
    }

    @Test
    public void testJiraSimpleGetters() {
        config.setProperty(PropertyReader.JIRA_EMAIL, "user@example.com");
        config.setProperty(PropertyReader.JIRA_API_TOKEN, "token");
        config.setProperty(PropertyReader.JIRA_BASE_PATH, "https://jira.example.com");
        config.setProperty(PropertyReader.JIRA_AUTH_TYPE, "Basic");
        config.setProperty(PropertyReader.JIRA_EXTRA_FIELDS_PROJECT, "PROJ");

        assertEquals("user@example.com", config.getJiraEmail());
        assertEquals("token", config.getJiraApiToken());
        assertEquals("https://jira.example.com", config.getJiraBasePath());
        assertEquals("Basic", config.getJiraAuthType());
        assertEquals("PROJ", config.getJiraExtraFieldsProject());
    }

    @Test
    public void testJiraBooleanFlags() {
        assertFalse(config.isJiraWaitBeforePerform());
        assertFalse(config.isJiraLoggingEnabled());
        assertFalse(config.isJiraClearCache());

        config.setProperty(PropertyReader.JIRA_WAIT_BEFORE_PERFORM, "true");
        config.setProperty(PropertyReader.JIRA_LOGGING_ENABLED, "TRUE");
        config.setProperty(PropertyReader.JIRA_CLEAR_CACHE, "false");

        assertTrue(config.isJiraWaitBeforePerform());
        assertTrue(config.isJiraLoggingEnabled());
        assertFalse(config.isJiraClearCache());
    }

    @Test
    public void testGetJiraExtraFieldsNullWhenUnset() {
        assertNull(config.getJiraExtraFields());
    }

    @Test
    public void testGetJiraExtraFieldsSplitsOnComma() {
        config.setProperty(PropertyReader.JIRA_EXTRA_FIELDS, "field1,field2,field3");
        assertArrayEquals(new String[]{"field1", "field2", "field3"}, config.getJiraExtraFields());
    }

    // ------------------------------------------------------------------
    // AIConfiguration (Dial + tracker)
    // ------------------------------------------------------------------

    @Test
    public void testDialGetters() {
        config.setProperty("DIAL_BATH_PATH", "https://dial.example.com");
        config.setProperty("DIAL_API_KEY", "dial-key");
        config.setProperty("DIAL_MODEL", "gpt-4");
        config.setProperty("DIAL_API_VERSION", "2024-01-01");

        assertEquals("https://dial.example.com", config.getDialBathPath());
        assertEquals("dial-key", config.getDialApiKey());
        assertEquals("gpt-4", config.getDialModel());
        assertEquals("2024-01-01", config.getDialApiVersion());
    }

    @Test
    public void testTrackerModelGetters() {
        config.setProperty("CODE_AI_MODEL", "code-model");
        config.setProperty("TEST_AI_MODEL", "test-model");
        config.setProperty("DEFAULT_LLM", "default-llm");
        config.setProperty("DEFAULT_TRACKER", "jira");

        assertEquals("code-model", config.getCodeAIModel());
        assertEquals("test-model", config.getTestAIModel());
        assertEquals("default-llm", config.getDefaultLLM());
        assertEquals("jira", config.getDefaultTracker());
    }

    @Test
    public void testGetAiRetryAmount() {
        assertEquals(3, config.getAiRetryAmount());

        config.setProperty("AI_RETRY_AMOUNT", "   ");
        assertEquals(3, config.getAiRetryAmount());

        config.setProperty("AI_RETRY_AMOUNT", "7");
        assertEquals(7, config.getAiRetryAmount());

        config.setProperty("AI_RETRY_AMOUNT", "not-a-number");
        assertEquals(3, config.getAiRetryAmount());
    }

    @Test
    public void testGetAiRetryDelayStep() {
        assertEquals(20000L, config.getAiRetryDelayStep());

        config.setProperty("AI_RETRY_DELAY_STEP", "  ");
        assertEquals(20000L, config.getAiRetryDelayStep());

        config.setProperty("AI_RETRY_DELAY_STEP", "5000");
        assertEquals(5000L, config.getAiRetryDelayStep());

        config.setProperty("AI_RETRY_DELAY_STEP", "abc");
        assertEquals(20000L, config.getAiRetryDelayStep());
    }

    @Test
    public void testGetPromptChunkTokenLimit() {
        assertEquals(50000, config.getPromptChunkTokenLimit());

        config.setProperty("PROMPT_CHUNK_TOKEN_LIMIT", "");
        assertEquals(50000, config.getPromptChunkTokenLimit());

        config.setProperty("PROMPT_CHUNK_TOKEN_LIMIT", "100000");
        assertEquals(100000, config.getPromptChunkTokenLimit());

        config.setProperty("PROMPT_CHUNK_TOKEN_LIMIT", "oops");
        assertEquals(50000, config.getPromptChunkTokenLimit());
    }

    @Test
    public void testGetPromptChunkMaxSingleFileSize() {
        assertEquals(4L * 1024 * 1024, config.getPromptChunkMaxSingleFileSize());

        config.setProperty("PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB", "10");
        assertEquals(10L * 1024 * 1024, config.getPromptChunkMaxSingleFileSize());

        config.setProperty("PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB", "bad");
        assertEquals(4L * 1024 * 1024, config.getPromptChunkMaxSingleFileSize());
    }

    @Test
    public void testGetPromptChunkMaxTotalFilesSize() {
        assertEquals(4L * 1024 * 1024, config.getPromptChunkMaxTotalFilesSize());

        config.setProperty("PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE_MB", "20");
        assertEquals(20L * 1024 * 1024, config.getPromptChunkMaxTotalFilesSize());

        config.setProperty("PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE_MB", "bad");
        assertEquals(4L * 1024 * 1024, config.getPromptChunkMaxTotalFilesSize());
    }

    @Test
    public void testGetPromptChunkMaxFiles() {
        assertEquals(10, config.getPromptChunkMaxFiles());

        config.setProperty("PROMPT_CHUNK_MAX_FILES", "25");
        assertEquals(25, config.getPromptChunkMaxFiles());

        config.setProperty("PROMPT_CHUNK_MAX_FILES", "bad");
        assertEquals(10, config.getPromptChunkMaxFiles());
    }

    @Test
    public void testGetAIAttachmentMaxSizeBytes() {
        assertEquals(0L, config.getAIAttachmentMaxSizeBytes());

        config.setProperty("AI_ATTACHMENT_MAX_SIZE_MB", " 5 ");
        assertEquals(5L * 1024 * 1024, config.getAIAttachmentMaxSizeBytes());

        config.setProperty("AI_ATTACHMENT_MAX_SIZE_MB", "bad");
        assertEquals(0L, config.getAIAttachmentMaxSizeBytes());
    }

    @Test
    public void testGetAIAttachmentAllowedExtensionsEmptyWhenUnset() {
        assertTrue(config.getAIAttachmentAllowedExtensions().isEmpty());

        config.setProperty("AI_ATTACHMENT_ALLOWED_EXTENSIONS", "   ");
        assertTrue(config.getAIAttachmentAllowedExtensions().isEmpty());
    }

    @Test
    public void testGetAIAttachmentAllowedExtensionsParsing() {
        config.setProperty("AI_ATTACHMENT_ALLOWED_EXTENSIONS", " PNG ,Jpg,, pdf ");
        Set<String> result = config.getAIAttachmentAllowedExtensions();

        assertEquals(3, result.size());
        assertTrue(result.contains("png"));
        assertTrue(result.contains("jpg"));
        assertTrue(result.contains("pdf"));
    }

    // ------------------------------------------------------------------
    // GeminiConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGeminiApiKey() {
        config.setProperty("GEMINI_API_KEY", "gemini-key");
        assertEquals("gemini-key", config.getGeminiApiKey());
    }

    @Test
    public void testGetGeminiDefaultModelPrefersGeminiModel() {
        config.setProperty("GEMINI_MODEL", "gemini-1.5-pro");
        config.setProperty("GEMINI_DEFAULT_MODEL", "gemini-1.0");

        assertEquals("gemini-1.5-pro", config.getGeminiDefaultModel());
    }

    @Test
    public void testGetGeminiDefaultModelFallsBackToLegacyKey() {
        config.setProperty("GEMINI_DEFAULT_MODEL", "gemini-1.0");
        assertEquals("gemini-1.0", config.getGeminiDefaultModel());
    }

    @Test
    public void testGetGeminiDefaultModelSkipsUnresolvedPlaceholder() {
        config.setProperty("GEMINI_MODEL", "${GEMINI_MODEL}");
        config.setProperty("GEMINI_DEFAULT_MODEL", "gemini-1.0");

        assertEquals("gemini-1.0", config.getGeminiDefaultModel());
    }

    @Test
    public void testGetGeminiDefaultModelNullWhenAllPlaceholders() {
        config.setProperty("GEMINI_MODEL", "${GEMINI_MODEL}");
        config.setProperty("GEMINI_DEFAULT_MODEL", "$GEMINI_DEFAULT_MODEL");

        assertNull(config.getGeminiDefaultModel());
    }

    @Test
    public void testGetGeminiDefaultModelNullWhenUnset() {
        assertNull(config.getGeminiDefaultModel());
    }

    @Test
    public void testGetGeminiBasePathDefaultAndOverride() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models", config.getGeminiBasePath());

        config.setProperty("GEMINI_BASE_PATH", "https://custom.example.com");
        assertEquals("https://custom.example.com", config.getGeminiBasePath());
    }

    @Test
    public void testIsGeminiVertexEnabled() {
        assertFalse(config.isGeminiVertexEnabled());

        config.setProperty("GEMINI_VERTEX_ENABLED", "TRUE");
        assertTrue(config.isGeminiVertexEnabled());

        config.setProperty("GEMINI_VERTEX_ENABLED", "yes");
        assertFalse(config.isGeminiVertexEnabled());
    }

    @Test
    public void testGeminiVertexGetters() {
        config.setProperty("GEMINI_VERTEX_PROJECT_ID", "proj-1");
        config.setProperty("GEMINI_VERTEX_LOCATION", "us-central1");
        config.setProperty("GEMINI_VERTEX_CREDENTIALS_PATH", "/tmp/creds.json");
        config.setProperty("GEMINI_VERTEX_CREDENTIALS_JSON", "{}");

        assertEquals("proj-1", config.getGeminiVertexProjectId());
        assertEquals("us-central1", config.getGeminiVertexLocation());
        assertEquals("/tmp/creds.json", config.getGeminiVertexCredentialsPath());
        assertEquals("{}", config.getGeminiVertexCredentialsJson());
    }

    @Test
    public void testGetGeminiVertexApiVersionDefaultAndOverride() {
        assertEquals("v1", config.getGeminiVertexApiVersion());

        config.setProperty("GEMINI_VERTEX_API_VERSION", "v1beta");
        assertEquals("v1beta", config.getGeminiVertexApiVersion());
    }

    // ------------------------------------------------------------------
    // OllamaConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGetOllamaBasePathDefaultAndOverride() {
        assertEquals("http://localhost:11434", config.getOllamaBasePath());

        config.setProperty("OLLAMA_BASE_PATH", "http://ollama:11434");
        assertEquals("http://ollama:11434", config.getOllamaBasePath());
    }

    @Test
    public void testOllamaSimpleGetters() {
        config.setProperty("OLLAMA_MODEL", "llama3");
        config.setProperty("OLLAMA_API_KEY", "ollama-key");

        assertEquals("llama3", config.getOllamaModel());
        assertEquals("ollama-key", config.getOllamaApiKey());
    }

    @Test
    public void testGetOllamaNumCtx() {
        assertEquals(16384, config.getOllamaNumCtx());

        config.setProperty("OLLAMA_NUM_CTX", " 8192 ");
        assertEquals(8192, config.getOllamaNumCtx());

        config.setProperty("OLLAMA_NUM_CTX", "bad");
        assertEquals(16384, config.getOllamaNumCtx());
    }

    @Test
    public void testGetOllamaNumPredict() {
        assertEquals(-1, config.getOllamaNumPredict());

        config.setProperty("OLLAMA_NUM_PREDICT", " 512 ");
        assertEquals(512, config.getOllamaNumPredict());

        config.setProperty("OLLAMA_NUM_PREDICT", "bad");
        assertEquals(-1, config.getOllamaNumPredict());
    }

    // ------------------------------------------------------------------
    // AnthropicConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGetAnthropicBasePathDefaultAndOverride() {
        assertEquals("https://api.anthropic.com/v1/messages", config.getAnthropicBasePath());

        config.setProperty("ANTHROPIC_BASE_PATH", "https://custom.anthropic.com");
        assertEquals("https://custom.anthropic.com", config.getAnthropicBasePath());
    }

    @Test
    public void testGetAnthropicModel() {
        config.setProperty("ANTHROPIC_MODEL", "claude-3");
        assertEquals("claude-3", config.getAnthropicModel());
    }

    @Test
    public void testGetAnthropicMaxTokens() {
        assertEquals(4096, config.getAnthropicMaxTokens());

        config.setProperty("ANTHROPIC_MAX_TOKENS", " 8192 ");
        assertEquals(8192, config.getAnthropicMaxTokens());

        config.setProperty("ANTHROPIC_MAX_TOKENS", "bad");
        assertEquals(4096, config.getAnthropicMaxTokens());
    }

    @Test
    public void testAnthropicCustomHeaders() {
        config.setProperty(PropertyReader.ANTHROPIC_CUSTOM_HEADER_NAMES, "X-A,X-B");
        config.setProperty(PropertyReader.ANTHROPIC_CUSTOM_HEADER_VALUES, "1,2");

        assertEquals("X-A,X-B", config.getAnthropicCustomHeaderNames());
        assertEquals("1,2", config.getAnthropicCustomHeaderValues());
    }

    // ------------------------------------------------------------------
    // BedrockConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGetBedrockBasePathExplicitValue() {
        config.setProperty("BEDROCK_BASE_PATH", "https://custom-bedrock.com");
        config.setProperty("BEDROCK_REGION", "us-east-1");

        assertEquals("https://custom-bedrock.com", config.getBedrockBasePath());
    }

    @Test
    public void testGetBedrockBasePathBuiltFromRegion() {
        config.setProperty("BEDROCK_REGION", "eu-west-1");
        assertEquals("https://bedrock-runtime.eu-west-1.amazonaws.com", config.getBedrockBasePath());
    }

    @Test
    public void testGetBedrockBasePathNullWhenNothingSet() {
        assertNull(config.getBedrockBasePath());
    }

    @Test
    public void testBedrockSimpleGetters() {
        config.setProperty("BEDROCK_REGION", "us-west-2");
        config.setProperty("BEDROCK_MODEL_ID", "anthropic.claude-v2");
        config.setProperty("BEDROCK_ACCESS_KEY_ID", "AKID");
        config.setProperty("BEDROCK_SECRET_ACCESS_KEY", "SECRET");
        config.setProperty("BEDROCK_SESSION_TOKEN", "SESSION");

        assertEquals("us-west-2", config.getBedrockRegion());
        assertEquals("anthropic.claude-v2", config.getBedrockModelId());
        assertEquals("AKID", config.getBedrockAccessKeyId());
        assertEquals("SECRET", config.getBedrockSecretAccessKey());
        assertEquals("SESSION", config.getBedrockSessionToken());
    }

    @Test
    public void testGetBedrockBearerTokenPrefersAwsBearerToken() {
        config.setProperty("AWS_BEARER_TOKEN_BEDROCK", "aws-token");
        config.setProperty("BEDROCK_BEARER_TOKEN", "bedrock-token");

        assertEquals("aws-token", config.getBedrockBearerToken());
    }

    @Test
    public void testGetBedrockBearerTokenSkipsPlaceholderAndFallsBack() {
        config.setProperty("AWS_BEARER_TOKEN_BEDROCK", "${AWS_BEARER_TOKEN_BEDROCK}");
        config.setProperty("BEDROCK_BEARER_TOKEN", "bedrock-token");

        assertEquals("bedrock-token", config.getBedrockBearerToken());
    }

    @Test
    public void testGetBedrockBearerTokenFallsBackWhenAwsTokenBlank() {
        config.setProperty("AWS_BEARER_TOKEN_BEDROCK", "  ");
        config.setProperty("BEDROCK_BEARER_TOKEN", "bedrock-token");

        // blank value falls back to env lookup in getValue, which is null here
        assertEquals("bedrock-token", config.getBedrockBearerToken());
    }

    @Test
    public void testGetBedrockMaxTokens() {
        assertEquals(4096, config.getBedrockMaxTokens());

        config.setProperty("BEDROCK_MAX_TOKENS", " 2048 ");
        assertEquals(2048, config.getBedrockMaxTokens());

        config.setProperty("BEDROCK_MAX_TOKENS", "0");
        assertEquals(4096, config.getBedrockMaxTokens());

        config.setProperty("BEDROCK_MAX_TOKENS", "-5");
        assertEquals(4096, config.getBedrockMaxTokens());

        config.setProperty("BEDROCK_MAX_TOKENS", "bad");
        assertEquals(4096, config.getBedrockMaxTokens());
    }

    @Test
    public void testGetBedrockTemperature() {
        assertEquals(1.0, config.getBedrockTemperature(), 0.0001);

        config.setProperty("BEDROCK_TEMPERATURE", "0.5");
        assertEquals(0.5, config.getBedrockTemperature(), 0.0001);

        config.setProperty("BEDROCK_TEMPERATURE", "-0.1");
        assertEquals(1.0, config.getBedrockTemperature(), 0.0001);

        config.setProperty("BEDROCK_TEMPERATURE", "1.5");
        assertEquals(1.0, config.getBedrockTemperature(), 0.0001);

        config.setProperty("BEDROCK_TEMPERATURE", "bad");
        assertEquals(1.0, config.getBedrockTemperature(), 0.0001);
    }

    // ------------------------------------------------------------------
    // OpenAIConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testOpenAISimpleGetters() {
        config.setProperty("OPENAI_API_KEY", "openai-key");
        config.setProperty("OPENAI_MODEL", "gpt-4o");

        assertEquals("openai-key", config.getOpenAIApiKey());
        assertEquals("gpt-4o", config.getOpenAIModel());
    }

    @Test
    public void testGetOpenAIBasePathDefaultAndOverride() {
        assertEquals("https://api.openai.com/v1/chat/completions", config.getOpenAIBasePath());

        config.setProperty("OPENAI_BASE_PATH", "https://custom-openai.com");
        assertEquals("https://custom-openai.com", config.getOpenAIBasePath());
    }

    @Test
    public void testGetOpenAIMaxTokens() {
        assertEquals(4096, config.getOpenAIMaxTokens());

        config.setProperty("OPENAI_MAX_TOKENS", " 1000 ");
        assertEquals(1000, config.getOpenAIMaxTokens());

        config.setProperty("OPENAI_MAX_TOKENS", "0");
        assertEquals(4096, config.getOpenAIMaxTokens());

        config.setProperty("OPENAI_MAX_TOKENS", "bad");
        assertEquals(4096, config.getOpenAIMaxTokens());
    }

    @Test
    public void testGetOpenAITemperature() {
        assertEquals(-1.0, config.getOpenAITemperature(), 0.0001);

        config.setProperty("OPENAI_TEMPERATURE", "0.7");
        assertEquals(0.7, config.getOpenAITemperature(), 0.0001);

        config.setProperty("OPENAI_TEMPERATURE", "-0.5");
        assertEquals(-0.5, config.getOpenAITemperature(), 0.0001);

        config.setProperty("OPENAI_TEMPERATURE", "3.0");
        assertEquals(2.0, config.getOpenAITemperature(), 0.0001);

        config.setProperty("OPENAI_TEMPERATURE", "bad");
        assertEquals(-1.0, config.getOpenAITemperature(), 0.0001);
    }

    @Test
    public void testGetOpenAIMaxTokensParamNameDefaultAndOverride() {
        assertEquals("max_completion_tokens", config.getOpenAIMaxTokensParamName());

        config.setProperty("OPENAI_MAX_TOKENS_PARAM_NAME", "max_tokens");
        assertEquals("max_tokens", config.getOpenAIMaxTokensParamName());
    }

    // ------------------------------------------------------------------
    // JSAIConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testJsAISimpleGetters() {
        config.setProperty("JSAI_SCRIPT_PATH", "/scripts/ai.js");
        config.setProperty("JSAI_SCRIPT_CONTENT", "console.log('hi');");
        config.setProperty("JSAI_DEFAULT_MODEL", "js-model");
        config.setProperty("JSAI_BASE_PATH", "https://jsai.example.com");

        assertEquals("/scripts/ai.js", config.getJsScriptPath());
        assertEquals("console.log('hi');", config.getJsScriptContent());
        assertEquals("js-model", config.getJsDefaultModel());
        assertEquals("https://jsai.example.com", config.getJsBasePath());
    }

    @Test
    public void testGetJsClientNameDefaultAndOverride() {
        assertEquals("JSAIClientFromProperties", config.getJsClientName());

        config.setProperty("JSAI_CLIENT_NAME", "   ");
        assertEquals("JSAIClientFromProperties", config.getJsClientName());

        config.setProperty("JSAI_CLIENT_NAME", "MyClient");
        assertEquals("MyClient", config.getJsClientName());
    }

    @Test
    public void testGetJsSecretsKeys() {
        assertNull(config.getJsSecretsKeys());

        config.setProperty("JSAI_SECRETS_KEYS", "  ");
        assertNull(config.getJsSecretsKeys());

        config.setProperty("JSAI_SECRETS_KEYS", "KEY1,KEY2");
        assertArrayEquals(new String[]{"KEY1", "KEY2"}, config.getJsSecretsKeys());
    }

    // ------------------------------------------------------------------
    // SourceControlConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGithubGetters() {
        config.setProperty("SOURCE_GITHUB_TOKEN", "gh-token");
        config.setProperty("SOURCE_GITHUB_WORKSPACE", "org");
        config.setProperty("SOURCE_GITHUB_REPOSITORY", "repo");
        config.setProperty("SOURCE_GITHUB_BRANCH", "main");
        config.setProperty("SOURCE_GITHUB_BASE_PATH", "https://api.github.com");

        assertEquals("gh-token", config.getGithubToken());
        assertEquals("org", config.getGithubWorkspace());
        assertEquals("repo", config.getGithubRepository());
        assertEquals("main", config.getGithubBranch());
        assertEquals("https://api.github.com", config.getGithubBasePath());
    }

    @Test
    public void testGitLabGetters() {
        config.setProperty("GITLAB_TOKEN", "gl-token");
        config.setProperty("GITLAB_WORKSPACE", "group");
        config.setProperty("GITLAB_REPOSITORY", "project");
        config.setProperty("GITLAB_BRANCH", "develop");
        config.setProperty("GITLAB_BASE_PATH", "https://gitlab.com");

        assertEquals("gl-token", config.getGitLabToken());
        assertEquals("group", config.getGitLabWorkspace());
        assertEquals("project", config.getGitLabRepository());
        assertEquals("develop", config.getGitLabBranch());
        assertEquals("https://gitlab.com", config.getGitLabBasePath());
    }

    @Test
    public void testBitbucketGetters() {
        config.setProperty("BITBUCKET_TOKEN", "bb-token");
        config.setProperty("BITBUCKET_API_VERSION", "2.0");
        config.setProperty("BITBUCKET_WORKSPACE", "ws");
        config.setProperty("BITBUCKET_REPOSITORY", "repo");
        config.setProperty("BITBUCKET_BRANCH", "master");
        config.setProperty("BITBUCKET_BASE_PATH", "https://api.bitbucket.org");

        assertEquals("bb-token", config.getBitbucketToken());
        assertEquals("2.0", config.getBitbucketApiVersion());
        assertEquals("ws", config.getBitbucketWorkspace());
        assertEquals("repo", config.getBitbucketRepository());
        assertEquals("master", config.getBitbucketBranch());
        assertEquals("https://api.bitbucket.org", config.getBitbucketBasePath());
    }

    @Test
    public void testIsReadPullRequestDiff() {
        // null means "default true"
        assertTrue(config.isReadPullRequestDiff());

        config.setProperty("IS_READ_PULL_REQUEST_DIFF", "false");
        assertFalse(config.isReadPullRequestDiff());

        config.setProperty("IS_READ_PULL_REQUEST_DIFF", "true");
        assertTrue(config.isReadPullRequestDiff());
    }

    // ------------------------------------------------------------------
    // ConfluenceConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGetConfluenceLoginPassTokenBasicAuth() {
        config.setProperty("CONFLUENCE_EMAIL", " user@example.com ");
        config.setProperty("CONFLUENCE_API_TOKEN", " conf-token ");
        config.setProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "legacy");

        String expected = Base64.getEncoder()
                .encodeToString("user@example.com:conf-token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, config.getConfluenceLoginPassToken());
    }

    @Test
    public void testGetConfluenceLoginPassTokenBearerAuth() {
        config.setProperty("CONFLUENCE_EMAIL", "user@example.com");
        config.setProperty("CONFLUENCE_API_TOKEN", " bearer-token ");
        config.setProperty("CONFLUENCE_AUTH_TYPE", "bearer");

        assertEquals("bearer-token", config.getConfluenceLoginPassToken());
    }

    @Test
    public void testGetConfluenceLoginPassTokenFallsBackToLegacy() {
        config.setProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "legacy");
        assertEquals("legacy", config.getConfluenceLoginPassToken());

        config.setProperty("CONFLUENCE_EMAIL", "user@example.com");
        // api token still missing -> fallback
        assertEquals("legacy", config.getConfluenceLoginPassToken());
    }

    @Test
    public void testConfluenceSimpleGetters() {
        config.setProperty("CONFLUENCE_BASE_PATH", "https://conf.example.com");
        config.setProperty("CONFLUENCE_EMAIL", "user@example.com");
        config.setProperty("CONFLUENCE_API_TOKEN", "token");
        config.setProperty("CONFLUENCE_GRAPHQL_PATH", "/graphql");
        config.setProperty("CONFLUENCE_DEFAULT_SPACE", "SPACE");

        assertEquals("https://conf.example.com", config.getConfluenceBasePath());
        assertEquals("user@example.com", config.getConfluenceEmail());
        assertEquals("token", config.getConfluenceApiToken());
        assertEquals("/graphql", config.getConfluenceGraphQLPath());
        assertEquals("SPACE", config.getConfluenceDefaultSpace());
    }

    @Test
    public void testGetConfluenceAuthTypeDefaultsToBasic() {
        assertEquals("Basic", config.getConfluenceAuthType());

        config.setProperty("CONFLUENCE_AUTH_TYPE", "Bearer");
        assertEquals("Bearer", config.getConfluenceAuthType());
    }

    // ------------------------------------------------------------------
    // MiscConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testGetSleepTimeRequest() {
        assertEquals(Long.valueOf(300L), config.getSleepTimeRequest());

        config.setProperty("SLEEP_TIME_REQUEST", "1500");
        assertEquals(Long.valueOf(1500L), config.getSleepTimeRequest());

        config.setProperty("SLEEP_TIME_REQUEST", "bad");
        assertEquals(Long.valueOf(300L), config.getSleepTimeRequest());
    }

    @Test
    public void testRallyAndFigmaGetters() {
        config.setProperty("RALLY_TOKEN", "rally-token");
        config.setProperty("RALLY_PATH", "https://rally.example.com");
        config.setProperty("FIGMA_BASE_PATH", "https://api.figma.com");
        config.setProperty(PropertyReader.FIGMA_TOKEN, "figma-token");
        config.setProperty(PropertyReader.FIGMA_OAUTH_ACCESS_TOKEN, "figma-access");
        config.setProperty(PropertyReader.FIGMA_OAUTH_REFRESH_TOKEN, "figma-refresh");

        assertEquals("rally-token", config.getRallyToken());
        assertEquals("https://rally.example.com", config.getRallyPath());
        assertEquals("https://api.figma.com", config.getFigmaBasePath());
        assertEquals("figma-token", config.getFigmaApiKey());
        assertEquals("figma-access", config.getFigmaOAuth2AccessToken());
        assertEquals("figma-refresh", config.getFigmaOAuth2RefreshToken());
    }

    @Test
    public void testGetDefaultTicketWeightIfNoSPs() {
        assertEquals(Integer.valueOf(-1), config.getDefaultTicketWeightIfNoSPs());

        config.setProperty(PropertyReader.DEFAULT_TICKET_WEIGHT_IF_NO_SP, "5");
        assertEquals(Integer.valueOf(5), config.getDefaultTicketWeightIfNoSPs());

        config.setProperty(PropertyReader.DEFAULT_TICKET_WEIGHT_IF_NO_SP, "bad");
        assertEquals(Integer.valueOf(-1), config.getDefaultTicketWeightIfNoSPs());
    }

    @Test
    public void testGetLinesOfCodeDivider() {
        assertEquals(Double.valueOf(1.0), config.getLinesOfCodeDivider());

        config.setProperty(PropertyReader.LINES_OF_CODE_DIVIDER, "2.5");
        assertEquals(Double.valueOf(2.5), config.getLinesOfCodeDivider());

        config.setProperty(PropertyReader.LINES_OF_CODE_DIVIDER, "bad");
        assertEquals(Double.valueOf(1.0), config.getLinesOfCodeDivider());
    }

    @Test
    public void testGetTimeSpentOnDivider() {
        assertEquals(Double.valueOf(1.0), config.getTimeSpentOnDivider());

        config.setProperty(PropertyReader.TIME_SPENT_ON_DIVIDER, "3600");
        assertEquals(Double.valueOf(3600.0), config.getTimeSpentOnDivider());

        config.setProperty(PropertyReader.TIME_SPENT_ON_DIVIDER, "bad");
        assertEquals(Double.valueOf(1.0), config.getTimeSpentOnDivider());
    }

    @Test
    public void testGetTicketFieldsChangedDividerFieldSpecific() {
        config.setProperty("TICKET_FIELDS_CHANGED_DIVIDER_STORYPOINTS", "3.0");
        assertEquals(Double.valueOf(3.0), config.getTicketFieldsChangedDivider("storyPoints"));
    }

    @Test
    public void testGetTicketFieldsChangedDividerFieldSpecificInvalid() {
        config.setProperty("TICKET_FIELDS_CHANGED_DIVIDER_PRIORITY", "bad");
        assertEquals(Double.valueOf(1.0), config.getTicketFieldsChangedDivider("priority"));
    }

    @Test
    public void testGetTicketFieldsChangedDividerUsesDefault() {
        config.setProperty(PropertyReader.TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT, "2.0");
        assertEquals(Double.valueOf(2.0), config.getTicketFieldsChangedDivider("anything"));
    }

    @Test
    public void testGetTicketFieldsChangedDividerDefaultInvalid() {
        config.setProperty(PropertyReader.TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT, "bad");
        assertEquals(Double.valueOf(1.0), config.getTicketFieldsChangedDivider("anything"));
    }

    @Test
    public void testGetTicketFieldsChangedDividerHardcodedDefault() {
        assertEquals(Double.valueOf(1.0), config.getTicketFieldsChangedDivider("anything"));
    }

    // ------------------------------------------------------------------
    // TestRailConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testTestRailGetters() {
        config.setProperty(PropertyReader.TESTRAIL_BASE_PATH, "https://testrail.example.com");
        config.setProperty(PropertyReader.TESTRAIL_USERNAME, "tr-user");
        config.setProperty(PropertyReader.TESTRAIL_API_KEY, "tr-key");
        config.setProperty(PropertyReader.TESTRAIL_PROJECT, "TRP");

        assertEquals("https://testrail.example.com", config.getTestRailBasePath());
        assertEquals("tr-user", config.getTestRailUsername());
        assertEquals("tr-key", config.getTestRailApiKey());
        assertEquals("TRP", config.getTestRailProject());
    }

    @Test
    public void testIsTestRailLoggingEnabled() {
        assertFalse(config.isTestRailLoggingEnabled());

        config.setProperty(PropertyReader.TESTRAIL_LOGGING_ENABLED, "true");
        assertTrue(config.isTestRailLoggingEnabled());

        config.setProperty(PropertyReader.TESTRAIL_LOGGING_ENABLED, "no");
        assertFalse(config.isTestRailLoggingEnabled());
    }

    // ------------------------------------------------------------------
    // BitriseConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testBitriseGetters() {
        config.setProperty(PropertyReader.BITRISE_TOKEN, "bitrise-token");
        config.setProperty(PropertyReader.BITRISE_APP_SLUG, "app-slug");

        assertEquals("bitrise-token", config.getBitriseToken());
        assertEquals("app-slug", config.getBitriseAppSlug());
    }

    @Test
    public void testGetBitriseBasePathDefaultAndOverride() {
        assertEquals("https://api.bitrise.io/v0.1", config.getBitriseBasePath());

        config.setProperty(PropertyReader.BITRISE_BASE_PATH, "https://custom.bitrise.io");
        assertEquals("https://custom.bitrise.io", config.getBitriseBasePath());
    }

    // ------------------------------------------------------------------
    // XrayConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testXrayGetters() {
        config.setProperty(PropertyReader.XRAY_CLIENT_ID, "xray-id");
        config.setProperty(PropertyReader.XRAY_CLIENT_SECRET, "xray-secret");
        config.setProperty(PropertyReader.XRAY_BASE_PATH, "https://xray.example.com");

        assertEquals("xray-id", config.getXrayClientId());
        assertEquals("xray-secret", config.getXrayClientSecret());
        assertEquals("https://xray.example.com", config.getXrayBasePath());
    }

    // ------------------------------------------------------------------
    // AdoConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testAdoGetters() {
        config.setProperty(PropertyReader.ADO_ORGANIZATION, "org");
        config.setProperty(PropertyReader.ADO_PROJECT, "project");
        config.setProperty(PropertyReader.ADO_PAT_TOKEN, "pat");

        assertEquals("org", config.getAdoOrganization());
        assertEquals("project", config.getAdoProject());
        assertEquals("pat", config.getAdoPatToken());
    }

    @Test
    public void testGetAdoBasePathDefaultAndOverride() {
        assertEquals("https://dev.azure.com", config.getAdoBasePath());

        config.setProperty(PropertyReader.ADO_BASE_PATH, "https://ado.example.com");
        assertEquals("https://ado.example.com", config.getAdoBasePath());
    }

    // ------------------------------------------------------------------
    // TeamsConfiguration
    // ------------------------------------------------------------------

    @Test
    public void testTeamsGetters() {
        config.setProperty(PropertyReader.TEAMS_CLIENT_ID, "client-id");
        config.setProperty(PropertyReader.TEAMS_REFRESH_TOKEN, "refresh-token");
        config.setProperty(PropertyReader.TEAMS_SCOPES, "User.Read");

        assertEquals("client-id", config.getTeamsClientId());
        assertEquals("refresh-token", config.getTeamsRefreshToken());
        assertEquals("User.Read", config.getTeamsScopes());
    }

    @Test
    public void testTeamsDefaults() {
        assertEquals("https://graph.microsoft.com/v1.0", config.getTeamsBasePath());
        assertEquals("common", config.getTenantId());
        assertEquals("8080", config.getTeamsAuthPort());
        assertEquals("device", config.getTeamsAuthMethod());
        assertEquals("./teams.token", config.getTeamsTokenCachePath());
    }

    @Test
    public void testTeamsOverrides() {
        config.setProperty(PropertyReader.TEAMS_BASE_PATH, "https://graph.example.com");
        config.setProperty(PropertyReader.TEAMS_TENANT_ID, "tenant-1");
        config.setProperty(PropertyReader.TEAMS_AUTH_PORT, "9090");
        config.setProperty(PropertyReader.TEAMS_AUTH_METHOD, "browser");
        config.setProperty(PropertyReader.TEAMS_TOKEN_CACHE_PATH, "/tmp/teams.token");

        assertEquals("https://graph.example.com", config.getTeamsBasePath());
        assertEquals("tenant-1", config.getTenantId());
        assertEquals("9090", config.getTeamsAuthPort());
        assertEquals("browser", config.getTeamsAuthMethod());
        assertEquals("/tmp/teams.token", config.getTeamsTokenCachePath());
    }
}
