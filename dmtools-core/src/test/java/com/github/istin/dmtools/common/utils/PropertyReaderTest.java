// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PropertyReaderTest {

    private PropertyReader propertyReader;

    @BeforeEach
    void setUp() throws Exception {
        propertyReader = new PropertyReader();
        // Reset static properties to avoid test interference
        PropertyReader.resetForTesting();
        // Use reflection to reset envFileProps and projectRoot for clean state
        // IMPORTANT: If reflection fails, the test should fail (not silently ignore)
        // to prevent cross-test contamination from static state
        java.lang.reflect.Field envFilePropsField = PropertyReader.class.getDeclaredField("envFileProps");
        envFilePropsField.setAccessible(true);
        envFilePropsField.set(null, null);

        java.lang.reflect.Field projectRootField = PropertyReader.class.getDeclaredField("projectRoot");
        projectRootField.setAccessible(true);
        projectRootField.set(null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up static state
        // IMPORTANT: If cleanup fails, the test should fail to prevent cross-test interference
        PropertyReader.resetForTesting();
        java.lang.reflect.Field envFilePropsField = PropertyReader.class.getDeclaredField("envFileProps");
        envFilePropsField.setAccessible(true);
        envFilePropsField.set(null, null);

        java.lang.reflect.Field projectRootField = PropertyReader.class.getDeclaredField("projectRoot");
        projectRootField.setAccessible(true);
        projectRootField.set(null, null);
    }

    @Test
    void testGetValueWithDefault() {
        String result = propertyReader.getValue("NON_EXISTENT_KEY", "defaultValue");
        assertEquals("defaultValue", result);
    }

    @Test
    void testGetAiRetryAmount_DefaultValue() {
        int result = propertyReader.getAiRetryAmount();
        assertEquals(3, result); // DEFAULT_AI_RETRY_AMOUNT
    }

    @Test
    void testGetAiRetryDelayStep_DefaultValue() {
        long result = propertyReader.getAiRetryDelayStep();
        assertEquals(20000L, result); // DEFAULT_AI_RETRY_DELAY_STEP
    }

    @Test
    void testGetPromptChunkTokenLimit_DefaultValue() {
        String envValue = System.getenv("PROMPT_CHUNK_TOKEN_LIMIT");
        String fileValue = propertyReader.getValue("PROMPT_CHUNK_TOKEN_LIMIT");
        int result = propertyReader.getPromptChunkTokenLimit();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value
            assertEquals(50000, result, "Default token limit should be 50000");
        } else {
            // If override exists, just verify it's a valid positive number
            assertTrue(result > 0, "Token limit should be positive");
        }
    }

    @Test
    void testGetPromptChunkMaxSingleFileSize_DefaultValue() {
        long result = propertyReader.getPromptChunkMaxSingleFileSize();
        assertEquals(4 * 1024 * 1024, result); // 4MB in bytes
    }

    @Test
    void testGetPromptChunkMaxTotalFilesSize_DefaultValue() {
        long result = propertyReader.getPromptChunkMaxTotalFilesSize();
        assertEquals(4 * 1024 * 1024, result); // 4MB in bytes
    }

    @Test
    void testGetPromptChunkMaxFiles_DefaultValue() {
        int result = propertyReader.getPromptChunkMaxFiles();
        assertEquals(10, result); // DEFAULT_PROMPT_CHUNK_MAX_FILES
    }

    @Test
    void testGetJsClientName_DefaultValue() {
        String result = propertyReader.getJsClientName();
        assertEquals("JSAIClientFromProperties", result); // DEFAULT_JSAI_CLIENT_NAME
    }

    @Test
    void testGetGeminiDefaultModel() {
        // Check both GEMINI_MODEL (priority 1) and GEMINI_DEFAULT_MODEL (priority 2)
        String envValueModel = System.getenv("GEMINI_MODEL");
        String fileValueModel = propertyReader.getValue("GEMINI_MODEL");
        String envValueDefaultModel = System.getenv("GEMINI_DEFAULT_MODEL");
        String fileValueDefaultModel = propertyReader.getValue("GEMINI_DEFAULT_MODEL");
        String result = propertyReader.getGeminiDefaultModel();

        // Check if any configuration is present
        boolean hasModel = (envValueModel != null && !envValueModel.trim().isEmpty() && !envValueModel.startsWith("$")) ||
                          (fileValueModel != null && !fileValueModel.trim().isEmpty() && !fileValueModel.startsWith("$"));
        boolean hasDefaultModel = (envValueDefaultModel != null && !envValueDefaultModel.trim().isEmpty() && !envValueDefaultModel.startsWith("$")) ||
                                 (fileValueDefaultModel != null && !fileValueDefaultModel.trim().isEmpty() && !fileValueDefaultModel.startsWith("$"));

        if (hasModel || hasDefaultModel) {
            // If configuration exists, result should not be null
            assertNotNull(result, "Should return configured model when GEMINI_MODEL or GEMINI_DEFAULT_MODEL is set");
            assertFalse(result.trim().isEmpty(), "Configured model should not be empty");
        } else {
            // If no configuration, result can be null (no hardcoded default)
            // This is the intended behavior after removing the hardcoded default
            // The method returns null to indicate that model must be explicitly configured
        }
    }

    @Test
    void testGetGeminiBasePath() {
        String envValue = System.getenv("GEMINI_BASE_PATH");
        String fileValue = propertyReader.getValue("GEMINI_BASE_PATH");
        String result = propertyReader.getGeminiBasePath();

        assertNotNull(result);
        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value
            assertEquals("https://generativelanguage.googleapis.com/v1beta/models", result,
                    "Default Gemini base path should be Google's generative language API");
        } else {
            // If override exists, just verify it's a valid URL-like string
            assertFalse(result.trim().isEmpty());
        }
    }

    @Test
    void testGetSleepTimeRequest_DefaultValue() {
        String envValue = System.getenv("SLEEP_TIME_REQUEST");
        String fileValue = propertyReader.getValue("SLEEP_TIME_REQUEST");
        Long result = propertyReader.getSleepTimeRequest();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value
            assertEquals(300L, result, "Default SLEEP_TIME_REQUEST should be 300L");
        } else {
            // If override exists, just verify it's valid
            assertNotNull(result);
            assertTrue(result > 0, "SLEEP_TIME_REQUEST should be positive");
        }
    }

    @Test
    void testGetDefaultTicketWeightIfNoSPs_DefaultValue() {
        String envValue = System.getenv("DEFAULT_TICKET_WEIGHT_IF_NO_SP");
        String fileValue = propertyReader.getValue("DEFAULT_TICKET_WEIGHT_IF_NO_SP");
        Integer result = propertyReader.getDefaultTicketWeightIfNoSPs();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value
            assertEquals(-1, result, "Default ticket weight should be -1");
        } else {
            // If override exists, just verify it's valid
            assertNotNull(result);
        }
    }

    @Test
    void testGetLinesOfCodeDivider_DefaultValue() {
        String envValue = System.getenv("LINES_OF_CODE_DIVIDER");
        String fileValue = propertyReader.getValue("LINES_OF_CODE_DIVIDER");
        Double result = propertyReader.getLinesOfCodeDivider();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value
            assertEquals(1.0, result, "Default LINES_OF_CODE_DIVIDER should be 1.0");
        } else {
            // If override exists, just verify it's valid
            assertNotNull(result);
            assertTrue(result > 0, "LINES_OF_CODE_DIVIDER should be positive");
        }
    }

    @Test
    void testGetTimeSpentOnDivider_DefaultValue() {
        String envValue = System.getenv("TIME_SPENT_ON_DIVIDER");
        String fileValue = propertyReader.getValue("TIME_SPENT_ON_DIVIDER");
        Double result = propertyReader.getTimeSpentOnDivider();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value
            assertEquals(1.0, result, "Default TIME_SPENT_ON_DIVIDER should be 1.0");
        } else {
            // If override exists, just verify it's valid
            assertNotNull(result);
            assertTrue(result > 0, "TIME_SPENT_ON_DIVIDER should be positive");
        }
    }

    @Test
    void testGetTicketFieldsChangedDivider_DefaultValue() {
        String fieldName = "testField";
        String envValueSpecific = System.getenv("TICKET_FIELDS_CHANGED_DIVIDER_" + fieldName.toUpperCase());
        String fileValueSpecific = propertyReader.getValue("TICKET_FIELDS_CHANGED_DIVIDER_" + fieldName.toUpperCase());
        String envValueDefault = System.getenv("TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT");
        String fileValueDefault = propertyReader.getValue("TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT");

        Double result = propertyReader.getTicketFieldsChangedDivider(fieldName);

        // Check if there are any overrides (field-specific or default)
        boolean hasOverride = (envValueSpecific != null && !envValueSpecific.trim().isEmpty()) ||
                              (fileValueSpecific != null && !fileValueSpecific.trim().isEmpty()) ||
                              (envValueDefault != null && !envValueDefault.trim().isEmpty()) ||
                              (fileValueDefault != null && !fileValueDefault.trim().isEmpty());

        if (!hasOverride) {
            // If no env/file override, expect exact default value
            assertEquals(1.0, result, "Default TICKET_FIELDS_CHANGED_DIVIDER should be 1.0");
        } else {
            // If override exists, just verify it's valid
            assertNotNull(result);
            assertTrue(result > 0, "TICKET_FIELDS_CHANGED_DIVIDER should be positive");
        }
    }

    @Test
    void testIsReadPullRequestDiff_DefaultValue() {
        boolean result = propertyReader.isReadPullRequestDiff();
        assertTrue(result);
    }

    @Test
    void testIsJiraWaitBeforePerform_DefaultValue() {
        String envValue = System.getenv("JIRA_WAIT_BEFORE_PERFORM");
        String fileValue = propertyReader.getValue("JIRA_WAIT_BEFORE_PERFORM");
        boolean result = propertyReader.isJiraWaitBeforePerform();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value (false)
            assertFalse(result, "Default JIRA_WAIT_BEFORE_PERFORM should be false");
        }
        // If override exists, just verify it's a valid boolean (no assertion needed)
    }

    @Test
    void testIsJiraLoggingEnabled_DefaultValue() {
        String envValue = System.getenv("JIRA_LOGGING_ENABLED");
        String fileValue = propertyReader.getValue("JIRA_LOGGING_ENABLED");
        boolean result = propertyReader.isJiraLoggingEnabled();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value (false)
            assertFalse(result, "Default JIRA_LOGGING_ENABLED should be false");
        }
        // If override exists, just verify it's a valid boolean (no assertion needed)
    }

    @Test
    void testIsJiraClearCache_DefaultValue() {
        String envValue = System.getenv("JIRA_CLEAR_CACHE");
        String fileValue = propertyReader.getValue("JIRA_CLEAR_CACHE");
        boolean result = propertyReader.isJiraClearCache();

        if ((envValue == null || envValue.trim().isEmpty()) &&
            (fileValue == null || fileValue.trim().isEmpty())) {
            // If no env/file override, expect exact default value (false)
            assertFalse(result, "Default JIRA_CLEAR_CACHE should be false");
        }
        // If override exists, just verify it's a valid boolean (no assertion needed, method returns boolean)
    }

    @Test
    void testGetJiraMaxSearchResults_DefaultValue() {
        int result = propertyReader.getJiraMaxSearchResults();
        assertEquals(-1, result);
    }

    @Test
    void testGetConfluenceAuthType_DefaultValue() {
        String result = propertyReader.getConfluenceAuthType();
        assertEquals("Basic", result);
    }

    @Test
    void testGetJsSecretsKeys_NullValue() {
        String[] result = propertyReader.getJsSecretsKeys();
        assertNull(result);
    }

    @Test
    void testGetJiraExtraFields_NullValue() {
        String envValue = System.getenv("JIRA_EXTRA_FIELDS");
        String fileValue = propertyReader.getValue("JIRA_EXTRA_FIELDS");
        String[] result = propertyReader.getJiraExtraFields();

        boolean hasConfiguredValue = (envValue != null && !envValue.trim().isEmpty() && !envValue.startsWith("$")) ||
            (fileValue != null && !fileValue.trim().isEmpty() && !fileValue.startsWith("$"));

        if (!hasConfiguredValue) {
            assertNull(result);
        } else {
            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Test
    void testSetConfigFile() {
        PropertyReader.setConfigFile("/test-config.properties");
        // This test just ensures the method doesn't throw an exception
        // Actual behavior depends on config file existence
    }

    @Test
    void testGetAllProperties() {
        Map<String, String> properties = propertyReader.getAllProperties();
        assertNotNull(properties);
        // Properties map may be empty if no config file exists
    }

    // X-ray Parallel Fetch Configuration Tests

    @Test
    void testIsXrayParallelFetchEnabled_DefaultValue() {
        boolean result = propertyReader.isXrayParallelFetchEnabled();
        assertFalse(result, "X-ray parallel fetch should be disabled by default");
    }

    @Test
    void testGetXrayParallelBatchSize_DefaultValue() {
        int result = propertyReader.getXrayParallelBatchSize();
        assertEquals(100, result, "Default batch size should be 100");
    }

    @Test
    void testGetXrayParallelThreads_DefaultValue() {
        int result = propertyReader.getXrayParallelThreads();
        assertEquals(2, result, "Default thread count should be 2");
    }

    @Test
    void testGetXrayParallelDelayMs_DefaultValue() {
        long result = propertyReader.getXrayParallelDelayMs();
        assertEquals(500L, result, "Default delay should be 500ms");
    }

    @Test
    void testXrayParallelConfigConstants_NotNull() {
        // Verify constants are defined
        assertNotNull(PropertyReader.XRAY_PARALLEL_FETCH_ENABLED, "XRAY_PARALLEL_FETCH_ENABLED constant should exist");
        assertNotNull(PropertyReader.XRAY_PARALLEL_BATCH_SIZE, "XRAY_PARALLEL_BATCH_SIZE constant should exist");
        assertNotNull(PropertyReader.XRAY_PARALLEL_THREADS, "XRAY_PARALLEL_THREADS constant should exist");
        assertNotNull(PropertyReader.XRAY_PARALLEL_DELAY_MS, "XRAY_PARALLEL_DELAY_MS constant should exist");
        assertNotNull(PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT, "XRAY_ENRICHMENT_ENABLED_BY_DEFAULT constant should exist");
    }

    @Test
    void testXrayParallelConfigConstants_CorrectValues() {
        // Verify constant values match property names
        assertEquals("XRAY_PARALLEL_FETCH_ENABLED", PropertyReader.XRAY_PARALLEL_FETCH_ENABLED);
        assertEquals("XRAY_PARALLEL_BATCH_SIZE", PropertyReader.XRAY_PARALLEL_BATCH_SIZE);
        assertEquals("XRAY_PARALLEL_THREADS", PropertyReader.XRAY_PARALLEL_THREADS);
        assertEquals("XRAY_PARALLEL_DELAY_MS", PropertyReader.XRAY_PARALLEL_DELAY_MS);
        assertEquals("XRAY_ENRICHMENT_ENABLED_BY_DEFAULT", PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT);
    }

    @Test
    void testIsXrayEnrichmentEnabledByDefault_DefaultValue() {
        boolean result = propertyReader.isXrayEnrichmentEnabledByDefault();
        assertTrue(result, "X-ray enrichment should be enabled by default");
    }

    @Test
    void testTestRailDefaultFormatConstant_NotNullAndCorrectValue() {
        assertNotNull(PropertyReader.TESTRAIL_DEFAULT_FORMAT, "TESTRAIL_DEFAULT_FORMAT constant should exist");
        assertEquals("TESTRAIL_DEFAULT_FORMAT", PropertyReader.TESTRAIL_DEFAULT_FORMAT);
    }

    @Test
    void testGetTestRailDefaultFormat_DefaultsToHtmlWhenUnset() {
        assertEquals("html", propertyReader.getTestRailDefaultFormat(),
                "TestRail default format should be 'html' when the env var is unset, to preserve existing behavior");
    }

    @Test
    void testGetTestRailDefaultFormat_ReturnsConfiguredValue() {
        PropertyReader.setOverrides(Map.of(PropertyReader.TESTRAIL_DEFAULT_FORMAT, "markdown"));
        try {
            assertEquals("markdown", propertyReader.getTestRailDefaultFormat());
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    // -------------------------------------------------------------------------
    // CLI output configuration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CLI_OUTPUT constant has the correct string value")
    void testCliOutputConstantValue() {
        assertEquals("CLI_OUTPUT", PropertyReader.CLI_OUTPUT);
    }

    @Test
    @DisplayName("getCliOutput() returns null when no CLI_OUTPUT is configured")
    void testGetCliOutputReturnsNullWhenNotSet() {
        // In the test environment, CLI_OUTPUT is not configured
        String envValue = System.getenv(PropertyReader.CLI_OUTPUT);
        if (envValue == null || envValue.trim().isEmpty()) {
            assertNull(propertyReader.getCliOutput(),
                "getCliOutput() should return null when CLI_OUTPUT is not set");
        }
    }

    @Test
    @DisplayName("getCliOutput() respects thread-local override (highest priority)")
    void testGetCliOutputThreadLocalOverride() {
        Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put(PropertyReader.CLI_OUTPUT, "toon");
        PropertyReader.setOverrides(overrides);
        try {
            assertEquals("toon", new PropertyReader().getCliOutput());
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    @DisplayName("getCliOutput() with override returns the overridden format")
    void testGetCliOutputOverrideFormats() {
        for (String format : new String[]{"json", "toon", "mini"}) {
            Map<String, String> overrides = new java.util.HashMap<>();
            overrides.put(PropertyReader.CLI_OUTPUT, format);
            PropertyReader.setOverrides(overrides);
            try {
                assertEquals(format, new PropertyReader().getCliOutput(),
                    "Should return the overridden format: " + format);
            } finally {
                PropertyReader.clearOverrides();
            }
        }
    }

    @Test
    @DisplayName("getJiraIssueIgnorePrefixes() returns empty set by default")
    void testGetJiraIssueIgnorePrefixes_DefaultValue() {
        Set<String> result = propertyReader.getJiraIssueIgnorePrefixes();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getJiraIssueAllowedPrefixes() returns empty set by default")
    void testGetJiraIssueAllowedPrefixes_DefaultValue() {
        Set<String> result = propertyReader.getJiraIssueAllowedPrefixes();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getJiraIssueIgnorePrefixes() parses comma-separated values and upper-cases them")
    void testGetJiraIssueIgnorePrefixes_Parsing() {
        Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put(PropertyReader.JIRA_ISSUE_IGNORE_PREFIXES, "PSR, rfc ,CVE");
        PropertyReader.setOverrides(overrides);
        try {
            Set<String> result = new PropertyReader().getJiraIssueIgnorePrefixes();
            assertEquals(3, result.size());
            assertTrue(result.contains("PSR"));
            assertTrue(result.contains("RFC"));
            assertTrue(result.contains("CVE"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    @DisplayName("getJiraIssueAllowedPrefixes() parses comma-separated values and upper-cases them")
    void testGetJiraIssueAllowedPrefixes_Parsing() {
        Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put(PropertyReader.JIRA_ISSUE_ALLOWED_PREFIXES, "PROJ, TEAM, PLATFORM");
        PropertyReader.setOverrides(overrides);
        try {
            Set<String> result = new PropertyReader().getJiraIssueAllowedPrefixes();
            assertEquals(3, result.size());
            assertTrue(result.contains("PROJ"));
            assertTrue(result.contains("TEAM"));
            assertTrue(result.contains("PLATFORM"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    // -------------------------------------------------------------------------
    // Environment variable interpolation in overrides
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolveEnvVariables interpolates $ENV_VAR_DEV_QUEUE and ${ENV_VAR_REVIEW_QUEUE} from extraEnv / dmtools.env")
    void testResolveEnvVariables_DevAndReviewQueueFromExtraEnv() {
        String devQueueJson = "[{\"provider_type\":\"openai_codex\",\"model\":\"gpt-5.4\"}]";
        String reviewQueueJson = "[{\"provider_type\":\"anthropic_claude_code\",\"model\":\"claude-opus-4-7\"}]";
        Map<String, String> extraEnv = Map.of(
                "ENV_VAR_DEV_QUEUE", devQueueJson,
                "ENV_VAR_REVIEW_QUEUE", reviewQueueJson
        );
        Map<String, String> rawOverrides = new java.util.LinkedHashMap<>();
        rawOverrides.put("DEFAULT_TRACKER", "github");
        rawOverrides.put("AI_AGENT_PROVIDER", "fa");
        rawOverrides.put("FA_PROVIDERS_QUEUE", "$ENV_VAR_DEV_QUEUE");
        rawOverrides.put("FA_REVIEW_QUEUE", "${ENV_VAR_REVIEW_QUEUE}");

        Map<String, String> resolved = PropertyReader.resolveEnvVariables(rawOverrides, extraEnv);

        assertEquals("github", resolved.get("DEFAULT_TRACKER"));
        assertEquals("fa", resolved.get("AI_AGENT_PROVIDER"));
        assertEquals(devQueueJson, resolved.get("FA_PROVIDERS_QUEUE"));
        assertEquals(reviewQueueJson, resolved.get("FA_REVIEW_QUEUE"));
    }

    @Test
    @DisplayName("setOverrides interpolates $VAR and ${VAR} from dmtools.env properties and System.getenv")
    void testSetOverrides_InterpolatesFromEnvFilePropsAndSystemEnv() throws Exception {
        String devQueueJson = "[{\"provider_type\":\"google_gemini_cli\",\"api_key_env\":\"GEMINI_API_KEY\"}]";
        java.util.Properties mockEnvProps = new java.util.Properties();
        mockEnvProps.setProperty("ENV_VAR_DEV_QUEUE", devQueueJson);

        java.lang.reflect.Field envFilePropsField = PropertyReader.class.getDeclaredField("envFileProps");
        envFilePropsField.setAccessible(true);
        envFilePropsField.set(null, mockEnvProps);

        String systemPath = System.getenv("PATH");
        Map<String, String> raw = new java.util.LinkedHashMap<>();
        raw.put("FA_PROVIDERS_QUEUE", "$ENV_VAR_DEV_QUEUE");
        if (systemPath != null) {
            raw.put("COPIED_PATH", "${PATH}");
        }

        PropertyReader.setOverrides(raw);
        try {
            assertEquals(devQueueJson, new PropertyReader().getValue("FA_PROVIDERS_QUEUE"));
            assertEquals(devQueueJson, PropertyReader.getOverrides().get("FA_PROVIDERS_QUEUE"));
            if (systemPath != null) {
                assertEquals(systemPath, new PropertyReader().getValue("COPIED_PATH"));
            }
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    @DisplayName("resolveEnvVariables interpolates embedded $VAR and ${VAR} in a larger string")
    void testResolveEnvVariables_EmbeddedVarInLargerString() {
        Map<String, String> extraEnv = Map.of(
                "HOST", "api.example.com",
                "PORT", "8443"
        );
        Map<String, String> raw = Map.of(
                "ENDPOINT", "https://${HOST}:$PORT/v1/chat"
        );

        Map<String, String> resolved = PropertyReader.resolveEnvVariables(raw, extraEnv);
        assertEquals("https://api.example.com:8443/v1/chat", resolved.get("ENDPOINT"));
    }

    @Test
    @DisplayName("resolveEnvVariables preserves unset $UNKNOWN_VAR and ${UNKNOWN_VAR}")
    void testResolveEnvVariables_UnsetVarPreserved() {
        Map<String, String> raw = Map.of(
                "QUEUE_A", "$DEFINITELY_UNSET_VAR_XYZ_987654",
                "QUEUE_B", "prefix-${DEFINITELY_UNSET_VAR_XYZ_987654}-suffix"
        );

        Map<String, String> resolved = PropertyReader.resolveEnvVariables(raw);
        assertEquals("$DEFINITELY_UNSET_VAR_XYZ_987654", resolved.get("QUEUE_A"));
        assertEquals("prefix-${DEFINITELY_UNSET_VAR_XYZ_987654}-suffix", resolved.get("QUEUE_B"));
    }

    @Test
    @DisplayName("resolveEnvVariables leaves literal JSON strings without $ untouched")
    void testResolveEnvVariables_LiteralJsonUntouched() {
        String jsonQueue = "[{\"provider_type\":\"openai_codex\",\"model\":\"gpt-5.4\",\"api_key_env\":\"CODEX_API_KEY\"}]";
        Map<String, String> raw = Map.of(
                "FA_PROVIDERS_QUEUE", jsonQueue,
                "DEFAULT_TRACKER", "github"
        );

        Map<String, String> resolved = PropertyReader.resolveEnvVariables(raw);
        assertEquals(jsonQueue, resolved.get("FA_PROVIDERS_QUEUE"));
        assertEquals("github", resolved.get("DEFAULT_TRACKER"));
    }

    @Test
    @DisplayName("resolveEnvVariables handles self-reference FOO=$FOO without infinite loop and resolves sibling overrides")
    void testResolveEnvVariables_SelfReferenceAndSiblingOverrides() {
        Map<String, String> raw = new java.util.LinkedHashMap<>();
        raw.put("FOO", "$FOO");
        raw.put("BASE_MODEL", "gpt-5.4");
        raw.put("DERIVED_MODEL", "${BASE_MODEL}-mini");

        Map<String, String> resolved = PropertyReader.resolveEnvVariables(raw);
        assertEquals("$FOO", resolved.get("FOO"));
        assertEquals("gpt-5.4-mini", resolved.get("DERIVED_MODEL"));
    }
}
