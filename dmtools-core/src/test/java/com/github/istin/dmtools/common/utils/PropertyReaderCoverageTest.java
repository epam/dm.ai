// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive coverage tests for {@link PropertyReader} exercising every public
 * getter through the thread-local override mechanism ({@link PropertyReader#setOverrides}),
 * plus the config.properties / dmtools.env file-loading paths via a temp user.dir.
 */
class PropertyReaderCoverageTest {

    private PropertyReader propertyReader;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        PropertyReader.resetForTesting();
        PropertyReader.clearOverrides();
        propertyReader = new PropertyReader();
    }

    @AfterEach
    void tearDown() {
        PropertyReader.clearOverrides();
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
        PropertyReader.resetForTesting();
    }

    private void override(Map<String, String> values) {
        PropertyReader.setOverrides(values);
    }

    private void override(String key, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(key, value);
        PropertyReader.setOverrides(values);
    }

    // -------------------------------------------------------------------------
    // Static override machinery
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getOverrides() returns empty map when no overrides are set")
    void testGetOverridesEmptyByDefault() {
        assertTrue(PropertyReader.getOverrides().isEmpty());
    }

    @Test
    @DisplayName("getOverrides() returns an unmodifiable view of the current overrides")
    void testGetOverridesUnmodifiable() {
        override("KEY_A", "1");
        Map<String, String> view = PropertyReader.getOverrides();
        assertEquals("1", view.get("KEY_A"));
        assertThrows(UnsupportedOperationException.class, () -> view.put("KEY_B", "2"));
    }

    @Test
    @DisplayName("getValue(key, default) returns default when key resolves to null")
    void testGetValueWithDefaultReturnsDefault() {
        assertEquals("fallback", propertyReader.getValue("DMTOOLS_COVERAGE_MISSING_KEY", "fallback"));
    }

    @Test
    @DisplayName("getValue(key, default) returns override value when present")
    void testGetValueWithDefaultReturnsOverride() {
        override("DMTOOLS_COVERAGE_KEY", "actual");
        assertEquals("actual", propertyReader.getValue("DMTOOLS_COVERAGE_KEY", "fallback"));
    }

    @Test
    @DisplayName("setConfigFile() switches the classpath resource used for loading")
    void testSetConfigFile() {
        PropertyReader.setConfigFile("/non-existent-config.properties");
        try {
            assertDoesNotThrow(() -> propertyReader.getValue("DMTOOLS_COVERAGE_MISSING_KEY"));
            assertNotNull(propertyReader.getAllProperties());
        } finally {
            PropertyReader.setConfigFile("/config.properties");
        }
    }

    @Test
    @DisplayName("getAllProperties() returns a non-null map from loadProperties()")
    void testGetAllProperties() {
        Map<String, String> properties = propertyReader.getAllProperties();
        assertNotNull(properties);
    }

    // -------------------------------------------------------------------------
    // File loading: config.properties at project root, dmtools.env at root and cwd
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getValue() loads config.properties from project root src/main/resources")
    void testConfigPropertiesLoadedFromProjectRoot(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("settings.gradle"), "rootProject.name = 'test'".getBytes(StandardCharsets.UTF_8));
        Path resources = tempDir.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.write(resources.resolve("config.properties"),
            "DMTOOLS_COVERAGE_FILE_KEY=file-value".getBytes(StandardCharsets.UTF_8));

        System.setProperty("user.dir", tempDir.toString());
        PropertyReader.resetForTesting();

        assertEquals("file-value", new PropertyReader().getValue("DMTOOLS_COVERAGE_FILE_KEY"));
    }

    @Test
    @DisplayName("getValue() loads dmtools.env from the project root")
    void testEnvFileLoadedFromProjectRoot(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("settings.gradle"), "rootProject.name = 'test'".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("dmtools.env"),
            "DMTOOLS_COVERAGE_ENV_KEY=env-value".getBytes(StandardCharsets.UTF_8));

        System.setProperty("user.dir", tempDir.toString());
        PropertyReader.resetForTesting();

        assertEquals("env-value", new PropertyReader().getValue("DMTOOLS_COVERAGE_ENV_KEY"));
    }

    @Test
    @DisplayName("getValue() falls back to dmtools.env in the working directory")
    void testEnvFileLoadedFromWorkingDirectory(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("settings.gradle"), "rootProject.name = 'test'".getBytes(StandardCharsets.UTF_8));
        Path workingDir = tempDir.resolve("sub");
        Files.createDirectories(workingDir);
        Files.write(workingDir.resolve("dmtools.env"),
            "DMTOOLS_COVERAGE_CWD_KEY=cwd-value".getBytes(StandardCharsets.UTF_8));

        System.setProperty("user.dir", workingDir.toString());
        PropertyReader.resetForTesting();

        assertEquals("cwd-value", new PropertyReader().getValue("DMTOOLS_COVERAGE_CWD_KEY"));
    }

    @Test
    @DisplayName("getValue() returns null for a key absent from all sources")
    void testGetValueMissingEverywhere(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("settings.gradle"), "rootProject.name = 'test'".getBytes(StandardCharsets.UTF_8));
        System.setProperty("user.dir", tempDir.toString());
        PropertyReader.resetForTesting();

        assertNull(new PropertyReader().getValue("DMTOOLS_COVERAGE_TRULY_MISSING_KEY"));
    }

    @Test
    @DisplayName("findProjectRoot() falls back to user.dir when no Gradle markers exist")
    void testProjectRootFallbackWithoutGradleMarkers(@TempDir Path tempDir) throws IOException {
        // Temp dirs have no settings.gradle ancestor, so findProjectRoot() falls back to user.dir
        Files.write(tempDir.resolve("dmtools.env"),
            "DMTOOLS_COVERAGE_NOROOT_KEY=no-root-value".getBytes(StandardCharsets.UTF_8));

        System.setProperty("user.dir", tempDir.toString());
        PropertyReader.resetForTesting();

        assertEquals("no-root-value", new PropertyReader().getValue("DMTOOLS_COVERAGE_NOROOT_KEY"));
    }

    // -------------------------------------------------------------------------
    // Jira
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getJiraLoginPassToken() base64-encodes email:apiToken when both are set")
    void testJiraLoginPassTokenFromEmailAndToken() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.JIRA_EMAIL, " user@example.com ");
        values.put(PropertyReader.JIRA_API_TOKEN, " secret-token ");
        values.put(PropertyReader.JIRA_LOGIN_PASS_TOKEN, "legacy-token");
        override(values);

        String expected = Base64.getEncoder().encodeToString("user@example.com:secret-token".getBytes());
        assertEquals(expected, propertyReader.getJiraLoginPassToken());
    }

    @Test
    @DisplayName("getJiraLoginPassToken() falls back to JIRA_LOGIN_PASS_TOKEN when email/token missing")
    void testJiraLoginPassTokenFallback() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.JIRA_EMAIL, "user@example.com");
        values.put(PropertyReader.JIRA_API_TOKEN, "  ");
        values.put(PropertyReader.JIRA_LOGIN_PASS_TOKEN, "legacy-token");
        override(values);

        assertEquals("legacy-token", propertyReader.getJiraLoginPassToken());
    }

    @Test
    @DisplayName("Jira pass-through getters resolve via overrides")
    void testJiraSimpleGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.JIRA_EMAIL, "user@example.com");
        values.put(PropertyReader.JIRA_API_TOKEN, "api-token");
        values.put(PropertyReader.JIRA_BASE_PATH, "https://jira.example.com");
        values.put(PropertyReader.JIRA_AUTH_TYPE, "Basic");
        values.put(PropertyReader.JIRA_EXTRA_FIELDS_PROJECT, "PROJ");
        values.put(PropertyReader.JIRA_EXTRA_FIELDS, "fieldA, fieldB");
        override(values);

        assertEquals("user@example.com", propertyReader.getJiraEmail());
        assertEquals("api-token", propertyReader.getJiraApiToken());
        assertEquals("https://jira.example.com", propertyReader.getJiraBasePath());
        assertEquals("Basic", propertyReader.getJiraAuthType());
        assertEquals("PROJ", propertyReader.getJiraExtraFieldsProject());
        assertArrayEquals(new String[]{"fieldA", " fieldB"}, propertyReader.getJiraExtraFields());
    }

    @Test
    @DisplayName("Jira boolean flags honor overrides")
    void testJiraBooleanFlags() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.JIRA_WAIT_BEFORE_PERFORM, "true");
        values.put(PropertyReader.JIRA_LOGGING_ENABLED, "TRUE");
        values.put(PropertyReader.JIRA_CLEAR_CACHE, "false");
        values.put("JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES", "yes");
        override(values);

        assertTrue(propertyReader.isJiraWaitBeforePerform());
        assertTrue(propertyReader.isJiraLoggingEnabled());
        assertFalse(propertyReader.isJiraClearCache());
        assertFalse(propertyReader.isJiraTransformCustomFieldsToNames());
    }

    @Test
    @DisplayName("Jira boolean flags default to false when unset")
    void testJiraBooleanFlagsDefaults() {
        // Empty-string overrides shadow any real environment variables and behave as "unset"
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.JIRA_WAIT_BEFORE_PERFORM, "");
        values.put(PropertyReader.JIRA_LOGGING_ENABLED, "");
        values.put(PropertyReader.JIRA_CLEAR_CACHE, "");
        values.put("JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES", "");
        override(values);
        assertFalse(propertyReader.isJiraWaitBeforePerform());
        assertFalse(propertyReader.isJiraLoggingEnabled());
        assertFalse(propertyReader.isJiraClearCache());
        assertFalse(propertyReader.isJiraTransformCustomFieldsToNames());
    }

    @Test
    @DisplayName("getJiraMaxSearchResults() parses a valid override")
    void testJiraMaxSearchResultsValid() {
        override("JIRA_MAX_SEARCH_RESULTS", " 250 ");
        assertEquals(250, propertyReader.getJiraMaxSearchResults());
    }

    @Test
    @DisplayName("getJiraMaxSearchResults() returns -1 for a non-numeric override")
    void testJiraMaxSearchResultsInvalid() {
        override("JIRA_MAX_SEARCH_RESULTS", "not-a-number");
        assertEquals(-1, propertyReader.getJiraMaxSearchResults());
    }

    // -------------------------------------------------------------------------
    // Xray
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Xray pass-through getters resolve via overrides")
    void testXraySimpleGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.XRAY_CLIENT_ID, "client-id");
        values.put(PropertyReader.XRAY_CLIENT_SECRET, "client-secret");
        values.put(PropertyReader.XRAY_BASE_PATH, "https://xray.example.com");
        override(values);

        assertEquals("client-id", propertyReader.getXrayClientId());
        assertEquals("client-secret", propertyReader.getXrayClientSecret());
        assertEquals("https://xray.example.com", propertyReader.getXrayBasePath());
    }

    @Test
    @DisplayName("Xray and cache boolean flags honor overrides")
    void testXrayAndCacheBooleanFlags() {
        Map<String, String> values = new HashMap<>();
        values.put("XRAY_CACHE_POST_REQUESTS_ENABLED", "true");
        values.put("DMTOOLS_CACHE_ENABLED", "true");
        values.put("XRAY_CACHE_GET_REQUESTS_ENABLED", "true");
        values.put(PropertyReader.XRAY_PARALLEL_FETCH_ENABLED, "true");
        values.put(PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT, "false");
        override(values);

        assertTrue(propertyReader.isXrayCachePostRequestsEnabled());
        assertTrue(propertyReader.isCacheEnabled());
        assertTrue(propertyReader.isXrayCacheGetRequestsEnabled());
        assertTrue(propertyReader.isXrayParallelFetchEnabled());
        assertFalse(propertyReader.isXrayEnrichmentEnabledByDefault());
    }

    @Test
    @DisplayName("Xray numeric settings parse valid overrides")
    void testXrayNumericValid() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.XRAY_PARALLEL_BATCH_SIZE, "50");
        values.put(PropertyReader.XRAY_PARALLEL_THREADS, "8");
        values.put(PropertyReader.XRAY_PARALLEL_DELAY_MS, "1500");
        override(values);

        assertEquals(50, propertyReader.getXrayParallelBatchSize());
        assertEquals(8, propertyReader.getXrayParallelThreads());
        assertEquals(1500L, propertyReader.getXrayParallelDelayMs());
    }

    @Test
    @DisplayName("Xray numeric settings fall back to defaults for non-numeric overrides")
    void testXrayNumericInvalid() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.XRAY_PARALLEL_BATCH_SIZE, "abc");
        values.put(PropertyReader.XRAY_PARALLEL_THREADS, "abc");
        values.put(PropertyReader.XRAY_PARALLEL_DELAY_MS, "abc");
        override(values);

        assertEquals(100, propertyReader.getXrayParallelBatchSize());
        assertEquals(2, propertyReader.getXrayParallelThreads());
        assertEquals(500L, propertyReader.getXrayParallelDelayMs());
    }

    @Test
    @DisplayName("getSleepTimeRequest() parses a valid override")
    void testSleepTimeRequest() {
        override("SLEEP_TIME_REQUEST", "750");
        assertEquals(750L, propertyReader.getSleepTimeRequest());
    }

    // -------------------------------------------------------------------------
    // Rally / ADO / Bitbucket / GitHub / GitLab
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rally getters resolve via overrides")
    void testRallyGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.RALLY_TOKEN, "rally-token");
        values.put(PropertyReader.RALLY_PATH, "https://rally.example.com");
        override(values);

        assertEquals("rally-token", propertyReader.getRallyToken());
        assertEquals("https://rally.example.com", propertyReader.getRallyPath());
    }

    @Test
    @DisplayName("ADO getters resolve via overrides; base path has a default")
    void testAdoGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.ADO_ORGANIZATION, "org");
        values.put(PropertyReader.ADO_PROJECT, "proj");
        values.put(PropertyReader.ADO_PAT_TOKEN, "pat");
        values.put(PropertyReader.ADO_BASE_PATH, "");
        override(values);

        assertEquals("org", propertyReader.getAdoOrganization());
        assertEquals("proj", propertyReader.getAdoProject());
        assertEquals("pat", propertyReader.getAdoPatToken());
        assertEquals("https://dev.azure.com", propertyReader.getAdoBasePath());

        override(PropertyReader.ADO_BASE_PATH, "https://ado.example.com");
        assertEquals("https://ado.example.com", propertyReader.getAdoBasePath());
    }

    @Test
    @DisplayName("Bitbucket getters resolve via overrides")
    void testBitbucketGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.BITBUCKET_TOKEN, "bb-token");
        values.put(PropertyReader.BITBUCKET_API_VERSION, "2.0");
        values.put(PropertyReader.BITBUCKET_WORKSPACE, "ws");
        values.put(PropertyReader.BITBUCKET_REPOSITORY, "repo");
        values.put(PropertyReader.BITBUCKET_BRANCH, "main");
        values.put(PropertyReader.BITBUCKET_BASE_PATH, "https://api.bitbucket.org");
        override(values);

        assertEquals("bb-token", propertyReader.getBitbucketToken());
        assertEquals("2.0", propertyReader.getBitbucketApiVersion());
        assertEquals("ws", propertyReader.getBitbucketWorkspace());
        assertEquals("repo", propertyReader.getBitbucketRepository());
        assertEquals("main", propertyReader.getBitbucketBranch());
        assertEquals("https://api.bitbucket.org", propertyReader.getBitbucketBasePath());
    }

    @Test
    @DisplayName("GitHub getters resolve via overrides; base path has a default")
    void testGithubGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.SOURCE_GITHUB_TOKEN, "gh-token");
        values.put(PropertyReader.SOURCE_GITHUB_WORKSPACE, "gh-ws");
        values.put(PropertyReader.SOURCE_GITHUB_REPOSITORY, "gh-repo");
        values.put(PropertyReader.SOURCE_GITHUB_BRANCH, "develop");
        values.put(PropertyReader.SOURCE_GITHUB_BASE_PATH, "");
        override(values);

        assertEquals("gh-token", propertyReader.getGithubToken());
        assertEquals("gh-ws", propertyReader.getGithubWorkspace());
        assertEquals("gh-repo", propertyReader.getGithubRepository());
        assertEquals("develop", propertyReader.getGithubBranch());
        assertEquals("https://api.github.com", propertyReader.getGithubBasePath());

        override(PropertyReader.SOURCE_GITHUB_BASE_PATH, "https://ghe.example.com/api/v3");
        assertEquals("https://ghe.example.com/api/v3", propertyReader.getGithubBasePath());
    }

    @Test
    @DisplayName("GitLab getters resolve via overrides")
    void testGitLabGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.GITLAB_TOKEN, "gl-token");
        values.put(PropertyReader.GITLAB_WORKSPACE, "gl-ws");
        values.put(PropertyReader.GITLAB_REPOSITORY, "gl-repo");
        values.put(PropertyReader.GITLAB_BRANCH, "main");
        values.put(PropertyReader.GITLAB_BASE_PATH, "https://gitlab.example.com");
        override(values);

        assertEquals("gl-token", propertyReader.getGitLabToken());
        assertEquals("gl-ws", propertyReader.getGitLabWorkspace());
        assertEquals("gl-repo", propertyReader.getGitLabRepository());
        assertEquals("main", propertyReader.getGitLabBranch());
        assertEquals("https://gitlab.example.com", propertyReader.getGitLabBasePath());
    }

    // -------------------------------------------------------------------------
    // Confluence
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getConfluenceLoginPassToken() uses Basic auth by default (base64 email:token)")
    void testConfluenceBasicAuth() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.CONFLUENCE_EMAIL, "user@example.com");
        values.put(PropertyReader.CONFLUENCE_API_TOKEN, "conf-token");
        override(values);

        String expected = Base64.getEncoder().encodeToString("user@example.com:conf-token".getBytes());
        assertEquals(expected, propertyReader.getConfluenceLoginPassToken());
        assertEquals("Basic", propertyReader.getConfluenceAuthType());
    }

    @Test
    @DisplayName("getConfluenceLoginPassToken() returns the raw token for Bearer auth")
    void testConfluenceBearerAuth() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.CONFLUENCE_EMAIL, "user@example.com");
        values.put(PropertyReader.CONFLUENCE_API_TOKEN, " bearer-token ");
        values.put(PropertyReader.CONFLUENCE_AUTH_TYPE, "bearer");
        override(values);

        assertEquals("bearer-token", propertyReader.getConfluenceLoginPassToken());
        assertEquals("bearer", propertyReader.getConfluenceAuthType());
    }

    @Test
    @DisplayName("getConfluenceLoginPassToken() falls back to CONFLUENCE_LOGIN_PASS_TOKEN")
    void testConfluenceTokenFallback() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.CONFLUENCE_EMAIL, "");
        values.put(PropertyReader.CONFLUENCE_API_TOKEN, "");
        values.put(PropertyReader.CONFLUENCE_LOGIN_PASS_TOKEN, "legacy-conf-token");
        override(values);
        assertEquals("legacy-conf-token", propertyReader.getConfluenceLoginPassToken());
    }

    @Test
    @DisplayName("Confluence pass-through getters resolve via overrides")
    void testConfluenceSimpleGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.CONFLUENCE_BASE_PATH, "https://conf.example.com");
        values.put(PropertyReader.CONFLUENCE_EMAIL, "user@example.com");
        values.put(PropertyReader.CONFLUENCE_API_TOKEN, "conf-token");
        values.put(PropertyReader.CONFLUENCE_GRAPHQL_PATH, "https://conf.example.com/graphql");
        values.put(PropertyReader.CONFLUENCE_DEFAULT_SPACE, "DEV");
        override(values);

        assertEquals("https://conf.example.com", propertyReader.getConfluenceBasePath());
        assertEquals("user@example.com", propertyReader.getConfluenceEmail());
        assertEquals("conf-token", propertyReader.getConfluenceApiToken());
        assertEquals("https://conf.example.com/graphql", propertyReader.getConfluenceGraphQLPath());
        assertEquals("DEV", propertyReader.getConfluenceDefaultSpace());
    }

    // -------------------------------------------------------------------------
    // DIAL / AI models / Figma
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getDialBathPath() prefers DIAL_BASE_PATH and falls back to DIAL_BATH_PATH")
    void testDialBasePathPriority() {
        override(PropertyReader.DIAL_BASE_PATH, "https://dial.example.com");
        assertEquals("https://dial.example.com", propertyReader.getDialBathPath());

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.DIAL_BASE_PATH, "");
        values.put("DIAL_BATH_PATH", "https://dial-legacy.example.com");
        override(values);
        assertEquals("https://dial-legacy.example.com", propertyReader.getDialBathPath());
    }

    @Test
    @DisplayName("DIAL and model getters resolve via overrides")
    void testDialAndModelGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.DIAL_API_KEY, "dial-key");
        values.put("DIAL_MODEL", "dial-model");
        values.put(PropertyReader.DIAL_API_VERSION, "2024-01-01");
        values.put("CODE_AI_MODEL", "code-model");
        values.put("TEST_AI_MODEL", "test-model");
        override(values);

        assertEquals("dial-key", propertyReader.getDialIApiKey());
        assertEquals("dial-model", propertyReader.getDialModel());
        assertEquals("2024-01-01", propertyReader.getDialApiVersion());
        assertEquals("code-model", propertyReader.getCodeAIModel());
        assertEquals("test-model", propertyReader.getTestAIModel());
    }

    @Test
    @DisplayName("Figma getters resolve via overrides")
    void testFigmaGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.FIGMA_BASE_PATH, "https://api.figma.com");
        values.put(PropertyReader.FIGMA_TOKEN, "figma-token");
        values.put(PropertyReader.FIGMA_CLIENT_ID, "figma-client-id");
        values.put(PropertyReader.FIGMA_CLIENT_SECRET, "figma-client-secret");
        values.put(PropertyReader.FIGMA_OAUTH_REFRESH_TOKEN, "refresh");
        values.put(PropertyReader.FIGMA_OAUTH_ACCESS_TOKEN, "access");
        values.put(PropertyReader.FIGMA_REDIRECT_URI, "http://localhost:8080/callback");
        override(values);

        assertEquals("https://api.figma.com", propertyReader.getFigmaBasePath());
        assertEquals("figma-token", propertyReader.getFigmaApiKey());
        assertEquals("figma-client-id", propertyReader.getFigmaClientId());
        assertEquals("figma-client-secret", propertyReader.getFigmaClientSecret());
        assertEquals("refresh", propertyReader.getFigmaOAuth2RefreshToken());
        assertEquals("access", propertyReader.getFigmaOAuth2AccessToken());
        assertEquals("http://localhost:8080/callback", propertyReader.getFigmaRedirectUri());
    }

    @Test
    @DisplayName("getFigmaOAuth2Scopes() prefers FIGMA_SCOPE and falls back to FIGMA_OAUTH_SCOPES")
    void testFigmaScopesPriority() {
        override(PropertyReader.FIGMA_SCOPE, " file_read ");
        assertEquals(" file_read ", propertyReader.getFigmaOAuth2Scopes());

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.FIGMA_SCOPE, "");
        values.put("FIGMA_OAUTH_SCOPES", "legacy-scope");
        override(values);
        assertEquals("legacy-scope", propertyReader.getFigmaOAuth2Scopes());
    }

    // -------------------------------------------------------------------------
    // Ticket weights / story points / dividers
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getDefaultTicketWeightIfNoSPs() parses a valid override")
    void testDefaultTicketWeight() {
        override(PropertyReader.DEFAULT_TICKET_WEIGHT_IF_NO_SP, "3");
        assertEquals(3, propertyReader.getDefaultTicketWeightIfNoSPs());
    }

    @Test
    @DisplayName("getDefaultTicketStoryPointsFields() splits a comma-separated override")
    void testDefaultTicketStoryPointsFields() {
        override("DEFAULT_TICKET_STORY_POINTS_FIELDS", "customfield_1,customfield_2");
        assertArrayEquals(new String[]{"customfield_1", "customfield_2"},
            propertyReader.getDefaultTicketStoryPointsFields());
    }

    @Test
    @DisplayName("getDefaultTicketStoryPointsFieldHumanNames() returns explicit override")
    void testStoryPointsFieldHumanNamesExplicit() {
        override("DEFAULT_TICKET_STORY_POINTS_FIELD_NAMES", "Story Points,SP");
        assertArrayEquals(new String[]{"Story Points", "SP"},
            propertyReader.getDefaultTicketStoryPointsFieldHumanNames());
    }

    @Test
    @DisplayName("getDefaultTicketStoryPointsFieldHumanNames() auto-detects from JIRA_EXTRA_FIELDS")
    void testStoryPointsFieldHumanNamesAutoDetect() {
        Map<String, String> values = new HashMap<>();
        values.put("DEFAULT_TICKET_STORY_POINTS_FIELD_NAMES", "");
        values.put(PropertyReader.JIRA_EXTRA_FIELDS, "Summary, Story Points estimate ,Other");
        override(values);
        assertArrayEquals(new String[]{"Story Points estimate"},
            propertyReader.getDefaultTicketStoryPointsFieldHumanNames());
    }

    @Test
    @DisplayName("getDefaultTicketStoryPointsFieldHumanNames() returns null when nothing matches")
    void testStoryPointsFieldHumanNamesNone() {
        Map<String, String> values = new HashMap<>();
        values.put("DEFAULT_TICKET_STORY_POINTS_FIELD_NAMES", "");
        values.put(PropertyReader.JIRA_EXTRA_FIELDS, "Summary,Priority");
        override(values);
        assertNull(propertyReader.getDefaultTicketStoryPointsFieldHumanNames());
    }

    @Test
    @DisplayName("Divider getters parse valid overrides")
    void testDividers() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.LINES_OF_CODE_DIVIDER, "2.5");
        values.put(PropertyReader.TIME_SPENT_ON_DIVIDER, "3.5");
        override(values);

        assertEquals(2.5, propertyReader.getLinesOfCodeDivider());
        assertEquals(3.5, propertyReader.getTimeSpentOnDivider());
    }

    @Test
    @DisplayName("getTicketFieldsChangedDivider() prefers field-specific value over the default")
    void testTicketFieldsChangedDivider() {
        Map<String, String> values = new HashMap<>();
        values.put("TICKET_FIELDS_CHANGED_DIVIDER_STATUS", "4.0");
        values.put(PropertyReader.TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT, "2.0");
        override(values);

        assertEquals(4.0, propertyReader.getTicketFieldsChangedDivider("status"));
        assertEquals(2.0, propertyReader.getTicketFieldsChangedDivider("priority"));
    }

    @Test
    @DisplayName("isReadPullRequestDiff() honors an explicit false override")
    void testReadPullRequestDiffFalse() {
        override("IS_READ_PULL_REQUEST_DIFF", "false");
        assertFalse(propertyReader.isReadPullRequestDiff());
    }

    // -------------------------------------------------------------------------
    // AI retry and prompt chunk configuration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AI retry settings parse valid overrides")
    void testAiRetryValid() {
        Map<String, String> values = new HashMap<>();
        values.put("AI_RETRY_AMOUNT", "7");
        values.put("AI_RETRY_DELAY_STEP", "1234");
        override(values);

        assertEquals(7, propertyReader.getAiRetryAmount());
        assertEquals(1234L, propertyReader.getAiRetryDelayStep());
    }

    @Test
    @DisplayName("AI retry settings fall back to defaults for non-numeric overrides")
    void testAiRetryInvalid() {
        Map<String, String> values = new HashMap<>();
        values.put("AI_RETRY_AMOUNT", "abc");
        values.put("AI_RETRY_DELAY_STEP", "abc");
        override(values);

        assertEquals(3, propertyReader.getAiRetryAmount());
        assertEquals(20000L, propertyReader.getAiRetryDelayStep());
    }

    @Test
    @DisplayName("Default branches: null (absent keys) and empty (shadowed keys) fall back to defaults")
    void testDefaultBranches() {
        // Keys present in the repo dmtools.env are shadowed with "" to deterministically
        // exercise the empty-value default branch; absent keys exercise the null branch.
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.LINES_OF_CODE_DIVIDER, "");
        values.put("PROMPT_CHUNK_TOKEN_LIMIT", "");
        values.put(PropertyReader.DEFAULT_TICKET_WEIGHT_IF_NO_SP, "");
        values.put(PropertyReader.GEMINI_BASE_PATH_KEY, "");
        override(values);

        assertEquals(1d, propertyReader.getLinesOfCodeDivider());
        assertEquals(50000, propertyReader.getPromptChunkTokenLimit());
        assertEquals(-1, propertyReader.getDefaultTicketWeightIfNoSPs());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models",
            propertyReader.getGeminiBasePath());

        assertNull(propertyReader.getCliOutput());
        assertNull(propertyReader.getFileReadAllowedPaths());
        assertEquals(-1, propertyReader.getJiraMaxSearchResults());
        assertNull(propertyReader.getJiraExtraFields());
        assertFalse(propertyReader.isXrayCachePostRequestsEnabled());
        assertFalse(propertyReader.isXrayCacheGetRequestsEnabled());
        assertFalse(propertyReader.isXrayParallelFetchEnabled());
        assertTrue(propertyReader.isXrayEnrichmentEnabledByDefault());
        assertEquals(100, propertyReader.getXrayParallelBatchSize());
        assertEquals(2, propertyReader.getXrayParallelThreads());
        assertEquals(500L, propertyReader.getXrayParallelDelayMs());
        assertEquals(300L, propertyReader.getSleepTimeRequest());
        assertNull(propertyReader.getDefaultTicketStoryPointsFields());
        assertEquals(1d, propertyReader.getTimeSpentOnDivider());
        assertTrue(propertyReader.isReadPullRequestDiff());
        assertEquals(3, propertyReader.getAiRetryAmount());
        assertEquals(20000L, propertyReader.getAiRetryDelayStep());
        assertEquals(4L * 1024 * 1024, propertyReader.getPromptChunkMaxSingleFileSize());
        assertEquals(4L * 1024 * 1024, propertyReader.getPromptChunkMaxTotalFilesSize());
        assertEquals(10, propertyReader.getPromptChunkMaxFiles());
        assertNull(propertyReader.getJsSecretsKeys());
        assertFalse(propertyReader.isCacheManagerLoggingEnabled());
        assertFalse(propertyReader.isTestRailLoggingEnabled());
    }

    @Test
    @DisplayName("Prompt chunk settings parse valid overrides (MB converted to bytes)")
    void testPromptChunkValid() {
        Map<String, String> values = new HashMap<>();
        values.put("PROMPT_CHUNK_TOKEN_LIMIT", "100000");
        values.put("PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB", "8");
        values.put("PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE_MB", "16");
        values.put("PROMPT_CHUNK_MAX_FILES", "25");
        override(values);

        assertEquals(100000, propertyReader.getPromptChunkTokenLimit());
        assertEquals(8L * 1024 * 1024, propertyReader.getPromptChunkMaxSingleFileSize());
        assertEquals(16L * 1024 * 1024, propertyReader.getPromptChunkMaxTotalFilesSize());
        assertEquals(25, propertyReader.getPromptChunkMaxFiles());
    }

    @Test
    @DisplayName("Prompt chunk settings fall back to defaults for non-numeric overrides")
    void testPromptChunkInvalid() {
        Map<String, String> values = new HashMap<>();
        values.put("PROMPT_CHUNK_TOKEN_LIMIT", "abc");
        values.put("PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB", "abc");
        values.put("PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE_MB", "abc");
        values.put("PROMPT_CHUNK_MAX_FILES", "abc");
        override(values);

        assertEquals(50000, propertyReader.getPromptChunkTokenLimit());
        assertEquals(4L * 1024 * 1024, propertyReader.getPromptChunkMaxSingleFileSize());
        assertEquals(4L * 1024 * 1024, propertyReader.getPromptChunkMaxTotalFilesSize());
        assertEquals(10, propertyReader.getPromptChunkMaxFiles());
    }

    @Test
    @DisplayName("AI attachment settings parse valid overrides")
    void testAiAttachmentValid() {
        Map<String, String> values = new HashMap<>();
        values.put("AI_ATTACHMENT_MAX_SIZE_MB", " 5 ");
        values.put("AI_ATTACHMENT_ALLOWED_EXTENSIONS", " PNG , jpg ,,PDF ");
        override(values);

        assertEquals(5L * 1024 * 1024, propertyReader.getAIAttachmentMaxSizeBytes());
        Set<String> extensions = propertyReader.getAIAttachmentAllowedExtensions();
        assertEquals(Set.of("png", "jpg", "pdf"), extensions);
        assertThrows(UnsupportedOperationException.class, () -> extensions.add("gif"));
    }

    @Test
    @DisplayName("AI attachment settings fall back to defaults when unset or invalid")
    void testAiAttachmentDefaultsAndInvalid() {
        Map<String, String> values = new HashMap<>();
        values.put("AI_ATTACHMENT_MAX_SIZE_MB", "");
        values.put("AI_ATTACHMENT_ALLOWED_EXTENSIONS", "");
        override(values);
        assertEquals(0, propertyReader.getAIAttachmentMaxSizeBytes());
        assertTrue(propertyReader.getAIAttachmentAllowedExtensions().isEmpty());

        override("AI_ATTACHMENT_MAX_SIZE_MB", "abc");
        assertEquals(0, propertyReader.getAIAttachmentMaxSizeBytes());
    }

    // -------------------------------------------------------------------------
    // JSAIClient
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JSAIClient getters resolve via overrides")
    void testJsClientGetters() {
        Map<String, String> values = new HashMap<>();
        values.put("JSAI_SCRIPT_PATH", "/scripts/ai.js");
        values.put("JSAI_SCRIPT_CONTENT", "print('hi')");
        values.put("JSAI_CLIENT_NAME", "CustomJsClient");
        values.put("JSAI_DEFAULT_MODEL", "gpt-4o");
        values.put("JSAI_BASE_PATH", "https://js.example.com");
        values.put("JSAI_SECRETS_KEYS", "KEY1, KEY2");
        override(values);

        assertEquals("/scripts/ai.js", propertyReader.getJsScriptPath());
        assertEquals("print('hi')", propertyReader.getJsScriptContent());
        assertEquals("CustomJsClient", propertyReader.getJsClientName());
        assertEquals("gpt-4o", propertyReader.getJsDefaultModel());
        assertEquals("https://js.example.com", propertyReader.getJsBasePath());
        assertArrayEquals(new String[]{"KEY1", " KEY2"}, propertyReader.getJsSecretsKeys());
    }

    // -------------------------------------------------------------------------
    // Gemini
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getGeminiDefaultModel() prefers GEMINI_MODEL over GEMINI_DEFAULT_MODEL")
    void testGeminiModelPriority() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.GEMINI_API_KEY, "gemini-key");
        values.put(PropertyReader.GEMINI_MODEL_KEY, "gemini-2.0");
        values.put(PropertyReader.GEMINI_DEFAULT_MODEL_KEY, "gemini-1.5");
        override(values);

        assertEquals("gemini-key", propertyReader.getGeminiApiKey());
        assertEquals("gemini-2.0", propertyReader.getGeminiDefaultModel());
    }

    @Test
    @DisplayName("getGeminiDefaultModel() skips $-placeholder values and uses GEMINI_DEFAULT_MODEL")
    void testGeminiModelPlaceholderFallback() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.GEMINI_MODEL_KEY, "${GEMINI_MODEL}");
        values.put(PropertyReader.GEMINI_DEFAULT_MODEL_KEY, "gemini-1.5-pro");
        override(values);

        assertEquals("gemini-1.5-pro", propertyReader.getGeminiDefaultModel());
    }

    @Test
    @DisplayName("getGeminiDefaultModel() returns null when only $-placeholders are configured")
    void testGeminiModelAllPlaceholders() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.GEMINI_MODEL_KEY, "$UNRESOLVED");
        values.put(PropertyReader.GEMINI_DEFAULT_MODEL_KEY, "  ");
        override(values);

        assertNull(propertyReader.getGeminiDefaultModel());
    }

    // -------------------------------------------------------------------------
    // Teams / SharePoint
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Teams getters resolve via overrides and expose defaults")
    void testTeamsGetters() {
        Map<String, String> empty = new HashMap<>();
        empty.put(PropertyReader.TEAMS_TENANT_ID, "");
        empty.put(PropertyReader.TEAMS_SCOPES, "");
        empty.put(PropertyReader.TEAMS_AUTH_METHOD, "");
        empty.put(PropertyReader.TEAMS_AUTH_PORT, "");
        empty.put(PropertyReader.TEAMS_TOKEN_CACHE_PATH, "");
        empty.put("SHAREPOINT_SCOPES", "");
        override(empty);
        assertEquals("common", propertyReader.getTeamsTenantId());
        assertTrue(propertyReader.getTeamsScopes().contains("Chat.Read"));
        assertEquals("device", propertyReader.getTeamsAuthMethod());
        assertEquals(8080, propertyReader.getTeamsAuthPort());
        assertEquals("./teams.token", propertyReader.getTeamsTokenCachePath());
        assertTrue(propertyReader.getSharePointScopes().contains("Files.Read.All"));

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.TEAMS_CLIENT_ID, "teams-client-id");
        values.put(PropertyReader.TEAMS_TENANT_ID, "tenant-123");
        values.put(PropertyReader.TEAMS_SCOPES, "User.Read");
        values.put(PropertyReader.TEAMS_AUTH_METHOD, "browser");
        values.put(PropertyReader.TEAMS_AUTH_PORT, "9090");
        values.put(PropertyReader.TEAMS_TOKEN_CACHE_PATH, "/tmp/teams.token");
        values.put(PropertyReader.TEAMS_REFRESH_TOKEN, "refresh-token");
        values.put("SHAREPOINT_SCOPES", "Files.ReadWrite.All");
        override(values);

        assertEquals("teams-client-id", propertyReader.getTeamsClientId());
        assertEquals("tenant-123", propertyReader.getTeamsTenantId());
        assertEquals("User.Read", propertyReader.getTeamsScopes());
        assertEquals("browser", propertyReader.getTeamsAuthMethod());
        assertEquals(9090, propertyReader.getTeamsAuthPort());
        assertEquals("/tmp/teams.token", propertyReader.getTeamsTokenCachePath());
        assertEquals("refresh-token", propertyReader.getTeamsRefreshToken());
        assertEquals("Files.ReadWrite.All", propertyReader.getSharePointScopes());
    }

    @Test
    @DisplayName("getTeamsAuthPort() falls back to 8080 for a non-numeric override")
    void testTeamsAuthPortInvalid() {
        override(PropertyReader.TEAMS_AUTH_PORT, "abc");
        assertEquals(8080, propertyReader.getTeamsAuthPort());
    }

    // -------------------------------------------------------------------------
    // Ollama
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Ollama getters resolve via overrides and expose defaults")
    void testOllamaGetters() {
        Map<String, String> empty = new HashMap<>();
        empty.put(PropertyReader.OLLAMA_BASE_PATH, "");
        empty.put(PropertyReader.OLLAMA_NUM_CTX, "");
        empty.put(PropertyReader.OLLAMA_NUM_PREDICT, "");
        override(empty);
        assertEquals("http://localhost:11434", propertyReader.getOllamaBasePath());
        assertEquals(16384, propertyReader.getOllamaNumCtx());
        assertEquals(-1, propertyReader.getOllamaNumPredict());

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.OLLAMA_BASE_PATH, "http://ollama:11434");
        values.put(PropertyReader.OLLAMA_MODEL, "llama3");
        values.put(PropertyReader.OLLAMA_NUM_CTX, " 8192 ");
        values.put(PropertyReader.OLLAMA_NUM_PREDICT, "256");
        values.put("OLLAMA_CUSTOM_HEADER_NAMES", "X-Auth");
        values.put("OLLAMA_CUSTOM_HEADER_VALUES", "token");
        override(values);

        assertEquals("http://ollama:11434", propertyReader.getOllamaBasePath());
        assertEquals("llama3", propertyReader.getOllamaModel());
        assertEquals(8192, propertyReader.getOllamaNumCtx());
        assertEquals(256, propertyReader.getOllamaNumPredict());
        assertEquals("X-Auth", propertyReader.getOllamaCustomHeaderNames());
        assertEquals("token", propertyReader.getOllamaCustomHeaderValues());
    }

    @Test
    @DisplayName("Ollama numeric settings fall back to defaults for non-numeric overrides")
    void testOllamaNumericInvalid() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.OLLAMA_NUM_CTX, "abc");
        values.put(PropertyReader.OLLAMA_NUM_PREDICT, "abc");
        override(values);

        assertEquals(16384, propertyReader.getOllamaNumCtx());
        assertEquals(-1, propertyReader.getOllamaNumPredict());
    }

    // -------------------------------------------------------------------------
    // Anthropic
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Anthropic getters resolve via overrides and expose defaults")
    void testAnthropicGetters() {
        Map<String, String> empty = new HashMap<>();
        empty.put(PropertyReader.ANTHROPIC_BASE_PATH, "");
        empty.put(PropertyReader.ANTHROPIC_MAX_TOKENS, "");
        override(empty);
        assertEquals("https://api.anthropic.com/v1/messages", propertyReader.getAnthropicBasePath());
        assertEquals(4096, propertyReader.getAnthropicMaxTokens());

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.ANTHROPIC_BASE_PATH, "https://anthropic.example.com");
        values.put(PropertyReader.ANTHROPIC_MODEL, "claude-3");
        values.put(PropertyReader.ANTHROPIC_MAX_TOKENS, " 8192 ");
        values.put(PropertyReader.ANTHROPIC_CUSTOM_HEADER_NAMES, "X-Key");
        values.put(PropertyReader.ANTHROPIC_CUSTOM_HEADER_VALUES, "secret");
        override(values);

        assertEquals("https://anthropic.example.com", propertyReader.getAnthropicBasePath());
        assertEquals("claude-3", propertyReader.getAnthropicModel());
        assertEquals(8192, propertyReader.getAnthropicMaxTokens());
        assertEquals("X-Key", propertyReader.getAnthropicCustomHeaderNames());
        assertEquals("secret", propertyReader.getAnthropicCustomHeaderValues());
    }

    @Test
    @DisplayName("getAnthropicMaxTokens() falls back to 4096 for a non-numeric override")
    void testAnthropicMaxTokensInvalid() {
        override(PropertyReader.ANTHROPIC_MAX_TOKENS, "abc");
        assertEquals(4096, propertyReader.getAnthropicMaxTokens());
    }

    // -------------------------------------------------------------------------
    // Bedrock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getBedrockBasePath() prefers explicit base path, then derives from region")
    void testBedrockBasePath() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.BEDROCK_BASE_PATH, "https://bedrock.example.com");
        values.put(PropertyReader.BEDROCK_REGION, "us-east-1");
        override(values);
        assertEquals("https://bedrock.example.com", propertyReader.getBedrockBasePath());

        values = new HashMap<>();
        values.put(PropertyReader.BEDROCK_BASE_PATH, "");
        values.put(PropertyReader.BEDROCK_REGION, "eu-west-1");
        override(values);
        assertEquals("https://bedrock-runtime.eu-west-1.amazonaws.com", propertyReader.getBedrockBasePath());

        values.put(PropertyReader.BEDROCK_REGION, "");
        override(values);
        assertNull(propertyReader.getBedrockBasePath());
    }

    @Test
    @DisplayName("getBedrockBearerToken() prefers AWS_BEARER_TOKEN_BEDROCK and skips $-placeholders")
    void testBedrockBearerToken() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.AWS_BEARER_TOKEN_BEDROCK, "aws-bearer");
        values.put(PropertyReader.BEDROCK_BEARER_TOKEN, "bedrock-bearer");
        override(values);
        assertEquals("aws-bearer", propertyReader.getBedrockBearerToken());

        values = new HashMap<>();
        values.put(PropertyReader.AWS_BEARER_TOKEN_BEDROCK, "${AWS_BEARER_TOKEN_BEDROCK}");
        values.put(PropertyReader.BEDROCK_BEARER_TOKEN, "bedrock-bearer");
        override(values);
        assertEquals("bedrock-bearer", propertyReader.getBedrockBearerToken());
    }

    @Test
    @DisplayName("Bedrock pass-through getters resolve via overrides")
    void testBedrockSimpleGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.BEDROCK_REGION, "us-west-2");
        values.put(PropertyReader.BEDROCK_MODEL_ID, "anthropic.claude-v2");
        values.put(PropertyReader.BEDROCK_ACCESS_KEY_ID, "access-key");
        values.put(PropertyReader.BEDROCK_SECRET_ACCESS_KEY, "secret-key");
        values.put(PropertyReader.BEDROCK_SESSION_TOKEN, "session-token");
        override(values);

        assertEquals("us-west-2", propertyReader.getBedrockRegion());
        assertEquals("anthropic.claude-v2", propertyReader.getBedrockModelId());
        assertEquals("access-key", propertyReader.getBedrockAccessKeyId());
        assertEquals("secret-key", propertyReader.getBedrockSecretAccessKey());
        assertEquals("session-token", propertyReader.getBedrockSessionToken());
    }

    @Test
    @DisplayName("getBedrockMaxTokens() validates minimum and numeric format")
    void testBedrockMaxTokens() {
        override(PropertyReader.BEDROCK_MAX_TOKENS, "");
        assertEquals(4096, propertyReader.getBedrockMaxTokens());

        override(PropertyReader.BEDROCK_MAX_TOKENS, " 2048 ");
        assertEquals(2048, propertyReader.getBedrockMaxTokens());

        override(PropertyReader.BEDROCK_MAX_TOKENS, "0");
        assertEquals(4096, propertyReader.getBedrockMaxTokens());

        override(PropertyReader.BEDROCK_MAX_TOKENS, "abc");
        assertEquals(4096, propertyReader.getBedrockMaxTokens());
    }

    @Test
    @DisplayName("getBedrockTemperature() clamps to [0.0, 1.0] and validates format")
    void testBedrockTemperature() {
        override(PropertyReader.BEDROCK_TEMPERATURE, "");
        assertEquals(1.0, propertyReader.getBedrockTemperature());

        override(PropertyReader.BEDROCK_TEMPERATURE, "0.7");
        assertEquals(0.7, propertyReader.getBedrockTemperature());

        override(PropertyReader.BEDROCK_TEMPERATURE, "-0.5");
        assertEquals(1.0, propertyReader.getBedrockTemperature());

        override(PropertyReader.BEDROCK_TEMPERATURE, "1.5");
        assertEquals(1.0, propertyReader.getBedrockTemperature());

        override(PropertyReader.BEDROCK_TEMPERATURE, "abc");
        assertEquals(1.0, propertyReader.getBedrockTemperature());
    }

    // -------------------------------------------------------------------------
    // OpenAI
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OpenAI getters resolve via overrides and expose defaults")
    void testOpenAIGetters() {
        Map<String, String> empty = new HashMap<>();
        empty.put(PropertyReader.OPENAI_BASE_PATH, "");
        empty.put(PropertyReader.OPENAI_MAX_TOKENS, "");
        empty.put(PropertyReader.OPENAI_TEMPERATURE, "");
        empty.put(PropertyReader.OPENAI_MAX_TOKENS_PARAM_NAME, "");
        override(empty);
        assertEquals("https://api.openai.com/v1/chat/completions", propertyReader.getOpenAIBasePath());
        assertEquals(4096, propertyReader.getOpenAIMaxTokens());
        assertEquals(-1.0, propertyReader.getOpenAITemperature());
        assertEquals("max_completion_tokens", propertyReader.getOpenAIMaxTokensParamName());

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.OPENAI_API_KEY, "openai-key");
        values.put(PropertyReader.OPENAI_BASE_PATH, "https://openai.example.com");
        values.put(PropertyReader.OPENAI_MODEL, "gpt-4o");
        values.put(PropertyReader.OPENAI_MAX_TOKENS, " 2048 ");
        values.put(PropertyReader.OPENAI_MAX_TOKENS_PARAM_NAME, "max_tokens");
        values.put(PropertyReader.DEFAULT_LLM, "openai");
        values.put(PropertyReader.DEFAULT_TRACKER, "jira");
        override(values);

        assertEquals("openai-key", propertyReader.getOpenAIApiKey());
        assertEquals("https://openai.example.com", propertyReader.getOpenAIBasePath());
        assertEquals("gpt-4o", propertyReader.getOpenAIModel());
        assertEquals(2048, propertyReader.getOpenAIMaxTokens());
        assertEquals("max_tokens", propertyReader.getOpenAIMaxTokensParamName());
        assertEquals("openai", propertyReader.getDefaultLLM());
        assertEquals("jira", propertyReader.getDefaultTracker());
    }

    @Test
    @DisplayName("getOpenAIMaxTokens() falls back to 4096 for a non-numeric override")
    void testOpenAIMaxTokensInvalid() {
        override(PropertyReader.OPENAI_MAX_TOKENS, "abc");
        assertEquals(4096, propertyReader.getOpenAIMaxTokens());
    }

    @Test
    @DisplayName("getOpenAITemperature() keeps negatives, clamps above 2.0, validates format")
    void testOpenAITemperature() {
        override(PropertyReader.OPENAI_TEMPERATURE, "0.9");
        assertEquals(0.9, propertyReader.getOpenAITemperature());

        override(PropertyReader.OPENAI_TEMPERATURE, "-0.3");
        assertEquals(-0.3, propertyReader.getOpenAITemperature());

        override(PropertyReader.OPENAI_TEMPERATURE, "2.5");
        assertEquals(2.0, propertyReader.getOpenAITemperature());

        override(PropertyReader.OPENAI_TEMPERATURE, "abc");
        assertEquals(-1.0, propertyReader.getOpenAITemperature());
    }

    // -------------------------------------------------------------------------
    // Misc: images, cache manager logging, file-read allowlist, TestRail, Bitrise
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Image settings resolve via overrides and expose defaults")
    void testImageSettings() {
        Map<String, String> empty = new HashMap<>();
        empty.put(PropertyReader.IMAGE_MAX_DIMENSION, "");
        empty.put(PropertyReader.IMAGE_JPEG_QUALITY, "");
        override(empty);
        assertEquals(8000, propertyReader.getImageMaxDimension());
        assertEquals(0.9f, propertyReader.getImageJpegQuality());

        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.IMAGE_MAX_DIMENSION, "1024");
        values.put(PropertyReader.IMAGE_JPEG_QUALITY, "0.75");
        override(values);
        assertEquals(1024, propertyReader.getImageMaxDimension());
        assertEquals(0.75f, propertyReader.getImageJpegQuality());
    }

    @Test
    @DisplayName("isCacheManagerLoggingEnabled() honors overrides and defaults to false")
    void testCacheManagerLogging() {
        override("CACHE_MANAGER_LOGGING_ENABLED", "");
        assertFalse(propertyReader.isCacheManagerLoggingEnabled());

        override("CACHE_MANAGER_LOGGING_ENABLED", "true");
        assertTrue(propertyReader.isCacheManagerLoggingEnabled());
    }

    @Test
    @DisplayName("getFileReadAllowedPaths() resolves via overrides")
    void testFileReadAllowedPaths() {
        override(PropertyReader.DMTOOLS_FILE_READ_ALLOWED_PATHS, "../.dmtools/**,../config/**");
        assertEquals("../.dmtools/**,../config/**", propertyReader.getFileReadAllowedPaths());
    }

    @Test
    @DisplayName("TestRail getters resolve via overrides")
    void testTestRailGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.TESTRAIL_BASE_PATH, "https://testrail.example.com");
        values.put(PropertyReader.TESTRAIL_USERNAME, "user");
        values.put(PropertyReader.TESTRAIL_API_KEY, "api-key");
        values.put(PropertyReader.TESTRAIL_PROJECT, "P1");
        values.put(PropertyReader.TESTRAIL_LOGGING_ENABLED, "true");
        values.put(PropertyReader.TESTRAIL_DEFAULT_FORMAT, "markdown");
        override(values);

        assertEquals("https://testrail.example.com", propertyReader.getTestRailBasePath());
        assertEquals("user", propertyReader.getTestRailUsername());
        assertEquals("api-key", propertyReader.getTestRailApiKey());
        assertEquals("P1", propertyReader.getTestRailProject());
        assertTrue(propertyReader.isTestRailLoggingEnabled());
        assertEquals("markdown", propertyReader.getTestRailDefaultFormat());
    }

    @Test
    @DisplayName("Bitrise getters resolve via overrides; base path has a default")
    void testBitriseGetters() {
        Map<String, String> values = new HashMap<>();
        values.put(PropertyReader.BITRISE_TOKEN, "bitrise-token");
        values.put(PropertyReader.BITRISE_APP_SLUG, "app-slug");
        values.put(PropertyReader.BITRISE_BASE_PATH, "");
        override(values);

        assertEquals("bitrise-token", propertyReader.getBitriseToken());
        assertEquals("app-slug", propertyReader.getBitriseAppSlug());
        assertEquals("https://api.bitrise.io/v0.1", propertyReader.getBitriseBasePath());

        override(PropertyReader.BITRISE_BASE_PATH, "https://bitrise.example.com");
        assertEquals("https://bitrise.example.com", propertyReader.getBitriseBasePath());
    }
}
