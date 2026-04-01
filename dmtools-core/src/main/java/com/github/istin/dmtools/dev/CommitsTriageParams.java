// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.job.BaseJobParams;
import org.json.JSONException;
import org.json.JSONObject;

public class CommitsTriageParams extends BaseJobParams {

    public static final String SOURCE_TYPE = "sourceType";
    public static final String REPO = "repo";
    public static final String WORKSPACE = "workspace";
    public static final String BRANCH = "branch";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String ROLE = "role";


    public CommitsTriageParams() {

    }

    public CommitsTriageParams(String json) throws JSONException {
        super(json);
    }

    public CommitsTriageParams(JSONObject json) {
        super(json);
    }

    public String getStartDate() {
        return getString(START_DATE);
    }

    public String getEndDate() {
        return getString(END_DATE);
    }

    public String getRepo() {
        return getString(REPO);
    }

    public String getWorkspace() {
        return getString(WORKSPACE);
    }

    public String getRole() {
        return getString(ROLE);
    }

    public String getSourceType() {
        return getString(SOURCE_TYPE);
    }

    public String getBranch() {
        return getString(BRANCH);
    }

    public CommitsTriageParams setStartDate(String startDate) {
        set(START_DATE, startDate);
        return this;
    }

    public CommitsTriageParams setEndDate(String endDate) {
        set(END_DATE, endDate);
        return this;
    }

    public CommitsTriageParams setRepo(String repo) {
        set(REPO, repo);
        return this;
    }

    public CommitsTriageParams setWorkspace(String workspace) {
        set(WORKSPACE, workspace);
        return this;
    }

    public CommitsTriageParams setRole(String role) {
        set(ROLE, role);
        return this;
    }

    public CommitsTriageParams setBranch(String branch) {
        set(BRANCH, branch);
        return this;
    }

    public CommitsTriageParams setSourceType(String sourceType) {
        set(SOURCE_TYPE, sourceType);
        return this;
    }

}