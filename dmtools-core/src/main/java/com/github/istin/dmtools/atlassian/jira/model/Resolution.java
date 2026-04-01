// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Resolution extends JSONModel {

    private static final String ID = "id";
    private static final String NAME = "name";

    public Resolution() {
    }

    public Resolution(String json) throws JSONException {
        super(json);
    }

    public Resolution(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString(ID);
    }

    public String getName() {
        return getString(NAME);
    }

    public JSONObject setId(String id) {
        set(ID, id);
        return getJSONObject();
    }

    public boolean isFixed() {
        String lowerCaseName = getName().toLowerCase();
        if (lowerCaseName.contains("fixed")) {
            return true;
        }
        return false;
    }

    public boolean isRejected() {
        String lowerCaseName = getName().toLowerCase();
        if (lowerCaseName.contains("not an issue")) {
            return true;
        }
        if (lowerCaseName.contains("won't fix")) {
            return true;
        }
        if (lowerCaseName.contains("not a bug")) {
            return true;
        }

        if (lowerCaseName.contains("not valid")) {
            return true;
        }

        if (lowerCaseName.contains("cannot reproduce")) {
            return true;
        }

        if (lowerCaseName.contains("duplicate")) {
            return true;
        }

        if (lowerCaseName.contains("not implemented")) {
            return true;
        }

        return false;
    }
}