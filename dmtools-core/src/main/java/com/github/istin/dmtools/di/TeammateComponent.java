package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.teammate.Teammate;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {JiraModule.class, SourceCodeModule.class, AIComponentsModule.class, ConfluenceModule.class, AIAgentsModule.class, ConfigurationModule.class, MermaidIndexModule.class})
public interface TeammateComponent {
    void inject(Teammate teammate);
    AI getAI();
    ApplicationConfiguration getConfiguration();
    GenericRequestAgent getGenericRequestAgent();
}
