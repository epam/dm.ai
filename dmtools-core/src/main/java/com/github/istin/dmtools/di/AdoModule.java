package com.github.istin.dmtools.di;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.IOException;

/**
 * Dagger module for Azure DevOps client dependency injection.
 * Provides singleton instance of BasicAzureDevOpsClient as TrackerClient.
 */
@Module
public class AdoModule {

    @Provides
    @Singleton
    TrackerClient<? extends ITicket> provideTrackerClient() {
        try {
            return BasicAzureDevOpsClient.getInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create BasicAzureDevOpsClient instance. " +
                    "Please ensure ADO_ORGANIZATION, ADO_PROJECT, and ADO_PAT_TOKEN are configured.", e);
        }
    }
}

