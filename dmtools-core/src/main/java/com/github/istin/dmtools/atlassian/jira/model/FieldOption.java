// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class FieldOption extends JSONModel {

    private static final String ID = "id";
    private static final String VALUE = "value";

    public FieldOption() {
    }

    public FieldOption(String json) throws JSONException {
        super(json);
    }

    public FieldOption(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString(ID);
    }

    public String getValue() {
        return getString(VALUE);
    }

    public JSONObject setId(String id) {
        set(ID, id);
        return getJSONObject();
    }
}
