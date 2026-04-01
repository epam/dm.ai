// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Component extends JSONModel {

    private static final String ID = "id";
    private static final String NAME = "name";

    public Component() {
    }

    public Component(String json) throws JSONException {
        super(json);
    }

    public Component(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString(ID);
    }

    public String getName() {
        return getString(NAME);
    }

    public void setName(String name) {
        set(NAME, name);
    }

}