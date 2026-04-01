// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sa;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class SolutionArchitectureCreatorParams extends JSONModel {

    public static final String STORIES_JQL = "storiesJql";
    public static final String LABEL_NAME_TO_MARK_AS_REVIEWED = "labelNameToMarkAsReviewed";
    public static final String ROLE_SPECIFIC = "roleSpecific";
    public static final String PROJECT_SPECIFIC = "projectSpecific";

    public SolutionArchitectureCreatorParams() {
    }

    public SolutionArchitectureCreatorParams(String json) throws JSONException {
        super(json);
    }

    public SolutionArchitectureCreatorParams(JSONObject json) {
        super(json);
    }

    public String getStoriesJql() {
        return getString(STORIES_JQL);
    }

    public String getLabelNameToMarkAsReviewed() {
        return getString(LABEL_NAME_TO_MARK_AS_REVIEWED);
    }

    public String getRoleSpecific() {
        return getString(ROLE_SPECIFIC);
    }

    public String getProjectSpecific() {
        return getString(PROJECT_SPECIFIC);
    }
}
