package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class IssueTypeScheme extends JSONModel {

    public IssueTypeScheme() {
    }

    public IssueTypeScheme(String json) throws JSONException {
        super(json);
    }

    public IssueTypeScheme(JSONObject json) {
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

    public String getDefaultIssueTypeId() {
        return getString("defaultIssueTypeId");
    }

    public List<String> getIssueTypeIds() {
        List<String> result = new ArrayList<>();
        JSONArray arr = getJSONArray("issueTypeIds");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.optString(i));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "IssueTypeScheme{id=" + getId() + ", name=" + getName() + "}";
    }
}
