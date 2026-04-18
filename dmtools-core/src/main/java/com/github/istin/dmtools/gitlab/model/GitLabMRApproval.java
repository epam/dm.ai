// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a single entry in the {@code approved_by} array returned by
 * the GitLab MR Approvals API:
 * {@code GET /projects/:id/merge_requests/:iid/approvals}
 *
 * <p>Response shape: {@code { "user": { "id": 7, "name": "...", ... } }}
 */
public class GitLabMRApproval extends JSONModel {

    public GitLabMRApproval() {
    }

    public GitLabMRApproval(String json) throws JSONException {
        super(json);
    }

    public GitLabMRApproval(JSONObject json) {
        super(json);
    }

    public IUser getUser() {
        return getModel(GitLabUser.class, "user");
    }
}
