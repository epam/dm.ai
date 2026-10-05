// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small helper for running independent Confluence requests concurrently with a bounded number of
 * threads. Results keep the order of the input list, and a failing task yields {@code null}
 * instead of aborting the others.
 */
final class ConfluenceParallel {

    static final String PARALLELISM_PROPERTY = "dmtools.confluence.parallelism";
    static final String PARALLELISM_ENV = "CONFLUENCE_PARALLELISM";
    private static final int DEFAULT_PARALLELISM = 4;
    private static final int MAX_PARALLELISM = 16;

    @FunctionalInterface
    interface Task<T, R> {
        R apply(T item) throws Exception;
    }

    private ConfluenceParallel() {
    }

    static int parallelism() {
        String value = System.getProperty(PARALLELISM_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(PARALLELISM_ENV);
        }
        if (value != null && !value.isBlank()) {
            try {
                return Math.max(1, Math.min(MAX_PARALLELISM, Integer.parseInt(value.trim())));
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return DEFAULT_PARALLELISM;
    }

    static <T, R> List<R> map(List<T> items, int threads, Task<T, R> task) {
        List<R> results = new ArrayList<>(items.size());
        if (items.isEmpty()) {
            return results;
        }
        int poolSize = Math.min(Math.max(1, threads), items.size());
        if (poolSize == 1) {
            for (T item : items) {
                results.add(run(task, item));
            }
            return results;
        }
        AtomicInteger counter = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "confluence-parallel-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<R>> futures = new ArrayList<>(items.size());
            for (T item : items) {
                futures.add(pool.submit(() -> run(task, item)));
            }
            for (Future<R> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(null);
                } catch (ExecutionException e) {
                    results.add(null);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    private static <T, R> R run(Task<T, R> task, T item) {
        try {
            return task.apply(item);
        } catch (Exception e) {
            return null;
        }
    }
}
