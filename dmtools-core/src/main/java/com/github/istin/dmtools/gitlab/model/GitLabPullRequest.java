// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class GitLabPullRequest extends JSONModel implements IPullRequest {

    public GitLabPullRequest() {
    }

    public GitLabPullRequest(String json) throws JSONException {
        super(json);
    }

    public GitLabPullRequest(JSONObject json) {
        super(json);
    }

    @Override
    public String getTitle() {
        return getString("title");
    }

    @Override
    public String getDescription() {
        return getString("description");
    }

    @Override
    public Integer getId() {
        return getInt("iid");  // GitLab uses `iid` for Merge Request internal ID
    }

    @Override
    public IUser getAuthor() {
        return getModel(GitLabUser.class, "author"); // GitLab uses 'author' instead of 'user'
    }

    @Override
    public String getTargetBranchName() {
        return getString("target_branch");
    }

    @Override
    public String getSourceBranchName() {
        return getString("source_branch");
    }

    @Override
    public Long getCreatedDate() {
        return DateUtils.smartParseDate(getString("created_at")).getTime();
    }

    @Override
    public Long getClosedDate() {
        String closedAt = getString("merged_at");
        Long result = closedAt != null ? DateUtils.smartParseDate(closedAt).getTime() : null;
        if (result == null) {
            return getUpdatedDate();
        }
        return result;
    }

    @Override
    public Long getUpdatedDate() {
        return DateUtils.smartParseDate(getString("updated_at")).getTime();
    }

}