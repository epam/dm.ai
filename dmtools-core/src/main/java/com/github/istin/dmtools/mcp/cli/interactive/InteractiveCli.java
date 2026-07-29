// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import com.github.istin.dmtools.mcp.cli.OutputFormat;
import com.github.istin.dmtools.mcp.cli.interactive.CommandItem.Kind;
import com.github.istin.dmtools.mcp.cli.interactive.ToolWizard.Result;
import org.json.JSONObject;

import java.util.List;

/**
 * Entry point for the Java-based interactive CLI.
 *
 * <p>Launches a command picker; if the user selects an MCP tool, a parameter
 * wizard is shown, then an output format is chosen, and finally the tool is
 * executed and the result printed.</p>
 *
 * <p>For runnable files and built-in jobs the selected command is printed to
 * stdout so that the shell wrapper can {@code exec} it.</p>
 */
public class InteractiveCli {

    private final Terminal terminal;
    private final CommandSource commandSource;

    public InteractiveCli(Terminal terminal, CommandSource commandSource) {
        this.terminal = terminal;
        this.commandSource = commandSource;
    }

    /**
     * Runs the interactive CLI.  Returns the shell command to execute, or an empty
     * string if the user cancelled.
     */
    public String run() {
        try {
            terminal.write("Loading commands...\n");
            List<CommandItem> commands = commandSource.loadCommands();
            if (commands.isEmpty()) {
                terminal.write("No commands available.\n");
                return "";
            }

            int[] size = terminalSize();
            CommandPicker picker = new CommandPicker(terminal, commands, size[0], size[1]);
            CommandItem chosen = picker.pick();
            if (chosen == null) {
                return "";
            }

            if (chosen.getKind() != Kind.MCP) {
                return chosen.toShellCommand();
            }

            ToolWizard wizard = new ToolWizard(terminal);
            Result result = wizard.run(chosen.getName());
            if (result.isCancelled()) {
                return "";
            }

            OutputFormatSelector formatSelector = new OutputFormatSelector(terminal);
            OutputFormat format = formatSelector.select();
            if (format == null) {
                terminal.write("Cancelled.\n");
                return "";
            }

            String dataJson = result.getParams().isEmpty() ? "" : new JSONObject(result.getParams()).toString();
            StringBuilder fullCmd = new StringBuilder();
            fullCmd.append("dmtools ").append(result.getToolName());
            if (!dataJson.isEmpty()) {
                fullCmd.append(" --data '").append(dataJson).append("'");
            }
            if (format != OutputFormat.JSON) {
                fullCmd.append(" --output ").append(format.getId());
            }
            terminal.write("\nExecuting: " + fullCmd + "\n");

            // Return the command to the wrapper so it can exec it.  The format matches
            // the previous Python implementation: tool_name\tparams\toutput_format.
            return result.getToolName() + "\t" + dataJson + "\t" + format.getId();
        } catch (Exception e) {
            terminal.write("Interactive mode failed: " + e.getMessage() + "\n");
            return "";
        } finally {
            terminal.close();
        }
    }

    private int[] terminalSize() {
        try {
            ProcessBuilder pb = new ProcessBuilder("stty", "size");
            pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/tty")));
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            Process process = pb.start();
            String output;
            try (java.io.InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            process.waitFor();
            String[] parts = output.split("\\s+");
            if (parts.length == 2) {
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            }
        } catch (Exception e) {
            // ignore
        }
        return new int[]{24, 80};
    }
}
