// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class GitHubPullRequest extends JSONModel implements IPullRequest {

    public GitHubPullRequest() {
    }

    public GitHubPullRequest(String json) throws JSONException {
        super(json);
    }

    public GitHubPullRequest(JSONObject json) {
        super(json);
    }

    @Override
    public String getTitle() {
        return getString("title");
    }

    @Override
    public String getDescription() {
        return getString("body");
    }

    @Override
    public Integer getId() {
        return getInt("number");
    }

    @Override
    public IUser getAuthor() {
        return getModel(GitHubUser.class, "user");
    }

    @Override
    public String getTargetBranchName() {
        return getJSONObject("base").getString("ref");
    }

    @Override
    public String getSourceBranchName() {
        return getJSONObject("head").getString("ref");
    }

    @Override
    public Long getCreatedDate() {
        return DateUtils.parseIsoDate(getString("created_at")).getTime();
    }

    @Override
    public Long getClosedDate() {
        String closedAt = getString("closed_at");
        return closedAt != null ? DateUtils.parseIsoDate(closedAt).getTime() : null;
    }

    @Override
    public Long getUpdatedDate() {
        return DateUtils.parseIsoDate(getString("updated_at")).getTime();
    }

    public boolean isMerged() {
        return getString("merged_at") != null;
    }

    @Override
    public IUser getMergedBy() {
        return getModel(GitHubUser.class, "merged_by");
    }
}