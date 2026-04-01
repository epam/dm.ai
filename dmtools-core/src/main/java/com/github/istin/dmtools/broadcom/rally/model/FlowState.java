// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class FlowState extends JSONModel {

    public FlowState() {
    }

    public FlowState(String json) throws JSONException {
        super(json);
    }

    public FlowState(JSONObject json) {
        super(json);
    }

    public String getRef() {
        return getString(RallyFields._REF);
    }

    public String getRefObjectName() {
        return getString(RallyFields._REF_OBJECT_NAME);
    }

}
