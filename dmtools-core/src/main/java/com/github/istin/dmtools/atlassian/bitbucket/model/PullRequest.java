// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.bitbucket.Bitbucket;
import com.github.istin.dmtools.atlassian.bitbucket.model.cloud.CloudPullRequest;
import com.github.istin.dmtools.atlassian.bitbucket.model.server.ServerPullRequest;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class PullRequest extends JSONModel implements IPullRequest {

    public PullRequest() {
    }

    public PullRequest(String json) throws JSONException {
        super(json);
    }

    public PullRequest(JSONObject json) {
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
        return getInt("id");
    }

    public static PullRequest create(Bitbucket.ApiVersion apiVersion, String json) {
        PullRequest pr = null;
        if (apiVersion == Bitbucket.ApiVersion.V1) {
            pr = new ServerPullRequest();
        } else {
            pr = new CloudPullRequest();
        }
        if (json != null) {
            pr.setJO(new JSONObject(json));
        }
        return pr;
    }

    public Integer getVersion() {
        return getInt("version");
    }

}