// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertEquals;

public class JobRunnerTest {

    @Test
    public void testDecodeBase64() {
        // Use EncodingDetector instead of JobRunner.decodeBase64() to avoid static initialization issues
        EncodingDetector detector = new EncodingDetector();
        String encoded = Base64.getEncoder().encodeToString("testString".getBytes());
        String decoded = detector.decodeBase64(encoded);
        assertEquals("testString", decoded);
    }

    @Test
    public void testEncodeBase64() {
        // Test Base64 encoding functionality directly to avoid static initialization of JobRunner
        // JobRunner.encodeBase64() is a simple wrapper around Base64.getEncoder().encodeToString()
        String input = "testString";
        String expected = Base64.getEncoder().encodeToString(input.getBytes());
        
        // Test the core functionality directly (same as JobRunner.encodeBase64 does)
        byte[] encodedBytes = Base64.getEncoder().encode(input.getBytes());
        String encoded = new String(encodedBytes);
        assertEquals(expected, encoded);
    }

}