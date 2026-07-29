// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class ToolWizardCoverageTest {

    /**
     * Terminal stub that replays scripted lines (no blocking stdin reads) and
     * records all written output.  An exhausted queue yields {@code null},
     * simulating EOF.
     */
    private static final class ScriptedTerminal extends Terminal {
        private final Queue<String> lines;
        private final StringBuilder output = new StringBuilder();

        ScriptedTerminal(String... lines) {
            super(null, null, false);
            this.lines = new ArrayDeque<>(Arrays.asList(lines));
        }

        @Override
        public String readLine() {
            return lines.poll();
        }

        @Override
        public void write(String s) {
            output.append(s);
        }

        String getOutput() {
            return output.toString();
        }
    }

    @Test
    void runFallsBackToEmptyParamsForUnknownTool() {
        ScriptedTerminal terminal = new ScriptedTerminal();
        ToolWizard.Result result = new ToolWizard(terminal).run("definitely_not_a_real_tool_xyz");

        assertFalse(result.isCancelled());
        assertEquals("definitely_not_a_real_tool_xyz", result.getToolName());
        assertTrue(result.getParams().isEmpty());
        assertTrue(terminal.getOutput().contains("Could not load schema"));
    }

    @Test
    void runCollectsAllConvertibleParameterTypes() {
        // confluence_download_pages: required outputPath (string), urlStrings (array);
        // optional depth (number), downloadAttachments (boolean) — required asked first
        ScriptedTerminal terminal = new ScriptedTerminal("/tmp/pages", "url1, url2 , ,url3", "2.5", "y");
        ToolWizard.Result result = new ToolWizard(terminal).run("confluence_download_pages");

        assertFalse(result.isCancelled());
        assertEquals("confluence_download_pages", result.getToolName());
        Map<String, Object> params = result.getParams();
        assertEquals("/tmp/pages", params.get("outputPath"));
        assertEquals(Arrays.asList("url1", "url2", "url3"), params.get("urlStrings"));
        assertEquals(2.5, params.get("depth"));
        assertEquals(Boolean.TRUE, params.get("downloadAttachments"));

        String output = terminal.getOutput();
        assertTrue(output.contains("Tool: confluence_download_pages"));
        assertTrue(output.contains("Description:"));
        assertTrue(output.contains("(required)"));
        assertTrue(output.contains("(optional, press Enter to skip)"));
        assertTrue(output.contains("Example:"));
        assertTrue(output.contains("[y/n]"));
    }

    @Test
    void runConvertsBooleanFalseAndSkipsEmptyOptionalParam() {
        ScriptedTerminal terminal = new ScriptedTerminal("out", "u1", "", "no");
        ToolWizard.Result result = new ToolWizard(terminal).run("confluence_download_pages");

        assertFalse(result.isCancelled());
        Map<String, Object> params = result.getParams();
        assertFalse(params.containsKey("depth"), "empty optional param must be skipped");
        assertEquals(Boolean.FALSE, params.get("downloadAttachments"));
    }

    @Test
    void runKeepsRawValueWhenNumberParsingFails() {
        ScriptedTerminal terminal = new ScriptedTerminal("out", "u1", "not-a-number", "");
        ToolWizard.Result result = new ToolWizard(terminal).run("confluence_download_pages");

        assertFalse(result.isCancelled());
        assertEquals("not-a-number", result.getParams().get("depth"));
    }

    @Test
    void runReasksWhenRequiredParamLeftEmpty() {
        // github_get_pr: all params required, asked alphabetically:
        // pullRequestId, repository, workspace
        ScriptedTerminal terminal = new ScriptedTerminal("", "42", "myrepo", "myws");
        ToolWizard.Result result = new ToolWizard(terminal).run("github_get_pr");

        assertFalse(result.isCancelled());
        Map<String, Object> params = result.getParams();
        assertEquals("42", params.get("pullRequestId"));
        assertEquals("myrepo", params.get("repository"));
        assertEquals("myws", params.get("workspace"));
        assertTrue(terminal.getOutput().contains("Required. Please provide a value."));
    }

    @Test
    void runHandlesParamsWithoutExampleAndObjectType() {
        // ado_get_comments: id (string) and ticket (object), neither has an example
        ScriptedTerminal terminal = new ScriptedTerminal("123", "T-1");
        ToolWizard.Result result = new ToolWizard(terminal).run("ado_get_comments");

        assertFalse(result.isCancelled());
        Map<String, Object> params = result.getParams();
        assertEquals("123", params.get("id"));
        assertEquals("T-1", params.get("ticket"));
        assertFalse(terminal.getOutput().contains("Example:"));
    }

    @Test
    void runCancelsOnEscape() {
        ScriptedTerminal terminal = new ScriptedTerminal("\u001b");
        ToolWizard.Result result = new ToolWizard(terminal).run("github_get_pr");

        assertTrue(result.isCancelled());
        assertNull(result.getToolName());
        assertNull(result.getParams());
        assertTrue(terminal.getOutput().contains("Cancelled."));
    }

    @Test
    void runCancelsOnEof() {
        ScriptedTerminal terminal = new ScriptedTerminal();
        ToolWizard.Result result = new ToolWizard(terminal).run("github_get_pr");

        assertTrue(result.isCancelled());
        assertTrue(terminal.getOutput().contains("Cancelled."));
    }

    @Test
    void runSucceedsForToolWithoutParameters() {
        ScriptedTerminal terminal = new ScriptedTerminal();
        ToolWizard.Result result = new ToolWizard(terminal).run("github_test");

        assertFalse(result.isCancelled());
        assertEquals("github_test", result.getToolName());
        assertTrue(result.getParams().isEmpty());
        assertTrue(terminal.getOutput().contains("Tool: github_test"));
    }

    @Test
    void resultOkParamsAreUnmodifiableCopy() {
        ScriptedTerminal terminal = new ScriptedTerminal("123", "T-1");
        ToolWizard.Result result = new ToolWizard(terminal).run("ado_get_comments");

        assertThrows(UnsupportedOperationException.class,
                () -> result.getParams().put("extra", "x"));
    }
}
