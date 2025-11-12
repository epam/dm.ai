package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.utils.FileConfig;
import com.github.istin.dmtools.common.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for processing instructions in Teammate jobs.
 * Handles extraction of content from:
 * - Confluence URLs (https://...)
 * - Local file paths (/, ./, ../)
 * - Plain text (used as-is)
 */
public class InstructionProcessor {

    private static final Logger logger = LogManager.getLogger(InstructionProcessor.class);

    private final Confluence confluence;
    private final String workingDirectory;

    /**
     * Creates an InstructionProcessor with Confluence support
     * Uses current working directory for resolving relative file paths
     * 
     * @param confluence Confluence client for URL processing
     */
    public InstructionProcessor(Confluence confluence) {
        this(confluence, System.getProperty("user.dir"));
    }

    /**
     * Creates an InstructionProcessor with custom working directory
     * 
     * @param confluence Confluence client for URL processing
     * @param workingDirectory Base directory for resolving relative paths
     */
    public InstructionProcessor(Confluence confluence, String workingDirectory) {
        this.confluence = confluence;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Extracts and processes instruction content from various sources.
     * - Confluence URLs: Fetches content from Confluence
     * - File paths: Reads content from local files
     * - Plain text: Returns as-is
     * 
     * @param inputArray Array of instructions to process
     * @return Array of processed instructions with expanded content
     * @throws IOException if Confluence access fails
     */
    public String[] extractIfNeeded(String... inputArray) throws IOException {
        if (inputArray == null) {
            return new String[0];
        }
        String[] extractedArray = new String[inputArray.length];
        for (int i = 0; i < inputArray.length; i++) {
            String input = inputArray[i];
            if (input != null) {
                // Confluence URL detection
                if (input.startsWith("https://")) {
                    input = processConfluenceUrl(input);
                }
                // File path detection and processing
                else if (isFilePath(input)) {
                    input = processFilePath(input);
                }
                // Plain text: no processing needed
            }
            extractedArray[i] = input;
        }
        return extractedArray;
    }

    /**
     * Processes a Confluence URL by fetching its content
     * 
     * @param url Confluence URL to process
     * @return Original URL concatenated with fetched content
     * @throws IOException if Confluence access fails
     */
    private String processConfluenceUrl(String url) throws IOException {
        if (confluence == null) {
            logger.warn("Confluence client not available, using URL as-is: {}", url);
            return url;
        }
        
        try {
            String value = confluence.contentByUrl(url).getStorage().getValue();
            if (StringUtils.isConfluenceYamlFormat(value)) {
                return url + "\n" + StringUtils.extractYamlContentFromConfluence(value);
            } else {
                return url + "\n" + value;
            }
        } catch (Exception e) {
            logger.warn("Error fetching Confluence content from '{}': {}. Using URL as fallback.", 
                    url, e.getMessage());
            return url;
        }
    }

    /**
     * Checks if the input string is a file path pattern.
     * Supports absolute paths (/) and relative paths (./ or ../)
     * 
     * @param input the string to check
     * @return true if the input matches a file path pattern
     */
    private boolean isFilePath(String input) {
        return input.startsWith("/") || input.startsWith("./") || input.startsWith("../");
    }

    /**
     * Processes a file path by reading its content.
     * Resolves paths relative to the current working directory where dmtools is run from.
     * Implements graceful error handling:
     * - File not found: Log warning and use original value as fallback
     * - I/O errors: Log warning and use original value as fallback
     * - Invalid path: Log warning and use original value as fallback
     * 
     * @param input the file path to process
     * @return the file content, or original path if file not found or error occurs
     */
    private String processFilePath(String input) {
        try {
            // Resolve relative paths from working directory (current directory where dmtools is run from)
            Path basePath = Paths.get(workingDirectory);
            Path filePath = input.startsWith("/") ? 
                    Paths.get(input) : basePath.resolve(input).normalize();

            logger.debug("Resolving file path '{}' relative to working directory: {} -> {}", 
                    input, workingDirectory, filePath);

            // Read file content using existing FileConfig utility
            FileConfig fileConfig = new FileConfig();
            String fileContent = fileConfig.readFile(filePath.toString());

            if (fileContent != null) {
                logger.debug("Successfully loaded file content from: {}", filePath);
                return fileContent;
            }
            
            // File not found - log warning and use original value
            logger.warn("File not found at: {} (resolved from '{}' relative to '{}'), using original value as fallback", 
                    filePath, input, workingDirectory);
            return input;
        } catch (RuntimeException e) {
            // Error reading file or invalid path - log warning and use original value
            logger.warn("Error processing file path '{}': {}. Using original value as fallback.", 
                    input, e.getMessage());
            return input;
        }
    }
}


