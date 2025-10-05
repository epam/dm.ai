package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.UriToObjectFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfluenceModuleTest {

    private ConfluenceModule module;

    @BeforeEach
    void setUp() {
        module = new ConfluenceModule();
    }

    @Test
    void testProvideConfluence_CreatesBasicConfluence() {
        Confluence confluence = module.provideConfluence();
        
        assertNotNull(confluence);
        assertTrue(confluence instanceof BasicConfluence);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProvideUriToObjectFactory_CreatesFactory() {
        TrackerClient<? extends ITicket> mockTrackerClient = (TrackerClient<? extends ITicket>) mock(TrackerClient.class);
        Confluence mockConfluence = mock(Confluence.class);
        SourceCodeFactory mockSourceCodeFactory = mock(SourceCodeFactory.class);
        
        UriToObjectFactory factory = module.provideUriToObjectFactory(
            mockTrackerClient, 
            mockConfluence, 
            mockSourceCodeFactory
        );
        
        assertNotNull(factory);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProvideUriToObjectFactory_WithNullDependencies() {
        // UriToObjectFactory accepts null parameters, so this should not throw
        TrackerClient<? extends ITicket> nullTracker = null;
        Confluence nullConfluence = null;
        SourceCodeFactory nullFactory = null;
        
        UriToObjectFactory factory = module.provideUriToObjectFactory(
            nullTracker, 
            nullConfluence, 
            nullFactory
        );
        
        assertNotNull(factory, "Factory should be created even with null dependencies");
    }

    @Test
    void testProvideConfluence_Singleton() {
        Confluence confluence1 = module.provideConfluence();
        Confluence confluence2 = module.provideConfluence();
        
        assertNotNull(confluence1);
        assertNotNull(confluence2);
        // Both should be BasicConfluence instances
        assertTrue(confluence1 instanceof BasicConfluence);
        assertTrue(confluence2 instanceof BasicConfluence);
    }

    @Test
    void testModuleInstantiation() {
        ConfluenceModule newModule = new ConfluenceModule();
        assertNotNull(newModule);
        
        Confluence confluence = newModule.provideConfluence();
        assertNotNull(confluence);
    }
}
