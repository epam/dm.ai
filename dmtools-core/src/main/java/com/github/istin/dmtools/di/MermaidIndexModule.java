package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.index.mermaid.tool.MermaidIndexTools;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

/**
 * Dagger module for Mermaid Index components.
 * Provides dependency injection for MermaidIndexTools.
 */
@Module
public class MermaidIndexModule {
    
    @Provides
    @Singleton
    public MermaidIndexTools provideMermaidIndexTools(MermaidDiagramGeneratorAgent diagramGenerator) {
        return new MermaidIndexTools(diagramGenerator);
    }
}
