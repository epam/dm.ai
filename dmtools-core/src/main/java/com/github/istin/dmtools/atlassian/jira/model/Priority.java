// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Priority extends JSONModel {
    public Priority() {
    }

    public Priority(String json) throws JSONException {
        super(json);
    }

    public Priority(JSONObject json) {
        super(json);
    }

    public String getName() {
        return getString("name");
    }

    public int getId() {
        return Integer.parseInt(getString("id"));
    }

    public JSONObject createPostObject() {
        return new JSONObject().put("id", getString("id"));
    }

}