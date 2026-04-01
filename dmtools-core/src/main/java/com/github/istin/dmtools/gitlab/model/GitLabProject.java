// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IRepository;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;
import org.json.JSONException;

public class GitLabProject extends JSONModel implements IRepository {

    public GitLabProject() {
    }

    public GitLabProject(String json) throws JSONException {
        super(json);
    }

    public GitLabProject(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString("id");
    }

    @Override
    public String getName() {
        String string = getString("path_with_namespace");
        if (string != null) {
            String[] split = string.split("/");
            if (split.length > 1) {
                return split[1];
            }
        }
        return getString("name");
    }

    public String getDescription() {
        return getString("description");
    }

    public String getWebUrl() {
        return getString("web_url");
    }

    public String getSshUrl() {
        return getString("ssh_url_to_repo");
    }

    public String getHttpUrl() {
        return getString("http_url_to_repo");
    }
}
