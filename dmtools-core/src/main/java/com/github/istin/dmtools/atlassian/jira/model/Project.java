// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Project extends JSONModel {

    private static final String ID = "id";
    private static final String KEY = "key";
    private static final String NAME = "name";
    private static final String STYLE = "style";
    private static final String ISSUE_TYPES = "issueTypes";

    public Project() {
    }

    public Project(String json) throws JSONException {
        super(json);
    }

    public Project(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString(ID);
    }

    public String getKey() {
        return getString(KEY);
    }

    public String getName() {
        return getString(NAME);
    }

    /** Returns "next-gen" for team-managed projects, "classic" for company-managed. */
    public String getStyle() {
        return getString(STYLE);
    }

    public boolean isNextGen() {
        return "next-gen".equalsIgnoreCase(getStyle());
    }

    /** Issue types embedded in the project details response (all project types). */
    public JSONArray getIssueTypesJson() {
        return getJSONArray(ISSUE_TYPES);
    }

    public String getLeadAccountId() {
        JSONObject lead = getJSONObject("lead");
        if (lead != null) {
            return lead.optString("accountId", null);
        }
        return null;
    }

    public String getProjectTypeKey() {
        return getString("projectTypeKey");
    }
}