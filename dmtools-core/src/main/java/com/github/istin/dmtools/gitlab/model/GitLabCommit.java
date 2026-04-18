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
        // GitLab commits API returns author_name and author_email as flat top-level strings,
        // not as a nested object. Build a synthetic GitLabUser from those fields.
        String name = getString("author_name");
        String email = getString("author_email");
        if (name == null) return null;
        JSONObject userJson = new JSONObject();
        userJson.put("name", name);
        userJson.put("email", email);
        return new GitLabUser(userJson);
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