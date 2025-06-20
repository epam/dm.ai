package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;

/**
 * Configuration interface for source control settings.
 * This interface extends all specific source control interfaces.
 */
public interface SourceControlConfiguration extends GitHubConfiguration, GitLabConfiguration, BitbucketConfiguration {
    /**
     * Checks if pull request diffs should be read
     * @return true if pull request diffs should be read, false otherwise
     */
    boolean isReadPullRequestDiff();
    
    /**
     * Gets the source code configuration for the specified type
     * @param type The source code type
     * @return The source code configuration
     */
    default SourceCodeConfig getSourceCodeConfig(SourceCodeConfig.Type type) {
        switch (type) {
            case GITHUB:
                return getGithubSourceCodeConfig();
            case GITLAB:
                return getGitLabSourceCodeConfig();
            case BITBUCKET:
                return getBitbucketSourceCodeConfig();
            default:
                throw new IllegalArgumentException("Unsupported source code type: " + type);
        }
    }
} 