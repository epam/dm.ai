// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

public class GitLabCommit extends JSONModel implements ICommit, IDiffStats {

    public GitLabCommit() {
    }

    public GitLabCommit(String json) throws JSONException {
        super(json);
    }

    public GitLabCommit(JSONObject json) {
        super(json);
    }

    @Override
    public String getId() {
        return getString("id");
    }

    @Override
    public String getHash() {
        return getString("id"); // GitLab uses `id` for commit hash
    }

    @Override
    public String getMessage() {
        return getString("message");
    }

    @Override
    public IStats getStats() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<IChange> getChanges() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IUser getAuthor() {
        // GitLab provides author details directly within `author`
        return getModel(GitLabUser.class, "author");
    }

    @Override
    public Long getCommiterTimestamp() {
        return DateUtils.parseIsoDate(getJSONObject("committer").getString("date")).getTime();
    }

    @Override
    public Calendar getCommitterDate() {
        return Utils.getComitterDate(this);
    }

    @Override
    public String getUrl() {
        return "url is not supported";
    }

}