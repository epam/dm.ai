package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface RelatedTestCaseAgentComponent {
    void inject(RelatedTestCaseAgent relatedTestCaseAgent);
}