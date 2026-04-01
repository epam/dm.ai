// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RallyResponse extends JSONModel {

    private static final String QUERY_RESULT = "QueryResult";

    public RallyResponse() {
    }

    public RallyResponse(String json) throws JSONException {
        super(json);
    }

    public RallyResponse(JSONObject json) {
        super(json);
    }

    public QueryResult getQueryResult() {
        return getModel(QueryResult.class, QUERY_RESULT);
    }

}
