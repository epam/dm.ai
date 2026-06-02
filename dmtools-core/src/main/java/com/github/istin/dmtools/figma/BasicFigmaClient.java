// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class BasicFigmaClient extends FigmaClient {

    private static final Logger logger = LogManager.getLogger(BasicFigmaClient.class);

    public static final String BASE_PATH;
    public static final String API_KEY;
    public static final String CLIENT_ID;
    public static final String CLIENT_SECRET;
    public static final String OAUTH_REFRESH_TOKEN;
    public static final String OAUTH_ACCESS_TOKEN;

    private static FigmaOAuth2TokenManager oAuth2TokenManager;
    private static BasicFigmaClient instance;

    static {
        PropertyReader propertyReader = new PropertyReader();
        BASE_PATH = propertyReader.getFigmaBasePath();
        API_KEY = propertyReader.getFigmaApiKey();
        CLIENT_ID = propertyReader.getFigmaClientId();
        CLIENT_SECRET = propertyReader.getFigmaClientSecret();
        OAUTH_REFRESH_TOKEN = propertyReader.getFigmaOAuth2RefreshToken();
        OAUTH_ACCESS_TOKEN = propertyReader.getFigmaOAuth2AccessToken();
    }

    public BasicFigmaClient() throws IOException {
        super(BASE_PATH, resolveAuthorization());
        if (isOAuth2Mode()) {
            setUseOAuth2Bearer(true);
        }
    }

    /**
     * Determines the authorization token to use based on available credentials.
     *
     * <p>Priority order:
     * <ol>
     *   <li>OAuth2 access token ({@code FIGMA_OAUTH_ACCESS_TOKEN})</li>
     *   <li>OAuth2 via refresh token ({@code FIGMA_OAUTH_REFRESH_TOKEN} + client credentials)</li>
     *   <li>Personal access token ({@code FIGMA_TOKEN})</li>
     * </ol>
     */
    private static String resolveAuthorization() {
        // Priority 1: direct OAuth2 access token
        if (isNotEmpty(OAUTH_ACCESS_TOKEN)) {
            logger.info("Figma: using OAuth2 access token (FIGMA_OAUTH_ACCESS_TOKEN)");
            return OAUTH_ACCESS_TOKEN;
        }

        // Priority 2: refresh token + client credentials → get fresh access token
        if (isOAuth2Mode()) {
            try {
                if (oAuth2TokenManager == null) {
                    oAuth2TokenManager = new FigmaOAuth2TokenManager(CLIENT_ID, CLIENT_SECRET);
                }
                String accessToken = oAuth2TokenManager.getValidAccessToken(OAUTH_REFRESH_TOKEN);
                logger.info("Figma: obtained OAuth2 access token via refresh token");
                return accessToken;
            } catch (Exception e) {
                logger.warn("Figma: failed to obtain OAuth2 access token via refresh token, "
                        + "falling back to personal token. Error: {}", e.getMessage());
            }
        }

        // Priority 3: personal access token
        if (isNotEmpty(API_KEY)) {
            logger.info("Figma: using personal access token (FIGMA_TOKEN)");
        }
        return API_KEY;
    }

    private static boolean isOAuth2Mode() {
        return isNotEmpty(CLIENT_ID) && isNotEmpty(CLIENT_SECRET) && isNotEmpty(OAUTH_REFRESH_TOKEN);
    }

    private static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static synchronized FigmaClient getInstance() throws IOException {
        if (instance == null) {
            if (BASE_PATH == null || BASE_PATH.isEmpty()) {
                return null;
            }
            instance = new BasicFigmaClient();
        }
        return instance;
    }

    /**
     * Resets the singleton instance and OAuth2 token manager.
     * Useful for testing or when credentials change.
     */
    public static synchronized void reset() {
        instance = null;
        oAuth2TokenManager = null;
    }
}

