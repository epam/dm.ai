// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.estimations;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class JEstimatorParams extends JSONModel {

    public JEstimatorParams() {
    }

    public JEstimatorParams(String json) throws JSONException {
        super(json);
    }

    public JEstimatorParams(JSONObject json) {
        super(json);
    }

    public String getReportName() {
        return getString("reportName");
    }

    public String getJQL() {
        return getString("jql");
    }
}