package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.SourceConfig;
import com.github.istin.dmtools.common.kb.model.SourceInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Manages source configuration for the Knowledge Base
 */
public class SourceConfigManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "source_config.json";
    
    /**
     * Load source configuration from output path
     */
    public SourceConfig loadConfig(Path outputPath) throws IOException {
        Path configFile = getConfigFilePath(outputPath);
        
        if (!Files.exists(configFile)) {
            return new SourceConfig();
        }
        
        String json = Files.readString(configFile);
        return GSON.fromJson(json, SourceConfig.class);
    }
    
    /**
     * Save source configuration to output path
     */
    public void saveConfig(SourceConfig config, Path outputPath) throws IOException {
        Path configFile = getConfigFilePath(outputPath);
        
        // Ensure inbox directory exists
        Files.createDirectories(configFile.getParent());
        
        String json = GSON.toJson(config);
        Files.writeString(configFile, json);
    }
    
    /**
     * Update last sync date for a source
     */
    public void updateLastSyncDate(String sourceName, String date, Path outputPath) throws IOException {
        SourceConfig config = loadConfig(outputPath);
        
        SourceInfo info = config.getSources().computeIfAbsent(sourceName, k -> new SourceInfo());
        info.setLastSyncDate(date);
        info.setUpdatedAt(Instant.now().toString());
        
        saveConfig(config, outputPath);
    }
    
    private Path getConfigFilePath(Path outputPath) {
        return outputPath.resolve("inbox").resolve(CONFIG_FILE_NAME);
    }
}


