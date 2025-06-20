package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {AIComponentsModule.class, ConfigurationModule.class})
public interface AIComponent {
    AI ai();
    IPromptTemplateReader promptTemplateReader();
} 