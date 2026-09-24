// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.graalvm.polyglot.Context;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-runtime conformance: {@code cross_runtime_conformance.js} is
 * byte-identical (same sha256 {@code 6aaee5e6748f4bdc…}) with the
 * epam/dmtools-dart fixture ({@code test/fixtures/}) and the
 * IstiN/quickjs_runtime fixture ({@code test/fixtures/}). GraalJS —
 * here, through the real bridge wiring (worker pool + {@code nodeCompat}
 * on the main engine AND the {@code runAsync} worker engines) — and
 * QuickJS must produce the identical result object.
 *
 * <p>Protocol (identical on every runtime — see the fixture header): the
 * bridge calls {@code action(params)} (sync surface + {@code runAsync}
 * parallel execution; GraalJS drains promise reactions at the end of
 * each eval); the timers family ({@code actionTimers} + one
 * {@code drainTimers} pass) is driven through a direct-context install
 * below.</p>
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
        // canned transport for the fixture's conformance://ping fetch
        bridge.setCompatHttpTransport(JsCrossRuntimeConformanceTest::cannedFetch);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    /**
     * The canned transport every runtime's harness installs for the
     * fixture's {@code conformance://ping} fetch call (JSON in, JSON out —
     * the same contract as the production transport).
     */
    static String cannedFetch(String requestJson) {
        JSONObject request = new JSONObject(requestJson);
        if ("conformance://ping".equals(request.optString("url"))) {
            return new JSONObject()
                    .put("status", 200)
                    .put("headers", new JSONObject().put("x", "y"))
                    .put("body", "pong")
                    .toString();
        }
        return new JSONObject()
                .put("status", 404)
                .put("headers", new JSONObject())
                .put("body", "no conformance route")
                .toString();
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
     * The expected {@code action(params)} result — kept in lockstep with
     * the expected maps in the dmtools-dart and quickjs_runtime
     * conformance tests.
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
                  "buffer": {
                    "typeofFn": true,
                    "isUint8Array": true,
                    "b64": "aGVsbG8=",
                    "hexRoundTrip": true,
                    "latin1Hex": "68ff",
                    "utf8ByteLen": 12,
                    "le": 1,
                    "be": 9,
                    "slice": "bc",
                    "copyRoundTrip": true,
                    "isBufferTrue": true,
                    "isBufferFalse": true
                  },
                  "url": {
                    "href": "https://example.com/a/b?q=1&x=%20#frag",
                    "origin": "https://example.com",
                    "pathname": "/a/b",
                    "search": "?q=1&x=%20",
                    "hash": "#frag",
                    "q": "1",
                    "xDecoded": " ",
                    "getAllA": "1|2",
                    "bDecoded": "x y",
                    "appendForm": "k=a+b",
                    "canParse": true
                  },
                  "utilExtras": {
                    "inspectString": "'hi'",
                    "inspectNumber": "42",
                    "isArray": true,
                    "isString": true,
                    "hasTime": true
                  },
                  "osProcess": {
                    "osEolType": "string",
                    "osPlatformType": "string",
                    "osArchType": "string",
                    "osHomedirType": "string",
                    "nextTickType": "function",
                    "hrtimeType": "function",
                    "argvIsArray": true,
                    "pidIsNumber": true,
                    "exitIsFunction": true
                  },
                  "intl": {
                    "numberFormat": true,
                    "dateTimeFormat": true,
                    "canonicalLocales": true
                  },
                  "events": {
                    "got": "t1,o",
                    "emitReturnNoListener": true,
                    "hasOff": true,
                    "listenerCount": 1
                  },
                  "fetchShapes": {
                    "fetchTypeof": "function",
                    "headerGet": "b",
                    "headerHas": true,
                    "responseType": "function",
                    "callStatus": 200,
                    "callHeader": "y",
                    "callBody": true
                  },
                  "stubGuards": true,
                  "parallel": {
                    "sum": 5050,
                    "workerBase": "parallel.js",
                    "workerUtf8": 4,
                    "workerBuffer": "b2s=",
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

    /**
     * Timers protocol (fixture steps 3-5): one ready drain pass runs
     * immediates before due timeouts; the microtask fired at the end of
     * the actionTimers eval (GraalJS job queue).
     */
    @Test
    void timersProtocolThroughTheDrainHandle() {
        Context ctx = Context.newBuilder("js").allowAllAccess(false).build();
        try {
            JsNodeCompat.Config config = JsNodeCompat.Config.cliDefaults();
            config.httpFetch = JsCrossRuntimeConformanceTest::cannedFetch;
            JsNodeCompat.Handle handle = JsNodeCompat.install(ctx, config);
            ctx.getBindings("js").putMember("__ncScriptPathRaw", "null");
            ctx.eval("js", Files.readString(
                    Path.of("src/test/resources/crossruntime/cross_runtime_conformance.js")));
            ctx.eval("js", "globalThis.params = { jobParams: { nodeCompat: true } };");
            ctx.eval("js", "actionTimers(globalThis.params)");
            handle.drainTimers();
            JSONObject out = new JSONObject(
                    ctx.eval("js", "JSON.stringify(globalThis.__timersOut)").asString());
            JSONObject expectedTimers = new JSONObject().put("log",
                    new org.json.JSONArray(java.util.List.of("sync", "p1", "imm", "t0", "iv")));
            assertTrue(expectedTimers.similar(out),
                    () -> "timers protocol mismatch, actual: " + out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            ctx.close();
        }
    }

    @Test
    void knobsOffTheSameScriptFails() {
        assertThrows(Exception.class,
                () -> bridge.executeJavaScript(conformanceScript(), new JSONObject()));
    }
}
