package com.github.istin.dmtools.index.mermaid.tool;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.index.mermaid.MermaidIndex;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * MCP Tools for Mermaid Index operations.
 * Provides MCP interface for generating Mermaid diagrams from content sources.
 */
@Singleton
public class MermaidIndexTools {
    
    private static final Logger logger = LogManager.getLogger(MermaidIndexTools.class);
    
    private final MermaidDiagramGeneratorAgent diagramGenerator;
    
    @Inject
    public MermaidIndexTools(MermaidDiagramGeneratorAgent diagramGenerator) {
        this.diagramGenerator = diagramGenerator;
        logger.info("MermaidIndexTools initialized");
    }
    
    /**
     * Generate Mermaid diagrams from Confluence content based on include/exclude patterns.
     * Processes content recursively and stores diagrams in hierarchical file structure.
     * 
     * @param integration Integration type (currently only "confluence" is supported)
     * @param storagePath Base path for storing generated diagrams
     * @param includePatterns Array of include patterns (e.g., ["AINA/pages/123/Page/**"])
     * @param excludePatterns Optional array of exclude patterns
     * @return JSON string with operation result and statistics
     */
    @MCPTool(
        name = "mermaid_index_generate",
        description = "Generate Mermaid diagrams from content sources (e.g., Confluence) based on include/exclude patterns. Processes content recursively and stores diagrams in hierarchical file structure.",
        integration = "mermaid",
        category = "diagram_generation"
    )
    public String mermaidIndexGenerate(
            @MCPParam(
                name = "integration",
                description = "Integration type (currently only 'confluence' is supported)",
                required = true,
                example = "confluence"
            ) String integration,
            @MCPParam(
                name = "storage_path",
                description = "Base path for storing generated diagrams",
                required = true,
                example = "./mermaid-diagrams"
            ) String storagePath,
            @MCPParam(
                name = "include_patterns",
                description = "Array of include patterns. Format: [\"SPACE/pages/PAGE_ID/PAGE_NAME/**\"] for recursive, [\"SPACE/pages/PAGE_ID/PAGE_NAME\"] for single page, [\"SPACE/pages/PAGE_ID/PAGE_NAME/*\"] for immediate children",
                required = true,
                example = "[\"AINA/pages/11665522/Templates/**\"]",
                type = "array"
            ) List<String> includePatterns,
            @MCPParam(
                name = "exclude_patterns",
                description = "Optional array of exclude patterns to filter out specific content",
                required = false,
                example = "[]",
                type = "array"
            ) List<String> excludePatterns
    ) {
        try {
            logger.info("Starting Mermaid index generation: integration={}, storagePath={}, include={}, exclude={}", 
                    integration, storagePath, includePatterns, excludePatterns);
            
            // Validate integration type
            if (!"confluence".equalsIgnoreCase(integration)) {
                return "{\"success\": false, \"error\": \"Unsupported integration: " + integration + ". Only 'confluence' is currently supported.\"}";
            }
            
            // Validate storage path
            if (storagePath == null || storagePath.trim().isEmpty()) {
                return "{\"success\": false, \"error\": \"Storage path is required\"}";
            }
            
            // Validate include patterns
            if (includePatterns == null || includePatterns.isEmpty()) {
                return "{\"success\": false, \"error\": \"At least one include pattern is required\"}";
            }
            
            // Get Confluence instance
            Confluence confluence;
            try {
                confluence = BasicConfluence.getInstance();
                if (confluence == null) {
                    return "{\"success\": false, \"error\": \"Confluence is not configured. Please configure Confluence credentials.\"}";
                }
            } catch (IOException e) {
                logger.error("Failed to get Confluence instance", e);
                return "{\"success\": false, \"error\": \"Failed to get Confluence instance: " + e.getMessage() + "\"}";
            }
            
            // Normalize exclude patterns (handle null)
            List<String> normalizedExclude = excludePatterns != null ? excludePatterns : new ArrayList<>();
            
            // Create MermaidIndex instance
            MermaidIndex mermaidIndex = new MermaidIndex(
                    integration,
                    storagePath,
                    includePatterns,
                    normalizedExclude,
                    confluence,
                    diagramGenerator
            );
            
            // Execute indexing
            mermaidIndex.index();
            
            logger.info("Mermaid index generation completed successfully");
            
            return "{\"success\": true, \"message\": \"Mermaid diagrams generated successfully\", \"integration\": \"" + 
                    integration + "\", \"storagePath\": \"" + storagePath + "\", \"includePatterns\": " + 
                    includePatterns.size() + ", \"excludePatterns\": " + normalizedExclude.size() + "}";
            
        } catch (Exception e) {
            logger.error("Error generating Mermaid index", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }
    
    /**
     * Read all Mermaid diagram files (.mmd) from storage path recursively.
     * Returns a list of ToText objects containing file path and content.
     * 
     * @param integration Integration type (currently only "confluence" is supported)
     * @param storagePath Base path where diagrams are stored
     * @return List of ToText objects, each containing path and content of a diagram file
     * @throws IOException if an error occurs reading files
     */
    @MCPTool(
        name = "mermaid_index_read_list",
        description = "Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of ToText objects with paths and content.",
        integration = "mermaid",
        category = "diagram_retrieval"
    )
    public List<ToText> read(
            @MCPParam(
                name = "integration",
                description = "Integration type (currently only 'confluence' is supported)",
                required = true,
                example = "confluence"
            ) String integration,
            @MCPParam(
                name = "storage_path",
                description = "Base path where diagrams are stored",
                required = true,
                example = "./mermaid-diagrams"
            ) String storagePath
    ) throws IOException {
        logger.info("Reading Mermaid diagrams: integration={}, storagePath={}", integration, storagePath);
        
        // Validate integration type
        if (!"confluence".equalsIgnoreCase(integration)) {
            throw new IllegalArgumentException("Unsupported integration: " + integration + ". Only 'confluence' is currently supported.");
        }
        
        // Validate storage path
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage path is required");
        }
        
        Path storagePathObj = Paths.get(storagePath, integration);
        if (!Files.exists(storagePathObj)) {
            throw new IOException("Storage path does not exist: " + storagePathObj);
        }
        
        // Recursively find all .mmd files
        List<ToText> diagrams = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(storagePathObj)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".mmd"))
                 .forEach(path -> {
                     try {
                         String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                         // Get relative path from storage base
                         String relativePath = storagePathObj.relativize(path).toString().replace(File.separator, "/");
                         diagrams.add(new MermaidDiagramFile(relativePath, content));
                     } catch (IOException e) {
                         logger.warn("Failed to read diagram file {}: {}", path, e.getMessage());
                     }
                 });
        }
        
        logger.info("Found {} Mermaid diagram files", diagrams.size());
        return diagrams;
    }
    
    /**
     * Read all Mermaid diagram files (.mmd) from storage path recursively.
     * Returns JSON string with list of diagram files (path and content).
     * 
     * @param integration Integration type (currently only "confluence" is supported)
     * @param storagePath Base path where diagrams are stored
     * @return JSON string with list of diagram files (path and content)
     */
    @MCPTool(
        name = "mermaid_index_read",
        description = "Read all Mermaid diagram files (.mmd) from storage path recursively. Returns list of diagrams with their paths and content.",
        integration = "mermaid",
        category = "diagram_retrieval"
    )
    public String mermaidIndexRead(
            @MCPParam(
                name = "integration",
                description = "Integration type (currently only 'confluence' is supported)",
                required = true,
                example = "confluence"
            ) String integration,
            @MCPParam(
                name = "storage_path",
                description = "Base path where diagrams are stored",
                required = true,
                example = "./mermaid-diagrams"
            ) String storagePath
    ) {
        try {
            List<ToText> diagrams = read(integration, storagePath);
            
            // Build JSON response
            StringBuilder json = new StringBuilder("{\"success\": true, \"count\": ").append(diagrams.size()).append(", \"diagrams\": [");
            for (int i = 0; i < diagrams.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                MermaidDiagramFile diagram = (MermaidDiagramFile) diagrams.get(i);
                json.append("{\"path\": \"").append(escapeJson(diagram.path)).append("\", ");
                json.append("\"content\": \"").append(escapeJson(diagram.toText())).append("\"}");
            }
            json.append("]}");
            
            return json.toString();
            
        } catch (Exception e) {
            logger.error("Error reading Mermaid index", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }
    
    /**
     * Escapes special characters in JSON strings.
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Represents a Mermaid diagram file with its path and content.
     * Implements ToText interface to return both path and file content.
     */
    private static class MermaidDiagramFile implements ToText {
        private final String path;
        private final String content;
        
        public MermaidDiagramFile(String path, String content) {
            this.path = path;
            this.content = content;
        }
        
        public String getPath() {
            return path;
        }
        
        @Override
        public String toText() throws IOException {
            return "Path: " + path + "\n\n" + content;
        }
    }
}
