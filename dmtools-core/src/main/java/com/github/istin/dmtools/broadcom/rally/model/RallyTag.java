// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class RallyTag extends JSONModel {

    public RallyTag() {
    }

    public RallyTag(String json) throws JSONException {
        super(json);
    }

    public RallyTag(JSONObject json) {
        super(json);
    }

    public String getRef() {
        return getString(RallyFields._REF);
    }

    public String getName() {
        return getString("Name");
    }


}
