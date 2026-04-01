// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.IChange;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Change extends JSONModel implements IChange {

    public Change() {
    }

    public Change(String json) throws JSONException {
        super(json);
    }

    public Change(JSONObject json) {
        super(json);
    }

    public int getLinesAdded() {
        return getInt("lines_added");
    }

    public int getLinesRemoved() {
        return getInt("lines_removed");
    }

    @Override
    public String getFilePath() {
        JSONObject path = getJSONObject("path");
        if (path == null) {
            JSONObject jsonObject = getJSONObject("new");
            if (jsonObject != null) {
                return jsonObject.getString("path");
            }
            return "";
        }
        return path.getString("toString");
    }

}