// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.json.JSONObject;

import java.util.concurrent.Future;

/**
 * {@code runAsync(fn, args)} support for {@link JobJavaScriptBridge} — the
 * Java side of the cross-runtime parallel-JS API (dmtools-dart#224; the
 * Dart port is the reference implementation and keeps the blocking
 * {@code job.wait()} shape because its scripting surface is sync-call-style;
 * Java is the same shape for free via {@code Future.get()}).
 *
 * <p>Wiring (default-off — only when a job's
 * {@code params.jobParams.parallelWorkers >= 2}): a {@code __jsrDispatch}
 * host executable is bound onto the context and a small JS prelude exposes
 * the public API:</p>
 *
 * <pre>{@code
 * var job = runAsync(function (args) { ... }, { ... });
 * var result = job.wait();               // blocks THIS engine only
 * runAsync.all([job1, job2]).wait();     // fan-out / join
 * }</pre>
 *
 * <p>{@code runAsync} is a plain JS function wrapping the host dispatch so
 * that {@code runAsync.all} is an ordinary property (host executables can
 * not carry members). Job handles are host {@link ProxyObject}s exposing
 * {@code id} and {@code wait} — proxies are the sanctioned interop path
 * under {@code allowAllAccess(false)}.</p>
 */
public final class RunAsyncJobSupport {

    private RunAsyncJobSupport() {
    }

    /**
     * Main-engine prelude: the public {@code runAsync} surface over the
     * {@code __jsrDispatch} host executable. Kept in lockstep with the Dart
     * {@code asyncJobPrelude} (minus the {@code AsyncJob} constructor,
     * which is a Dart-side affordance for its FFI job ids — scripts should
     * only ever construct jobs through {@code runAsync}).
     */
    static final String PRELUDE = """
            (function() {
                function runAsync(fn, args) {
                    if (typeof fn !== 'function') {
                        throw new Error('runAsync expects a function as its first argument');
                    }
                    return __jsrDispatch(fn.toString(), args === undefined ? null : args);
                }
                runAsync.all = function(jobs) {
                    if (!Array.isArray(jobs)) {
                        throw new Error('runAsync.all expects an array of jobs');
                    }
                    return {
                        wait: function() {
                            return jobs.map(function(job) { return job.wait(); });
                        }
                    };
                };
                globalThis.runAsync = runAsync;
            })();
            """;

    /**
     * Binds the {@code runAsync} API onto the bridge's context.
     */
    public static void wire(JobJavaScriptBridge bridge, Context context,
                            AsyncJobWorkerPool pool, JSONObject parameters) {
        context.getBindings("js").putMember("__jsrDispatch", new DispatchProxy(bridge, pool, parameters));
        context.eval("js", PRELUDE);
    }

    /**
     * {@code __jsrDispatch(fn, args)} — serializes the (closure-free)
     * function and args, dispatches to the pool, and returns the job
     * handle. Java argument-validation errors throw into JS, mirroring
     * {@code IllegalArgumentException} propagation of the other host
     * executables.
     */
    static final class DispatchProxy implements ProxyExecutable {

        private final JobJavaScriptBridge bridge;
        private final AsyncJobWorkerPool pool;
        private final JSONObject parameters;

        DispatchProxy(JobJavaScriptBridge bridge, AsyncJobWorkerPool pool, JSONObject parameters) {
            this.bridge = bridge;
            this.pool = pool;
            this.parameters = parameters;
        }

        @Override
        public Object execute(Value... arguments) {
            // The prelude sends `fn.toString()` (real Function.prototype.toString):
            // polyglot Value.toString() is a DEBUG display string and truncates
            // long function sources with `...<omitted>...`, which would corrupt
            // the dispatched source (Dart prelude parity: stringify in JS).
            if (arguments.length < 1 || !arguments[0].isString()) {
                throw new IllegalArgumentException("runAsync expects a function as its first argument");
            }
            String fnSource = arguments[0].asString();
            Value argsValue = arguments.length > 1 ? arguments[1] : null;
            String argsJson = bridge.asyncArgsJson(argsValue);
            Future<AsyncJobWorkerPool.AsyncJobResult> future = pool.dispatch(
                    fnSource, argsJson, bridge.asyncScriptDirectory(), parameters);
            return new JobProxy(bridge, pool, future);
        }
    }

    /**
     * Job handle: a host object with {@code id} (number) and {@code wait}
     * (executable). {@code wait()} blocks the calling engine on
     * {@link Future#get()} until the worker answers, then converts the
     * result to a real JS value; a failed envelope throws into JS — the
     * Java-parity error channel.
     */
    static final class JobProxy implements ProxyObject {

        private final JobJavaScriptBridge bridge;
        private final AsyncJobWorkerPool pool;
        private final Future<AsyncJobWorkerPool.AsyncJobResult> future;

        JobProxy(JobJavaScriptBridge bridge, AsyncJobWorkerPool pool,
                 Future<AsyncJobWorkerPool.AsyncJobResult> future) {
            this.bridge = bridge;
            this.pool = pool;
            this.future = future;
        }

        @Override
        public Object getMember(String key) {
            switch (key) {
                case "wait":
                    return (ProxyExecutable) arguments -> {
                        AsyncJobWorkerPool.AsyncJobResult result = pool.await(future);
                        if (!result.isOk()) {
                            throw new IllegalStateException("runAsync job failed: " + result.getError());
                        }
                        return bridge.asyncWaitResult(result);
                    };
                case "id":
                    return System.identityHashCode(future);
                default:
                    return null;
            }
        }

        @Override
        public Object getMemberKeys() {
            return java.util.Arrays.asList("id", "wait");
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("runAsync job handles are read-only");
        }

        @Override
        public boolean hasMember(String key) {
            return "id".equals(key) || "wait".equals(key);
        }
    }
}
