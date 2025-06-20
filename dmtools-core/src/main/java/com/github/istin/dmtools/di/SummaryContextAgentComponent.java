package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.SummaryContextAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface SummaryContextAgentComponent {
    void inject(SummaryContextAgent summaryContextAgent);
}