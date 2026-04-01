// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class Segment extends JSONModel {

    public static final String TYPE = "type";

    public Segment() {
    }

    public Segment(String json) throws JSONException {
        super(json);
    }

    public Segment(JSONObject json) {
        super(json);
    }

    public String getType() {
        return getString(TYPE);
    }

    public List<Line> getLines() {
        return getModels(Line.class, "lines");
    }
}