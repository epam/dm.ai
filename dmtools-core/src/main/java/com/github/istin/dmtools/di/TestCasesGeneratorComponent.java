package com.github.istin.dmtools.di;

import com.github.istin.dmtools.qa.TestCasesGenerator;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, TrackerModule.class, AIComponentsModule.class, ConfluenceModule.class, AIAgentsModule.class})
public interface TestCasesGeneratorComponent {
    void inject(TestCasesGenerator testCasesGenerator);
}
