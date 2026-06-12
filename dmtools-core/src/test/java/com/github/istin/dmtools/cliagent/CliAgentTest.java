// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.job.TrackerParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            List<ResultItem> results = agent.runJobImpl(params);

            assertEquals(1, results.size());
            assertNotNull(results.get(0).getResult());
            assertTrue(results.get(0).getResult().contains("hello"));
            mocked.verify(() -> CommandLineUtils.runCommand(eq("echo hello"), any(), any(), any(), eq(false)));
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("done\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            mocked.verify(() -> CommandLineUtils.runCommand(
                    argThat(cmd -> cmd.startsWith("cursor-agent") && cmd.contains("dmtools_cli_prompt_")),
                    any(), any(), any(), eq(false)));
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("hello\nworld\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            assertDoesNotThrow(() -> agent.runJobImpl(params));

            mocked.verify(() -> CommandLineUtils.runCommand(
                    eq("echo hello && echo world"), any(), any(), any(), eq(false)));
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("outputs")), "outputs/ folder should be created in working directory");
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any(), anyBoolean()))
                    .thenReturn("hello\nExit Code: 0");

            agent.runJobImpl(params);

            mocked.verify(() -> CommandLineUtils.runCommand(
                    anyString(),
                    any(),
                    argThat((Map<String, String> env) ->
                            !env.containsKey("SECRET_TOKEN") && env.containsKey("PUBLIC_VAR")),
                    any(),
                    anyBoolean()));
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
    void testReturnsOutputResponseFileContent() throws Exception {
        CliAgentParams params = new CliAgentParams();
        params.setCliCommands(new String[]{"echo hello"});
        params.setOutputType(TrackerParams.OutputType.none);
        params.setRequireCliOutputFile(false);
        params.setCleanupInputFolder(true);
        params.setCleanupOutputsFolder(false);
        params.setWorkingDirectory(tempDir.toString());

        Path outputsDir = tempDir.resolve("outputs");
        Files.createDirectories(outputsDir);
        Files.writeString(outputsDir.resolve("response.md"), "file based response");

        CliAgent agent = buildAgent();

        try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            List<ResultItem> results = agent.runJobImpl(params);

            assertEquals(1, results.size());
            assertEquals("file based response", results.get(0).getResult());
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
            mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any(), anyBoolean()))
                    .thenReturn("hello\nExit Code: 0");
            mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            agent.runJobImpl(params);

            assertTrue(Files.exists(tempDir.resolve("input/cli-agent")),
                    "input folder should be kept when cleanupInputFolder=false");
        }
    }
}
