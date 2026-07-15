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
        JSONObject stats = getJSONObject("stats");
        if (stats == null) return null;
        return new IStats() {
            @Override public int getTotal()     { return stats.optInt("total", 0); }
            @Override public int getAdditions() { return stats.optInt("additions", 0); }
            @Override public int getDeletions() { return stats.optInt("deletions", 0); }
        };
    }

    @Override
    public List<IChange> getChanges() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IUser getAuthor() {
        String name = getString("author_name");
        if (name == null) return null;
        return GitLabUser.fromCommitFields(name, getString("author_email"));
    }

    @Override
    public Long getCommiterTimestamp() {
        // GitLab uses committed_date (ISO 8601) at top level, not committer.date
        java.util.Date parsed = DateUtils.smartParseDate(getString("committed_date"));
        return parsed != null ? parsed.getTime() : null;
    }

    @Override
    public Calendar getCommitterDate() {
        return Utils.getComitterDate(this);
    }

    @Override
    public String getUrl() {
        return getString("web_url");
    }

}