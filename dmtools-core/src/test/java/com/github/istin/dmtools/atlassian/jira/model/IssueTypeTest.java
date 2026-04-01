// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class IssueTypeTest {

    private IssueType issueType;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = mock(JSONObject.class);
        when(jsonObject.getString("id")).thenReturn("123");
        when(jsonObject.getString("name")).thenReturn("Bug");
        issueType = new IssueType(jsonObject);
    }

    @Test
    public void testGetId() {
        assertEquals("123", issueType.getId());
    }

    @Test
    public void testGetName() {
        assertEquals("Bug", issueType.getName());
    }

    @Test
    public void testIsBug() {
        assertTrue(issueType.isBug());
    }

    @Test
    public void testIsBugStatic() {
        assertTrue(IssueType.isBug("Bug"));
        assertTrue(IssueType.isBug("Defect"));
        assertTrue(IssueType.isBug("Incident"));
        assertFalse(IssueType.isBug("Feature"));
    }

    @Test
    public void testIsTestCaseStatic() {
        assertTrue(IssueType.isTestCase("Test"));
        assertTrue(IssueType.isTestCase("Test Case"));
        assertTrue(IssueType.isTestCase("Testcase"));
        assertFalse(IssueType.isTestCase("Feature"));
    }

    @Test
    public void testIsTaskStatic() {
        assertTrue(IssueType.isTask("Task"));
        assertFalse(IssueType.isTask("Feature"));
    }

    @Test
    public void testIsSubTaskStatic() {
        assertTrue(IssueType.isSubTask("Sub-task"));
        assertFalse(IssueType.isSubTask("Feature"));
    }

    @Test
    public void testIsExternalDeliveryStatic() {
        assertTrue(IssueType.isExternalDelivery("External Delivery"));
        assertFalse(IssueType.isExternalDelivery("Feature"));
    }

    @Test
    public void testIsEpicStatic() {
        assertTrue(IssueType.isEpic("Epic"));
        assertFalse(IssueType.isEpic("Feature"));
    }

    @Test
    public void testIsStoryStatic() {
        assertTrue(IssueType.isStory("Story"));
        assertFalse(IssueType.isStory("Feature"));
    }

    @Test
    public void testIsEpic() {
        when(jsonObject.getString("name")).thenReturn("Epic Story");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isEpic());
    }

    @Test
    public void testIsTest() {
        when(jsonObject.getString("name")).thenReturn("Test Case");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isTest());
    }

    @Test
    public void testIsStory() {
        when(jsonObject.getString("name")).thenReturn("User Story");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isStory());
    }

    @Test
    public void testIsProductStory() {
        when(jsonObject.getString("name")).thenReturn("Product Story");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isProductStory());
    }

    @Test
    public void testIsQuestion() {
        when(jsonObject.getString("name")).thenReturn("Question");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isQuestion());
    }

    @Test
    public void testIsDependency() {
        when(jsonObject.getString("name")).thenReturn("Dependency");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isDependency());
    }

    @Test
    public void testIsClarification() {
        when(jsonObject.getString("name")).thenReturn("Clarification");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isClarification());
    }

    @Test
    public void testIsDesignTask() {
        when(jsonObject.getString("name")).thenReturn("Design Task");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isDesignTask());
    }

    @Test
    public void testIsImpactAssessment() {
        when(jsonObject.getString("name")).thenReturn("Impact Assessment");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isImpactAssessment());
    }

    @Test
    public void testIsConfigurationTask() {
        when(jsonObject.getString("name")).thenReturn("Configuration Task");
        issueType = new IssueType(jsonObject);
        assertTrue(issueType.isConfigurationTask());
    }
}