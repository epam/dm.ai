// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.job.TrackerParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class CliAgentTest {

    @TempDir
    Path tempDir;

    private CliAgent buildAgent() {
        CliAgent agent = new CliAgent();
        agent.initializeStandalone();
        return agent;
    }

    @Test
    void testEmptyCliCommandsReturnsEmptyResult() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{});

        CliAgent agent = buildAgent();
        List<ResultItem> results = agent.runJobImpl(params);

        assertTrue(results.isEmpty());
    }

    @Test
    void testExecutesCliCommandsAndReturnsResponse() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            List<ResultItem> results = agent.runJobImpl(params);

            assertEquals(1, results.size());
            assertNotNull(results.get(0).getResult());
            assertTrue(results.get(0).getResult().contains("hello"));
            mocked.verify(() -> CommandLineUtils.runCommand(eq("echo hello"), any(), any(), any(), eq(false), any(), anyInt()));
        }
    }

    @Test
    void testAggregatesCliPromptsIntoCommands() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"cursor-agent"});
        params.setCliPrompts(new String[]{"Review the code"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("done\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            mocked.verify(() -> CommandLineUtils.runCommand(
                    argThat(cmd -> cmd.startsWith("cursor-agent") && cmd.contains("dmtools_cli_prompt_")),
                    any(), any(), any(), eq(false), any(), anyInt()));
        }
    }

    @Test
    void testAllowsShellSyntaxWithoutWhitelist() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello && echo world"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nworld\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            assertDoesNotThrow(() -> agent.runJobImpl(params));

            mocked.verify(() -> CommandLineUtils.runCommand(
                    eq("echo hello && echo world"), any(), any(), any(), eq(false), any(), anyInt()));
        }
    }

    @Test
    void testSetupAndResetHooksExecuted() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setSetup("echo setup");
        params.setReset("echo reset");
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("ok\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("ok\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            mocked.verify(() -> CommandLineUtils.runCommand(eq("echo setup"), any(), any(), any(), eq(false)));
            mocked.verify(() -> CommandLineUtils.runCommand(eq("echo reset"), any(), any(), any(), eq(false)));
        }
    }

    @Test
    void testPreJSActionReceivesTrackerClient() throws Exception {
        Path script = tempDir.resolve("preAction.js");
        Files.writeString(script, "function action(params) { jira_search_by_jql({jql: 'key = PROJ-1', fields: ['summary']}); return {success: true}; }");

        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setPreJSAction(script.toString());
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        JiraClient trackerClient = mock(JiraClient.class);
        ITicket ticket = mockTicket("PROJ-1", "Summary");
        when(trackerClient.searchAndPerform(eq("key = PROJ-1"), aryEq(new String[]{"summary"})))
                .thenReturn(List.of(ticket));
        agent.trackerClient = trackerClient;

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            verify(trackerClient).searchAndPerform(eq("key = PROJ-1"), aryEq(new String[]{"summary"}));
        }
    }

    @Test
    void testCustomParamsStoredInParams() {
        CliAgentParams params = new CliAgentParams();
        params.setCustomParams(Map.of("mode", "test", "count", 42));

        assertEquals("test", params.getCustomParams().get("mode"));
        assertEquals(42, params.getCustomParams().get("count"));
    }

    @Test
    void testCreatesOutputFolderByDefault() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("outputs")), "outputs/ folder should be created in working directory");
        }
    }

    @Test
    void testRecreatesOutputFolderAfterSetupHookRemovesIt() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setSetup("remove outputs");
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(
                            eq("remove outputs"), any(), any(), any(), eq(false)))
                    .thenAnswer(invocation -> {
                        Path outputs = tempDir.resolve("outputs");
                        if (Files.exists(outputs)) {
                            org.apache.commons.io.FileUtils.deleteDirectory(outputs.toFile());
                        }
                        return "removed";
                    });
            mocked.when(() -> CommandLineUtils.runCommand(
                            eq("echo hello"), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.isDirectory(tempDir.resolve("outputs")));
        }
    }

    @Test
    void testCleanupOutputsFolderRemovesOutputFolder() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setCleanupOutputsFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        Path outputDir = tempDir.resolve("outputs");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("response.md"), "temporary outputs");

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertFalse(Files.exists(outputDir), "outputs/ folder should be cleaned up when cleanupOutputsFolder is true");
        }
    }

    @Test
    void testCleanupOutputsFolderAlsoRemovesLegacyOutputFolder() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setCleanupOutputsFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        Path legacyOutputDir = tempDir.resolve("output");
        Files.createDirectories(legacyOutputDir);
        Files.writeString(legacyOutputDir.resolve("response.md"), "legacy output");

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertFalse(Files.exists(legacyOutputDir), "legacy output/ folder should be cleaned up when cleanupOutputsFolder is true");
        }
    }

    @Test
    void testCreatesInputContextUnderWorkingDirectory() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("input/cli-agent")),
                    "input context should be created under workingDirectory");
        }
    }

    @Test
    void testRequireCliOutputFileReturnsWarningWhenOutputMissing() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo done"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(true);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("done\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            List<ResultItem> results = agent.runJobImpl(params);

            assertEquals(1, results.size());
            assertTrue(results.get(0).getResult().contains("did not produce output file"),
                    "Should report missing output file when requireCliOutputFile=true");
        }
    }

    @Test
    void testExcludedEnvVariablesPassedToSubprocess() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());
        params.setExcludedEnvVariables(new String[]{"SECRET_TOKEN"});

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of("SECRET_TOKEN", "secret", "PUBLIC_VAR", "visible"));
            mocked.when(() -> CommandLineUtils.runCommand(
                            anyString(), any(), any(Map.class), any(), anyBoolean(), any(), anyInt(), any(), any()))
                    .thenReturn("hello\nExit Code: 0");

            agent.runJobImpl(params);

            mocked.verify(() -> CommandLineUtils.runCommand(
                    anyString(),
                    any(),
                    argThat((Map<String, String> env) ->
                            !env.containsKey("SECRET_TOKEN") && env.containsKey("PUBLIC_VAR")),
                    any(),
                    anyBoolean(),
                    any(),
                    anyInt(),
                    aryEq(new String[]{"SECRET_TOKEN"}),
                    isNull()));
        }
    }

    @Test
    void testCustomParamsSerialization() {
        CliAgentParams params = new CliAgentParams();
        params.setCustomParams(Map.of("mode", "test", "count", 42));

        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(params);

        assertTrue(json.contains("\"customParams\""), "Serialized params should contain customParams");
        assertTrue(json.contains("\"mode\""), "Serialized params should contain customParams.mode");
    }

    @Test
    void testReturnsOutputResponseCreatedByCurrentRun() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setCleanupOutputsFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenAnswer(invocation -> {
                        Path outputsDir = tempDir.resolve("outputs");
                        Files.createDirectories(outputsDir);
                        Files.writeString(outputsDir.resolve("response.md"), "file based response");
                        return "hello\nExit Code: 0";
                    });
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            List<ResultItem> results = agent.runJobImpl(params);

            assertEquals(1, results.size());
            assertEquals("file based response", results.get(0).getResult());
        }
    }

    @Test
    void testStaleOutputResponseIsRemovedBeforeExecution() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(true);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        Path outputsDir = tempDir.resolve("outputs");
        Files.createDirectories(outputsDir);
        Files.writeString(outputsDir.resolve("response.md"), "stale response");

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            List<ResultItem> results = agent.runJobImpl(params);

            assertFalse(Files.exists(outputsDir.resolve("response.md")));
            assertTrue(results.get(0).getResult().contains("did not produce output file"));
            assertFalse(results.get(0).getResult().contains("stale response"));
        }
    }

    @Test
    void testStandaloneConfluenceClientExpandsCliPrompt() throws Exception {
        BasicConfluence standaloneConfluence = mock(BasicConfluence.class);
        Content page = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(page.getStorage()).thenReturn(storage);
        when(storage.getValue()).thenReturn("<p>Standalone prompt content</p>");
        when(standaloneConfluence.contentByUrl("https://wiki.example/page")).thenReturn(page);

        CliAgent agent = new CliAgent();
        agent.trackerClient = mock(TrackerClient.class);
        agent.figmaClient = mock(FigmaClient.class);

        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"cursor-agent"});
        params.setCliPrompt("https://wiki.example/page");
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setWorkingDirectory(tempDir.toString());

        try (MockedStatic<BasicConfluence> basicConfluence = mockStatic(BasicConfluence.class);
             MockedStatic<CommandLineUtils> commands = mockStatic(CommandLineUtils.class)) {
            basicConfluence.when(BasicConfluence::getInstance).thenReturn(standaloneConfluence);
            commands.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("done\nExit Code: 0");
            commands.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.initializeStandalone();
            agent.runJobImpl(params);

            verify(standaloneConfluence).contentByUrl("https://wiki.example/page");
        }
    }

    @Test
    void testCleanupInputFolderFalseKeepsInputFolder() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("input/cli-agent")),
                    "input folder should be kept when cleanupInputFolder=false");
        }
    }

    @Test
    void testInputAsTicketKeyBuildsTicketContext() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setInput(new InputParams("PROJ-123"));
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        TrackerClient<ITicket> trackerClient = mockTrackerClient();
        agent.trackerClient = trackerClient;

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            Path inputFolder = tempDir.resolve("input/PROJ-123");
            assertTrue(Files.exists(inputFolder.resolve("request.md")),
                    "request.md should be created for the ticket");
            verify(trackerClient).performTicket(eq("PROJ-123"), any());
        }
    }

    @Test
    void testInputFolderNamedByTicketKeyForPreCliCompatibility() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setInput(new InputParams("PROJ-123"));
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        agent.trackerClient = mockTrackerClient();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("input/PROJ-123")),
                    "input folder should be named after the ticket key");
        }
    }

    @Test
    void testInputAsJqlUsesFirstTicket() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        InputParams input = new InputParams();
        input.setJql("project = PROJ");
        input.setSmart(false);
        params.setInput(input);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
        ITicket first = mockTicket("PROJ-111", "First");
        ITicket second = mockTicket("PROJ-222", "Second");
        when(trackerClient.searchAndPerform("project = PROJ", trackerClient.getExtendedQueryFields()))
                .thenReturn(List.of(first, second));
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("text");
        agent.trackerClient = trackerClient;

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("input/PROJ-111")));
            assertFalse(Files.exists(tempDir.resolve("input/PROJ-222")));
        }
    }

    @Test
    void testSmartConfluenceWritesPagesToInputFolder() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        InputParams input = new InputParams("PROJ-123");
        input.setSources(new String[]{"confluence"});
        params.setInput(input);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        TrackerClient<ITicket> trackerClient = mockTrackerClient();
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("See https://wiki/page");
        agent.trackerClient = trackerClient;

        Confluence confluence = mock(Confluence.class);
        Content page = mock(Content.class);
        when(page.getTitle()).thenReturn("Page");
        when(page.getId()).thenReturn("123");
        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn("<p>Body</p>");
        when(page.getStorage()).thenReturn(storage);
        when(confluence.parseUris(anyString())).thenReturn(Set.of("https://wiki/page"));
        when(confluence.contentByUrl("https://wiki/page")).thenReturn(page);
        when(confluence.downloadPageAttachments(anyString(), any(File.class))).thenReturn(List.of());
        agent.confluence = confluence;

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            Path confluenceFolder = tempDir.resolve("input/PROJ-123/confluence");
            assertTrue(Files.exists(confluenceFolder), "Confluence folder should be created");
            assertTrue(Files.walk(confluenceFolder).anyMatch(p -> p.toString().endsWith(".md")),
                    "Confluence page should be written");
        }
    }

    @Test
    void testSmartFigmaWritesImagesToInputFolder() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        InputParams input = new InputParams("PROJ-123");
        input.setSources(new String[]{"figma"});
        params.setInput(input);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        TrackerClient<ITicket> trackerClient = mockTrackerClient();
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("See https://figma.com/file/abc");
        agent.trackerClient = trackerClient;

        FigmaClient figmaClient = mock(FigmaClient.class);
        File imageFile = tempDir.resolve("source.png").toFile();
        Files.writeString(imageFile.toPath(), "image");
        when(figmaClient.parseUris(anyString())).thenReturn(Set.of("https://figma.com/file/abc"));
        when(figmaClient.uriToObject("https://figma.com/file/abc")).thenReturn(imageFile);
        agent.figmaClient = figmaClient;

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            Path figmaFolder = tempDir.resolve("input/PROJ-123/figma");
            assertTrue(Files.exists(figmaFolder), "Figma folder should be created");
            assertTrue(Files.list(figmaFolder).anyMatch(p -> p.toString().endsWith(".png")),
                    "Figma image should be written");
        }
    }

    @Test
    void testSmartDisabledSkipsConfluenceAndFigma() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        InputParams input = new InputParams("PROJ-123");
        input.setSmart(false);
        params.setInput(input);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        CliAgent agent = buildAgent();
        TrackerClient<ITicket> trackerClient = mockTrackerClient();
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("See https://wiki/page");
        agent.trackerClient = trackerClient;
        Confluence confluence = mock(Confluence.class);
        agent.confluence = confluence;
        FigmaClient figmaClient = mock(FigmaClient.class);
        agent.figmaClient = figmaClient;

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean(), any(), anyInt()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            verify(confluence, never()).parseUris(anyString());
            verify(figmaClient, never()).parseUris(anyString());
            assertFalse(Files.exists(tempDir.resolve("input/PROJ-123/confluence")));
            assertFalse(Files.exists(tempDir.resolve("input/PROJ-123/figma")));
        }
    }

    @Test
    void testInputStringDeserialization() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        CliAgentParams params = gson.fromJson(
                "{\"input\":\"PROJ-123\"}", CliAgentParams.class);
        assertNotNull(params.getInput());
        assertEquals("PROJ-123", params.getInput().getTicket());
    }

    @Test
    void testInputObjectDeserialization() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        CliAgentParams params = gson.fromJson(
                "{\"input\":{\"ticket\":\"PROJ-123\",\"smart\":false,\"sources\":[\"confluence\"]}}",
                CliAgentParams.class);
        assertNotNull(params.getInput());
        assertEquals("PROJ-123", params.getInput().getTicket());
        assertFalse(params.getInput().isSmart());
        assertArrayEquals(new String[]{"confluence"}, params.getInput().getSources());
    }

    private TrackerClient<ITicket> mockTrackerClient() throws Exception {
        TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
        ITicket ticket = mockTicket("PROJ-123", "Summary");
        when(trackerClient.performTicket("PROJ-123", trackerClient.getExtendedQueryFields())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("Summary\nDescription");
        return trackerClient;
    }

    private ITicket mockTicket(String key, String title) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.getTicketTitle()).thenReturn(title);
        when(ticket.getTicketDescription()).thenReturn("Description");
        doReturn(Collections.emptyList()).when(ticket).getAttachments();
        when(ticket.toText()).thenReturn(title + "\nDescription");
        return ticket;
    }
}
