package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.prompt.PromptContext;
import lombok.Getter;
import org.json.JSONObject;

import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractSimpleAgent<Params, Result> implements IAgent<Params, Result> {

    public interface GetFiles {
        List<File> getFiles();
    }

    public interface GetChunks {
        List<ChunkPreparation.Chunk> getChunks();

        default long getChunksProcessingTimeout() {
            return 0;
        }
    }

    @Inject
    protected AI ai;

    @Inject
    protected IPromptTemplateReader promptTemplateReader;

    @Getter
    private final String promptName;
    
    /**
     * Default constructor with prompt name
     * @param promptName The name of the prompt template to use
     */
    public AbstractSimpleAgent(String promptName) {
        this.promptName = promptName;
    }
    
    @Override
    public Result run(Params params) throws Exception {
        return executeWithDependencies(params);
    }
    
    /**
     * Executes the agent with the given parameters and dependencies
     * @param params The parameters
     * @return The result
     * @throws Exception If an error occurs
     */
    protected Result executeWithDependencies(Params params) throws Exception {
        PromptContext context = new PromptContext(params);
        
        List<File> files = new ArrayList<>();
        if (params instanceof GetFiles) {
            List<File> fileList = ((GetFiles) params).getFiles();
            if (fileList != null) {
                files.addAll(fileList);
            }
        }
        
        List<ChunkPreparation.Chunk> chunks = new ArrayList<>();
        long chunksProcessingTimeout = 0;
        if (params instanceof GetChunks) {
            GetChunks getChunks = (GetChunks) params;
            List<ChunkPreparation.Chunk> chunksList = getChunks.getChunks();
            if (chunksList != null) {
                chunks.addAll(chunksList);
            }
            chunksProcessingTimeout = getChunks.getChunksProcessingTimeout();
        }

        String prompt = promptTemplateReader.read(promptName, context);

        String response;
        if (!files.isEmpty()) {
            if (files.size() == 1) {
                response = ai.chat(null, prompt, files.get(0));
            } else {
                response = ai.chat(null, prompt, files);
            }
        } else if (!chunks.isEmpty()) {
            // Process chunks one by one
            StringBuilder chunkResponses = new StringBuilder();
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < chunks.size(); i++) {
                ChunkPreparation.Chunk chunk = chunks.get(i);
                
                // Check for timeout
                if (chunksProcessingTimeout > 0 && 
                    System.currentTimeMillis() - startTime > chunksProcessingTimeout) {
                    break;
                }
                
                // Process the chunk
                PromptContext chunkContext = new PromptContext(params);
                chunkContext.set("chunk", chunk);
                chunkContext.set("chunkIndex", i + 1);
                chunkContext.set("totalChunks", chunks.size());
                
                String chunkPrompt = promptTemplateReader.read(promptName, chunkContext);
                String chunkResponse = ai.chat(chunkPrompt);
                
                if (i > 0) {
                    chunkResponses.append("\n\n");
                }
                chunkResponses.append(chunkResponse);
            }
            
            response = chunkResponses.toString();
        } else {
            response = ai.chat(prompt);
        }

        return transformAIResponse(params, response);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Class<Params> getParamsClass() {
        return (Class<Params>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }
    
    /**
     * Default implementation that returns the response as is
     * Override this method to transform the AI response into the result type
     */
    @Override
    public Result transformAIResponse(Params params, String response) throws Exception {
        return (Result) response;
    }
}
