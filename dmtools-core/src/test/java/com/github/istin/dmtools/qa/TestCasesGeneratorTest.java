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
        params.setExamples(null);
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
        when(testCaseGeneratorAgent.run(any())).thenReturn(generated);

        // Avoid real tracker interactions
        doNothing().when(trackerClient).postComment(anyString(), anyString());

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
}