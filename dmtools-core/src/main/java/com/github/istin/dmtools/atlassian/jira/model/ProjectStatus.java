// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ProjectStatus extends JSONModel {
    public ProjectStatus() {
    }

    public ProjectStatus(String json) throws JSONException {
        super(json);
    }

    public ProjectStatus(JSONObject json) {
        super(json);
    }

    public String getName() {
        return getString("name");
    }


    public List<Status> getStatuses() {
        return getModels(Status.class, "statuses");
    }
}