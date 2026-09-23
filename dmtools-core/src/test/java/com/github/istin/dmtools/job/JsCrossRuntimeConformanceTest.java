// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-runtime conformance: {@code cross_runtime_conformance.js} is
 * byte-identical (same sha256) with the epam/dmtools-dart fixture
 * ({@code test/fixtures/}) and the IstiN/quickjs_runtime fixture
 * ({@code test/fixtures/}). GraalJS — here, through the real bridge
 * wiring (worker pool + {@code nodeCompat} on the main engine AND the
 * {@code runAsync} worker engines) — and QuickJS must produce the
 * identical result object.
 */
class JsCrossRuntimeConformanceTest {

    @Mock
    private TrackerClient<?> mockTrackerClient;

    @Mock
    private AI mockAI;

    @Mock
    private Confluence mockConfluence;

    @Mock
    private SourceCode mockSourceCode;

    private JobJavaScriptBridge bridge;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        bridge = new JobJavaScriptBridge(mockTrackerClient, mockAI, mockConfluence, mockSourceCode, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static String conformanceScript() throws Exception {
        return Files.readString(
                Path.of("src/test/resources/crossruntime/cross_runtime_conformance.js"));
    }

    private static JSONObject knobsOn() {
        return new JSONObject().put("jobParams",
                new JSONObject().put("parallelWorkers", 2).put("nodeCompat", true));
    }

    /**
     * The contract every runtime must satisfy — kept in lockstep with the
     * expected maps in the dmtools-dart and quickjs_runtime conformance tests.
     */
    private static JSONObject expected() {
        return new JSONObject("""
                {
                  "globalAlias": true,
                  "processType": "object",
                  "envIsObject": true,
                  "cwdIsString": true,
                  "joined": "a/b/c.txt",
                  "baseName": "z.md",
                  "assertOk": true,
                  "formatted": "answer=42",
                  "utf8ByteLen": 10,
                  "utf8RoundTrip": true,
                  "base64": true,
                  "clockIsNumber": true,
                  "uuidShape": true,
                  "randomFilled": true,
                  "cloneDeep": true,
                  "stubGuards": true,
                  "parallel": {
                    "sum": 5050,
                    "workerBase": "parallel.js",
                    "workerUtf8": 4,
                    "allValues": ["first", "second"]
                  }
                }
                """);
    }

    @Test
    void conformanceScriptProducesTheIdenticalCrossRuntimeResult() throws Exception {
        Object result = bridge.executeJavaScript(conformanceScript(), knobsOn());
        JSONObject actual = new JSONObject(result.toString());
        assertTrue(expected().similar(actual),
                () -> "cross-runtime mismatch, actual: " + actual);
    }

    @Test
    void knobsOffTheSameScriptFails() {
        assertThrows(Exception.class,
                () -> bridge.executeJavaScript(conformanceScript(), new JSONObject()));
    }
}
