// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Diagram extends JSONModel {

    private static String TYPE = "type";
    private static String CODE = "code";

    public Diagram() {
    }

    public Diagram(String json) throws JSONException {
        super(json);
    }

    public Diagram(JSONObject json) {
        super(json);
    }

    public String getType() {
        return getString(TYPE);
    }

    public String getCode() {
        return getString(CODE);
    }
}
