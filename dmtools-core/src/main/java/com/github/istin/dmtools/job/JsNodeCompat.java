// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Opt-in Node/js compatibility layer for the GraalJS bridge — the Java
 * mirror of the {@code quickjs_runtime} {@code installNodeCompat} API
 * (IstiN/quickjs_runtime issue #3 / PR #4; the Dart implementation is the
 * reference). The two runtimes expose the same surface and the same error
 * texts so AI-written agent scripts behave identically on both.
 *
 * <p>Two tiers:</p>
 * <ul>
 *   <li><b>Tier 1 — real:</b> {@code global} alias, {@code console.*}
 *       (pluggable sink), {@code process} (env snapshot, platform/arch/
 *       version, exitCode, cwd, exit), {@code path} (posix subset),
 *       {@code assert} / {@code util} (node-like subsets),
 *       {@code TextEncoder}/{@code TextDecoder} (utf-8, pure JS),
 *       {@code atob}/{@code btoa}, {@code performance.now()},
 *       {@code crypto.randomUUID()}/{@code getRandomValues()},
 *       {@code structuredClone} (JSON fidelity), and a {@code require()}
 *       that resolves builtin compat modules, consumer-registered modules
 *       ({@link #installModule}) and falls back to a pre-existing
 *       {@code require} loader.</li>
 *   <li><b>Tier 2 — self-documenting stubs:</b> {@code Buffer},
 *       {@code fetch}, {@code AbortController}, {@code setTimeout}/{@code
 *       setInterval}/{@code setImmediate}, {@code process.nextTick} —
 *       typeof-safe (feature guards keep working) and throw the working
 *       alternative on call.</li>
 * </ul>
 *
 * <p>Everything is opt-in: wired by {@link JobJavaScriptBridge} only when
 * the job's params carry {@code params.jobParams.nodeCompat === true}
 * (same default-off contract as {@code parallelWorkers}), so the scripting
 * surface of existing configs is byte-for-byte unchanged.</p>
 *
 * <p>Interop note (allowAllAccess(false)): hook proxies only exchange
 * primitives (String/int/double) — the sanctioned interop path; everything
 * array-shaped (utf-8 bytes, base64) is implemented in pure JS.</p>
 */
public final class JsNodeCompat {

    private JsNodeCompat() {
    }

    /**
     * Whether the job's params enable the layer
     * ({@code params.jobParams.nodeCompat === true}).
     */
    public static boolean isEnabled(JSONObject parameters) {
        if (parameters == null) {
            return false;
        }
        JSONObject jobParams = parameters.optJSONObject("jobParams");
        return jobParams != null && jobParams.optBoolean("nodeCompat", false);
    }

    /**
     * Installs the compat surface with default config (OS env/platform,
     * user.dir cwd, wall clock, SecureRandom, stdout console).
     */
    public static void install(Context context) {
        install(context, new Config());
    }

    /**
     * Installs the compat surface with the given hooks. Idempotent per
     * context (reinstalling replaces the previous surface).
     */
    public static void install(Context context, Config config) {
        var bindings = context.getBindings("js");
        bindings.putMember("__ncConsoleWrite", (ProxyExecutable) args -> {
            try {
                config.consoleSink.accept(
                        args.length > 0 && args[0].isString() ? args[0].asString() : "log",
                        args.length > 1 && args[1].isString() ? args[1].asString() : "");
            } catch (RuntimeException e) {
                // console must never break the script — swallow sink errors
            }
            return null;
        });
        bindings.putMember("__ncCwd", (ProxyExecutable) args -> config.cwd.get());
        bindings.putMember("__ncNow", (ProxyExecutable) args -> config.clock.getAsDouble());
        bindings.putMember("__ncExit", (ProxyExecutable) args -> {
            int code = args.length > 0 && args[0].isNumber() ? args[0].asInt() : 0;
            config.exitHook.accept(code);
            return null;
        });
        bindings.putMember("__ncRandomByte", (ProxyExecutable) args -> config.randomByte.getAsInt());
        bindings.putMember("__ncRandomUuid", (ProxyExecutable) args -> config.randomUuid.get());
        bindings.putMember("__ncEnvJson", (ProxyExecutable) args -> {
            // primitives-only interop: env crosses as a JSON string
            return new JSONObject(config.env).toString();
        });
        context.eval("js", "globalThis.__ncConfig = "
                + new JSONObject()
                .put("platform", config.platform)
                .put("arch", config.arch)
                .put("nodeVersion", config.nodeVersion)
                + ";");
        context.eval("js", PRELUDE);
    }

    /**
     * Registers (or replaces) one consumer-provided builtin module visible
     * to the compat {@code require} (e.g. {@code 'fs'} mapped onto file
     * tools). The factory receives the module name and returns the module
     * exports; keep the return value JS-compatible (primitives or values
     * already converted for the bridge).
     */
    public static void installModule(Context context, String name,
                                     UnaryOperator<String> jsonFactory) {
        String safe = name.replaceAll("[^A-Za-z0-9_]", "_");
        context.getBindings("js").putMember("__ncModule_" + safe,
                (ProxyExecutable) args -> args.length > 0 && args[0].isString()
                        ? jsonFactory.apply(args[0].asString())
                        : jsonFactory.apply(name));
        context.eval("js", "globalThis.__ncRegistry[" + JSONObject.quote(name)
                + "] = function (moduleName) { return __ncModule_" + safe
                + "(moduleName); };");
    }

    /**
     * Hook configuration; every field has a self-contained default, so
     * {@code new Config()} works without any setup.
     */
    public static final class Config {

        /**
         * {@code process.env} snapshot.
         */
        public Map<String, String> env = new LinkedHashMap<>(System.getenv());

        /**
         * {@code process.platform} (node-style: linux/darwin/win32).
         */
        public String platform = platformDefault();

        /**
         * {@code process.arch}.
         */
        public String arch = System.getProperty("os.arch", "x64");

        /**
         * {@code process.version}.
         */
        public String nodeVersion = "v22.0.0-compat";

        /**
         * {@code process.cwd()}.
         */
        public Supplier<String> cwd = () -> System.getProperty("user.dir", "/");

        /**
         * {@code performance.now()} in fractional ms.
         */
        public DoubleSupplier clock = () -> System.nanoTime() / 1_000_000.0;

        /**
         * One secure random byte ({@code crypto.getRandomValues} filler).
         */
        public IntSupplier randomByte = new IntSupplier() {
            private final SecureRandom random = new SecureRandom();

            @Override
            public int getAsInt() {
                return random.nextInt(256);
            }
        };

        /**
         * {@code crypto.randomUUID()}.
         */
        public Supplier<String> randomUuid = () -> UUID.randomUUID().toString();

        /**
         * {@code console} sink: {@code (level, message)}.
         */
        public BiConsumer<String, String> consoleSink =
                (level, message) -> System.out.println(message);

        /**
         * {@code process.exit(code)} notification (the JS side still throws
         * a {@code ProcessExit} error so sync scripts stop).
         */
        public Consumer<Integer> exitHook = code -> {
        };

        private static String platformDefault() {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                return "win32";
            }
            if (os.contains("mac") || os.contains("darwin")) {
                return "darwin";
            }
            return "linux";
        }
    }

    /**
     * The compat surface as one JS bootstrap.
     *
     * Tier-2 stubs are call-time errors and typeof-safe. The utf-8 and
     * base64 codecs are pure JS: under {@code allowAllAccess(false)} hook
     * proxies exchange primitives only, so nothing array-shaped crosses
     * the interop boundary. Error texts are byte-identical with the Dart
     * {@code quickjs_runtime} implementation on purpose — cross-runtime
     * script parity.
     */
    static final String PRELUDE = """
            (function () {
                var cfg = globalThis.__ncConfig || {
                    platform: 'linux', arch: 'x64', nodeVersion: 'v22.0.0-compat'
                };

                function safeStringify(v) {
                    try { return JSON.stringify(v); } catch (e) { return String(v); }
                }
                function unsupported(name, alternative) {
                    return function () {
                        throw new Error(name + ' is not available in quickjs_runtime: ' +
                            alternative);
                    };
                }

                // ── console ──
                var consoleObj = {};
                ['log', 'info', 'warn', 'error', 'debug', 'trace'].forEach(function (level) {
                    consoleObj[level] = function () {
                        var parts = [];
                        for (var i = 0; i < arguments.length; i++) {
                            var a = arguments[i];
                            parts.push(typeof a === 'string' ? a : safeStringify(a));
                        }
                        __ncConsoleWrite(level, parts.join(' '));
                    };
                });
                globalThis.console = consoleObj;

                // ── global ──
                globalThis.global = globalThis;

                // ── process ──
                var envObj = JSON.parse(__ncEnvJson());
                function cwdSafe() {
                    try { return __ncCwd(); } catch (e) { return '/'; }
                }
                globalThis.process = {
                    env: envObj,
                    platform: cfg.platform,
                    arch: cfg.arch,
                    version: cfg.nodeVersion,
                    exitCode: 0,
                    argv: ['graaljs'],
                    cwd: cwdSafe,
                    exit: function (code) {
                        try { __ncExit(code || 0); } catch (e) { /* host hook only */ }
                        throw new Error('ProcessExit: ' + (code || 0));
                    },
                    nextTick: unsupported('process.nextTick',
                        'no scheduling beyond promises — run the work directly')
                };

                // ── performance ──
                globalThis.performance = {
                    timeOrigin: 0,
                    now: function () { return __ncNow(); }
                };

                // ── base64 (pure JS: primitives-only interop) ──
                var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
                function b64Encode(str) {
                    var bytes = utf8EncodeBytes(String(str));
                    var out = '';
                    for (var i = 0; i < bytes.length; i += 3) {
                        var b0 = bytes[i];
                        var b1 = i + 1 < bytes.length ? bytes[i + 1] : null;
                        var b2 = i + 2 < bytes.length ? bytes[i + 2] : null;
                        out += B64[b0 >> 2];
                        out += B64[((b0 & 3) << 4) | ((b1 === null ? 0 : b1) >> 4)];
                        out += b1 === null ? '=' : B64[((b1 & 15) << 2) | ((b2 === null ? 0 : b2) >> 6)];
                        out += b2 === null ? '=' : B64[b2 & 63];
                    }
                    return out;
                }
                function b64Decode(text) {
                    var clean = String(text).replace(/=+$/, '');
                    var out = [];
                    for (var i = 0; i < clean.length; i += 4) {
                        var n = [0, 0, 0, 0];
                        for (var k = 0; k < 4; k++) {
                            n[k] = i + k < clean.length ? B64.indexOf(clean.charAt(i + k)) : 0;
                        }
                        out.push((n[0] << 2) | (n[1] >> 4));
                        if (i + 2 < clean.length) out.push(((n[1] & 15) << 4) | (n[2] >> 2));
                        if (i + 3 < clean.length) out.push(((n[2] & 3) << 6) | n[3]);
                    }
                    return utf8DecodeBytes(out);
                }

                // ── utf-8 (pure JS) ──
                function utf8EncodeBytes(str) {
                    var out = [];
                    for (var i = 0; i < str.length; i++) {
                        var c = str.charCodeAt(i);
                        if (c < 0x80) {
                            out.push(c);
                        } else if (c < 0x800) {
                            out.push(0xC0 | (c >> 6), 0x80 | (c & 63));
                        } else if (c >= 0xD800 && c < 0xDC00 && i + 1 < str.length) {
                            var c2 = str.charCodeAt(++i);
                            var cp = 0x10000 + ((c & 0x3FF) << 10) + (c2 & 0x3FF);
                            out.push(0xF0 | (cp >> 18), 0x80 | ((cp >> 12) & 63),
                                0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63));
                        } else {
                            out.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
                        }
                    }
                    return out;
                }
                function utf8DecodeBytes(bytes) {
                    var out = '';
                    for (var i = 0; i < bytes.length;) {
                        var b = bytes[i];
                        if (b < 0x80) {
                            out += String.fromCharCode(b);
                            i += 1;
                        } else if (b < 0xE0) {
                            out += String.fromCharCode(((b & 31) << 6) | (bytes[i + 1] & 63));
                            i += 2;
                        } else if (b < 0xF0) {
                            out += String.fromCharCode(((b & 15) << 12) |
                                ((bytes[i + 1] & 63) << 6) | (bytes[i + 2] & 63));
                            i += 3;
                        } else {
                            var cp = ((b & 7) << 18) | ((bytes[i + 1] & 63) << 12) |
                                ((bytes[i + 2] & 63) << 6) | (bytes[i + 3] & 63);
                            cp -= 0x10000;
                            out += String.fromCharCode(0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF));
                            i += 4;
                        }
                    }
                    return out;
                }

                globalThis.btoa = b64Encode;
                globalThis.atob = b64Decode;

                // ── TextEncoder / TextDecoder ──
                function TextEncoder() {}
                TextEncoder.prototype.encoding = 'utf-8';
                TextEncoder.prototype.encode = function (text) {
                    return Uint8Array.from(utf8EncodeBytes(text === undefined ? '' : String(text)));
                };
                TextEncoder.prototype.encodeInto = unsupported('TextEncoder.encodeInto',
                    'use encode() instead');
                globalThis.TextEncoder = TextEncoder;

                function TextDecoder() {}
                TextDecoder.prototype.encoding = 'utf-8';
                TextDecoder.prototype.decode = function (bytes) {
                    var arr = [];
                    if (bytes) {
                        for (var i = 0; i < bytes.length; i++) arr.push(bytes[i] & 255);
                    }
                    return utf8DecodeBytes(arr);
                };
                globalThis.TextDecoder = TextDecoder;

                // ── crypto (subset) ──
                globalThis.crypto = {
                    getRandomValues: function (array) {
                        for (var i = 0; i < array.length; i++) {
                            array[i] = __ncRandomByte();
                        }
                        return array;
                    },
                    randomUUID: function () { return __ncRandomUuid(); }
                };

                // ── structuredClone (JSON fidelity, documented) ──
                globalThis.structuredClone = function (value) {
                    return JSON.parse(JSON.stringify(value));
                };

                // ── path (posix subset) ──
                function isAbsolute(p) {
                    return typeof p === 'string' && p.charAt(0) === '/';
                }
                function normalize(p) {
                    var abs = isAbsolute(p);
                    var parts = String(p).split('/');
                    var out = [];
                    for (var i = 0; i < parts.length; i++) {
                        var part = parts[i];
                        if (part === '' || part === '.') continue;
                        if (part === '..') {
                            if (out.length && out[out.length - 1] !== '..') out.pop();
                            else if (!abs) out.push('..');
                            continue;
                        }
                        out.push(part);
                    }
                    var joined = out.join('/');
                    return (abs ? '/' : '') + (joined || (abs ? '' : '.'));
                }
                function join() {
                    var parts = [];
                    for (var i = 0; i < arguments.length; i++) {
                        if (typeof arguments[i] === 'string' && arguments[i] !== '') {
                            parts.push(arguments[i]);
                        }
                    }
                    return normalize(parts.join('/'));
                }
                function resolve() {
                    var parts = [];
                    for (var i = arguments.length - 1; i >= 0; i--) {
                        var a = arguments[i];
                        if (typeof a === 'string' && a !== '') {
                            parts.unshift(a);
                            if (isAbsolute(a)) break;
                        }
                    }
                    if (!parts.length || !isAbsolute(parts[0])) parts.unshift(cwdSafe());
                    return normalize(parts.join('/'));
                }
                function basename(p, ext) {
                    var b = String(p).split('/').pop() || '';
                    if (ext && b.slice(-ext.length) === ext && b !== ext) {
                        b = b.slice(0, b.length - ext.length);
                    }
                    return b;
                }
                function dirname(p) {
                    var s = String(p);
                    var idx = s.lastIndexOf('/');
                    if (idx < 0) return '.';
                    if (idx === 0) return '/';
                    return s.slice(0, idx) || '/';
                }
                function extname(p) {
                    var b = basename(p);
                    var idx = b.lastIndexOf('.');
                    return idx <= 0 ? '' : b.slice(idx);
                }
                function relative(from, to) {
                    from = resolve(from);
                    to = resolve(to);
                    if (from === to) return '';
                    var f = from.split('/').filter(Boolean);
                    var t = to.split('/').filter(Boolean);
                    var i = 0;
                    while (i < f.length && i < t.length && f[i] === t[i]) i++;
                    var out = [];
                    for (var u = 0; u < f.length - i; u++) out.push('..');
                    for (var d = i; d < t.length; d++) out.push(t[d]);
                    return out.join('/') || '.';
                }
                var path = {
                    sep: '/',
                    delimiter: ':',
                    posix: null,
                    win32: unsupported('path.win32', 'posix only in this runtime'),
                    isAbsolute: isAbsolute,
                    normalize: normalize,
                    join: join,
                    resolve: resolve,
                    basename: basename,
                    dirname: dirname,
                    extname: extname,
                    relative: relative
                };
                path.posix = path;
                globalThis.path = path;

                // ── assert (node-like subset) ──
                function fail(message) {
                    var err = new Error(message);
                    err.name = 'AssertionError';
                    return err;
                }
                function assert(value, message) {
                    if (!value) {
                        throw fail(message ||
                            'The expression evaluated to a falsy value.');
                    }
                }
                assert.ok = assert;
                assert.equal = function (a, b, m) {
                    if (a != b) {
                        throw fail(m || 'Expected ' + safeStringify(a) +
                            ' == ' + safeStringify(b));
                    }
                };
                assert.notEqual = function (a, b, m) {
                    if (a == b) {
                        throw fail(m || 'Expected ' + safeStringify(a) +
                            ' != ' + safeStringify(b));
                    }
                };
                assert.deepEqual = function (a, b, m) {
                    if (safeStringify(a) !== safeStringify(b)) {
                        throw fail(m || 'Expected ' + safeStringify(a) +
                            ' to deeply equal ' + safeStringify(b));
                    }
                };
                assert.notDeepEqual = function (a, b, m) {
                    if (safeStringify(a) === safeStringify(b)) {
                        throw fail(m || 'Expected different deep values');
                    }
                };
                assert.throws = function (fn, expected, m) {
                    try {
                        fn();
                    } catch (e) {
                        if (expected instanceof RegExp) {
                            if (!expected.test(e.message)) {
                                throw fail(m || 'Expected error message to match ' +
                                    expected + ', got: ' + e.message);
                            }
                        }
                        return e;
                    }
                    throw fail(m || 'Expected the function to throw');
                };
                assert.doesNotThrow = function (fn, m) {
                    try {
                        fn();
                    } catch (e) {
                        throw fail(m || 'Expected the function not to throw, got: ' +
                            e.message);
                    }
                };
                assert.match = function (str, re, m) {
                    if (!re.test(String(str))) {
                        throw fail(m || 'Expected ' + safeStringify(String(str)) +
                            ' to match ' + String(re));
                    }
                };
                assert.fail = function (m) { throw fail(m || 'assert.fail()'); };
                assert.strict = assert;
                globalThis.assert = assert;

                // ── util (subset) ──
                function format() {
                    var args = Array.prototype.slice.call(arguments);
                    var out = String(args.shift() || '').replace(/%[sdjf%]/g, function (spec) {
                        if (spec === '%%') return '%';
                        if (!args.length) return spec;
                        var v = args.shift();
                        if (spec === '%s') return String(v);
                        if (spec === '%d') return String(parseInt(v, 10));
                        return safeStringify(v);
                    });
                    if (args.length) {
                        out += ' ' + args.map(function (v) { return safeStringify(v); }).join(' ');
                    }
                    return out;
                }
                globalThis.util = {
                    inspect: function (v) { return safeStringify(v); },
                    format: format,
                    types: {
                        isPromise: function (v) { return v instanceof Promise; }
                    }
                };

                // ── require: builtin compat modules + registry + fallback ──
                var registry = {};
                globalThis.__ncRegistry = registry;
                var builtins = {
                    path: function () { return path; },
                    assert: function () { return assert; },
                    util: function () { return globalThis.util; }
                };
                var baseRequire = typeof require === 'function' ? require : null;
                function compatRequire(name) {
                    if (Object.prototype.hasOwnProperty.call(registry, name)) {
                        return registry[name](name);
                    }
                    if (Object.prototype.hasOwnProperty.call(builtins, name)) {
                        return builtins[name](name);
                    }
                    if (baseRequire) return baseRequire(name);
                    throw new Error("Cannot find module '" + name +
                        "' (compat builtins: path, assert, util" +
                        (Object.keys(registry).length
                            ? '; consumer-registered: ' + Object.keys(registry).join(', ')
                            : '') + ')');
                }
                globalThis.require = compatRequire;

                // ── Tier 2: call-time stubs (typeof-safe) ──
                globalThis.Buffer = unsupported('Buffer',
                    'use TextEncoder / TextDecoder for bytes, atob / btoa for base64');
                globalThis.fetch = unsupported('fetch',
                    'this runtime is sync-call-style — use the host-provided sync tools ' +
                    'or runAsync(fn, args) for parallel engines');
                globalThis.AbortController = unsupported('AbortController',
                    'no async operations in this runtime — nothing to abort');
                globalThis.setTimeout = unsupported('setTimeout',
                    'no event loop in v1 — run the work directly, or use runAsync ' +
                    'for parallel engines');
                globalThis.clearTimeout = function () {};
                globalThis.setInterval = unsupported('setInterval',
                    'no event loop in v1 — run the work directly');
                globalThis.clearInterval = function () {};
                globalThis.setImmediate = unsupported('setImmediate',
                    'no event loop in v1 — run the work directly');
            })();
            """;
}
