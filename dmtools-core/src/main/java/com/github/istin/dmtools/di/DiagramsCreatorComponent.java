package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.diagram.DiagramsCreator;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {JiraModule.class, AIComponentsModule.class, ConfigurationModule.class})
public interface DiagramsCreatorComponent {
    void inject(DiagramsCreator diagramsCreator);
    AI getAI();
    ApplicationConfiguration getConfiguration();
}
