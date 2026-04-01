// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;

public class AIAgentsReportParams extends ProductivityJobParams {

    public static final String COMMENTS_REGEX = "comments_regex";
    public static String REPORT_NAME = "report_name";
    public static String IS_WEIGHT = "is_weight";
    public static final String START_DATE = "start_date";

    public AIAgentsReportParams() {
    }

    public AIAgentsReportParams(String json) throws JSONException {
        super(json);
    }

    public AIAgentsReportParams(JSONObject json) {
        super(json);
    }

    public String getReportName() {
        return getString(REPORT_NAME);
    }

    public String getStartDate() {
        return getString(START_DATE);
    }


    public Boolean isWeight() {
        return getBoolean(IS_WEIGHT);
    }

    public String getCommentsRegex() {
        return getString(COMMENTS_REGEX);
    }
}