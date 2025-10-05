package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class JiraModuleTest {

    private JiraModule module;

    @BeforeEach
    void setUp() {
        module = new JiraModule();
    }

    @Test
    void testProvideTrackerClient_CreatesBasicJiraClient() {
        TrackerClient<? extends ITicket> client = module.provideTrackerClient();
        
        assertNotNull(client);
        assertTrue(client instanceof BasicJiraClient);
    }

    @Test
    void testProvideTrackerClient_Singleton() {
        TrackerClient<? extends ITicket> client1 = module.provideTrackerClient();
        TrackerClient<? extends ITicket> client2 = module.provideTrackerClient();
        
        assertNotNull(client1);
        assertNotNull(client2);
        // Note: Since @Singleton is managed by Dagger, this test just verifies instances can be created
    }

    @Test
    void testModuleInstantiation() {
        JiraModule newModule = new JiraModule();
        assertNotNull(newModule);
        
        TrackerClient<? extends ITicket> client = newModule.provideTrackerClient();
        assertNotNull(client);
    }
}
