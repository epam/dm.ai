// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.graalvm.polyglot.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsNodeCompat} — the opt-in node/js compat layer over the
 * GraalJS bridge. Mirrors the Dart test suite in quickjs_runtime
 * {@code test/node_compat_test.dart}: same scenarios, same error texts,
 * cross-runtime parity.
 */
class JsNodeCompatTest {

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

    private static JSONObject params(boolean nodeCompat, int parallelWorkers) {
        JSONObject jobParams = new JSONObject();
        if (nodeCompat) {
            jobParams.put("nodeCompat", true);
        }
        if (parallelWorkers >= 2) {
            jobParams.put("parallelWorkers", parallelWorkers);
        }
        return new JSONObject().put("jobParams", jobParams);
    }

    private Object runScript(String js, JSONObject parameters) throws Exception {
        return bridge.executeJavaScript(js, parameters);
    }

    private JSONObject runObject(String js, JSONObject parameters) throws Exception {
        return new JSONObject(runScript(js, parameters).toString());
    }

    @Test
    void bareSurfaceStaysCleanWithoutTheKnob() throws Exception {
        String js = """
                function action(params) {
                    return {
                        process: typeof process,
                        path: typeof path,
                        Buffer: typeof Buffer,
                        require: typeof require
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(false, 0));
        assertEquals("undefined", decoded.getString("process"));
        assertEquals("undefined", decoded.getString("path"));
        assertEquals("undefined", decoded.getString("Buffer"));
        // the loader's require is still there
        assertEquals("function", decoded.getString("require"));
    }

    @Test
    void knobEnablesTheCompatSurface() throws Exception {
        String js = """
                function action(params) {
                    return {
                        process: typeof process,
                        path: typeof path,
                        assert: typeof assert,
                        util: typeof util,
                        console: typeof console,
                        TextEncoder: typeof TextEncoder,
                        globalAlias: global === globalThis,
                        Buffer: typeof Buffer
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals("object", decoded.getString("process"));
        assertEquals("object", decoded.getString("path"));
        assertEquals("function", decoded.getString("assert"));
        assertEquals("object", decoded.getString("util"));
        assertEquals("object", decoded.getString("console"));
        assertEquals("function", decoded.getString("TextEncoder"));
        assertTrue(decoded.getBoolean("globalAlias"));
        // tier-2 stubs are typeof-safe
        assertEquals("function", decoded.getString("Buffer"));
    }

    @Test
    void processExposesEnvPlatformArchVersionCwdExitCode() throws Exception {
        String js = """
                function action(params) {
                    return {
                        platform: process.platform,
                        arch: process.arch,
                        version: process.version,
                        exitCode: process.exitCode,
                        cwdIsAbsolute: process.cwd().charAt(0) === '/',
                        envHasHome: typeof process.env.HOME === 'string' ||
                                    typeof process.env.USERPROFILE === 'string'
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertTrue(List.of("linux", "darwin", "win32").contains(decoded.getString("platform")));
        assertFalse(decoded.getString("arch").isEmpty());
        assertTrue(decoded.getString("version").contains("compat"));
        assertEquals(0, decoded.getInt("exitCode"));
        assertTrue(decoded.getBoolean("cwdIsAbsolute"));
        assertTrue(decoded.getBoolean("envHasHome"));
    }

    @Test
    void consoleRoutesToTheSink() {
        List<String> logs = new ArrayList<>();
        Context ctx = Context.newBuilder("js").allowAllAccess(false).build();
        try {
            JsNodeCompat.Config config = new JsNodeCompat.Config();
            config.consoleSink = (level, message) -> logs.add(level + ": " + message);
            JsNodeCompat.install(ctx, config);
            ctx.eval("js", "console.log('hello', 42, {a: 1}); console.warn('careful');");
            assertEquals(List.of(
                    "log: hello 42 { a: 1 }",
                    "warn: careful"
            ), logs);
        } finally {
            ctx.close();
        }
    }

    @Test
    void processExitThrowsAndNotifiesTheHook() {
        List<Integer> exitCodes = new ArrayList<>();
        Context ctx = Context.newBuilder("js").allowAllAccess(false).build();
        try {
            JsNodeCompat.Config config = new JsNodeCompat.Config();
            config.exitHook = exitCodes::add;
            JsNodeCompat.install(ctx, config);
            var exception = assertThrowsPolyglot(ctx, "process.exit(7)");
            assertTrue(exception.contains("ProcessExit: 7"), "actual: " + exception);
            assertEquals(List.of(7), exitCodes);
        } finally {
            ctx.close();
        }
    }

    @Test
    void pathPosixSubset() throws Exception {
        String js = """
                function action(params) {
                    return {
                        join: path.join('a', 'b', 'c.js'),
                        joinDots: path.join('a/', './b/../c'),
                        resolveAbs: path.resolve('x', '/abs', 'y'),
                        basename: path.basename('/a/b/c.tar.gz'),
                        basenameExt: path.basename('c.js', '.js'),
                        dirname: path.dirname('/a/b/c'),
                        extname: path.extname('file.tar.gz'),
                        isAbsolute: path.isAbsolute('/x'),
                        relative: path.relative('/a/b', '/a/c/d'),
                        sep: path.sep
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals("a/b/c.js", decoded.getString("join"));
        assertEquals("a/c", decoded.getString("joinDots"));
        assertEquals("/abs/y", decoded.getString("resolveAbs"));
        assertEquals("c.tar.gz", decoded.getString("basename"));
        assertEquals("c", decoded.getString("basenameExt"));
        assertEquals("/a/b", decoded.getString("dirname"));
        assertEquals(".gz", decoded.getString("extname"));
        assertTrue(decoded.getBoolean("isAbsolute"));
        assertEquals("../c/d", decoded.getString("relative"));
        assertEquals("/", decoded.getString("sep"));
    }

    @Test
    void assertSubset() throws Exception {
        String ok = """
                function action(params) {
                    assert(1 === 1);
                    assert.equal('a', 'a');
                    assert.deepEqual({x: [1]}, {x: [1]});
                    assert.ok(true);
                    assert.throws(function () { throw new Error('boom'); }, /boom/);
                    return 'ok';
                }
                """;
        assertEquals("ok", runScript(ok, params(true, 0)).toString());

        String failure = """
                function action(params) {
                    try {
                        assert.equal(1, 2);
                        return 'no-error';
                    } catch (e) {
                        return { name: e.name, message: String(e) };
                    }
                }
                """;
        JSONObject decoded = runObject(failure, params(true, 0));
        assertEquals("AssertionError", decoded.getString("name"));
        assertTrue(decoded.getString("message").contains("=="));
    }

    @Test
    void utilFormatAndInspect() throws Exception {
        String js = """
                function action(params) {
                    return {
                        format: util.format('%s=%d %j', 'a', 5, {b: 2}),
                        inspect: util.inspect({k: 1})
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals("a=5 {\"b\":2}", decoded.getString("format"));
        assertEquals("{ k: 1 }", decoded.getString("inspect"));
    }

    @Test
    void btoaAtobRoundtrip() throws Exception {
        String js = """
                function action(params) {
                    return {
                        encoded: btoa('hello'),
                        decoded: atob('aGVsbG8='),
                        roundtrip: atob(btoa('round trip'))
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals("aGVsbG8=", decoded.getString("encoded"));
        assertEquals("hello", decoded.getString("decoded"));
        assertEquals("round trip", decoded.getString("roundtrip"));
    }

    @Test
    void textEncoderDecoderRoundtrip() throws Exception {
        String js = """
                function action(params) {
                    var enc = new TextEncoder();
                    var bytes = enc.encode('hi');
                    var emoji = enc.encode('\\uD83D\\uDC4D');
                    return {
                        len: bytes.length,
                        first: bytes[0],
                        decoded: new TextDecoder().decode(bytes),
                        emojiLen: emoji.length,
                        emojiBack: new TextDecoder().decode(emoji)
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals(2, decoded.getInt("len"));
        assertEquals(104, decoded.getInt("first"));
        assertEquals("hi", decoded.getString("decoded"));
        assertEquals(4, decoded.getInt("emojiLen"));
        assertEquals("\uD83D\uDC4D", decoded.getString("emojiBack"));
    }

    @Test
    void cryptoUuidShapeAndGetRandomValues() throws Exception {
        String js = """
                function action(params) {
                    var a = new Uint8Array(4);
                    crypto.getRandomValues(a);
                    var inRange = true;
                    for (var i = 0; i < a.length; i++) {
                        if (a[i] < 0 || a[i] > 255) inRange = false;
                    }
                    return {
                        uuid: crypto.randomUUID(),
                        filled: a.length,
                        inRange: inRange
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertTrue(decoded.getString("uuid").matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertEquals(4, decoded.getInt("filled"));
        assertTrue(decoded.getBoolean("inRange"));
    }

    @Test
    void performanceNowIsANumber() throws Exception {
        String js = """
                function action(params) {
                    var t = performance.now();
                    return { type: typeof t, nonNegative: t >= 0 };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals("number", decoded.getString("type"));
        assertTrue(decoded.getBoolean("nonNegative"));
    }

    @Test
    void structuredCloneIsJsonFidelity() throws Exception {
        String js = """
                function action(params) {
                    var v = {a: [1, {b: 'x'}]};
                    var c = structuredClone(v);
                    c.a[1].b = 'y';
                    return { original: v.a[1].b, clone: c.a[1].b };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertEquals("x", decoded.getString("original"));
        assertEquals("y", decoded.getString("clone"));
    }

    @Test
    void requireBuiltinsAndLoaderFallback(@TempDir Path dir) throws Exception {
        Path module = dir.resolve("dep.js");
        Files.writeString(module, "module.exports = { mark: 'from-loader' };");
        Path main = dir.resolve("main.js");
        Files.writeString(main, """
                function action(params) {
                    return {
                        builtin: require('path').sep,
                        fromLoader: require('./dep.js').mark
                    };
                }
                """);
        JSONObject decoded = new JSONObject(
                bridge.executeJavaScript(main.toString(), params(true, 0)).toString());
        assertEquals("/", decoded.getString("builtin"));
        assertEquals("from-loader", decoded.getString("fromLoader"));
    }

    @Test
    void installModuleRegistersConsumerModule() {
        Context ctx = Context.newBuilder("js").allowAllAccess(false).build();
        try {
            JsNodeCompat.install(ctx);
            JsNodeCompat.installModule(ctx, "fs",
                    name -> "host-file-tools:" + name);
            String exports = ctx.eval("js", "require('fs')").asString();
            assertEquals("host-file-tools:fs", exports);
        } finally {
            ctx.close();
        }
    }

    @Test
    void stubsAreTypeofSafeButThrowOnCall() {
        // Library-default config: no fetch transport, ready drain —
        // fetch/AbortController stay typeof-safe stubs while Buffer and
        // the timers are real.
        Context ctx = Context.newBuilder("js").allowAllAccess(false).build();
        try {
            JsNodeCompat.install(ctx);
            ctx.eval("js", """
                    globalThis.__probe = {
                        typeofs: [typeof fetch, typeof AbortController, typeof Buffer,
                            typeof setTimeout, typeof setImmediate, typeof process.nextTick],
                        fetchCall: (function () {
                            try { fetch('http://x'); return 'no-error'; }
                            catch (e) { return String(e); }
                        })()
                    };
                    """);
            JSONObject probe = new JSONObject(ctx.eval("js", "JSON.stringify(globalThis.__probe)").asString());
            JSONArray typeofs = probe.getJSONArray("typeofs");
            assertEquals("function", typeofs.getString(0));
            assertEquals("function", typeofs.getString(1));
            assertEquals("function", typeofs.getString(2));
            assertEquals("function", typeofs.getString(3));
            assertEquals("function", typeofs.getString(4));
            assertEquals("function", typeofs.getString(5));
            assertTrue(probe.getString("fetchCall").contains("quickjs_runtime"));
        } finally {
            ctx.close();
        }
    }

    @Test
    void bufferIsRealAndUrlAndOsAndEventsAreRequireables() throws Exception {
        String js = """
                function action(params) {
                    var B = require('buffer').Buffer;
                    var b = B.from('hi', 'utf8');
                    return {
                        isU8: b instanceof Uint8Array,
                        b64: b.toString('base64'),
                        len: B.byteLength('привет'),
                        canParse: URL.canParse('https://h/i'),
                        eol: typeof require('os').EOL,
                        emitter: typeof require('events').EventEmitter,
                        isArray: util.isArray([]),
                        inspect: util.inspect('hi')
                    };
                }
                """;
        JSONObject decoded = runObject(js, params(true, 0));
        assertTrue(decoded.getBoolean("isU8"));
        assertEquals("aGk=", decoded.getString("b64"));
        assertEquals(12, decoded.getInt("len"));
        assertTrue(decoded.getBoolean("canParse"));
        assertEquals("string", decoded.getString("eol"));
        assertEquals("function", decoded.getString("emitter"));
        assertTrue(decoded.getBoolean("isArray"));
        assertEquals("'hi'", decoded.getString("inspect"));
    }

    @Test
    void timersRunThroughTheDrainHandle() {
        Context ctx = Context.newBuilder("js").allowAllAccess(false).build();
        try {
            JsNodeCompat.Config config = new JsNodeCompat.Config();
            config.clock = () -> 1000.0;
            JsNodeCompat.Handle handle = JsNodeCompat.install(ctx, config);
            ctx.eval("js", """
                    globalThis.__out = [];
                    setTimeout(function () { __out.push('t0'); }, 0);
                    setImmediate(function () { __out.push('imm'); });
                    var p = Promise.resolve(1).then(function (v) { __out.push('p' + v); });
                    """);
            // the microtask drained at eval end (GraalJS job queue)
            handle.drainTimers();
            String out = ctx.eval("js", "JSON.stringify(globalThis.__out)").asString();
            assertEquals("[\"p1\",\"imm\",\"t0\"]", out);
        } finally {
            ctx.close();
        }
    }

    @Test
    void clearTimeoutAndClearIntervalAreNoOps() throws Exception {
        String js = """
                function action(params) {
                    clearTimeout(1);
                    clearInterval(2);
                    return 'ok';
                }
                """;
        assertEquals("ok", runScript(js, params(true, 0)).toString());
    }

    @Test
    void workerEnginesGetTheSameCompatSurface() throws Exception {
        String js = """
                function action(params) {
                    return runAsync(function (args) {
                        return {
                            joined: path.join('a', 'b'),
                            platform: process.platform,
                            globalAlias: global === globalThis
                        };
                    }, {}).wait();
                }
                """;
        JSONObject decoded = runObject(js, params(true, 2));
        assertEquals("a/b", decoded.getString("joined"));
        assertTrue(List.of("linux", "darwin", "win32").contains(decoded.getString("platform")));
        assertTrue(decoded.getBoolean("globalAlias"));
    }

    private static String assertThrowsPolyglot(Context ctx, String code) {
        try {
            ctx.eval("js", code);
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
        throw new AssertionError("expected " + code + " to throw");
    }
}
