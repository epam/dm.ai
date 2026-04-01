// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class SearchResult extends JSONModel {

    public SearchResult() {
    }

    public SearchResult(String json) throws JSONException {
        super(json);
    }

    public SearchResult(JSONObject json) {
        super(json);
    }

    public static final String TITLE = "title";
    public static final String ID = "id";
    public static final String ENTITY_ID = "entityId";
    public static final String TYPE = "type";
    public static final String URL = "url";
    public static final String EXCERPT = "excerpt";

    public String getTitle() {
        return getString(TITLE);
    }
    public String getEntityId() {
        return getString(ENTITY_ID);
    }
    public String getId() {
        return getString(ID);
    }
    public String getType() {
        return getString(TYPE);
    }
    public String getUrl() {
        return getString(URL);
    }

    public String getExcerpt() {
        return getString(EXCERPT);
    }
}
