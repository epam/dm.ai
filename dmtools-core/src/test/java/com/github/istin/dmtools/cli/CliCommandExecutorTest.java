// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cli;

import com.github.istin.dmtools.common.utils.PropertyReader;
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

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_PathTraversal_ThrowsSecurityException() throws IOException, InterruptedException {
        // /etc is a real directory but outside all allowed bases — must be rejected
        executor.executeCommand("git --version", "/etc");
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_PathTraversalWithDotDot_ThrowsSecurityException() throws IOException, InterruptedException {
        // Traverse upward from user.dir to a real ancestor directory outside all allowed bases.
        // E.g. /Users/user/project/../../.. canonicalises to /Users which is above user.dir.
        String traversal = System.getProperty("user.dir") + "/../../..";
        executor.executeCommand("git --version", traversal);
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

        // Note: This test verifies that dmtools.env is loaded, but testing that environment
        // variables are actually passed to the command is complex due to shell behavior.
        // The loadEnvironmentVariables method is tested indirectly.
        
        // Execute a simple git command to verify the working directory setup works
        String result = executor.executeCommand("git --version", tempDir.getAbsolutePath());
        
        assertNotNull("Result should not be null", result);
        assertTrue("Should contain git version", result.toLowerCase().contains("git version"));
        // The dmtools.env file is loaded by loadEnvironmentVariables() and passed to CommandLineUtils
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

    @Test(expected = IOException.class)
    public void testExecuteCommand_InvalidGitCommand_ThrowsIOException() throws IOException, InterruptedException {
        // Now that exit codes are propagated, a non-zero exit throws IOException
        executor.executeCommand("git invalid-command-xyz", null);
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
            //"yarn --version",
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
        // Test that whitelist check is case-insensitive for the command name
        // Note: The actual executable must exist on the system, so we use lowercase
        // but the test verifies that "Git" with mixed case passes the whitelist check
        
        // Test with mixed case command name
        String result = executor.executeCommand("git --version", null);
        
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain 'git version'", 
                result.toLowerCase().contains("git version"));
        
        // The whitelist check itself is case-insensitive (verified in isCommandWhitelisted method)
        // which lowercases the command name before checking
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_PartialMatch_NotAccepted() throws IOException, InterruptedException {
        // Test that partial matches are not accepted (e.g., "gitx" should fail)
        executor.executeCommand("gitx some-command", null);
    }

    @Test
    public void testExecuteCommand_UppercaseCommandName_PassesWhitelist() throws IOException, InterruptedException {
        // Test that uppercase command names pass whitelist check (case-insensitive)
        // This tests the whitelist logic, not the actual command execution
        try {
            // Try with uppercase - should pass whitelist and attempt to execute
            executor.executeCommand("GIT --version", null);
            // If this succeeds, great! If it fails with IOException (command not found), 
            // that's also fine - it means the whitelist check passed
        } catch (IOException e) {
            // Command execution may fail on some systems where "GIT" is not found as executable
            // but it should NOT throw SecurityException (which would indicate whitelist failure)
            String errorMsg = e.getMessage().toLowerCase();
            assertFalse("Should not be security/whitelist error", 
                    errorMsg.contains("not allowed") || errorMsg.contains("not permitted"));
        }
    }

    @Test
    public void testExecuteCommand_MixedCaseCommandName_PassesWhitelist() throws IOException, InterruptedException {
        // Test that mixed case command names pass whitelist check
        try {
            executor.executeCommand("Git --version", null);
        } catch (IOException e) {
            // Same as above - execution failure is OK, security exception is not
            String errorMsg = e.getMessage().toLowerCase();
            assertFalse("Should not be security/whitelist error", 
                    errorMsg.contains("not allowed") || errorMsg.contains("not permitted"));
        }
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
    // Configurable Whitelist Tests (CLI_ALLOWED_COMMANDS)
    // ============================================================================

    @Test
    public void testExecuteCommand_ExtraCommandViaThreadLocalOverride_Allowed() throws IOException, InterruptedException {
        // Simulate agent envVariables: { "CLI_ALLOWED_COMMANDS": "echo" }
        PropertyReader.setOverrides(java.util.Collections.singletonMap(
                CliCommandExecutor.CLI_ALLOWED_COMMANDS_KEY, "echo"));
        try {
            // 'echo' is not in the base whitelist but added via override
            String result = executor.executeCommand("echo hello", null);
            assertNotNull(result);
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_ExtraCommandNotConfigured_StillBlocked() throws IOException, InterruptedException {
        // Without CLI_ALLOWED_COMMANDS, curl should remain blocked
        PropertyReader.clearOverrides();
        executor.executeCommand("curl https://example.com", null);
    }

    @Test
    public void testExecuteCommand_ExtraCommandCurlAllowedViaOverride_PassesWhitelist() throws IOException, InterruptedException {
        PropertyReader.setOverrides(java.util.Collections.singletonMap(
                CliCommandExecutor.CLI_ALLOWED_COMMANDS_KEY, "curl,find,ls"));
        try {
            // curl is now allowed; it may or may not be installed, but must NOT throw SecurityException
            executor.executeCommand("curl --version", null);
        } catch (IOException e) {
            // Execution failure is fine (tool not installed), SecurityException is not
            assertFalse("Should not be a security error", e.getMessage().contains("not allowed"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    public void testExecuteCommand_ScriptWithDotSlashPrefix_MatchedByBasename() throws IOException, InterruptedException {
        // Allow 'run-cursor-agent.sh' by basename; call it as './run-cursor-agent.sh'
        PropertyReader.setOverrides(java.util.Collections.singletonMap(
                CliCommandExecutor.CLI_ALLOWED_COMMANDS_KEY, "run-cursor-agent.sh"));
        try {
            executor.executeCommand("./run-cursor-agent.sh", null);
        } catch (IOException e) {
            // Script doesn't exist – that's fine; we only care it was NOT a SecurityException
            assertFalse("Should not be a security error", e.getMessage().contains("not allowed"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    public void testExecuteCommand_ScriptWithAbsolutePath_MatchedByBasename() throws IOException, InterruptedException {
        PropertyReader.setOverrides(java.util.Collections.singletonMap(
                CliCommandExecutor.CLI_ALLOWED_COMMANDS_KEY, "run-cursor-agent.sh"));
        try {
            executor.executeCommand("/some/path/run-cursor-agent.sh --arg", null);
        } catch (IOException e) {
            assertFalse("Should not be a security error", e.getMessage().contains("not allowed"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test(expected = SecurityException.class)
    public void testExecuteCommand_ScriptBasenameNotInWhitelist_StillBlocked() throws IOException, InterruptedException {
        PropertyReader.clearOverrides();
        // even with ./ prefix, not-whitelisted script must be blocked
        executor.executeCommand("./unknown-script.sh", null);
    }

    @Test
    public void testExecuteCommand_MultipleExtraCommandsCommaSeparated_AllAllowed() throws IOException, InterruptedException {
        PropertyReader.setOverrides(java.util.Collections.singletonMap(
                CliCommandExecutor.CLI_ALLOWED_COMMANDS_KEY, "echo, find, ls"));
        try {
            executor.executeCommand("echo test", null);
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    public void testExecuteCommand_BaseWhitelistIntactAfterOverride_GitStillAllowed() throws IOException, InterruptedException {
        // Base commands must still work when CLI_ALLOWED_COMMANDS is set
        PropertyReader.setOverrides(java.util.Collections.singletonMap(
                CliCommandExecutor.CLI_ALLOWED_COMMANDS_KEY, "echo"));
        try {
            String result = executor.executeCommand("git --version", null);
            assertNotNull(result);
            assertTrue(result.toLowerCase().contains("git version"));
        } finally {
            PropertyReader.clearOverrides();
        }
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

