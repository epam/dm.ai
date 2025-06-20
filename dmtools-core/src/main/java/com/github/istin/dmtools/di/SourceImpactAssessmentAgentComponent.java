package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.SourceImpactAssessmentAgent;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface SourceImpactAssessmentAgentComponent {
    void inject(SourceImpactAssessmentAgent sourceImpactAssessmentAgent);
}
