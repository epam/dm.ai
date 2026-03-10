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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

        logger.debug("Running command: {}", command);
        validateNoShellInjection(command);

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

        // Merge stdout and stderr so all output is captured
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Drain stdout/stderr in real-time so the process doesn't block on a full pipe
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        logger.debug("Process exited with code: {}", exitCode);

        // Propagate non-zero exit codes so callers are not silently misled.
        if (exitCode != 0) {
            throw new IOException("Command failed (exit code " + exitCode + "): " + command + "\nOutput:\n" + output.toString().trim());
        }

        return output.toString().trim();
    }

    /**
     * Rejects commands that contain shell injection metacharacters.
     * Semicolons, newlines, backtick execution, and subshell substitution ($(...), ${...})
     * are not required by any legitimate caller and are the primary vectors for
     * injecting additional commands past a whitelist check.
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
                || command.contains("`")
                || command.contains("$(")
                || command.contains("${")) {
            throw new IllegalArgumentException(
                    "Command contains disallowed shell metacharacters (;, newline, `, $(...), ${...}): "
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
            System.err.println("Warning: Could not load environment file " + filename + ": " + e.getMessage());
        }

        return envVars;
    }
}