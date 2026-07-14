// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Jenkins settings.
 */
public interface JenkinsConfiguration {
    /**
     * Gets the Jenkins base path URL.
     * @return The Jenkins base path URL
     */
    String getJenkinsBasePath();

    /**
     * Gets the Jenkins user name.
     * @return The Jenkins user name
     */
    String getJenkinsUser();

    /**
     * Gets the Jenkins API token.
     * @return The Jenkins API token
     */
    String getJenkinsApiToken();
}
