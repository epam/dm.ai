package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helper class for processing run command arguments.
 * Orchestrates file loading, encoding detection, and configuration merging.
 */
public class RunCommandProcessor {
    
    private static final Logger logger = LogManager.getLogger(RunCommandProcessor.class);
    
    private final EncodingDetector encodingDetector;
    private final ConfigurationMerger configurationMerger;
    
    public RunCommandProcessor() {
        this.encodingDetector = new EncodingDetector();
        this.configurationMerger = new ConfigurationMerger();
    }
    
    // Constructor for testing with dependency injection
    public RunCommandProcessor(EncodingDetector encodingDetector, ConfigurationMerger configurationMerger) {
        this.encodingDetector = encodingDetector;
        this.configurationMerger = configurationMerger;
    }
    
    /**
     * Processes the run command arguments and creates a JobParams object.
     * Handles both syntaxes:
     * - dmtools run [file]
     * - dmtools run [file] [encoded-config]
     * 
     * @param args Command line arguments starting with "run"
     * @return JobParams object ready for job execution
     * @throws IllegalArgumentException if arguments are invalid or processing fails
     */
    public JobParams processRunCommand(String[] args) {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException("Invalid run command arguments. Expected: run [json-file-path] [optional-encoded-config]");
        }
        
        if (!"run".equals(args[0])) {
            throw new IllegalArgumentException("First argument must be 'run'");
        }
        
        String filePath = args[1];
        String encodedConfig = args.length > 2 ? args[2] : null;
        
        logger.info("Processing run command: file={}, hasEncodedConfig={}", filePath, encodedConfig != null);
        
        try {
            // Load JSON from file
            String fileJson = loadJsonFromFile(filePath);
            
            // Process encoded configuration if provided
            String finalConfigJson;
            if (encodedConfig != null && !encodedConfig.trim().isEmpty()) {
                String decodedJson = encodingDetector.autoDetectAndDecode(encodedConfig);
                finalConfigJson = configurationMerger.mergeConfigurations(fileJson, decodedJson);
                logger.info("Configuration merged successfully from file and encoded parameter");
            } else {
                finalConfigJson = fileJson;
                logger.info("Using file configuration only");
            }
            
            // Create JobParams with final merged configuration
            JobParams jobParams = new JobParams(finalConfigJson);
            logger.info("JobParams created successfully for job: {}", jobParams.getName());
            
            return jobParams;
            
        } catch (Exception e) {
            logger.error("Failed to process run command: {}", e.getMessage());
            throw new IllegalArgumentException("Run command processing failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Loads JSON content from the specified file path.
     * 
     * @param filePath Path to the JSON configuration file
     * @return JSON content as string
     * @throws IllegalArgumentException if file cannot be read or doesn't exist
     */
    public String loadJsonFromFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        try {
            Path path = Paths.get(filePath);
            
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Configuration file does not exist: " + filePath);
            }
            
            if (!Files.isReadable(path)) {
            if (content.trim().isEmpty()) {
            }
            
            String content = Files.readString(path);
            
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("Configuration file is empty: " + filePath);
            }
            
            logger.info("Successfully loaded JSON configuration from file: {}", filePath);
            return content;
            
        } catch (IOException e) {
            logger.error("Failed to read configuration file {}: {}", filePath, e.getMessage());
            throw new IllegalArgumentException("Failed to read configuration file: " + e.getMessage(), e);
        }
    }
}
