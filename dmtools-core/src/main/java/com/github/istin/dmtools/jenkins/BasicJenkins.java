// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.jenkins;

import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

/**
 * Concrete Jenkins client backed by environment-variable configuration.
 *
 * <p>Required environment variables:
 * <pre>
 *   JENKINS_USER      – Jenkins user name
 *   JENKINS_API_TOKEN – Jenkins API token
 * </pre>
 *
 * <p>Optional environment variables:
 * <pre>
 *   JENKINS_BASE_PATH – Override base URL (default: http://localhost:8080)
 * </pre>
 */
public class BasicJenkins extends Jenkins {

    private static BasicJenkins instance;

    public BasicJenkins(String basePath, String user, String apiToken) throws IOException {
        super(basePath, user, apiToken);
    }

    public BasicJenkins() throws IOException {
        this(readBasePath(), readUser(), readApiToken());
    }

    /** Returns {@code true} when all required configuration values are present. */
    public static boolean isConfigured() {
        String basePath = readBasePath();
        String user = readUser();
        String apiToken = readApiToken();
        return basePath != null && !basePath.isEmpty()
                && user != null && !user.isEmpty()
                && apiToken != null && !apiToken.isEmpty();
    }

    /**
     * Returns a shared singleton instance, or {@code null} when not configured.
     * Thread-safe: uses synchronized method.
     */
    public static synchronized BasicJenkins getInstance() throws IOException {
        if (instance == null) {
            if (!isConfigured()) {
                return null;
            }
            instance = new BasicJenkins();
        }
        return instance;
    }

    /** Resets the singleton — useful for tests that need to re-initialize. */
    public static synchronized void resetInstance() {
        instance = null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String readBasePath() {
        return new PropertyReader().getJenkinsBasePath();
    }

    private static String readUser() {
        return new PropertyReader().getJenkinsUser();
    }

    private static String readApiToken() {
        return new PropertyReader().getJenkinsApiToken();
    }
}
