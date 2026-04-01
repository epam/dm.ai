// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

public class QAProductivityReportParamsTest {

    private QAProductivityReportParams params;
    private JSONObject mockJson;

    @Before
    public void setUp() {
        mockJson = Mockito.mock(JSONObject.class);
        params = Mockito.spy(new QAProductivityReportParams(mockJson));
    }

    @Test
    public void testGetBugsProjectCode() {
        String expected = "bug_project_code_value";
        doReturn(expected).when(params).getString(QAProductivityReportParams.BUGS_PROJECT_CODE);
        String actual = params.getBugsProjectCode();
        assertEquals(expected, actual);
    }

    @Test
    public void testGetTestCasesProjectCode() {
        String expected = "test_cases_project_code_value";
        doReturn(expected).when(params).getString(QAProductivityReportParams.TEST_CASES_PROJECT_CODE);
        String actual = params.getTestCasesProjectCode();
        assertEquals(expected, actual);
    }

    @Test
    public void testGetStatusesDone() {
        String[] expected = {"done1", "done2"};
        doReturn(expected).when(params).getStringArray(QAProductivityReportParams.STATUSES_DONE);
        String[] actual = params.getStatusesDone();
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testGetStatusesInTesting() {
        String[] expected = {"testing1", "testing2"};
        doReturn(expected).when(params).getStringArray(QAProductivityReportParams.STATUSES_IN_TESTING);
        String[] actual = params.getStatusesInTesting();
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testGetStatusesInDevelopment() {
        String[] expected = {"development1", "development2"};
        doReturn(expected).when(params).getStringArray(QAProductivityReportParams.STATUSES_IN_DEVELOPMENT);
        String[] actual = params.getStatusesInDevelopment();
        assertArrayEquals(expected, actual);
    }
}