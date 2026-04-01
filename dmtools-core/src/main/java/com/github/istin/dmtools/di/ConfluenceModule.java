package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.UriToObjectFactory;
import dagger.Module;
import dagger.Provides;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Singleton;
import java.io.IOException;

@Module
public class ConfluenceModule {

    private static final Logger logger = LogManager.getLogger(ConfluenceModule.class);

    @Provides
    @Singleton
    Confluence provideConfluence() {
        try {
            return BasicConfluence.getInstance();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Confluence instance", e);
        }
    }

    @Provides
    UriToObjectFactory provideUriToObjectFactory(TrackerClient<? extends ITicket> trackerClient, 
                                                 Confluence confluence, 
                                                 SourceCodeFactory sourceCodeFactory) {
        logger.debug("[ConfluenceModule] Creating UriToObjectFactory for standalone mode");
        return new UriToObjectFactory(trackerClient, confluence, sourceCodeFactory);
    }
}