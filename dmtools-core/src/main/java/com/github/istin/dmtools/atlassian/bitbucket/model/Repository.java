// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Repository extends JSONModel {

    public Repository() {
    }

    public Repository(String json) throws JSONException {
        super(json);
    }

    public Repository(JSONObject json) {
        super(json);
    }

    public String getName() {
        return getString("name");
    }

}