// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCommandFailedExceptionTest {

    @Test
    void getMessage_excludesRawOutput() {
        CliCommandFailedException e = new CliCommandFailedException("claude --model invalid-model", 1,
                "API Error: 400 ValidationException: invalid model identifier");

        assertEquals("Command failed (exit code 1): claude --model invalid-model", e.getMessage());
        assertFalse(e.getMessage().contains("ValidationException"),
                "getMessage() must stay short so every catch site that logs/propagates it doesn't reprint the captured output");
    }

    @Test
    void getDiagnosticMessage_includesFullRawOutput() {
        String output = "API Error: 400 ValidationException: invalid model identifier";
        CliCommandFailedException e = new CliCommandFailedException("claude --model invalid-model", 1, output);

        String diagnostic = e.getDiagnosticMessage();
        assertTrue(diagnostic.contains(e.getMessage()));
        assertTrue(diagnostic.contains(output));
    }

    @Test
    void getTruncatedDiagnosticMessage_keepsShortOutputIntact() {
        String output = "API Error: 400 ValidationException: invalid model identifier";
        CliCommandFailedException e = new CliCommandFailedException("claude --model invalid-model", 1, output);

        String truncated = e.getTruncatedDiagnosticMessage();
        assertTrue(truncated.contains("ValidationException"));
        assertFalse(truncated.contains("truncated"));
    }

    @Test
    void getTruncatedDiagnosticMessage_capsLongOutputToTail() {
        String noise = "x".repeat(5000);
        String signature = "ValidationException: invalid model identifier";
        CliCommandFailedException e = new CliCommandFailedException("claude --model invalid-model", 1, noise + signature);

        String truncated = e.getTruncatedDiagnosticMessage(1000);
        assertTrue(truncated.contains(signature), "tail must retain the actual error signature");
        assertTrue(truncated.contains("truncated"));
        assertTrue(truncated.length() < noise.length() + signature.length());
    }

    @Test
    void getTruncatedDiagnosticMessage_nullOutput_returnsPlainMessage() {
        CliCommandFailedException e = new CliCommandFailedException("some-tool", 2, null);

        assertEquals(e.getMessage(), e.getTruncatedDiagnosticMessage());
    }
}
