package com.github.istin.dmtools.common.ai.config;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIPromptConfig {

    public static final String _KEY = "ai_prompt_config";

    // AI Model Configuration
    public static final String MODEL_NAME = "model_name";
    public static final String MODEL_PROVIDER = "model_provider";
    public static final String API_KEY = "api_key";
    
    // Chunk Configuration
    public static final String PROMPT_CHUNK_TOKEN_LIMIT = "prompt_chunk_token_limit";
    public static final String PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE = "prompt_chunk_max_single_file_size";
    public static final String PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE = "prompt_chunk_max_total_files_size";
    public static final String PROMPT_CHUNK_MAX_FILES = "prompt_chunk_max_files";

    public enum ModelProvider {
        OPENAI, ANTHROPIC, GEMINI, AZURE_OPENAI, MISTRAL
    }

    @SerializedName(MODEL_NAME)
    private String modelName;

    @SerializedName(MODEL_PROVIDER)
    private ModelProvider modelProvider;

    @SerializedName(API_KEY)
    private String apiKey;

    @SerializedName(PROMPT_CHUNK_TOKEN_LIMIT)
    private Integer promptChunkTokenLimit;

    @SerializedName(PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE)
    private Long promptChunkMaxSingleFileSize;

    @SerializedName(PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE)
    private Long promptChunkMaxTotalFilesSize;

    @SerializedName(PROMPT_CHUNK_MAX_FILES)
    private Integer promptChunkMaxFiles;

    public boolean isModelConfigured() {
        return modelName != null && modelProvider != null;
    }
    
    public boolean isChunkConfigured() {
        return promptChunkTokenLimit != null 
                || promptChunkMaxSingleFileSize != null 
                || promptChunkMaxTotalFilesSize != null 
                || promptChunkMaxFiles != null;
    }
} 