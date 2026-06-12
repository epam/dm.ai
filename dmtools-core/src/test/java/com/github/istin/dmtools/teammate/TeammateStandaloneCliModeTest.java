// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

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
import com.github.istin.dmtools.job.ResultItem;
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

/**
 * Tests for Teammate standalone CLI mode (no inputJql) and JS-action skip flags.
 */
class TeammateStandaloneCliModeTest {

    /**
     * Test subclass that overrides the protected js() method to capture calls.
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

    @TempDir
    Path tempDir;

    private TrackerClient<ITicket> trackerClient;
    private Teammate.TeammateParams params;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        trackerClient = mock(TrackerClient.class, withSettings().extraInterfaces(UriToObject.class));
        UriToObject uriToObject = (UriToObject) trackerClient;
        when(uriToObject.parseUris(any())).thenReturn(Set.of());
        when(uriToObject.uriToObject(any())).thenReturn(null);

        params = new Teammate.TeammateParams();
        RequestDecompositionAgent.Result agentParams = new RequestDecompositionAgent.Result(
            "Role", "Request",
            new String[]{}, new String[]{},
            new String[]{}, "Known info",
            "", ""
        );
        params.setAgentParams(agentParams);
        params.setOutputType(Params.OutputType.none);
        params.setSkipAIProcessing(true);
        params.setRequireCliOutputFile(false);

        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary", "description"});
        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());
    }

    private Teammate buildTeammate() {
        Teammate teammate = new Teammate();
        teammate.trackerClient = trackerClient;
        teammate.ai = ai;
        teammate.genericRequestAgent = genericRequestAgent;
        teammate.contextOrchestrator = contextOrchestrator;
        teammate.uriToObjectFactory = uriToObjectFactory;
        teammate.instructionProcessor = new InstructionProcessor(null, tempDir.toString());
        teammate.agentParamsFileWriter = new AgentParamsFileWriter(teammate.instructionProcessor);
        return teammate;
    }

    @Test
    void testStandaloneModeDoesNotCallSearchAndPerform() throws Exception {
        params.setInputJql(null);
        params.setCliCommands(new String[]{"echo ok"});

        Teammate teammate = buildTeammate();

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                    .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);

                verify(trackerClient, never()).searchAndPerform(any(), any(), any());
                assertEquals(1, results.size());
                assertEquals("standalone", results.get(0).getKey());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testStandaloneModeExecutesCliCommandsWithAggregatedPrompt() throws Exception {
        params.setInputJql("");
        params.setCliPrompts(new String[]{"prompt from array"});
        params.setCliCommands(new String[]{"cursor-agent"});
        params.setCleanupInputFolder(true);

        Teammate teammate = buildTeammate();

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                    .thenReturn("executed\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                teammate.runJobImpl(params);

                mocked.verify(() -> CommandLineUtils.runCommand(
                        argThat(cmd -> cmd.startsWith("cursor-agent") && cmd.contains("dmtools_cli_prompt_")),
                        any(), any(), any()));
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testStandaloneModeSkipsJsActionsByDefault() throws Exception {
        JavaScriptExecutor mockExecutor = buildMockExecutor();
        TeammateWithJsSpy spy = new TeammateWithJsSpy("agents/js/pre.js", mockExecutor);
        inject(spy);

        params.setInputJql(null);
        params.setPreJSAction("agents/js/pre.js");
        params.setPreCliJSAction("agents/js/preCli.js");
        params.setPostJSAction("agents/js/post.js");
        params.setCliCommands(new String[]{"echo ok"});

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                    .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                spy.runJobImpl(params);

                assertFalse(spy.jsCalls.contains("agents/js/pre.js"),
                    "preJSAction must not be invoked in standalone mode");
                assertFalse(spy.jsCalls.contains("agents/js/preCli.js"),
                    "preCliJSAction must not be invoked in standalone mode");
                assertFalse(spy.jsCalls.contains("agents/js/post.js"),
                    "postJSAction must not be invoked in standalone mode");
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testSkipFlagsPreventJsActionsWithTicket() throws Exception {
        JavaScriptExecutor mockExecutor = buildMockExecutor();
        TeammateWithJsSpy spy = new TeammateWithJsSpy("agents/js/pre.js", mockExecutor);
        inject(spy);

        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn("TEST-1");
        when(ticket.getTicketKey()).thenReturn("TEST-1");
        when(ticket.toText()).thenReturn("Mock ticket text");
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("Test fields");
        when(trackerClient.getComments(anyString(), any())).thenReturn(Collections.emptyList());
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0, JiraClient.Performer.class);
            try { performer.perform(ticket); } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());

        params.setInputJql("key = TEST-1");
        params.setPreJSAction("agents/js/pre.js");
        params.setPreCliJSAction("agents/js/preCli.js");
        params.setPostJSAction("agents/js/post.js");
        params.setCliCommands(new String[]{"echo ok"});
        params.setSkipPreJSAction(true);
        params.setSkipPreCliJSAction(true);
        params.setSkipPostJSAction(true);
        params.setCleanupInputFolder(true);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                    .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                spy.runJobImpl(params);

                assertFalse(spy.jsCalls.contains("agents/js/pre.js"),
                    "preJSAction must be skipped when skipPreJSAction=true");
                assertFalse(spy.jsCalls.contains("agents/js/preCli.js"),
                    "preCliJSAction must be skipped when skipPreCliJSAction=true");
                assertFalse(spy.jsCalls.contains("agents/js/post.js"),
                    "postJSAction must be skipped when skipPostJSAction=true");
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testStandaloneModeWorksWithoutTrackerClient() throws Exception {
        Teammate teammate = buildTeammate();
        teammate.trackerClient = null;

        params.setInputJql(null);
        params.setCliCommands(new String[]{"echo ok"});
        params.setCleanupInputFolder(true);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                    .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);

                assertEquals(1, results.size());
                assertEquals("standalone", results.get(0).getKey());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    private void inject(Teammate teammate) {
        teammate.trackerClient = trackerClient;
        teammate.ai = ai;
        teammate.genericRequestAgent = genericRequestAgent;
        teammate.contextOrchestrator = contextOrchestrator;
        teammate.uriToObjectFactory = uriToObjectFactory;
        teammate.instructionProcessor = new InstructionProcessor(null, tempDir.toString());
        teammate.agentParamsFileWriter = new AgentParamsFileWriter(teammate.instructionProcessor);
    }

    private JavaScriptExecutor buildMockExecutor() throws Exception {
        JavaScriptExecutor mockExecutor = mock(JavaScriptExecutor.class);
        when(mockExecutor.mcp(any(), any(), any(), any())).thenReturn(mockExecutor);
        when(mockExecutor.withJobContext(any(), any(), any())).thenReturn(mockExecutor);
        when(mockExecutor.with(anyString(), any())).thenReturn(mockExecutor);
        when(mockExecutor.execute()).thenReturn(null);
        return mockExecutor;
    }
}
