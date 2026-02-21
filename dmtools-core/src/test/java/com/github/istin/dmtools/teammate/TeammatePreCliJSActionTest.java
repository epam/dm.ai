package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.job.JavaScriptExecutor;
import com.github.istin.dmtools.job.Params;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TeammatePreCliJSActionTest {

    /**
     * Test subclass that overrides the protected js() method to capture calls,
     * allowing verification without requiring direct access to the protected method.
     */
    private static class TeammateWithJsSpy extends Teammate {
        final List<String> jsCalls = new ArrayList<>();
        private JavaScriptExecutor capturedExecutor;
        private String targetScript;

        TeammateWithJsSpy(String targetScript, JavaScriptExecutor capturedExecutor) {
            this.targetScript = targetScript;
            this.capturedExecutor = capturedExecutor;
        }

        @Override
        protected JavaScriptExecutor js(String jsCode) {
            jsCalls.add(jsCode);
            if (targetScript != null && targetScript.equals(jsCode)) {
                return capturedExecutor;
            }
            return super.js(jsCode);
        }
    }

    @Mock
    private AI ai;

    @Mock
    private GenericRequestAgent genericRequestAgent;

    @Mock
    private ContextOrchestrator contextOrchestrator;

    @Mock
    private UriToObjectFactory uriToObjectFactory;

    @Mock
    private ITicket ticket;

    @TempDir
    Path tempDir;

    private TrackerClient<ITicket> trackerClient;
    private Teammate teammate;
    private Teammate.TeammateParams params;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        trackerClient = mock(TrackerClient.class, withSettings().extraInterfaces(UriToObject.class));
        UriToObject uriToObject = (UriToObject) trackerClient;
        when(uriToObject.parseUris(any())).thenReturn(Set.of());
        when(uriToObject.uriToObject(any())).thenReturn(null);

        teammate = new Teammate();
        teammate.trackerClient = trackerClient;
        teammate.ai = ai;
        teammate.genericRequestAgent = genericRequestAgent;
        teammate.contextOrchestrator = contextOrchestrator;
        teammate.uriToObjectFactory = uriToObjectFactory;
        teammate.instructionProcessor = new InstructionProcessor(null, tempDir.toString());

        params = new Teammate.TeammateParams();
        RequestDecompositionAgent.Result agentParams = new RequestDecompositionAgent.Result(
            "Test role", "Test request",
            new String[]{"Test question"}, new String[]{"Test task"},
            new String[]{"Test instructions"}, "Test known info",
            "Test formatting", "Test fewshots"
        );
        params.setAgentParams(agentParams);
        params.setInputJql("key = TEST-1");
        params.setOutputType(Params.OutputType.comment);

        when(ticket.getKey()).thenReturn("TEST-1");
        when(ticket.getTicketKey()).thenReturn("TEST-1");
        when(ticket.toText()).thenReturn("Mock ticket text");
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());

        when(trackerClient.getTextFieldsOnly(any())).thenReturn("Test fields");
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary", "description"});
        when(trackerClient.getComments(anyString(), any())).thenReturn(Collections.emptyList());
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0, JiraClient.Performer.class);
            try { performer.perform(ticket); } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());

        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());
        when(genericRequestAgent.run(any())).thenReturn("AI response");
    }

    @Test
    void testNullPreCliJSActionDoesNotFail() throws Exception {
        params.setPreCliJSAction(null);
        params.setCliCommands(new String[]{"echo ok"});

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any()))
                .thenReturn("ok\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                .thenReturn(Map.of());

            assertDoesNotThrow(() -> teammate.runJobImpl(params));
        }
    }

    @Test
    void testBlankPreCliJSActionDoesNotFail() throws Exception {
        params.setPreCliJSAction("   ");
        params.setCliCommands(new String[]{"echo ok"});

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any()))
                .thenReturn("ok\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                .thenReturn(Map.of());

            assertDoesNotThrow(() -> teammate.runJobImpl(params));
        }
    }

    @Test
    void testPreCliJSActionNotInvokedWithoutCliCommands() throws Exception {
        JavaScriptExecutor mockExecutor = buildMockExecutor();
        TeammateWithJsSpy spy = buildSpy("agents/js/extendFolder.js", mockExecutor);

        params.setPreCliJSAction("agents/js/extendFolder.js");
        params.setCliCommands(null);

        spy.runJobImpl(params);

        assertFalse(spy.jsCalls.contains("agents/js/extendFolder.js"),
            "preCliJSAction must not be called when cliCommands is null");
    }

    @Test
    void testPreCliJSActionInvokedWithInputFolderPath() throws Exception {
        JavaScriptExecutor mockExecutor = buildMockExecutor();
        TeammateWithJsSpy spy = buildSpy("agents/js/extendFolder.js", mockExecutor);

        params.setPreCliJSAction("agents/js/extendFolder.js");
        params.setCliCommands(new String[]{"echo ok"});
        params.setCleanupInputFolder(true);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any()))
                    .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                spy.runJobImpl(params);
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        assertTrue(spy.jsCalls.contains("agents/js/extendFolder.js"),
            "preCliJSAction script must be invoked when cliCommands is set");

        verify(mockExecutor).with(eq("inputFolderPath"), argThat(path ->
            path instanceof String && ((String) path).contains("TEST-1")
        ));
    }

    @Test
    void testPreCliJSActionJsonDeserialization() {
        String json = "{\"preCliJSAction\": \"agents/js/extendFolder.js\", \"cliCommands\": [\"echo ok\"]}";
        Teammate.TeammateParams deserialized = new Gson().fromJson(json, Teammate.TeammateParams.class);

        assertEquals("agents/js/extendFolder.js", deserialized.getPreCliJSAction());
        assertNotNull(deserialized.getCliCommands());
        assertEquals(1, deserialized.getCliCommands().length);
    }

    @Test
    void testPreCliJSActionNotInvokedWithEmptyCliCommands() throws Exception {
        JavaScriptExecutor mockExecutor = buildMockExecutor();
        TeammateWithJsSpy spy = buildSpy("agents/js/extendFolder.js", mockExecutor);

        params.setPreCliJSAction("agents/js/extendFolder.js");
        params.setCliCommands(new String[]{});  // empty array, not null

        spy.runJobImpl(params);

        assertFalse(spy.jsCalls.contains("agents/js/extendFolder.js"),
            "preCliJSAction must not be called when cliCommands is an empty array");
    }

    @Test
    void testPreCliJSActionExceptionDoesNotStopCliExecution() throws Exception {
        JavaScriptExecutor throwingExecutor = mock(JavaScriptExecutor.class);
        when(throwingExecutor.mcp(any(), any(), any(), any())).thenReturn(throwingExecutor);
        when(throwingExecutor.withJobContext(any(), any(), any())).thenReturn(throwingExecutor);
        when(throwingExecutor.with(anyString(), any())).thenReturn(throwingExecutor);
        when(throwingExecutor.execute()).thenThrow(new RuntimeException("JS script failed"));

        TeammateWithJsSpy spy = buildSpy("agents/js/extendFolder.js", throwingExecutor);

        params.setPreCliJSAction("agents/js/extendFolder.js");
        params.setCliCommands(new String[]{"echo ok"});
        params.setCleanupInputFolder(true);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any()))
                    .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                assertDoesNotThrow(() -> spy.runJobImpl(params));

                // CLI command must still have been executed despite JS failure
                mocked.verify(() -> CommandLineUtils.runCommand(eq("echo ok"), any(), any()));
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    // ---- helpers ----

    private JavaScriptExecutor buildMockExecutor() throws Exception {
        JavaScriptExecutor mockExecutor = mock(JavaScriptExecutor.class);
        when(mockExecutor.mcp(any(), any(), any(), any())).thenReturn(mockExecutor);
        when(mockExecutor.withJobContext(any(), any(), any())).thenReturn(mockExecutor);
        when(mockExecutor.with(anyString(), any())).thenReturn(mockExecutor);
        when(mockExecutor.execute()).thenReturn(null);
        return mockExecutor;
    }

    private TeammateWithJsSpy buildSpy(String targetScript, JavaScriptExecutor executor) {
        TeammateWithJsSpy spy = new TeammateWithJsSpy(targetScript, executor);
        spy.trackerClient = trackerClient;
        spy.ai = ai;
        spy.genericRequestAgent = genericRequestAgent;
        spy.contextOrchestrator = contextOrchestrator;
        spy.uriToObjectFactory = uriToObjectFactory;
        spy.instructionProcessor = new InstructionProcessor(null, tempDir.toString());
        return spy;
    }
}
