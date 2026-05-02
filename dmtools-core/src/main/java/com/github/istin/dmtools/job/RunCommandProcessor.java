// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class for processing run command arguments.
 * Orchestrates file loading, encoding detection, and configuration merging.
 */
public class RunCommandProcessor {
    
    private static final Logger logger = LogManager.getLogger(RunCommandProcessor.class);
    
    private final EncodingDetector encodingDetector;
    private final ConfigurationMerger configurationMerger;
    private final ParentConfigResolver parentConfigResolver;
    
    public RunCommandProcessor() {
        this.encodingDetector = new EncodingDetector();
        this.configurationMerger = new ConfigurationMerger();
        this.parentConfigResolver = new ParentConfigResolver();
    }
    
    // Constructor for testing with dependency injection
    public RunCommandProcessor(EncodingDetector encodingDetector, ConfigurationMerger configurationMerger) {
        this.encodingDetector = encodingDetector;
        this.configurationMerger = configurationMerger;
        this.parentConfigResolver = new ParentConfigResolver(configurationMerger);
    }

    // Full DI constructor
    public RunCommandProcessor(EncodingDetector encodingDetector, ConfigurationMerger configurationMerger, ParentConfigResolver parentConfigResolver) {
        this.encodingDetector = encodingDetector;
        this.configurationMerger = configurationMerger;
        this.parentConfigResolver = parentConfigResolver;
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
            throw new IllegalArgumentException("Invalid run command arguments. Expected: run [json-file-path] [optional-encoded-config] [--key value ...]");
        }

        if (!"run".equals(args[0])) {
            throw new IllegalArgumentException("First argument must be 'run'");
        }

        String filePath = args[1];

        // Parse optional encoded config and --key value overrides from remaining args.
        // Convention: if args[2] does NOT start with "--" it is treated as the encoded config;
        // all subsequent "--key value" pairs are param overrides applied to the "params" block.
        String encodedConfig = null;
        Map<String, String> cliOverrides = new LinkedHashMap<>();

        int i = 2;
        if (i < args.length && !args[i].startsWith("--")) {
            encodedConfig = args[i];
            i++;
        }
        while (i < args.length) {
            String token = args[i];
            if (token.startsWith("--") && i + 1 < args.length) {
                cliOverrides.put(token.substring(2), args[i + 1]);
                i += 2;
            } else {
                i++; // skip unrecognised tokens
            }
        }

        logger.info("Processing run command: file={}, hasEncodedConfig={}, cliOverrides={}", filePath, encodedConfig != null, cliOverrides.keySet());

        if (filePath.endsWith(".js")) {
            logger.info("Detected JS file, building JSRunner config in memory");
            return buildJSRunnerJobParams(filePath, encodedConfig);
        }

        try {
            if (JobRunner.isKnownJobName(filePath) && !Files.exists(Paths.get(filePath))) {
                logger.info("Detected known job name for run command: {}", filePath);
                return buildDirectJobParams(filePath, encodedConfig, cliOverrides);
            }

            // Load JSON from file
            String fileJson = loadJsonFromFile(filePath);

            // Resolve parent config inheritance (parent.path → deep-merge + override/merge semantics)
            JSONObject resolvedConfig = parentConfigResolver.resolve(new JSONObject(fileJson), Paths.get(filePath));
            fileJson = resolvedConfig.toString();

            return createJobParams(fileJson, encodedConfig, cliOverrides);

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
                throw new IllegalArgumentException("Configuration file is not readable: " + filePath);
            }
            
            String content = Files.readString(path);
            
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("Configuration file is empty: " + filePath);
            }
            
            logger.info("Successfully loaded JSON configuration from file: {}", filePath);
            return content;
            
        } catch (IOException e) {
            logger.error("Failed to read configuration file {}: {}", filePath, e.getMessage());
            throw new IllegalArgumentException("Failed to read configuration file: " + e.getMessage(), e);
        }
    }

    private JobParams buildJSRunnerJobParams(String jsPath, String rawParams) {
        try {
            JSONObject jobParams = new JSONObject();
            if (rawParams != null && !rawParams.trim().isEmpty()) {
                try {
                    jobParams = new JSONObject(rawParams);
                } catch (Exception jsonEx) {
                    String decoded = encodingDetector.autoDetectAndDecode(rawParams);
                    jobParams = new JSONObject(decoded);
                }
            }
            JSONObject params = new JSONObject();
            params.put("jsPath", jsPath);
            params.put("jobParams", jobParams);
            JSONObject root = new JSONObject();
            root.put("name", "JSRunner");
            root.put("params", params);
            return new JobParams(root.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build JSRunner JobParams: " + e.getMessage(), e);
        }
    }

    private JobParams buildDirectJobParams(String jobName, String encodedConfig, Map<String, String> cliOverrides) {
        JSONObject root = new JSONObject();
        root.put("name", jobName);
        root.put(JobParams.PARAMS, new JSONObject());
        return createJobParams(root.toString(), encodedConfig, cliOverrides);
    }

    private JobParams createJobParams(String baseConfigJson, String encodedConfig, Map<String, String> cliOverrides) {
        String finalConfigJson = mergeEncodedConfig(baseConfigJson, encodedConfig);
        finalConfigJson = applyCliOverrides(finalConfigJson, cliOverrides);

        JobParams jobParams = new JobParams(finalConfigJson);
        logger.info("JobParams created successfully for job: {}", jobParams.getName());
        return jobParams;
    }

    private String mergeEncodedConfig(String baseConfigJson, String encodedConfig) {
        if (encodedConfig == null || encodedConfig.trim().isEmpty()) {
            logger.info("Using file/job configuration only");
            return baseConfigJson;
        }

        String decodedJson = encodingDetector.autoDetectAndDecode(encodedConfig);
        String mergedJson = configurationMerger.mergeConfigurations(baseConfigJson, decodedJson);
        logger.info("Configuration merged successfully from base configuration and encoded parameter");
        return mergedJson;
    }

    private String applyCliOverrides(String configJson, Map<String, String> cliOverrides) {
        if (cliOverrides.isEmpty()) {
            return configJson;
        }

        JSONObject root = new JSONObject(configJson);
        JSONObject params = root.optJSONObject(JobParams.PARAMS);
        if (params == null) {
            params = new JSONObject();
            root.put(JobParams.PARAMS, params);
        }
        for (Map.Entry<String, String> entry : cliOverrides.entrySet()) {
            params.put(entry.getKey(), entry.getValue());
        }
        logger.info("Applied {} CLI override(s) to params block: {}", cliOverrides.size(), cliOverrides.keySet());
        return root.toString();
    }
}
