// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a GitHub issue returned by the REST API.
 */
public class GitHubIssue extends JSONModel {

    public GitHubIssue() {
    }

    public GitHubIssue(String json) throws JSONException {
        super(json);
    }

    public GitHubIssue(JSONObject json) {
        super(json);
    }

    public Integer getNumber() {
        return getInt("number");
    }

    public String getTitle() {
        return getString("title");
    }

    public String getBody() {
        return getString("body");
    }

    public String getState() {
        return getString("state");
    }

    public String getHtmlUrl() {
        return getString("html_url");
    }

    public IUser getAuthor() {
        return getModel(GitHubUser.class, "user");
    }

    public List<String> getLabels() {
        JSONArray labelsArray = getJSONArray("labels");
        if (labelsArray == null) {
            return Collections.emptyList();
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < labelsArray.length(); i++) {
            JSONObject label = labelsArray.optJSONObject(i);
            if (label != null) {
                String name = label.optString("name", null);
                if (name != null) {
                    labels.add(name);
                }
            }
        }
        return labels;
    }

    public List<IUser> getAssignees() {
        return getModels(GitHubUser.class, "assignees");
    }

    public Integer getCommentsCount() {
        return getInt("comments");
    }

    public Long getCreatedAt() {
        String value = getString("created_at");
        return value != null ? DateUtils.parseIsoDate(value).getTime() : null;
    }

    public Long getUpdatedAt() {
        String value = getString("updated_at");
        return value != null ? DateUtils.parseIsoDate(value).getTime() : null;
    }

    public Long getClosedAt() {
        String value = getString("closed_at");
        return value != null ? DateUtils.parseIsoDate(value).getTime() : null;
    }
}
