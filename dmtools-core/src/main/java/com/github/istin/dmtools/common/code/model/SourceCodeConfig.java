// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.code.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceCodeConfig {

    public static final String _KEY = "source_code_config";

    public static final String BRANCH_NAME = "branch_name";
    public static final String REPO_NAME = "repo_name";
    public static final String WORKSPACE_NAME = "workspace_name";
    public static final String TYPE = "type";
    public static final String AUTH = "auth";
    public static final String PATH = "path";
    public static final String API_VERSION = "api_version";

    public enum Type {
        GITHUB, BITBUCKET, GITLAB
    }

    @SerializedName(BRANCH_NAME)
    private String branchName;

    @SerializedName(REPO_NAME)
    private String repoName;

    @SerializedName(WORKSPACE_NAME)
    private String workspaceName;

    @SerializedName(TYPE)
    private Type type;

    @SerializedName(AUTH)
    private String auth;

    @SerializedName(PATH)
    private String path;

    @SerializedName(API_VERSION)
    private String apiVersion;

    public boolean isConfigured() {
        return path != null || auth != null || repoName != null || branchName != null || workspaceName != null;
    }

    public SourceCodeConfig clone(String repoName, String branchName) {
        return SourceCodeConfig.builder()
                .branchName(branchName)
                .repoName(repoName)
                .workspaceName(this.workspaceName)
                .type(this.type)
                .auth(this.auth)
                .path(this.path)
                .build();
    }
}
