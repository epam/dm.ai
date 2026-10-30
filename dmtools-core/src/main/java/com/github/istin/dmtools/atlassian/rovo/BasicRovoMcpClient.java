// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.rovo;

import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

/**
 * Concrete Atlassian Rovo MCP client backed by {@link PropertyReader} configuration.
 *
 * <p>Configuration variables:
 * <ul>
 *   <li>{@code ROVO_MCP_URL} – Remote MCP endpoint (default: {@code https://mcp.atlassian.com/v2/mcp})</li>
 *   <li>{@code ROVO_LOGIN_PASS_TOKEN} (or {@code ROVO_EMAIL} + {@code ROVO_API_TOKEN}) – Authentication credentials</li>
 *   <li>{@code ROVO_AUTH_TYPE} – {@code Basic} (default) or {@code Bearer}</li>
 *   <li>{@code ROVO_CLOUD_ID} – Default Atlassian Cloud ID (optional)</li>
 * </ul>
 */
public class BasicRovoMcpClient extends RovoMcpClient {

    private static BasicRovoMcpClient instance;

    public BasicRovoMcpClient(String basePath, String authorization, String authType, String defaultCloudId) throws IOException {
        super(basePath, authorization, authType, defaultCloudId);
    }

    public BasicRovoMcpClient() throws IOException {
        this(readMcpUrl(), readLoginPassToken(), readAuthType(), readCloudId());
    }

    /**
     * Returns {@code true} when authentication credentials for Rovo MCP are configured.
     */
    public static boolean isConfigured() {
        String token = readLoginPassToken();
        return token != null && !token.trim().isEmpty();
    }

    /**
     * Returns a shared singleton instance, or {@code null} when not configured.
     */
    public static synchronized RovoMcpClient getInstance() throws IOException {
        if (instance == null) {
            if (!isConfigured()) {
                return null;
            }
            instance = new BasicRovoMcpClient();
        }
        return instance;
    }

    /**
     * Resets the singleton instance (for unit tests).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    private static String readMcpUrl() {
        return new PropertyReader().getRovoMcpUrl();
    }

    private static String readLoginPassToken() {
        return new PropertyReader().getRovoLoginPassToken();
    }

    private static String readAuthType() {
        return new PropertyReader().getRovoAuthType();
    }

    private static String readCloudId() {
        return new PropertyReader().getRovoCloudId();
    }
}
