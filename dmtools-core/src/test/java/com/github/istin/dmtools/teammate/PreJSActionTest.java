package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.job.TrackerParams;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.job.Params;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for preJSAction functionality in Teammate and Expert jobs.
 * Tests the execution of JavaScript functions before AI processing begins.
 */
public class PreJSActionTest {

    private Teammate teammate;
    private Teammate.TeammateParams params;

    @Mock
    private TrackerClient<ITicket> trackerClient;

    @Mock
    private AI ai;

    @Mock
    private GenericRequestAgent genericRequestAgent;

    @Mock
    private ContextOrchestrator contextOrchestrator;

    @Mock
    private UriToObjectFactory uriToObjectFactory;

    @Mock
    private Confluence confluence;

    @Mock
    private ITicket ticket;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Create a TrackerClient mock that also implements UriToObject
        trackerClient = mock(TrackerClient.class, withSettings().extraInterfaces(UriToObject.class));
        UriToObject uriToObjectClient = (UriToObject) trackerClient;
        when(uriToObjectClient.parseUris(any())).thenReturn(Set.of());
        when(uriToObjectClient.uriToObject(any())).thenReturn(null);

        // Create teammate instance and inject mocked dependencies
        teammate = new Teammate();
        teammate.trackerClient = trackerClient;
        teammate.ai = ai;
        teammate.genericRequestAgent = genericRequestAgent;
        teammate.contextOrchestrator = contextOrchestrator;
        teammate.uriToObjectFactory = uriToObjectFactory;
        teammate.confluence = confluence;
        teammate.instructionProcessor = new InstructionProcessor(confluence);

        // Set up test parameters
        params = new Teammate.TeammateParams();
        RequestDecompositionAgent.Result agentParams = new RequestDecompositionAgent.Result(
                "Test role",
                "Test request",
                new String[]{"Test question"},
                new String[]{"Test task"},
                new String[]{"Test instructions"},
                "Test known info",
                "Test formatting rules",
                "Test few shots"
        );
        params.setAgentParams(agentParams);
        params.setInputJql("key = TEST-123");
        params.setOutputType(Params.OutputType.none);
        params.setInitiator("test@example.com");

        // Mock ticket
        when(ticket.getKey()).thenReturn("TEST-123");
        when(ticket.getTicketKey()).thenReturn("TEST-123");
        when(ticket.toText()).thenReturn("Mock ticket text");
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());
        when(ticket.getTicketTitle()).thenReturn("Test Ticket");
        when(ticket.getTicketDescription()).thenReturn("Test Description");
        when(ticket.getStatus()).thenReturn("Open");
        when(ticket.getIssueType()).thenReturn("Task");
        when(ticket.getPriority()).thenReturn("Medium");
        
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("summary", "Test Ticket");
        fieldsJson.put("status", new JSONObject().put("name", "Open"));
        when(ticket.getFieldsAsJSON()).thenReturn(fieldsJson);

        // Mock trackerClient
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("Test fields");
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});

        // Mock context orchestrator
        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());

        // Mock AI agent
        when(genericRequestAgent.run(any())).thenReturn("AI response");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testTrackerParamsHasPreJSActionField() {
        // Arrange & Act
        TrackerParams trackerParams = new TrackerParams();
        trackerParams.setPreJSAction("function action(params) { return true; }");

        // Assert
        assertNotNull(trackerParams.getPreJSAction());
        assertEquals("function action(params) { return true; }", trackerParams.getPreJSAction());
    }

    @Test
    void testPreJSActionConstantExists() {
        // Assert
        assertEquals("preJSAction", TrackerParams.PRE_ACTION);
    }

    @Test
    void testPreActionNotExecutedWhenNull() throws Exception {
        // Arrange
        params.setPreJSAction(null);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("TEST-123", results.get(0).getKey());
        // Verify AI processing continued (not skipped)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionNotExecutedWhenEmpty() throws Exception {
        // Arrange
        params.setPreJSAction("");
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing continued
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionReturnsTrueContinuesProcessing() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                console.log('Pre-action executing');
                return true;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("TEST-123", results.get(0).getKey());
        // Verify AI processing was executed
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionReturnsFalseSkipsProcessing() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                console.log('Pre-action: skipping ticket');
                return false;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("TEST-123", results.get(0).getKey());
        assertEquals("Skipped by pre-action", results.get(0).getResult());
        // Verify AI processing was NOT executed
        verify(genericRequestAgent, never()).run(any());
    }

    @Test
    void testPreActionHasAccessToTicketParameters() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                var ticket = params.ticket;
                console.log('Processing ticket: ' + ticket.key);
                console.log('Status: ' + ticket.status);
                console.log('Title: ' + ticket.title);
                return ticket.key === 'TEST-123';
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing was executed (returned true)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionHasAccessToJobParams() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                var jobParams = params.jobParams;
                console.log('Initiator: ' + params.initiator);
                return params.initiator !== null;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing was executed
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionResponseParameterIsNull() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                // Response should be null in pre-action
                return params.response === null || params.response === undefined;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing was executed (returned true because response is null)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionExceptionDoesNotBlockProcessing() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                throw new Error('Pre-action error');
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act - should not throw exception
        assertDoesNotThrow(() -> {
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            // Verify AI processing continued despite error (fail-safe behavior)
            verify(genericRequestAgent, times(1)).run(any());
        });
    }

    @Test
    void testPreActionReturnsNullContinuesProcessing() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                console.log('Pre-action returning null');
                return null;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing was executed (null is not false)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionReturnsUndefinedContinuesProcessing() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                // Implicit return undefined
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing was executed (undefined is not false)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionWithComplexLogic() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                var ticket = params.ticket;
                
                // Complex validation logic
                if (!ticket || !ticket.key) {
                    console.log('Invalid ticket');
                    return false;
                }
                
                // Check if ticket has specific status
                if (ticket.status === 'Closed') {
                    console.log('Ticket is closed, skipping');
                    return false;
                }
                
                // Check if ticket is a specific type
                if (ticket.issueType === 'Epic') {
                    console.log('Ticket is Epic, skipping');
                    return false;
                }
                
                console.log('All checks passed, continuing');
                return true;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        // Verify AI processing was executed (status is Open, not Closed)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionCanSkipBasedOnTicketStatus() throws Exception {
        // Arrange
        when(ticket.getStatus()).thenReturn("Closed");
        
        String jsCode = """
            function action(params) {
                var ticket = params.ticket;
                return ticket.status !== 'Closed';
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Skipped by pre-action", results.get(0).getResult());
        // Verify AI processing was NOT executed
        verify(genericRequestAgent, never()).run(any());
    }

    @Test
    void testPreActionExecutedBeforeContextPreparation() throws Exception {
        // Arrange
        String jsCode = """
            function action(params) {
                console.log('Pre-action executing before context preparation');
                return false;
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Skipped by pre-action", results.get(0).getResult());
        
        // Verify context orchestrator was NOT called (since we skipped early)
        // Note: context orchestrator is called once in the beginning for known info processing
        verify(contextOrchestrator, atMost(2)).summarize();
    }

    @Test
    void testMultipleTicketsWithPreAction() throws Exception {
        // Arrange
        ITicket ticket1 = mock(ITicket.class);
        when(ticket1.getKey()).thenReturn("TEST-1");
        when(ticket1.getTicketKey()).thenReturn("TEST-1");
        when(ticket1.getStatus()).thenReturn("Open");
        when(ticket1.toText()).thenReturn("Ticket 1");
        when(ticket1.getAttachments()).thenReturn(Collections.emptyList());
        when(ticket1.getTicketTitle()).thenReturn("Ticket 1");
        when(ticket1.getFieldsAsJSON()).thenReturn(new JSONObject());
        
        ITicket ticket2 = mock(ITicket.class);
        when(ticket2.getKey()).thenReturn("TEST-2");
        when(ticket2.getTicketKey()).thenReturn("TEST-2");
        when(ticket2.getStatus()).thenReturn("Closed");
        when(ticket2.toText()).thenReturn("Ticket 2");
        when(ticket2.getAttachments()).thenReturn(Collections.emptyList());
        when(ticket2.getTicketTitle()).thenReturn("Ticket 2");
        when(ticket2.getFieldsAsJSON()).thenReturn(new JSONObject());

        String jsCode = """
            function action(params) {
                var ticket = params.ticket;
                // Skip closed tickets
                return ticket.status !== 'Closed';
            }
            """;
        params.setPreJSAction(jsCode);
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket1);
            performer.perform(ticket2);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("TEST-1", results.get(0).getKey());
        assertEquals("TEST-2", results.get(1).getKey());
        assertEquals("Skipped by pre-action", results.get(1).getResult());
        
        // Verify AI processing was called only once (for TEST-1, not TEST-2)
        verify(genericRequestAgent, times(1)).run(any());
    }

    @Test
    void testPreActionWithInlineJavaScript() throws Exception {
        // Arrange
        params.setPreJSAction("function action(params) { return true; }");
        
        doAnswer(invocation -> {
            var performer = invocation.getArgument(0, JiraClient.Performer.class);
            performer.perform(ticket);
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Act
        List<ResultItem> results = teammate.runJobImpl(params);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(genericRequestAgent, times(1)).run(any());
    }
}
