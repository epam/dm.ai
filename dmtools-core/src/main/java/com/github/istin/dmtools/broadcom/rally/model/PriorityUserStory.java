// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PriorityUserStory extends JSONModel {

    private static final String TAGS_NAME = "_tagsNameArray";

    public PriorityUserStory() {
    }

    public PriorityUserStory(String json) throws JSONException {
        super(json);
    }

    public PriorityUserStory(JSONObject json) {
        super(json);
    }

    public String getPriority() {
        JSONArray jsonArray = getJSONArray(TAGS_NAME);
        if (jsonArray != null && !jsonArray.isEmpty()) {
            return jsonArray.getJSONObject(0).getString("Name");
        }
        return null;
    }

}
