// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IMatch;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class GitHubMatch extends JSONModel implements IMatch {

    public GitHubMatch() {

    }

    public GitHubMatch(String json) throws JSONException {
        super(json);
    }

    public GitHubMatch(JSONObject json) {
        super(json);
    }

    @Override
    public String getIndices() {
        return getJSONArray("indices").toString();
    }

    @Override
    public String getText() {
        return getString("text");
    }
}