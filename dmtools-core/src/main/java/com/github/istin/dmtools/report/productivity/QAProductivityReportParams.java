// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;

public class QAProductivityReportParams extends ProductivityJobParams {

    public static final String BUGS_PROJECT_CODE = "bugs_project_code";
    public static final String TEST_CASES_PROJECT_CODE = "test_cases_project_code";

    public static String STATUSES_DONE = "statuses_done";

    public static String STATUSES_IN_TESTING = "statuses_in_testing";
    public static String STATUSES_IN_DEVELOPMENT = "statuses_in_development";

    public QAProductivityReportParams() {
    }

    public QAProductivityReportParams(String json) throws JSONException {
        super(json);
    }

    public QAProductivityReportParams(JSONObject json) {
        super(json);
    }

    public String getBugsProjectCode() {
        return getString(BUGS_PROJECT_CODE);
    }

    public String getTestCasesProjectCode() {
        return getString(TEST_CASES_PROJECT_CODE);
    }

    public String[] getStatusesDone() {
        return getStringArray(STATUSES_DONE);
    }

    public String[] getStatusesInTesting() {
        return getStringArray(STATUSES_IN_TESTING);
    }

    public String[] getStatusesInDevelopment() {
        return getStringArray(STATUSES_IN_DEVELOPMENT);
    }

    public String getCommentsRegex() {
        return getString("comments_regex");
    }
}