// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.jenkins.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

public class JenkinsBuild extends JSONModel {

    public JenkinsBuild() {
        super();
    }

    public JenkinsBuild(JSONObject json) {
        super(json);
    }

    public JenkinsBuild(String json) {
        super(json);
    }

    public int getNumber() {
        return getInt("number");
    }

    public String getResult() {
        return getString("result");
    }

    public String getUrl() {
        return getString("url");
    }

    public long getDuration() {
        Long duration = getLong("duration");
        return duration != null ? duration : 0L;
    }

    public boolean isBuilding() {
        Boolean building = getBoolean("building");
        return building != null && building;
    }
}
