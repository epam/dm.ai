package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;
import java.util.List;

public class TeamAssistantAgent extends AbstractSimpleAgent<TeamAssistantAgent.Params, String> {

    @AllArgsConstructor
    @Getter
    public static class Params implements AbstractSimpleAgent.GetFiles, AbstractSimpleAgent.GetChunks {
        private RequestDecompositionAgent.Result request;
        private List<File> files;
        private List<ChunkPreparation.Chunk> chunks;
        private long chunksProcessingTimeout;

        public Params(RequestDecompositionAgent.Result request) {
            this.request = request;
        }

        public Params(RequestDecompositionAgent.Result request, List<File> files) {
            this.request = request;
            this.files = files;
        }

        public Params(RequestDecompositionAgent.Result request, List<File> files, List<ChunkPreparation.Chunk> chunks) {
            this.request = request;
            this.files = files;
            this.chunks = chunks;
        }
    }

    // Default constructor - dependencies will be injected by Dagger
    public TeamAssistantAgent() {
        super("agents/team_assistant");
        // Dependencies injected via constructor or module provider
    }

    // Constructor for server-managed mode with injected dependencies
    public TeamAssistantAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/team_assistant");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        // The response is already in HTML format as per prompt requirements
        return response;
    }
}