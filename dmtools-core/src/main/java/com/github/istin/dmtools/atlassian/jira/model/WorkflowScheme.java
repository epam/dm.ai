// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class WorkflowScheme extends JSONModel {

    public WorkflowScheme() {
    }

    public WorkflowScheme(String json) throws JSONException {
        super(json);
    }

    public WorkflowScheme(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString("id");
    }

    public String getName() {
        return getString("name");
    }

    public String getDescription() {
        return getString("description");
    }

    public String getDefaultWorkflow() {
        return getString("defaultWorkflow");
    }

    /**
     * Returns a map of issue type ID → workflow name for custom mappings.
     */
    public Map<String, String> getIssueTypeMappings() {
        Map<String, String> result = new HashMap<>();
        JSONObject mappings = getJSONObject("issueTypeMappings");
        if (mappings != null) {
            for (String key : mappings.keySet()) {
                result.put(key, mappings.optString(key));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "WorkflowScheme{id=" + getId() + ", name=" + getName() + "}";
    }
}
