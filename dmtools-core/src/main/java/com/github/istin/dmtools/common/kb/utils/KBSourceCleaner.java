package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility for cleaning Q/A/N files from a specific source.
 * After deletion, automatically triggers regeneration of person profiles and statistics.
 */
public class KBSourceCleaner {
    
    private final KBFileParser fileParser;
    private final KBStructureManager structureManager;
    
    public KBSourceCleaner(KBFileParser fileParser, KBStructureManager structureManager) {
        this.fileParser = fileParser;
        this.structureManager = structureManager;
    }
    
    /**
     * Clean all Q/A/N files from a specific source.
     * After deletion, regenerates person profiles, topics, and statistics.
     * 
     * @param outputPath KB output directory
     * @param sourceName Source name to clean (e.g., "confluence_page_123")
     * @param logger Logger for progress tracking
     * @return List of deleted file IDs (e.g., ["q_0001", "a_0002", "n_0003"])
     */
    public List<String> cleanSourceFiles(Path outputPath, String sourceName, Logger logger) throws Exception {
        List<String> deletedIds = new ArrayList<>();
        
        if (logger != null) {
            logger.info("Starting source cleanup for: {}", sourceName);
        }
        
        // Clean questions
        deletedIds.addAll(cleanDirectory(outputPath.resolve("questions"), sourceName, "question", logger));
        
        // Clean answers
        deletedIds.addAll(cleanDirectory(outputPath.resolve("answers"), sourceName, "answer", logger));
        
        // Clean notes
        deletedIds.addAll(cleanDirectory(outputPath.resolve("notes"), sourceName, "note", logger));
        
        if (logger != null) {
            logger.info("Deleted {} files from source '{}'", deletedIds.size(), sourceName);
        }
        
        // Regenerate person profiles (will recalculate stats from remaining files)
        if (logger != null) {
            logger.info("Regenerating person profiles after cleanup...");
        }
        structureManager.rebuildPeopleProfiles(outputPath, sourceName, logger);
        
        // Regenerate statistics and indexes (will recalculate topic/area stats)
        if (logger != null) {
            logger.info("Regenerating statistics and indexes after cleanup...");
        }
        structureManager.generateIndexes(outputPath);
        
        if (logger != null) {
            logger.info("Source cleanup completed successfully");
        }
        
        return deletedIds;
    }
    
    /**
     * Clean files from a specific directory that match the source name
     */
    private List<String> cleanDirectory(Path directory, String sourceName, String type, Logger logger) throws IOException {
        List<String> deletedIds = new ArrayList<>();
        
        if (!Files.exists(directory)) {
            return deletedIds;
        }
        
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".md"))
                 .forEach(file -> {
                     try {
                         String content = Files.readString(file);
                         String fileSource = fileParser.extractSource(content);
                         
                         // Check if this file matches the target source
                         if (fileSource != null && fileSource.equals(sourceName)) {
                             String filename = file.getFileName().toString();
                             String id = filename.substring(0, filename.lastIndexOf(".md"));
                             
                             // Delete the file
                             Files.delete(file);
                             deletedIds.add(id);
                             
                             if (logger != null) {
                                 logger.debug("Deleted {} file: {} (source: {})", type, id, fileSource);
                             }
                         }
                     } catch (IOException e) {
                         if (logger != null) {
                             logger.warn("Failed to process file: {}", file, e);
                         }
                     }
                 });
        }
        
        if (logger != null && !deletedIds.isEmpty()) {
            logger.info("Deleted {} {} files from source '{}'", deletedIds.size(), type, sourceName);
        }
        
        return deletedIds;
    }
}

