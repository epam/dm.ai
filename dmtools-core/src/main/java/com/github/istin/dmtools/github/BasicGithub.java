// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicGithub extends GitHub {

    private static SourceCodeConfig DEFAULT_CONFIG;

    private SourceCodeConfig config;

    static {
        PropertyReader propertyReader = new PropertyReader();
        DEFAULT_CONFIG = SourceCodeConfig.builder()
                .branchName(propertyReader.getGithubBranch())
                .repoName(propertyReader.getGithubRepository())
                .workspaceName(propertyReader.getGithubWorkspace())
                .type(SourceCodeConfig.Type.GITHUB)
                .auth(propertyReader.getGithubToken())
                .path(propertyReader.getGithubBasePath())
                .build();
    }


    public BasicGithub() throws IOException {
        this(DEFAULT_CONFIG);
    }

    public BasicGithub(SourceCodeConfig config) throws IOException {
        super(config.getPath(), config.getAuth());
        this.config = config;
    }


    /**
     * Creates a new GitHub client instance with the specified token, reusing the default base path.
     * Use this when a JS agent needs to switch tokens at runtime via set_env_variable().
     *
     * @param token the GitHub authorization token
     * @return a new BasicGithub instance configured with the given token
     */
    public static BasicGithub createWithToken(String token) throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .branchName(DEFAULT_CONFIG.getBranchName())
                .repoName(DEFAULT_CONFIG.getRepoName())
                .workspaceName(DEFAULT_CONFIG.getWorkspaceName())
                .type(SourceCodeConfig.Type.GITHUB)
                .auth(token)
                .path(DEFAULT_CONFIG.getPath())
                .build();
        return new BasicGithub(config);
    }

    private static BasicGithub instance;

    public static synchronized SourceCode getInstance() throws IOException {
        if (instance == null) {
            if (!DEFAULT_CONFIG.isConfigured()) {
                return null;
            }
            instance = new BasicGithub();
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
}