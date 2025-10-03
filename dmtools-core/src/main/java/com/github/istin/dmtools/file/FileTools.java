package com.github.istin.dmtools.file;

import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
}

