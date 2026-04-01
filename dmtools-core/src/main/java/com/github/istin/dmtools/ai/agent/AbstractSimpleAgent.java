// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.AIFileFilter;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.params.JSONFixParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.prompt.PromptContext;
import lombok.Getter;
import org.json.JSONException;
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

    private JSONObject agentContext;

    /**
     * Default constructor with prompt name
     * @param promptName The name of the prompt template to use
     */
    public AbstractSimpleAgent(String promptName) {
        this.promptName = promptName;
        this.agentContext = new JSONObject().put(AI.AgentParams.AGENT_PROMPT, promptName);
    }
    
    @Override
    public Result run(Params params) throws Exception {
        return run(null, params);
    }

    @Override
    public Result run(String model, Params params) throws Exception {
        return executeWithDependencies(model, params);
    }
    
    /**
     * Executes the agent with the given parameters and dependencies
     * @param params The parameters
     * @return The result
     * @throws Exception If an error occurs
     */
    protected Result executeWithDependencies(String model, Params params) throws Exception {
        PromptContext context = new PromptContext(params);
        
        List<File> files = new ArrayList<>();
        if (params instanceof GetFiles) {
            List<File> fileList = ((GetFiles) params).getFiles();
            if (fileList != null) {
                files.addAll(fileList);
            }
        }
        // Apply size/extension filter before sending files to any AI client
        files = new AIFileFilter().filter(files);
        
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



        String response;
        String prompt;
        if (!chunks.isEmpty()) {
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
                chunkContext.set("chunkIndex", i);
                chunkContext.set("totalChunks", chunks.size());
                
                String chunkPrompt = promptTemplateReader.read(promptName, chunkContext);
                String chunkResponse = ai.chat(model, chunkPrompt, agentContext);
                
                if (i > 0) {
                    chunkResponses.append("\n\n");
                }
                chunkResponses.append(chunkResponse);
            }
            PromptContext chunkContext = new PromptContext(params);
            chunkContext.set("chunk", new ChunkPreparation.Chunk(chunkResponses.toString(), null, 0));
            chunkContext.set("chunkIndex", chunks.size());
            chunkContext.set("totalChunks", chunks.size());

            String chunkPrompt = promptTemplateReader.read(promptName, chunkContext);
            prompt = chunkPrompt;
            response = ai.chat(model, chunkPrompt, agentContext);
        } else {
            context.set("chunkIndex", -1);
            prompt = promptTemplateReader.read(promptName, context);
            if (!files.isEmpty()) {
                if (files.size() == 1) {
                    response = ai.chat(model, prompt, files.get(0), agentContext);
                } else {
                    response = ai.chat(model, prompt, files, agentContext);
                }
            } else {
                response = ai.chat(model, prompt, agentContext);
            }
        }

        try {
            return transformAIResponse(params, response);
        } catch (JSONException e) {
            JSONFixParams jsonFixParams = new JSONFixParams();
            jsonFixParams.setMalformedJson(response);
            jsonFixParams.setErrorMessage(e.toString());
            jsonFixParams.setExpectedSchema(prompt);
            return transformAIResponse(params, new JSONFixAgent(ai, promptTemplateReader).run(jsonFixParams));
        }
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
