package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import com.github.istin.dmtools.ai.agent.RelatedTestCasesAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.atlassian.jira.model.Relationship;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestCasesGeneratorParallelTest {

    private TestCasesGenerator generator;
    private TrackerClient trackerClient;
    private Confluence confluence;
    private RelatedTestCasesAgent relatedTestCasesAgent;
    private RelatedTestCaseAgent relatedTestCaseAgent;
    private AI ai;

    @Before
    public void study() throws Exception {
        generator = new TestCasesGenerator();
        trackerClient = mock(TrackerClient.class);
        confluence = mock(Confluence.class);
        relatedTestCasesAgent = mock(RelatedTestCasesAgent.class);
        relatedTestCaseAgent = mock(RelatedTestCaseAgent.class);
        ai = mock(AI.class);

        setField(generator, "trackerClient", trackerClient);
        setField(generator, "confluence", confluence);
        setField(generator, "relatedTestCasesAgent", relatedTestCasesAgent);
        setField(generator, "relatedTestCaseAgent", relatedTestCaseAgent);
        setField(generator, "ai", ai);

        // Initialize InstructionProcessor after confluence is set
        setField(generator, "instructionProcessor", new com.github.istin.dmtools.teammate.InstructionProcessor(confluence));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testFindAndLinkSimilarTestCasesBySummary_Parallel() throws Exception {
        // Arrange
        String ticketKey = "TEST-1";
        String ticketText = "Sample ticket text";
        List<ITicket> allTestCases = new ArrayList<>();
        // Create enough test cases to form chunks (assuming token counting will split them)
        // But here I'm mocking chunks preparation implicitly by mocking RelatedTestCasesAgent behavior or inputs.
        // Actually, logic depends on ChunkPreparation which is instantiaed inside the method.
        // I cannot easily mock ChunkPreparation as it is "new ChunkPreparation()".
        // However, I can create enough data.

        for (int i = 0; i < 5; i++) {
            ITicket tc = mock(ITicket.class);
            when(tc.getKey()).thenReturn("TC-" + i);
            when(tc.getTicketKey()).thenReturn("TC-" + i);
            when(tc.toText()).thenReturn("Test Case " + i);
            allTestCases.add(tc);
        }

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setEnableParallelTestCaseCheck(true);
        params.setParallelTestCaseCheckThreads(2);
        params.setModelTestCasesRelation("model1");
        params.setModelTestCaseRelation("model2");

        // Mock Confluence response
        Content content = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(confluence.contentByUrl(anyString())).thenReturn(content);
        when(content.getStorage()).thenReturn(storage);
        when(storage.getValue()).thenReturn("rules");

        // Mock Agents
        when(relatedTestCasesAgent.run(anyString(), any(RelatedTestCasesAgent.Params.class)))
                .thenReturn(new JSONArray("[\"TC-0\", \"TC-1\", \"TC-2\", \"TC-3\", \"TC-4\"]"));

        when(relatedTestCaseAgent.run(anyString(), any(RelatedTestCaseAgent.Params.class)))
                .thenReturn(true);

        // Act
        List<ITicket> result = generator.findAndLinkSimilarTestCasesBySummary(
                ticketKey, ticketText, allTestCases, true, "http://rules", Relationship.RELATES_TO, Collections.emptyList(), params
        );

        // Assert
        assertEquals(5, result.size());
        verify(relatedTestCasesAgent, atLeastOnce()).run(eq("model1"), any(RelatedTestCasesAgent.Params.class));
        verify(relatedTestCaseAgent, times(5)).run(eq("model2"), any(RelatedTestCaseAgent.Params.class));

        // Verify linking happened
        verify(trackerClient, times(5)).linkIssueWithRelationship(eq(ticketKey), anyString(), eq(Relationship.RELATES_TO));
    }

    @Test
    public void testFindAndLinkSimilarTestCasesBySummary_ParallelPostVerification() throws Exception {
        // Arrange
        String ticketKey = "TEST-1";
        String ticketText = "Sample ticket text";
        List<ITicket> allTestCases = new ArrayList<>();
        // Create enough test cases to test post-verification parallelism
        for (int i = 0; i < 10; i++) {
            ITicket tc = mock(ITicket.class);
            when(tc.getKey()).thenReturn("TC-" + i);
            when(tc.getTicketKey()).thenReturn("TC-" + i);
            when(tc.toText()).thenReturn("Test Case " + i);
            allTestCases.add(tc);
        }

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setEnableParallelTestCaseCheck(false); // Sequential chunk processing
        params.setEnableParallelPostVerification(true); // But parallel post-verification
        params.setParallelPostVerificationThreads(4);
        params.setModelTestCasesRelation("model1");
        params.setModelTestCaseRelation("model2");

        // Mock Confluence response
        Content content = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(confluence.contentByUrl(anyString())).thenReturn(content);
        when(content.getStorage()).thenReturn(storage);
        when(storage.getValue()).thenReturn("rules");

        // Mock Agents - return all test cases as potential matches
        when(relatedTestCasesAgent.run(anyString(), any(RelatedTestCasesAgent.Params.class)))
                .thenReturn(new JSONArray("[\"TC-0\", \"TC-1\", \"TC-2\", \"TC-3\", \"TC-4\", \"TC-5\", \"TC-6\", \"TC-7\", \"TC-8\", \"TC-9\"]"));

        // Mock verification to confirm only even numbered test cases
        when(relatedTestCaseAgent.run(anyString(), any(RelatedTestCaseAgent.Params.class)))
                .thenAnswer(invocation -> {
                    RelatedTestCaseAgent.Params params1 = invocation.getArgument(1);
                    String testText = params1.getExistingTestCase();
                    // Extract test case number from "Test Case X"
                    int num = Integer.parseInt(testText.split(" ")[2]);
                    return num % 2 == 0; // Only even numbers are confirmed
                });

        // Act
        List<ITicket> result = generator.findAndLinkSimilarTestCasesBySummary(
                ticketKey, ticketText, allTestCases, true, "http://rules", Relationship.RELATES_TO, Collections.emptyList(), params
        );

        // Assert
        assertEquals(5, result.size()); // Only even numbered test cases (0,2,4,6,8)
        verify(relatedTestCasesAgent, atLeastOnce()).run(eq("model1"), any(RelatedTestCasesAgent.Params.class));
        verify(relatedTestCaseAgent, times(10)).run(eq("model2"), any(RelatedTestCaseAgent.Params.class));

        // Verify only even numbered test cases were linked
        verify(trackerClient, times(5)).linkIssueWithRelationship(eq(ticketKey), argThat(key -> {
            int num = Integer.parseInt(key.split("-")[1]);
            return num % 2 == 0;
        }), eq(Relationship.RELATES_TO));
    }

    @Test
    public void testFindAndLinkSimilarTestCasesBySummary_BothParallel() throws Exception {
        // Arrange
        String ticketKey = "TEST-1";
        String ticketText = "Sample ticket text";
        List<ITicket> allTestCases = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ITicket tc = mock(ITicket.class);
            when(tc.getKey()).thenReturn("TC-" + i);
            when(tc.getTicketKey()).thenReturn("TC-" + i);
            when(tc.toText()).thenReturn("Test Case " + i);
            allTestCases.add(tc);
        }

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setEnableParallelTestCaseCheck(true); // Parallel chunk processing
        params.setParallelTestCaseCheckThreads(2);
        params.setEnableParallelPostVerification(true); // Parallel post-verification
        params.setParallelPostVerificationThreads(3);
        params.setModelTestCasesRelation("model1");
        params.setModelTestCaseRelation("model2");

        Content content = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(confluence.contentByUrl(anyString())).thenReturn(content);
        when(content.getStorage()).thenReturn(storage);
        when(storage.getValue()).thenReturn("rules");

        when(relatedTestCasesAgent.run(anyString(), any(RelatedTestCasesAgent.Params.class)))
                .thenReturn(new JSONArray("[\"TC-0\", \"TC-1\", \"TC-2\", \"TC-3\", \"TC-4\", \"TC-5\", \"TC-6\", \"TC-7\"]"));
        when(relatedTestCaseAgent.run(anyString(), any(RelatedTestCaseAgent.Params.class)))
                .thenReturn(true);

        // Act
        List<ITicket> result = generator.findAndLinkSimilarTestCasesBySummary(
                ticketKey, ticketText, allTestCases, true, "http://rules", Relationship.RELATES_TO, Collections.emptyList(), params
        );

        // Assert
        assertEquals(8, result.size());
        verify(relatedTestCasesAgent, atLeastOnce()).run(eq("model1"), any(RelatedTestCasesAgent.Params.class));
        verify(relatedTestCaseAgent, times(8)).run(eq("model2"), any(RelatedTestCaseAgent.Params.class));
        verify(trackerClient, times(8)).linkIssueWithRelationship(eq(ticketKey), anyString(), eq(Relationship.RELATES_TO));
    }

    @Test
    public void testFindAndLinkSimilarTestCasesBySummary_Sequential() throws Exception {
        // Arrange
        String ticketKey = "TEST-1";
        String ticketText = "Sample ticket text";
        List<ITicket> allTestCases = new ArrayList<>();
        for (int i = 0; i < 2; i++) { // Smaller set
            ITicket tc = mock(ITicket.class);
            when(tc.getKey()).thenReturn("TC-" + i);
            when(tc.getTicketKey()).thenReturn("TC-" + i);
            when(tc.toText()).thenReturn("Test Case " + i);
            allTestCases.add(tc);
        }

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setEnableParallelTestCaseCheck(false); // Disable parallel
        params.setModelTestCasesRelation("model1");
        params.setModelTestCaseRelation("model2");

        Content content = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(confluence.contentByUrl(anyString())).thenReturn(content);
        when(content.getStorage()).thenReturn(storage);
        when(storage.getValue()).thenReturn("rules");

        when(relatedTestCasesAgent.run(anyString(), any(RelatedTestCasesAgent.Params.class)))
                .thenReturn(new JSONArray("[\"TC-0\", \"TC-1\"]"));
        when(relatedTestCaseAgent.run(anyString(), any(RelatedTestCaseAgent.Params.class)))
                .thenReturn(true);

        // Act
        List<ITicket> result = generator.findAndLinkSimilarTestCasesBySummary(
                ticketKey, ticketText, allTestCases, true, "http://rules", Relationship.RELATES_TO, Collections.emptyList(), params
        );

        // Assert
        assertEquals(2, result.size());
        verify(trackerClient, times(2)).linkIssueWithRelationship(eq(ticketKey), anyString(), eq(Relationship.RELATES_TO));
    }
}

