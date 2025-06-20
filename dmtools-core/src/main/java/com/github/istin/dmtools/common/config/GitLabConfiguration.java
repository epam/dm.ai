package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;

/**
 * Configuration interface for GitLab settings.
 */
public interface GitLabConfiguration {
    /**
     * Gets the GitLab token
     * @return The GitLab token
     */
    String getGitLabToken();

    /**
     * Gets the GitLab workspace
     * @return The GitLab workspace
     */
    String getGitLabWorkspace();

    /**
     * Gets the GitLab repository
     * @return The GitLab repository
     */
    String getGitLabRepository();

    /**
     * Gets the GitLab branch
     * @return The GitLab branch
     */
    String getGitLabBranch();

    /**
     * Gets the GitLab base path
     * @return The GitLab base path
     */
    String getGitLabBasePath();
    
    /**
     * Gets the GitLab source code configuration
     * @return The GitLab source code configuration
     */
    default SourceCodeConfig getGitLabSourceCodeConfig() {
        return SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITLAB)
                .auth(getGitLabToken())
                .workspaceName(getGitLabWorkspace())
                .repoName(getGitLabRepository())
                .branchName(getGitLabBranch())
                .path(getGitLabBasePath())
                .build();
    }
} 