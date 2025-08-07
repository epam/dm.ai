package com.github.istin.dmtools.ba;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.ai.JAssistant;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class BusinessAnalyticDORGenerationTest {

    private BasicJiraClient trackerClientMock;
    private BasicConfluence confluenceMock;
    private JAssistant jAssistantMock;
    private BusinessAnalyticDORGenerationParams paramsMock;

    @Before
    public void setUp() {
        trackerClientMock = mock(BasicJiraClient.class);
        confluenceMock = mock(BasicConfluence.class);
        jAssistantMock = mock(JAssistant.class);
        paramsMock = mock(BusinessAnalyticDORGenerationParams.class);
    }


}