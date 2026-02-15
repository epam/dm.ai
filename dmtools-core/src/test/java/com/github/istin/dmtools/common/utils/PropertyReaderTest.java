package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PropertyReaderTest {

    private PropertyReader propertyReader;

    @BeforeEach
    void setUp() throws Exception {
        propertyReader = new PropertyReader();
        // Reset static properties to avoid test interference
        PropertyReader.prop = null;
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
        PropertyReader.prop = null;
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
            assertEquals(4000, result, "Default token limit should be 4000");
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
        String[] result = propertyReader.getJiraExtraFields();
        assertNull(result);
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
    }

    @Test
    void testXrayParallelConfigConstants_CorrectValues() {
        // Verify constant values match property names
        assertEquals("XRAY_PARALLEL_FETCH_ENABLED", PropertyReader.XRAY_PARALLEL_FETCH_ENABLED);
        assertEquals("XRAY_PARALLEL_BATCH_SIZE", PropertyReader.XRAY_PARALLEL_BATCH_SIZE);
        assertEquals("XRAY_PARALLEL_THREADS", PropertyReader.XRAY_PARALLEL_THREADS);
        assertEquals("XRAY_PARALLEL_DELAY_MS", PropertyReader.XRAY_PARALLEL_DELAY_MS);
    }
}
