// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sm;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class ScrumMasterDailyParams extends JSONModel {

    public static final String JQL = "jql";
    public static final String CONFLUENCE_PAGE = "confluencePage";

    public ScrumMasterDailyParams() {
    }

    public ScrumMasterDailyParams(String json) throws JSONException {
        super(json);
    }

    public ScrumMasterDailyParams(JSONObject json) {
        super(json);
    }

    public String getConfluencePage() {
        return getString(CONFLUENCE_PAGE);
    }

    public String getJql() {
        return getString(JQL);
    }

}