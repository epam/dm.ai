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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code runAsync(fn, args)} — the engine-worker pool over the
 * GraalJS bridge (dmtools-dart#224). Mirrors the Dart test suite in
 * dmtools-dart {@code test/js/async_job_pool_test.dart}: same scenarios,
 * same contracts, cross-runtime parity.
 */
class RunAsyncJobTest {

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

    private static JSONObject parallelWorkers(int workers) {
        return new JSONObject().put("jobParams", new JSONObject().put("parallelWorkers", workers));
    }

    @Test
    void runAsyncReturnsAJobWhoseWaitYieldsTheFunctionResult() throws Exception {
        String js = """
                function action(params) {
                    var job = runAsync(function(args) {
                        return { sum: args.a + args.b };
                    }, { a: 2, b: 3 });
                    return { r: job.wait() };
                }
                """;
        Object result = bridge.executeJavaScript(js, parallelWorkers(2));
        JSONObject decoded = new JSONObject(result.toString());
        assertEquals(5, decoded.getJSONObject("r").getInt("sum"));
    }

    @Test
    void twoJobsRunInParallelWallTimeAboutOneJobNotTheSum() throws Exception {
        String js = """
                function action(params) {
                    var started = Date.now();
                    var fn = function(args) {
                        var end = Date.now() + args.ms;
                        while (Date.now() < end) { /* busy wait */ }
                        return args.ms;
                    };
                    var total = runAsync.all([
                        runAsync(fn, { ms: 400 }),
                        runAsync(fn, { ms: 400 })
                    ]).wait();
                    return { elapsed: Date.now() - started, first: total[0], second: total[1] };
                }
                """;
        Object result = bridge.executeJavaScript(js, parallelWorkers(2));
        JSONObject decoded = new JSONObject(result.toString());
        int elapsed = decoded.getInt("elapsed");
        // Sequential execution would need >= 800ms of busy wait alone.
        assertTrue(elapsed < 780, "jobs must overlap, elapsed=" + elapsed);
        assertEquals(400, decoded.getInt("first"));
        assertEquals(400, decoded.getInt("second"));
    }

    @Test
    void workerJsExceptionsPropagateThroughWait() throws Exception {
        String js = """
                function action(params) {
                    try {
                        runAsync(function() { throw new Error('boom-in-worker'); }, null).wait();
                        return 'no-error';
                    } catch (e) {
                        return String(e);
                    }
                }
                """;
        Object result = bridge.executeJavaScript(js, parallelWorkers(2));
        assertTrue(result.toString().contains("boom-in-worker"), "actual: " + result);
    }

    @Test
    void workerEnginesSeeParamsAndTheFullToolBridge() throws Exception {
        String js = """
                function action(params) {
                    return runAsync(function(args) {
                        return {
                            marker: params.jobParams.marker,
                            doubled: args.n * 2
                        };
                    }, { n: 21 }).wait();
                }
                """;
        JSONObject params = parallelWorkers(2).getJSONObject("jobParams").put("marker", "async-marker");
        Object result = bridge.executeJavaScript(js, new JSONObject().put("jobParams", params));
        JSONObject decoded = new JSONObject(result.toString());
        assertEquals("async-marker", decoded.getString("marker"));
        assertEquals(42, decoded.getInt("doubled"));
    }

    @Test
    void requireInsideAWorkerResolvesAgainstTheScriptDirectory() throws Exception {
        Path dir = Files.createTempDirectory("dmtools_runasync");
        try {
            Files.writeString(Path.of(dir.toString(), "dep.js"),
                    "module.exports = { double: function(n) { return n * 2; } };");
            Path main = Path.of(dir.toString(), "main.js");
            Files.writeString(main, """
                    function action(params) {
                        var job = runAsync(function(args) {
                            var dep = require('./dep.js');
                            return dep.double(args.n);
                        }, { n: 21 });
                        return job.wait();
                    }
                    """);
            Object result = bridge.executeJavaScript(main.toString(), parallelWorkers(2));
            // the bridge converts JS numbers to Java doubles (pre-existing trait)
            assertEquals(42.0, Double.parseDouble(result.toString()));
        } finally {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void runAsyncAllWaitsEveryJobAndReturnsOrderedResults() throws Exception {
        String js = """
                function action(params) {
                    var fn = function(args) { return args.n * 10; };
                    return runAsync.all([
                        runAsync(fn, { n: 1 }),
                        runAsync(fn, { n: 2 }),
                        runAsync(fn, { n: 3 })
                    ]).wait();
                }
                """;
        Object result = bridge.executeJavaScript(js, parallelWorkers(2));
        org.json.JSONArray array = new org.json.JSONArray(result.toString());
        assertEquals(10, array.getInt(0));
        assertEquals(20, array.getInt(1));
        assertEquals(30, array.getInt(2));
    }

    @Test
    void runAsyncValidatesItsArguments() throws Exception {
        String js = """
                function action(params) {
                    try { runAsync(42, null); return 'no-error'; } catch (e) {
                        return String(e);
                    }
                }
                """;
        Object result = bridge.executeJavaScript(js, parallelWorkers(2));
        assertTrue(result.toString().contains("runAsync expects a function"), "actual: " + result);
    }

    @Test
    void parallelWorkersKnobOffKeepsTheDefaultSequentialSurface() throws Exception {
        String js = "function action(params) { return typeof runAsync; }";
        Object result = bridge.executeJavaScript(js, new JSONObject());
        assertEquals("undefined", result.toString());
    }

    @Test
    void actionErrorHandlingIsUntouchedByTheAsyncWiring() {
        String js = "function action(params) { throw new Error('action-failed'); }";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript(js, parallelWorkers(2)));
        assertTrue(thrown.getMessage().contains("action-failed"), "actual: " + thrown.getMessage());
    }

    @Test
    void workerPoolIsolatesStateBetweenJobs() throws Exception {
        String moduleScript = """
                function action(params) {
                    var job = runAsync(function() {
                        if (typeof globalThis.__counter === 'undefined') { globalThis.__counter = 0; }
                        globalThis.__counter += 1;
                        return globalThis.__counter;
                    }, null);
                    return job.wait();
                }
                """;
        Object first = bridge.executeJavaScript(moduleScript, parallelWorkers(2));
        assertEquals(1.0, ((Number) first).doubleValue());
        Object second = bridge.executeJavaScript(moduleScript, parallelWorkers(2));
        assertEquals(1.0, ((Number) second).doubleValue());
    }
}
