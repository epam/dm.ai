// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import com.github.istin.dmtools.mcp.cli.OutputFormat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveCliComponentsTest {

    @Test
    void commandItemSearchableTextNormalisesSpacesToUnderscores() {
        CommandItem item = new CommandItem(CommandItem.Kind.MCP, "jira", "jira get ticket", "desc");
        assertTrue(item.getSearchableText().contains("jira_get_ticket"));
    }

    @Test
    void commandItemShellCommandForMcpIsJustName() {
        CommandItem item = new CommandItem(CommandItem.Kind.MCP, "jira", "jira_get_ticket", "desc");
        assertEquals("jira_get_ticket", item.toShellCommand());
    }

    @Test
    void commandItemShellCommandForRunnableIsPrefixedWithRun() {
        CommandItem item = new CommandItem(CommandItem.Kind.JS, "js", "scripts/foo.js", "desc");
        assertEquals("run scripts/foo.js", item.toShellCommand());
    }

    @Test
    void outputFormatSelectorPicksFormatByNumber() {
        TestTerminal terminal = new TestTerminal("2\n");
        OutputFormatSelector selector = new OutputFormatSelector(terminal);
        assertEquals(OutputFormat.TOON, selector.select());
    }

    @Test
    void outputFormatSelectorPicksFormatByName() {
        TestTerminal terminal = new TestTerminal("table\n");
        OutputFormatSelector selector = new OutputFormatSelector(terminal);
        assertEquals(OutputFormat.TABLE, selector.select());
    }

    @Test
    void outputFormatSelectorReturnsJsonForEmptyInput() {
        TestTerminal terminal = new TestTerminal("\n");
        OutputFormatSelector selector = new OutputFormatSelector(terminal);
        assertEquals(OutputFormat.JSON, selector.select());
    }

    @Test
    void outputFormatSelectorReturnsNullOnEsc() {
        TestTerminal terminal = new TestTerminal("\u001b");
        OutputFormatSelector selector = new OutputFormatSelector(terminal);
        assertNull(selector.select());
    }

    @Test
    void commandPickerFiltersOnQueryAndReturnsSelectedItem() {
        List<CommandItem> commands = Arrays.asList(
                new CommandItem(CommandItem.Kind.MCP, "jira", "jira_get_ticket", "Get a ticket"),
                new CommandItem(CommandItem.Kind.MCP, "ado", "ado_get_work_item", "Get a work item"),
                new CommandItem(CommandItem.Kind.JOB, "job", "JSRunner", "Run JS")
        );

        // Type "jira_get" then press Enter
        TestTerminal terminal = new TestTerminal("j", "i", "r", "a", "_", "g", "e", "t", "\r");
        CommandPicker picker = new CommandPicker(terminal, commands, 24, 80);
        CommandItem selected = picker.pick();

        assertNotNull(selected);
        assertEquals("jira_get_ticket", selected.getName());
        assertTrue(terminal.getOutput().contains("> jira_get"));
    }

    @Test
    void commandPickerCancelsOnEsc() {
        List<CommandItem> commands = Arrays.asList(
                new CommandItem(CommandItem.Kind.MCP, "jira", "jira_get_ticket", "Get a ticket")
        );
        TestTerminal terminal = new TestTerminal("\u001b");
        CommandPicker picker = new CommandPicker(terminal, commands, 24, 80);
        assertNull(picker.pick());
    }

    /**
     * Minimal terminal implementation for unit tests.  It feeds characters from
     * a queue and records all written output as a string.
     */
    private static final class TestTerminal extends Terminal {
        private final List<Integer> inputQueue;
        private final AtomicInteger readIndex = new AtomicInteger(0);
        private final StringBuilder output = new StringBuilder();

        TestTerminal(String... inputs) {
            super(null, null, false);
            inputQueue = new ArrayList<>();
            for (String s : inputs) {
                for (char c : s.toCharArray()) {
                    inputQueue.add((int) c);
                }
            }
        }

        @Override
        public int read() {
            int idx = readIndex.getAndIncrement();
            if (idx >= inputQueue.size()) {
                return -1;
            }
            return inputQueue.get(idx);
        }

        @Override
        public String readEscapeSequence(int timeoutMs) {
            StringBuilder seq = new StringBuilder();
            seq.append((char) 0x1b);
            int idx = readIndex.getAndIncrement();
            if (idx < inputQueue.size()) {
                seq.append((char) (int) inputQueue.get(idx));
            }
            return seq.toString();
        }

        @Override
        public String readLine() {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int code = read();
                if (code < 0 || code == 10 || code == 13) {
                    break;
                }
                if (code == 27) {
                    return "\u001b";
                }
                sb.append((char) code);
            }
            return sb.toString();
        }

        @Override
        public void write(String s) {
            output.append(s);
        }

        @Override
        public void clearScreen() {
            output.append("[CLEAR]");
        }

        @Override
        public void moveCursor(int row, int col) {
            output.append(String.format("[MOVE %d,%d]", row, col));
        }

        @Override
        public void clearLine() {
            output.append("[CLRLINE]");
        }

        String getOutput() {
            return output.toString();
        }
    }
}
