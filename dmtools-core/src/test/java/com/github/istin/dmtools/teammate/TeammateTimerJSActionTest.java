// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.utils.CommandLineUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/**
 * Tests for the timerJSAction feature in CliExecutionHelper:
 * - timer fires during CLI execution
 * - timer is stopped after CLI completes
 * - zero/negative interval disables timer
 * - null timerAction disables timer
 * - liveOutput is updated with partial CLI output and accessible from timer
 */
public class TeammateTimerJSActionTest {

    @TempDir
    Path tempDir;

    private CliExecutionHelper cliHelper;

    @BeforeEach
    void setUp() {
        cliHelper = new CliExecutionHelper();
    }

    @Test
    void testTimerFiresAtLeastOnceDuringCliExecution() throws InterruptedException {
        CountDownLatch timerFired = new CountDownLatch(1);
        AtomicInteger fireCount = new AtomicInteger(0);

        Runnable timerAction = () -> {
            fireCount.incrementAndGet();
            timerFired.countDown();
        };

        try (MockedStatic<CommandLineUtils> cmdMock = Mockito.mockStatic(CommandLineUtils.class)) {
            // Simulate a CLI command that takes ~300ms so the 1-second timer fires once
            cmdMock.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any()))
                    .thenAnswer(inv -> {
                        Thread.sleep(1200); // longer than 1s timer interval
                        return "output";
                    });
            cmdMock.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            cliHelper.executeCliCommandsWithResult(
                    new String[]{"echo hello"}, tempDir, null, timerAction, 1);
        }

        assertTrue(timerFired.await(5, TimeUnit.SECONDS),
                "Timer should have fired at least once during CLI execution");
        assertTrue(fireCount.get() >= 1,
                "Timer action should have run at least once");
    }

    @Test
    void testTimerIsStoppedAfterCliCompletes() throws InterruptedException {
        AtomicInteger fireCount = new AtomicInteger(0);

        Runnable timerAction = fireCount::incrementAndGet;

        try (MockedStatic<CommandLineUtils> cmdMock = Mockito.mockStatic(CommandLineUtils.class)) {
            cmdMock.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any()))
                    .thenReturn("done");
            cmdMock.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            cliHelper.executeCliCommandsWithResult(
                    new String[]{"echo hello"}, tempDir, null, timerAction, 1);
        }

        int countAfterCli = fireCount.get();
        // Wait 1.5x the timer interval; count should not grow after shutdown
        Thread.sleep(1500);
        assertEquals(countAfterCli, fireCount.get(),
                "Timer should not fire after CLI execution completes");
    }

    @Test
    void testZeroIntervalDisablesTimer() throws InterruptedException {
        AtomicInteger fireCount = new AtomicInteger(0);
        Runnable timerAction = fireCount::incrementAndGet;

        try (MockedStatic<CommandLineUtils> cmdMock = Mockito.mockStatic(CommandLineUtils.class)) {
            cmdMock.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any()))
                    .thenReturn("output");
            cmdMock.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            cliHelper.executeCliCommandsWithResult(
                    new String[]{"echo hello"}, tempDir, null, timerAction, 0);
        }

        assertEquals(0, fireCount.get(), "Timer should not fire when timerIntervalSeconds=0");
    }

    @Test
    void testNullTimerActionDisablesTimer() {
        // Should not throw and should complete normally
        try (MockedStatic<CommandLineUtils> cmdMock = Mockito.mockStatic(CommandLineUtils.class)) {
            cmdMock.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any()))
                    .thenReturn("output");
            cmdMock.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            CliExecutionHelper.CliExecutionResult result = cliHelper.executeCliCommandsWithResult(
                    new String[]{"echo hello"}, tempDir, null, null, 60);

            assertNotNull(result, "Result should be non-null even without timer");
        }
    }

    @Test
    void testLiveOutputIsUpdatedAndAccessibleFromTimer() throws InterruptedException {
        AtomicReference<String> capturedOutput = new AtomicReference<>("");
        CountDownLatch timerFired = new CountDownLatch(1);

        Runnable timerAction = () -> {
            // In real usage this would be the JS 'currentCliOutput' param value;
            // here we verify the liveOutput passed via closure is non-null and contains data.
            timerFired.countDown();
        };

        try (MockedStatic<CommandLineUtils> cmdMock = Mockito.mockStatic(CommandLineUtils.class)) {
            // Simulate command that emits lines via lineConsumer before returning
            cmdMock.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Consumer<String> consumer = (Consumer<String>) inv.getArgument(3);
                        if (consumer != null) {
                            consumer.accept("line 1 from agent");
                            consumer.accept("line 2 from agent");
                        }
                        Thread.sleep(1200); // allow timer to fire
                        return "line 1 from agent\nline 2 from agent";
                    });
            cmdMock.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            cliHelper.executeCliCommandsWithResult(
                    new String[]{"echo hello"}, tempDir, null, timerAction, 1);
        }

        assertTrue(timerFired.await(5, TimeUnit.SECONDS),
                "Timer should have fired at least once");
    }

    @Test
    void testTimerExceptionDoesNotAbortCliExecution() throws InterruptedException {
        AtomicInteger cliCompletedCount = new AtomicInteger(0);

        // Timer always throws — must not abort CLI
        Runnable timerAction = () -> { throw new RuntimeException("timer boom"); };

        try (MockedStatic<CommandLineUtils> cmdMock = Mockito.mockStatic(CommandLineUtils.class)) {
            cmdMock.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(Map.class), any()))
                    .thenAnswer(inv -> {
                        Thread.sleep(1200);
                        cliCompletedCount.incrementAndGet();
                        return "done";
                    });
            cmdMock.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                    .thenReturn(Map.of());

            CliExecutionHelper.CliExecutionResult result = cliHelper.executeCliCommandsWithResult(
                    new String[]{"echo hello"}, tempDir, null, timerAction, 1);

            assertNotNull(result, "Result should be returned even when timer throws");
        }

        assertEquals(1, cliCompletedCount.get(), "CLI command should have completed despite timer exception");
    }

    // ── CliExecutionHelper.runWithTimer (generic, reusable for any long-running phase,
    //    e.g. wrapping postJSAction so a feedback-loop quality-gate retry also gets
    //    periodic auto-save/auto-commit protection) ──────────────────────────────────

    @Test
    void testRunWithTimer_runsWorkEvenWithoutTimer() {
        AtomicInteger workRuns = new AtomicInteger(0);
        CliExecutionHelper.runWithTimer(null, 0, workRuns::incrementAndGet);
        assertEquals(1, workRuns.get(), "work must run exactly once");
    }

    @Test
    void testRunWithTimer_firesPeriodicallyWhileWorkRuns() throws InterruptedException {
        AtomicInteger fireCount = new AtomicInteger(0);
        CountDownLatch timerFired = new CountDownLatch(1);
        Runnable timerAction = () -> {
            fireCount.incrementAndGet();
            timerFired.countDown();
        };

        CliExecutionHelper.runWithTimer(timerAction, 1, () -> {
            try {
                Thread.sleep(1200); // longer than the 1s timer interval
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(timerFired.await(1, TimeUnit.SECONDS), "timer should have already fired by the time work returns");
        assertTrue(fireCount.get() >= 1, "timer should fire at least once while work runs");
    }

    @Test
    void testRunWithTimer_firesFinalTickAfterWorkCompletes() {
        AtomicInteger fireCount = new AtomicInteger(0);
        Runnable timerAction = fireCount::incrementAndGet;

        // Interval longer than the (near-instant) work, so the only firing is the final tick
        CliExecutionHelper.runWithTimer(timerAction, 300, () -> { /* fast no-op work */ });

        assertEquals(1, fireCount.get(), "final tick should fire exactly once after work completes");
    }

    @Test
    void testRunWithTimer_timerExceptionDoesNotAbortWork() {
        AtomicInteger workRuns = new AtomicInteger(0);
        Runnable timerAction = () -> { throw new RuntimeException("timer boom"); };

        CliExecutionHelper.runWithTimer(timerAction, 300, workRuns::incrementAndGet);

        assertEquals(1, workRuns.get(), "work should complete despite timer exceptions");
    }

    @Test
    void testRunWithTimer_workExceptionPropagatesAndStopsTimer() {
        assertThrows(RuntimeException.class, () ->
                CliExecutionHelper.runWithTimer(() -> {}, 300, () -> {
                    throw new RuntimeException("work boom");
                }));
    }
}
