package com.github.istin.dmtools.sm;

import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ScrumMasterDailyTest {

    @Mock
    private BasicConfluence mockConfluence;
    @Mock
    private TrackerClient<ITicket> mockTrackerClient;
    @Mock
    private BasicDialAI mockDial;
    @Mock
    private PromptManager mockPromptManager;
    @Mock
    private JAssistant mockJAssistant;
    @Mock
    private Logger mockLogger;

    private ScrumMasterDaily scrumMasterDaily;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        scrumMasterDaily = new ScrumMasterDaily();
    }

}