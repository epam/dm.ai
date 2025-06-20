package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;

/**
 * Configuration interface for Bitbucket settings.
 */
public interface BitbucketConfiguration {
    /**
     * Gets the Bitbucket token
     * @return The Bitbucket token
     */
    String getBitbucketToken();

    /**
     * Gets the Bitbucket API version
     * @return The Bitbucket API version
     */
    String getBitbucketApiVersion();

    /**
     * Gets the Bitbucket workspace
     * @return The Bitbucket workspace
     */
    String getBitbucketWorkspace();

    /**
     * Gets the Bitbucket repository
     * @return The Bitbucket repository
     */
    String getBitbucketRepository();

    /**
     * Gets the Bitbucket branch
     * @return The Bitbucket branch
     */
    String getBitbucketBranch();

    /**
     * Gets the Bitbucket base path
     * @return The Bitbucket base path
     */
    String getBitbucketBasePath();
    
    /**
     * Gets the Bitbucket source code configuration
     * @return The Bitbucket source code configuration
     */
    default SourceCodeConfig getBitbucketSourceCodeConfig() {
        return SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.BITBUCKET)
                .auth(getBitbucketToken())
                .workspaceName(getBitbucketWorkspace())
                .repoName(getBitbucketRepository())
                .branchName(getBitbucketBranch())
                .path(getBitbucketBasePath())
                .apiVersion(getBitbucketApiVersion())
                .build();
    }
} 