// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model.server;

import com.github.istin.dmtools.atlassian.bitbucket.model.PullRequest;
import com.github.istin.dmtools.atlassian.common.model.Assignee;
import org.json.JSONException;
import org.json.JSONObject;

public class ServerPullRequest extends PullRequest {

    public ServerPullRequest() {
    }

    public ServerPullRequest(String json) throws JSONException {
        super(json);
    }

    public ServerPullRequest(JSONObject json) {
        super(json);
    }


    @Override
    public String getSourceBranchName() {
        return getJSONObject().getJSONObject("fromRef").getString("displayId");
    }

    @Override
    public String getTargetBranchName() {
        return getJSONObject().getJSONObject("toRef").getString("displayId");
    }

    @Override
    public Assignee getAuthor() {
        JSONObject authorObject = getJSONObject().getJSONObject("author");
        JSONObject jsonObject = authorObject.optJSONObject("user");
        return new Assignee(jsonObject);
    }


    @Override
    public Long getCreatedDate() {
        return getLong("createdDate");
    }

    @Override
    public Long getClosedDate() {
        return getLong("closedDate");
    }

    @Override
    public Long getUpdatedDate() {
        return getLong("updatedDate");
    }
}