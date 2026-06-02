// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class FigmaOAuth2TokenManagerTest {

    @Test
    public void testBuildAuthorizationUrl_containsRequiredParams() {
        FigmaOAuth2TokenManager manager = new FigmaOAuth2TokenManager("my-client-id", "my-client-secret");
        String url = manager.buildAuthorizationUrl("http://localhost:8080/callback", "test_state");

        assertTrue(url.startsWith(FigmaOAuth2TokenManager.FIGMA_OAUTH_AUTHORIZE_URL));
        assertTrue(url.contains("client_id=my-client-id"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("state=test_state"));
        assertTrue(url.contains("scope="));
        assertTrue(url.contains("redirect_uri="));
    }

    @Test
    public void testBuildAuthorizationUrl_containsDefaultReadScopes() {
        FigmaOAuth2TokenManager manager = new FigmaOAuth2TokenManager("cid", "csecret");
        String url = manager.buildAuthorizationUrl("http://localhost/cb", "state1");

        assertTrue("Auth URL should contain file_content:read scope", url.contains("file_content%3Aread"));
        assertTrue("Auth URL should contain file_metadata:read scope", url.contains("file_metadata%3Aread"));
    }

    @Test
    public void testBuildAuthorizationUrl_usesCustomScopeWhenProvided() {
        FigmaOAuth2TokenManager manager = new FigmaOAuth2TokenManager(
                "cid",
                "csecret",
                "file_content:read file_metadata:read file_versions:read"
        );
        String url = manager.buildAuthorizationUrl("http://localhost/cb", "state1");

        assertTrue("Auth URL should contain custom scope value", url.contains("file_versions%3Aread"));
    }

    @Test
    public void testTokenEndpointUrl_isCorrect() {
        assertEquals("https://www.figma.com/api/oauth/token", FigmaOAuth2TokenManager.FIGMA_OAUTH_TOKEN_URL);
    }

    @Test
    public void testTokenResponse_getters() {
        FigmaOAuth2TokenManager.TokenResponse response =
                new FigmaOAuth2TokenManager.TokenResponse("acc_token", "ref_token", 3600L);

        assertEquals("acc_token", response.getAccessToken());
        assertEquals("ref_token", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
    }

    @Test
    public void testTokenResponse_toString_masksTokens() {
        FigmaOAuth2TokenManager.TokenResponse response =
                new FigmaOAuth2TokenManager.TokenResponse("acc_token", "ref_token", 1800L);
        String str = response.toString();

        assertTrue(str.contains("[SET]"));
        assertFalse("Token value should not be exposed in toString", str.contains("acc_token"));
        assertFalse("Refresh token value should not be exposed in toString", str.contains("ref_token"));
    }

    @Test
    public void testTokenResponse_toString_showsNullForMissingTokens() {
        FigmaOAuth2TokenManager.TokenResponse response =
                new FigmaOAuth2TokenManager.TokenResponse(null, null, 0L);
        String str = response.toString();

        assertTrue(str.contains("null"));
    }
}
