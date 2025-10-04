package com.github.istin.dmtools.cli;

import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CLI command execution tool for JavaScript post-action functions.
 * Provides secure, sandboxed command execution for automation workflows.
 * 
 * Security Features:
 * - Command whitelisting (only pre-approved commands allowed)
 * - Working directory boundary enforcement
 * - Sensitive data masking in logs
 * - Environment variable inheritance from workflow context
 * - Automatic dmtools.env file loading
 * 
 * Supported Commands:
 * git, gh, dmtools, npm, yarn, docker, kubectl, terraform, ansible, aws, gcloud, az
 */
@Singleton
public class CliCommandExecutor {
    
    private static final Logger logger = LogManager.getLogger(CliCommandExecutor.class);
    
    // Whitelist of allowed command prefixes for security
    private static final String[] ALLOWED_COMMANDS = {
        "git", "gh", "dmtools", "npm", "yarn", "docker", 
        "kubectl", "terraform", "ansible", "aws", "gcloud", "az"
    };
    
    // Patterns for masking sensitive data in logs
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(token[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(password[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_PATTERN = Pattern.compile("(key[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("(GITHUB_TOKEN[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    
    /**
     * Execute CLI command from JavaScript post-action function.
     * 
     * Command Execution Flow:
     * 1. Validate command against whitelist
     * 2. Resolve working directory (git root or specified directory)
     * 3. Load environment variables from dmtools.env file
     * 4. Execute command using CommandLineUtils
     * 5. Mask sensitive data in logs
     * 6. Return command output to JavaScript
     * 
     * @param command CLI command to execute (must start with whitelisted command)
     * @param workingDirectory Working directory for command execution (optional, defaults to git root or current directory)
     * @return Command output as string (stdout/stderr combined)
     * @throws IOException If an I/O error occurs during command execution
     * @throws InterruptedException If the command is interrupted
     * @throws SecurityException If command is not whitelisted
     */
    @MCPTool(
        name = "cli_execute_command",
        description = "Execute CLI commands (git, gh, dmtools, npm, yarn, docker, kubectl, terraform, ansible, aws, gcloud, az) from JavaScript post-actions. Returns command output as string. Commands execute synchronously with environment variables inherited from workflow context. Only whitelisted commands allowed for security.",
        integration = "cli",
        category = "system"
    )
    public String executeCommand(
        @MCPParam(
            name = "command",
            description = "CLI command to execute. Must start with whitelisted command (git, gh, dmtools, npm, yarn, docker, kubectl, terraform, ansible, aws, gcloud, az).",
            required = true,
            example = "git commit -m 'Automated update'"
        ) String command,
        @MCPParam(
            name = "workingDirectory",
            description = "Working directory for command execution. Defaults to repository root if not specified. Use absolute path or path relative to current directory.",
            required = false,
            example = "/path/to/repo"
        ) String workingDirectory
    ) throws IOException, InterruptedException {
        
        // Validate command is not null or empty
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        
        String trimmedCommand = command.trim();
        
        // Security check: Validate command against whitelist
        if (!isCommandWhitelisted(trimmedCommand)) {
            String maskedCommand = maskSensitiveData(trimmedCommand);
            logger.error("Security violation: Command not whitelisted - {}", maskedCommand);
            throw new SecurityException("Command not allowed. Only whitelisted commands are permitted: " + String.join(", ", ALLOWED_COMMANDS));
        }
        
        // Resolve working directory
        File resolvedWorkingDir = resolveWorkingDirectory(workingDirectory);
        logger.debug("Resolved working directory: {}", resolvedWorkingDir.getAbsolutePath());
        
        // Load environment variables from dmtools.env file if it exists
        Map<String, String> envVars = loadEnvironmentVariables(resolvedWorkingDir);
        logger.debug("Loaded {} environment variables from dmtools.env", envVars.size());
        
        // Log command execution (with masked sensitive data)
        String maskedCommand = maskSensitiveData(trimmedCommand);
        logger.info("Executing CLI command: {} in directory: {}", maskedCommand, resolvedWorkingDir.getAbsolutePath());
        
        try {
            // Execute command using CommandLineUtils
            String output = CommandLineUtils.runCommand(trimmedCommand, resolvedWorkingDir, envVars);
            
            logger.debug("Command executed successfully, output length: {} characters", output.length());
            return output;
            
        } catch (IOException | InterruptedException e) {
            // Mask sensitive data in error message
            String maskedError = maskSensitiveData(e.getMessage() != null ? e.getMessage() : "");
            logger.error("Command execution failed: {} - {}", maskedCommand, maskedError, e);
            
            // Re-throw with masked error message
            if (e instanceof IOException) {
                throw new IOException("Command execution failed: " + maskedError, e);
            } else {
                throw (InterruptedException) e;
            }
        }
    }
    
    /**
     * Validates if command is whitelisted for execution.
     * 
     * @param command Command to validate
     * @return true if command starts with allowed prefix, false otherwise
     */
    private boolean isCommandWhitelisted(String command) {
        // Extract the first part of the command (the executable name)
        String commandName = command.split(" ")[0].toLowerCase();
        
        for (String allowedCommand : ALLOWED_COMMANDS) {
            if (commandName.equals(allowedCommand)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Resolves working directory for command execution.
     * 
     * Resolution strategy:
     * 1. If workingDirectory specified, use it (validate existence)
     * 2. Try to detect git repository root using 'git rev-parse --show-toplevel'
     * 3. Fallback to current directory
     * 
     * @param workingDirectory User-specified working directory (optional)
     * @return Resolved working directory
     */
    private File resolveWorkingDirectory(String workingDirectory) {
        // If working directory explicitly specified, use it
        if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
            File specifiedDir = new File(workingDirectory.trim());
            
            if (specifiedDir.exists() && specifiedDir.isDirectory()) {
                return specifiedDir;
            } else {
                logger.warn("Specified working directory does not exist or is not a directory: {}, falling back to git root or current directory", workingDirectory);
            }
        }
        
        // Try to detect git repository root
        try {
            String gitRoot = CommandLineUtils.runCommand("git rev-parse --show-toplevel");
            if (gitRoot != null && !gitRoot.trim().isEmpty()) {
                File gitRootDir = new File(gitRoot.trim());
                if (gitRootDir.exists() && gitRootDir.isDirectory()) {
                    logger.debug("Detected git repository root: {}", gitRootDir.getAbsolutePath());
                    return gitRootDir;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not detect git repository root: {}, falling back to current directory", e.getMessage());
        }
        
        // Fallback to current directory
        File currentDir = new File(System.getProperty("user.dir"));
        logger.debug("Using current directory: {}", currentDir.getAbsolutePath());
        return currentDir;
    }
    
    /**
     * Loads environment variables from dmtools.env file if it exists in working directory.
     * 
     * @param workingDirectory Directory to check for dmtools.env file
     * @return Map of environment variables
     */
    private Map<String, String> loadEnvironmentVariables(File workingDirectory) {
        Path envFilePath = Paths.get(workingDirectory.getAbsolutePath(), "dmtools.env");
        
        if (Files.exists(envFilePath)) {
            logger.debug("Found dmtools.env file at: {}", envFilePath);
            return CommandLineUtils.loadEnvironmentFromFile(envFilePath.toString());
        } else {
            logger.debug("No dmtools.env file found in working directory: {}. Continuing with system environment.", workingDirectory.getAbsolutePath());
            return Map.of(); // Return empty map if file doesn't exist
        }
    }
    
    /**
     * Masks sensitive data in command strings and log messages.
     * 
     * Masks patterns:
     * - token=VALUE
     * - password=VALUE
     * - key=VALUE
     * - GITHUB_TOKEN=VALUE
     * 
     * @param input String to mask
     * @return String with sensitive data replaced with ***
     */
    private String maskSensitiveData(String input) {
        if (input == null) {
            return null;
        }
        
        String masked = input;
        
        // Mask tokens
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1***");
        
        // Mask passwords
        masked = PASSWORD_PATTERN.matcher(masked).replaceAll("$1***");
        
        // Mask keys
        masked = KEY_PATTERN.matcher(masked).replaceAll("$1***");
        
        // Mask GitHub tokens
        masked = GITHUB_TOKEN_PATTERN.matcher(masked).replaceAll("$1***");
        
        return masked;
    }
}


