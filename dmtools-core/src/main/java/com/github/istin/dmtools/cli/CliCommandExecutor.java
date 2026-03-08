package com.github.istin.dmtools.cli;

import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CLI command execution tool for JavaScript post-action functions.
 * Provides secure, sandboxed command execution for automation workflows.
 * 
 * Security Features:
 * - Command whitelisting (only pre-approved commands allowed)
 * - Additional commands configurable via CLI_ALLOWED_COMMANDS env var / dmtools.env
 * - Working directory boundary enforcement
 * - Sensitive data masking in logs
 * - Environment variable inheritance from workflow context
 * - Automatic dmtools.env file loading
 * 
 * Base whitelist:
 * git, gh, dmtools, npm, yarn, docker, kubectl, terraform, ansible, aws, gcloud, az
 *
 * Extra commands (opt-in, comma-separated) via dmtools.env or agent envVariables:
 *   CLI_ALLOWED_COMMANDS=find,ls,cat,mkdir,pytest,python3,pip3,curl,ffmpeg,bash,run-cursor-agent.sh
 */
@Singleton
public class CliCommandExecutor {
    
    private static final Logger logger = LogManager.getLogger(CliCommandExecutor.class);

    /** Config key for extra allowed commands (comma-separated). */
    public static final String CLI_ALLOWED_COMMANDS_KEY = "CLI_ALLOWED_COMMANDS";

    // Security baseline – never removed regardless of configuration
    private static final String[] BASE_ALLOWED_COMMANDS = {
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
        description = "Execute CLI commands from JavaScript post-actions. Base whitelist: git, gh, dmtools, npm, yarn, docker, kubectl, terraform, ansible, aws, gcloud, az. Additional commands can be enabled via CLI_ALLOWED_COMMANDS env var (comma-separated) in dmtools.env or agent envVariables, e.g. CLI_ALLOWED_COMMANDS=find,ls,cat,pytest,python3,curl,bash,run-cursor-agent.sh. Returns command output as string.",
        integration = "cli",
        category = "system"
    )
    public String executeCommand(
        @MCPParam(
            name = "command",
            description = "CLI command to execute. Must start with a whitelisted command. Extend the whitelist via CLI_ALLOWED_COMMANDS in dmtools.env.",
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
        
        // Security check: Validate command against whitelist (base + configurable extras)
        Set<String> effectiveAllowed = buildEffectiveAllowedCommands();
        if (!isCommandWhitelisted(trimmedCommand, effectiveAllowed)) {
            String maskedCommand = maskSensitiveData(trimmedCommand);
            logger.error("Security violation: Command not whitelisted - {}", maskedCommand);
            throw new SecurityException("Command not allowed. Whitelisted commands: " + String.join(", ", effectiveAllowed)
                    + ". Add more via CLI_ALLOWED_COMMANDS in dmtools.env or agent envVariables.");
        }
        
        // Resolve working directory
        File resolvedWorkingDir = resolveWorkingDirectory(workingDirectory);
        logger.debug("Resolved working directory: {}", resolvedWorkingDir.getAbsolutePath());
        
        // Load environment variables (default + dmtools.env file if it exists)
        Map<String, String> envVars = loadEnvironmentVariables(resolvedWorkingDir);
        logger.debug("Loaded {} environment variables (including defaults)", envVars.size());
        
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
     * Builds the effective allowed-commands set: base whitelist merged with any
     * extra commands from {@code CLI_ALLOWED_COMMANDS} (read via PropertyReader so
     * it respects dmtools.env, system env vars, and per-job thread-local overrides
     * set through {@code envVariables} in agent JSON).
     */
    private Set<String> buildEffectiveAllowedCommands() {
        Set<String> allowed = new HashSet<>(Arrays.asList(BASE_ALLOWED_COMMANDS));
        String extra = new PropertyReader().getValue(CLI_ALLOWED_COMMANDS_KEY);
        if (extra != null && !extra.trim().isEmpty()) {
            for (String cmd : extra.split(",")) {
                String trimmed = cmd.trim();
                if (!trimmed.isEmpty()) {
                    allowed.add(trimmed.toLowerCase());
                }
            }
        }
        return allowed;
    }

    /**
     * Validates if command is allowed.
     *
     * <p>The executable name is extracted as the first whitespace-separated token.
     * Leading {@code ./} and any directory path are stripped so that
     * {@code ./run-cursor-agent.sh arg1} matches {@code run-cursor-agent.sh} in the
     * allowed set.
     */
    private boolean isCommandWhitelisted(String command, Set<String> allowed) {
        String token = command.split("\\s+")[0].toLowerCase();
        // Exact match
        if (allowed.contains(token)) {
            return true;
        }
        // Basename match (strips leading ./ or full path)
        String basename = Paths.get(token).getFileName().toString();
        return allowed.contains(basename);
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
     * Also adds default environment variables for non-interactive execution.
     * 
     * @param workingDirectory Directory to check for dmtools.env file
     * @return Map of environment variables
     */
    private Map<String, String> loadEnvironmentVariables(File workingDirectory) {
        Map<String, String> envVars = new java.util.HashMap<>();
        
        // Add default environment variables for non-interactive execution
        // Disable git pager to prevent hangs in CI/CD environments
        envVars.put("GIT_PAGER", "cat");
        envVars.put("GIT_TERMINAL_PROMPT", "0");

        // Propagate PATH and extend it with common tool installation directories.
        // This is necessary when the JVM is launched from a GUI (e.g. IntelliJ) or CI
        // environment that does not source shell startup scripts, so Homebrew or other
        // package-manager paths may be absent from the inherited PATH.
        String systemPath = System.getenv("PATH");
        String extendedPath = systemPath != null ? systemPath : "";
        for (String commonPath : new String[]{"/usr/local/bin", "/opt/homebrew/bin", "/usr/bin", "/bin"}) {
            if (!extendedPath.contains(commonPath)) {
                extendedPath = extendedPath + ":" + commonPath;
            }
        }
        envVars.put("PATH", extendedPath);
        
        // Load from dmtools.env file if it exists
        Path envFilePath = Paths.get(workingDirectory.getAbsolutePath(), "dmtools.env");
        
        if (Files.exists(envFilePath)) {
            logger.debug("Found dmtools.env file at: {}", envFilePath);
            Map<String, String> fileEnvVars = CommandLineUtils.loadEnvironmentFromFile(envFilePath.toString());
            envVars.putAll(fileEnvVars);
        } else {
            logger.debug("No dmtools.env file found in working directory: {}. Continuing with system environment.", workingDirectory.getAbsolutePath());
        }
        
        return envVars;
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


