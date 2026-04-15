// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bitrise;

import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

/**
 * Concrete Bitrise client backed by environment-variable configuration.
 *
 * <p>Required environment variables:
 * <pre>
 *   BITRISE_TOKEN     – Personal Access Token from bitrise.io account settings
 * </pre>
 *
 * <p>Optional environment variables:
 * <pre>
 *   BITRISE_BASE_PATH – Override base URL (default: https://api.bitrise.io/v0.1)
 *   BITRISE_APP_SLUG  – Default app slug used when no app slug is provided
 * </pre>
 */
public class BasicBitrise extends Bitrise {

    private static BasicBitrise instance;

    private final String defaultAppSlug;

    public BasicBitrise(String basePath, String token, String defaultAppSlug) throws IOException {
        super(basePath, token);
        this.defaultAppSlug = defaultAppSlug;
    }

    public BasicBitrise() throws IOException {
        this(readBasePath(), readToken(), readAppSlug());
    }

    /** Returns the default app slug configured via {@code BITRISE_APP_SLUG}. */
    public String getDefaultAppSlug() {
        return defaultAppSlug;
    }

    /** Returns {@code true} when the minimum required configuration is present. */
    public static boolean isConfigured() {
        String token = readToken();
        return token != null && !token.isEmpty();
    }

    /**
     * Returns a shared singleton instance, or {@code null} when not configured.
     * Thread-safe: uses synchronized method.
     */
    public static synchronized BasicBitrise getInstance() throws IOException {
        if (instance == null) {
            if (!isConfigured()) {
                return null;
            }
            instance = new BasicBitrise();
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

    private static String readToken() {
        return new PropertyReader().getBitriseToken();
    }

    private static String readBasePath() {
        return new PropertyReader().getBitriseBasePath();
    }

    private static String readAppSlug() {
        return new PropertyReader().getBitriseAppSlug();
    }
}
