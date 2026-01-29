package com.github.istin.dmtools.qa;

import com.google.gson.Gson;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TestCasesGeneratorParams, focusing on the parallel test case check configuration.
 */
public class TestCasesGeneratorParamsTest {

    private TestCasesGeneratorParams params;
    private Gson gson;

    @Before
    public void setUp() {
        params = new TestCasesGeneratorParams();
        gson = new Gson();
    }

    @Test
    public void testDefaultParallelCheckDisabled() {
        assertFalse("Parallel test case check should be disabled by default",
                    params.isEnableParallelTestCaseCheck());
    }

    @Test
    public void testDefaultThreadCount() {
        assertEquals("Default thread count should be 5",
                     5, params.getParallelTestCaseCheckThreads());
    }

    @Test
    public void testSetEnableParallelTestCaseCheck() {
        params.setEnableParallelTestCaseCheck(true);
        assertTrue("Parallel test case check should be enabled",
                   params.isEnableParallelTestCaseCheck());
    }

    @Test
    public void testSetParallelTestCaseCheckThreads() {
        params.setParallelTestCaseCheckThreads(10);
        assertEquals("Thread count should be 10",
                     10, params.getParallelTestCaseCheckThreads());
    }

    @Test
    public void testSerializationWithDefaultValues() {
        String json = gson.toJson(params);
        JSONObject jsonObject = new JSONObject(json);

        assertFalse("Serialized JSON should contain enableParallelTestCaseCheck=false",
                    jsonObject.getBoolean("enableParallelTestCaseCheck"));
        assertEquals("Serialized JSON should contain parallelTestCaseCheckThreads=5",
                     5, jsonObject.getInt("parallelTestCaseCheckThreads"));
    }

    @Test
    public void testSerializationWithCustomValues() {
        params.setEnableParallelTestCaseCheck(true);
        params.setParallelTestCaseCheckThreads(15);

        String json = gson.toJson(params);
        JSONObject jsonObject = new JSONObject(json);

        assertTrue("Serialized JSON should contain enableParallelTestCaseCheck=true",
                   jsonObject.getBoolean("enableParallelTestCaseCheck"));
        assertEquals("Serialized JSON should contain parallelTestCaseCheckThreads=15",
                     15, jsonObject.getInt("parallelTestCaseCheckThreads"));
    }

    @Test
    public void testDeserializationWithDefaultValues() {
        String json = "{}";
        TestCasesGeneratorParams deserializedParams = gson.fromJson(json, TestCasesGeneratorParams.class);

        assertFalse("Deserialized params should have enableParallelTestCaseCheck=false by default",
                    deserializedParams.isEnableParallelTestCaseCheck());
        assertEquals("Deserialized params should have parallelTestCaseCheckThreads=5 by default",
                     5, deserializedParams.getParallelTestCaseCheckThreads());
    }

    @Test
    public void testDeserializationWithCustomValues() {
        String json = "{\"enableParallelTestCaseCheck\":true,\"parallelTestCaseCheckThreads\":20}";
        TestCasesGeneratorParams deserializedParams = gson.fromJson(json, TestCasesGeneratorParams.class);

        assertTrue("Deserialized params should have enableParallelTestCaseCheck=true",
                   deserializedParams.isEnableParallelTestCaseCheck());
        assertEquals("Deserialized params should have parallelTestCaseCheckThreads=20",
                     20, deserializedParams.getParallelTestCaseCheckThreads());
    }

    @Test
    public void testAllArgsConstructor() {
        TestCasesGeneratorParams constructedParams = new TestCasesGeneratorParams(
            "jql",                              // existingTestCasesJql
            "High,Medium,Low",                  // testCasesPriorities
            "https://rules.com",                // relatedTestCasesRules
            "examples",                         // examples
            "Test",                             // testCaseIssueType
            true,                               // isConvertToJiraMarkdown
            true,                               // includeOtherTicketReferences
            true,                               // isFindRelated
            true,                               // isLinkRelated
            true,                               // isGenerateNew
            false,                              // isOverridePromptExamples
            "tests",                            // testCaseLinkRelationship
            null,                               // testCaseLinkRelationshipForNew
            null,                               // testCaseLinkRelationshipForExisting
            new String[]{"field1"},             // testCasesCustomFields
            "customRules",                      // customFieldsRules
            "preprocess",                       // preprocessJSAction
            "model1",                           // modelTestCasesCreation
            "model2",                           // modelTestCasesRelation
            "model3",                           // modelTestCaseRelation
            "model4",                           // modelTestCaseDeduplication
            true,                               // enableParallelTestCaseCheck
            10,                                 // parallelTestCaseCheckThreads
            true,                               // enableParallelPostVerification
            5,                                  // parallelPostVerificationThreads
            "jqlModifier.js"                    // jqlModifierJSAction
        );

        assertTrue("Constructor should set enableParallelTestCaseCheck correctly",
                   constructedParams.isEnableParallelTestCaseCheck());
        assertEquals("Constructor should set parallelTestCaseCheckThreads correctly",
                     10, constructedParams.getParallelTestCaseCheckThreads());
        assertTrue("Constructor should set enableParallelPostVerification correctly",
                   constructedParams.isEnableParallelPostVerification());
        assertEquals("Constructor should set parallelPostVerificationThreads correctly",
                     5, constructedParams.getParallelPostVerificationThreads());
    }

    @Test
    public void testThreadCountBoundaryValues() {
        // Test minimum reasonable value
        params.setParallelTestCaseCheckThreads(1);
        assertEquals("Thread count should accept minimum value of 1",
                     1, params.getParallelTestCaseCheckThreads());

        // Test maximum reasonable value
        params.setParallelTestCaseCheckThreads(50);
        assertEquals("Thread count should accept maximum value of 50",
                     50, params.getParallelTestCaseCheckThreads());

        // Test zero (edge case, though not recommended)
        params.setParallelTestCaseCheckThreads(0);
        assertEquals("Thread count should accept value of 0",
                     0, params.getParallelTestCaseCheckThreads());
    }

    @Test
    public void testConstantValues() {
        assertEquals("ENABLE_PARALLEL_TEST_CASE_CHECK constant should match",
                     "enableParallelTestCaseCheck",
                     TestCasesGeneratorParams.ENABLE_PARALLEL_TEST_CASE_CHECK);
        assertEquals("PARALLEL_TEST_CASE_CHECK_THREADS constant should match",
                     "parallelTestCaseCheckThreads",
                     TestCasesGeneratorParams.PARALLEL_TEST_CASE_CHECK_THREADS);
        assertEquals("ENABLE_PARALLEL_POST_VERIFICATION constant should match",
                     "enableParallelPostVerification",
                     TestCasesGeneratorParams.ENABLE_PARALLEL_POST_VERIFICATION);
        assertEquals("PARALLEL_POST_VERIFICATION_THREADS constant should match",
                     "parallelPostVerificationThreads",
                     TestCasesGeneratorParams.PARALLEL_POST_VERIFICATION_THREADS);
    }

    @Test
    public void testBackwardCompatibility() {
        // Test that existing JSON without new fields can be deserialized
        String oldJson = "{\"existingTestCasesJql\":\"project=TEST\",\"testCaseIssueType\":\"Test Case\"}";
        TestCasesGeneratorParams deserializedParams = gson.fromJson(oldJson, TestCasesGeneratorParams.class);

        assertNotNull("Should deserialize old JSON format", deserializedParams);
        assertFalse("Old JSON should default to parallel check disabled",
                    deserializedParams.isEnableParallelTestCaseCheck());
        assertEquals("Old JSON should default to 5 threads",
                     5, deserializedParams.getParallelTestCaseCheckThreads());
        assertFalse("Old JSON should default to parallel post-verification disabled",
                    deserializedParams.isEnableParallelPostVerification());
        assertEquals("Old JSON should default to 3 post-verification threads",
                     3, deserializedParams.getParallelPostVerificationThreads());
        assertEquals("Should preserve existing fields",
                     "project=TEST", deserializedParams.getExistingTestCasesJql());
    }

    @Test
    public void testParallelCheckEnabledWithCustomThreads() {
        params.setEnableParallelTestCaseCheck(true);
        params.setParallelTestCaseCheckThreads(8);

        assertTrue("Parallel check should be enabled",
                   params.isEnableParallelTestCaseCheck());
        assertEquals("Custom thread count should be set",
                     8, params.getParallelTestCaseCheckThreads());
    }

    @Test
    public void testRoundTripSerializationDeserialization() {
        // Set custom values
        params.setEnableParallelTestCaseCheck(true);
        params.setParallelTestCaseCheckThreads(12);
        params.setEnableParallelPostVerification(true);
        params.setParallelPostVerificationThreads(4);
        params.setExistingTestCasesJql("type=Test");
        params.setTestCaseIssueType("Test Case");

        // Serialize
        String json = gson.toJson(params);

        // Deserialize
        TestCasesGeneratorParams roundTrippedParams = gson.fromJson(json, TestCasesGeneratorParams.class);

        // Verify parallel check settings survived round trip
        assertTrue("Round trip should preserve enableParallelTestCaseCheck",
                   roundTrippedParams.isEnableParallelTestCaseCheck());
        assertEquals("Round trip should preserve parallelTestCaseCheckThreads",
                     12, roundTrippedParams.getParallelTestCaseCheckThreads());
        assertTrue("Round trip should preserve enableParallelPostVerification",
                   roundTrippedParams.isEnableParallelPostVerification());
        assertEquals("Round trip should preserve parallelPostVerificationThreads",
                     4, roundTrippedParams.getParallelPostVerificationThreads());
        assertEquals("Round trip should preserve other fields",
                     "type=Test", roundTrippedParams.getExistingTestCasesJql());
    }

    @Test
    public void testDefaultParallelPostVerificationDisabled() {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        assertFalse("Default enableParallelPostVerification should be false",
                    params.isEnableParallelPostVerification());
        assertEquals("Default parallelPostVerificationThreads should be 3",
                     3, params.getParallelPostVerificationThreads());
    }

    @Test
    public void testSetEnableParallelPostVerification() {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setEnableParallelPostVerification(true);
        assertTrue("Should set enableParallelPostVerification to true",
                   params.isEnableParallelPostVerification());
    }

    @Test
    public void testSetParallelPostVerificationThreads() {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setParallelPostVerificationThreads(6);
        assertEquals("Should set parallelPostVerificationThreads to 6",
                     6, params.getParallelPostVerificationThreads());
    }

    @Test
    public void testSerializationWithPostVerificationEnabled() {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setEnableParallelPostVerification(true);
        params.setParallelPostVerificationThreads(8);

        String json = gson.toJson(params);
        JSONObject jsonObject = new JSONObject(json);

        assertTrue("Serialized JSON should contain enableParallelPostVerification=true",
                   jsonObject.getBoolean("enableParallelPostVerification"));
        assertEquals("Serialized JSON should contain parallelPostVerificationThreads=8",
                     8, jsonObject.getInt("parallelPostVerificationThreads"));
    }

    @Test
    public void testDeserializationWithPostVerificationValues() {
        String json = "{\"enableParallelPostVerification\":true,\"parallelPostVerificationThreads\":7}";
        TestCasesGeneratorParams deserializedParams = gson.fromJson(json, TestCasesGeneratorParams.class);

        assertTrue("Deserialized params should have enableParallelPostVerification=true",
                   deserializedParams.isEnableParallelPostVerification());
        assertEquals("Deserialized params should have parallelPostVerificationThreads=7",
                     7, deserializedParams.getParallelPostVerificationThreads());
    }
}

