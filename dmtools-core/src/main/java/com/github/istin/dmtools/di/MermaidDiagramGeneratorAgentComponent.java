package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface MermaidDiagramGeneratorAgentComponent {
    void inject(MermaidDiagramGeneratorAgent mermaidDiagramGeneratorAgent);
}
