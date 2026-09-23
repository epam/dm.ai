// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Opt-in Node/js compatibility layer for the GraalJS bridge — the Java
 * mirror of the {@code quickjs_runtime} {@code installNodeCompat} API.
 * The JS surface is the <b>same prelude source</b> the Dart runtime ships
 * ({@code src/main/resources/jsr/node_compat*.js}, byte-identical with the
 * quickjs_runtime preludes), so scripts see one surface on both runtimes;
 * only the host hooks differ (this class implements them with the JVM).
 *
 * <p>Surface: {@code global}, {@code console} (timers/table/group/count),
 * {@code process} (env/argv/hrtime/memoryUsage/stdout/stderr/on('exit')),
 * {@code path}/{@code os}/{@code url}/{@code buffer}/{@code events} as
 * {@code require()} builtins, real {@code Buffer} (Uint8Array subclass),
 * {@code URL}/{@code URLSearchParams}, {@code util} (inspect with Node
 * quoting, legacy predicates, promisify/callbackify), {@code assert},
 * {@code TextEncoder}/{@code TextDecoder}, {@code atob}/{@code btoa},
 * {@code performance}, {@code crypto}, {@code structuredClone},
 * Intl typeof-safe stubs, real timers driven by the host
 * ({@code setTimeout}/{@code setImmediate}/{@code setInterval} +
 * {@code queueMicrotask}/{@code process.nextTick}) via
 * {@link Handle#drainTimers()}, and a sync hook-backed {@code fetch}.</p>
 *
 * <p>Everything is opt-in: wired by {@link JobJavaScriptBridge} only when
 * the job's params carry {@code params.jobParams.nodeCompat === true}
 * (same default-off contract as {@code parallelWorkers}).</p>
 *
 * <p>Interop note (allowAllAccess(false)): hook proxies exchange
 * primitives and plain lists only; the preludes marshal anything complex
 * through JSON strings.</p>
 */
public final class JsNodeCompat {

    private JsNodeCompat() {
    }

    /** Resource paths of the shared preludes, in install order. */
    private static final String[] PRELUDES = {
            "/jsr/node_compat.js",
            "/jsr/node_compat_async.js",
            "/jsr/node_compat_buffer.js",
            "/jsr/node_compat_url.js",
    };

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
     * user.dir cwd, wall clock, SecureRandom, stdout console, no fetch
     * transport — {@code fetch} stays a typeof-safe stub).
     *
     * @return the drain handle ({@link Handle#drainTimers()})
     */
    public static Handle install(Context context) {
        return install(context, new Config());
    }

    /**
     * Installs the compat surface with the given hooks. Idempotent per
     * context (reinstalling replaces the previous surface).
     *
     * @return the drain handle ({@link Handle#drainTimers()})
     */
    public static Handle install(Context context, Config config) {
        var bindings = context.getBindings("js");

        // The config object the preludes read: one JSON.parse so arrays
        // and maps cross as real JS values (env, argv, ...).
        JSONObject cfg = new JSONObject(new LinkedHashMap<>());
        cfg.put("env", new JSONObject(config.env));
        cfg.put("platform", config.platform);
        cfg.put("arch", config.arch);
        cfg.put("nodeVersion", config.nodeVersion);
        cfg.put("argv", new JSONArray(config.argv));
        cfg.put("scriptPath", config.scriptPath == null ? JSONObject.NULL : config.scriptPath);
        cfg.put("pid", config.pid);
        cfg.put("cpusCount", config.cpusCount);
        cfg.put("hostname", config.hostname);
        cfg.put("tmpdir", config.tmpdir == null ? JSONObject.NULL : config.tmpdir.get());
        cfg.put("homedir", config.homedir == null ? JSONObject.NULL : config.homedir.get());
        bindings.putMember("__ncConfigRaw", cfg.toString());
        context.eval("js", "globalThis.__ncConfig = JSON.parse(__ncConfigRaw);");
        bindings.putMember("__ncMaxTimerCallbacks", config.maxTimerCallbacks);

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
        bindings.putMember("__ncRandomUuid", (ProxyExecutable) args -> config.randomUuid.get());
        bindings.putMember("__ncRandomValues", (ProxyExecutable) args -> {
            int length = args.length > 0 && args[0].isNumber() ? Math.max(0, args[0].asInt()) : 0;
            byte[] bytes = new byte[length];
            config.randomFiller.accept(bytes);
            return jsBytes(bytes);
        });
        bindings.putMember("__ncUtf8Encode", (ProxyExecutable) args -> jsBytes(
                args.length > 0 && args[0].isString()
                        ? args[0].asString().getBytes(StandardCharsets.UTF_8)
                        : new byte[0]));
        bindings.putMember("__ncUtf8Decode", (ProxyExecutable) args -> bytes(
                args.length > 0 ? args[0] : null, raw -> new String(raw, StandardCharsets.UTF_8)));
        bindings.putMember("__ncBase64Encode", (ProxyExecutable) args -> {
            byte[] bytes = args.length > 0 && args[0].isString()
                    ? args[0].asString().getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            return java.util.Base64.getEncoder().encodeToString(bytes);
        });
        // Node atob semantics: the binary string of the decoded bytes
        // (jsr default `_b64Decode` parity — UTF-8 decoded string).
        bindings.putMember("__ncBase64Decode", (ProxyExecutable) args ->
                args.length > 0 && args[0].isString()
                        ? new String(java.util.Base64.getDecoder().decode(args[0].asString()),
                                StandardCharsets.UTF_8)
                        : "");
        bindings.putMember("__ncFetch", (ProxyExecutable) args -> config.httpFetch == null
                ? null
                : config.httpFetch.apply(args.length > 0 ? valueToJson(args[0]) : "null"));

        for (String prelude : PRELUDES) {
            context.eval("js", resource(prelude));
        }
        if (config.httpFetch != null) {
            context.eval("js", resource("/jsr/node_compat_fetch.js"));
        }
        return new Handle(context, config);
    }

    /**
     * Registers (or replaces) one consumer-provided builtin module visible
     * to the compat {@code require} (e.g. {@code 'fs'} mapped onto file
     * tools). The factory receives the module name and returns the module
     * exports; keep the return value JS-compatible.
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

    private static String bytes(Value value, java.util.function.Function<byte[], String> decoder) {
        if (value == null || value.isNull()) {
            return decoder.apply(new byte[0]);
        }
        int size = value.hasArrayElements() ? (int) value.getArraySize() : 0;
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) value.getArrayElement(i).asInt();
        }
        return decoder.apply(bytes);
    }

    /**
     * Byte arrays cross the interop boundary as real JS arrays
     * ({@link ProxyArray}) — the jsr host convention (the Dart bridge
     * decodes its returned JSON into a plain array).
     */
    private static ProxyArray jsBytes(byte[] bytes) {
        Integer[] out = new Integer[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = bytes[i] & 0xFF;
        }
        return ProxyArray.fromArray((Object[]) out);
    }

    /**
     * Marshals one guest value to JSON text — the jsr bridge convention
     * (each JS argument arrives at the host hook as its JSON encoding).
     */
    private static String valueToJson(Value value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isString()) {
            return JSONObject.quote(value.asString());
        }
        if (value.isBoolean()) {
            return String.valueOf(value.asBoolean());
        }
        if (value.isNumber()) {
            return value.as(Object.class).toString();
        }
        if (value.hasArrayElements()) {
            StringBuilder out = new StringBuilder("[");
            long size = value.getArraySize();
            for (long i = 0; i < size; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(valueToJson(value.getArrayElement(i)));
            }
            return out.append(']').toString();
        }
        if (value.hasMembers()) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (String key : value.getMemberKeys()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(JSONObject.quote(key)).append(':')
                        .append(valueToJson(value.getMember(key)));
            }
            return out.append('}').toString();
        }
        return "null";
    }

    private static String resource(String path) {
        InputStream stream = JsNodeCompat.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("jsr prelude missing from the classpath: " + path);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("jsr prelude unreadable: " + path, e);
        }
    }

    /** How {@link Handle#drainTimers()} runs due timers and immediates. */
    public enum TimerDrainMode {

        /**
         * Runs one drain pass (immediates, then due timers) and returns —
         * never waits. Safe everywhere; pending later timers simply stay
         * queued.
         */
        READY,

        /**
         * Additionally sleeps until the earliest non-unref'd timer is due
         * and keeps draining until the queue empties, the wall-clock budget
         * elapses (raises), or {@code maxTimerCallbacks} is exceeded per
         * pass (raises — the {@code setInterval(fn, 0)} storm guard).
         * Headless CLI parity with the Dart product wiring.
         */
        BLOCK,

        /** No-op: timers are registered but never run. */
        NONE
    }

    /** Timer drain knobs + stats (jsr {@code NodeCompatConfig} parity). */
    public static final class Config {

        /** {@code process.env} snapshot. */
        public Map<String, String> env = new LinkedHashMap<>(System.getenv());

        /** {@code process.platform} (node-style: linux/darwin/win32). */
        public String platform = platformDefault();

        /** {@code process.arch}. */
        public String arch = System.getProperty("os.arch", "x64");

        /** {@code process.version}. */
        public String nodeVersion = "v22.0.0-compat";

        /** {@code process.argv}. */
        public List<String> argv = List.of("graaljs");

        /** Script path for {@code __dirname}/{@code __filename}; {@code null} = unset. */
        public String scriptPath;

        /** {@code process.pid} stand-in. */
        public int pid = 1;

        /** {@code os.cpus().length} stand-in. */
        public int cpusCount = 1;

        /** {@code os.hostname()}. */
        public String hostname = "localhost";

        /** {@code os.tmpdir()}; {@code null} = the prelude's platform default. */
        public Supplier<String> tmpdir;

        /** {@code os.homedir()}; {@code null} = the prelude's env-based default. */
        public Supplier<String> homedir;

        /** {@code process.cwd()}. */
        public Supplier<String> cwd = () -> System.getProperty("user.dir", "/");

        /** {@code performance.now()} / the timer clock, fractional ms. */
        public DoubleSupplier clock = () -> System.nanoTime() / 1_000_000.0;

        /** Fills a byte array with secure random bytes. */
        public Consumer<byte[]> randomFiller = bytes -> new SecureRandom().nextBytes(bytes);

        /** {@code crypto.randomUUID()}. */
        public Supplier<String> randomUuid = () -> UUID.randomUUID().toString();

        /** {@code console} sink: {@code (level, message)}. */
        public BiConsumer<String, String> consoleSink =
                (level, message) -> System.out.println(message);

        /** {@code process.exit(code)} notification. */
        public Consumer<Integer> exitHook = code -> {
        };

        /**
         * The sync {@code fetch} transport: {@code requestJson → responseJson}
         * ({@code null} = transport miss). {@code null} keeps {@code fetch}
         * as the typeof-safe stub; the bridge wires the real JVM transport.
         */
        public UnaryOperator<String> httpFetch;

        /** Timer drain mode (see {@link TimerDrainMode}). */
        public TimerDrainMode timerDrain = TimerDrainMode.READY;

        /** Storm guard: max callbacks per {@link Handle#drainTimers()} pass. */
        public int maxTimerCallbacks = 1000;

        /** Block-mode budget for a single drain. */
        public long maxTimerDrainWallClockMs = Duration.ofSeconds(30).toMillis();

        /**
         * Headless CLI defaults: block-mode timer drain ("setTimeout as
         * sleep" behaves like Node). Product wiring parity with the Dart
         * {@code wireEngine} default.
         */
        public static Config cliDefaults() {
            Config config = new Config();
            config.timerDrain = TimerDrainMode.BLOCK;
            return config;
        }

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

    /** Stats from one {@link Handle#drainTimers()} run. */
    public static final class TimerDrainStats {

        /** Callbacks that ran. */
        public final int ran;

        /** Timers still queued after the drain. */
        public final int pending;

        TimerDrainStats(int ran, int pending) {
            this.ran = ran;
            this.pending = pending;
        }
    }

    /**
     * Per-context drain handle: runs the timer queue the preludes
     * registered (the JS side is a plain table — there is no background
     * event loop, the host drives it).
     */
    public static final class Handle {

        private final Context context;
        private final Config config;

        Handle(Context context, Config config) {
            this.context = context;
            this.config = config;
        }

        /**
         * Updates {@code __dirname}/{@code __filename} for the next script
         * (the product bridge calls this before each script eval).
         */
        public void setScriptPath(String scriptPath) {
            context.getBindings("js").putMember("__ncScriptPathRaw",
                    scriptPath == null ? "null" : JSONObject.quote(scriptPath));
            context.eval("js", "if (typeof __ncSetScriptPath === 'function') "
                    + "__ncSetScriptPath(JSON.parse(__ncScriptPathRaw));");
        }

        /**
         * Runs due timers and immediates. READY: one pass. BLOCK: sleeps
         * between passes until the queue empties or a budget raises.
         * Callbacks run as fresh top-level evals — never re-entrantly.
         */
        public TimerDrainStats drainTimers() {
            if (config.timerDrain == TimerDrainMode.NONE) {
                return new TimerDrainStats(0, 0);
            }
            long wallStart = System.nanoTime();
            long limitMs = config.maxTimerDrainWallClockMs;
            int ran = 0;
            while (true) {
                DrainPass pass = drainPass();
                ran += pass.ran;
                // promise reactions queued by callbacks run before the next
                // pass: GraalJS drains the job queue at the end of this eval
                if (config.timerDrain != TimerDrainMode.BLOCK || pass.nextDue == null) {
                    return finish(ran);
                }
                long waitMs = (long) Math.ceil(pass.nextDue - config.clock.getAsDouble());
                long elapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
                if (waitMs > 0) {
                    if (elapsedMs >= limitMs) {
                        throw new IllegalStateException(
                                "node_compat timer drain exceeded maxTimerDrainWallClock ("
                                        + limitMs + "ms) with " + pass.pending
                                        + " timers still pending — an interval that never ends?");
                    }
                    try {
                        Thread.sleep(Math.min(waitMs, limitMs - elapsedMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("node_compat timer drain interrupted", e);
                    }
                }
            }
        }

        private static final class DrainPass {
            final int ran;
            final int pending;
            final Double nextDue;

            DrainPass(int ran, int pending, Double nextDue) {
                this.ran = ran;
                this.pending = pending;
                this.nextDue = nextDue;
            }
        }

        private DrainPass drainPass() {
            Value raw = context.eval("js", "JSON.stringify(globalThis.__ncTimerDrain())");
            if (raw == null || !raw.isString()) {
                throw new IllegalStateException("node_compat timer drain failed: no stats");
            }
            JSONObject st = new JSONObject(raw.asString());
            if (st.optBoolean("capped", false)) {
                throw new IllegalStateException(
                        "node_compat timer drain exceeded maxTimerCallbacks ("
                                + config.maxTimerCallbacks + ") — possible setInterval(fn, 0) storm");
            }
            return new DrainPass(st.optInt("ran", 0), st.optInt("pending", 0),
                    st.isNull("nextDue") || st.opt("nextDue") == null
                            ? null : st.optDouble("nextDue"));
        }

        private TimerDrainStats finish(int ran) {
            Value pending = context.eval("js", "Number(globalThis.__ncTimerPendingCount())");
            return new TimerDrainStats(ran,
                    pending == null || !pending.isNumber() ? 0 : pending.asInt());
        }
    }

    /**
     * The default sync HTTP transport for {@code fetch} — plain JVM
     * {@link HttpClient} ({@code requestJson → responseJson}, the same
     * JSON contract the Dart {@code SyncHttpClient} hook speaks).
     */
    public static final class SyncHttp {

        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        private SyncHttp() {
        }

        /**
         * Performs one sync HTTP request.
         *
         * @param requestJson {@code {"method","url","headers","body"}}
         * @return {@code {"status","headers","body"}} or the transport-fail
         *         shape ({@code {"status":0,"error":"..."}}) — never null
         */
        public static String fetch(String requestJson) {
            try {
                JSONObject request = new JSONObject(requestJson);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(request.getString("url")))
                        .timeout(Duration.ofSeconds(30));
                String method = request.optString("method", "GET").toUpperCase();
                JSONObject headers = request.optJSONObject("headers");
                if (headers != null) {
                    for (String key : headers.keySet()) {
                        builder.header(key, String.valueOf(headers.get(key)));
                    }
                }
                String body = request.optString("body", null);
                if (body != null && !body.isEmpty()
                        && (method.equals("POST") || method.equals("PUT")
                        || method.equals("PATCH") || method.equals("DELETE"))) {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(
                            body, StandardCharsets.UTF_8));
                } else {
                    builder.GET();
                }
                HttpResponse<String> response =
                        CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                JSONObject out = new JSONObject(new LinkedHashMap<>());
                out.put("status", response.statusCode());
                out.put("headers", new JSONObject(new LinkedHashMap<>()));
                out.put("body", response.body());
                return out.toString();
            } catch (Exception e) {
                return new JSONObject(new LinkedHashMap<>())
                        .put("status", 0)
                        .put("error", String.valueOf(e.getMessage())).toString();
            }
        }
    }
}
