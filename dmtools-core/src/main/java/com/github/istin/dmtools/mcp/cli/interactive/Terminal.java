// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Low-level terminal handling for the interactive CLI.
 *
 * <p>On Unix-like systems the terminal is switched to raw mode using {@code stty}
 * so that single keystrokes (arrows, Esc, Enter, Backspace) can be read without
 * waiting for Enter.  On unsupported platforms the class falls back to plain
 * {@code System.console()} input.</p>
 *
 * <p>The class is intentionally small and free of external dependencies so it
 * can be exercised in unit tests with {@link #createForTesting(java.util.function.IntSupplier,
 * java.util.function.IntConsumer)}.</p>
 */
public class Terminal implements AutoCloseable {

    private final InputStream in;
    private final OutputStream out;
    private final boolean rawMode;
    private final boolean ownsStreams;

    private boolean closed = false;
    private String originalStty;

    public Terminal(InputStream in, OutputStream out, boolean rawMode) {
        this.in = in;
        this.out = out;
        this.rawMode = rawMode;
        this.ownsStreams = false;
        if (rawMode) {
            enableRawMode();
        }
    }

    private Terminal(InputStream in, OutputStream out, boolean rawMode, boolean ownsStreams) {
        this.in = in;
        this.out = out;
        this.rawMode = rawMode;
        this.ownsStreams = ownsStreams;
    }

    /**
     * Creates a terminal backed by the real stdin/stdout.  Raw mode is enabled
     * when stdin is a TTY and the platform supports {@code stty}.  The interactive
     * UI is written to {@code /dev/tty} when possible so that the final command
     * printed to stdout can be captured by the wrapper script.
     */
    public static Terminal create() {
        InputStream in;
        OutputStream out;
        boolean ttyAvailable = false;
        try {
            in = new java.io.FileInputStream("/dev/tty");
            ttyAvailable = true;
        } catch (Exception e) {
            in = System.in;
        }
        try {
            out = new java.io.FileOutputStream("/dev/tty");
        } catch (Exception e) {
            out = System.out;
        }
        boolean useRaw = ttyAvailable && isSttyAvailable();
        return new Terminal(in, out, useRaw);
    }

    /**
     * Creates a test terminal that reads characters from {@code input} and writes
     * character codes to {@code output}.  Raw mode is {@code true} but no stty
     * calls are performed.
     */
    public static Terminal createForTesting(java.util.function.IntSupplier input,
                                            java.util.function.IntConsumer output) {
        return new Terminal(
                new InputStream() {
                    @Override
                    public int read() {
                        return input.getAsInt();
                    }
                },
                new OutputStream() {
                    @Override
                    public void write(int b) {
                        output.accept(b);
                    }
                },
                true,
                true
        );
    }

    public boolean isRawMode() {
        return rawMode;
    }

    /**
     * Reads the next character.  In raw mode this returns immediately for a single
     * keystroke; in fallback mode it reads a line and yields characters from it.
     *
     * @return the character code, or -1 on EOF/error
     */
    public int read() {
        if (rawMode) {
            try {
                return in.read();
            } catch (IOException e) {
                return -1;
            }
        }
        return readFromConsole();
    }

    private String consoleBuffer = null;
    private int consoleIndex = 0;

    private synchronized int readFromConsole() {
        if (consoleBuffer != null && consoleIndex < consoleBuffer.length()) {
            return consoleBuffer.charAt(consoleIndex++);
        }
        java.io.Console console = System.console();
        if (console == null) {
            return -1;
        }
        String line = console.readLine();
        if (line == null) {
            return -1;
        }
        consoleBuffer = line + "\n";
        consoleIndex = 0;
        return readFromConsole();
    }

    /**
     * Reads an ANSI escape sequence that has already started with ESC (0x1b).
     *
     * @param timeoutMs maximum time to wait for continuation bytes
     * @return the full sequence including the leading ESC
     */
    public String readEscapeSequence(int timeoutMs) {
        StringBuilder seq = new StringBuilder();
        seq.append((char) 0x1b);
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (System.currentTimeMillis() < deadline && seq.length() < 8) {
                if (in.available() > 0) {
                    int c = in.read();
                    if (c < 0) {
                        break;
                    }
                    seq.append((char) c);
                    if (isCompleteEscapeSequence(seq.toString())) {
                        break;
                    }
                } else {
                    Thread.sleep(5);
                }
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return seq.toString();
    }

    /**
     * Reads a line of text.  In raw mode this accumulates characters until Enter
     * is pressed; in fallback mode it delegates to the console.
     *
     * @return the line without the trailing newline, or {@code null} on EOF/Esc
     */
    public String readLine() {
        if (!rawMode) {
            return readConsoleLine();
        }
        StringBuilder sb = new StringBuilder();
        while (true) {
            int code = read();
            if (code < 0) {
                return null;
            }
            if (code == 10 || code == 13) {
                return sb.toString();
            }
            if (code == 127 || code == 8) {
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
            } else if (code == 3 || code == 4) { // Ctrl-C / Ctrl-D
                return null;
            } else if (code == 27) { // Esc
                return "\u001b";
            } else if (code >= 32 && code <= 126) {
                sb.append((char) code);
            }
        }
    }

    private String readConsoleLine() {
        java.io.Console console = System.console();
        if (console == null) {
            return null;
        }
        String line = console.readLine();
        if (line == null) {
            return null;
        }
        if (line.startsWith("\u001b")) {
            return line;
        }
        return line;
    }
    public void write(String s) {
        try {
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Clears the screen using ANSI escape codes.
     */
    public void clearScreen() {
        write("\u001b[2J\u001b[H");
    }

    /**
     * Moves the cursor to the given 1-based row and column.
     */
    public void moveCursor(int row, int col) {
        write("\u001b[" + row + ";" + col + "H");
    }

    /**
     * Clears the current line.
     */
    public void clearLine() {
        write("\u001b[2K");
    }

    /**
     * Enables the alternate screen buffer.
     */
    public void enterAltScreen() {
        write("\u001b[?1049h");
    }

    /**
     * Restores the normal screen buffer.
     */
    public void exitAltScreen() {
        write("\u001b[?1049l");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (rawMode) {
            disableRawMode();
        }
        if (ownsStreams) {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
            try {
                out.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private static boolean isSttyAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("stty");
            // stty needs a TTY for input; discard output so it is not captured by the wrapper
            pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/tty")));
            pb.redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")));
            pb.redirectError(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")));
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void enableRawMode() {
        if (!isSttyAvailable()) {
            return;
        }
        try {
            originalStty = execStty("-g");
            // -icanon: non-canonical mode, -echo: do not echo, min 1 time 0: read at least 1 char, no timeout
            execStty("-icanon", "-echo", "min", "1", "time", "0");
        } catch (Exception e) {
            originalStty = null;
        }
    }

    private void disableRawMode() {
        if (originalStty == null || originalStty.isEmpty()) {
            return;
        }
        try {
            execStty(originalStty);
        } catch (Exception e) {
            // Last resort: try to restore a sane state
            try {
                execStty("sane");
            } catch (Exception ignored) {
            }
        }
    }

    private String execStty(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("stty");
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        // stty needs a TTY; redirecting stdin from /dev/tty is the standard trick
        pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/tty")));
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = pb.start();
        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        process.waitFor();
        return output;
    }

    private static boolean isCompleteEscapeSequence(String seq) {
        if (seq.length() < 2) {
            return false;
        }
        char second = seq.charAt(1);
        // Single byte after ESC (e.g. Esc alone)
        if (second != '[' && second != 'O') {
            return true;
        }
        // CSI/O sequences end with a letter or ~
        char last = seq.charAt(seq.length() - 1);
        return (last >= 'A' && last <= 'Z')
                || (last >= 'a' && last <= 'z')
                || last == '~';
    }

    /**
     * Returns the stack trace of the given throwable as a string.
     */
    public static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
