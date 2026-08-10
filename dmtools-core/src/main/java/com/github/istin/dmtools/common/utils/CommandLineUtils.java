// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CommandLineUtils {

    private static final Logger logger = LogManager.getLogger(CommandLineUtils.class);

    /**
     * Runs a command-line command and returns the output as a string.
     * Uses script command on Unix/Mac to create a pseudo-terminal for TTY-dependent commands.
     *
     * @param command The command to run
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted while waiting for the command to complete
     */
    public static String runCommand(String command) throws IOException, InterruptedException {
        return runCommand(command, null, new HashMap<>());
    }

    /**
     * Runs a command-line command in the specified working directory.
     * Uses script command on Unix/Mac to create a pseudo-terminal for TTY-dependent commands.
     *
     * @param command The command to run
     * @param workingDirectory The working directory for the command (null to use current directory)
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted while waiting for the command to complete
     */
    public static String runCommand(String command, File workingDirectory) throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, new HashMap<>());
    }

    /**
     * Runs a command-line command with full options.
     * Uses script command on Unix/Mac to create a pseudo-terminal for TTY-dependent commands.
     *
     * @param command The command to run
     * @param workingDirectory The working directory for the command (null to use current directory)
     * @param additionalEnv Additional environment variables to set
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted
     */
    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv)
            throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, additionalEnv, null, true, null, -1);
    }

    /**
     * Runs a command-line command with full options and an optional per-line output consumer.
     * Logs every output line at INFO by default (maxLinesToLog = -1, i.e. no cap) — use this
     * overload for callers where the live output IS the thing operators want to see (e.g. an
     * interactive CLI agent session such as run-agent.sh). For callers whose output is often
     * large/noisy raw command output (e.g. a `git diff` or `mvn` invocation from a JS tool
     * call), use {@link #runCommand(String, File, Map, Consumer, int)} with a small cap instead.
     *
     * @param command The command to run
     * @param workingDirectory The working directory for the command (null to use current directory)
     * @param additionalEnv Additional environment variables to set
     * @param lineConsumer Optional consumer called for each output line as it is produced (null to skip)
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted
     */
    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv,
                                    Consumer<String> lineConsumer)
            throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, additionalEnv, lineConsumer, true, null, -1);
    }

    /**
     * Runs a command-line command with full options, an optional per-line output consumer, and
     * a cap on how many output lines get logged at INFO level by default.
     *
     * @param command The command to run
     * @param workingDirectory The working directory for the command (null to use current directory)
     * @param additionalEnv Additional environment variables to set
     * @param lineConsumer Optional consumer called for each output line as it is produced (null to skip)
     * @param maxLinesToLog Caps how many output lines are logged at INFO level when
     *                      DMTOOLS_JS_LOG_TOOL_CALLS is not enabled. Use -1 for "no cap - always
     *                      log every line" (e.g. an interactive CLI agent session whose live
     *                      output is the point). Use a small positive value (e.g. 50) for
     *                      callers whose output is often large/noisy raw command output (e.g.
     *                      `git diff`, `mvn`) where only a preview is useful by default; the
     *                      full output is always still returned/available to the caller and to
     *                      DMTOOLS_JS_LOG_TOOL_CALLS=true regardless of this cap.
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted
     */
    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv,
                                    Consumer<String> lineConsumer, int maxLinesToLog)
            throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, additionalEnv, lineConsumer, true, null, maxLinesToLog);
    }

    /**
     * Runs a command-line command with full options, optional per-line output consumer,
     * and optional shell-injection validation bypass.
     *
     * @param command The command to run
     * @param workingDirectory The working directory for the command (null to use current directory)
     * @param additionalEnv Additional environment variables to set
     * @param lineConsumer Optional consumer called for each output line as it is produced (null to skip)
     * @param validateCommand When {@code false}, skips {@link #validateNoShellInjection(String)}.
     *                        Use only for trusted job configs where arbitrary shell syntax is required.
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted
     */
    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv,
                                    Consumer<String> lineConsumer, boolean validateCommand)
            throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, additionalEnv, lineConsumer, validateCommand, null, -1);
    }

    /**
     * Runs a command-line command with full options, optional per-line output consumer,
     * optional shell-injection validation bypass, and an optional per-line stop predicate.
     *
     * @param command           The command to run
     * @param workingDirectory  The working directory for the command (null to use current directory)
     * @param additionalEnv     Additional environment variables to set
     * @param lineConsumer      Optional consumer called for each output line as it is produced (null to skip)
     * @param validateCommand   When {@code false}, skips {@link #validateNoShellInjection(String)}.
     * @param lineStopPredicate Optional predicate called for each output line; returning {@code true} stops
     *                          the command and throws {@link CliExecutionStoppedException}.
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted
     */
    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv,
                                    Consumer<String> lineConsumer, boolean validateCommand,
                                    Predicate<String> lineStopPredicate)
            throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, additionalEnv, lineConsumer, validateCommand, lineStopPredicate, -1);
    }

    /**
     * Runs a command-line command with full options, optional per-line output consumer,
     * optional shell-injection validation bypass, optional per-line stop predicate, and
     * a cap on how many output lines get logged at INFO level by default.
     *
     * @param command           The command to run
     * @param workingDirectory  The working directory for the command (null to use current directory)
     * @param additionalEnv     Additional environment variables to set
     * @param lineConsumer      Optional consumer called for each output line as it is produced (null to skip)
     * @param validateCommand   When {@code false}, skips {@link #validateNoShellInjection(String)}.
     * @param lineStopPredicate Optional predicate called for each output line; returning {@code true} stops
     *                          the command and throws {@link CliExecutionStoppedException}.
     * @param maxLinesToLog     Caps how many output lines are logged at INFO level when
     *                          DMTOOLS_JS_LOG_TOOL_CALLS is not enabled. Use -1 for no cap.
     * @return The output of the command as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the current thread is interrupted
     */
    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv,
                                    Consumer<String> lineConsumer, boolean validateCommand,
                                    Predicate<String> lineStopPredicate, int maxLinesToLog)
            throws IOException, InterruptedException {
        return runCommand(command, workingDirectory, additionalEnv, lineConsumer, validateCommand,
                lineStopPredicate, maxLinesToLog, null, null);
    }

    public static String runCommand(String command, File workingDirectory, Map<String, String> additionalEnv,
                                    Consumer<String> lineConsumer, boolean validateCommand,
                                    Predicate<String> lineStopPredicate, int maxLinesToLog,
                                    String[] excludedEnvVariables, String[] excludedEnvRegexes)
            throws IOException, InterruptedException {

        logger.debug("Running command: {}", SecurityUtils.maskCommand(command));
        if (validateCommand) {
            validateNoShellInjection(command);
        } else {
            logger.info("Skipping shell-injection validation for command: {}", SecurityUtils.maskCommand(command));
        }

        // Write command to a temp shell script to avoid shell-escaping issues with
        // special characters (em-dash, embedded quotes, etc.) in the command string.
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder processBuilder;

        if (os.contains("win")) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            File tempScript = writeTempScript(command, timestamp, uniqueId);
            // Run the script directly — no 'script' TTY wrapper needed.
            // This guarantees the real exit code is always propagated correctly
            // (Linux 'script' utility is known to return 0 regardless of child exit code
            // on older util-linux versions used in many CI runners).
            processBuilder = new ProcessBuilder("/bin/sh", tempScript.getAbsolutePath());
        }

        // Set working directory if provided
        if (workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory()) {
            processBuilder.directory(workingDirectory);
        }

        // Merge additional environment variables
        if (additionalEnv != null && !additionalEnv.isEmpty()) {
            processBuilder.environment().putAll(additionalEnv);
        }
        removeExcludedEnvironmentVariables(
                processBuilder.environment(), excludedEnvVariables, excludedEnvRegexes);

        // Merge stdout and stderr so all output is captured
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Drain stdout/stderr in real-time so the process doesn't block on a full pipe
        boolean verboseCommandOutputLogging = new PropertyReader().isJsToolCallLoggingEnabled();
        // Path a full, untruncated transcript would be written to IF this command's
        // output ends up exceeding the logging cap (computed upfront, from the same
        // timestamp/uniqueId used for the temp script, so the truncation notice can
        // point at it immediately instead of only after the fact).
        Path fullOutputLogFile = resolveFullOutputLogFile(timestamp, uniqueId);
        StringBuilder output = new StringBuilder();
        int lineCount = 0;
        int loggedLineCount = 0;
        boolean truncationNoticeLogged = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                // Logging every output line unconditionally at INFO can flood CI logs for
                // commands with large output (e.g. `git diff`, `mvn test`, `curl` responses).
                // maxLinesToLog < 0 means "no cap" (always log, e.g. an interactive CLI agent
                // session); otherwise once DMTOOLS_JS_LOG_TOOL_CALLS is not set, only the first
                // maxLinesToLog lines are logged and a single truncation notice follows.
                boolean withinCap = maxLinesToLog < 0 || loggedLineCount < maxLinesToLog;
                if (verboseCommandOutputLogging || withinCap) {
                    logger.info(line);
                    loggedLineCount++;
                } else if (!truncationNoticeLogged) {
                    if (fullOutputLogFile != null) {
                        logger.info("... output truncated after {} line(s); full output will be saved to {} ...",
                                maxLinesToLog, fullOutputLogFile);
                    } else {
                        logger.info("... output truncated after {} line(s); set DMTOOLS_JS_LOG_TOOL_CALLS=true to log full output ...", maxLinesToLog);
                    }
                    truncationNoticeLogged = true;
                }

                output.append(line).append(System.lineSeparator());
                if (lineConsumer != null) {
                    try {
                        lineConsumer.accept(line);
                    } catch (Exception e) {
                        logger.warn("lineConsumer threw exception on line, ignoring: {}", e.getMessage());
                    }
                }
                if (lineStopPredicate != null && lineStopPredicate.test(line)) {
                    logger.warn("Line stop predicate returned true on line, terminating process: {}", line);
                    process.destroyForcibly();
                    throw new CliExecutionStoppedException(
                            "CLI execution stopped by line callback at line: " + line, line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (!verboseCommandOutputLogging) {
            logger.debug("Command produced {} line(s) of output ({} chars), {} logged; set DMTOOLS_JS_LOG_TOOL_CALLS=true to log full output",
                    lineCount, output.length(), loggedLineCount);
        }
        logger.debug("Process exited with code: {}", exitCode);

        // Persist the complete transcript whenever the console logging was actually
        // capped, regardless of exit code, so a failing/CI run is never left blind
        // even before the exception below is thrown/handled by the caller.
        if (truncationNoticeLogged && fullOutputLogFile != null) {
            persistFullOutput(fullOutputLogFile, command, workingDirectory, output.toString(), lineCount, exitCode);
        }

        // Propagate non-zero exit codes so callers are not silently misled.
        if (exitCode != 0) {
            throw new IOException("Command failed (exit code " + exitCode + "): " + command + "\nOutput:\n" + output.toString().trim());
        }

        return output.toString().trim();
    }

    /**
     * Resolves (without creating) the path a full command-output transcript would be
     * written to, or null if full-output persistence is disabled
     * (DMTOOLS_CLI_LOG_DIR set to an empty string).
     */
    private static Path resolveFullOutputLogFile(String timestamp, String uniqueId) {
        String logDirProperty = new PropertyReader().getCliFullOutputLogDir();
        if (logDirProperty == null || logDirProperty.isBlank()) {
            return null;
        }
        return Paths.get(logDirProperty).resolve(timestamp + "-" + uniqueId + ".log");
    }

    /**
     * Writes the complete, untruncated output of a command to disk. Only called
     * when the console logging was actually capped (see maxLinesToLog), so trivial
     * commands whose full output already fit on the console never produce a file.
     * Best-effort: failures to write are logged and swallowed, never propagated,
     * so a read-only filesystem or full disk can't turn a successful command into
     * a failure.
     */
    private static void persistFullOutput(Path logFile, String command, File workingDirectory,
                                           String fullOutput, int lineCount, int exitCode) {
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String header = "Command: " + SecurityUtils.maskCommand(command) + System.lineSeparator()
                    + "Working directory: " + (workingDirectory != null ? workingDirectory.getAbsolutePath() : System.getProperty("user.dir")) + System.lineSeparator()
                    + "Exit code: " + exitCode + System.lineSeparator()
                    + "Total output lines: " + lineCount + System.lineSeparator()
                    + "----" + System.lineSeparator();
            Files.writeString(logFile, header + fullOutput);
            logger.info("Full output ({} line(s)) written to: {}", lineCount, logFile.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to persist full command output to {}: {}", logFile, e.getMessage());
        }
    }

    static void removeExcludedEnvironmentVariables(Map<String, String> environment,
                                                   String[] excludedEnvVariables,
                                                   String[] excludedEnvRegexes) {
        if (environment == null || environment.isEmpty()) {
            return;
        }
        if (excludedEnvVariables != null) {
            for (String name : excludedEnvVariables) {
                if (name != null && !name.isBlank()) {
                    environment.remove(name);
                }
            }
        }
        List<Pattern> patterns = new ArrayList<>();
        if (excludedEnvRegexes != null) {
            for (String regex : excludedEnvRegexes) {
                if (regex != null && !regex.isBlank()) {
                    patterns.add(Pattern.compile(regex));
                }
            }
        }
        if (!patterns.isEmpty()) {
            environment.keySet().removeIf(name ->
                    patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches()));
        }
    }

    /**
     * Rejects commands that contain shell injection metacharacters.
     *
     * <p>Blocked characters/sequences prevent an attacker from chaining additional
     * commands past the whitelist check (e.g. {@code git status && evil}) or
     * redirecting output to sensitive files (e.g. {@code git log > /etc/cron.d/x}).
     *
     * @param command the raw command string to validate
     * @throws IllegalArgumentException if the command contains dangerous shell metacharacters
     */
    static void validateNoShellInjection(String command) {
        if (command == null) {
            return;
        }
        if (command.contains(";")
                || command.contains("\n")
                || command.contains("\r")
                || command.contains("`")
                || command.contains("$(")
                || command.contains("${")
                || command.contains("&&")
                || command.contains("||")
                || command.contains("|")
                || command.contains(">")
                || command.contains("<")) {
            throw new IllegalArgumentException(
                    "Command contains disallowed shell metacharacters "
                    + "(;, newline, \\r, `, $(...), ${...}, &&, ||, |, >, <): "
                    + SecurityUtils.maskSensitiveValue(command.substring(0, Math.min(30, command.length()))));
        }
    }

    /**
     * Writes the given shell command to a temporary executable script file.
     * Using a file avoids all shell-quoting/escaping issues that arise when
     * commands contain special characters (em-dash, embedded quotes, etc.).
     */
    private static File writeTempScript(String command, String timestamp, String uniqueId) throws IOException {
        File tempScript = File.createTempFile("cmd_script_" + timestamp + "_" + uniqueId + "_", ".sh");
        tempScript.deleteOnExit();
        Files.write(tempScript.toPath(), ("#!/bin/sh\n" + command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        tempScript.setExecutable(true);
        return tempScript;
    }

    /**
     * Loads environment variables from the specified file if it exists.
     *
     * @param filename The environment file to load
     * @return Map of environment variables loaded from the file
     */
    public static Map<String, String> loadEnvironmentFromFile(String filename) {
        Map<String, String> envVars = new HashMap<>();

        if (filename == null) {
            return envVars;
        }

        Path envFile = Paths.get(filename);

        if (!Files.exists(envFile)) {
            return envVars; // Return empty map if file doesn't exist
        }

        try {
            Files.lines(envFile)
                    .filter(line -> !line.trim().startsWith("#") && line.contains("="))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            envVars.put(key, value);
                        }
                    });
        } catch (IOException e) {
            // Log error but don't fail - just return empty map
            logger.error("Warning: Could not load environment file " + filename + ": " + e.getMessage());
        }

        return envVars;
    }
}