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

    /** Default cap for {@link #getTruncatedDiagnosticMessage()} — enough to keep error
     * signatures (e.g. an API validation message) without embedding a huge blob in
     * structured, JS-facing error fields. */
    public static final int DEFAULT_TRUNCATED_OUTPUT_CHARS = 1000;

    private final String command;
    private final int exitCode;
    private final String output;

    public CliCommandFailedException(String command, int exitCode, String output) {
        // Message intentionally excludes the raw output (use getOutput()) so every catch site
        // that logs/propagates e.getMessage() doesn't reprint the full captured CLI output.
        super("Command failed (exit code " + exitCode + "): " + command);
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

    /**
     * Single source of truth for "short message + full raw output", so callers that need to
     * surface everything (e.g. a one-off log line) don't each hand-roll their own concatenation.
     */
    public String getDiagnosticMessage() {
        return getMessage() + "\nOutput:\n" + (output != null ? output.trim() : "");
    }

    /**
     * Short message plus at most the last {@code maxOutputChars} characters of the captured
     * output — enough to preserve error signatures for structured error fields (e.g.
     * {@code currentCliErrorMessage}) without embedding a potentially huge blob.
     */
    public String getTruncatedDiagnosticMessage(int maxOutputChars) {
        if (output == null || output.isEmpty()) {
            return getMessage();
        }
        String trimmed = output.trim();
        String tail = trimmed.length() > maxOutputChars
                ? "...[truncated]...\n" + trimmed.substring(trimmed.length() - maxOutputChars)
                : trimmed;
        return getMessage() + "\nOutput:\n" + tail;
    }

    public String getTruncatedDiagnosticMessage() {
        return getTruncatedDiagnosticMessage(DEFAULT_TRUNCATED_OUTPUT_CHARS);
    }
}
