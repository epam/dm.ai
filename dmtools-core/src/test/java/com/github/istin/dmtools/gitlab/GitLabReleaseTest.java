// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public class GitLabReleaseTest {

    private static final String BASE_PATH = "https://gitlab.example.com";
    private static final String WORKSPACE = "mygroup";
    private static final String REPOSITORY = "myrepo";

    private GitLab gitLab;
    private File tempAsset;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(BASE_PATH)
                .auth("test-token")
                .workspaceName(WORKSPACE)
                .repoName(REPOSITORY)
                .branchName("main")
                .type(SourceCodeConfig.Type.GITLAB)
                .build();
        gitLab = mock(BasicGitLab.class, withSettings().useConstructor(config).defaultAnswer(CALLS_REAL_METHODS));
    }

    @After
    public void tearDown() throws IOException {
        if (tempAsset != null) {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    @Test
    public void testGetOrCreateRelease_returnsExistingReleaseWithoutCreate() throws IOException {
        JSONArray releases = new JSONArray()
                .put(buildReleaseJson("pr-attachments-storage", "PR Attachments Storage"));
        doReturn(releases.toString()).when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.getOrCreateRelease(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", "PR Attachments Storage", "main", "storage");

        JSONObject release = new JSONObject(result);
        assertEquals("pr-attachments-storage", release.getString("tag_name"));
        verify(gitLab, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testGetOrCreateRelease_createsReleaseWhenMissing() throws IOException {
        doReturn("[]").when(gitLab).execute(any(GenericRequest.class));
        doReturn(buildReleaseJson("pr-attachments-storage", "PR Attachments Storage").toString())
                .when(gitLab).post(any(GenericRequest.class));

        String result = gitLab.getOrCreateRelease(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", "PR Attachments Storage", "main", "storage");

        JSONObject release = new JSONObject(result);
        assertEquals("pr-attachments-storage", release.getString("tag_name"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        GenericRequest request = captor.getValue();
        assertTrue(request.url().contains("projects/mygroup%2Fmyrepo/releases"));

        JSONObject requestBody = new JSONObject(request.getBody());
        assertEquals("pr-attachments-storage", requestBody.getString("tag_name"));
        assertEquals("PR Attachments Storage", requestBody.getString("name"));
        assertEquals("main", requestBody.getString("ref"));
        assertEquals("storage", requestBody.getString("description"));
    }

    @Test
    public void testUploadReleaseAsset_uploadsBinaryAndCreatesReleaseLink() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".png");
        Files.writeString(tempAsset.toPath(), "png-data");

        doReturn("").when(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
        doReturn("{\"id\":2,\"name\":\"preview.png\",\"direct_asset_url\":\"https://gitlab.example.com/download\"}")
                .when(gitLab).post(any(GenericRequest.class));

        String result = gitLab.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", tempAsset.getAbsolutePath(),
                "preview.png", "image/png", null, null);

        assertTrue(result.contains("direct_asset_url"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitLab).uploadGenericPackageBinary(urlCaptor.capture(), fileCaptor.capture(), typeCaptor.capture());
        assertEquals(tempAsset.getAbsolutePath(), fileCaptor.getValue().getAbsolutePath());
        assertEquals("image/png", typeCaptor.getValue());
        assertTrue(urlCaptor.getValue().contains("packages/generic/release-assets/pr-attachments-storage/preview.png"));

        ArgumentCaptor<GenericRequest> postCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(postCaptor.capture());
        assertTrue(postCaptor.getValue().url().contains("releases/pr-attachments-storage/assets/links"));
        JSONObject linkBody = new JSONObject(postCaptor.getValue().getBody());
        assertEquals("preview.png", linkBody.getString("name"));
        assertEquals("package", linkBody.getString("link_type"));
    }

    @Test
    public void testUploadReleaseAsset_defaultsAssetNameToLocalFileName() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".txt");
        Files.writeString(tempAsset.toPath(), "hello");

        doReturn("").when(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
        doReturn("{}").when(gitLab).post(any(GenericRequest.class));

        gitLab.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", tempAsset.getAbsolutePath(),
                null, "text/plain", null, null);

        ArgumentCaptor<GenericRequest> postCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(postCaptor.capture());
        JSONObject linkBody = new JSONObject(postCaptor.getValue().getBody());
        assertEquals(tempAsset.getName(), linkBody.getString("name"));
    }

    @Test
    public void testUploadReleaseAsset_overwriteFalse_doesNotCheckExistingAssets() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".png");
        Files.writeString(tempAsset.toPath(), "png-data");

        doReturn("").when(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
        doReturn("{}").when(gitLab).post(any(GenericRequest.class));

        gitLab.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", tempAsset.getAbsolutePath(),
                "screenshot.png", "image/png", null, "false");

        verify(gitLab, never()).execute(any(GenericRequest.class));
        verify(gitLab, never()).delete(any(GenericRequest.class));
    }

    @Test
    public void testUploadReleaseAsset_overwriteTrue_deletesExistingLinkAndPackage() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".png");
        Files.writeString(tempAsset.toPath(), "png-data");

        JSONArray existingLinks = new JSONArray()
                .put(buildLinkJson(5L, "screenshot.png"));
        JSONArray existingPackages = new JSONArray()
                .put(buildPackageJson(77L, "release-assets", "pr-attachments-storage"));
        doReturn(existingLinks.toString(), existingPackages.toString())
                .when(gitLab).execute(any(GenericRequest.class));
        doReturn(null).when(gitLab).delete(any(GenericRequest.class));
        doReturn("").when(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
        doReturn("{}").when(gitLab).post(any(GenericRequest.class));

        gitLab.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", tempAsset.getAbsolutePath(),
                "screenshot.png", "image/png", null, "true");

        ArgumentCaptor<GenericRequest> deleteCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(2)).delete(deleteCaptor.capture());
        assertTrue(deleteCaptor.getAllValues().get(0).url().contains("assets/links/5"));
        assertTrue(deleteCaptor.getAllValues().get(1).url().contains("packages/77"));
    }

    @Test
    public void testUploadReleaseAsset_overwriteTrue_noExistingAsset_uploadsDirectly() throws IOException {
        tempAsset = File.createTempFile("release-asset-", ".png");
        Files.writeString(tempAsset.toPath(), "png-data");

        doReturn("[]").when(gitLab).execute(any(GenericRequest.class));
        doReturn("").when(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
        doReturn("{}").when(gitLab).post(any(GenericRequest.class));

        gitLab.uploadReleaseAsset(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", tempAsset.getAbsolutePath(),
                "new.png", "image/png", null, "true");

        verify(gitLab, never()).delete(any(GenericRequest.class));
        verify(gitLab, times(1)).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
    }

    @Test
    public void testListReleaseAssets_returnsLinksForRelease() throws IOException {
        JSONArray links = new JSONArray()
                .put(buildLinkJson(1L, "file-a.zip"))
                .put(buildLinkJson(2L, "file-b.txt"));
        doReturn(links.toString()).when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.listReleaseAssets(WORKSPACE, REPOSITORY, "pr-attachments-storage");

        JSONArray resultArray = new JSONArray(result);
        assertEquals(2, resultArray.length());
        assertEquals("file-a.zip", resultArray.getJSONObject(0).getString("name"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("releases/pr-attachments-storage/assets/links"));
    }

    @Test
    public void testDeleteReleaseAsset_deletesLinkAndPackage() throws IOException {
        JSONArray existingLinks = new JSONArray()
                .put(buildLinkJson(9L, "old.zip"));
        JSONArray existingPackages = new JSONArray()
                .put(buildPackageJson(44L, "release-assets", "pr-attachments-storage"));
        doReturn(existingLinks.toString(), existingPackages.toString())
                .when(gitLab).execute(any(GenericRequest.class));
        doReturn(null).when(gitLab).delete(any(GenericRequest.class));

        gitLab.deleteReleaseAsset(WORKSPACE, REPOSITORY, "pr-attachments-storage", "old.zip", null);

        ArgumentCaptor<GenericRequest> deleteCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(2)).delete(deleteCaptor.capture());
        assertTrue(deleteCaptor.getAllValues().get(0).url().contains("assets/links/9"));
        assertTrue(deleteCaptor.getAllValues().get(1).url().contains("packages/44"));
    }

    @Test
    public void testDownloadReleaseAsset_buildsCorrectUrlAndDelegatesToBinaryDownload() throws IOException {
        File targetFile = File.createTempFile("downloaded-", ".png");
        targetFile.deleteOnExit();

        doReturn(targetFile.getAbsolutePath())
                .when(gitLab).downloadBinaryToFile(anyString(), any(File.class));

        String result = gitLab.downloadReleaseAsset(
                WORKSPACE, REPOSITORY, "pr-attachments-storage", "preview.png", targetFile.getAbsolutePath(), null);

        assertEquals(targetFile.getAbsolutePath(), result);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitLab).downloadBinaryToFile(urlCaptor.capture(), any(File.class));
        assertTrue(urlCaptor.getValue().contains("packages/generic/release-assets/pr-attachments-storage/preview.png"));
    }

    private JSONObject buildLinkJson(long id, String name) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("url", "https://gitlab.example.com/download/" + name);
        json.put("direct_asset_url", "https://gitlab.example.com/direct/" + name);
        json.put("link_type", "package");
        return json;
    }

    private JSONObject buildPackageJson(long id, String name, String version) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("version", version);
        json.put("package_type", "generic");
        return json;
    }

    private JSONObject buildReleaseJson(String tagName, String name) {
        JSONObject json = new JSONObject();
        json.put("tag_name", tagName);
        json.put("name", name);
        return json;
    }
}
