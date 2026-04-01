// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class HistoryItem extends JSONModel implements IHistoryItem {
    private static final String FIELD = "field";
    private static final String FROM = "from";
    private static final String FROM_STRING = "fromString";
    private static final String TO = "to";
    private static final String TO_STRING = "toString";

    public HistoryItem() {
    }

    public HistoryItem(String json) throws JSONException {
        super(json);
    }

    public HistoryItem(JSONObject json) {
        super(json);
    }

    @Override
    public String getField() {
        return getString(FIELD);
    }

    public String getFrom() {
        return getString(FROM);
    }

    @Override
    public String getFromAsString() {
        return getFromString();
    }

    public String getFromString() {
        return getString(FROM_STRING);
    }

    public String getTo() {
        return getString(TO);
    }
    @Override
    public String getToAsString() {
        return getToString();
    }

    public String getToString() {
        return getString(TO_STRING);
    }
}