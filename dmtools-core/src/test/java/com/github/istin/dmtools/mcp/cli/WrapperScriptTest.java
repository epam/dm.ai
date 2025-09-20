package com.github.istin.dmtools.mcp.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the dmtools_mcp.sh wrapper script.
 * These tests verify the script functionality on Unix-like systems.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class WrapperScriptTest {

    private static final String[] POSSIBLE_SCRIPT_PATHS = {
        "../dmtools_mcp.sh",           // From dmtools-core subdirectory
        "dmtools_mcp.sh",              // From root directory
        "../../dmtools_mcp.sh",        // From deeper nested directory
        "./dmtools_mcp.sh"             // Current directory
    };

    /**
     * Find the wrapper script at one of the possible locations.
     * @return the path to the script, or null if not found
     */
    private String findScriptPath() {
        for (String path : POSSIBLE_SCRIPT_PATHS) {
            File candidate = new File(path);
            if (candidate.exists()) {
                return path;
            }
        }
        return null;
    }

    @Test
    @DisplayName("Should verify wrapper script exists and is executable")
    void testScriptExistsAndExecutable() {
        File script = null;
        for (String path : POSSIBLE_SCRIPT_PATHS) {
            File candidate = new File(path);
            if (candidate.exists()) {
                script = candidate;
                break;
            }
        }
        
        assertNotNull(script, "Wrapper script should exist at one of the expected locations: " + 
                     String.join(", ", POSSIBLE_SCRIPT_PATHS));
        assertTrue(script.exists(), "Wrapper script should exist");
        assertTrue(script.canExecute(), "Wrapper script should be executable");
    }

    @Test
    @DisplayName("Should show help when called without arguments")
    void testScriptHelp() throws Exception {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            // Skip test if script doesn't exist (e.g., in CI environment)
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(scriptPath);
        pb.directory(new File("."));
        Process process = pb.start();

        // Close stdin to prevent waiting for input
        process.getOutputStream().close();

        // Use timeout to prevent hanging
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            fail("Script took too long to show help (>10 seconds)");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        String outputStr = output.toString();

        // Should show usage information
        assertTrue(outputStr.contains("Usage:") || outputStr.contains("DMTools MCP CLI"));
    }

    @Test
    @DisplayName("Should handle help command")
    void testScriptHelpCommand() throws Exception {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(scriptPath, "help");
        pb.directory(new File("."));
        Process process = pb.start();

        // Close stdin to prevent waiting for input
        process.getOutputStream().close();

        // Use timeout to prevent hanging
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            fail("Script took too long to show help (>10 seconds)");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        String outputStr = output.toString();

        assertTrue(outputStr.contains("Usage:") || outputStr.contains("DMTools MCP CLI"));
    }

    @Test
    @DisplayName("Should handle list command if JAR exists")
    void testScriptListCommand() throws Exception {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            return;
        }

        // Check if JAR file exists
        String jarPath = "dmtools-core/build/libs/dmtools-core-1.7.16.jar";
        if (!Files.exists(Paths.get(jarPath))) {
            // Skip test if JAR doesn't exist (not built yet)
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(scriptPath, "list");
        pb.directory(new File("."));
        Process process = pb.start();

        // Close stdin to prevent waiting for input
        process.getOutputStream().close();

        // Use timeout to prevent hanging
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            fail("Script took too long to list tools (>15 seconds)");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        
        while ((line = errorReader.readLine()) != null) {
            errorOutput.append(line).append("\n");
        }

        int exitCode = process.exitValue();
        String outputStr = output.toString();
        String errorStr = errorOutput.toString();

        // Should either succeed or fail gracefully with meaningful error
        if (exitCode == 0) {
            // If successful, should contain JSON or tools information
            assertTrue(outputStr.contains("{") || outputStr.contains("tools") || 
                      errorStr.contains("Info:"));
        } else {
            // If failed, should contain meaningful error message
            assertTrue(errorStr.contains("Error:") || outputStr.contains("error") ||
                      errorStr.contains("Failed") || outputStr.contains("Failed"));
        }
    }

    @Test
    @DisplayName("Should validate script syntax")
    void testScriptSyntax() throws Exception {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            return;
        }

        // Use bash -n to check syntax without executing
        ProcessBuilder pb = new ProcessBuilder("bash", "-n", scriptPath);
        Process process = pb.start();

        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder errorOutput = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            errorOutput.append(line).append("\n");
        }

        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "Script should have valid bash syntax. Errors: " + errorOutput.toString());
    }

    @Test
    @DisplayName("Should handle invalid commands gracefully")
    void testScriptInvalidCommand() throws Exception {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(scriptPath, "invalid_command_that_does_not_exist");
        pb.directory(new File("."));
        Process process = pb.start();

        // Close stdin to prevent the script from waiting for input
        process.getOutputStream().close();

        // Use timeout to prevent hanging
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            fail("Script took too long to handle invalid command (>10 seconds)");
        }

        int exitCode = process.exitValue();

        // Should handle invalid commands gracefully (not crash)
        // Exit code might be non-zero, but should not be a shell error (127 or 126)
        assertTrue(exitCode >= 0 && exitCode != 127 && exitCode != 126, 
                  "Script should handle invalid commands gracefully, got exit code: " + exitCode);
    }

    @Test
    @DisplayName("Should contain proper shebang and error handling")
    void testScriptContent() throws Exception {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            return;
        }

        String content = Files.readString(Paths.get(scriptPath));
        
        // Should have proper shebang
        assertTrue(content.startsWith("#!/bin/bash"), "Script should have proper bash shebang");
        
        // Should have error handling
        assertTrue(content.contains("set -e"), "Script should have error handling with 'set -e'");
        
        // Should have usage function
        assertTrue(content.contains("usage()"), "Script should have usage function");
        
        // Should handle STDIN
        assertTrue(content.contains("STDIN"), "Script should handle STDIN input");
        
        // Should handle different argument types
        assertTrue(content.contains("--data"), "Script should handle --data arguments");
        assertTrue(content.contains("--file"), "Script should handle --file arguments");
    }
}


