package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.model.KBProcessingMode;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.common.kb.utils.*;
import com.github.istin.dmtools.common.utils.LLMOptimizedJson;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import com.github.istin.dmtools.common.kb.model.PersonContributions;
import com.github.istin.dmtools.common.kb.model.Question;
import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Note;

/**
 * Main orchestration service for Knowledge Base building
 * Coordinates all stages: Analysis → Structure → Aggregation → Statistics
 * This is NOT an agent - it's a coordinator that uses agents internally
 */
public class KBOrchestrator {
    
    private static final Logger logger = LogManager.getLogger(KBOrchestrator.class);
    private static final Gson GSON = new Gson();
    
    protected final KBAnalysisAgent analysisAgent;
    protected final KBStructureBuilder structureBuilder;
    protected final KBAggregationAgent aggregationAgent;
    protected final KBQuestionAnswerMappingAgent qaMappingAgent;
    protected final KBStatistics statistics;
    protected final KBAnalysisResultMerger resultMerger;
    protected final SourceConfigManager sourceConfigManager;
    protected final ChunkPreparation chunkPreparation;
    
    // Utility classes
    private final KBFileReader fileReader;
    private final KBFileParser fileParser;
    private final KBContextLoader contextLoader;
    private final PersonStatsCollector statsCollector;
    private final KBAggregationHelper aggregationHelper;
    private final KBRollbackManager rollbackManager;
    private final KBFileUtils fileUtils;
    private final KBStructureManager structureManager;
    private final KBAnalysisValidator analysisValidator;
    private final KBQAMappingService qaMappingService;
    private final KBChunkAnalyzer chunkAnalyzer;
    private final KBRegenerationManager regenerationManager;
    private final KBAggregationBatchHelper aggregationBatchHelper;
    private final KBAggregationMetricsCollector metricsCollector;
    private final KBAggregateOnlyService aggregateOnlyService;
    
    @Inject
    public KBOrchestrator(
            KBAnalysisAgent analysisAgent,
            KBStructureBuilder structureBuilder,
            KBAggregationAgent aggregationAgent,
            KBQuestionAnswerMappingAgent qaMappingAgent,
            KBStatistics statistics,
            KBAnalysisResultMerger resultMerger,
            SourceConfigManager sourceConfigManager,
            ChunkPreparation chunkPreparation
    ) {
        this.analysisAgent = analysisAgent;
        this.structureBuilder = structureBuilder;
        this.aggregationAgent = aggregationAgent;
        this.qaMappingAgent = qaMappingAgent;
        this.statistics = statistics;
        this.resultMerger = resultMerger;
        this.sourceConfigManager = sourceConfigManager;
        this.chunkPreparation = chunkPreparation;
        
        // Initialize utilities
        this.fileReader = new KBFileReader();
        this.fileParser = new KBFileParser();
        this.contextLoader = new KBContextLoader(fileParser);
        this.statsCollector = new PersonStatsCollector(fileParser, structureBuilder);
        this.aggregationHelper = new KBAggregationHelper(aggregationAgent, structureBuilder, contextLoader);
        this.rollbackManager = new KBRollbackManager();
        this.fileUtils = new KBFileUtils();
        this.structureManager = new KBStructureManager(structureBuilder, statsCollector, statistics, contextLoader);
        this.analysisValidator = new KBAnalysisValidator();
        this.qaMappingService = new KBQAMappingService(qaMappingAgent);
        this.chunkAnalyzer = new KBChunkAnalyzer(analysisAgent, resultMerger);
        this.regenerationManager = new KBRegenerationManager(fileParser, structureBuilder, structureManager, contextLoader);
        this.aggregationBatchHelper = new KBAggregationBatchHelper(aggregationHelper);
        this.aggregateOnlyService = new KBAggregateOnlyService(aggregationHelper, structureManager);
        this.metricsCollector = new KBAggregationMetricsCollector();
        
        logger.info("KBOrchestrator initialized with utilities");
    }
    
    /**
     * Main execution method for KB processing
     */
    public KBResult run(KBOrchestratorParams params) throws Exception {
        logger.info("Starting KB orchestration for source: {} (mode: {})", 
                   params.getSourceName(), params.getProcessingMode());
        
        Path outputPath = Paths.get(params.getOutputPath());
        
        // Handle AGGREGATE_ONLY mode separately
        if (params.getProcessingMode() == KBProcessingMode.AGGREGATE_ONLY) {
            return runAggregateOnly(outputPath, params);
        }
        
        // Track created files for potential rollback
        List<Path> createdFiles = new ArrayList<>();
        Path analyzedJsonPath = null;
        
        try {
            // Step 1: Initialize output directories
            contextLoader.initializeOutputDirectories(outputPath, params.isCleanOutput());
            
            // Step 2: Clean source-specific files if requested
            if (params.isCleanSourceBeforeProcessing()) {
                logger.info("Cleaning existing files for source: {}", params.getSourceName());
                KBSourceCleaner sourceCleaner = new KBSourceCleaner(fileParser, structureManager);
                List<String> deletedIds = sourceCleaner.cleanSourceFiles(
                    outputPath, 
                    params.getSourceName(), 
                    logger
                );
                logger.info("Cleaned {} Q/A/N files from source '{}'", 
                    deletedIds.size(), params.getSourceName());
            }
            
            // Step 3: Handle input file location
            Path inputFilePath = Paths.get(params.getInputFile());
            String inputFileName = inputFilePath.getFileName().toString();
            Path rawInboxPath = outputPath.resolve("inbox/raw");
            Files.createDirectories(rawInboxPath);
            
            // Check if file is already in inbox/raw - if not, copy it there
            Path rawCopyPath;
            boolean isAlreadyInInbox = inputFilePath.startsWith(rawInboxPath);
            
            if (isAlreadyInInbox) {
                // File is already in inbox/raw - use it in place
                rawCopyPath = inputFilePath;
                logger.info("Input file is already in inbox/raw, processing in place: {}", rawCopyPath);
            } else {
                // File is external - copy it to inbox/raw
                String timestamp = String.valueOf(System.currentTimeMillis());
                rawCopyPath = rawInboxPath.resolve(timestamp + "_" + inputFileName);
                Files.copy(inputFilePath, rawCopyPath);
                createdFiles.add(rawCopyPath);
                logger.info("Copied external input file to: {}", rawCopyPath);
            }
            
            // Step 4: Load existing KB context
            KBContext context = contextLoader.loadKBContext(outputPath);
            
            // Step 5: Read and prepare input
            String inputContent = fileReader.readAndNormalize(inputFilePath);
            logger.info("Read input file, content length: {} chars", inputContent.length());
            
            // Try to parse as JSON and convert if needed
            inputContent = normalizeInputContent(inputContent, params.getDateTime());
            logger.info("After normalization, content length: {} chars", inputContent.length());
            
            // Step 6: Chunk input if large
            List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(Arrays.asList(inputContent));
            logger.info("Created {} chunks from input", chunks.size());
            
            // Step 7: AI Analysis (process each chunk separately)
            AnalysisResult analysisResult;
            long analysisStartTime = System.currentTimeMillis();
            if (chunks.size() == 1) {
                logger.info("Processing single chunk (size: {} chars)", chunks.get(0).getText().length());
                analysisResult = chunkAnalyzer.analyzeChunk(chunks.get(0).getText(), params.getSourceName(), context, params.getAnalysisExtraInstructions());
            } else {
                int totalSize = chunks.stream().mapToInt(c -> c.getText().length()).sum();
                logger.info("Processing {} chunks (total size: {} chars)", chunks.size(), totalSize);
                analysisResult = chunkAnalyzer.analyzeAndMergeChunks(chunks, params.getSourceName(), context, params.getAnalysisExtraInstructions(), logger);
            }
            long analysisEndTime = System.currentTimeMillis();
            logger.info("✓ AI analysis completed in {}.{} seconds", 
                       (analysisEndTime - analysisStartTime) / 1000,
                       (analysisEndTime - analysisStartTime) % 1000);
            
            // Save analyzed JSON to inbox/analyzed/[source]/[filename]_analyzed.json
            Path analyzedInboxPath = outputPath.resolve("inbox/analyzed").resolve(params.getSourceName());
            Files.createDirectories(analyzedInboxPath);
            
            // Remove extension from input filename and add _analyzed.json suffix
            String baseFileName = inputFileName.replaceAll("\\.[^.]+$", "");
            analyzedJsonPath = analyzedInboxPath.resolve(baseFileName + "_analyzed.json");
            
            String analyzedJson = GSON.toJson(analysisResult);
            Files.writeString(analyzedJsonPath, analyzedJson);
            createdFiles.add(analyzedJsonPath);
            logger.info("Saved analyzed JSON to: {}", analyzedJsonPath);
            logger.info("Analyzed JSON preview: questions={}, answers={}, notes={}", 
                    analysisResult.getQuestions().size(),
                    analysisResult.getAnswers().size(),
                    analysisResult.getNotes().size());
            
            // Step 7.5: Validate and clean up analysis result (filter out incomplete entries)
            analysisValidator.validateAndClean(analysisResult, logger);
            
            // Step 7.6: Map new answers/notes to existing unanswered questions
            qaMappingService.applyMapping(analysisResult, context, params.getQaMappingExtraInstructions(), logger);
            
            // Step 8: Build Structure (mechanical) - track created files
            // NOTE: personContributions will be collected INSIDE buildStructure AFTER ID mapping
            structureManager.buildStructure(analysisResult, outputPath, params.getSourceName(), null, logger);

            // Step 9: AI Aggregation (conditional based on mode)
            long start = System.nanoTime();
            if (params.getProcessingMode() == KBProcessingMode.FULL) {
                logger.info("Running AI aggregation (FULL mode)");
                aggregationBatchHelper.aggregateBatch(analysisResult, outputPath, params.getAggregationExtraInstructions(), logger);
            } else {
                logger.info("Skipping AI aggregation (PROCESS_ONLY mode)");
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            metricsCollector.recordAggregation(durationMs);

            // Step 10: Generate Statistics and Indexes (mechanical)
            structureManager.generateIndexes(outputPath);
            
            // Step 11: Update source config
            sourceConfigManager.updateLastSyncDate(params.getSourceName(), params.getDateTime(), outputPath);

            // Step 12: Build and return result
            return structureManager.buildResult(analysisResult, outputPath, fileUtils);
            
        } catch (Exception e) {
            logger.error("❌ KB processing failed: {}", e.getMessage(), e);
            logger.warn("Rolling back created files...");
            
            // Rollback: delete all created files
            rollbackManager.rollbackCreatedFiles(createdFiles, logger);
            
            // Re-throw exception
            throw new Exception("KB processing failed and rolled back: " + e.getMessage(), e);
        }
    }
    
    /**
     * Regenerate KB structure from existing Q/A/N files without AI processing.
     * Useful after cleanup operations to rebuild topics, areas, and people profiles.
     * 
     * @param outputPath Path to the KB output directory
     * @param sourceName Source name for tagging
     * @return KBResult with regeneration statistics
     */
    public KBResult regenerateStructureFromExistingFiles(Path outputPath, String sourceName) throws Exception {
        return regenerationManager.regenerate(outputPath, sourceName, logger, fileUtils);
    }

    /**
     * Run AGGREGATE_ONLY mode: generate AI descriptions for existing KB structure
     */
    private KBResult runAggregateOnly(Path outputPath, KBOrchestratorParams params) throws Exception {
        boolean smartMode = params != null && params.isSmartAggregation();
        logger.info("Running AGGREGATE_ONLY mode: generating AI descriptions for existing KB (smart mode: {})", smartMode);
        return aggregateOnlyService.aggregateExisting(outputPath, null, fileUtils, logger, smartMode);
    }


    private String normalizeInputContent(String content, String dateTime) {
        // Try to detect if JSON and convert to more friendly format if needed
        try {
            // First check if content is valid JSON
            JsonParser.parseString(content);
            // If we get here, it's valid JSON - format it
            return LLMOptimizedJson.formatSkipEmpty(content, true);
        } catch (Exception e) {
            // Not JSON or failed to parse - check for other formats
            logger.debug("Input is not JSON or failed to parse: {}", e.getMessage());
        }
        
        // Check if content is VTT format
        if (VTTUtils.isVTTFormat(content)) {
            logger.info("Detected VTT format, transforming to clean text");
            
            // Extract date from dateTime if available (format: 2025-10-24T11:02:38.229696Z -> 2025-10-24)
            String date = null;
            if (dateTime != null && !dateTime.trim().isEmpty()) {
                try {
                    date = dateTime.substring(0, 10); // Extract YYYY-MM-DD
                    logger.debug("Extracted date from dateTime: {}", date);
                } catch (Exception ex) {
                    logger.debug("Could not extract date from dateTime: {}", ex.getMessage());
                }
            }
            
            String transformed = VTTUtils.transformVTT(content, date);
            logger.info("VTT transformation complete, new length: {} chars", transformed.length());
            return transformed;
        }
        
        // Return as is (plain text)
        return content;
    }

    /**
     * Collect person contributions from analysis result
     * Returns a map of normalized person names to their contributions (questions, answers, notes with IDs and dates)
     */
}

