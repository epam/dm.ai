package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;

/**
 * Configuration interface for GitHub settings.
 */
public interface GitHubConfiguration {
    /**
     * Gets the GitHub token
     * @return The GitHub token
     */
    String getGithubToken();

    /**
     * Gets the GitHub workspace
     * @return The GitHub workspace
     */
    String getGithubWorkspace();

    /**
     * Gets the GitHub repository
     * @return The GitHub repository
     */
    String getGithubRepository();

    /**
     * Gets the GitHub branch
     * @return The GitHub branch
     */
    String getGithubBranch();

    /**
     * Gets the GitHub base path
     * @return The GitHub base path
     */
    String getGithubBasePath();
    
    /**
     * Gets the GitHub source code configuration
     * @return The GitHub source code configuration
     */
    default SourceCodeConfig getGithubSourceCodeConfig() {
        return SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITHUB)
                .auth(getGithubToken())
                .workspaceName(getGithubWorkspace())
                .repoName(getGithubRepository())
                .branchName(getGithubBranch())
                .path(getGithubBasePath())
                .build();
    }
} 