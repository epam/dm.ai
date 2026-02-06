package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.job.Params;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TrackerClient validation in Teammate.
 * Tests that Teammate fails gracefully when TrackerClient is not configured but inputJql is provided.
 */
public class TeammateTrackerValidationTest {

    private Teammate teammate;
    private Teammate.TeammateParams params;

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
    private TrackerClient<ITicket> trackerClient;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Create teammate instance
        teammate = new Teammate();
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
        params.setOutputType(Params.OutputType.none);

        // Mock context orchestrator
        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());

        // Mock AI agent
        when(genericRequestAgent.run(org.mockito.ArgumentMatchers.any())).thenReturn("AI response");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testShouldFailWhenTrackerClientIsNullAndInputJqlIsProvided() {
        // Arrange
        teammate.trackerClient = null; // Simulate missing TrackerClient configuration
        params.setInputJql("key = TEST-123");

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            teammate.runJobImpl(params);
        });

        // Verify error message is informative
        assertTrue(exception.getMessage().contains("TrackerClient is not configured"),
                "Error message should mention TrackerClient is not configured");
        assertTrue(exception.getMessage().contains("inputJql is provided"),
                "Error message should mention inputJql is provided");
        assertTrue(exception.getMessage().contains("JIRA_BASE_PATH"),
                "Error message should mention JIRA configuration");
        assertTrue(exception.getMessage().contains("ADO_ORGANIZATION"),
                "Error message should mention ADO configuration");
        assertTrue(exception.getMessage().contains("RALLY_PATH"),
                "Error message should mention Rally configuration");
    }

    @Test
    void testShouldFailWhenTrackerClientIsNullAndInputJqlHasWhitespace() {
        // Arrange
        teammate.trackerClient = null;
        params.setInputJql("  key = TEST-123  "); // JQL with leading/trailing whitespace

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            teammate.runJobImpl(params);
        });

        // Verify error is thrown even with whitespace
        assertTrue(exception.getMessage().contains("TrackerClient is not configured"));
    }

    @Test
    void testShouldSucceedWhenTrackerClientIsNullAndInputJqlIsNull() throws Exception {
        // Arrange
        teammate.trackerClient = null;
        params.setInputJql(null); // No JQL query

        // Act - should not throw exception
        assertDoesNotThrow(() -> {
            // This should work because inputJql is null (no tracker needed)
            // Note: The actual execution will fail later because genericRequestAgent is mocked
            // but we're only testing the validation logic here
            try {
                teammate.runJobImpl(params);
            } catch (NullPointerException e) {
                // Expected due to mocked dependencies, ignore
            }
        });
    }

    @Test
    void testShouldSucceedWhenTrackerClientIsNullAndInputJqlIsEmpty() throws Exception {
        // Arrange
        teammate.trackerClient = null;
        params.setInputJql(""); // Empty JQL query

        // Act - should not throw IllegalStateException
        assertDoesNotThrow(() -> {
            try {
                teammate.runJobImpl(params);
            } catch (NullPointerException e) {
                // Expected due to mocked dependencies, ignore
            }
        });
    }

    @Test
    void testShouldSucceedWhenTrackerClientIsNullAndInputJqlIsWhitespaceOnly() throws Exception {
        // Arrange
        teammate.trackerClient = null;
        params.setInputJql("   "); // Whitespace-only JQL query

        // Act - should not throw IllegalStateException
        assertDoesNotThrow(() -> {
            try {
                teammate.runJobImpl(params);
            } catch (NullPointerException e) {
                // Expected due to mocked dependencies, ignore
            }
        });
    }

    @Test
    void testShouldSucceedWhenTrackerClientIsProvidedAndInputJqlIsProvided() throws Exception {
        // Arrange
        teammate.trackerClient = trackerClient; // TrackerClient is configured
        params.setInputJql("key = TEST-123");

        // Mock tracker behavior
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});

        // Act - should not throw exception (validation passes)
        assertDoesNotThrow(() -> {
            try {
                teammate.runJobImpl(params);
            } catch (Exception e) {
                // May fail due to mocked dependencies, but validation should pass
                // We're only testing that IllegalStateException is NOT thrown
                if (e instanceof IllegalStateException) {
                    throw e; // Re-throw IllegalStateException to fail the test
                }
                // Ignore other exceptions (expected due to mocking)
            }
        });
    }

    @Test
    void testErrorMessageContainsAllRequiredConfigurations() {
        // Arrange
        teammate.trackerClient = null;
        params.setInputJql("project = DEMO");

        // Act
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            teammate.runJobImpl(params);
        });

        // Assert - verify all configuration options are mentioned
        String message = exception.getMessage();

        // Jira configuration
        assertTrue(message.contains("JIRA_BASE_PATH"), "Should mention JIRA_BASE_PATH");
        assertTrue(message.contains("JIRA_EMAIL"), "Should mention JIRA_EMAIL");
        assertTrue(message.contains("JIRA_API_TOKEN"), "Should mention JIRA_API_TOKEN");

        // ADO configuration
        assertTrue(message.contains("ADO_ORGANIZATION"), "Should mention ADO_ORGANIZATION");
        assertTrue(message.contains("ADO_PROJECT"), "Should mention ADO_PROJECT");
        assertTrue(message.contains("ADO_PAT_TOKEN"), "Should mention ADO_PAT_TOKEN");

        // Rally configuration
        assertTrue(message.contains("RALLY_PATH"), "Should mention RALLY_PATH");
        assertTrue(message.contains("RALLY_TOKEN"), "Should mention RALLY_TOKEN");

        // Alternative action
        assertTrue(message.contains("remove inputJql") || message.contains("inputJql parameter"),
                "Should mention removing inputJql as an alternative");
    }
}
