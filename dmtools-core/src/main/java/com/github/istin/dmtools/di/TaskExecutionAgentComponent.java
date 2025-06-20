package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.TaskExecutionAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface TaskExecutionAgentComponent {
    void inject(TaskExecutionAgent taskExecutionAgent);
}