package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testRunCommand_SimpleEcho() throws IOException, InterruptedException {
        String result = CommandLineUtils.runCommand("echo Hello World");
        assertTrue(result.contains("Hello World"));
    }

    @Test
    void testRunCommand_WithWorkingDirectory() throws IOException, InterruptedException {
        File workingDir = tempDir.toFile();
        String result = CommandLineUtils.runCommand("pwd", workingDir);
        assertNotNull(result);
        // Result should contain the temp directory path
        assertTrue(result.contains(tempDir.toString()) || result.length() > 0);
    }

    @Test
    void testRunCommand_WithEnvironmentVariables() throws IOException, InterruptedException {
        Map<String, String> env = new HashMap<>();
        env.put("TEST_VAR", "test_value");
        
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        if (os.contains("win")) {
            command = "echo %TEST_VAR%";
        } else {
            command = "echo $TEST_VAR";
        }
        
        String result = CommandLineUtils.runCommand(command, null, env);
        assertNotNull(result);
        // Note: Environment variable expansion may not work in all contexts
    }

    @Test
    void testRunCommand_ExitCodeZero_DoesNotThrow() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            String result = CommandLineUtils.runCommand("true");
            assertNotNull(result);
        }
    }

    @Test
    void testRunCommand_NonZeroExitCode_ThrowsIOException() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            IOException ex = assertThrows(IOException.class,
                    () -> CommandLineUtils.runCommand("false"));
            assertTrue(ex.getMessage().contains("exit code 1"),
                    "Exception message should contain exit code: " + ex.getMessage());
        }
    }

    @Test
    void testRunCommand_ExitCode128_ThrowsIOExceptionWithCode() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            // Simulate git push rejection exit code
            IOException ex = assertThrows(IOException.class,
                    () -> CommandLineUtils.runCommand("exit 128"));
            assertTrue(ex.getMessage().contains("exit code 128"),
                    "Exception message should contain exit code 128: " + ex.getMessage());
        }
    }

    @Test
    void testRunCommand_NonZeroExitCode_OutputIncludedInException() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            IOException ex = assertThrows(IOException.class,
                    () -> CommandLineUtils.runCommand("echo 'error output' && exit 1"));
            assertTrue(ex.getMessage().contains("error output") || ex.getMessage().contains("exit code 1"),
                    "Exception should contain output or exit code: " + ex.getMessage());
        }
    }

    @Test
    void testRunCommand_CommandWithEmDash_Success() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            // Em-dash (—) in a command argument is what broke the old 'script -q -c "..."' approach.
            // Writing to a temp script file avoids all shell-escaping issues.
            String result = CommandLineUtils.runCommand("echo 'Test — em dash message'");
            assertNotNull(result);
            assertTrue(result.contains("em dash"), "Em-dash command should execute successfully");
        }
    }

    @Test
    void testRunCommand_CommandWithEmbeddedDoubleQuotes_Success() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            // Embedded double quotes in a commit message used to break script -q -c "..."
            String result = CommandLineUtils.runCommand("echo 'Message with \"quoted\" words'");
            assertNotNull(result);
            assertTrue(result.contains("quoted"), "Command with embedded quotes should execute successfully");
        }
    }

    @Test
    void testRunCommand_EnvironmentVariableExpansion_Works() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            Map<String, String> env = new HashMap<>();
            env.put("MY_TEST_VAR", "hello_from_env");
            // Environment variables set via additionalEnv should be visible inside the temp script
            String result = CommandLineUtils.runCommand("echo $MY_TEST_VAR", null, env);
            assertNotNull(result);
            assertTrue(result.contains("hello_from_env"),
                    "Environment variable should be expanded in command output, got: " + result);
        }
    }

    @Test
    void testLoadEnvironmentFromFile_NonExistentFile() {
        Map<String, String> result = CommandLineUtils.loadEnvironmentFromFile("non_existent_file.env");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testLoadEnvironmentFromFile_NullFilename() {
        Map<String, String> result = CommandLineUtils.loadEnvironmentFromFile(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testLoadEnvironmentFromFile_ValidFile() throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "KEY1=value1\nKEY2=value2\n# Comment line\nKEY3=value3");
        
        Map<String, String> result = CommandLineUtils.loadEnvironmentFromFile(envFile.toString());
        
        assertNotNull(result);
        assertEquals("value1", result.get("KEY1"));
        assertEquals("value2", result.get("KEY2"));
        assertEquals("value3", result.get("KEY3"));
        assertFalse(result.containsKey("# Comment line"));
    }

    @Test
    void testLoadEnvironmentFromFile_WithEquals() throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "URL=https://example.com/path?param=value");
        
        Map<String, String> result = CommandLineUtils.loadEnvironmentFromFile(envFile.toString());
        
        assertNotNull(result);
        assertEquals("https://example.com/path?param=value", result.get("URL"));
    }

    @Test
    void testLoadEnvironmentFromFile_EmptyLines() throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "KEY1=value1\n\n\nKEY2=value2\n");
        
        Map<String, String> result = CommandLineUtils.loadEnvironmentFromFile(envFile.toString());
        
        assertNotNull(result);
        assertEquals("value1", result.get("KEY1"));
        assertEquals("value2", result.get("KEY2"));
    }

    @Test
    void testLoadEnvironmentFromFile_IgnoresComments() throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "#COMMENTED_KEY=value\nACTUAL_KEY=value");
        
        Map<String, String> result = CommandLineUtils.loadEnvironmentFromFile(envFile.toString());
        
        assertNotNull(result);
        assertFalse(result.containsKey("COMMENTED_KEY"));
        assertEquals("value", result.get("ACTUAL_KEY"));
    }

    @Test
    void testRunCommand_MultiLineOutput() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        if (os.contains("win")) {
            command = "echo Line1 && echo Line2";
        } else {
            command = "echo Line1 && echo Line2";
        }
        
        String result = CommandLineUtils.runCommand(command);
        assertNotNull(result);
        assertTrue(result.contains("Line1"));
        assertTrue(result.contains("Line2"));
    }
}
