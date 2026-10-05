// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IOUtilsDeleteRecursivelyTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesNestedTree() throws Exception {
        Path root = tempDir.resolve("cache");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/f.txt"), "x");
        Files.writeString(root.resolve("a/g.txt"), "y");

        IOUtils.deleteRecursively(root.toFile());

        assertFalse(Files.exists(root));
    }

    @Test
    void ignoresMissingAndNullPaths() throws Exception {
        IOUtils.deleteRecursively((File) null);
        IOUtils.deleteRecursively(tempDir.resolve("does-not-exist"));
    }

    /**
     * Regression for the parallel-fork flake (ConfluenceTest): two clients share the same
     * "cache<ClassName>" folder, one deletes it while the other deletes/creates entries in it.
     * Neither deleter may fail and the folder must be gone once both finished with no writer left.
     */
    @Test
    void concurrentDeletersAndWritersDoNotFail() throws Exception {
        for (int round = 0; round < 40; round++) {
            Path root = tempDir.resolve("shared" + round);
            Files.createDirectories(root);
            for (int i = 0; i < 60; i++) {
                Files.writeString(root.resolve("f" + i), "x");
            }
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(4);
            for (int t = 0; t < 2; t++) {
                pool.submit(() -> {
                    try {
                        go.await();
                        IOUtils.deleteRecursively(root);
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    }
                });
            }
            pool.submit(() -> {
                try {
                    go.await();
                    for (int i = 0; i < 60; i++) {
                        Files.writeString(root.resolve("late" + i), "y");
                    }
                } catch (java.io.IOException ignored) {
                    // the folder may be deleted under the writer — that is the point
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
            assertNull(failure.get(), () -> "deleteRecursively failed under concurrency: " + failure.get());
        }
    }
}
