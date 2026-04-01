// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;

public class TestCasesReportParams extends ProductivityJobParams {

    public static String REPORT_NAME = "report_name";
    public static String IS_WEIGHT = "is_weight";
    public static final String START_DATE = "start_date";
    public static final String TEST_CASES_PROJECT_CODE = "test_cases_project_code";

    public TestCasesReportParams() {
    }

    public TestCasesReportParams(String json) throws JSONException {
        super(json);
    }

    public TestCasesReportParams(JSONObject json) {
        super(json);
    }

    public String getReportName() {
        return getString(REPORT_NAME);
    }

    public String getStartDate() {
        return getString(START_DATE);
    }


    public String getTestCasesProjectCode() {
        return getString(TEST_CASES_PROJECT_CODE);
    }

    public Boolean isWeight() {
        return getBoolean(IS_WEIGHT);
    }

    public String getCommentsRegex() {
        return getString("comments_regex");
    }
}