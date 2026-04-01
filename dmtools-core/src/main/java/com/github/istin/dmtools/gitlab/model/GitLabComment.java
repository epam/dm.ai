// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class GitLabComment extends JSONModel implements IComment {

    public GitLabComment() {
    }

    public GitLabComment(String json) throws JSONException {
        super(json);
    }

    public GitLabComment(JSONObject json) {
        super(json);
    }

    @Override
    public IUser getAuthor() {
        return getModel(GitLabUser.class, "author"); // GitLab uses 'author' to represent the user
    }

    @Override
    public String getBody() {
        return getString("body");
    }

    public Boolean isSystem() {
        return getBoolean("system");
    }

    /**
     * DiffNote - comment and task
     */
    public String getType() {
        return getString("type");
    }

    @Override
    public String getId() {
        return String.valueOf(getLong("id"));
    }

    @Override
    public Date getCreated() {
        throw new UnsupportedOperationException();
    }

}