// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Engine-worker pool behind {@code runAsync(fn, args)} — parallel JavaScript
 * execution for the GraalJS bridge (dmtools-dart#224 cross-runtime parity;
 * the Dart port is the reference implementation).
 *
 * <p>Mirrors the Dart worker-isolate pool: N long-lived daemon worker
 * threads; each job runs on a <b>fresh</b> {@link JobJavaScriptBridge}
 * created <b>on the worker thread</b> — GraalJS contexts are thread-confined,
 * so one context per job on its own thread, never shared (QuickJS is not
 * thread-safe either; the Dart pool keeps one engine per isolate). A fresh
 * bridge per job matches both runtimes' isolation norms: per-job require
 * cache, per-job set_env_variable overrides, per-job script directory.</p>
 *
 * <p>Contract (identical on both runtimes): dispatched functions must be
 * closure-free — they cross the engine boundary as {@code fn.toString()} +
 * JSON args, and see the job's {@code params} object as a global plus the
 * full tool bridge of the worker engine.</p>
 *
 * <p>The current thread's {@link PropertyReader} overrides snapshot is taken
 * at dispatch time and installed on the worker thread for the duration of
 * the job — the Java {@code ThreadLocal} analog of the Dart per-worker
 * overrides snapshot.</p>
 */
public class AsyncJobWorkerPool {

    /**
     * Completion envelope of one dispatched function.
     */
    public static final class AsyncJobResult {
        private final boolean ok;
        private final Object result;
        private final String error;

        private AsyncJobResult(boolean ok, Object result, String error) {
            this.ok = ok;
            this.result = result;
            this.error = error;
        }

        public static AsyncJobResult ok(Object result) {
            return new AsyncJobResult(true, result, null);
        }

        static AsyncJobResult error(String error) {
            return new AsyncJobResult(false, null, error);
        }

        public boolean isOk() {
            return ok;
        }

        public Object getResult() {
            return result;
        }

        public String getError() {
            return error;
        }
    }

    /**
     * Creates worker-thread bridges. Called on the worker thread; GraalJS
     * contexts are thread-confined, so the bridge (and its context) must be
     * built and used on the same thread that runs the job.
     */
    public interface WorkerBridgeFactory {
        JobJavaScriptBridge create() throws Exception;
    }

    private final ExecutorService executor;
    private final WorkerBridgeFactory bridgeFactory;

    public AsyncJobWorkerPool(int workers, WorkerBridgeFactory bridgeFactory) {
        this.bridgeFactory = bridgeFactory;
        this.executor = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "jsr-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Dispatches one job to a worker and returns its future.
     *
     * <p>There are exactly {@code workers} threads; a job starts as soon as
     * a thread is free (the executor queues the rest, FIFO). No timeouts in
     * this version: a dispatched function that never returns blocks its
     * {@code wait()} forever, exactly like a main-script infinite loop
     * would.</p>
     *
     * <p>The dispatching thread's {@link PropertyReader#getOverrides()}
     * snapshot is captured here, on the calling (main JS) thread.
     */
    public Future<AsyncJobResult> dispatch(String fnSource, String argsJson,
                                           String scriptDirectory,
                                           JSONObject parameters) {
        Map<String, String> overridesSnapshot = PropertyReader.getOverrides();
        return executor.submit(() -> {
            PropertyReader.setOverrides(overridesSnapshot);
            try {
                JobJavaScriptBridge bridge = bridgeFactory.create();
                return bridge.runAsyncJob(fnSource, argsJson, scriptDirectory, parameters);
            } catch (Exception e) {
                return AsyncJobResult.error(e.getMessage() != null ? e.getMessage() : e.toString());
            } finally {
                PropertyReader.clearOverrides();
            }
        });
    }

    /**
     * Blocks until the dispatched job completes and unwraps its envelope.
     * Worker-side failures (JS exceptions, tool errors) surface as a
     * failed envelope, never as an {@link ExecutionException}.
     */
    public AsyncJobResult await(Future<AsyncJobResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AsyncJobResult.error("runAsync wait interrupted: " + e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return AsyncJobResult.error(cause.getMessage() != null ? cause.getMessage() : cause.toString());
        }
    }

    /**
     * Stops accepting new jobs; in-flight jobs finish (daemon threads never
     * block JVM exit).
     */
    public void shutdown() {
        executor.shutdown();
    }
}
