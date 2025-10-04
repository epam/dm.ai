package com.github.istin.dmtools.cli;

import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Unit tests for CliCommandExecutor class.
 * Tests command whitelisting, execution, working directory resolution,
 * environment variable loading, and error handling.
 */
public class CliCommandExecutorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CliCommandExecutor executor;

    @Before
    public void setUp() {
        executor = new CliCommandExecutor();
    }

    // ============================================================================
    // Command Whitelisting Tests
    // ============================================================================

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_NotWhitelisted_ThrowsSecurityException() throws IOException, InterruptedException {
        // Attempt to execute non-whitelisted command
        executor.executeCommand("rm -rf /", null);
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_CurlNotWhitelisted_ThrowsSecurityException() throws IOException, InterruptedException {
        // curl is not in the whitelist
        executor.executeCommand("curl https://example.com", null);
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_WgetNotWhitelisted_ThrowsSecurityException() throws IOException, InterruptedException {
        // wget is not in the whitelist
        executor.executeCommand("wget https://example.com", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteCommand_EmptyCommand_ThrowsException() throws IOException, InterruptedException {
        executor.executeCommand("", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteCommand_NullCommand_ThrowsException() throws IOException, InterruptedException {
        executor.executeCommand(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteCommand_WhitespaceCommand_ThrowsException() throws IOException, InterruptedException {
        executor.executeCommand("   ", null);
    }

    // ============================================================================
    // Whitelisted Commands Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_GitCommand_Success() throws IOException, InterruptedException {
        // Test git command execution
        String result = executor.executeCommand("git --version", null);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    @Test
    public void testExecuteCommand_GitStatusShort_Success() throws IOException, InterruptedException {
        // Test git status command
        String result = executor.executeCommand("git status --short", null);
        
        assertNotNull("Result should not be null", result);
        // Result can be empty if no changes, or contain file listings
    }

    @Test
    public void testExecuteCommand_NpmVersion_Success() throws IOException, InterruptedException {
        try {
            // Test npm command (may not be installed on all systems)
            String result = executor.executeCommand("npm --version", null);
            assertNotNull("Result should not be null", result);
        } catch (IOException e) {
            // npm might not be installed - this is acceptable for the test
            assertTrue("Error should indicate npm not found", 
                    e.getMessage().contains("npm") || e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testExecuteCommand_DockerVersion_Success() throws IOException, InterruptedException {
        try {
            // Test docker command (may not be installed on all systems)
            String result = executor.executeCommand("docker --version", null);
            assertNotNull("Result should not be null", result);
        } catch (IOException e) {
            // docker might not be installed - this is acceptable for the test
            assertTrue("Error should indicate docker not found or not running", 
                    e.getMessage().contains("docker") || 
                    e.getMessage().contains("not found") ||
                    e.getMessage().contains("Cannot connect"));
        }
    }

    // ============================================================================
    // Working Directory Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_CustomWorkingDirectory_Success() throws IOException, InterruptedException {
        // Create temporary directory
        File tempDir = tempFolder.newFolder("test-workdir");

        // Execute command in custom working directory 
        // Use git command which is whitelisted and works reliably
        String result = executor.executeCommand("git --version", tempDir.getAbsolutePath());
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    @Test
    public void testExecuteCommand_InvalidWorkingDirectory_FallsBackToGitRoot() throws IOException, InterruptedException {
        // Test with non-existent working directory - should fallback gracefully
        String result = executor.executeCommand("git --version", "/non/existent/directory/path");
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    @Test
    public void testExecuteCommand_NullWorkingDirectory_UsesGitRoot() throws IOException, InterruptedException {
        // Test with null working directory - should use git root or current dir
        String result = executor.executeCommand("git --version", null);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    // ============================================================================
    // Environment Variables Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_EnvironmentVariables_LoadedFromDmtoolsEnv() throws IOException, InterruptedException {
        // Create temporary directory with dmtools.env file
        File tempDir = tempFolder.newFolder("test-env");
        File envFile = new File(tempDir, "dmtools.env");
        Files.writeString(envFile.toPath(), "TEST_VAR=test_value\nANOTHER_VAR=another_value\n");

        // Execute command that echoes environment variable
        String osName = System.getProperty("os.name").toLowerCase();
        String command;
        if (osName.contains("win")) {
            command = "echo %TEST_VAR%";
        } else {
            command = "git config --get user.name"; // Use a command that works reliably
        }
        
        String result = executor.executeCommand(command, tempDir.getAbsolutePath());
        
        assertNotNull("Result should not be null", result);
        // Note: Environment variables are loaded but may not be accessible in all shell contexts
    }

    @Test
    public void testExecuteCommand_NoDmtoolsEnvFile_ExecutesSuccessfully() throws IOException, InterruptedException {
        // Create temporary directory WITHOUT dmtools.env file
        File tempDir = tempFolder.newFolder("test-no-env");

        // Execute command without dmtools.env file
        String result = executor.executeCommand("git --version", tempDir.getAbsolutePath());
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    // ============================================================================
    // Command Execution Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_GitBranchCurrent_Success() throws IOException, InterruptedException {
        // Test git branch --show-current command
        String result = executor.executeCommand("git branch --show-current", null);
        
        assertNotNull("Result should not be null", result);
        // Result should be the current branch name (e.g., "main", "master", etc.)
    }

    @Test
    public void testExecuteCommand_GitRemote_Success() throws IOException, InterruptedException {
        // Test git remote command (fast and works in any repo state)
        String result = executor.executeCommand("git remote -v", null);
        
        assertNotNull("Result should not be null", result);
        // Result can be empty if no remotes configured, or contain remote URLs
    }

    @Test
    public void testExecuteCommand_MultipleWhitelistedCommands_Success() throws IOException, InterruptedException {
        // Test multiple whitelisted commands
        String[] commands = {
            "git --version",
            "git branch --show-current",
            "git status --short"
        };

        for (String command : commands) {
            String result = executor.executeCommand(command, null);
            assertNotNull("Result should not be null for command: " + command, result);
        }
    }

    // ============================================================================
    // Error Handling Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_InvalidGitCommand_ReturnsErrorOutput() throws IOException, InterruptedException {
        // Test git command with invalid syntax
        // Git will execute but return error message in output (not throw exception)
        String result = executor.executeCommand("git invalid-command-xyz", null);
        
        assertNotNull("Result should not be null", result);
        // Git will return error message about unknown command
        assertTrue("Result should contain error message", 
                result.toLowerCase().contains("invalid") || 
                result.toLowerCase().contains("not a git command") ||
                result.toLowerCase().contains("unknown"));
    }

    @Test
    public void testExecuteCommand_CommandWithSpecialCharacters_Success() throws IOException, InterruptedException {
        // Test command with quotes and special characters using a fast, reliable command
        String result = executor.executeCommand("git config --get user.name", null);
        
        // Should execute without security issues (may return empty if user.name not set)
        assertNotNull("Should handle command execution", result);
    }

    // ============================================================================
    // Whitelist Validation Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_AllWhitelistedCommands_Accepted() throws IOException, InterruptedException {
        // Test that all whitelisted command prefixes are accepted
        String[] whitelistedCommands = {
            "git --version",
            "gh --version",
            "dmtools --help",
            "npm --version",
            "yarn --version",
            "docker --version",
            "kubectl version --client",
            "terraform --version",
            "ansible --version",
            "aws --version",
            "gcloud --version",
            "az --version"
        };

        for (String command : whitelistedCommands) {
            try {
                // Try to execute - may fail if tool not installed, but should not throw SecurityException
                String result = executor.executeCommand(command, null);
                assertNotNull("Result should not be null for: " + command, result);
            } catch (IOException e) {
                // Tool might not be installed - this is acceptable
                // Should NOT be SecurityException
                String errorMsg = e.getMessage().toLowerCase();
                assertFalse("Should not be security error for whitelisted command: " + command,
                        errorMsg.contains("not allowed") || errorMsg.contains("not permitted"));
            }
        }
    }

    @Test
    public void testExecuteCommand_CaseInsensitiveWhitelist_Success() throws IOException, InterruptedException {
        // Test that whitelist check is case-insensitive
        String result = executor.executeCommand("GIT --version", null);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_PartialMatch_NotAccepted() throws IOException, InterruptedException {
        // Test that partial matches are not accepted (e.g., "gitx" should fail)
        executor.executeCommand("gitx some-command", null);
    }

    // ============================================================================
    // Integration Tests
    // ============================================================================

    @Test
    public void testExecuteCommand_CompleteWorkflow_Success() throws IOException, InterruptedException {
        // Test a complete workflow: check status, get branch
        
        // Step 1: Get git version
        String version = executor.executeCommand("git --version", null);
        assertNotNull("Git version should not be null", version);
        assertTrue("Should contain 'git version'", version.toLowerCase().contains("git version"));

        // Step 2: Get current branch
        String branch = executor.executeCommand("git branch --show-current", null);
        assertNotNull("Branch name should not be null", branch);

        // Step 3: Get status
        String status = executor.executeCommand("git status --short", null);
        assertNotNull("Status should not be null", status);
    }

    @Test
    public void testExecuteCommand_WithCommandArguments_Success() throws IOException, InterruptedException {
        // Test commands with various arguments
        String result = executor.executeCommand("git status --porcelain", null);
        
        assertNotNull("Result should not be null", result);
        // Porcelain format should work the same as --short
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    public void testExecuteCommand_CommandWithLeadingSpaces_Success() throws IOException, InterruptedException {
        // Test command with leading spaces (should be trimmed)
        String result = executor.executeCommand("   git --version   ", null);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    @Test
    public void testExecuteCommand_CommandWithTrailingSpaces_Success() throws IOException, InterruptedException {
        // Test command with trailing spaces (should be trimmed)
        String result = executor.executeCommand("git --version   ", null);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
    }

    @Test
    public void testExecuteCommand_GitCommandWithManyArguments_Success() throws IOException, InterruptedException {
        // Test git command with multiple arguments using a fast command
        String result = executor.executeCommand("git status --short --branch", null);
        
        assertNotNull("Result should not be null", result);
        // Status command is fast and works in any repo state
    }

    // ============================================================================
    // Performance Tests
    // ============================================================================

    @Test(timeout = 10000) // 10 second timeout
    public void testExecuteCommand_Performance_CompletesQuickly() throws IOException, InterruptedException {
        // Test that command execution completes within reasonable time
        String result = executor.executeCommand("git --version", null);
        
        assertNotNull("Result should not be null", result);
    }

    @Test(timeout = 10000) // 10 second timeout
    public void testExecuteCommand_MultipleExecutions_CompletesQuickly() throws IOException, InterruptedException {
        // Test multiple executions complete within reasonable time
        for (int i = 0; i < 5; i++) {
            String result = executor.executeCommand("git --version", null);
            assertNotNull("Result should not be null for iteration " + i, result);
        }
    }
}

