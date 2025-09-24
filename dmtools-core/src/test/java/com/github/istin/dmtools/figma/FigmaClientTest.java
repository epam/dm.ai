package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
    public void testGetFiles() throws Exception {
        String response = "{\"files\": [{\"key\": \"fileKey\", \"name\": \"File 1\"}]}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        JSONArray files = figmaClient.getFiles("projectId");
        assertEquals(1, files.length());
        assertEquals("fileKey", files.getJSONObject(0).getString("key"));
    }

    @Test
    public void testGetComments() throws Exception {
        String response = "{\"comments\": [{\"id\": \"1\", \"text\": \"Comment 1\"}]}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        List<IComment> comments = figmaClient.getComments("fileKey");
        assertEquals(1, comments.size());
    }
}