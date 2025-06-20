package com.github.istin.dmtools.di;

import com.github.istin.dmtools.dev.CommitsTriage;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, JiraModule.class, SourceCodeModule.class, AIComponentsModule.class})
public interface CommitsTriageComponent {
    void inject(CommitsTriage commitsTriage);
}
