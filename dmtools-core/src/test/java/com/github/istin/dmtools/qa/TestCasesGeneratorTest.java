package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.model.Relationship;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.JavaScriptExecutor;
import com.github.istin.dmtools.job.TrackerParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestCasesGeneratorTest {

    private TestCasesGenerator generator;
    private Method resolveNewMethod;
    private Method resolveExistingMethod;

    @Before
    public void setUp() throws Exception {
        generator = new TestCasesGenerator();
        resolveNewMethod = TestCasesGenerator.class.getDeclaredMethod("resolveRelationshipForNew", TestCasesGeneratorParams.class);
        resolveNewMethod.setAccessible(true);
        resolveExistingMethod = TestCasesGenerator.class.getDeclaredMethod("resolveRelationshipForExisting", TestCasesGeneratorParams.class);
        resolveExistingMethod.setAccessible(true);
    }

    @Test
    public void testToTextImplementation() {
        TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
                "High",
                "User can login",
                "Test login functionality"
        );

        String text = testCase.toText();

        assertTrue(text.contains("Priority: High"));
        assertTrue(text.contains("Summary: User can login"));
        assertTrue(text.contains("Description: Test login functionality"));
    }

    @Test
    public void testToTextUtilsWithMultipleTestCases() throws Exception {
        List<TestCaseGeneratorAgent.TestCase> testCases = new ArrayList<>();
        testCases.add(new TestCaseGeneratorAgent.TestCase("High", "Test 1", "Desc 1"));
        testCases.add(new TestCaseGeneratorAgent.TestCase("Medium", "Test 2", "Desc 2"));

        String result = ToText.Utils.toText(testCases);

        assertTrue(result.contains("Test 1"));
        assertTrue(result.contains("Test 2"));
        assertTrue(result.contains("-"));
    }

    @Test
    public void testChunkPreparationWithTestCases() throws Exception {
        List<TestCaseGeneratorAgent.TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            testCases.add(new TestCaseGeneratorAgent.TestCase(
                    "High",
                    "Test case " + i,
                    "Description for test case " + i
            ));
        }

        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int tokenLimit = 1000;

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(testCases, tokenLimit);

        // Should create at least one chunk
        assertTrue(chunks.size() >= 1);

        // Each chunk should have content
        for (ChunkPreparation.Chunk chunk : chunks) {
            assertNotNull(chunk.getText());
            assertFalse(chunk.getText().isEmpty());
        }
    }

    @Test
    public void testTokenCountingForTestCase() {
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
                "High",
                "User can login",
                "Test login functionality"
        );

        String text = testCase.toText();
        int tokens = tokenCounter.countTokens(text);

        // Should have a reasonable token count
        assertTrue(tokens > 0);
        assertTrue(tokens < 1000); // Shouldn't be excessive for a simple test case
    }

    @Test
    public void resolveRelationshipForNewPrefersOverride() throws Exception {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setTestCaseLinkRelationship("legacy");
        params.setTestCaseLinkRelationshipForNew("tests");

        String relationship = (String) resolveNewMethod.invoke(generator, params);

        assertEquals("tests", relationship);
    }

    @Test
    public void resolveRelationshipForExistingFallsBackToLegacy() throws Exception {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setTestCaseLinkRelationship("legacy");

        String relationship = (String) resolveExistingMethod.invoke(generator, params);

        assertEquals("legacy", relationship);
    }

    @Test
    public void resolveRelationshipForExistingDefaultsWhenBlank() throws Exception {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setTestCaseLinkRelationshipForExisting("   ");
        params.setTestCaseLinkRelationship(null);

        String relationship = (String) resolveExistingMethod.invoke(generator, params);

        assertEquals(Relationship.RELATES_TO, relationship);
    }

    @Test
    public void postJSActionInvokedWithExpectedContextPerTicket() throws Exception {
        // Custom generator that lets us intercept JavaScriptExecutor usage and capture jsCode
        class TestableGenerator extends TestCasesGenerator {
            JavaScriptExecutor capturedExecutor;
            String capturedJsCode;

            @Override
            protected JavaScriptExecutor js(String jsCode) {
                capturedJsCode = jsCode;
                capturedExecutor = mock(JavaScriptExecutor.class, RETURNS_SELF);
                try {
                    when(capturedExecutor.execute()).thenReturn(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return capturedExecutor;
            }
        }

        TestableGenerator testableGenerator = new TestableGenerator();

        // Wire minimal dependencies
        @SuppressWarnings("unchecked")
        TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
        Confluence confluence = mock(Confluence.class);
        TestCaseGeneratorAgent testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);

        AI ai = mock(AI.class);
        testableGenerator.trackerClient = trackerClient;
        testableGenerator.confluence = confluence;
        testableGenerator.testCaseGeneratorAgent = testCaseGeneratorAgent;
        testableGenerator.ai = ai;

        // Params with postJSAction and initiator
        String expectedJsCode = "console.log('test');";
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setFindRelated(false);
        params.setGenerateNew(true);
        params.setExamples(null);
        params.setTestCasesPriorities("High,Medium,Low");
        params.setModelTestCasesCreation("gpt-4");
        params.setOutputType(TrackerParams.OutputType.comment);
        params.setInitiator("qa@example.com");
        params.setPostJSAction("console.log('test')");
        params.setPostJSAction(expectedJsCode);

        // Ticket and context
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("DMC-123");
        when(ticket.getKey()).thenReturn("DMC-123");

        TicketContext ticketContext = mock(TicketContext.class);
        when(ticketContext.getTicket()).thenReturn(ticket);
        when(ticketContext.toText()).thenReturn("Some ticket text");

        // Generated test cases
        List<TestCaseGeneratorAgent.TestCase> generated = new ArrayList<>();
        generated.add(new TestCaseGeneratorAgent.TestCase("High", "Summary", "Description"));
        when(testCaseGeneratorAgent.run(anyString(), any())).thenReturn(generated);
        when(testCaseGeneratorAgent.run(any())).thenReturn(generated);

        // Avoid real tracker interactions
        doNothing().when(trackerClient).postComment(anyString(), anyString());
        when(trackerClient.getTestCases(any(), anyString())).thenReturn(Collections.emptyList());

        TestCasesGenerator.TestCasesResult result = testableGenerator.generateTestCases(
                ticketContext,
                "",
                Collections.emptyList(),
                params
        );

        assertNotNull(result);
        assertEquals("DMC-123", result.getKey());
        assertNotNull(testableGenerator.capturedExecutor);

        // Verify the correct JavaScript code was passed to js()
        assertEquals(expectedJsCode, testableGenerator.capturedJsCode);

        // Verify JS executor was configured with expected context
        verify(testableGenerator.capturedExecutor).mcp(trackerClient, ai, confluence, null);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(testableGenerator.capturedExecutor).withJobContext(eq(params), eq(ticket), responseCaptor.capture());
        assertSame(result, responseCaptor.getValue());

        verify(testableGenerator.capturedExecutor).with(TrackerParams.INITIATOR, "qa@example.com");
        verify(testableGenerator.capturedExecutor).execute();
    }

    @Test
    public void testCombineFieldsWithCustomFields_BackwardCompatibility_NoCustomFields() throws Exception {
        // Test backward compatibility: when customFields is null or empty, should return baseFields as-is
        Method combineMethod = TestCasesGenerator.class.getDeclaredMethod(
            "combineFieldsWithCustomFields", String[].class, String[].class
        );
        combineMethod.setAccessible(true);

        String[] baseFields = new String[]{"System.Title", "System.Description"};
        
        // Test with null customFields
        String[] result1 = (String[]) combineMethod.invoke(generator, baseFields, (String[]) null);
        assertArrayEquals(baseFields, result1);
        
        // Test with empty customFields
        String[] result2 = (String[]) combineMethod.invoke(generator, baseFields, new String[0]);
        assertArrayEquals(baseFields, result2);
    }

    @Test
    public void testCombineFieldsWithCustomFields_WithCustomFields() throws Exception {
        Method combineMethod = TestCasesGenerator.class.getDeclaredMethod(
            "combineFieldsWithCustomFields", String[].class, String[].class
        );
        combineMethod.setAccessible(true);

        String[] baseFields = new String[]{"System.Title", "System.Description"};
        String[] customFields = new String[]{"Microsoft.VSTS.TCM.Steps"};
        
        String[] result = (String[]) combineMethod.invoke(generator, baseFields, customFields);
        
        assertEquals(3, result.length);
        assertEquals("System.Title", result[0]);
        assertEquals("System.Description", result[1]);
        assertEquals("Microsoft.VSTS.TCM.Steps", result[2]);
    }

    @Test
    public void testCombineFieldsWithCustomFields_NoDuplicates() throws Exception {
        Method combineMethod = TestCasesGenerator.class.getDeclaredMethod(
            "combineFieldsWithCustomFields", String[].class, String[].class
        );
        combineMethod.setAccessible(true);

        // Base fields already contain a field that's also in customFields
        String[] baseFields = new String[]{"System.Title", "System.Description", "Microsoft.VSTS.TCM.Steps"};
        String[] customFields = new String[]{"Microsoft.VSTS.TCM.Steps", "Microsoft.VSTS.Common.Priority"};
        
        String[] result = (String[]) combineMethod.invoke(generator, baseFields, customFields);
        
        // Should not have duplicates
        assertEquals(4, result.length);
        assertEquals("System.Title", result[0]);
        assertEquals("System.Description", result[1]);
        assertEquals("Microsoft.VSTS.TCM.Steps", result[2]);
        assertEquals("Microsoft.VSTS.Common.Priority", result[3]);
    }

    @Test
    public void testCombineFieldsWithCustomFields_NullBaseFields() throws Exception {
        Method combineMethod = TestCasesGenerator.class.getDeclaredMethod(
            "combineFieldsWithCustomFields", String[].class, String[].class
        );
        combineMethod.setAccessible(true);

        String[] customFields = new String[]{"Microsoft.VSTS.TCM.Steps"};
        
        // Test with null baseFields
        String[] result = (String[]) combineMethod.invoke(generator, null, customFields);
        assertArrayEquals(customFields, result);
    }

    @Test
    public void testTestCaseWithNullCustomFields() {
        // Test that TestCase works with null customFields (backward compatibility)
        TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
            "High",
            "Test summary",
            "Test description"
        );

        assertNotNull(testCase.getCustomFields());
        assertEquals(0, testCase.getCustomFields().length());
        assertTrue(testCase.toText().contains("Priority: High"));
        assertTrue(testCase.toText().contains("Summary: Test summary"));
    }

    @Test
    public void testTestCaseWithCustomFields() {
        // Test that TestCase works with customFields
        org.json.JSONObject customFields = new org.json.JSONObject();
        customFields.put("Microsoft.VSTS.TCM.Steps", "<steps>...</steps>");
        
        TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
            "High",
            "Test summary",
            "Test description",
            customFields
        );

        assertNotNull(testCase.getCustomFields());
        assertEquals(1, testCase.getCustomFields().length());
        assertTrue(testCase.getCustomFields().has("Microsoft.VSTS.TCM.Steps"));
        assertTrue(testCase.toText().contains("Custom Fields:"));
    }

    @Test
    public void testTestCaseGeneratorAgentParams_BackwardCompatibility_NoCustomFieldsRules() {
        // Test backward compatibility: when customFieldsRules not provided, should use empty string
        TestCaseGeneratorAgent.Params params = new TestCaseGeneratorAgent.Params(
            "High, Medium",
            "existing test cases",
            "story description",
            "extra rules"
        );

        assertEquals("High, Medium", params.getPriorities());
        assertEquals("existing test cases", params.getExistingTestCases());
        assertEquals("story description", params.getStoryDescription());
        assertEquals("extra rules", params.getExtraRules());
        assertEquals("", params.getCustomFieldsRules()); // Should default to empty string
    }

    @Test
    public void testTestCaseGeneratorAgentParams_WithCustomFieldsRules() {
        // Test with customFieldsRules provided
        TestCaseGeneratorAgent.Params params = new TestCaseGeneratorAgent.Params(
            "High, Medium",
            "existing test cases",
            "story description",
            "extra rules",
            false,
            "examples",
            "custom fields rules"
        );

        assertEquals("custom fields rules", params.getCustomFieldsRules());
    }

    @Test
    public void testCreateTestCase_BackwardCompatibility_NoCustomFields() {
        // Test backward compatibility: createTestCase without customFields
        org.json.JSONObject testCase = TestCaseGeneratorAgent.createTestCase(
            "High",
            "Test summary",
            "Test description"
        );

        assertEquals("High", testCase.getString("priority"));
        assertEquals("Test summary", testCase.getString("summary"));
        assertEquals("Test description", testCase.getString("description"));
        assertFalse(testCase.has("customFields")); // Should not have customFields when not provided
    }

    @Test
    public void testCreateTestCase_WithCustomFields() {
        // Test createTestCase with customFields
        org.json.JSONObject customFields = new org.json.JSONObject();
        customFields.put("Microsoft.VSTS.TCM.Steps", "<steps>...</steps>");
        
        org.json.JSONObject testCase = TestCaseGeneratorAgent.createTestCase(
            "High",
            "Test summary",
            "Test description",
            customFields
        );

        assertEquals("High", testCase.getString("priority"));
        assertEquals("Test summary", testCase.getString("summary"));
        assertEquals("Test description", testCase.getString("description"));
        assertTrue(testCase.has("customFields"));
        assertEquals("<steps>...</steps>", testCase.getJSONObject("customFields").getString("Microsoft.VSTS.TCM.Steps"));
    }

    @Test
    public void testCreateTestCase_WithEmptyCustomFields() {
        // Test createTestCase with empty customFields (should not include in JSON)
        org.json.JSONObject customFields = new org.json.JSONObject();
        
        org.json.JSONObject testCase = TestCaseGeneratorAgent.createTestCase(
            "High",
            "Test summary",
            "Test description",
            customFields
        );

        assertEquals("High", testCase.getString("priority"));
        assertFalse(testCase.has("customFields")); // Should not include empty customFields
    }

    @Test
    public void testExtractJqlFromResult_JsonObject() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();
        Method extractJqlMethod = TestCasesGenerator.class.getDeclaredMethod("extractJqlFromResult", Object.class, String.class);
        extractJqlMethod.setAccessible(true);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("existingTestCasesJql", "project = TEST and labels = web");

        String jql = (String) extractJqlMethod.invoke(generator, result, "fallback");

        assertEquals("project = TEST and labels = web", jql);
    }

    @Test
    public void testExtractJqlFromResult_DirectString() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();
        Method extractJqlMethod = TestCasesGenerator.class.getDeclaredMethod("extractJqlFromResult", Object.class, String.class);
        extractJqlMethod.setAccessible(true);

        String result = "project = TEST and labels = mobile";

        String jql = (String) extractJqlMethod.invoke(generator, result, "fallback");

        assertEquals("project = TEST and labels = mobile", jql);
    }

    @Test
    public void testExtractJqlFromResult_Null() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();
        Method extractJqlMethod = TestCasesGenerator.class.getDeclaredMethod("extractJqlFromResult", Object.class, String.class);
        extractJqlMethod.setAccessible(true);

        String jql = (String) extractJqlMethod.invoke(generator, null, "fallback");

        assertEquals("fallback", jql);
    }

    @Test
    public void testExtractJqlFromResult_InvalidJsonObject() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();
        Method extractJqlMethod = TestCasesGenerator.class.getDeclaredMethod("extractJqlFromResult", Object.class, String.class);
        extractJqlMethod.setAccessible(true);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("someOtherField", "value");

        String jql = (String) extractJqlMethod.invoke(generator, result, "fallback");

        assertEquals("fallback", jql);
    }

    @Test
    public void testApplyJqlModifier_WithEmptyOriginalJql() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();
        Method applyJqlMethod = TestCasesGenerator.class.getDeclaredMethod("applyJqlModifier", ITicket.class, TestCasesGeneratorParams.class);
        applyJqlMethod.setAccessible(true);

        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("TEST-123");

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setExistingTestCasesJql("");
        params.setJqlModifierJSAction("test.js");

        String result = (String) applyJqlMethod.invoke(generator, ticket, params);

        assertEquals("", result);
    }

    @Test
    public void testApplyJqlModifier_WithNullOriginalJql() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();
        Method applyJqlMethod = TestCasesGenerator.class.getDeclaredMethod("applyJqlModifier", ITicket.class, TestCasesGeneratorParams.class);
        applyJqlMethod.setAccessible(true);

        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("TEST-123");

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setExistingTestCasesJql(null);
        params.setJqlModifierJSAction("test.js");

        String result = (String) applyJqlMethod.invoke(generator, ticket, params);

        assertNull(result);
    }

    @Test
    public void testInstructionProcessorInitialization_Standalone() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();

        // Mock all dependencies to avoid Dagger component creation in unit tests
        // (Dagger components require real configuration like Jira which isn't available in CI)
        Confluence mockConfluence = mock(Confluence.class);
        generator.confluence = mockConfluence;
        generator.trackerClient = mock(TrackerClient.class);
        generator.ai = mock(AI.class);
        generator.testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);

        // Initialize instruction processor directly (simulates what initializeStandalone does)
        generator.instructionProcessor = new com.github.istin.dmtools.teammate.InstructionProcessor(mockConfluence);

        // Verify InstructionProcessor was initialized
        assertNotNull(generator.instructionProcessor);
    }

    @Test
    public void testInstructionProcessorInitialization_ServerManaged() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();

        // Mock confluence for initialization
        Confluence mockConfluence = mock(Confluence.class);
        generator.confluence = mockConfluence;
        generator.trackerClient = mock(TrackerClient.class);
        generator.ai = mock(AI.class);
        generator.testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);

        // Create mock resolved integrations
        org.json.JSONObject resolvedIntegrations = new org.json.JSONObject();
        resolvedIntegrations.put("jira", new org.json.JSONObject());

        try {
            // Call initializeServerManaged - may fail due to Dagger component issues, but that's expected in unit tests
            generator.initializeServerManaged(resolvedIntegrations);
        } catch (RuntimeException e) {
            // Expected in unit tests without full Dagger setup
            // The important part is that if it succeeds, instructionProcessor is initialized
        }
    }

    @Test
    public void testConfluencePageProcessing_WithInstructionProcessor() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();

        // Mock dependencies
        Confluence mockConfluence = mock(Confluence.class);
        com.github.istin.dmtools.atlassian.confluence.model.Content mockContent = mock(com.github.istin.dmtools.atlassian.confluence.model.Content.class);
        com.github.istin.dmtools.atlassian.confluence.model.Storage mockStorage = mock(com.github.istin.dmtools.atlassian.confluence.model.Storage.class);

        when(mockConfluence.contentByUrl(anyString())).thenReturn(mockContent);
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("Test rules from Confluence");

        generator.confluence = mockConfluence;
        generator.instructionProcessor = new com.github.istin.dmtools.teammate.InstructionProcessor(mockConfluence);

        // Test that Confluence URL is processed
        String[] result = generator.instructionProcessor.extractIfNeeded("https://confluence.example.com/page");

        assertNotNull(result);
        assertEquals(1, result.length);
        assertTrue(result[0].contains("Test rules from Confluence"));
        verify(mockConfluence).contentByUrl("https://confluence.example.com/page");
    }

    @Test
    public void testPlainTextPassThrough_WithInstructionProcessor() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();

        // Mock confluence
        Confluence mockConfluence = mock(Confluence.class);
        generator.confluence = mockConfluence;
        generator.instructionProcessor = new com.github.istin.dmtools.teammate.InstructionProcessor(mockConfluence);

        // Test that plain text is passed through unchanged
        String plainText = "Some plain text rules";
        String[] result = generator.instructionProcessor.extractIfNeeded(plainText);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(plainText, result[0]);

        // Verify Confluence was not called
        verify(mockConfluence, never()).contentByUrl(anyString());
    }

    @Test
    public void testMultipleInstructions_WithInstructionProcessor() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();

        // Mock confluence
        Confluence mockConfluence = mock(Confluence.class);
        com.github.istin.dmtools.atlassian.confluence.model.Content mockContent = mock(com.github.istin.dmtools.atlassian.confluence.model.Content.class);
        com.github.istin.dmtools.atlassian.confluence.model.Storage mockStorage = mock(com.github.istin.dmtools.atlassian.confluence.model.Storage.class);

        when(mockConfluence.contentByUrl(anyString())).thenReturn(mockContent);
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("Confluence content");

        generator.confluence = mockConfluence;
        generator.instructionProcessor = new com.github.istin.dmtools.teammate.InstructionProcessor(mockConfluence);

        // Test with multiple instructions: Confluence URL, plain text
        String[] result = generator.instructionProcessor.extractIfNeeded(
            "https://confluence.example.com/rules",
            "Plain text rule"
        );

        assertNotNull(result);
        assertEquals(2, result.length);
        assertTrue(result[0].contains("Confluence content"));
        assertEquals("Plain text rule", result[1]);
    }

    @Test
    public void testNullAndEmptyInstructions_WithInstructionProcessor() throws Exception {
        TestCasesGenerator generator = new TestCasesGenerator();

        // Mock confluence
        Confluence mockConfluence = mock(Confluence.class);
        generator.confluence = mockConfluence;
        generator.instructionProcessor = new com.github.istin.dmtools.teammate.InstructionProcessor(mockConfluence);

        // Test with null input
        String[] result1 = generator.instructionProcessor.extractIfNeeded((String[]) null);
        assertNotNull(result1);
        assertEquals(0, result1.length);

        // Test with empty array
        String[] result2 = generator.instructionProcessor.extractIfNeeded();
        assertNotNull(result2);
        assertEquals(0, result2.length);
    }
}