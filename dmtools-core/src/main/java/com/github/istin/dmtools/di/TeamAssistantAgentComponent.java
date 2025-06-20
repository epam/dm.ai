package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.TeamAssistantAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface TeamAssistantAgentComponent {
    void inject(TeamAssistantAgent teamAssistantAgent);
}