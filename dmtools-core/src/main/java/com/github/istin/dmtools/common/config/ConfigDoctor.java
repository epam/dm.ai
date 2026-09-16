// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.utils.PropertyReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates DMTools configuration and reports which integrations are ready to use.
 * This is intentionally stateless and only reads configuration values from the
 * provided {@link ApplicationConfiguration}; it never performs network calls.
 */
public class ConfigDoctor {

    /**
     * Result entry for a single integration check.
     */
    public static class CheckResult {
        private final String name;
        private final boolean ready;
        private final String status;
        private final List<String> missing;
        private final List<String> warnings;

        public CheckResult(String name, boolean ready, String status,
                           List<String> missing, List<String> warnings) {
            this.name = name;
            this.ready = ready;
            this.status = status;
            this.missing = missing;
            this.warnings = warnings;
        }

        public String getName() { return name; }
        public boolean isReady() { return ready; }
        public String getStatus() { return status; }
        public List<String> getMissing() { return missing; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Runs all known integration checks against the supplied configuration.
     */
    public static List<CheckResult> diagnose(ApplicationConfiguration config) {
        List<CheckResult> results = new ArrayList<>();
        results.add(checkJira(config));
        results.add(checkConfluence(config));
        results.add(checkFigma(config));
        results.add(checkGitHub(config));
        results.add(checkGitLab(config));
        results.add(checkBitbucket(config));
        results.add(checkAdo(config));
        results.add(checkRally(config));
        results.add(checkTestRail(config));
        results.add(checkBitrise(config));
        results.add(checkJenkins(config));
        results.add(checkXray(config));
        results.add(checkAi(config));
        results.add(checkTeams(config));
        results.add(checkSharePoint(config));
        results.add(checkDefaults(config));
        return results;
    }

    private static CheckResult checkJira(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getJiraBasePath())) missing.add(PropertyReader.JIRA_BASE_PATH);
        boolean hasBasicAuth = !isBlank(config.getJiraEmail()) && !isBlank(config.getJiraApiToken());
        boolean hasLoginPassToken = !isBlank(config.getJiraLoginPassToken());
        if (!hasBasicAuth && !hasLoginPassToken) {
            missing.add(PropertyReader.JIRA_EMAIL + " + " + PropertyReader.JIRA_API_TOKEN + " or " + PropertyReader.JIRA_LOGIN_PASS_TOKEN);
        }
        return result("jira", missing, "Basic Jira authentication");
    }

    private static CheckResult checkConfluence(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getConfluenceBasePath())) missing.add(PropertyReader.CONFLUENCE_BASE_PATH);
        if (isBlank(config.getConfluenceEmail())) missing.add(PropertyReader.CONFLUENCE_EMAIL);
        if (isBlank(config.getConfluenceApiToken())) missing.add(PropertyReader.CONFLUENCE_API_TOKEN);
        return result("confluence", missing, "Confluence authentication");
    }

    private static CheckResult checkFigma(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        boolean oauth = !isBlank(config.getFigmaOAuth2AccessToken()) || !isBlank(config.getFigmaOAuth2RefreshToken());
        if (isBlank(config.getFigmaApiKey()) && !oauth) {
            missing.add(PropertyReader.FIGMA_TOKEN + " or FIGMA OAuth credentials");
        }
        if (isBlank(config.getFigmaBasePath())) missing.add(PropertyReader.FIGMA_BASE_PATH);
        return result("figma", missing, "Figma API or OAuth authentication");
    }

    private static CheckResult checkGitHub(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getGithubToken())) missing.add(PropertyReader.SOURCE_GITHUB_TOKEN + " (or GITHUB_TOKEN)");
        return result("github", missing, "GitHub token");
    }

    private static CheckResult checkGitLab(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getGitLabToken())) missing.add(PropertyReader.GITLAB_TOKEN);
        return result("gitlab", missing, "GitLab token");
    }

    private static CheckResult checkBitbucket(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getBitbucketToken())) missing.add(PropertyReader.BITBUCKET_TOKEN);
        return result("bitbucket", missing, "Bitbucket token");
    }

    private static CheckResult checkAdo(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getAdoOrganization())) missing.add(PropertyReader.ADO_ORGANIZATION);
        if (isBlank(config.getAdoProject())) missing.add(PropertyReader.ADO_PROJECT);
        if (isBlank(config.getAdoPatToken())) missing.add(PropertyReader.ADO_PAT_TOKEN);
        return result("ado", missing, "Azure DevOps PAT and project");
    }

    private static CheckResult checkRally(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getRallyToken())) missing.add(PropertyReader.RALLY_TOKEN);
        if (isBlank(config.getRallyPath())) missing.add(PropertyReader.RALLY_PATH);
        return result("rally", missing, "Rally token and path");
    }

    private static CheckResult checkTestRail(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getTestRailBasePath())) missing.add(PropertyReader.TESTRAIL_BASE_PATH);
        if (isBlank(config.getTestRailUsername())) missing.add(PropertyReader.TESTRAIL_USERNAME);
        if (isBlank(config.getTestRailApiKey())) missing.add(PropertyReader.TESTRAIL_API_KEY);
        return result("testrail", missing, "TestRail authentication");
    }

    private static CheckResult checkBitrise(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getBitriseToken())) missing.add(PropertyReader.BITRISE_TOKEN);
        return result("bitrise", missing, "Bitrise token");
    }

    private static CheckResult checkXray(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getXrayClientId())) missing.add(PropertyReader.XRAY_CLIENT_ID);
        if (isBlank(config.getXrayClientSecret())) missing.add(PropertyReader.XRAY_CLIENT_SECRET);
        if (isBlank(config.getXrayBasePath())) missing.add(PropertyReader.XRAY_BASE_PATH);
        return result("xray", missing, "Xray client credentials");
    }

    private static CheckResult checkAi(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        boolean dial = !isBlank(config.getDialApiKey()) && !isBlank(config.getDialBathPath());
        boolean gemini = !isBlank(config.getGeminiApiKey());
        boolean openai = !isBlank(config.getOpenAIApiKey());
        boolean anthropic = !isBlank(config.getAnthropicModel()) &&
                (!isBlank(config.getAnthropicCustomHeaderValues()) || !isBlank(config.getAnthropicBasePath()));
        boolean bedrock = !isBlank(config.getBedrockAccessKeyId()) && !isBlank(config.getBedrockSecretAccessKey());
        boolean ollama = !isBlank(config.getOllamaModel());
        if (!dial && !gemini && !openai && !anthropic && !bedrock && !ollama) {
            missing.add("At least one AI provider (DIAL_API_KEY, GEMINI_API_KEY, OPENAI_API_KEY, ANTHROPIC_*, BEDROCK_*, OLLAMA_MODEL)");
        }
        return result("ai", missing, "AI provider credentials");
    }

    private static CheckResult checkTeams(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getTeamsClientId())) missing.add(PropertyReader.TEAMS_CLIENT_ID);
        if (isBlank(config.getTenantId())) missing.add(PropertyReader.TEAMS_TENANT_ID);
        return result("teams", missing, "Microsoft Teams OAuth credentials");
    }

    private static CheckResult checkJenkins(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getJenkinsBasePath())) missing.add(PropertyReader.JENKINS_BASE_PATH);
        if (isBlank(config.getJenkinsUser())) missing.add(PropertyReader.JENKINS_USER);
        if (isBlank(config.getJenkinsApiToken())) missing.add(PropertyReader.JENKINS_API_TOKEN);
        return result("jenkins", missing, "Jenkins authentication");
    }

    private static CheckResult checkSharePoint(ApplicationConfiguration config) {
        // SharePoint reuses the Teams (TEAMS_*) OAuth configuration.
        List<String> missing = new ArrayList<>();
        if (isBlank(config.getTeamsClientId())) missing.add(PropertyReader.TEAMS_CLIENT_ID);
        if (isBlank(config.getTenantId())) missing.add(PropertyReader.TEAMS_TENANT_ID);
        return result("sharepoint", missing, "SharePoint via Teams OAuth credentials");
    }

    private static CheckResult checkDefaults(ApplicationConfiguration config) {
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (isBlank(config.getDefaultTracker())) {
            warnings.add(PropertyReader.DEFAULT_TRACKER + " is not set; tracker_ aliases may not resolve");
        }
        if (isBlank(config.getDefaultLLM())) {
            warnings.add(PropertyReader.DEFAULT_LLM + " is not set; some AI features may use fallback logic");
        }
        return result("defaults", missing, warnings, "Default tracker/LLM selection");
    }

    private static CheckResult result(String name, List<String> missing, String status) {
        return result(name, missing, new ArrayList<>(), status);
    }

    private static CheckResult result(String name, List<String> missing,
                                      List<String> warnings, String status) {
        boolean ready = missing.isEmpty();
        String finalStatus = ready ? status + " configured" : status + " incomplete";
        return new CheckResult(name, ready, finalStatus, missing, warnings);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Registry integrations that need no external credentials and are therefore
     * always listable. "teams_auth" stays: teams_auth_start is the bootstrap
     * entry point used to obtain the Teams configuration itself.
     * kb/mermaid are deliberately NOT here: their core tools are AI-driven
     * (KBOrchestrator runs AI analysis agents; mermaid_index_generate is an AI
     * agent), so they follow the "ai" readiness below.
     */
    private static final Set<String> ALWAYS_AVAILABLE_INTEGRATIONS =
            new LinkedHashSet<>(Arrays.asList("cli", "file", "other", "teams_auth"));

    /**
     * Returns the registry integration names whose credentials are present in the
     * given configuration — presence of tokens/paths only, no network calls and
     * no client construction. Used by `dmtools list` when DMTOOLS_INTEGRATIONS
     * is not set, so the listing reflects what this machine can actually run
     * (e.g. Jenkins tools show up exactly when JENKINS_* is configured).
     */
    public static Set<String> getConfiguredIntegrations(ApplicationConfiguration config) {
        Map<String, CheckResult> checks = new LinkedHashMap<>();
        for (CheckResult result : diagnose(config)) {
            checks.put(result.getName(), result);
        }

        Set<String> integrations = new LinkedHashSet<>(ALWAYS_AVAILABLE_INTEGRATIONS);
        addIfReady(integrations, checks, "jira", "jira");
        // Xray tools run against the Jira host and need both credential sets.
        if (isReady(checks, "jira") && isReady(checks, "xray")) {
            integrations.add("jira_xray");
        }
        addIfReady(integrations, checks, "confluence", "confluence");
        addIfReady(integrations, checks, "figma", "figma");
        addIfReady(integrations, checks, "github", "github");
        addIfReady(integrations, checks, "gitlab", "gitlab");
        addIfReady(integrations, checks, "bitbucket", "bitbucket");
        addIfReady(integrations, checks, "ado", "ado");
        addIfReady(integrations, checks, "rally", "rally");
        addIfReady(integrations, checks, "testrail", "testrail");
        addIfReady(integrations, checks, "bitrise", "bitrise");
        addIfReady(integrations, checks, "jenkins", "jenkins");
        if (isReady(checks, "ai")) {
            // kb and mermaid core tools are AI-driven (KB analysis/aggregation
            // agents, mermaid_index_generate) — without an AI provider they have
            // no useful functionality, so they follow ai readiness.
            integrations.add("ai");
            integrations.add("kb");
            integrations.add("mermaid");
        }
        if (isReady(checks, "teams")) {
            // SharePoint reuses the Teams (TEAMS_*) OAuth configuration.
            integrations.add("teams");
            integrations.add("teams_auth");
            integrations.add("sharepoint");
        }
        return integrations;
    }

    private static boolean isReady(Map<String, CheckResult> checks, String name) {
        CheckResult result = checks.get(name);
        return result != null && result.isReady();
    }

    private static void addIfReady(Set<String> integrations, Map<String, CheckResult> checks,
                                   String checkName, String integrationName) {
        if (isReady(checks, checkName)) {
            integrations.add(integrationName);
        }
    }
}
