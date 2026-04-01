// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchResultsAssessmentAgentTest {

    private SearchResultsAssessmentAgent agent;

    @Before
    public void setup() {
        agent = new SearchResultsAssessmentAgent();
    }

    @Test
    public void testJiraSearchResults() throws Exception {
        String searchResults = new JSONObject()
                .put("issues", new JSONArray()
                        .put(new JSONObject()
                                .put("key", "AUTH-123")
                                .put("summary", "Implement login functionality"))
                        .put(new JSONObject()
                                .put("key", "UI-456")
                                .put("summary", "Design dashboard")))
                .toString();

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "jira",
                "key",
                "Fix authentication issues",
                searchResults
        );

        JSONArray result = agent.run(params);
        assertTrue("Result should not be empty", !result.isEmpty());
        assertEquals("Should return AUTH-123 key", "AUTH-123", result.getString(0));
    }

    @Test
    public void testFileContentSearch() throws Exception {
        String searchResults = new JSONObject()
                .put("files", new JSONArray()
                        .put(new JSONObject()
                                .put("path", "src/main/java/auth/LoginService.java")
                                .put("matches", new JSONArray()
                                        .put("public void authenticate(String username, String password)")
                                        .put("private void validateCredentials(UserDTO user)"))))
                .toString();

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "files",
                "textMatch",
                "Fix authentication method",
                searchResults
        );

        JSONArray result = agent.run(params);
        assertTrue("Should return one match", result.length() >= 1);
        assertTrue("Should contain authenticate method",
                result.getString(0).contains("authenticate"));
    }

    @Test
    public void testConfluenceContentSearch() throws Exception {
        String searchResults = new JSONObject()
                .put("pages", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "12345")
                                .put("title", "Authentication Documentation")
                                .put("content", new JSONArray()
                                        .put("OAuth2 token configuration steps")
                                        .put("API versioning guidelines"))))
                .toString();

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "confluence",
                "contentMatch",
                "Update OAuth2 documentation",
                searchResults
        );

        JSONArray result = agent.run(params);
        assertEquals("Should return one match", 1, result.length());
        assertTrue("Should contain OAuth2",
                result.getString(0).contains("OAuth2"));
    }

    @Test
    public void testFilePathsSearch() throws Exception {
        String searchResults = new JSONObject()
                .put("files", new JSONArray()
                        .put(new JSONObject()
                                .put("path", "src/main/java/auth/LoginService.java")
                                .put("matches", new JSONArray()
                                        .put("public class LoginService {")
                                        .put("private AuthenticationManager authManager;")))
                        .put(new JSONObject()
                                .put("path", "src/main/java/auth/AuthenticationManager.java")
                                .put("matches", new JSONArray()
                                        .put("public class AuthenticationManager {")
                                        .put("public boolean validateCredentials(String username, String password)")))
                        .put(new JSONObject()
                                .put("path", "src/main/java/user/UserProfile.java")
                                .put("matches", new JSONArray()
                                        .put("public class UserProfile {"))))
                .toString();

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "files",
                "path",
                "Update authentication manager implementation and related to that code",
                searchResults
        );

        JSONArray result = agent.run(params);
        assertEquals("Should return two matches", 2, result.length());
        assertTrue("Should contain LoginService.java",
                result.getString(0).contains("LoginService.java"));
        assertTrue("Should contain AuthenticationManager.java",
                result.getString(1).contains("AuthenticationManager.java"));
    }

    @Test
    public void testConfluenceIdSearch() throws Exception {
        String searchResults = new JSONObject()
                .put("pages", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "CONF-123")
                                .put("title", "Authentication Overview")
                                .put("content", new JSONArray()
                                        .put("OAuth2 implementation details")
                                        .put("Authentication flow description")))
                        .put(new JSONObject()
                                .put("id", "CONF-124")
                                .put("title", "OAuth2 Configuration Guide")
                                .put("content", new JSONArray()
                                        .put("Step by step guide for OAuth2 setup")
                                        .put("Token management procedures")))
                        .put(new JSONObject()
                                .put("id", "CONF-125")
                                .put("title", "API Endpoints")
                                .put("content", new JSONArray()
                                        .put("REST endpoints documentation")
                                        .put("Rate limiting information"))))
                .toString();

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "confluence",
                "id",
                "Review OAuth2 implementation documentation",
                searchResults
        );

        JSONArray result = agent.run(params);
        assertEquals("Should return two matches", 2, result.length());
        assertEquals("First result should be CONF-123",
                "CONF-123", result.getString(0));
        assertEquals("Second result should be CONF-124",
                "CONF-124", result.getString(1));
    }

    @Test
    public void testInvalidSearchResults() throws Exception {
        String searchResults = "invalid json";

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "jira",
                "key",
                "Some task",
                searchResults
        );

        assertTrue(agent.run(params).isEmpty());;
    }

    public void testEmptySearchResults() throws Exception {
        String searchResults = new JSONObject()
                .put("issues", new JSONArray())
                .toString();

        SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                "jira",
                "key",
                "Some task",
                searchResults
        );

        assertTrue(agent.run(params).isEmpty());
    }
}