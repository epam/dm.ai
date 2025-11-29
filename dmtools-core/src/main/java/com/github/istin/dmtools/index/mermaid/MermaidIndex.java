package com.github.istin.dmtools.index.mermaid;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.index.ConfluenceMermaidIndexIntegration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;

/**
 * Core orchestration class for Mermaid indexing.
 * Manages file system operations, diagram generation, and coordinates with integrations.
 */
public class MermaidIndex {
    
    private static final Logger logger = LogManager.getLogger(MermaidIndex.class);
    
    private final String integrationName;
    private final String storagePath;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final MermaidIndexIntegration integration;
    private final MermaidDiagramGeneratorAgent diagramGenerator;
    
    /**
     * Creates a new MermaidIndex instance.
     * 
     * @param integrationName Name of the integration (e.g., "confluence")
     * @param storagePath Base path for storing generated diagrams
     * @param includePatterns List of patterns to include
     * @param excludePatterns List of patterns to exclude
     * @param confluence Confluence instance (required for confluence integration)
     * @param diagramGenerator Diagram generator agent
     */
    public MermaidIndex(String integrationName, String storagePath, 
                       List<String> includePatterns, List<String> excludePatterns,
                       Confluence confluence, MermaidDiagramGeneratorAgent diagramGenerator) {
        this.integrationName = integrationName;
        this.storagePath = storagePath;
        this.includePatterns = includePatterns;
        this.excludePatterns = excludePatterns;
        this.diagramGenerator = diagramGenerator;
        
        // Create integration instance based on name
        if ("confluence".equalsIgnoreCase(integrationName)) {
            if (confluence == null) {
                throw new IllegalArgumentException("Confluence instance is required for confluence integration");
            }
            this.integration = new ConfluenceMermaidIndexIntegration(confluence);
        } else {
            throw new IllegalArgumentException("Unsupported integration: " + integrationName);
        }
    }
    
    /**
     * Executes the indexing process.
     * Retrieves content from the integration and generates diagrams for matching items.
     */
    public void index() throws Exception {
        logger.info("Starting Mermaid indexing for integration: {}", integrationName);
        
        integration.getContentForIndex(includePatterns, excludePatterns, (pathOrId, contentName, content, metadata, lastModified) -> {
            try {
                processContent(pathOrId, contentName, content, metadata, lastModified);
            } catch (Exception e) {
                logger.error("Error processing content {}: {}", pathOrId, e.getMessage(), e);
            }
        });
        
        logger.info("Mermaid indexing completed");
    }
    
    /**
     * Processes a single content item.
     * Checks if diagram needs to be generated/updated and creates it if necessary.
     */
    private void processContent(String pathOrId, String contentName, String content, 
                               List<String> metadata, Date lastModified) throws Exception {
        // Parse pathOrId to extract spaceKey and pageId
        // Format: spaceKey/pageId
        String[] parts = pathOrId.split("/", 2);
        if (parts.length != 2) {
            logger.warn("Invalid pathOrId format: {}, expected spaceKey/pageId", pathOrId);
            return;
        }
        
        String spaceKey = sanitizePath(parts[0]);
        String pageId = sanitizePath(parts[1]);
        String sanitizedContentName = sanitizeFileName(contentName);
        
        // Build file path: storagePath/integrationName/spaceKey/pageId/pageTitle.mmd
        Path baseDir = Paths.get(storagePath, integrationName, spaceKey, pageId);
        Path diagramPath = baseDir.resolve(sanitizedContentName + ".mmd");
        
        // Check if file exists and compare modification time
        if (Files.exists(diagramPath)) {
            try {
                long fileModTime = Files.getLastModifiedTime(diagramPath).toMillis();
                long contentModTime = lastModified.getTime();
                
                // If file was created after content was last modified, skip
                if (fileModTime >= contentModTime) {
                    logger.debug("Skipping {} - file is up to date", diagramPath);
                    return;
                }
            } catch (Exception e) {
                logger.warn("Failed to check modification time for {}: {}", diagramPath, e.getMessage());
            }
        }
        
        // Generate diagram
        logger.info("Generating diagram for: {}", diagramPath);
        String diagram = diagramGenerator.run(new MermaidDiagramGeneratorAgent.Params(content));
        
        // Create directory if it doesn't exist
        Files.createDirectories(baseDir);
        
        // Write diagram to file
        Files.write(diagramPath, diagram.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Set file modification time to match content last modified date
        try {
            Files.setLastModifiedTime(diagramPath, java.nio.file.attribute.FileTime.fromMillis(lastModified.getTime()));
        } catch (Exception e) {
            logger.warn("Failed to set modification time for {}: {}", diagramPath, e.getMessage());
        }
        
        // Process attachments if metadata contains attachment references
        // Attachments will be stored in the same folder (future enhancement)
        logger.debug("Processed diagram for: {}", diagramPath);
    }
    
    /**
     * Sanitizes a path component to be filesystem-safe.
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return "unknown";
        }
        // Replace invalid filesystem characters
        return path.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Sanitizes a filename to be filesystem-safe.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "untitled";
        }
        // Replace invalid filesystem characters, limit length
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Limit to 200 characters to avoid filesystem issues
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        return sanitized;
    }
}
