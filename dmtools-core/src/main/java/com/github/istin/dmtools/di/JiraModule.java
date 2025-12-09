package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.IOException;

@Module
public class JiraModule {

    // Deprecated: This method is kept for backward compatibility but components should migrate to TrackerModule
    @Provides
    @Singleton
    TrackerClient<? extends ITicket> provideTrackerClient() {
        try {
            return new BasicJiraClient();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create TrackerClient instance", e);
        }
    }
}