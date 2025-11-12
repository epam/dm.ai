package com.github.istin.dmtools.file;

import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File operations tool for reading files from job working directory.
 * Provides secure, sandboxed file access for JavaScript post-processing functions
 * in Expert and Teammate jobs.
 * 
 * Security:
 * - All file access is restricted to the job working directory
 * - Path traversal attacks are prevented through path normalization
 * - Attempts to access files outside working directory return null
 */
@Singleton
public class FileTools {
    
    private static final Logger logger = LogManager.getLogger(FileTools.class);
    private static final Gson gson = new Gson();
    
    /**
     * Read file content from working directory.
     * 
     * Supports reading from:
     * - outputs/ directory (CLI command outputs like outputs/response.md)
     * - input/ directory (job input files like input/TICKET-KEY/request.md)
     * - Any subdirectory within working directory
     * 
     * Security Features:
     * - Sandboxed to working directory only
     * - Path traversal prevention (../, absolute paths outside working dir)
     * - Returns null for missing or inaccessible files (graceful error handling)
     * 
     * @param filePath File path relative to working directory or absolute path within working directory
     * @return File content as UTF-8 string, or null if file doesn't exist or is inaccessible
     */
    @MCPTool(
        name = "file_read",
        description = "Read file content from working directory (supports input/ and outputs/ folders). Returns file content as string or null if file doesn't exist or is inaccessible. All file formats supported as UTF-8 text.",
        integration = "file"
    )
    public String readFile(
            @MCPParam(
                name = "path",
                description = "File path relative to working directory or absolute path within working directory",
                required = true,
                example = "outputs/response.md"
            ) String filePath
    ) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("File path cannot be null or empty");
            return null;
        }
        
        try {
            // Get working directory (absolute, normalized)
            Path workingDir = Paths.get(System.getProperty("user.dir"))
                    .toAbsolutePath()
                    .normalize();
            
            // Parse requested path
            Path requestedPath = Paths.get(filePath.trim());
            
            // Resolve to absolute path and normalize (handles ./ and ../ components)
            Path resolvedPath;
            if (requestedPath.isAbsolute()) {
                resolvedPath = requestedPath.normalize();
            } else {
                resolvedPath = workingDir.resolve(requestedPath).normalize();
            }
            
            // Security check: Ensure resolved path is within working directory
            if (!resolvedPath.startsWith(workingDir)) {
                logger.error("Security violation: Path traversal attempt blocked - requested: {}, resolved: {}, working dir: {}", 
                        filePath, resolvedPath, workingDir);
                return null;
            }
            
            // Check if file exists
            if (!Files.exists(resolvedPath)) {
                logger.warn("File not found: {} (resolved to: {})", filePath, resolvedPath);
                return null;
            }
            
            // Check if file is readable
            if (!Files.isReadable(resolvedPath)) {
                logger.warn("File not readable: {} (resolved to: {})", filePath, resolvedPath);
                return null;
            }
            
            // Check if path is a directory
            if (Files.isDirectory(resolvedPath)) {
                logger.warn("Path is a directory, not a file: {} (resolved to: {})", filePath, resolvedPath);
                return null;
            }
            
            // Read file content as UTF-8 string
            String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            
            logger.debug("Successfully read {} characters from: {} (resolved to: {})", 
                    content.length(), filePath, resolvedPath);
            
            return content;
            
        } catch (IOException e) {
            logger.error("Failed to read file: {} - {}", filePath, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error reading file: {} - {}", filePath, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Write content to file in working directory.
     * 
     * Creates parent directories automatically if they don't exist.
     * Supports writing to any subdirectory within working directory.
     * 
     * Security Features:
     * - Sandboxed to working directory only
     * - Path traversal prevention (../, absolute paths outside working dir)
     * - Automatic directory creation
     * 
     * @param filePath File path relative to working directory or absolute path within working directory
     * @param content Content to write as UTF-8 string
     * @return Success message or null if operation failed
     */
    @MCPTool(
        name = "file_write",
        description = "Write content to file in working directory. Creates parent directories automatically. Returns success message or null on failure.",
        integration = "file"
    )
    public String writeFile(
            @MCPParam(
                name = "path",
                description = "File path relative to working directory or absolute path within working directory",
                required = true,
                example = "inbox/raw/teams_messages/1729766400000-messages.json"
            ) String filePath,
            @MCPParam(
                name = "content",
                description = "Content to write to the file as UTF-8 string",
                required = true,
                example = "{\"messages\": []}"
            ) String content
    ) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("File path cannot be null or empty");
            return null;
        }
        
        if (content == null) {
            logger.warn("Content cannot be null");
            return null;
        }
        
        try {
            // Get working directory (absolute, normalized)
            Path workingDir = Paths.get(System.getProperty("user.dir"))
                    .toAbsolutePath()
                    .normalize();
            
            // Parse requested path
            Path requestedPath = Paths.get(filePath.trim());
            
            // Resolve to absolute path and normalize (handles ./ and ../ components)
            Path resolvedPath;
            if (requestedPath.isAbsolute()) {
                resolvedPath = requestedPath.normalize();
            } else {
                resolvedPath = workingDir.resolve(requestedPath).normalize();
            }
            
            // Security check: Ensure resolved path is within working directory
            if (!resolvedPath.startsWith(workingDir)) {
                logger.error("Security violation: Path traversal attempt blocked - requested: {}, resolved: {}, working dir: {}", 
                        filePath, resolvedPath, workingDir);
                return null;
            }
            
            // Create parent directories if they don't exist
            Path parentDir = resolvedPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.debug("Created parent directories: {}", parentDir);
            }
            
            // Write file content as UTF-8 string
            Files.writeString(resolvedPath, content, StandardCharsets.UTF_8);
            
            logger.info("Successfully wrote {} characters to: {} (resolved to: {})", 
                    content.length(), filePath, resolvedPath);
            
            return "File written successfully: " + filePath;
            
        } catch (IOException e) {
            logger.error("Failed to write file: {} - {}", filePath, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error writing file: {} - {}", filePath, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Validate JSON string and provide detailed error information if invalid.
     * 
     * Returns a JSON string with validation result:
     * - If valid: {"valid": true}
     * - If invalid: {"valid": false, "error": "error message", "line": line_number, "column": column_number, "position": character_position, "context": "context around error"}
     * 
     * The error message includes:
     * - Type of error (syntax error, missing quotes, etc.)
     * - Line and column numbers where error occurred (from Gson)
     * - Character position where error occurred
     * - Context around the error location
     * 
     * @param jsonString JSON string to validate
     * @return JSON validation result as string
     */
    @MCPTool(
        name = "file_validate_json",
        description = "Validate JSON string and return detailed error information if invalid. Returns JSON string with validation result: {\"valid\": true} for valid JSON, or {\"valid\": false, \"error\": \"error message\", \"line\": line_number, \"column\": column_number, \"position\": character_position, \"context\": \"context around error\"} for invalid JSON.",
        integration = "file"
    )
    public String validateJson(
            @MCPParam(
                name = "json",
                description = "JSON string to validate",
                required = true,
                example = "{\"key\": \"value\"}"
            ) String jsonString
    ) {
        if (jsonString == null) {
            logger.warn("JSON string cannot be null");
            return createValidationResult(false, "JSON string cannot be null", -1, -1, -1, null);
        }
        
        String trimmed = jsonString.trim();
        if (trimmed.isEmpty()) {
            logger.warn("JSON string cannot be empty");
            return createValidationResult(false, "JSON string cannot be empty", -1, -1, -1, null);
        }
        
        try {
            // Use Gson to parse JSON - it provides detailed error information
            // Try parsing as generic Object to validate any JSON structure
            gson.fromJson(trimmed, Object.class);
            logger.debug("JSON validation successful");
            return createValidationResult(true, null, -1, -1, -1, null);
        } catch (JsonSyntaxException e) {
            // Gson provides detailed error information including line and column
            return handleGsonException(e, trimmed);
        } catch (Exception e) {
            logger.error("Unexpected error validating JSON: {}", e.getMessage(), e);
            return createValidationResult(false, "Unexpected error: " + e.getMessage(), -1, -1, -1, null);
        }
    }
    
    /**
     * Validate JSON file and provide detailed error information if invalid.
     * 
     * Reads the file from working directory and validates its JSON content.
     * Returns a JSON string with validation result:
     * - If valid: {"valid": true, "file": "file_path"}
     * - If invalid: {"valid": false, "file": "file_path", "error": "error message", "line": line_number, "column": column_number, "position": character_position, "context": "context around error"}
     * - If file not found or unreadable: {"valid": false, "file": "file_path", "error": "File not found or unreadable"}
     * 
     * The error message includes:
     * - Type of error (syntax error, missing quotes, etc.)
     * - Line and column numbers where error occurred (from Gson)
     * - Character position where error occurred
     * - Context around the error location
     * 
     * @param filePath File path relative to working directory or absolute path within working directory
     * @return JSON validation result as string
     */
    @MCPTool(
        name = "file_validate_json_file",
        description = "Validate JSON file and return detailed error information if invalid. Reads file from working directory and validates its JSON content. Returns JSON string with validation result including file path.",
        integration = "file"
    )
    public String validateJsonFile(
            @MCPParam(
                name = "path",
                description = "File path relative to working directory or absolute path within working directory",
                required = true,
                example = "outputs/response.json"
            ) String filePath
    ) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("File path cannot be null or empty");
            return createFileValidationResult(false, null, "File path cannot be null or empty", -1, -1, -1, null);
        }
        
        // Read file content
        String content = readFile(filePath);
        if (content == null) {
            logger.warn("File not found or unreadable: {}", filePath);
            return createFileValidationResult(false, filePath, "File not found or unreadable", -1, -1, -1, null);
        }
        
        // Validate the JSON content
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            logger.warn("File is empty: {}", filePath);
            return createFileValidationResult(false, filePath, "File is empty", -1, -1, -1, null);
        }
        
        try {
            // Use Gson to parse JSON - it provides detailed error information
            gson.fromJson(trimmed, Object.class);
            logger.debug("JSON validation successful for file: {}", filePath);
            return createFileValidationResult(true, filePath, null, -1, -1, -1, null);
        } catch (JsonSyntaxException e) {
            // Gson provides detailed error information including line and column
            return handleGsonExceptionForFile(e, trimmed, filePath);
        } catch (Exception e) {
            logger.error("Unexpected error validating JSON file {}: {}", filePath, e.getMessage(), e);
            return createFileValidationResult(false, filePath, "Unexpected error: " + e.getMessage(), -1, -1, -1, null);
        }
    }
    
    /**
     * Handle Gson JsonSyntaxException and extract detailed error information.
     * Gson provides line and column numbers in error messages like:
     * "Expected a string but was BEGIN_ARRAY at line 2 column 17 path $.languages"
     */
    private String handleGsonException(JsonSyntaxException e, String jsonString) {
        String errorMessage = e.getMessage();
        
        // Extract line and column from Gson error message
        // Format: "... at line X column Y ..."
        int line = extractLineNumber(errorMessage);
        int column = extractColumnNumber(errorMessage);
        
        // Calculate character position from line and column
        int position = calculatePosition(jsonString, line, column);
        
        // Extract context around error
        String context = extractContext(jsonString, position);
        
        // Clean up error message (remove position info that we'll include separately)
        String cleanMessage = cleanGsonErrorMessage(errorMessage);
        
        logger.debug("JSON validation failed: {} at line {} column {} (position {})", 
                cleanMessage, line, column, position);
        
        return createValidationResult(false, cleanMessage, line, column, position, context);
    }
    
    /**
     * Handle Gson JsonSyntaxException for file validation and extract detailed error information.
     */
    private String handleGsonExceptionForFile(JsonSyntaxException e, String jsonString, String filePath) {
        String errorMessage = e.getMessage();
        
        // Extract line and column from Gson error message
        int line = extractLineNumber(errorMessage);
        int column = extractColumnNumber(errorMessage);
        
        // Calculate character position from line and column
        int position = calculatePosition(jsonString, line, column);
        
        // Extract context around error
        String context = extractContext(jsonString, position);
        
        // Clean up error message (remove position info that we'll include separately)
        String cleanMessage = cleanGsonErrorMessage(errorMessage);
        
        logger.debug("JSON validation failed for file {}: {} at line {} column {} (position {})", 
                filePath, cleanMessage, line, column, position);
        
        return createFileValidationResult(false, filePath, cleanMessage, line, column, position, context);
    }
    
    /**
     * Extract line number from Gson error message.
     * Format: "... at line X column Y ..."
     */
    private int extractLineNumber(String errorMessage) {
        if (errorMessage == null) {
            return -1;
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "at\\s+line\\s+(\\d+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(errorMessage);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return -1;
    }
    
    /**
     * Extract column number from Gson error message.
     * Format: "... at line X column Y ..."
     */
    private int extractColumnNumber(String errorMessage) {
        if (errorMessage == null) {
            return -1;
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "at\\s+line\\s+\\d+\\s+column\\s+(\\d+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(errorMessage);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return -1;
    }
    
    /**
     * Calculate character position from line and column numbers.
     */
    private int calculatePosition(String jsonString, int line, int column) {
        if (line < 1 || column < 1) {
            return -1;
        }
        
        String[] lines = jsonString.split("\n", -1);
        if (line > lines.length) {
            return -1;
        }
        
        int position = 0;
        // Sum up characters from previous lines
        for (int i = 0; i < line - 1; i++) {
            position += lines[i].length() + 1; // +1 for newline character
        }
        
        // Add column position (column is 1-based, so subtract 1)
        if (line <= lines.length) {
            int columnPos = Math.min(column - 1, lines[line - 1].length());
            position += columnPos;
        }
        
        return position;
    }
    
    /**
     * Extract context around error position.
     */
    private String extractContext(String jsonString, int position) {
        if (position < 0 || position >= jsonString.length()) {
            return null;
        }
        
        int start = Math.max(0, position - 20);
        int end = Math.min(jsonString.length(), position + 20);
        
        String context = jsonString.substring(start, end);
        int relativePos = position - start;
        
        // Add indicator for error position
        StringBuilder sb = new StringBuilder(context);
        sb.insert(relativePos, " <-- ERROR HERE");
        
        return sb.toString();
    }
    
    /**
     * Clean up Gson error message for better readability.
     * Remove position info that we'll include separately.
     */
    private String cleanGsonErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "Unknown JSON error";
        }
        
        // Remove position info like "at line X column Y" and "path $..."
        String cleaned = errorMessage
            .replaceAll("\\s*at\\s+line\\s+\\d+\\s+column\\s+\\d+.*", "")
            .replaceAll("\\s+path\\s+\\$[^\\s]*", "")
            .trim();
        
        if (cleaned.isEmpty()) {
            return "Invalid JSON syntax";
        }
        
        return cleaned;
    }
    
    /**
     * Create validation result JSON string.
     */
    private String createValidationResult(boolean valid, String error, int line, int column, int position, String context) {
        try {
            JSONObject result = new JSONObject();
            result.put("valid", valid);
            
            if (!valid) {
                if (error != null) {
                    result.put("error", error);
                }
                if (line >= 1) {
                    result.put("line", line);
                }
                if (column >= 1) {
                    result.put("column", column);
                }
                if (position >= 0) {
                    result.put("position", position);
                }
                if (context != null) {
                    result.put("context", context);
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            // Should never happen, but fallback
            logger.error("Failed to create validation result JSON", e);
            return "{\"valid\": false, \"error\": \"Failed to create validation result\"}";
        }
    }
    
    /**
     * Create file validation result JSON string with file path.
     */
    private String createFileValidationResult(boolean valid, String filePath, String error, int line, int column, int position, String context) {
        try {
            JSONObject result = new JSONObject();
            result.put("valid", valid);
            
            if (filePath != null) {
                result.put("file", filePath);
            }
            
            if (!valid) {
                if (error != null) {
                    result.put("error", error);
                }
                if (line >= 1) {
                    result.put("line", line);
                }
                if (column >= 1) {
                    result.put("column", column);
                }
                if (position >= 0) {
                    result.put("position", position);
                }
                if (context != null) {
                    result.put("context", context);
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            // Should never happen, but fallback
            logger.error("Failed to create file validation result JSON", e);
            return "{\"valid\": false, \"error\": \"Failed to create validation result\"}";
        }
    }
}

