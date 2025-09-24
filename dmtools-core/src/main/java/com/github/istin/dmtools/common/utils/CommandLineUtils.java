package com.github.istin.dmtools.common.utils;

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

        System.out.println("LOG: Running command: " + command);

        // Create a unique temporary file to capture output
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        File tempOutput = File.createTempFile("cmd_output_" + timestamp + "_" + uniqueId + "_", ".txt");
        tempOutput.deleteOnExit();

        System.out.println("LOG: Temporary output file: " + tempOutput.getAbsolutePath());

        // Detect OS and use appropriate approach
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder processBuilder;

        if (os.contains("win")) {
            // Windows - use cmd.exe directly
            System.out.println("LOG: Windows detected - using cmd.exe");
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else if (os.contains("mac")) {
            // macOS - use script command for TTY support
            String scriptCommand = String.format("script -q %s %s", tempOutput.getAbsolutePath(), command);
            processBuilder = new ProcessBuilder("/bin/sh", "-c", scriptCommand);
        } else {
            // Linux - check if script command supports -c flag, otherwise use direct execution
            try {
                // Test if script command supports -c flag
                Process testProcess = new ProcessBuilder("script", "--help").start();
                testProcess.waitFor();
                
                // Use script command if available
                String scriptCommand = String.format("script -q -c \"%s\" %s", command, tempOutput.getAbsolutePath());
                processBuilder = new ProcessBuilder("/bin/sh", "-c", scriptCommand);
            } catch (Exception e) {
                // Fallback to direct execution without script command
                System.out.println("LOG: script command not available, using direct execution");
                processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            }
        }

        // Set working directory if provided
        if (workingDirectory != null && workingDirectory.exists() && workingDirectory.isDirectory()) {
            processBuilder.directory(workingDirectory);
        }

        // Add additional environment variables
        if (additionalEnv != null && !additionalEnv.isEmpty()) {
            processBuilder.environment().putAll(additionalEnv);
        }

        // Redirect error stream to output stream
        processBuilder.redirectErrorStream(true);

        // Start the process
        Process process = processBuilder.start();

        // Read output in real-time for Windows or if script fails
        StringBuilder directOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                directOutput.append(line).append(System.lineSeparator());
            }
        }

        // Wait for process to complete
        int exitCode = process.waitFor();
        System.out.println("LOG: Process exited with code: " + exitCode);

        // For Unix/Mac, read the captured output from temp file
        if (!os.contains("win") && tempOutput.exists()) {
            try {
                String capturedOutput = new String(Files.readAllBytes(tempOutput.toPath()));

                // Clean up ANSI escape codes
                capturedOutput = capturedOutput.replaceAll("\u001B\\[[;\\d]*m", "");
                capturedOutput = capturedOutput.replaceAll("\r", "");

                // Delete temp file
                tempOutput.delete();

                return capturedOutput.trim();
            } catch (IOException e) {
                System.err.println("LOG: Failed to read temp file, using direct output");
            }
        }

        // For Windows or if temp file reading fails, return direct output
        return directOutput.toString().trim();
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