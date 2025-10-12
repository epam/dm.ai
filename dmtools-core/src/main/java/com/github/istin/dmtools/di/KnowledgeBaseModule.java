package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.agent.KBAggregationAgent;
import com.github.istin.dmtools.common.kb.agent.KBAnalysisAgent;
import com.github.istin.dmtools.common.kb.agent.KBOrchestrator;
import com.github.istin.dmtools.common.kb.agent.KBQuestionAnswerMappingAgent;
import com.github.istin.dmtools.common.kb.tool.KBTools;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.common.utils.PropertyReader;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

/**
 * Dagger module for Knowledge Base components.
 * Provides dependency injection for all KB agents, helpers, and tools.
 */
@Module
public class KnowledgeBaseModule {
    
    @Provides
    @Singleton
    public KBAnalysisAgent provideKBAnalysisAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new KBAnalysisAgent(ai, promptTemplateReader);
    }
    
    @Provides
    @Singleton
    public KBAggregationAgent provideKBAggregationAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new KBAggregationAgent(ai, promptTemplateReader);
    }
    
    @Provides
    @Singleton
    public KBQuestionAnswerMappingAgent provideKBQuestionAnswerMappingAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new KBQuestionAnswerMappingAgent(ai, promptTemplateReader);
    }
    
    @Provides
    @Singleton
    public KBStructureBuilder provideKBStructureBuilder() {
        return new KBStructureBuilder();
    }
    
    @Provides
    @Singleton
    public KBStatistics provideKBStatistics() {
        return new KBStatistics();
    }
    
    @Provides
    @Singleton
    public SourceConfigManager provideSourceConfigManager() {
        return new SourceConfigManager();
    }
    
    @Provides
    @Singleton
    public ContentMergeAgent provideContentMergeAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new ContentMergeAgent(ai, promptTemplateReader);
    }
    
    @Provides
    @Singleton
    public KBAnalysisResultMerger provideKBAnalysisResultMerger(ContentMergeAgent contentMergeAgent) {
        return new KBAnalysisResultMerger(contentMergeAgent);
    }
    
    @Provides
    @Singleton
    public ChunkPreparation provideChunkPreparation() {
        return new ChunkPreparation();
    }
    
    @Provides
    @Singleton
    public KBOrchestrator provideKBOrchestrator(
            KBAnalysisAgent analysisAgent,
            KBStructureBuilder structureBuilder,
            KBAggregationAgent aggregationAgent,
            KBQuestionAnswerMappingAgent qaMappingAgent,
            KBStatistics statistics,
            KBAnalysisResultMerger resultMerger,
            SourceConfigManager sourceConfigManager,
            ChunkPreparation chunkPreparation
    ) {
        return new KBOrchestrator(
                analysisAgent,
                structureBuilder,
                aggregationAgent,
                qaMappingAgent,
                statistics,
                resultMerger,
                sourceConfigManager,
                chunkPreparation
        );
    }
    
    @Provides
    @Singleton
    public KBTools provideKBTools(
            KBOrchestrator orchestrator,
            PropertyReader propertyReader,
            SourceConfigManager sourceConfigManager
    ) {
        return new KBTools(orchestrator, propertyReader, sourceConfigManager);
    }
}


