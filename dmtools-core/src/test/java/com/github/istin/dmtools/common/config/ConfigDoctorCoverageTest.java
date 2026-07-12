// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage tests for {@link ConfigDoctor}.
 * The class is a pure, stateless validator over {@link ApplicationConfiguration};
 * Mockito mocks are used instead of {@link InMemoryConfiguration} so that
 * environment-variable fallbacks cannot leak into the "missing" scenarios.
 */
public class ConfigDoctorCoverageTest {

    private static final String[] EXPECTED_CHECKS = {
            "jira", "confluence", "figma", "github", "gitlab", "bitbucket",
            "ado", "rally", "testrail", "bitrise", "xray", "ai", "teams", "defaults"
    };

    /**
     * Mock where every getter returns a non-blank value -> everything is configured.
     */
    private ApplicationConfiguration fullyConfigured() {
        return mock(ApplicationConfiguration.class, (Answer<String>) invocation -> "configured");
    }

    /**
     * Mock where every getter returns null (Mockito default) -> nothing is configured.
     */
    private ApplicationConfiguration emptyConfig() {
        return mock(ApplicationConfiguration.class);
    }

    private Map<String, ConfigDoctor.CheckResult> byName(List<ConfigDoctor.CheckResult> results) {
        return results.stream().collect(Collectors.toMap(ConfigDoctor.CheckResult::getName, Function.identity()));
    }

    private void assertReady(Map<String, ConfigDoctor.CheckResult> results, String name) {
        ConfigDoctor.CheckResult result = results.get(name);
        assertTrue(name + " should be ready", result.isReady());
        assertTrue(name + " status should be 'configured'", result.getStatus().endsWith(" configured"));
        assertTrue(name + " should have no missing items", result.getMissing().isEmpty());
    }

    private void assertIncomplete(Map<String, ConfigDoctor.CheckResult> results, String name) {
        ConfigDoctor.CheckResult result = results.get(name);
        assertFalse(name + " should not be ready", result.isReady());
        assertTrue(name + " status should be 'incomplete'", result.getStatus().endsWith(" incomplete"));
        assertFalse(name + " should report missing items", result.getMissing().isEmpty());
    }

    // ------------------------------------------------------------------
    // diagnose()
    // ------------------------------------------------------------------

    @Test
    public void testDiagnoseRunsAllKnownChecks() {
        List<ConfigDoctor.CheckResult> results = ConfigDoctor.diagnose(emptyConfig());

        assertEquals(EXPECTED_CHECKS.length, results.size());
        for (int i = 0; i < EXPECTED_CHECKS.length; i++) {
            assertEquals(EXPECTED_CHECKS[i], results.get(i).getName());
        }
    }

    @Test
    public void testDiagnoseWithFullyConfiguredConfigReportsAllReady() {
        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(fullyConfigured()));

        for (String name : EXPECTED_CHECKS) {
            assertReady(results, name);
        }
        assertTrue(results.get("defaults").getWarnings().isEmpty());
    }

    @Test
    public void testDiagnoseWithEmptyConfigReportsEverythingIncompleteExceptDefaults() {
        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(emptyConfig()));

        for (String name : EXPECTED_CHECKS) {
            if ("defaults".equals(name)) {
                assertTrue(results.get(name).isReady());
                assertEquals(2, results.get(name).getWarnings().size());
            } else {
                assertIncomplete(results, name);
            }
        }
    }

    // ------------------------------------------------------------------
    // Jira
    // ------------------------------------------------------------------

    @Test
    public void testJiraReadyWithBasicAuth() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getJiraBasePath()).thenReturn("https://jira.example.com");
        when(config.getJiraEmail()).thenReturn("user@example.com");
        when(config.getJiraApiToken()).thenReturn("token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "jira");
    }

    @Test
    public void testJiraReadyWithLoginPassTokenOnly() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getJiraBasePath()).thenReturn("https://jira.example.com");
        when(config.getJiraLoginPassToken()).thenReturn("legacy-token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "jira");
    }

    @Test
    public void testJiraIncompleteWithEmailButNoApiToken() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getJiraBasePath()).thenReturn("https://jira.example.com");
        when(config.getJiraEmail()).thenReturn("user@example.com");

        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(config));
        assertIncomplete(results, "jira");
        assertEquals(1, results.get("jira").getMissing().size());
    }

    @Test
    public void testJiraReportsTwoMissingItemsWhenNothingSet() {
        ConfigDoctor.CheckResult jira = byName(ConfigDoctor.diagnose(emptyConfig())).get("jira");
        assertEquals(2, jira.getMissing().size());
    }

    // ------------------------------------------------------------------
    // Confluence
    // ------------------------------------------------------------------

    @Test
    public void testConfluenceReadyWhenFullyConfigured() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getConfluenceBasePath()).thenReturn("https://conf.example.com");
        when(config.getConfluenceEmail()).thenReturn("user@example.com");
        when(config.getConfluenceApiToken()).thenReturn("token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "confluence");
    }

    @Test
    public void testConfluenceReportsThreeMissingItemsWhenNothingSet() {
        ConfigDoctor.CheckResult confluence = byName(ConfigDoctor.diagnose(emptyConfig())).get("confluence");
        assertEquals(3, confluence.getMissing().size());
        assertIncomplete(Map.of("confluence", confluence), "confluence");
    }

    // ------------------------------------------------------------------
    // Figma
    // ------------------------------------------------------------------

    @Test
    public void testFigmaReadyWithApiKey() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getFigmaApiKey()).thenReturn("figma-token");
        when(config.getFigmaBasePath()).thenReturn("https://api.figma.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "figma");
    }

    @Test
    public void testFigmaReadyWithOAuthRefreshTokenOnly() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getFigmaOAuth2RefreshToken()).thenReturn("refresh-token");
        when(config.getFigmaBasePath()).thenReturn("https://api.figma.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "figma");
    }

    @Test
    public void testFigmaReadyWithOAuthAccessTokenOnly() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getFigmaOAuth2AccessToken()).thenReturn("access-token");
        when(config.getFigmaBasePath()).thenReturn("https://api.figma.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "figma");
    }

    @Test
    public void testFigmaReportsTwoMissingItemsWhenNothingSet() {
        ConfigDoctor.CheckResult figma = byName(ConfigDoctor.diagnose(emptyConfig())).get("figma");
        assertEquals(2, figma.getMissing().size());
    }

    // ------------------------------------------------------------------
    // Single-token checks: GitHub, GitLab, Bitbucket, Bitrise
    // ------------------------------------------------------------------

    @Test
    public void testGitHubReadyWhenTokenSet() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getGithubToken()).thenReturn("gh-token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "github");
    }

    @Test
    public void testGitLabReadyWhenTokenSet() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getGitLabToken()).thenReturn("gl-token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "gitlab");
    }

    @Test
    public void testBitbucketReadyWhenTokenSet() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getBitbucketToken()).thenReturn("bb-token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "bitbucket");
    }

    @Test
    public void testBitriseReadyWhenTokenSet() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getBitriseToken()).thenReturn("bitrise-token");

        assertReady(byName(ConfigDoctor.diagnose(config)), "bitrise");
    }

    @Test
    public void testSingleTokenChecksIncompleteWhenNothingSet() {
        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(emptyConfig()));

        assertIncomplete(results, "github");
        assertIncomplete(results, "gitlab");
        assertIncomplete(results, "bitbucket");
        assertIncomplete(results, "bitrise");
    }

    // ------------------------------------------------------------------
    // Multi-field checks: ADO, Rally, TestRail, Xray, Teams
    // ------------------------------------------------------------------

    @Test
    public void testAdoReadyWhenFullyConfigured() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getAdoOrganization()).thenReturn("org");
        when(config.getAdoProject()).thenReturn("project");
        when(config.getAdoPatToken()).thenReturn("pat");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ado");
    }

    @Test
    public void testAdoReportsThreeMissingItemsWhenNothingSet() {
        assertEquals(3, byName(ConfigDoctor.diagnose(emptyConfig())).get("ado").getMissing().size());
    }

    @Test
    public void testRallyReadyWhenFullyConfigured() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getRallyToken()).thenReturn("rally-token");
        when(config.getRallyPath()).thenReturn("https://rally.example.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "rally");
    }

    @Test
    public void testRallyReportsTwoMissingItemsWhenNothingSet() {
        assertEquals(2, byName(ConfigDoctor.diagnose(emptyConfig())).get("rally").getMissing().size());
    }

    @Test
    public void testTestRailReadyWhenFullyConfigured() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getTestRailBasePath()).thenReturn("https://testrail.example.com");
        when(config.getTestRailUsername()).thenReturn("user");
        when(config.getTestRailApiKey()).thenReturn("key");

        assertReady(byName(ConfigDoctor.diagnose(config)), "testrail");
    }

    @Test
    public void testTestRailReportsThreeMissingItemsWhenNothingSet() {
        assertEquals(3, byName(ConfigDoctor.diagnose(emptyConfig())).get("testrail").getMissing().size());
    }

    @Test
    public void testXrayReadyWhenFullyConfigured() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getXrayClientId()).thenReturn("id");
        when(config.getXrayClientSecret()).thenReturn("secret");
        when(config.getXrayBasePath()).thenReturn("https://xray.example.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "xray");
    }

    @Test
    public void testXrayReportsThreeMissingItemsWhenNothingSet() {
        assertEquals(3, byName(ConfigDoctor.diagnose(emptyConfig())).get("xray").getMissing().size());
    }

    @Test
    public void testTeamsReadyWhenFullyConfigured() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getTeamsClientId()).thenReturn("client-id");
        when(config.getTenantId()).thenReturn("tenant-id");

        assertReady(byName(ConfigDoctor.diagnose(config)), "teams");
    }

    @Test
    public void testTeamsReportsTwoMissingItemsWhenNothingSet() {
        assertEquals(2, byName(ConfigDoctor.diagnose(emptyConfig())).get("teams").getMissing().size());
    }

    // ------------------------------------------------------------------
    // AI provider variants
    // ------------------------------------------------------------------

    @Test
    public void testAiIncompleteWhenNoProviderConfigured() {
        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(emptyConfig()));

        assertIncomplete(results, "ai");
        assertEquals(1, results.get("ai").getMissing().size());
    }

    @Test
    public void testAiReadyWithDialProvider() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getDialApiKey()).thenReturn("dial-key");
        when(config.getDialBathPath()).thenReturn("https://dial.example.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiIncompleteWhenDialKeyWithoutBasePath() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getDialApiKey()).thenReturn("dial-key");

        assertIncomplete(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiReadyWithGeminiProvider() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getGeminiApiKey()).thenReturn("gemini-key");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiReadyWithOpenAIProvider() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getOpenAIApiKey()).thenReturn("openai-key");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiReadyWithAnthropicModelAndBasePath() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getAnthropicModel()).thenReturn("claude-3");
        when(config.getAnthropicBasePath()).thenReturn("https://api.anthropic.com");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiReadyWithAnthropicModelAndCustomHeaders() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getAnthropicModel()).thenReturn("claude-3");
        when(config.getAnthropicCustomHeaderValues()).thenReturn("1,2");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiIncompleteWithAnthropicModelOnly() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getAnthropicModel()).thenReturn("claude-3");

        assertIncomplete(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiReadyWithBedrockProvider() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getBedrockAccessKeyId()).thenReturn("AKID");
        when(config.getBedrockSecretAccessKey()).thenReturn("SECRET");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiIncompleteWhenBedrockMissingSecretKey() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getBedrockAccessKeyId()).thenReturn("AKID");

        assertIncomplete(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    @Test
    public void testAiReadyWithOllamaProvider() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getOllamaModel()).thenReturn("llama3");

        assertReady(byName(ConfigDoctor.diagnose(config)), "ai");
    }

    // ------------------------------------------------------------------
    // Defaults (warnings only, always "ready")
    // ------------------------------------------------------------------

    @Test
    public void testDefaultsReadyWithoutWarningsWhenTrackerAndLlmSet() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getDefaultTracker()).thenReturn("jira");
        when(config.getDefaultLLM()).thenReturn("gpt-4");

        ConfigDoctor.CheckResult defaults = byName(ConfigDoctor.diagnose(config)).get("defaults");
        assertTrue(defaults.isReady());
        assertTrue(defaults.getMissing().isEmpty());
        assertTrue(defaults.getWarnings().isEmpty());
    }

    @Test
    public void testDefaultsWarnsOnlyAboutMissingTracker() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getDefaultLLM()).thenReturn("gpt-4");

        ConfigDoctor.CheckResult defaults = byName(ConfigDoctor.diagnose(config)).get("defaults");
        assertTrue(defaults.isReady());
        assertEquals(1, defaults.getWarnings().size());
    }

    @Test
    public void testDefaultsWarnsOnlyAboutMissingLlm() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getDefaultTracker()).thenReturn("jira");

        ConfigDoctor.CheckResult defaults = byName(ConfigDoctor.diagnose(config)).get("defaults");
        assertTrue(defaults.isReady());
        assertEquals(1, defaults.getWarnings().size());
    }

    // ------------------------------------------------------------------
    // isBlank handling: whitespace-only values count as missing
    // ------------------------------------------------------------------

    @Test
    public void testWhitespaceOnlyValuesAreTreatedAsMissing() {
        ApplicationConfiguration config = mock(ApplicationConfiguration.class,
                (Answer<String>) invocation -> "   ");

        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(config));

        for (String name : EXPECTED_CHECKS) {
            if ("defaults".equals(name)) {
                assertTrue(results.get(name).isReady());
                assertEquals(2, results.get(name).getWarnings().size());
            } else {
                assertIncomplete(results, name);
            }
        }
    }

    @Test
    public void testStatusMessageContainsCheckDescription() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getGithubToken()).thenReturn("gh-token");

        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(config));
        assertEquals("GitHub token configured", results.get("github").getStatus());
        assertEquals("GitHub token incomplete",
                byName(ConfigDoctor.diagnose(emptyConfig())).get("github").getStatus());
    }

    @Test
    public void testCheckResultGetters() {
        ConfigDoctor.CheckResult result = byName(ConfigDoctor.diagnose(emptyConfig())).get("gitlab");

        assertEquals("gitlab", result.getName());
        assertFalse(result.isReady());
        assertTrue(result.getStatus().startsWith("GitLab token"));
        assertFalse(result.getMissing().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    public void testDiagnoseRejectsNoOpCallOnMockedConfig() {
        // sanity: diagnose must never interact with the config beyond getters,
        // and must work on any ApplicationConfiguration implementation
        List<ConfigDoctor.CheckResult> results = ConfigDoctor.diagnose(mock(ApplicationConfiguration.class));
        assertEquals(EXPECTED_CHECKS.length, results.size());
    }

    @Test
    public void testMixedConfigurationOnlyConfiguredIntegrationsAreReady() {
        ApplicationConfiguration config = emptyConfig();
        when(config.getGithubToken()).thenReturn("gh-token");
        when(config.getOllamaModel()).thenReturn("llama3");

        Map<String, ConfigDoctor.CheckResult> results = byName(ConfigDoctor.diagnose(config));

        assertReady(results, "github");
        assertReady(results, "ai");
        assertIncomplete(results, "jira");
        assertIncomplete(results, "confluence");
        assertIncomplete(results, "figma");
    }

    @Test
    public void testAnyMethodOnMockReturnsNullByDefault() {
        // guard: if Mockito default answers change, the "missing" tests above
        // would silently break; verify the assumption explicitly
        ApplicationConfiguration config = mock(ApplicationConfiguration.class);
        assertEquals(null, config.getGithubToken());
        assertEquals(null, config.getJiraBasePath());
    }

    @Test
    public void testFullyConfiguredMockAnswerAppliesToAnyGetter() {
        ApplicationConfiguration config = fullyConfigured();
        // guard for the custom default answer used in the "all ready" test
        assertEquals("configured", config.getRallyToken());
        assertEquals("configured", config.getTeamsClientId());
    }
}
