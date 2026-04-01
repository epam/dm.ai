// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class GitLabJob extends JSONModel implements ITag {

    public GitLabJob() {
    }

    public GitLabJob(String json) throws JSONException {
        super(json);
    }

    public GitLabJob(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString("id");
    }

    public String getStatus() {
        return getString("status");
    }

    public String getStage() {
        return getString("stage");
    }

    public String getName() {
        return getString("name");
    }

    public IUser getUser() {
        return getModel(GitLabUser.class, "user");
    }

    public String getStartedAt() {
        return getString("started_at");
    }

    public String getFinishedAt() {
        return getString("finished_at");
    }

    public String getWebUrl() {
        return getString("web_url");
    }

}