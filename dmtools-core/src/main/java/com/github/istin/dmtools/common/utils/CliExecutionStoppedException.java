// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

/**
 * Thrown when a CLI execution is intentionally stopped by a caller-provided
 * callback (e.g. a per-line JavaScript action decided to kill the process).
 */
public class CliExecutionStoppedException extends RuntimeException {

    private final String line;

    public CliExecutionStoppedException(String message, String line) {
        super(message);
        this.line = line;
    }

    public String getLine() {
        return line;
    }
}
