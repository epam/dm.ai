// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.testrail;

import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.qa.CustomTestCasesTrackerParams;
import com.github.istin.dmtools.qa.TestCasesGeneratorParams;
import com.github.istin.dmtools.testrail.model.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TestRailTestCasesAdapterTest {

    @Mock
    private TestRailClient mockClient;

    private TestRailTestCasesAdapter adapter;

    private static final String PROJECT_NAME = "My Project";
    private static final String BASE_PATH = "https://example.testrail.com";

    @Before
    public void setUp() {
        CustomTestCasesTrackerParams config = new CustomTestCasesTrackerParams();
        config.setType("testrail");
        JSONObject params = new JSONObject();
        JSONArray projectNames = new JSONArray();
        projectNames.put(PROJECT_NAME);
        params.put("projectNames", projectNames);
        params.put("creationMode", "simple");
        config.setParams(params);

        adapter = new TestRailTestCasesAdapter(mockClient, config);
    }

    // ----- getExistingCases -----

    @Test
    public void testGetExistingCasesAggregatesAcrossProjects() throws Exception {
        CustomTestCasesTrackerParams config = new CustomTestCasesTrackerParams();
        config.setType("testrail");
        JSONObject configParams = new JSONObject();
        JSONArray projectNames = new JSONArray();
        projectNames.put("Project A");
        projectNames.put("Project B");
        configParams.put("projectNames", projectNames);
        configParams.put("creationMode", "simple");
        config.setParams(configParams);
        TestRailTestCasesAdapter multiProjectAdapter = new TestRailTestCasesAdapter(mockClient, config);

        TestCase tc1 = new TestCase(BASE_PATH, new JSONObject("{\"id\":1,\"title\":\"TC1\"}"));
        TestCase tc2 = new TestCase(BASE_PATH, new JSONObject("{\"id\":2,\"title\":\"TC2\"}"));
        TestCase tc3 = new TestCase(BASE_PATH, new JSONObject("{\"id\":3,\"title\":\"TC3\"}"));

        when(mockClient.getAllCases("Project A")).thenReturn(Arrays.asList(tc1, tc2));
        when(mockClient.getAllCases("Project B")).thenReturn(Arrays.asList(tc3));

        List<ITicket> result = multiProjectAdapter.getExistingCases();

        assertEquals(3, result.size());
        verify(mockClient).getAllCases("Project A");
        verify(mockClient).getAllCases("Project B");
    }

    @Test
    public void testGetExistingCasesEmptyProjectNames() throws Exception {
        CustomTestCasesTrackerParams config = new CustomTestCasesTrackerParams();
        config.setType("testrail");
        JSONObject configParams = new JSONObject();
        configParams.put("projectNames", new JSONArray());
        configParams.put("creationMode", "simple");
        config.setParams(configParams);
        TestRailTestCasesAdapter emptyAdapter = new TestRailTestCasesAdapter(mockClient, config);

        List<ITicket> result = emptyAdapter.getExistingCases();
        assertTrue(result.isEmpty());
        verifyNoInteractions(mockClient);
    }

    // ----- getLinkedCases -----

    @Test
    public void testGetLinkedCasesCallsCasesByRefPerProject() throws Exception {
        TestCase tc1 = new TestCase(BASE_PATH, new JSONObject("{\"id\":10,\"title\":\"Linked TC\"}"));
        when(mockClient.getCasesByRefs("PROJ-123", PROJECT_NAME)).thenReturn(Arrays.asList(tc1));

        List<ITicket> result = adapter.getLinkedCases("PROJ-123");

        assertEquals(1, result.size());
        assertEquals("C10", result.get(0).getKey());
        verify(mockClient).getCasesByRefs("PROJ-123", PROJECT_NAME);
    }

    @Test
    public void testGetLinkedCasesAggregatesAcrossMultipleProjects() throws Exception {
        CustomTestCasesTrackerParams config = new CustomTestCasesTrackerParams();
        config.setType("testrail");
        JSONObject configParams = new JSONObject();
        JSONArray projectNames = new JSONArray();
        projectNames.put("Project A");
        projectNames.put("Project B");
        configParams.put("projectNames", projectNames);
        configParams.put("creationMode", "simple");
        config.setParams(configParams);
        TestRailTestCasesAdapter multiProjectAdapter = new TestRailTestCasesAdapter(mockClient, config);

        TestCase tc1 = new TestCase(BASE_PATH, new JSONObject("{\"id\":10,\"title\":\"TC A\"}"));
        TestCase tc2 = new TestCase(BASE_PATH, new JSONObject("{\"id\":20,\"title\":\"TC B\"}"));
        when(mockClient.getCasesByRefs("PROJ-123", "Project A")).thenReturn(Arrays.asList(tc1));
        when(mockClient.getCasesByRefs("PROJ-123", "Project B")).thenReturn(Arrays.asList(tc2));

        List<ITicket> result = multiProjectAdapter.getLinkedCases("PROJ-123");

        assertEquals(2, result.size());
    }

    // ----- createTestCase (simple mode) -----

    @Test
    public void testCreateTestCaseSimpleMode() throws Exception {
        TestCaseGeneratorAgent.TestCase tc = new TestCaseGeneratorAgent.TestCase("High", "Login test", "Verify login");
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        String responseJson = "{\"id\":42,\"title\":\"Login test\"}";
        // simple mode now delegates to createCaseDetailed so typeId/labelIds are applied
        when(mockClient.createCaseDetailed(eq(PROJECT_NAME), eq("Login test"),
                eq("Verify login"), isNull(), isNull(),
                eq("3"), isNull(), eq("PROJ-100"), isNull()))
                .thenReturn(responseJson);
        when(mockClient.createTicket(responseJson)).thenReturn(new TestCase(BASE_PATH, new JSONObject(responseJson)));

        ITicket created = adapter.createTestCase(tc, "PROJ-100", params);

        assertNotNull(created);
        assertEquals("C42", created.getKey());
        verify(mockClient).createCaseDetailed(PROJECT_NAME, "Login test",
                "Verify login", null, null, "3", null, "PROJ-100", null);
    }

    @Test
    public void testCreateTestCaseDetailedMode() throws Exception {
        CustomTestCasesTrackerParams config = new CustomTestCasesTrackerParams();
        config.setType("testrail");
        JSONObject configParams = new JSONObject();
        JSONArray projectNames = new JSONArray();
        projectNames.put(PROJECT_NAME);
        configParams.put("projectNames", projectNames);
        configParams.put("creationMode", "detailed");
        configParams.put("typeId", "5");
        configParams.put("labelIds", "7,8");
        config.setParams(configParams);
        TestRailTestCasesAdapter detailedAdapter = new TestRailTestCasesAdapter(mockClient, config);

        JSONObject customFields = new JSONObject();
        customFields.put("custom_preconds", "Preconditions");
        customFields.put("custom_steps", "Steps");
        customFields.put("custom_expected", "Expected");
        TestCaseGeneratorAgent.TestCase tc = new TestCaseGeneratorAgent.TestCase("Critical", "Detailed test", "Desc", customFields);
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();

        String responseJson = "{\"id\":55,\"title\":\"Detailed test\"}";
        when(mockClient.createCaseDetailed(eq(PROJECT_NAME), eq("Detailed test"),
                eq("Preconditions"), eq("Steps"), eq("Expected"),
                eq("4"), eq("5"), eq("PROJ-200"), eq("7,8")))
                .thenReturn(responseJson);
        when(mockClient.createTicket(responseJson)).thenReturn(new TestCase(BASE_PATH, new JSONObject(responseJson)));

        ITicket created = detailedAdapter.createTestCase(tc, "PROJ-200", params);

        assertEquals("C55", created.getKey());
        verify(mockClient).createCaseDetailed(PROJECT_NAME, "Detailed test",
                "Preconditions", "Steps", "Expected", "4", "5", "PROJ-200", "7,8");
    }

    @Test
    public void testCreateTestCaseStepsMode() throws Exception {
        CustomTestCasesTrackerParams config = new CustomTestCasesTrackerParams();
        config.setType("testrail");
        JSONObject configParams = new JSONObject();
        JSONArray projectNames = new JSONArray();
        projectNames.put(PROJECT_NAME);
        configParams.put("projectNames", projectNames);
        configParams.put("creationMode", "steps");
        configParams.put("typeId", "7");
        config.setParams(configParams);
        TestRailTestCasesAdapter stepsAdapter = new TestRailTestCasesAdapter(mockClient, config);

        JSONObject customFields = new JSONObject();
        customFields.put("custom_preconds", "Preconditions");
        customFields.put("custom_steps_json", "[{\"content\":\"Open page\",\"expected\":\"Page visible\"}]");
        TestCaseGeneratorAgent.TestCase tc = new TestCaseGeneratorAgent.TestCase("Medium", "Steps test", "Desc", customFields);
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();

        String responseJson = "{\"id\":77,\"title\":\"Steps test\"}";
        when(mockClient.createCaseSteps(eq(PROJECT_NAME), eq("Steps test"),
                eq("Preconditions"), eq("[{\"content\":\"Open page\",\"expected\":\"Page visible\"}]"),
                eq("2"), eq("7"), eq("PROJ-300"), isNull()))
                .thenReturn(responseJson);
        when(mockClient.createTicket(responseJson)).thenReturn(new TestCase(BASE_PATH, new JSONObject(responseJson)));

        ITicket created = stepsAdapter.createTestCase(tc, "PROJ-300", params);

        assertEquals("C77", created.getKey());
    }

    // ----- linkToSource -----

    @Test
    public void testLinkToSourceStripsC_Prefix() throws IOException {
        when(mockClient.linkToRequirement(eq("123"), eq("PROJ-456"))).thenReturn("{}");

        adapter.linkToSource("C123", "PROJ-456", "relates to");

        verify(mockClient).linkToRequirement("123", "PROJ-456");
    }

    @Test
    public void testLinkToSourceWithoutC_Prefix() throws IOException {
        when(mockClient.linkToRequirement(eq("99"), eq("PROJ-1"))).thenReturn("{}");

        adapter.linkToSource("99", "PROJ-1", "is tested by");

        verify(mockClient).linkToRequirement("99", "PROJ-1");
    }

    // ----- Priority conversion -----

    @Test
    public void testCreateTestCasePriorityMapping_Critical() throws Exception {
        TestCaseGeneratorAgent.TestCase tc = new TestCaseGeneratorAgent.TestCase("Critical", "Test", "Desc");
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        String response = "{\"id\":1,\"title\":\"Test\"}";
        when(mockClient.createCaseDetailed(any(), any(), any(), any(), any(), eq("4"), any(), any(), any())).thenReturn(response);
        when(mockClient.createTicket(response)).thenReturn(new TestCase(BASE_PATH, new JSONObject(response)));

        adapter.createTestCase(tc, "KEY-1", params);
        verify(mockClient).createCaseDetailed(any(), any(), any(), any(), any(), eq("4"), any(), any(), any());
    }

    @Test
    public void testCreateTestCasePriorityMapping_Low() throws Exception {
        TestCaseGeneratorAgent.TestCase tc = new TestCaseGeneratorAgent.TestCase("Low", "Test", "Desc");
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        String response = "{\"id\":2,\"title\":\"Test\"}";
        when(mockClient.createCaseDetailed(any(), any(), any(), any(), any(), eq("1"), any(), any(), any())).thenReturn(response);
        when(mockClient.createTicket(response)).thenReturn(new TestCase(BASE_PATH, new JSONObject(response)));

        adapter.createTestCase(tc, "KEY-2", params);
        verify(mockClient).createCaseDetailed(any(), any(), any(), any(), any(), eq("1"), any(), any(), any());
    }

    @Test
    public void testCreateTestCasePriorityMapping_Medium() throws Exception {
        TestCaseGeneratorAgent.TestCase tc = new TestCaseGeneratorAgent.TestCase("Medium", "Test", "Desc");
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        String response = "{\"id\":3,\"title\":\"Test\"}";
        when(mockClient.createCaseDetailed(any(), any(), any(), any(), any(), eq("2"), any(), any(), any())).thenReturn(response);
        when(mockClient.createTicket(response)).thenReturn(new TestCase(BASE_PATH, new JSONObject(response)));

        adapter.createTestCase(tc, "KEY-3", params);
        verify(mockClient).createCaseDetailed(any(), any(), any(), any(), any(), eq("2"), any(), any(), any());
    }
}
