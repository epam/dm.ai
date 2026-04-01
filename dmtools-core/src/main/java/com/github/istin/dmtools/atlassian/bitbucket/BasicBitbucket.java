// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicBitbucket extends Bitbucket {

    private static SourceCodeConfig DEFAULT_CONFIG;

    private SourceCodeConfig config;

    static {
        PropertyReader propertyReader = new PropertyReader();
        String bitbucketApiVersion = propertyReader.getBitbucketApiVersion();
        DEFAULT_CONFIG = SourceCodeConfig.builder()
                .branchName(propertyReader.getBitbucketBranch())
                .repoName(propertyReader.getBitbucketRepository())
                .workspaceName(propertyReader.getBitbucketWorkspace())
                .type(SourceCodeConfig.Type.BITBUCKET)
                .auth(propertyReader.getBitbucketToken())
                .path(propertyReader.getBitbucketBasePath())
                .apiVersion(bitbucketApiVersion == null ? null : Bitbucket.ApiVersion.valueOf(bitbucketApiVersion).toString())
                .build();
    }


    public BasicBitbucket() throws IOException {
        this(DEFAULT_CONFIG);
    }

    public BasicBitbucket(SourceCodeConfig config) throws IOException {
        super(config.getPath(), config.getAuth());
        this.config = config;
        String apiVersion = config.getApiVersion();
        if (apiVersion != null) {
            setApiVersion(ApiVersion.valueOf(apiVersion));
        }
    }


    private static BasicBitbucket instance;

    public static synchronized SourceCode getInstance() throws IOException {
        if (instance == null) {
            instance = new BasicBitbucket();
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
        return config.isConfigured() && config.getApiVersion() != null;
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