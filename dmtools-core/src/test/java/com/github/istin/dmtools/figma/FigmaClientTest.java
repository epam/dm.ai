// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FigmaClientTest {

    private FigmaClient figmaClient;
    private static final String BASE_PATH = "https://api.figma.com/v1/";
    private static final String AUTHORIZATION = "Bearer token";

    @Before
    public void setUp() throws Exception {
        figmaClient = Mockito.spy(new FigmaClient(BASE_PATH, AUTHORIZATION));
    }

    @Test
    public void testPath() {
        String path = "images";
        String expected = BASE_PATH + path;
        assertEquals(expected, figmaClient.path(path));
    }


    @Test
    public void testGetTimeout() {
        assertEquals(300, figmaClient.getTimeout());
    }

    @Test
    public void testDownloadImageAsBase64() throws IOException {
        String path = "https://image.url";
        String expectedBase64 = "base64string";

        doReturn(new File("image.png")).when(figmaClient).downloadImage(path);
        doReturn(expectedBase64).when(figmaClient).downloadImageAsBase64(path);

        String base64 = figmaClient.downloadImageAsBase64(path);
        assertEquals(expectedBase64, base64);
    }

    @Test
    public void testParseFileId() {
        String url = "https://www.figma.com/file/1234567890abcdef";
        String expectedFileId = "1234567890abcdef";
        assertEquals(expectedFileId, figmaClient.parseFileId(url));
    }

    @Test
    public void testExtractValueByParameter() {
        String url = "https://www.figma.com/file/1234567890abcdef?node-id=1:2";
        String paramName = "node-id";
        String expectedValue = "1:2";
        assertEquals(expectedValue, figmaClient.extractValueByParameter(url, paramName));
    }

    @Test
    public void testExtractTeamId_rawId() {
        assertEquals("1633438210497791577", FigmaClient.extractTeamId("1633438210497791577"));
    }

    @Test
    public void testExtractTeamId_fromTeamUrl() {
        String url = "https://www.figma.com/files/1008118788610687562/team/1633438210497791577?fuid=1626552638292432631";
        assertEquals("1633438210497791577", FigmaClient.extractTeamId(url));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExtractTeamId_invalidUrl() {
        FigmaClient.extractTeamId("https://www.figma.com/file/1234567890abcdef");
    }

    @Test
    public void testExtractProjectId_rawId() {
        assertEquals("123456789", FigmaClient.extractProjectId("123456789"));
    }

    @Test
    public void testExtractProjectId_fromProjectUrl() {
        String url = "https://www.figma.com/files/project/123456789/My-Project";
        assertEquals("123456789", FigmaClient.extractProjectId(url));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExtractProjectId_invalidUrl() {
        FigmaClient.extractProjectId("https://www.figma.com/file/1234567890abcdef");
    }

    @Test
    public void testConvertUrlToFile() throws Exception {
        String href = "https://www.figma.com/file/1234567890abcdef";
        File expectedFile = new File("image.png");

        doReturn("https://image.url").when(figmaClient).getImageOfSource(href);
        doReturn(expectedFile).when(figmaClient).downloadImage("https://image.url");

        File file = figmaClient.convertUrlToFile(href);
        assertEquals(expectedFile, file);
    }

    @Test
    public void testGetAllTeams() throws Exception {
        String response = "{\"teams\": [{\"id\": \"1\", \"name\": \"Team 1\"}]}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        JSONArray teams = figmaClient.getAllTeams();
        assertEquals(1, teams.length());
        assertEquals("1", teams.getJSONObject(0).getString("id"));
    }

    @Test
    public void testGetAllCommentsForAllTeams() throws Exception {
        doNothing().when(figmaClient).getAllCommentsForTeam(anyString());
        doReturn(new JSONArray("[{\"id\": \"1\", \"name\": \"Team 1\"}]")).when(figmaClient).getAllTeams();

        figmaClient.getAllCommentsForAllTeams();
        verify(figmaClient, times(1)).getAllCommentsForTeam("1");
    }

    @Test
    public void testGetAllCommentsForTeam() throws Exception {
        doReturn(new JSONArray("[{\"id\": \"1\"}]")).when(figmaClient).getProjects(anyString());
        doReturn(new JSONArray("[{\"key\": \"fileKey\"}]")).when(figmaClient).getFiles(anyString());
        doReturn(List.of(mock(IComment.class))).when(figmaClient).getComments(anyString());

        figmaClient.getAllCommentsForTeam("teamId");
        verify(figmaClient, times(1)).getProjects("teamId");
        verify(figmaClient, times(1)).getFiles("1");
        verify(figmaClient, times(1)).getComments("fileKey");
    }

    @Test
    public void testGetProjects() throws Exception {
        String response = "{\"projects\": [{\"id\": \"1\", \"name\": \"Project 1\"}]}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        JSONArray projects = figmaClient.getProjects("teamId");
        assertEquals(1, projects.length());
        assertEquals("1", projects.getJSONObject(0).getString("id"));
    }

    @Test
    public void testListTeamProjects_rawId() throws Exception {
        doReturn(new JSONArray("[{\"id\": \"1\", \"name\": \"Project 1\"}]")).when(figmaClient).getProjects("123456789");

        String result = figmaClient.listTeamProjects("123456789");
        verify(figmaClient, times(1)).getProjects("123456789");
        assertEquals(new JSONArray("[{\"id\": \"1\", \"name\": \"Project 1\"}]").toString(), result);
    }

    @Test
    public void testListTeamProjects_fromTeamUrl() throws Exception {
        String url = "https://www.figma.com/files/1008118788610687562/team/1633438210497791577?fuid=1626552638292432631";
        doReturn(new JSONArray("[{\"id\": \"1\"}]")).when(figmaClient).getProjects("1633438210497791577");

        figmaClient.listTeamProjects(url);
        verify(figmaClient, times(1)).getProjects("1633438210497791577");
    }

    @Test
    public void testGetFiles() throws Exception {
        String response = "{\"files\": [{\"key\": \"fileKey\", \"name\": \"File 1\"}]}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        JSONArray files = figmaClient.getFiles("projectId");
        assertEquals(1, files.length());
        assertEquals("fileKey", files.getJSONObject(0).getString("key"));
    }

    @Test
    public void testListProjectFiles_rawId() throws Exception {
        doReturn(new JSONArray("[{\"key\": \"fileKey\"}]")).when(figmaClient).getFiles("987654321");

        String result = figmaClient.listProjectFiles("987654321");
        verify(figmaClient, times(1)).getFiles("987654321");
        assertEquals(new JSONArray("[{\"key\": \"fileKey\"}]").toString(), result);
    }

    @Test
    public void testListProjectFiles_fromProjectUrl() throws Exception {
        String url = "https://www.figma.com/files/project/123456789/My-Project";
        doReturn(new JSONArray("[{\"key\": \"fileKey\"}]")).when(figmaClient).getFiles("123456789");

        figmaClient.listProjectFiles(url);
        verify(figmaClient, times(1)).getFiles("123456789");
    }

    @Test
    public void testGetComments() throws Exception {
        String response = "{\"comments\": [{\"id\": \"1\", \"text\": \"Comment 1\"}]}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        List<IComment> comments = figmaClient.getComments("fileKey");
        assertEquals(1, comments.size());
    }

    @Test
    public void testGetFileComments_byFileUrl() throws Exception {
        String href = "https://www.figma.com/file/1234567890abcdef";
        IComment comment = mock(IComment.class);
        doReturn("{\"id\":\"1\",\"message\":\"Looks good\"}").when(comment).toString();
        doReturn(List.of(comment)).when(figmaClient).getComments("1234567890abcdef");

        String result = figmaClient.getFileComments(href);
        verify(figmaClient, times(1)).getComments("1234567890abcdef");
        assertEquals("Looks good", new JSONArray(result).getJSONObject(0).getString("message"));
    }

    @Test
    public void testGetFileComments_byRawFileKey() throws Exception {
        IComment comment = mock(IComment.class);
        doReturn("{\"id\":\"1\",\"message\":\"Ship it\"}").when(comment).toString();
        doReturn(List.of(comment)).when(figmaClient).getComments("rawFileKey123");

        // A bare file key has no path segments for parseFileId to split on, so it must fall back to using it as-is.
        String result = figmaClient.getFileComments("rawFileKey123");
        verify(figmaClient, times(1)).getComments("rawFileKey123");
        assertEquals("Ship it", new JSONArray(result).getJSONObject(0).getString("message"));
    }


    @Test
    public void testSetUseOAuth2Bearer_defaultIsFalse() throws Exception {
        FigmaClient client = new FigmaClient("https://api.figma.com/v1", "test_token");
        assertFalse(client.isUseOAuth2Bearer());
    }

    @Test
    public void testSetUseOAuth2Bearer_canBeEnabled() throws Exception {
        FigmaClient client = new FigmaClient("https://api.figma.com/v1", "test_token");
        client.setUseOAuth2Bearer(true);
        assertTrue(client.isUseOAuth2Bearer());
    }

    @Test
    public void testSign_usesXFigmaTokenByDefault() throws Exception {
        FigmaClient client = new FigmaClient("https://api.figma.com/v1", "my_personal_token");
        Request.Builder builder = new Request.Builder().url("https://api.figma.com/v1/me");
        Request.Builder signed = client.sign(builder);
        Request request = signed.build();

        assertEquals("my_personal_token", request.header("X-Figma-Token"));
        assertNull(request.header("Authorization"));
    }

    @Test
    public void testSign_usesBearerWhenOAuth2Enabled() throws Exception {
        FigmaClient client = new FigmaClient("https://api.figma.com/v1", "oauth2_access_token");
        client.setUseOAuth2Bearer(true);
        Request.Builder builder = new Request.Builder().url("https://api.figma.com/v1/me");
        Request.Builder signed = client.sign(builder);
        Request request = signed.build();

        assertEquals("Bearer oauth2_access_token", request.header("Authorization"));
        assertNull(request.header("X-Figma-Token"));
    }
}