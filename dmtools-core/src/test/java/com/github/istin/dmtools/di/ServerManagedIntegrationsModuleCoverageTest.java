// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.anthropic.AnthropicAIClient;
import com.github.istin.dmtools.ai.dial.DialAIClient;
import com.github.istin.dmtools.ai.ollama.OllamaAIClient;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.xray.XrayClient;
import com.github.istin.dmtools.broadcom.rally.RallyClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.logging.LogCallback;
import com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Additional coverage tests for ServerManagedIntegrationsModule, focusing on
 * tracker auto-detection and DEFAULT_TRACKER branches, custom client implementations,
 * Confluence/Figma auth variants and AI provider priority branches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ServerManagedIntegrationsModuleCoverageTest {

    private static JSONObject jiraConfig(String email, String token) {
        JSONObject jira = new JSONObject();
        if (email != null) {
            jira.put("JIRA_EMAIL", email);
        }
        if (token != null) {
            jira.put("JIRA_API_TOKEN", token);
        }
        jira.put("JIRA_BASE_PATH", "https://test.atlassian.net/");
        jira.put("JIRA_AUTH_TYPE", "Basic");
        return jira;
    }

    private static JSONObject xrayConfig() {
        JSONObject xray = new JSONObject();
        xray.put("XRAY_BASE_PATH", "https://xray.cloud.getxray.app/api/v2");
        xray.put("XRAY_CLIENT_ID", "client-id");
        xray.put("XRAY_CLIENT_SECRET", "client-secret");
        return xray;
    }

    private static JSONObject adoConfig(String organization, String project, String patToken) {
        JSONObject ado = new JSONObject();
        ado.put("ADO_ORGANIZATION", organization);
        ado.put("ADO_PROJECT", project);
        ado.put("ADO_PAT_TOKEN", patToken);
        return ado;
    }

    private static JSONObject rallyConfig(String token) {
        JSONObject rally = new JSONObject();
        rally.put("RALLY_PATH", "https://rally1.rallydev.com");
        if (token != null) {
            rally.put("RALLY_TOKEN", token);
        }
        return rally;
    }

    // ---------- provideTrackerClient: DEFAULT_TRACKER branches ----------

    @Test
    void testProvideTrackerClient_DefaultTrackerJira() {
        JSONObject integrations = new JSONObject();
        integrations.put("DEFAULT_TRACKER", "jira");
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof JiraClient);
        assertEquals(TrackerClient.TextType.MARKDOWN, client.getTextType());
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerJiraXray() {
        JSONObject integrations = new JSONObject();
        integrations.put("DEFAULT_TRACKER", "jira_xray");
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));
        integrations.put("xray", xrayConfig());

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof XrayClient);
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerAdo() {
        JSONObject integrations = new JSONObject();
        integrations.put("DEFAULT_TRACKER", "ado");
        integrations.put("ado", adoConfig("my-org", "my-project", "pat-token"));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof AzureDevOpsClient);
        assertEquals(TrackerClient.TextType.HTML, client.getTextType());
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerRally() {
        JSONObject integrations = new JSONObject();
        integrations.put("DEFAULT_TRACKER", "rally");
        integrations.put("rally", rallyConfig("rally-token"));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof RallyClient);
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerUnknownFallsBackToAutoDetection() {
        JSONObject integrations = new JSONObject();
        integrations.put("DEFAULT_TRACKER", "unknown_tracker");

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNull(client);
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerPreferredFailsFallsBack() {
        JSONObject integrations = new JSONObject();
        integrations.put("DEFAULT_TRACKER", "ado");
        // ADO config missing required parameters -> preferred creation fails,
        // falls back to auto-detection which also fails
        integrations.put("ado", adoConfig("", "", ""));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNull(client);
    }

    // ---------- provideTrackerClient: auto-detection branches ----------

    @Test
    void testProvideTrackerClient_AutoDetectXrayPreferredOverJira() {
        JSONObject integrations = new JSONObject();
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));
        integrations.put("xray", xrayConfig());

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof XrayClient);
    }

    @Test
    void testProvideTrackerClient_AutoDetectAdoOnly() {
        JSONObject integrations = new JSONObject();
        integrations.put("ado", adoConfig("my-org", "my-project", "pat-token"));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof AzureDevOpsClient);
    }

    @Test
    void testProvideTrackerClient_AutoDetectRallyOnly() {
        JSONObject integrations = new JSONObject();
        integrations.put("rally", rallyConfig("rally-token"));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof RallyClient);
    }

    @Test
    void testProvideTrackerClient_InvalidRallyConfigReturnsNull() {
        JSONObject integrations = new JSONObject();
        integrations.put("rally", rallyConfig(null));

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNull(client);
    }

    @Test
    void testProvideTrackerClient_InvalidXrayFallsBackToJira() {
        JSONObject integrations = new JSONObject();
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));
        // X-ray config present but missing required secret -> X-ray creation fails, Jira is used
        JSONObject xray = new JSONObject();
        xray.put("XRAY_BASE_PATH", "https://xray.cloud.getxray.app/api/v2");
        integrations.put("xray", xray);

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof JiraClient);
        assertFalse(client instanceof XrayClient);
    }

    @Test
    void testProvideTrackerClient_JiraLoginPassTokenAuth() {
        JSONObject integrations = new JSONObject();
        JSONObject jira = new JSONObject();
        jira.put("JIRA_BASE_PATH", "https://test.atlassian.net/");
        jira.put("JIRA_LOGIN_PASS_TOKEN", "legacy-token");
        integrations.put("jira", jira);

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        assertTrue(client instanceof JiraClient);
    }

    @Test
    void testProvideTrackerClient_NullResolvedIntegrations() {
        ServerManagedIntegrationsModule nullModule = new ServerManagedIntegrationsModule(null);

        assertNull(nullModule.provideTrackerClient());
    }

    // ---------- CustomServerManagedJiraClient behavior ----------

    @Test
    void testCustomJiraClient_QueryFieldsAndExtraFields() {
        JSONObject integrations = new JSONObject();
        JSONObject jira = jiraConfig("test@example.com", "test-token");
        jira.put("JIRA_EXTRA_FIELDS_PROJECT", "customfield_10001, customfield_10002");
        jira.put("JIRA_MAX_SEARCH_RESULTS", 50);
        integrations.put("jira", jira);

        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();

        assertNotNull(client);
        String[] defaultFields = client.getDefaultQueryFields();
        String[] extendedFields = client.getExtendedQueryFields();
        assertNotNull(defaultFields);
        assertNotNull(extendedFields);
        assertTrue(extendedFields.length > defaultFields.length);
        boolean hasExtraField = false;
        for (String field : extendedFields) {
            if ("customfield_10001".equals(field)) {
                hasExtraField = true;
                break;
            }
        }
        assertTrue(hasExtraField, "Extra fields should be appended to extended query fields");
    }

    @Test
    void testCustomJiraClient_GetTextFieldsOnly() throws IOException {
        JSONObject integrations = new JSONObject();
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));
        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();
        assertNotNull(client);

        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketTitle()).thenReturn("Title");
        when(ticket.getTicketDescription()).thenReturn("Description");

        assertEquals("Title\nDescription", client.getTextFieldsOnly(ticket));
    }

    @Test
    void testCustomJiraClient_GetTextFieldsOnlyIOException() throws IOException {
        JSONObject integrations = new JSONObject();
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));
        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();
        assertNotNull(client);

        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketTitle()).thenThrow(new IOException("boom"));

        assertThrows(RuntimeException.class, () -> client.getTextFieldsOnly(ticket));
    }

    @Test
    void testCustomJiraClient_DeleteCommentIfExistsNoOp() throws IOException {
        JSONObject integrations = new JSONObject();
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));
        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();
        assertNotNull(client);

        assertDoesNotThrow(() -> client.deleteCommentIfExists("KEY-1", "comment"));
    }

    // ---------- CustomServerManagedRallyClient behavior ----------

    @Test
    void testCustomRallyClient_Behavior() throws IOException {
        JSONObject integrations = new JSONObject();
        integrations.put("rally", rallyConfig("rally-token"));
        TrackerClient<? extends ITicket> client = new ServerManagedIntegrationsModule(integrations).provideTrackerClient();
        assertNotNull(client);

        assertEquals(TrackerClient.TextType.HTML, client.getTextType());
        assertArrayEquals(com.github.istin.dmtools.broadcom.rally.model.RallyFields.DEFAULT, client.getDefaultQueryFields());
        assertTrue(client.buildUrlToSearch("foo").contains("keywords=foo"));
        assertTrue(client.getTestCases(mock(ITicket.class), "TestCase").isEmpty());
        assertDoesNotThrow(() -> client.deleteCommentIfExists("US1", "comment"));

        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketTitle()).thenReturn("Story");
        when(ticket.getTicketDescription()).thenReturn("Desc");
        assertEquals("Story\nDesc", client.getTextFieldsOnly(ticket));

        ITicket brokenTicket = mock(ITicket.class);
        when(brokenTicket.getTicketTitle()).thenThrow(new IOException("boom"));
        assertEquals("", client.getTextFieldsOnly(brokenTicket));

        assertThrows(UnsupportedOperationException.class,
                () -> client.createTicketInProject("P", "Story", "s", "d", null));
        assertThrows(UnsupportedOperationException.class,
                () -> client.updateTicket("US1", null));
    }

    // ---------- provideSourceCodes / CustomServerManagedGitHub ----------

    @Test
    void testProvideSourceCodes_GitHubConfigOverrides() {
        JSONObject integrations = new JSONObject();
        JSONObject github = new JSONObject();
        github.put("SOURCE_GITHUB_TOKEN", "test-github-token");
        github.put("SOURCE_GITHUB_BASE_PATH", "https://api.github.com");
        github.put("SOURCE_GITHUB_WORKSPACE", "TestOrg");
        github.put("SOURCE_GITHUB_REPOSITORY", "test-repo");
        integrations.put("github", github);

        List<SourceCode> sourceCodes = new ServerManagedIntegrationsModule(integrations).provideSourceCodes();

        assertEquals(1, sourceCodes.size());
        SourceCode githubSourceCode = sourceCodes.get(0);
        assertEquals("test-repo", githubSourceCode.getDefaultRepository());
        assertEquals("main", githubSourceCode.getDefaultBranch());
        assertEquals("TestOrg", githubSourceCode.getDefaultWorkspace());
        assertNotNull(githubSourceCode.getDefaultConfig());
        // Workspace and repository are set, so the source code reports itself as configured
        assertEquals(githubSourceCode.getDefaultConfig().isConfigured(), githubSourceCode.isConfigured());
    }

    @Test
    void testProvideSourceCodes_GitHubMissingTokenReturnsEmptyList() {
        JSONObject integrations = new JSONObject();
        JSONObject github = new JSONObject();
        github.put("SOURCE_GITHUB_BASE_PATH", "https://api.github.com");
        integrations.put("github", github);

        List<SourceCode> sourceCodes = new ServerManagedIntegrationsModule(integrations).provideSourceCodes();

        assertNotNull(sourceCodes);
        assertTrue(sourceCodes.isEmpty());
    }

    // ---------- provideConfluence: auth variants ----------

    @Test
    void testProvideConfluence_BearerAuth() {
        JSONObject integrations = new JSONObject();
        JSONObject confluence = new JSONObject();
        confluence.put("CONFLUENCE_EMAIL", "test@example.com");
        confluence.put("CONFLUENCE_API_TOKEN", "test-token");
        confluence.put("CONFLUENCE_BASE_PATH", "https://test.atlassian.net/wiki");
        confluence.put("CONFLUENCE_DEFAULT_SPACE", "TEST");
        confluence.put("CONFLUENCE_AUTH_TYPE", "Bearer");
        integrations.put("confluence", confluence);

        Confluence result = new ServerManagedIntegrationsModule(integrations).provideConfluence();

        assertNotNull(result);
    }

    @Test
    void testProvideConfluence_LoginPassTokenAuth() {
        JSONObject integrations = new JSONObject();
        JSONObject confluence = new JSONObject();
        confluence.put("CONFLUENCE_BASE_PATH", "https://test.atlassian.net/wiki");
        confluence.put("CONFLUENCE_LOGIN_PASS_TOKEN", "legacy-token");
        integrations.put("confluence", confluence);

        Confluence result = new ServerManagedIntegrationsModule(integrations).provideConfluence();

        assertNotNull(result);
    }

    @Test
    void testProvideConfluence_MissingTokenReturnsNull() {
        JSONObject integrations = new JSONObject();
        JSONObject confluence = new JSONObject();
        confluence.put("CONFLUENCE_BASE_PATH", "https://test.atlassian.net/wiki");
        integrations.put("confluence", confluence);

        Confluence result = new ServerManagedIntegrationsModule(integrations).provideConfluence();

        assertNull(result);
    }

    @Test
    void testProvideConfluence_WithCallbackLogger() {
        JSONObject integrations = new JSONObject();
        JSONObject confluence = new JSONObject();
        confluence.put("CONFLUENCE_EMAIL", "test@example.com");
        confluence.put("CONFLUENCE_API_TOKEN", "test-token");
        confluence.put("CONFLUENCE_BASE_PATH", "https://test.atlassian.net/wiki");
        integrations.put("confluence", confluence);

        LogCallback logCallback = (executionId, level, message, component) -> { };
        ServerManagedIntegrationsModule callbackModule =
                new ServerManagedIntegrationsModule(integrations, "exec-1", logCallback);

        Confluence result = callbackModule.provideConfluence();

        assertNotNull(result);
    }

    @Test
    void testProvideConfluence_NullResolvedIntegrations() {
        assertNull(new ServerManagedIntegrationsModule(null).provideConfluence());
    }

    // ---------- provideAI: provider priority branches ----------

    @Test
    void testProvideAI_WithAnthropicConfig() {
        JSONObject integrations = new JSONObject();
        JSONObject anthropic = new JSONObject();
        anthropic.put("ANTHROPIC_MODEL", "claude-3-5-sonnet");
        anthropic.put("ANTHROPIC_BASE_PATH", "https://api.anthropic.com/v1/messages");
        anthropic.put("ANTHROPIC_MAX_TOKENS", 2048);
        integrations.put("anthropic", anthropic);

        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(integrations);
        AI ai = module.provideAI(module.provideConversationObserver());

        assertNotNull(ai);
        assertTrue(ai instanceof AnthropicAIClient);
    }

    @Test
    void testProvideAI_AnthropicWithCustomHeaders() {
        JSONObject integrations = new JSONObject();
        JSONObject anthropic = new JSONObject();
        anthropic.put("ANTHROPIC_MODEL", "claude-3-5-sonnet");
        anthropic.put("ANTHROPIC_CUSTOM_HEADER_NAMES", "X-Custom-One, X-Custom-Two");
        anthropic.put("ANTHROPIC_CUSTOM_HEADER_VALUES", "value1, value2");
        integrations.put("anthropic", anthropic);

        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(integrations);
        AI ai = module.provideAI(module.provideConversationObserver());

        assertNotNull(ai);
        assertTrue(ai instanceof AnthropicAIClient);
    }

    @Test
    void testProvideAI_AnthropicMissingModelReturnsNull() {
        JSONObject integrations = new JSONObject();
        JSONObject anthropic = new JSONObject();
        anthropic.put("ANTHROPIC_BASE_PATH", "https://api.anthropic.com/v1/messages");
        integrations.put("anthropic", anthropic);

        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(integrations);
        AI ai = module.provideAI(module.provideConversationObserver());

        assertNull(ai);
    }

    @Test
    void testProvideAI_OllamaWithApiKeyAndMismatchedCustomHeaders() {
        JSONObject integrations = new JSONObject();
        JSONObject ollama = new JSONObject();
        ollama.put("OLLAMA_BASE_PATH", "http://localhost:11434");
        ollama.put("OLLAMA_MODEL", "llama3");
        ollama.put("OLLAMA_API_KEY", "secret-key");
        // Mismatched header names/values counts -> custom headers are skipped with a warning
        ollama.put("OLLAMA_CUSTOM_HEADER_NAMES", "X-One, X-Two");
        ollama.put("OLLAMA_CUSTOM_HEADER_VALUES", "only-one-value");
        integrations.put("ollama", ollama);

        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(integrations);
        AI ai = module.provideAI(module.provideConversationObserver());

        assertNotNull(ai);
        assertTrue(ai instanceof OllamaAIClient);
    }

    @Test
    void testProvideAI_WithDialConfig() {
        JSONObject integrations = new JSONObject();
        JSONObject dial = new JSONObject();
        dial.put("DIAL_AI_API_KEY", "dial-key");
        dial.put("DIAL_AI_MODEL", "gpt-4");
        dial.put("DIAL_AI_BATH_PATH", "https://api.openai.com/v1");
        dial.put("DIAL_API_VERSION", "2024-02-01");
        integrations.put("dial", dial);

        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(integrations);
        AI ai = module.provideAI(module.provideConversationObserver());

        assertNotNull(ai);
        assertTrue(ai instanceof DialAIClient);
    }

    @Test
    void testProvideAI_DialMissingApiKeyReturnsNull() {
        JSONObject integrations = new JSONObject();
        JSONObject dial = new JSONObject();
        dial.put("DIAL_AI_MODEL", "gpt-4");
        integrations.put("dial", dial);

        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(integrations);
        AI ai = module.provideAI(module.provideConversationObserver());

        assertNull(ai);
    }

    @Test
    void testProvideAI_NullResolvedIntegrations() {
        ServerManagedIntegrationsModule nullModule = new ServerManagedIntegrationsModule(null);

        assertNull(nullModule.provideAI(nullModule.provideConversationObserver()));
    }

    @Test
    void testCreateAI_UsesModuleLogic() {
        JSONObject integrations = new JSONObject();
        JSONObject ollama = new JSONObject();
        ollama.put("OLLAMA_MODEL", "llama3");
        integrations.put("ollama", ollama);

        AI ai = new ServerManagedIntegrationsModule(integrations).createAI();

        assertNotNull(ai);
        assertTrue(ai instanceof OllamaAIClient);
    }

    // ---------- provideFigmaClient ----------

    @Test
    void testProvideFigmaClient_WithValidConfig() {
        JSONObject integrations = new JSONObject();
        JSONObject figma = new JSONObject();
        figma.put("FIGMA_BASE_PATH", "https://api.figma.com");
        figma.put("FIGMA_TOKEN", "figma-token");
        integrations.put("figma", figma);

        FigmaClient figmaClient = new ServerManagedIntegrationsModule(integrations).provideFigmaClient();

        assertNotNull(figmaClient);
    }

    @Test
    void testProvideFigmaClient_MissingTokenReturnsNull() {
        JSONObject integrations = new JSONObject();
        JSONObject figma = new JSONObject();
        figma.put("FIGMA_BASE_PATH", "https://api.figma.com");
        integrations.put("figma", figma);

        assertNull(new ServerManagedIntegrationsModule(integrations).provideFigmaClient());
    }

    @Test
    void testProvideFigmaClient_NoFigmaConfigReturnsNull() {
        JSONObject integrations = new JSONObject();
        integrations.put("jira", jiraConfig("test@example.com", "test-token"));

        assertNull(new ServerManagedIntegrationsModule(integrations).provideFigmaClient());
    }

    @Test
    void testProvideFigmaClient_NullResolvedIntegrations() {
        assertNull(new ServerManagedIntegrationsModule(null).provideFigmaClient());
    }
}
