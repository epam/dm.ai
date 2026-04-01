// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.ITask;
import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONException;
import org.json.JSONObject;

public class GitHubTask extends GitHubActivity implements ITask {

    public GitHubTask() {
    }

    public GitHubTask(String json) throws JSONException {
        super(json);
    }

    public GitHubTask(JSONObject json) {
        super(json);
    }

    @Override
    public String getBody() {
        return getComment().getBody();
    }

    @Override
    public IUser getAuthor() {
        return getApproval();
    }

}