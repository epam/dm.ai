// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class TestCasesReportParamsTest {

    private TestCasesReportParams testCasesReportParams;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = Mockito.mock(JSONObject.class);
        testCasesReportParams = spy(new TestCasesReportParams(mockJsonObject));
    }

    @Test
    public void testGetReportName() {
        String expectedReportName = "Sample Report";
        doReturn(expectedReportName).when(testCasesReportParams).getString(TestCasesReportParams.REPORT_NAME);

        String actualReportName = testCasesReportParams.getReportName();
        assertEquals(expectedReportName, actualReportName);
    }

    @Test
    public void testGetStartDate() {
        String expectedStartDate = "2023-10-01";
        doReturn(expectedStartDate).when(testCasesReportParams).getString(TestCasesReportParams.START_DATE);

        String actualStartDate = testCasesReportParams.getStartDate();
        assertEquals(expectedStartDate, actualStartDate);
    }

    @Test
    public void testGetTestCasesProjectCode() {
        String expectedProjectCode = "TC123";
        doReturn(expectedProjectCode).when(testCasesReportParams).getString(TestCasesReportParams.TEST_CASES_PROJECT_CODE);

        String actualProjectCode = testCasesReportParams.getTestCasesProjectCode();
        assertEquals(expectedProjectCode, actualProjectCode);
    }

    @Test
    public void testIsWeight() {
        Boolean expectedIsWeight = true;
        doReturn(expectedIsWeight).when(testCasesReportParams).getBoolean(TestCasesReportParams.IS_WEIGHT);

        Boolean actualIsWeight = testCasesReportParams.isWeight();
        assertEquals(expectedIsWeight, actualIsWeight);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"report_name\":\"Sample Report\",\"is_weight\":true,\"start_date\":\"2023-10-01\",\"test_cases_project_code\":\"TC123\"}";
        TestCasesReportParams params = new TestCasesReportParams(jsonString);

        assertEquals("Sample Report", params.getReportName());
        assertTrue(params.isWeight());
        assertEquals("2023-10-01", params.getStartDate());
        assertEquals("TC123", params.getTestCasesProjectCode());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("report_name", "Sample Report");
        jsonObject.put("is_weight", true);
        jsonObject.put("start_date", "2023-10-01");
        jsonObject.put("test_cases_project_code", "TC123");

        TestCasesReportParams params = new TestCasesReportParams(jsonObject);

        assertEquals("Sample Report", params.getReportName());
        assertTrue(params.isWeight());
        assertEquals("2023-10-01", params.getStartDate());
        assertEquals("TC123", params.getTestCasesProjectCode());
    }

}