// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IStats;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class GithubStats extends JSONModel implements IStats {

    public GithubStats() {
    }

    public GithubStats(String json) throws JSONException {
        super(json);
    }

    public GithubStats(JSONObject json) {
        super(json);
    }

    @Override
    public int getTotal() {
        return getInt("total");
    }

    @Override
    public int getAdditions() {
        return getInt("additions");
    }

    @Override
    public int getDeletions() {
        return getInt("deletions");
    }

}