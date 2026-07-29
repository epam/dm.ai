// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.*;

class TerminalCoverageTest {

    private static IntSupplier supplierOf(int... codes) {
        AtomicInteger idx = new AtomicInteger(0);
        return () -> {
            int i = idx.getAndIncrement();
            return i < codes.length ? codes[i] : -1;
        };
    }

    private static IntConsumer collectingInto(List<Integer> sink) {
        return sink::add;
    }

    @Test
    void createFallsBackToSystemStreamsWithoutTty() {
        // Gradle test workers have no controlling terminal, so /dev/tty and stty
        // are unavailable and create() must fall back without blocking
        Terminal terminal = Terminal.create();
        assertNotNull(terminal);
        assertFalse(terminal.isRawMode());
        terminal.close();
    }

    @Test
    void createForTestingReportsRawMode() {
        Terminal terminal = Terminal.createForTesting(supplierOf(), collectingInto(new ArrayList<>()));
        assertTrue(terminal.isRawMode());
        terminal.close();
    }

    @Test
    void publicConstructorReportsFallbackMode() {
        Terminal terminal = new Terminal(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), false);
        assertFalse(terminal.isRawMode());
    }

    @Test
    void readReturnsSupplierCharactersInRawMode() {
        Terminal terminal = Terminal.createForTesting(supplierOf('a', 'b'), collectingInto(new ArrayList<>()));
        assertEquals('a', terminal.read());
        assertEquals('b', terminal.read());
        terminal.close();
    }

    @Test
    void readReturnsMinusOneOnEof() {
        Terminal terminal = Terminal.createForTesting(supplierOf(), collectingInto(new ArrayList<>()));
        assertEquals(-1, terminal.read());
        terminal.close();
    }

    @Test
    void readReturnsMinusOneOnIoException() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        // rawMode=true attempts stty, which fails fast without a TTY in test workers
        Terminal terminal = new Terminal(failing, new ByteArrayOutputStream(), true);
        assertEquals(-1, terminal.read());
    }

    @Test
    void fallbackReadReturnsMinusOneWithoutConsole() {
        Terminal terminal = new Terminal(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), false);
        // System.console() is null under the Gradle test runner
        assertEquals(-1, terminal.read());
    }

    @Test
    void fallbackReadLineReturnsNullWithoutConsole() {
        Terminal terminal = new Terminal(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), false);
        assertNull(terminal.readLine());
    }

    @Test
    void readLineAccumulatesPrintableCharactersUntilEnter() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('h', 'e', 'l', 'l', 'o', '\n'), collectingInto(new ArrayList<>()));
        assertEquals("hello", terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineAcceptsCarriageReturnAsEnter() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('o', 'k', '\r'), collectingInto(new ArrayList<>()));
        assertEquals("ok", terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineHandlesBackspaceAndDelete() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('a', 'b', 127, 'c', 8, 'd', '\n'), collectingInto(new ArrayList<>()));
        assertEquals("ad", terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineBackspaceOnEmptyBufferIsNoOp() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf(127, 8, 'x', '\n'), collectingInto(new ArrayList<>()));
        assertEquals("x", terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineIgnoresNonPrintableCharacters() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('a', 1, 200, 'b', '\n'), collectingInto(new ArrayList<>()));
        assertEquals("ab", terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineReturnsNullOnCtrlC() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('a', 3), collectingInto(new ArrayList<>()));
        assertNull(terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineReturnsNullOnCtrlD() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('a', 4), collectingInto(new ArrayList<>()));
        assertNull(terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineReturnsEscMarkerOnEscape() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('a', 27), collectingInto(new ArrayList<>()));
        assertEquals("\u001b", terminal.readLine());
        terminal.close();
    }

    @Test
    void readLineReturnsNullOnEof() {
        Terminal terminal = Terminal.createForTesting(
                supplierOf('a', 'b', -1), collectingInto(new ArrayList<>()));
        assertNull(terminal.readLine());
        terminal.close();
    }

    @Test
    void readEscapeSequenceReadsCompleteCsiSequence() {
        Terminal terminal = new Terminal(
                new ByteArrayInputStream("[A".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), false);
        assertEquals("\u001b[A", terminal.readEscapeSequence(500));
    }

    @Test
    void readEscapeSequenceReadsSs3Sequence() {
        Terminal terminal = new Terminal(
                new ByteArrayInputStream("OP".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), false);
        // 'O' is itself a letter, so the sequence is considered complete after it
        assertEquals("\u001bO", terminal.readEscapeSequence(500));
    }

    @Test
    void readEscapeSequenceCompletesAfterSingleNonCsiByte() {
        Terminal terminal = new Terminal(
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), false);
        assertEquals("\u001bx", terminal.readEscapeSequence(500));
    }

    @Test
    void readEscapeSequenceReadsTildeTerminatedSequence() {
        Terminal terminal = new Terminal(
                new ByteArrayInputStream("[3~".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), false);
        assertEquals("\u001b[3~", terminal.readEscapeSequence(500));
    }

    @Test
    void readEscapeSequenceReturnsEscAloneOnTimeout() {
        Terminal terminal = Terminal.createForTesting(supplierOf(), collectingInto(new ArrayList<>()));
        long start = System.currentTimeMillis();
        String seq = terminal.readEscapeSequence(30);
        assertEquals("\u001b", seq);
        assertTrue(System.currentTimeMillis() - start < 5000);
        terminal.close();
    }

    @Test
    void readEscapeSequenceStopsAtEightCharacters() {
        Terminal terminal = new Terminal(
                new ByteArrayInputStream("[123456789".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), false);
        String seq = terminal.readEscapeSequence(500);
        assertEquals(8, seq.length());
        assertEquals("\u001b[123456", seq);
    }

    @Test
    void readEscapeSequenceStopsOnEofByte() {
        InputStream eofStream = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public int available() {
                return 1;
            }
        };
        Terminal terminal = new Terminal(eofStream, new ByteArrayOutputStream(), false);
        assertEquals("\u001b", terminal.readEscapeSequence(500));
    }

    @Test
    void readEscapeSequenceInterruptedSleepReturnsPartialSequence() {
        Terminal terminal = Terminal.createForTesting(supplierOf(), collectingInto(new ArrayList<>()));
        Thread.currentThread().interrupt();
        String seq = terminal.readEscapeSequence(5000);
        assertEquals("\u001b", seq);
        assertTrue(Thread.interrupted(), "interrupt flag should be restored");
        terminal.close();
    }

    @Test
    void writeAndAnsiHelpersEmitExpectedSequences() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Terminal terminal = new Terminal(new ByteArrayInputStream(new byte[0]), out, false);
        terminal.write("hi");
        terminal.clearScreen();
        terminal.moveCursor(3, 5);
        terminal.clearLine();
        terminal.enterAltScreen();
        terminal.exitAltScreen();
        assertEquals("hi\u001b[2J\u001b[H\u001b[3;5H\u001b[2K\u001b[?1049h\u001b[?1049l",
                out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void writeIgnoresIoException() {
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("boom");
            }
        };
        Terminal terminal = new Terminal(new ByteArrayInputStream(new byte[0]), failing, false);
        assertDoesNotThrow(() -> terminal.write("anything"));
    }

    @Test
    void createForTestingWritesGoToOutputConsumer() {
        List<Integer> written = new ArrayList<>();
        Terminal terminal = Terminal.createForTesting(supplierOf(), collectingInto(written));
        terminal.write("ab");
        assertEquals((int) 'a', written.get(0));
        assertEquals((int) 'b', written.get(1));
        terminal.close();
    }

    @Test
    void closeIsIdempotentForOwnedStreams() {
        Terminal terminal = Terminal.createForTesting(supplierOf(), collectingInto(new ArrayList<>()));
        terminal.close();
        assertDoesNotThrow(terminal::close);
    }

    @Test
    void closeDoesNotCloseForeignStreams() {
        AtomicInteger closeCount = new AtomicInteger(0);
        InputStream in = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                closeCount.incrementAndGet();
                super.close();
            }
        };
        OutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                closeCount.incrementAndGet();
                super.close();
            }
        };
        Terminal terminal = new Terminal(in, out, false);
        terminal.close();
        terminal.close();
        assertEquals(0, closeCount.get());
    }

    @Test
    void stackTraceContainsExceptionDetails() {
        String trace = Terminal.stackTrace(new IllegalStateException("bad state"));
        assertTrue(trace.contains("IllegalStateException"));
        assertTrue(trace.contains("bad state"));
    }
}
