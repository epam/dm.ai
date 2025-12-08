package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.di.DaggerMermaidDiagramGeneratorAgentComponent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.List;

/**
 * Agent for generating Mermaid diagrams from content text.
 * Follows the AbstractSimpleAgent pattern similar to TestCaseVisualizerAgent.
 * Supports file attachments (images) that are passed directly to the AI model.
 * Supports chunked content processing for large inputs.
 */
public class MermaidDiagramGeneratorAgent extends AbstractSimpleAgent<MermaidDiagramGeneratorAgent.Params, String> {

    @Getter
    @Setter
    public static class Params implements AbstractSimpleAgent.GetFiles, AbstractSimpleAgent.GetChunks {
        private String content;
        private List<File> files;
        private List<ChunkPreparation.Chunk> chunks;
        private long chunksProcessingTimeout;
        
        public Params(String content) {
            this.content = content;
            this.files = null;
            this.chunks = null;
            this.chunksProcessingTimeout = 0;
        }
        
        public Params(String content, List<File> files) {
            this.content = content;
            this.files = files;
            this.chunks = null;
            this.chunksProcessingTimeout = 0;
        }
        
        public Params(String content, List<File> files, List<ChunkPreparation.Chunk> chunks) {
            this.content = content;
            this.files = files;
            this.chunks = chunks;
            this.chunksProcessingTimeout = 0;
        }
        
        public Params(String content, List<File> files, List<ChunkPreparation.Chunk> chunks, long chunksProcessingTimeout) {
            this.content = content;
            this.files = files;
            this.chunks = chunks;
            this.chunksProcessingTimeout = chunksProcessingTimeout;
        }
        
        @Override
        public List<File> getFiles() {
            return files;
        }
        
        @Override
        public List<ChunkPreparation.Chunk> getChunks() {
            return chunks;
        }
        
        @Override
        public long getChunksProcessingTimeout() {
            return chunksProcessingTimeout;
        }
    }

    public MermaidDiagramGeneratorAgent() {
        super("agents/mermaid_diagram_generator");
        DaggerMermaidDiagramGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        return response;
    }
}
