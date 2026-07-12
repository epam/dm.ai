// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage tests for {@link PropertyReaderConfiguration}.
 * The class is a thin delegating wrapper around {@link PropertyReader}, so
 * every test mocks the reader and asserts the value is passed through
 * (including the few inlined branches and defaults).
 */
public class PropertyReaderConfigurationCoverageTest {

    private PropertyReader propertyReader;
    private PropertyReaderConfiguration config;

    @Before
    public void setUp() {
        propertyReader = mock(PropertyReader.class);
        config = new PropertyReaderConfiguration(propertyReader);
    }

    // ------------------------------------------------------------------
    // Constructors / config file
    // ------------------------------------------------------------------

    @Test
    public void testDefaultConstructorCreatesWorkingConfiguration() {
        PropertyReaderConfiguration cfg = new PropertyReaderConfiguration();
        assertNotNull(cfg);
    }

    @Test
    public void testSetConfigFileDelegatesToStaticPropertyReader() {
        try (MockedStatic<PropertyReader> mocked = mockStatic(PropertyReader.class)) {
            config.setConfigFile("my.properties");
            mocked.verify(() -> PropertyReader.setConfigFile("my.properties"));
        }
    }

    // ------------------------------------------------------------------
    // Generic value access
    // ------------------------------------------------------------------

    @Test
    public void testGetValueAndDefaultAndAllProperties() {
        when(propertyReader.getValue("KEY")).thenReturn("value");
        when(propertyReader.getValue("MISSING", "fallback")).thenReturn("fallback");
        Map<String, String> all = new HashMap<>();
        all.put("A", "1");
        when(propertyReader.getAllProperties()).thenReturn(all);

        assertEquals("value", config.getValue("KEY"));
        assertEquals("fallback", config.getValue("MISSING", "fallback"));
        assertSame(all, config.getAllProperties());
        assertNull(config.getValue("UNSET"));
    }

    // ------------------------------------------------------------------
    // Jira
    // ------------------------------------------------------------------

    @Test
    public void testJiraDelegations() {
        when(propertyReader.getJiraLoginPassToken()).thenReturn("token");
        when(propertyReader.getJiraEmail()).thenReturn("a@b.c");
        when(propertyReader.getJiraApiToken()).thenReturn("apiToken");
        when(propertyReader.getJiraBasePath()).thenReturn("https://jira");
        when(propertyReader.getJiraAuthType()).thenReturn("basic");
        when(propertyReader.isJiraWaitBeforePerform()).thenReturn(true);
        when(propertyReader.isJiraLoggingEnabled()).thenReturn(true);
        when(propertyReader.isJiraClearCache()).thenReturn(false);
        when(propertyReader.getJiraExtraFieldsProject()).thenReturn("PROJ");
        when(propertyReader.getJiraExtraFields()).thenReturn(new String[]{"f1", "f2"});

        assertEquals("token", config.getJiraLoginPassToken());
        assertEquals("a@b.c", config.getJiraEmail());
        assertEquals("apiToken", config.getJiraApiToken());
        assertEquals("https://jira", config.getJiraBasePath());
        assertEquals("basic", config.getJiraAuthType());
        assertTrue(config.isJiraWaitBeforePerform());
        assertTrue(config.isJiraLoggingEnabled());
        assertFalse(config.isJiraClearCache());
        assertEquals("PROJ", config.getJiraExtraFieldsProject());
        assertArrayEquals(new String[]{"f1", "f2"}, config.getJiraExtraFields());
    }

    // ------------------------------------------------------------------
    // Dial
    // ------------------------------------------------------------------

    @Test
    public void testDialDelegations() {
        when(propertyReader.getDialBathPath()).thenReturn("https://dial");
        when(propertyReader.getDialIApiKey()).thenReturn("dialKey");
        when(propertyReader.getDialModel()).thenReturn("gpt-4");
        when(propertyReader.getDialApiVersion()).thenReturn("2024-01");

        assertEquals("https://dial", config.getDialBathPath());
        assertEquals("dialKey", config.getDialApiKey());
        assertEquals("gpt-4", config.getDialModel());
        assertEquals("2024-01", config.getDialApiVersion());
    }

    // ------------------------------------------------------------------
    // AI tuning
    // ------------------------------------------------------------------

    @Test
    public void testAiTuningDelegations() {
        when(propertyReader.getCodeAIModel()).thenReturn("codeModel");
        when(propertyReader.getTestAIModel()).thenReturn("testModel");
        when(propertyReader.getAiRetryAmount()).thenReturn(3);
        when(propertyReader.getAiRetryDelayStep()).thenReturn(500L);
        when(propertyReader.getPromptChunkTokenLimit()).thenReturn(1000);
        when(propertyReader.getPromptChunkMaxSingleFileSize()).thenReturn(2048L);
        when(propertyReader.getPromptChunkMaxTotalFilesSize()).thenReturn(4096L);
        when(propertyReader.getPromptChunkMaxFiles()).thenReturn(10);

        assertEquals("codeModel", config.getCodeAIModel());
        assertEquals("testModel", config.getTestAIModel());
        assertEquals(3, config.getAiRetryAmount());
        assertEquals(500L, config.getAiRetryDelayStep());
        assertEquals(1000, config.getPromptChunkTokenLimit());
        assertEquals(2048L, config.getPromptChunkMaxSingleFileSize());
        assertEquals(4096L, config.getPromptChunkMaxTotalFilesSize());
        assertEquals(10, config.getPromptChunkMaxFiles());
    }

    // ------------------------------------------------------------------
    // Gemini (plain + Vertex with inline branches)
    // ------------------------------------------------------------------

    @Test
    public void testGeminiDelegations() {
        when(propertyReader.getGeminiApiKey()).thenReturn("geminiKey");
        when(propertyReader.getGeminiDefaultModel()).thenReturn("gemini-pro");
        when(propertyReader.getGeminiBasePath()).thenReturn("https://gemini");
        when(propertyReader.getValue("GEMINI_VERTEX_PROJECT_ID")).thenReturn("proj");
        when(propertyReader.getValue("GEMINI_VERTEX_LOCATION")).thenReturn("us-central1");
        when(propertyReader.getValue("GEMINI_VERTEX_CREDENTIALS_PATH")).thenReturn("/tmp/cred.json");
        when(propertyReader.getValue("GEMINI_VERTEX_CREDENTIALS_JSON")).thenReturn("{}");

        assertEquals("geminiKey", config.getGeminiApiKey());
        assertEquals("gemini-pro", config.getGeminiDefaultModel());
        assertEquals("https://gemini", config.getGeminiBasePath());
        assertEquals("proj", config.getGeminiVertexProjectId());
        assertEquals("us-central1", config.getGeminiVertexLocation());
        assertEquals("/tmp/cred.json", config.getGeminiVertexCredentialsPath());
        assertEquals("{}", config.getGeminiVertexCredentialsJson());
    }

    @Test
    public void testGeminiVertexEnabledBranches() {
        when(propertyReader.getValue("GEMINI_VERTEX_ENABLED", "false")).thenReturn("TRUE");
        assertTrue(config.isGeminiVertexEnabled());

        when(propertyReader.getValue("GEMINI_VERTEX_ENABLED", "false")).thenReturn("false");
        assertFalse(config.isGeminiVertexEnabled());
    }

    @Test
    public void testGeminiVertexApiVersionBranches() {
        when(propertyReader.getValue("GEMINI_VERTEX_API_VERSION")).thenReturn("v1beta");
        assertEquals("v1beta", config.getGeminiVertexApiVersion());

        when(propertyReader.getValue("GEMINI_VERTEX_API_VERSION")).thenReturn(null);
        assertEquals("v1", config.getGeminiVertexApiVersion());

        when(propertyReader.getValue("GEMINI_VERTEX_API_VERSION")).thenReturn("  ");
        assertEquals("v1", config.getGeminiVertexApiVersion());
    }

    // ------------------------------------------------------------------
    // JS client
    // ------------------------------------------------------------------

    @Test
    public void testJsDelegations() {
        when(propertyReader.getJsScriptPath()).thenReturn("/scripts");
        when(propertyReader.getJsScriptContent()).thenReturn("content");
        when(propertyReader.getJsClientName()).thenReturn("jsClient");
        when(propertyReader.getJsDefaultModel()).thenReturn("jsModel");
        when(propertyReader.getJsBasePath()).thenReturn("https://js");
        when(propertyReader.getJsSecretsKeys()).thenReturn(new String[]{"k"});

        assertEquals("/scripts", config.getJsScriptPath());
        assertEquals("content", config.getJsScriptContent());
        assertEquals("jsClient", config.getJsClientName());
        assertEquals("jsModel", config.getJsDefaultModel());
        assertEquals("https://js", config.getJsBasePath());
        assertArrayEquals(new String[]{"k"}, config.getJsSecretsKeys());
    }

    // ------------------------------------------------------------------
    // GitHub / GitLab / Bitbucket
    // ------------------------------------------------------------------

    @Test
    public void testGithubDelegations() {
        when(propertyReader.getGithubToken()).thenReturn("ghToken");
        when(propertyReader.getGithubWorkspace()).thenReturn("ghWs");
        when(propertyReader.getGithubRepository()).thenReturn("ghRepo");
        when(propertyReader.getGithubBranch()).thenReturn("main");
        when(propertyReader.getGithubBasePath()).thenReturn("https://api.github.com");

        assertEquals("ghToken", config.getGithubToken());
        assertEquals("ghWs", config.getGithubWorkspace());
        assertEquals("ghRepo", config.getGithubRepository());
        assertEquals("main", config.getGithubBranch());
        assertEquals("https://api.github.com", config.getGithubBasePath());
    }

    @Test
    public void testGitLabDelegations() {
        when(propertyReader.getGitLabToken()).thenReturn("glToken");
        when(propertyReader.getGitLabWorkspace()).thenReturn("glWs");
        when(propertyReader.getGitLabRepository()).thenReturn("glRepo");
        when(propertyReader.getGitLabBranch()).thenReturn("develop");
        when(propertyReader.getGitLabBasePath()).thenReturn("https://gitlab");

        assertEquals("glToken", config.getGitLabToken());
        assertEquals("glWs", config.getGitLabWorkspace());
        assertEquals("glRepo", config.getGitLabRepository());
        assertEquals("develop", config.getGitLabBranch());
        assertEquals("https://gitlab", config.getGitLabBasePath());
    }

    @Test
    public void testBitbucketDelegations() {
        when(propertyReader.getBitbucketToken()).thenReturn("bbToken");
        when(propertyReader.getBitbucketApiVersion()).thenReturn("2.0");
        when(propertyReader.getBitbucketWorkspace()).thenReturn("bbWs");
        when(propertyReader.getBitbucketRepository()).thenReturn("bbRepo");
        when(propertyReader.getBitbucketBranch()).thenReturn("master");
        when(propertyReader.getBitbucketBasePath()).thenReturn("https://bitbucket");
        when(propertyReader.isReadPullRequestDiff()).thenReturn(true);

        assertEquals("bbToken", config.getBitbucketToken());
        assertEquals("2.0", config.getBitbucketApiVersion());
        assertEquals("bbWs", config.getBitbucketWorkspace());
        assertEquals("bbRepo", config.getBitbucketRepository());
        assertEquals("master", config.getBitbucketBranch());
        assertEquals("https://bitbucket", config.getBitbucketBasePath());
        assertTrue(config.isReadPullRequestDiff());
    }

    // ------------------------------------------------------------------
    // Confluence / Rally / Figma
    // ------------------------------------------------------------------

    @Test
    public void testConfluenceDelegations() {
        when(propertyReader.getConfluenceBasePath()).thenReturn("https://wiki");
        when(propertyReader.getConfluenceLoginPassToken()).thenReturn("confToken");
        when(propertyReader.getConfluenceEmail()).thenReturn("w@x.y");
        when(propertyReader.getConfluenceApiToken()).thenReturn("confApi");
        when(propertyReader.getConfluenceAuthType()).thenReturn("token");
        when(propertyReader.getConfluenceGraphQLPath()).thenReturn("/graphql");
        when(propertyReader.getConfluenceDefaultSpace()).thenReturn("SPACE");

        assertEquals("https://wiki", config.getConfluenceBasePath());
        assertEquals("confToken", config.getConfluenceLoginPassToken());
        assertEquals("w@x.y", config.getConfluenceEmail());
        assertEquals("confApi", config.getConfluenceApiToken());
        assertEquals("token", config.getConfluenceAuthType());
        assertEquals("/graphql", config.getConfluenceGraphQLPath());
        assertEquals("SPACE", config.getConfluenceDefaultSpace());
    }

    @Test
    public void testRallyAndSleepDelegations() {
        when(propertyReader.getSleepTimeRequest()).thenReturn(250L);
        when(propertyReader.getRallyToken()).thenReturn("rallyToken");
        when(propertyReader.getRallyPath()).thenReturn("https://rally");

        assertEquals(Long.valueOf(250L), config.getSleepTimeRequest());
        assertEquals("rallyToken", config.getRallyToken());
        assertEquals("https://rally", config.getRallyPath());
    }

    @Test
    public void testFigmaDelegations() {
        when(propertyReader.getFigmaBasePath()).thenReturn("https://figma");
        when(propertyReader.getFigmaApiKey()).thenReturn("figmaKey");
        when(propertyReader.getFigmaOAuth2AccessToken()).thenReturn("access");
        when(propertyReader.getFigmaOAuth2RefreshToken()).thenReturn("refresh");

        assertEquals("https://figma", config.getFigmaBasePath());
        assertEquals("figmaKey", config.getFigmaApiKey());
        assertEquals("access", config.getFigmaOAuth2AccessToken());
        assertEquals("refresh", config.getFigmaOAuth2RefreshToken());
    }

    // ------------------------------------------------------------------
    // Dividers
    // ------------------------------------------------------------------

    @Test
    public void testDividerDelegations() {
        when(propertyReader.getDefaultTicketWeightIfNoSPs()).thenReturn(5);
        when(propertyReader.getLinesOfCodeDivider()).thenReturn(1.5);
        when(propertyReader.getTimeSpentOnDivider()).thenReturn(2.5);
        when(propertyReader.getTicketFieldsChangedDivider("status")).thenReturn(3.5);

        assertEquals(Integer.valueOf(5), config.getDefaultTicketWeightIfNoSPs());
        assertEquals(Double.valueOf(1.5), config.getLinesOfCodeDivider());
        assertEquals(Double.valueOf(2.5), config.getTimeSpentOnDivider());
        assertEquals(Double.valueOf(3.5), config.getTicketFieldsChangedDivider("status"));
        verify(propertyReader).getTicketFieldsChangedDivider("status");
    }

    // ------------------------------------------------------------------
    // Ollama / Anthropic / Bedrock / OpenAI
    // ------------------------------------------------------------------

    @Test
    public void testOllamaDelegations() {
        when(propertyReader.getOllamaBasePath()).thenReturn("https://ollama");
        when(propertyReader.getOllamaModel()).thenReturn("llama3");
        when(propertyReader.getOllamaNumCtx()).thenReturn(4096);
        when(propertyReader.getOllamaNumPredict()).thenReturn(512);
        when(propertyReader.getValue("OLLAMA_API_KEY")).thenReturn("ollamaKey");

        assertEquals("https://ollama", config.getOllamaBasePath());
        assertEquals("llama3", config.getOllamaModel());
        assertEquals(4096, config.getOllamaNumCtx());
        assertEquals(512, config.getOllamaNumPredict());
        assertEquals("ollamaKey", config.getOllamaApiKey());
    }

    @Test
    public void testAnthropicDelegations() {
        when(propertyReader.getAnthropicBasePath()).thenReturn("https://anthropic");
        when(propertyReader.getAnthropicModel()).thenReturn("claude");
        when(propertyReader.getAnthropicMaxTokens()).thenReturn(8192);
        when(propertyReader.getAnthropicCustomHeaderNames()).thenReturn("h1");
        when(propertyReader.getAnthropicCustomHeaderValues()).thenReturn("v1");

        assertEquals("https://anthropic", config.getAnthropicBasePath());
        assertEquals("claude", config.getAnthropicModel());
        assertEquals(8192, config.getAnthropicMaxTokens());
        assertEquals("h1", config.getAnthropicCustomHeaderNames());
        assertEquals("v1", config.getAnthropicCustomHeaderValues());
    }

    @Test
    public void testBedrockDelegations() {
        when(propertyReader.getBedrockBasePath()).thenReturn("https://bedrock");
        when(propertyReader.getBedrockRegion()).thenReturn("us-east-1");
        when(propertyReader.getBedrockModelId()).thenReturn("modelId");
        when(propertyReader.getBedrockBearerToken()).thenReturn("bearer");
        when(propertyReader.getBedrockAccessKeyId()).thenReturn("accessId");
        when(propertyReader.getBedrockSecretAccessKey()).thenReturn("secret");
        when(propertyReader.getBedrockSessionToken()).thenReturn("session");
        when(propertyReader.getBedrockMaxTokens()).thenReturn(4096);
        when(propertyReader.getBedrockTemperature()).thenReturn(0.7);

        assertEquals("https://bedrock", config.getBedrockBasePath());
        assertEquals("us-east-1", config.getBedrockRegion());
        assertEquals("modelId", config.getBedrockModelId());
        assertEquals("bearer", config.getBedrockBearerToken());
        assertEquals("accessId", config.getBedrockAccessKeyId());
        assertEquals("secret", config.getBedrockSecretAccessKey());
        assertEquals("session", config.getBedrockSessionToken());
        assertEquals(4096, config.getBedrockMaxTokens());
        assertEquals(0.7, config.getBedrockTemperature(), 0.0);
    }

    @Test
    public void testOpenAIDelegations() {
        when(propertyReader.getOpenAIApiKey()).thenReturn("openaiKey");
        when(propertyReader.getOpenAIBasePath()).thenReturn("https://openai");
        when(propertyReader.getOpenAIModel()).thenReturn("gpt-4o");
        when(propertyReader.getOpenAIMaxTokens()).thenReturn(2048);
        when(propertyReader.getOpenAITemperature()).thenReturn(0.3);
        when(propertyReader.getOpenAIMaxTokensParamName()).thenReturn("max_tokens");

        assertEquals("openaiKey", config.getOpenAIApiKey());
        assertEquals("https://openai", config.getOpenAIBasePath());
        assertEquals("gpt-4o", config.getOpenAIModel());
        assertEquals(2048, config.getOpenAIMaxTokens());
        assertEquals(0.3, config.getOpenAITemperature(), 0.0);
        assertEquals("max_tokens", config.getOpenAIMaxTokensParamName());
    }

    // ------------------------------------------------------------------
    // Defaults / TestRail / ADO / Bitrise / Xray / Teams / attachments
    // ------------------------------------------------------------------

    @Test
    public void testDefaultProviderDelegations() {
        when(propertyReader.getDefaultLLM()).thenReturn("gemini");
        when(propertyReader.getDefaultTracker()).thenReturn("jira");

        assertEquals("gemini", config.getDefaultLLM());
        assertEquals("jira", config.getDefaultTracker());
    }

    @Test
    public void testTestRailDelegations() {
        when(propertyReader.getTestRailBasePath()).thenReturn("https://testrail");
        when(propertyReader.getTestRailUsername()).thenReturn("user");
        when(propertyReader.getTestRailApiKey()).thenReturn("trKey");
        when(propertyReader.getTestRailProject()).thenReturn("P1");
        when(propertyReader.isTestRailLoggingEnabled()).thenReturn(true);

        assertEquals("https://testrail", config.getTestRailBasePath());
        assertEquals("user", config.getTestRailUsername());
        assertEquals("trKey", config.getTestRailApiKey());
        assertEquals("P1", config.getTestRailProject());
        assertTrue(config.isTestRailLoggingEnabled());
    }

    @Test
    public void testAdoDelegations() {
        when(propertyReader.getAdoOrganization()).thenReturn("org");
        when(propertyReader.getAdoProject()).thenReturn("proj");
        when(propertyReader.getAdoPatToken()).thenReturn("pat");
        when(propertyReader.getAdoBasePath()).thenReturn("https://ado");

        assertEquals("org", config.getAdoOrganization());
        assertEquals("proj", config.getAdoProject());
        assertEquals("pat", config.getAdoPatToken());
        assertEquals("https://ado", config.getAdoBasePath());
    }

    @Test
    public void testBitriseDelegations() {
        when(propertyReader.getBitriseToken()).thenReturn("bitriseToken");
        when(propertyReader.getBitriseBasePath()).thenReturn("https://bitrise");
        when(propertyReader.getBitriseAppSlug()).thenReturn("slug");

        assertEquals("bitriseToken", config.getBitriseToken());
        assertEquals("https://bitrise", config.getBitriseBasePath());
        assertEquals("slug", config.getBitriseAppSlug());
    }

    @Test
    public void testXrayDelegations() {
        when(propertyReader.getXrayClientId()).thenReturn("xrayId");
        when(propertyReader.getXrayClientSecret()).thenReturn("xraySecret");
        when(propertyReader.getXrayBasePath()).thenReturn("https://xray");

        assertEquals("xrayId", config.getXrayClientId());
        assertEquals("xraySecret", config.getXrayClientSecret());
        assertEquals("https://xray", config.getXrayBasePath());
    }

    @Test
    public void testTeamsDelegations() {
        when(propertyReader.getValue("TEAMS_BASE_PATH", "https://graph.microsoft.com/v1.0"))
                .thenReturn("https://graph.example");
        when(propertyReader.getTeamsClientId()).thenReturn("clientId");
        when(propertyReader.getTeamsTenantId()).thenReturn("tenant");
        when(propertyReader.getTeamsScopes()).thenReturn("scope");
        when(propertyReader.getTeamsAuthMethod()).thenReturn("device_code");
        when(propertyReader.getTeamsAuthPort()).thenReturn(8080);
        when(propertyReader.getTeamsTokenCachePath()).thenReturn("/tmp/cache");
        when(propertyReader.getTeamsRefreshToken()).thenReturn("refresh");

        assertEquals("https://graph.example", config.getTeamsBasePath());
        assertEquals("clientId", config.getTeamsClientId());
        assertEquals("tenant", config.getTenantId());
        assertEquals("tenant", config.getTeamsTenantId());
        assertEquals("scope", config.getTeamsScopes());
        assertEquals("device_code", config.getTeamsAuthMethod());
        assertEquals("8080", config.getTeamsAuthPort());
        assertEquals("/tmp/cache", config.getTeamsTokenCachePath());
        assertEquals("refresh", config.getTeamsRefreshToken());
    }

    @Test
    public void testAttachmentDelegations() {
        Set<String> extensions = Collections.singleton("png");
        when(propertyReader.getAIAttachmentMaxSizeBytes()).thenReturn(1024L);
        when(propertyReader.getAIAttachmentAllowedExtensions()).thenReturn(extensions);

        assertEquals(1024L, config.getAIAttachmentMaxSizeBytes());
        assertSame(extensions, config.getAIAttachmentAllowedExtensions());
    }
}
