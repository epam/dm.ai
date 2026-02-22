package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.utils.FileConfig;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.github.GitHub;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for processing instructions in Teammate jobs.
 * Handles extraction of content from:
 * - GitHub URLs (https://github.com/...): fetches file content via BasicGithub using SOURCE_GITHUB_TOKEN
 * - Confluence URLs (https://...): Fetches content from Confluence
 * - Local file paths (/, ./, ../)
 * - Plain text (used as-is)
 */
public class InstructionProcessor {

    private static final Logger logger = LogManager.getLogger(InstructionProcessor.class);

    private final Confluence confluence;
    private final String workingDirectory;

    public InstructionProcessor(Confluence confluence) {
        this(confluence, System.getProperty("user.dir"));
    }

    public InstructionProcessor(Confluence confluence, String workingDirectory) {
        this.confluence = confluence;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Extracts and processes instruction content from various sources.
     * - GitHub URLs: Fetches file content via BasicGithub (SOURCE_GITHUB_TOKEN)
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
                // GitHub URL detection (must be checked before generic https://)
                if (isGithubUrl(input)) {
                    input = processGithubUrl(input);
                }
                // Confluence URL detection
                else if (input.startsWith("https://")) {
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
     * Returns true if the URL points to a GitHub file.
     */
    boolean isGithubUrl(String input) {
        return input.startsWith("https://github.com/") ||
               input.startsWith("https://raw.githubusercontent.com/");
    }

    /**
     * Creates the GitHub client used to fetch file content.
     * Overridable in tests to avoid real network calls.
     */
    protected GitHub createGithubClient() throws IOException {
        return (BasicGithub) BasicGithub.getInstance();
    }

    private String processGithubUrl(String url) {
        try {
            String content = createGithubClient().getFileContent(url);
            if (content != null) {
                logger.info("Successfully fetched GitHub content from: {}", url);
                return content;
            }
            logger.warn("GitHub returned null content for '{}'. Using URL as fallback.", url);
        } catch (Exception e) {
            logger.warn("Error fetching GitHub content from '{}': {}. Using URL as fallback.", url, e.getMessage());
        }
        return url;
    }

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

    private boolean isFilePath(String input) {
        return input.startsWith("/") || input.startsWith("./") || input.startsWith("../");
    }

    private String processFilePath(String input) {
        try {
            Path basePath = Paths.get(workingDirectory);
            Path filePath = input.startsWith("/") ?
                    Paths.get(input) : basePath.resolve(input).normalize();

            logger.debug("Resolving file path '{}' relative to working directory: {} -> {}",
                    input, workingDirectory, filePath);

            FileConfig fileConfig = new FileConfig();
            String fileContent = fileConfig.readFile(filePath.toString());

            if (fileContent != null) {
                logger.debug("Successfully loaded file content from: {}", filePath);
                return fileContent;
            }

            logger.warn("File not found at: {} (resolved from '{}' relative to '{}'), using original value as fallback",
                    filePath, input, workingDirectory);
            return input;
        } catch (RuntimeException e) {
            logger.warn("Error processing file path '{}': {}. Using original value as fallback.",
                    input, e.getMessage());
            return input;
        }
    }
}
