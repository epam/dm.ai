package com.github.istin.dmtools.di;

import com.github.istin.dmtools.presentation.PresentationMakerOrchestrator;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class, PresentationAgentsModule.class})
public interface PresentationMakerOrchestratorComponent {
    void inject(PresentationMakerOrchestrator presentationMakerOrchestrator);
}