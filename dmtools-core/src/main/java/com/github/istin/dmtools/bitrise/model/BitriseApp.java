// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bitrise.model;

import org.json.JSONObject;

public class BitriseApp {

    private final JSONObject json;

    public BitriseApp(JSONObject json) {
        this.json = json;
    }

    public BitriseApp(String json) {
        this(new JSONObject(json));
    }

    public String getSlug() {
        return json.optString("slug", null);
    }

    public String getTitle() {
        return json.optString("title", null);
    }

    public String getProjectType() {
        return json.optString("project_type", null);
    }

    public String getProvider() {
        return json.optString("provider", null);
    }

    public String getRepoUrl() {
        return json.optString("repo_url", null);
    }

    public String getStatus() {
        return json.optString("status", null);
    }

    public JSONObject toJSON() {
        return json;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
