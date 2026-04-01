// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class Hunk extends JSONModel {

    public Hunk() {
    }

    public Hunk(String json) throws JSONException {
        super(json);
    }

    public Hunk(JSONObject json) {
        super(json);
    }

    public List<Segment> getSegments() {
        return getModels(Segment.class, "segments");
    }
}