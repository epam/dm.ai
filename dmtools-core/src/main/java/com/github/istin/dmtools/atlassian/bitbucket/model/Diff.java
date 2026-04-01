// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class Diff extends JSONModel {

    public Diff() {
    }

    public Diff(String json) throws JSONException {
        super(json);
    }

    public Diff(JSONObject json) {
        super(json);
    }

    public List<Hunk> getHunks() {
        return getModels(Hunk.class, "hunks");
    }

    public String getSource() {
        JSONObject source = getJSONObject("source");
        if (source == null) {
            return "";
        }
        return source.optString("toString");
    }
}