// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.mock;

public class GitHubReleaseTest {

    private static final String BASE_PATH = "https://api.github.com";
    private static final String WORKSPACE = "IstiN";
    private static final String REPOSITORY = "dmtools";

    private GitHub gitHub;
    private File tempAsset;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(BASE_PATH)
                .auth("test-token")
                .workspaceName(WORKSPACE)
                .repoName(REPOSITORY)
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = mock(BasicGithub.class, withSettings().useConstructor(config).defaultAnswer(CALLS_REAL_METHODS));
    }

    @After
    public void tearDown() throws IOException {
        if (tempAsset != null) {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    @Test
    public void testGetOrCreateDraftRelease_returnsExistingDraftWithoutCreate() throws IOException {
        JSONArray releases = new JSONArray()
                .put(buildReleaseJson(321L, "pr-attachments-storage", "PR Attachments Storage", true));
        doReturn(releases.toString()).when(gitHub).execute(any(GenericRequest.class));

        String result = gitHub.getOrCreateDraftRelease(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", "PR Attachments Storage", "main", "storage");

        JSONObject release = new JSONObject(result);
        assertEquals(321L, release.getLong("id"));
        assertTrue(release.getBoolean("draft"));
        verify(gitHub, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testGetOrCreateDraftRelease_createsReleaseWhenMissing() throws IOException {
        doReturn("[]").when(gitHub).execute(any(GenericRequest.class));
        doReturn(buildReleaseJson(555L, "pr-attachments-storage", "PR Attachments Storage", true).toString())
                .when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.getOrCreateDraftRelease(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", "PR Attachments Storage", "main", "storage");

        JSONObject release = new JSONObject(result);
        assertEquals(555L, release.getLong("id"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).post(captor.capture());
        GenericRequest request = captor.getValue();
        assertTrue(request.url().contains("repos/IstiN/dmtools/releases"));

        JSONObject requestBody = new JSONObject(request.getBody());
        assertEquals("pr-attachments-storage", requestBody.getString("tag_name"));
        assertEquals("PR Attachments Storage", requestBody.getString("name"));
        assertEquals("main", requestBody.getString("target_commitish"));
        assertEquals("storage", requestBody.getString("body"));
        assertTrue(requestBody.getBoolean("draft"));
    }

    @Test
    public void testGetOrCreateDraftRelease_throwsWhenMatchingPublishedReleaseExists() throws IOException {
        JSONArray releases = new JSONArray()
                .put(buildReleaseJson(999L, "pr-attachments-storage", "PR Attachments Storage", false));
        doReturn(releases.toString()).when(gitHub).execute(any(GenericRequest.class));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                gitHub.getOrCreateDraftRelease(
                        WORKSPACE, REPOSITORY, "pr-attachments-storage", "PR Attachments Storage", null, null));

        assertTrue(exception.getMessage().contains("not a draft"));
        verify(gitHub, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testUploadReleaseAsset_buildsUploadUrlAndPassesResolvedMetadata() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".png");
        Files.writeString(tempAsset.toPath(), "png-data");

        doReturn("{\"browser_download_url\":\"https://github.com/download\"}")
                .when(gitHub).uploadReleaseAssetBinary(anyString(), any(File.class), anyString());

        String result = gitHub.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "323096697", tempAsset.getAbsolutePath(),
                "preview image.png", "image/png", "Screenshot Label");

        assertTrue(result.contains("browser_download_url"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHub).uploadReleaseAssetBinary(urlCaptor.capture(), fileCaptor.capture(), typeCaptor.capture());

        assertEquals(tempAsset.getAbsolutePath(), fileCaptor.getValue().getAbsolutePath());
        assertEquals("image/png", typeCaptor.getValue());
        assertTrue(urlCaptor.getValue().contains("/repos/IstiN/dmtools/releases/323096697/assets"));
        assertTrue(urlCaptor.getValue().contains("name=preview%20image.png"));
        assertTrue(urlCaptor.getValue().contains("label=Screenshot%20Label"));
    }

    @Test
    public void testUploadReleaseAsset_defaultsAssetNameToLocalFileName() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".txt");
        Files.writeString(tempAsset.toPath(), "hello");

        doAnswer(invocation -> "{\"state\":\"uploaded\"}")
                .when(gitHub).uploadReleaseAssetBinary(anyString(), any(File.class), anyString());

        gitHub.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "323096697", tempAsset.getAbsolutePath(),
                null, "text/plain", null);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHub, times(1)).uploadReleaseAssetBinary(urlCaptor.capture(), any(File.class), anyString());
        assertTrue(urlCaptor.getValue().contains("name=" + tempAsset.getName()));
    }

    private JSONObject buildReleaseJson(long id, String tagName, String name, boolean draft) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("tag_name", tagName);
        json.put("name", name);
        json.put("draft", draft);
        json.put("upload_url", "https://uploads.github.com/repos/IstiN/dmtools/releases/" + id + "/assets{?name,label}");
        return json;
    }
}
