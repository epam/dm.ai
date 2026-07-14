// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.jenkins.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.List;

public class JenkinsJob extends JSONModel {

    public JenkinsJob() {
        super();
    }

    public JenkinsJob(JSONObject json) {
        super(json);
    }

    public JenkinsJob(String json) {
        super(json);
    }

    public String getName() {
        return getString("name");
    }

    public String getUrl() {
        return getString("url");
    }

    public List<JenkinsBuild> getBuilds() {
        return getModels(JenkinsBuild.class, "builds");
    }
}
