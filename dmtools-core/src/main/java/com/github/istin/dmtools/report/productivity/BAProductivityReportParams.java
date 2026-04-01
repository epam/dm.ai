// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;

public class BAProductivityReportParams extends ProductivityJobParams {

    public static final String FEATURE_PROJECT_CODE = "feature_project_code";

    public static final String STORY_PROJECT_CODE = "story_project_code";

    public static String STATUSES_DONE = "statuses_done";

    public static String STATUSES_IN_PROGRESS = "statuses_in_progress";
    public static String FIGMA_FILES = "figma_files";

    public BAProductivityReportParams() {
    }

    public BAProductivityReportParams(String json) throws JSONException {
        super(json);
    }

    public BAProductivityReportParams(JSONObject json) {
        super(json);
    }

    public String getFeatureProjectCode() {
        return getString(FEATURE_PROJECT_CODE);
    }

    public String getStoryProjectCode() {
        return getString(STORY_PROJECT_CODE);
    }

    public String[] getStatusesDone() {
        return getStringArray(STATUSES_DONE);
    }

    public String[] getStatusesInProgress() {
        return getStringArray(STATUSES_IN_PROGRESS);
    }

    public String[] getFigmaFiles() {
        return getStringArray(FIGMA_FILES);
    }

}