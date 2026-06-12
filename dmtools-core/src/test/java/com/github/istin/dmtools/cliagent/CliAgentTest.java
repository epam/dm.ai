// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.job.TrackerParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

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
}
