package com.github.istin.dmtools.di;

import com.github.istin.dmtools.js.JSRunner;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component for JSRunner.
 * Provides dependency injection for standalone JavaScript execution.
 */
@Singleton
@Component(modules = {
        JiraModule.class,
        ConfluenceModule.class,
        AIComponentsModule.class,
        SourceCodeModule.class,
        AIAgentsModule.class,
        ConfigurationModule.class
})
public interface JSRunnerComponent {
    
    /**
     * Inject dependencies into JSRunner
     */
    void inject(JSRunner jsRunner);
}
