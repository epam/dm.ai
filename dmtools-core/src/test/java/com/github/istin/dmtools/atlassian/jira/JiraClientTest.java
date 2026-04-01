// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.jira.model.SearchResult;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JiraClientTest {

    private JiraClient<Ticket> jiraClient;
    private OkHttpClient mockClient;
    private GenericRequest mockRequest;

    @Before
    public void setUp() throws IOException {
        mockClient = mock(OkHttpClient.class);
        jiraClient = new JiraClient<Ticket>("http://example.com", "auth") {
            @Override
            public String getTextFieldsOnly(ITicket ticket) {
                return ticket.getTicketDescription();
            }

            @Override
            public String[] getDefaultQueryFields() {
                return new String[0];
            }

            @Override
            public String[] getExtendedQueryFields() {
                return new String[0];
            }

            @Override
            public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
                return List.of();
            }

            @Override
            public TextType getTextType() {
                return null;
            }

            @Override
            public Ticket createTicket(String body) {
                return new Ticket(body);
            }

        };
        mockRequest = mock(GenericRequest.class);
    }

    @Test
    public void testParseJiraProject() {
        String projectKey = "TEST-123";
        String expected = "TEST";
        String actual = JiraClient.parseJiraProject(projectKey);
        assertEquals(expected, actual);
    }

    @Test
    public void testSetClearCache() throws IOException {
        jiraClient.setClearCache(true);
        // Verify cache initialization logic
    }

    @Test
    public void testGetTicketBrowseUrl() {
        String ticketKey = "TEST-123";
        String expectedUrl = "http://example.com/browse/TEST-123";
        String actualUrl = jiraClient.getTicketBrowseUrl(ticketKey);
        assertEquals(expectedUrl, actualUrl);
    }


    @Test
    public void testAddLabelIfNotExists() throws IOException {
        ITicket mockTicket = mock(ITicket.class);
        when(mockTicket.getTicketLabels()).thenReturn(new JSONArray());
        when(mockTicket.getKey()).thenReturn("PROJECT-123");

        JiraClient<Ticket> spyClient = spy(jiraClient);
        doNothing().when(spyClient).log(anyString());

        spyClient.addLabelIfNotExists(mockTicket, "new-label");
        // Verify label addition logic
    }


    @Test
    public void testSearchAndPerform() throws Exception {
        JiraClient<Ticket> spyClient = spy(jiraClient);
        doReturn(new SearchResult()).when(spyClient).search(anyString(), anyInt(), any());

        List<Ticket> tickets = spyClient.searchAndPerform("query", new String[]{"field1", "field2"});
        assertNotNull(tickets);
    }

    @Test
    public void testSearchAndPerformWithNullFields() throws Exception {
        JiraClient<Ticket> spyClient = spy(jiraClient);
        doReturn(new SearchResult()).when(spyClient).search(anyString(), anyInt(), any());

        // This should not throw ClassCastException
        List<Ticket> tickets = spyClient.searchAndPerform("query", null);
        assertNotNull(tickets);
    }

    @Test
    public void testSearchAndPerformWithEmptyFields() throws Exception {
        JiraClient<Ticket> spyClient = spy(jiraClient);
        doReturn(new SearchResult()).when(spyClient).search(anyString(), anyInt(), any());

        // This should not throw ClassCastException
        List<Ticket> tickets = spyClient.searchAndPerform("query", new String[]{});
        assertNotNull(tickets);
    }

    @Test
    public void testUpdateDescription() throws IOException {
        String ticketKey = "TEST-123";
        String description = "New description";

        JiraClient<Ticket> spyClient = spy(jiraClient);
        doReturn(mockRequest).when(spyClient).getTicket(anyString());
        when(mockRequest.put()).thenReturn("Success");

        String result = spyClient.updateDescription(ticketKey, description);
        assertEquals("Success", result);
    }

    @Test
    public void testUpdateField_MultiselectCustomFieldWrapsStringValueAsOptionArray() throws IOException {
        JiraClient<Ticket> spyClient = spy(jiraClient);
        doNothing().when(spyClient).log(anyString());
        doReturn(mockRequest).when(spyClient).getTicket("TEST-123");
        doReturn(new JSONArray()
                .put(new JSONObject()
                        .put("id", "customfield_10091")
                        .put("name", "Dependencies")
                        .put("schema", new JSONObject()
                                .put("type", "array")
                                .put("items", "option")
                                .put("custom", "com.atlassian.jira.plugin.system.customfieldtypes:multiselect")))
                .toString()).when(spyClient).getFields("TEST");
        when(mockRequest.put()).thenReturn("");

        spyClient.updateField("TEST-123", "customfield_10091", "Option A");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRequest).setBody(bodyCaptor.capture());

        JSONObject requestBody = new JSONObject(bodyCaptor.getValue());
        JSONArray setValue = requestBody.getJSONObject("update")
                .getJSONArray("customfield_10091")
                .getJSONObject(0)
                .getJSONArray("set");

        assertEquals(1, setValue.length());
        assertEquals("Option A", setValue.getJSONObject(0).getString("value"));
    }

    @Test
    public void testUpdateField_DoesNotLogSuccessWhenJiraReturnsErrorResponse() throws IOException {
        JiraClient<Ticket> spyClient = spy(jiraClient);
        doNothing().when(spyClient).log(anyString());
        doReturn(mockRequest).when(spyClient).getTicket("TEST-123");
        doReturn(new JSONArray()
                .put(new JSONObject()
                        .put("id", "customfield_10091")
                        .put("name", "Dependencies")
                        .put("schema", new JSONObject()
                                .put("type", "array")
                                .put("items", "option")
                                .put("custom", "com.atlassian.jira.plugin.system.customfieldtypes:multiselect")))
                .toString()).when(spyClient).getFields("TEST");
        when(mockRequest.put()).thenReturn("{\"errorMessages\":[\"Option is not valid\"]}");

        try {
            spyClient.updateField("TEST-123", "customfield_10091", "Option A");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Option is not valid"));
        }

        verify(spyClient, never()).log(startsWith("Updated field"));
    }

    @Test
    public void testUpdateAllFieldsWithName_DoesNotReportSuccessForErrorResponses() throws IOException {
        JiraClient<Ticket> spyClient = spy(jiraClient);
        doNothing().when(spyClient).log(anyString());
        doReturn(mockRequest).when(spyClient).getTicket("TEST-123");
        doReturn(new JSONArray()
                .put(new JSONObject()
                        .put("id", "customfield_10091")
                        .put("name", "Dependencies")
                        .put("schema", new JSONObject()
                                .put("type", "array")
                                .put("items", "option")
                                .put("custom", "com.atlassian.jira.plugin.system.customfieldtypes:multiselect")))
                .toString()).when(spyClient).getFields("TEST");
        when(mockRequest.put()).thenReturn("{\"errorMessages\":[\"Option is not valid\"]}");

        String result = spyClient.updateAllFieldsWithName("TEST-123", "Dependencies", "Option A");

        assertTrue(result.contains("❌ Failed customfield_10091"));
        assertTrue(result.contains("Updated 0 of 1 fields with name 'Dependencies'"));
        verify(spyClient, never()).log(startsWith("Updated field"));
    }

    @Test
    public void testBuildJQL() {
        Collection<String> keys = Arrays.asList("TEST-1", "TEST-2");
        String expectedJQL = "key in (TEST-1,TEST-2)";
        String actualJQL = JiraClient.buildJQL(keys);
        assertEquals(expectedJQL, actualJQL);
    }

    @Test
    public void testSeparateExpandFields_ChangelogMovedToExpand() throws Exception {
        Method method = JiraClient.class.getDeclaredMethod("separateExpandFields", List.class);
        method.setAccessible(true);

        List<String> fields = Arrays.asList("summary", "status", "changelog");
        String[] result = (String[]) method.invoke(jiraClient, fields);

        assertFalse("fields param must not contain 'changelog'", result[0].contains("changelog"));
        assertTrue("fields param must contain 'summary'", result[0].contains("summary"));
        assertTrue("fields param must contain 'status'", result[0].contains("status"));
        assertEquals("expand param must be 'changelog'", "changelog", result[1]);
    }

    @Test
    public void testSeparateExpandFields_NoExpandFields() throws Exception {
        Method method = JiraClient.class.getDeclaredMethod("separateExpandFields", List.class);
        method.setAccessible(true);

        List<String> fields = Arrays.asList("summary", "status", "assignee");
        String[] result = (String[]) method.invoke(jiraClient, fields);

        assertTrue("fields param must contain all fields", result[0].contains("summary") && result[0].contains("status"));
        assertNull("expand param must be null when no expand fields", result[1]);
    }

    @Test
    public void testSeparateExpandFields_OnlyExpandField() throws Exception {
        Method method = JiraClient.class.getDeclaredMethod("separateExpandFields", List.class);
        method.setAccessible(true);

        List<String> fields = Arrays.asList("changelog");
        String[] result = (String[]) method.invoke(jiraClient, fields);

        assertTrue("fields param must be empty", result[0].isEmpty());
        assertEquals("expand param must be 'changelog'", "changelog", result[1]);
    }

    @Test
    public void testSeparateExpandFields_CaseInsensitive() throws Exception {
        Method method = JiraClient.class.getDeclaredMethod("separateExpandFields", List.class);
        method.setAccessible(true);

        List<String> fields = Arrays.asList("summary", "CHANGELOG");
        String[] result = (String[]) method.invoke(jiraClient, fields);

        assertFalse("fields param must not contain 'CHANGELOG'", result[0].toLowerCase().contains("changelog"));
        assertEquals("expand param must be 'changelog'", "changelog", result[1]);
    }

    @Test
    public void testBuildJQLUrl() {
        String basePath = "http://example.com";
        String jql = "key in (TEST-1,TEST-2)";
        String expectedUrl = "http://example.com/issues/?jql=key+in+%28TEST-1%2CTEST-2%29";
        String actualUrl = JiraClient.buildJQLUrl(basePath, jql);
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void testTag() {
        String basePath = "http://example.com";
        String notifierId = "user123";
        String notifierName = "User Name";
        String expectedTag = "<a class=\"user-hover\" href=\"http://example.com/secure/ViewProfile.jspa?name=user123\" rel=\"user123\">User Name</a>";
        String actualTag = JiraClient.tag(basePath, notifierId, notifierName);
        assertEquals(expectedTag, actualTag);
    }

    @Test
    public void testIsValidImageUrl() throws IOException {
        String validUrl = "http://example.com/image.png";
        String invalidUrl = "http://example.com/file.txt";

        JiraClient<Ticket> spyClient = spy(jiraClient);
        doReturn(true).when(spyClient).isImageAttachment(anyString());

        assertTrue(spyClient.isValidImageUrl(validUrl));
    }

}
