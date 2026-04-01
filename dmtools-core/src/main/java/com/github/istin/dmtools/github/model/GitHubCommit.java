// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

public class GitHubCommit extends JSONModel implements ICommit, IDiffStats {

    public GitHubCommit() {
    }

    public GitHubCommit(String json) throws JSONException {
        super(json);
    }

    public GitHubCommit(JSONObject json) {
        super(json);
    }

    @Override
    public String getId() {
        return getString("node_id");
    }

    @Override
    public String getHash() {
        return getString("sha");
    }

    @Override
    public String getMessage() {
        return getJSONObject("commit").getString("message");
    }

    @Override
    public IStats getStats() {
        return getModel(GithubStats.class, "stats");
    }

    @Override
    public List<IChange> getChanges() {
        return getModels(GitHubDiffFile.class, "files");
    }

    @Override
    public IUser getAuthor() {
        return getModel(GitHubUser.class, "author");
    }

    @Override
    public Long getCommiterTimestamp() {
        return DateUtils.parseIsoDate(getJSONObject("commit").getJSONObject("committer").getString("date")).getTime();
    }

    @Override
    public Calendar getCommitterDate() {
        return Utils.getComitterDate(this);
    }

    @Override
    public String getUrl() {
        return getString("html_url");
    }
}