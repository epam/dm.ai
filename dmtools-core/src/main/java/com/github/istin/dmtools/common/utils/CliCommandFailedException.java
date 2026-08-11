// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import java.io.IOException;

/**
 * Thrown by {@link CommandLineUtils#runCommand} when the underlying subprocess exits
 * with a non-zero exit code. Extends {@link IOException} so existing callers that only
 * catch {@code IOException}/{@code Exception} continue to behave exactly as before;
 * callers that need the structured exit code / raw output can catch this specific type.
 */
public class CliCommandFailedException extends IOException {

    private final String command;
    private final int exitCode;
    private final String output;

    public CliCommandFailedException(String command, int exitCode, String output) {
        super("Command failed (exit code " + exitCode + "): " + command + "\nOutput:\n" + output.trim());
        this.command = command;
        this.exitCode = exitCode;
        this.output = output;
    }

    public String getCommand() {
        return command;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }
}
