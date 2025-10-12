package com.github.istin.dmtools.common.kb.tool;

import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.agent.KBOrchestrator;
import com.github.istin.dmtools.common.kb.model.KBProcessingMode;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.model.SourceConfig;
import com.github.istin.dmtools.common.kb.model.SourceInfo;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * MCP Tools for Knowledge Base operations.
 * Provides CLI interface for building and querying indexed knowledge bases.
 */
@Singleton
public class KBTools {
    
    private static final Logger logger = LogManager.getLogger(KBTools.class);
    
    protected final KBOrchestrator orchestrator;
    protected final PropertyReader propertyReader;
    protected final SourceConfigManager sourceConfigManager;
    
    @Inject
    public KBTools(
            KBOrchestrator orchestrator,
            PropertyReader propertyReader,
            SourceConfigManager sourceConfigManager
    ) {
        this.orchestrator = orchestrator;
        this.propertyReader = propertyReader;
        this.sourceConfigManager = sourceConfigManager;
        logger.info("KBTools initialized");
    }
    
    /**
     * Get last sync date for a knowledge base source.
     * 
     * @param sourceName Name of the data source (e.g., "teams_chat", "slack_general")
     * @param outputPath Optional path to KB directory. If not provided, uses DMTOOLS_KB_OUTPUT_PATH env var or current directory
     * @return Last sync date in ISO 8601 format, or "Source not found" message
     */
    @MCPTool(
        name = "kb_get",
        description = "Get last sync date for a knowledge base source. Returns ISO 8601 date string or 'Source not found' message.",
        integration = "kb"
    )
    public String kbGet(
            @MCPParam(
                name = "source_name",
                description = "Name of the data source (e.g., 'teams_chat', 'slack_general')",
                required = true,
                example = "teams_chat"
            ) String sourceName,
            @MCPParam(
                name = "output_path",
                description = "Optional path to KB directory. Defaults to DMTOOLS_KB_OUTPUT_PATH env var or current directory",
                required = false,
                example = "/path/to/knowledge-base"
            ) String outputPath
    ) {
        try {
            Path kbPath = resolveOutputPath(outputPath);
            logger.info("Checking source '{}' in KB at: {}", sourceName, kbPath);
            
            SourceConfig config = sourceConfigManager.loadConfig(kbPath);
            SourceInfo info = config.getSources().get(sourceName);
            
            if (info == null) {
                return "Source not found: " + sourceName;
            }
            
            return info.getLastSyncDate();
            
        } catch (Exception e) {
            logger.error("Error getting source info for '{}': {}", sourceName, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Build or update knowledge base from input file.
     * 
     * @param sourceName Name of the data source
     * @param inputFile Path to input file (JSON or text format)
     * @param dateTime Sync date/time in ISO 8601 format
     * @param outputPath Optional path to KB directory. If not provided, uses DMTOOLS_KB_OUTPUT_PATH env var or current directory
     * @return JSON string with build results including counts and status
     */
    @MCPTool(
        name = "kb_build",
        description = "Build or update knowledge base from input file. Processes chat messages, documentation, or any text data to create indexed, searchable knowledge base. Returns JSON with statistics.",
        integration = "kb"
    )
    public String kbBuild(
            @MCPParam(
                name = "source_name",
                description = "Name of the data source (e.g., 'teams_chat', 'slack_general')",
                required = true,
                example = "teams_chat"
            ) String sourceName,
            @MCPParam(
                name = "input_file",
                description = "Path to input file (JSON or text format). Can be JSON array of messages or plain text.",
                required = true,
                example = "/path/to/messages.json"
            ) String inputFile,
            @MCPParam(
                name = "date_time",
                description = "Sync date/time in ISO 8601 format (e.g., '2024-10-10T12:00:00Z')",
                required = true,
                example = "2024-10-10T12:00:00Z"
            ) String dateTime,
            @MCPParam(
                name = "output_path",
                description = "Optional path to KB directory. Defaults to DMTOOLS_KB_OUTPUT_PATH env var or current directory",
                required = false,
                example = "/path/to/knowledge-base"
            ) String outputPath
    ) {
        try {
            // Validate input file exists
            Path inputPath = Paths.get(inputFile);
            if (!Files.exists(inputPath)) {
                return "{\"success\": false, \"message\": \"Input file not found: " + inputFile + "\"}";
            }
            
            Path kbPath = resolveOutputPath(outputPath);
            logger.info("Building KB from source '{}' at: {}", sourceName, kbPath);
            logger.info("Input file: {}", inputFile);
            logger.info("Sync date: {}", dateTime);
            
            // Create orchestrator params
            KBOrchestratorParams params = new KBOrchestratorParams();
            params.setSourceName(sourceName);
            params.setInputFile(inputFile);
            params.setDateTime(dateTime);
            params.setOutputPath(kbPath.toString());
            params.setProcessingMode(KBProcessingMode.FULL);
            
            // Run orchestrator
            KBResult result = orchestrator.run(params);
            
            // Convert result to JSON
            return formatResult(result);
            
        } catch (Exception e) {
            logger.error("Error building KB for source '{}': {}", sourceName, e.getMessage(), e);
            return "{\"success\": false, \"message\": \"Error: " + escapeJson(e.getMessage()) + "\"}";
        }
    }
    
    /**
     * Process input file without generating AI descriptions (fast mode).
     * 
     * @param sourceName Name of the data source
     * @param inputFile Path to input file (JSON or text format)
     * @param dateTime Sync date/time in ISO 8601 format
     * @param outputPath Optional path to KB directory. If not provided, uses DMTOOLS_KB_OUTPUT_PATH env var or current directory
     * @return JSON string with build results including counts and status
     */
    @MCPTool(
        name = "kb_process",
        description = "Process input file and build KB structure WITHOUT generating AI descriptions (fast mode). Use this for bulk data processing. Run kb_aggregate later to generate descriptions.",
        integration = "kb"
    )
    public String kbProcess(
            @MCPParam(
                name = "source_name",
                description = "Name of the data source (e.g., 'teams_chat', 'slack_general')",
                required = true,
                example = "teams_chat"
            ) String sourceName,
            @MCPParam(
                name = "input_file",
                description = "Path to input file (JSON or text format). Can be JSON array of messages or plain text.",
                required = true,
                example = "/path/to/messages.json"
            ) String inputFile,
            @MCPParam(
                name = "date_time",
                description = "Sync date/time in ISO 8601 format (e.g., '2024-10-10T12:00:00Z')",
                required = true,
                example = "2024-10-10T12:00:00Z"
            ) String dateTime,
            @MCPParam(
                name = "output_path",
                description = "Optional path to KB directory. Defaults to DMTOOLS_KB_OUTPUT_PATH env var or current directory",
                required = false,
                example = "/path/to/knowledge-base"
            ) String outputPath
    ) {
        try {
            // Validate input file exists
            Path inputPath = Paths.get(inputFile);
            if (!Files.exists(inputPath)) {
                return "{\"success\": false, \"message\": \"Input file not found: " + inputFile + "\"}";
            }
            
            Path kbPath = resolveOutputPath(outputPath);
            logger.info("Processing KB (PROCESS_ONLY mode) from source '{}' at: {}", sourceName, kbPath);
            logger.info("Input file: {}", inputFile);
            logger.info("Sync date: {}", dateTime);
            
            // Create orchestrator params
            KBOrchestratorParams params = new KBOrchestratorParams();
            params.setSourceName(sourceName);
            params.setInputFile(inputFile);
            params.setDateTime(dateTime);
            params.setOutputPath(kbPath.toString());
            params.setProcessingMode(KBProcessingMode.PROCESS_ONLY);
            
            // Run orchestrator
            KBResult result = orchestrator.run(params);
            
            // Convert result to JSON
            return formatResult(result);
            
        } catch (Exception e) {
            logger.error("Error processing KB for source '{}': {}", sourceName, e.getMessage(), e);
            return "{\"success\": false, \"message\": \"Error: " + escapeJson(e.getMessage()) + "\"}";
        }
    }
    
    /**
     * Generate AI descriptions for existing KB structure (without processing new data).
     * 
     * @param sourceName Name of the data source (required for proper tagging)
     * @param outputPath Optional path to KB directory. If not provided, uses DMTOOLS_KB_OUTPUT_PATH env var or current directory
     * @return JSON string with build results including counts and status
     */
    @MCPTool(
        name = "kb_aggregate",
        description = "Generate AI descriptions for existing KB structure WITHOUT processing new data. Use this after kb_process to add AI-generated descriptions for people and topics.",
        integration = "kb"
    )
    public String kbAggregate(
            @MCPParam(
                name = "source_name",
                description = "Name of the data source (required for proper tagging)",
                required = true,
                example = "teams_chat"
            ) String sourceName,
            @MCPParam(
                name = "output_path",
                description = "Optional path to KB directory. Defaults to DMTOOLS_KB_OUTPUT_PATH env var or current directory",
                required = false,
                example = "/path/to/knowledge-base"
            ) String outputPath
    ) {
        try {
            Path kbPath = resolveOutputPath(outputPath);
            logger.info("Generating AI descriptions (AGGREGATE_ONLY mode) for KB at: {}", kbPath);
            
            // Create orchestrator params
            KBOrchestratorParams params = new KBOrchestratorParams();
            params.setSourceName(sourceName);  // Can be null for all sources
            params.setOutputPath(kbPath.toString());
            params.setProcessingMode(KBProcessingMode.AGGREGATE_ONLY);
            
            // Run orchestrator
            KBResult result = orchestrator.run(params);
            
            // Convert result to JSON
            return formatResult(result);
            
        } catch (Exception e) {
            logger.error("Error aggregating KB: {}", e.getMessage(), e);
            return "{\"success\": false, \"message\": \"Error: " + escapeJson(e.getMessage()) + "\"}";
        }
    }
    
    /**
     * Resolve output path based on priority:
     * 1. Provided outputPath parameter
     * 2. DMTOOLS_KB_OUTPUT_PATH environment variable
     * 3. Current working directory
     */
    private Path resolveOutputPath(String outputPath) {
        if (outputPath != null && !outputPath.trim().isEmpty()) {
            return Paths.get(outputPath.trim()).toAbsolutePath().normalize();
        }
        
        String envPath = propertyReader.getValue("DMTOOLS_KB_OUTPUT_PATH");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return Paths.get(envPath.trim()).toAbsolutePath().normalize();
        }
        
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
    
    /**
     * Format KBResult as JSON string
     */
    private String formatResult(KBResult result) {
        return String.format(
                "{\"success\": %s, \"message\": \"%s\", " +
                "\"topics\": %d, \"themes\": %d, \"questions\": %d, \"answers\": %d, \"notes\": %d, \"people\": %d}",
                result.isSuccess(),
                escapeJson(result.getMessage()),
                result.getTopicsCount(),
                result.getThemesCount(),
                result.getQuestionsCount(),
                result.getAnswersCount(),
                result.getNotesCount(),
                result.getPeopleCount()
        );
    }
    
    /**
     * Escape special characters for JSON string
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

