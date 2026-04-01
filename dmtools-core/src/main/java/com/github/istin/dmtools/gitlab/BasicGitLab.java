// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicGitLab extends GitLab {

    private static SourceCodeConfig DEFAULT_CONFIG;

    private SourceCodeConfig config;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_CONFIG = SourceCodeConfig.builder()
                .branchName(propertyReader.getGitLabBranch())
                .repoName(propertyReader.getGitLabRepository())
                .workspaceName(propertyReader.getGitLabWorkspace())
                .type(SourceCodeConfig.Type.GITLAB)
                .auth(propertyReader.getGitLabToken())
                .path(propertyReader.getGitLabBasePath())
                .build();
    }


    public BasicGitLab() throws IOException {
        this(DEFAULT_CONFIG);
    }

    public BasicGitLab(SourceCodeConfig config) throws IOException {
        super(config.getPath(), config.getAuth());
        this.config = config;
    }


    private static BasicGitLab instance;

    public static synchronized SourceCode getInstance() throws IOException {
        if (instance == null) {
            if (!DEFAULT_CONFIG.isConfigured()) {
                return null;
            }
            instance = new BasicGitLab();
        }
        return instance;
    }


    @Override
    public String getDefaultRepository() {
        return config.getRepoName();
    }

    @Override
    public String getDefaultBranch() {
        return config.getBranchName();
    }

    @Override
    public String getDefaultWorkspace() {
        return config.getWorkspaceName();
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public SourceCodeConfig getDefaultConfig() {
        return config;
    }

    @Override
    public String callHookAndWaitResponse(String hookUrl, String string) throws Exception {
        throw  new UnsupportedOperationException("Implement Me!");
    }
}