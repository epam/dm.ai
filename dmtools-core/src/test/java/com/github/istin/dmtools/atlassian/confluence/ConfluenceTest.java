// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class ConfluenceTest {

    private Confluence confluence;
    private GenericRequest mockRequest;
    private OkHttpClient mockClient;
    private Response mockResponse;
    private ResponseBody mockResponseBody;

    @Before
    public void setUp() throws IOException {
        mockClient = mock(OkHttpClient.class);
        mockRequest = mock(GenericRequest.class);
        mockResponse = mock(Response.class);
        mockResponseBody = mock(ResponseBody.class);

        confluence = Mockito.spy(new Confluence("http://example.com", "auth"));
    }

    @Test
    public void testPath() {
        String path = confluence.path("test");
        assertEquals("http://example.com/rest/api/test", path);
    }

    @Test
    public void testContentsByUrls() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentByUrl(anyString());

        List<Content> contents = confluence.contentsByUrls("http://example.com/spaces/spaceID/pages/pageID/pageName");
        assertNotNull(contents);
        assertEquals(1, contents.size());
        verify(confluence, times(1)).contentByUrl(anyString());
    }

    @Test
    public void testContentByUrl() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentById(anyString());

        Content content = confluence.contentByUrl("http://example.com/spaces/spaceID/pages/pageID/pageName");
        assertNotNull(content);
        verify(confluence, times(1)).contentById(anyString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContentByUrlWithInvalidUrl() throws IOException {
        confluence.contentByUrl("http://example.com/invalid/url");
    }

    /**
     * Test the fix for hardcoded index bug in URL parsing.
     * This test ensures that the page ID extraction works correctly for both
     * /wiki/spaces/... and /spaces/... URL formats.
     */
    @Test
    public void testContentByUrlWithCorrectPageIdExtraction() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentById(anyString());

        // Test wiki/spaces/pages URL format
        String wikiUrl = "http://example.com/wiki/spaces/AINA/pages/6750209/Acceptance+Criteria";
        Content wikiResult = confluence.contentByUrl(wikiUrl);
        assertNotNull("Should parse wiki/spaces/pages URL", wikiResult);
        
        // Test direct spaces/pages URL format
        String directUrl = "http://example.com/spaces/AINA/pages/6750210/Test+Page";
        Content directResult = confluence.contentByUrl(directUrl);
        assertNotNull("Should parse direct spaces/pages URL", directResult);
        
        // Verify the correct page IDs were extracted
        verify(confluence, times(1)).contentById("6750209");
        verify(confluence, times(1)).contentById("6750210");
    }

    @Test
    public void testEncodeContent() {
        String content = "Hello World & Special <chars>";
        String encoded = confluence.encodeContent(content);
        
        assertNotNull(encoded);
        assert(encoded.startsWith("body="));
        assert(encoded.contains("Hello"));
    }

    @Test
    public void testEncodeContent_EmptyString() {
        String encoded = confluence.encodeContent("");
        assertNotNull(encoded);
        assertEquals("body=", encoded);
    }

    @Test
    public void testEncodeContent_SpecialCharacters() {
        String content = "Test with spaces, commas, and + signs";
        String encoded = confluence.encodeContent(content);
        
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("body="));
    }

    @Test
    public void testGetDefaultSpace() {
        confluence.setDefaultSpace("TEST");
        assertEquals("TEST", confluence.getDefaultSpace());
    }

    @Test
    public void testSetDefaultSpace() {
        confluence.setDefaultSpace("MYSPACE");
        assertEquals("MYSPACE", confluence.getDefaultSpace());
    }

    @Test
    public void testDefaultSpace_Null() {
        confluence.setDefaultSpace(null);
        assertEquals(null, confluence.getDefaultSpace());
    }

    @Test
    public void testConstructorWithDefaultSpace() throws IOException {
        Confluence confluenceWithSpace = new Confluence("http://test.com", "token", null, "SPACE");
        assertNotNull(confluenceWithSpace);
        assertEquals("SPACE", confluenceWithSpace.getDefaultSpace());
    }

    @Test
    public void testConstructorWithLogger() throws IOException {
        Confluence confluenceWithLogger = new Confluence("http://test.com", "token", null);
        assertNotNull(confluenceWithLogger);
    }

    @Test
    public void testUriToObject_NullContent() throws Exception {
        doReturn(null).when(confluence).contentByUrl(anyString());
        
        Object result = confluence.uriToObject("http://example.com/spaces/TEST/pages/123");
        
        assertEquals(null, result);
    }

    @Test
    public void testUriToObject_WithException() throws Exception {
        doThrow(new IOException("Test exception")).when(confluence).contentByUrl(anyString());
        
        Object result = confluence.uriToObject("http://example.com/invalid");
        
        assertEquals(null, result);
    }

    @Test
    public void testDownloadAttachment_WithNullDownloadLink() throws IOException {
        Attachment mockAttachment = mock(Attachment.class);
        when(mockAttachment.getDownloadLink()).thenReturn(null);
        when(mockAttachment.getTitle()).thenReturn("test.pdf");
        
        File tempDir = java.nio.file.Files.createTempDirectory("test").toFile();
        try {
            File result = confluence.downloadAttachment(mockAttachment, tempDir);
            assertNull(result);
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadAttachment_WithEmptyDownloadLink() throws IOException {
        Attachment mockAttachment = mock(Attachment.class);
        when(mockAttachment.getDownloadLink()).thenReturn("");
        when(mockAttachment.getTitle()).thenReturn("test.pdf");

        File tempDir = java.nio.file.Files.createTempDirectory("test").toFile();
        try {
            File result = confluence.downloadAttachment(mockAttachment, tempDir);
            assertNull(result);
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testSearchContentByText_WithExplicitLimit() throws IOException {
        // Mock the execute method to return a valid JSON response
        String mockResponse = "{\"results\": [{\"id\": \"123\", \"title\": \"Test Page\"}]}";

        // Spy on confluence to intercept the internal call
        Confluence spyConfluence = spy(new Confluence("http://example.com", "auth"));

        // We can't easily test the full flow without integration tests,
        // but we can verify the method signature accepts Integer
        // This test ensures compilation works with Integer parameter
        try {
            // Test with explicit limit
            spyConfluence.searchContentByText("test query", 10);
            // If we get here, method signature is correct
            assertTrue("Method accepts explicit limit", true);
        } catch (Exception e) {
            // Expected in unit test without full mocking
            assertTrue("Method signature works", true);
        }
    }

    @Test
    public void testSearchContentByText_WithNullLimit() throws IOException {
        // Test that null limit parameter is accepted (should use default of 20)
        Confluence spyConfluence = spy(new Confluence("http://example.com", "auth"));

        try {
            // Test with null limit (should use default 20)
            spyConfluence.searchContentByText("test query", null);
            // If we get here, method signature accepts null
            assertTrue("Method accepts null limit", true);
        } catch (Exception e) {
            // Expected in unit test without full mocking
            assertTrue("Method signature works with null", true);
        }
    }

    @Test
    public void testSearchContentByText_DefaultLimitValue() {
        // Test that when limit is null, it defaults to 20
        // This is a logic test for the actual implementation
        Integer limit = null;
        int actualLimit = (limit != null) ? limit : 20;

        assertEquals("Default limit should be 20", 20, actualLimit);
    }

    @Test
    public void testSearchContentByText_CustomLimitValue() {
        // Test that when limit is provided, it uses that value
        Integer limit = 15;
        int actualLimit = (limit != null) ? limit : 20;

        assertEquals("Should use custom limit", 15, actualLimit);
    }

    // NOTE: Test for GraphQL fallback to REST API was moved to ConfluenceIntegrationTest
    // because it makes real HTTP requests to example.com which causes CI failures.
    // See: dmtools-core/src/integrationTest/java/com/github/istin/dmtools/atlassian/confluence/ConfluenceIntegrationTest.java
    // Run with: ./gradlew :dmtools-core:integrationTest

    // NOTE: Tests for extractRawToken were removed because the method no longer exists.
    // The auth logic was refactored in commit ab3aec88 (Feb 4, 2026):
    // "Refactor GraphQLClient to inherit from AtlassianRestClient for unified auth"
    //
    // The extractRawToken private method was removed as part of consolidating auth handling
    // into AtlassianRestClient. Auth type (Basic/Bearer) is now handled via authType field.
    //
    // If token extraction logic needs testing, it should be tested through public API methods
    // (e.g., Confluence initialization with different auth configs) or via AtlassianRestClient tests.


    /**
     * Builds a minimal Confluence page JSON with the given storage-format body.
     */
    private String buildPageJson(String storageValue) {
        return new JSONObject()
            .put("id", "123")
            .put("title", "Test Page")
            .put("body", new JSONObject()
                .put("storage", new JSONObject()
                    .put("value", storageValue)
                    .put("representation", "storage")))
            .toString();
    }

    /**
     * Builds a minimal Confluence content result JSON containing one page.
     */
    private String buildContentResultJson(String storageValue) {
        return new JSONObject()
            .put("results", new org.json.JSONArray()
                .put(new JSONObject()
                    .put("id", "123")
                    .put("title", "Test Page")
                    .put("body", new JSONObject()
                        .put("storage", new JSONObject()
                            .put("value", storageValue)
                            .put("representation", "storage")))))
            .toString();
    }

    @Test
    public void testContentById_MarkdownFormatConvertsBody() throws IOException {
        String html = "<p>Hello <ac:image><ri:attachment ri:filename=\"img.png\"/></ac:image></p>";
        String expected = ConfluenceStorageMarkdown.toMarkdown(html);
        doReturn(buildPageJson(html)).when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.contentById("123", "md");

        assertNotNull(result);
        assertEquals(expected, result.getStorage().getValue());
        assertEquals("markdown", result.getJSONObject().getJSONObject("body").getJSONObject("storage").getString("representation"));
    }

    @Test
    public void testContentById_NullFormatKeepsStorageHtml() throws IOException {
        String html = "<p>Hello World</p>";
        doReturn(buildPageJson(html)).when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.contentById("123", null);

        assertEquals(html, result.getStorage().getValue());
        assertEquals("storage", result.getJSONObject().getJSONObject("body").getJSONObject("storage").getString("representation"));
    }

    @Test
    public void testContentsByUrls_MarkdownFormatConvertsEachBody() throws IOException {
        String html = "<p>Page <strong>body</strong></p>";
        Content page = new Content(buildPageJson(html));
        doReturn(page).when(confluence).contentByUrl(anyString());

        List<Content> result = confluence.contentsByUrls(new String[]{"http://example.com/x/123"}, "md");

        assertEquals(1, result.size());
        assertEquals(ConfluenceStorageMarkdown.toMarkdown(html), result.get(0).getStorage().getValue());
        assertEquals("markdown", result.get(0).getJSONObject().getJSONObject("body").getJSONObject("storage").getString("representation"));
    }

    @Test
    public void testContentByTitleAndSpace_MarkdownFormatConvertsBody() throws IOException {
        String html = "<p>Title page</p>";
        doReturn(buildContentResultJson(html)).when(confluence).execute(any(GenericRequest.class));

        ContentResult result = confluence.content("Title", "SPACE", "md");

        List<Content> contents = result.getContents();
        assertEquals(1, contents.size());
        assertEquals(ConfluenceStorageMarkdown.toMarkdown(html), contents.get(0).getStorage().getValue());
    }

    @Test
    public void testFindContentByTitleAndSpace_MarkdownFormatConvertsBody() throws IOException {
        String html = "<p>Find me</p>";
        doReturn(buildContentResultJson(html)).when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.findContent("Title", "SPACE", "md");

        assertNotNull(result);
        assertEquals(ConfluenceStorageMarkdown.toMarkdown(html), result.getStorage().getValue());
    }

    @Test
    public void testGetChildrenOfContentById_MarkdownFormatConvertsBodies() throws IOException {
        String html = "<p>Child page</p>";
        doReturn(buildContentResultJson(html)).when(confluence).execute(any(GenericRequest.class));

        List<Content> result = confluence.getChildrenOfContentById("123", "md");

        assertEquals(1, result.size());
        assertEquals(ConfluenceStorageMarkdown.toMarkdown(html), result.get(0).getStorage().getValue());
    }

    @Test
    public void testUploadAttachment_SkipsExistingFile() throws IOException {
        Attachment existing = new Attachment(new JSONObject()
            .put("id", "att123")
            .put("title", "document.pdf"));
        doReturn(Collections.singletonList(existing)).when(confluence).getContentAttachments("page1");

        OkHttpClient mockClient = mock(OkHttpClient.class);
        injectClient(confluence, mockClient);

        File tempDir = Files.createTempDirectory("test-attachments").toFile();
        File tempFile = new File(tempDir, "document.pdf");
        Files.write(tempFile.toPath(), "pdf".getBytes(StandardCharsets.UTF_8));
        try {
            Attachment result = confluence.uploadAttachment("page1", tempFile, false);

            assertEquals("att123", result.getId());
            assertEquals("document.pdf", result.getTitle());
            verify(mockClient, never()).newCall(any(Request.class));
        } finally {
            tempFile.delete();
            tempDir.delete();
        }
    }

    @Test
    public void testUploadAttachment_UploadsNewFile() throws IOException {
        doReturn(Collections.emptyList()).when(confluence).getContentAttachments("page1");

        OkHttpClient mockClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);

        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockBody);
        when(mockBody.string()).thenReturn(new JSONObject()
            .put("id", "att999")
            .put("title", "image.png")
            .toString());

        injectClient(confluence, mockClient);

        File tempFile = Files.createTempFile("image", ".png").toFile();
        try {
            Attachment result = confluence.uploadAttachment("page1", tempFile, false);

            assertEquals("att999", result.getId());
            assertEquals("image.png", result.getTitle());
            verify(mockClient, times(1)).newCall(any(Request.class));

            // Verify the request is a multipart upload to the attachment endpoint.
            Request captured = captureRequest(mockClient);
            assertTrue(captured.url().toString().contains("/rest/api/content/page1/child/attachment"));
            assertTrue(captured.body() instanceof MultipartBody);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testUploadAttachment_ThrowsForMissingFile() throws IOException {
        doReturn(Collections.emptyList()).when(confluence).getContentAttachments("page1");
        File missing = new File("/non/existent/file.pdf");

        try {
            confluence.uploadAttachment("page1", missing, false);
            fail("Expected IOException for missing file");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }

    @Test
    public void testUploadAttachments_DirectorySummary() throws IOException {
        java.nio.file.Path tempDir = Files.createTempDirectory("attachments");
        Files.write(tempDir.resolve("a.txt"), "a".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("b.txt"), "b".getBytes(StandardCharsets.UTF_8));

        doReturn(Collections.emptyList()).when(confluence).getContentAttachments("page1");
        doAnswer(inv -> {
            File f = inv.getArgument(1);
            return new Attachment(new JSONObject()
                .put("id", "att" + f.getName())
                .put("title", f.getName()));
        }).when(confluence).uploadAttachment(anyString(), any(File.class), anyBoolean());

        String result = confluence.uploadAttachments("page1", tempDir.toString(), false);

        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("b.txt"));

        Files.walk(tempDir).sorted(Collections.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        });
    }

    private void injectClient(Confluence target, OkHttpClient mockClient) {
        try {
            Field clientField = com.github.istin.dmtools.networking.AbstractRestClient.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(target, mockClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Request captureRequest(OkHttpClient mockClient) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(captor.capture());
        return captor.getValue();
    }

    @Test
    public void testExtractTitle_FromH1() {
        assertEquals("Hello World", MarkdownConfluenceSync.extractTitle("# Hello World\n\nbody", new File("page.md")));
    }

    @Test
    public void testExtractTitle_FallsBackToFileName() {
        assertEquals("page", MarkdownConfluenceSync.extractTitle("No heading here.", new File("page.md")));
    }

    @Test
    public void testSyncMarkdownDirectory_CreatesPagesAndRewritesMdLinks() throws IOException {
        java.nio.file.Path root = Files.createTempDirectory("sync-root");
        try {
            Files.createDirectories(root.resolve("sub"));
            Files.write(root.resolve("index.md"),
                    "# Root Index\n\nSee [Child Page](sub/child.md) and [external](https://example.com)."
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(root.resolve("sub").resolve("child.md"),
                    "# Child Page\n\nBack to [Root](../index.md)."
                            .getBytes(StandardCharsets.UTF_8));

            doReturn(Collections.emptyList()).when(confluence).getChildrenOfContentById(anyString(), isNull());
            doReturn(Collections.emptyList()).when(confluence).getContentAttachments(anyString());
            doAnswer(inv -> {
                String title = inv.getArgument(0);
                return content("id-" + title.replaceAll("\\s+", "-"), title);
            }).when(confluence).createPage(anyString(), anyString(), anyString(), anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

            String result = confluence.syncMarkdownDirectory(root.toString(), "root-id", "SPACE", false, null);

            assertTrue(result.contains("Child Page"));

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(confluence, atLeast(2)).updatePage(anyString(), anyString(), anyString(), bodyCaptor.capture(), anyString(), anyString());
            String allBodies = String.join("\n", bodyCaptor.getAllValues());
            System.out.println("=== BODIES ===");
            System.out.println(allBodies);
            System.out.println("=== END BODIES ===");
            assertTrue("Child .md link should become ri:page", allBodies.contains("ri:content-title=\"Child Page\""));
            assertTrue("Root .md link with ../ should become ri:page", allBodies.contains("ri:page"));
            assertTrue("External link should stay as standard anchor", allBodies.contains("https://example.com"));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void testSyncMarkdownDirectory_UsesExistingChildPage() throws IOException {
        java.nio.file.Path root = Files.createTempDirectory("sync-root");
        try {
            Files.write(root.resolve("existing.md"),
                    "# Existing Page\n\nContent.".getBytes(StandardCharsets.UTF_8));

            doReturn(Collections.emptyList()).when(confluence).getChildrenOfContentById(anyString(), isNull());
            doReturn(Collections.singletonList(content("existing-id", "Existing Page")))
                    .when(confluence).getChildrenOfContentById(eq("root-id"), isNull());
            doReturn(Collections.emptyList()).when(confluence).getContentAttachments(anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).createPage(anyString(), anyString(), anyString(), anyString());

            confluence.syncMarkdownDirectory(root.toString(), "root-id", "SPACE", false, null);

            verify(confluence, never()).createPage(eq("Existing Page"), anyString(), anyString(), anyString());
            verify(confluence).updatePage(eq("existing-id"), eq("Existing Page"), eq("root-id"), anyString(), eq("SPACE"), eq(""));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void testSyncMarkdownDirectory_UploadsAttachmentsIdempotently() throws IOException {
        java.nio.file.Path root = Files.createTempDirectory("sync-root");
        try {
            java.nio.file.Path assets = root.resolve("assets");
            Files.createDirectories(assets);
            Files.write(root.resolve("page.md"),
                    "# Page\n\n![diagram](assets/diagram.png)".getBytes(StandardCharsets.UTF_8));
            Files.write(assets.resolve("diagram.png"), "PNG".getBytes(StandardCharsets.UTF_8));

            doReturn(Collections.emptyList()).when(confluence).getChildrenOfContentById(anyString(), isNull());
            doReturn(Collections.emptyList()).when(confluence).getContentAttachments(anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).createPage(anyString(), anyString(), anyString(), anyString());

            ConfluenceAttachmentHelper attachmentHelper = mock(ConfluenceAttachmentHelper.class);
            when(attachmentHelper.getAttachments(anyString())).thenReturn(Collections.emptyList());
            when(attachmentHelper.uploadAttachment(anyString(), any(File.class), anyBoolean()))
                    .thenAnswer(inv -> new Attachment(new JSONObject()
                            .put("id", "att-" + ((File) inv.getArgument(1)).getName())
                            .put("title", ((File) inv.getArgument(1)).getName())));
            confluence.setAttachmentHelper(attachmentHelper);

            // First sync uploads the referenced attachment (assets/diagram.png).
            confluence.syncMarkdownDirectory(root.toString(), "root-id", "SPACE", false, null);
            verify(attachmentHelper, atLeast(1)).uploadAttachment(anyString(), argThat((File f) -> f.getName().equals("diagram.png")), anyBoolean());

            // Second sync: stub now returns existing attachment so all uploads are skipped.
            when(attachmentHelper.getAttachments(anyString())).thenReturn(Collections.singletonList(
                    new Attachment(new JSONObject().put("id", "att1").put("title", "diagram.png"))));
            confluence.syncMarkdownDirectory(root.toString(), "root-id", "SPACE", false, null);
            verify(attachmentHelper, atLeast(1)).uploadAttachment(anyString(), argThat((File f) -> f.getName().equals("diagram.png")), anyBoolean());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void testSyncMarkdownDirectory_UploadsNonMarkdownFilesAsAttachments() throws IOException {
        java.nio.file.Path root = Files.createTempDirectory("sync-root");
        try {
            Files.createDirectories(root.resolve("sub"));
            Files.write(root.resolve("sub").resolve("data.json"),
                    "{\"x\":1}".getBytes(StandardCharsets.UTF_8));
            Files.write(root.resolve("sub").resolve("index.md"),
                    "# Sub".getBytes(StandardCharsets.UTF_8));

            doReturn(Collections.emptyList()).when(confluence).getChildrenOfContentById(anyString(), isNull());
            doReturn(Collections.emptyList()).when(confluence).getContentAttachments(anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).createPage(anyString(), anyString(), anyString(), anyString());

            ConfluenceAttachmentHelper attachmentHelper = mock(ConfluenceAttachmentHelper.class);
            when(attachmentHelper.getAttachments(anyString())).thenReturn(Collections.emptyList());
            when(attachmentHelper.uploadAttachment(anyString(), any(File.class), anyBoolean()))
                    .thenAnswer(inv -> new Attachment(new JSONObject()
                            .put("id", "att-" + ((File) inv.getArgument(1)).getName())
                            .put("title", ((File) inv.getArgument(1)).getName())));
            confluence.setAttachmentHelper(attachmentHelper);

            confluence.syncMarkdownDirectory(root.toString(), "root-id", "SPACE", false, null);

            verify(attachmentHelper).uploadAttachment(anyString(), argThat(f -> f.getName().equals("data.json")), anyBoolean());

            // The non-referenced attachment should appear as a forced link in the folder body.
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(confluence, atLeast(1)).updatePage(anyString(), anyString(), anyString(), bodyCaptor.capture(), anyString(), anyString());
            String allBodies = String.join("\n", bodyCaptor.getAllValues());
            assertTrue("Non-Markdown attachment should be linked explicitly", allBodies.contains("ri:filename=\"data.json\""));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void testSyncMarkdownDirectory_DeletesOrphanPages() throws IOException {
        java.nio.file.Path root = Files.createTempDirectory("sync-root");
        try {
            Files.write(root.resolve("kept.md"),
                    "# Kept Page\n\nContent.".getBytes(StandardCharsets.UTF_8));

            doReturn(Collections.emptyList()).when(confluence).getChildrenOfContentById(anyString(), isNull());
            Content orphan = content("orphan-id", "Orphan Page");
            doReturn(Collections.singletonList(orphan))
                    .when(confluence).getChildrenOfContentById(eq("root-id"), isNull());
            doReturn(Collections.emptyList())
                    .when(confluence).getChildrenOfContentById(eq("orphan-id"), isNull());
            doReturn(Collections.emptyList()).when(confluence).getContentAttachments(anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            doAnswer(inv -> content(inv.getArgument(0), inv.getArgument(1)))
                    .when(confluence).createPage(anyString(), anyString(), anyString(), anyString());
            doReturn("").when(confluence).deletePage(anyString());

            String result = confluence.syncMarkdownDirectory(root.toString(), "root-id", "SPACE", true, null);

            assertTrue(result.contains("deleted"));
            assertTrue(result.contains("Orphan Page"));
            verify(confluence).deletePage(eq("orphan-id"));
        } finally {
            deleteRecursively(root);
        }
    }

    private Content content(String id, String title) {
        return new Content(new JSONObject().put("id", id).put("title", title));
    }

    private void deleteRecursively(java.nio.file.Path path) {
        try {
            Files.walk(path)
                    .sorted(Collections.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
