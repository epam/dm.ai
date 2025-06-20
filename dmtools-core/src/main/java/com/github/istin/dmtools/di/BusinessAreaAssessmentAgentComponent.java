package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.BusinessAreaAssessmentAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface BusinessAreaAssessmentAgentComponent {
    void inject(BusinessAreaAssessmentAgent businessAreaAssessmentAgent);
}