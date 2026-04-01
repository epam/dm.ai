// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class BasicJiraClientTest {

    private BasicJiraClient basicJiraClient;

    @Before
    public void setUp() throws IOException {
        basicJiraClient = Mockito.spy(new BasicJiraClient());
    }

    @Test
    public void testGetDefaultQueryFields() {
        String[] expectedFields = {
                "summary", "status", "attachment", "updated", "created", "creator", "reporter",
                "components", "issuetype", "fixVersions", "customfield_10004", "labels", "priority", "parent"
        };
        String[] actualFields = basicJiraClient.getDefaultQueryFields();
        List<String> actualFieldsList = Arrays.asList(actualFields);
        
        // Verify that all expected fields are present in the actual result
        for (String expectedField : expectedFields) {
            assertTrue("Expected field '" + expectedField + "' should be present in default query fields", 
                      actualFieldsList.contains(expectedField));
        }
        
        // Verify that we have at least the minimum expected number of fields
        assertTrue("Default query fields should contain at least " + expectedFields.length + " fields", 
                  actualFields.length >= expectedFields.length);
    }

    @Test
    public void testGetExtendedQueryFields() {
        String[] expectedFields = {
                "description", "issuelinks", "summary", "status", "attachment", "updated", "created", "creator",
                "reporter", "components", "issuetype", "fixVersions", "customfield_10004", "labels",
                "priority", "parent"
        };
        String[] actualFields = basicJiraClient.getExtendedQueryFields();
        List<String> actualFieldsList = Arrays.asList(actualFields);
        
        // Verify that all expected fields are present in the actual result
        for (String expectedField : expectedFields) {
            assertTrue("Expected field '" + expectedField + "' should be present in extended query fields", 
                      actualFieldsList.contains(expectedField));
        }
        
        // Verify that we have at least the minimum expected number of fields
        assertTrue("Extended query fields should contain at least " + expectedFields.length + " fields", 
                  actualFields.length >= expectedFields.length);
    }

    @Test
    public void testGetTestCases() throws IOException {
        ITicket mockTicket = mock(ITicket.class);
        List<? extends ITicket> testCases = basicJiraClient.getTestCases(mockTicket, "Test Case");
        assertNotNull(testCases);
        assertEquals(0, testCases.size());
    }

    @Test
    public void testGetTextType() {
        assertEquals(TrackerClient.TextType.MARKDOWN, basicJiraClient.getTextType());
    }

    @Test
    public void testDeleteCommentIfExists() throws IOException {
        // Placeholder for testing deleteCommentIfExists method
        // TODO: Implement test logic for deleteCommentIfExists
    }
}