// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import com.github.istin.dmtools.mcp.cli.OutputFormat;

/**
 * Lets the user pick an output format before a tool is executed.
 *
 * <p>The selector prints the list of supported formats and accepts either a
 * number or the format name.  Esc cancels the session.</p>
 */
public class OutputFormatSelector {

    private final Terminal terminal;

    public OutputFormatSelector(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Prompts for a format and returns the chosen format, or {@code null} if cancelled.
     */
    public OutputFormat select() {
        OutputFormat[] formats = OutputFormat.values();
        StringBuilder prompt = new StringBuilder();
        prompt.append("\nSelect output format:\n");
        for (int i = 0; i < formats.length; i++) {
            prompt.append("  ").append(i + 1).append(". ").append(formats[i].getId()).append("\n");
        }
        prompt.append("> ");
        terminal.write(prompt.toString());

        String line = terminal.readLine();
        if (line == null || line.startsWith("\u001b")) {
            return null;
        }
        line = line.trim();
        if (line.isEmpty()) {
            return OutputFormat.JSON;
        }
        try {
            int choice = Integer.parseInt(line);
            if (choice >= 1 && choice <= formats.length) {
                return formats[choice - 1];
            }
        } catch (NumberFormatException e) {
            // fall through to name matching
        }
        OutputFormat matched = OutputFormat.fromString(line);
        if (matched == OutputFormat.JSON && !"json".equalsIgnoreCase(line)) {
            terminal.write("Unknown format '" + line + "', using json.\n");
        }
        return matched;
    }
}
