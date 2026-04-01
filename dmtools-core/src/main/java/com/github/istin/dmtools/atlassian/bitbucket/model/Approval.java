// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Approval extends JSONModel {

    public Approval() {
    }

    public Approval(String json) throws JSONException {
        super(json);
    }

    public Approval(JSONObject json) {
        super(json);
    }

    public Assignee getUser() {
        return getModel(Assignee.class, "user");
    }

}