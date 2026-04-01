// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Storage extends JSONModel {

    public static final String VALUE = "value";


    public Storage() {
    }

    public Storage(String json) throws JSONException {
        super(json);
    }

    public Storage(JSONObject json) {
        super(json);
    }



    public String getValue() {
        return getString(VALUE);
    }

}