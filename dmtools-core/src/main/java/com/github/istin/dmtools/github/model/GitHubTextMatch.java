// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IMatch;
import com.github.istin.dmtools.common.model.ITextMatch;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class GitHubTextMatch extends JSONModel implements ITextMatch {

    public GitHubTextMatch() {

    }

    public GitHubTextMatch(String json) throws JSONException {
        super(json);
    }

    public GitHubTextMatch(JSONObject json) {
        super(json);
    }

    @Override
    public String getFragment() {
        return getString("fragment");
    }

    @Override
    public String getObjectUrl() {
        return getString("object_url");
    }

    @Override
    public String getObjectType() {
        return getString("object_type");
    }

    @Override
    public List<IMatch> getMatches() {
        return getModels(GitHubMatch.class, "matches");
    }
}