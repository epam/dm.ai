// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.atlassian.confluence.model.SearchResult;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.networking.AbstractRestClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfluenceCoverageTest {

    private Confluence confluence;

    @Before
    public void setUp() throws IOException {
        confluence = Mockito.spy(new Confluence("http://example.com", "auth"));
    }

    private String buildPageJson(String id, String title, String storageValue) {
        return new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("body", new JSONObject()
                        .put("storage", new JSONObject()
                                .put("value", storageValue)
                                .put("representation", "storage")))
                .toString();
    }

    private String buildContentResultJson(String id, String title, String storageValue) {
        return new JSONObject()
                .put("results", new JSONArray()
                        .put(new JSONObject(buildPageJson(id, title, storageValue))))
                .toString();
    }

    // ------------------------------------------------------------------
    // contentByUrl / URL handling
    // ------------------------------------------------------------------

    @Test
    public void testContentByUrl_WikiShortLinkRedirect() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentById(anyString());

        try (MockedStatic<AbstractRestClient> mocked = mockStatic(AbstractRestClient.class)) {
            mocked.when(() -> AbstractRestClient.resolveRedirect(any(), eq("http://example.com/wiki/x/abc")))
                    .thenReturn("http://example.com/wiki/spaces/SP/pages/555/Page");

            Content result = confluence.contentByUrl("http://example.com/wiki/x/abc");

            assertNotNull(result);
            verify(confluence, times(1)).contentById("555");
        }
    }

    @Test
    public void testContentByUrl_LRedirect() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentById(anyString());

        try (MockedStatic<AbstractRestClient> mocked = mockStatic(AbstractRestClient.class)) {
            mocked.when(() -> AbstractRestClient.resolveRedirect(any(), eq("http://example.com/l/xyz")))
                    .thenReturn("http://example.com/wiki/spaces/SP/pages/777/Page");

            Content result = confluence.contentByUrl("http://example.com/l/xyz");

            assertNotNull(result);
            verify(confluence, times(1)).contentById("777");
        }
    }

    @Test
    public void testContentByUrl_DisplayUrl() throws IOException {
        doReturn(buildContentResultJson("42", "Page Name", "<p>Body</p>"))
                .when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.contentByUrl("http://example.com/wiki/display/~user/Page+Name");

        assertNotNull(result);
        assertEquals("42", result.getId());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContentByUrl_EmptyPath() throws IOException {
        confluence.contentByUrl("http://example.com");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContentByUrl_WikiWithoutKnownSegment() throws IOException {
        // /wiki/something-else -> handleWikiUrls fails on both base indices
        confluence.contentByUrl("http://example.com/wiki/other/page");
    }

    @Test
    public void testContentsByUrls_SkipsNullAndEmptyAndLogsErrors() throws IOException {
        doThrow(new IOException("boom")).when(confluence).contentByUrl(anyString());

        List<Content> result = confluence.contentsByUrls(new String[]{null, "", "http://example.com/x/1"}, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // searchContentByText
    // ------------------------------------------------------------------

    @Test
    public void testSearchContentByText_RestApiPath() throws IOException {
        doReturn("{\"results\": [{\"id\": \"1\", \"title\": \"Doc\"}, {\"id\": \"2\", \"title\": \"Doc2\"}]}")
                .when(confluence).execute(any(GenericRequest.class));

        List<SearchResult> results = confluence.searchContentByText("doc", 10);

        assertEquals(2, results.size());
    }

    @Test
    public void testSearchContentByText_GraphqlFailureFallsBackToRest() throws IOException {
        // A malformed GraphQL path makes the GraphQL client fail before any network call,
        // exercising the catch/fallback branch.
        confluence.setGraphQLPath(":::malformed-url");
        doReturn("{\"results\": [{\"id\": \"9\", \"title\": \"Fallback\"}]}")
                .when(confluence).execute(any(GenericRequest.class));

        List<SearchResult> results = confluence.searchContentByText("query", null);

        assertEquals(1, results.size());
    }

    // ------------------------------------------------------------------
    // profile / testConnection
    // ------------------------------------------------------------------

    @Test
    public void testProfile() throws IOException {
        doReturn("{\"displayName\": \"Jane\"}").when(confluence).execute(any(GenericRequest.class));

        assertEquals("{\"displayName\": \"Jane\"}", confluence.profile());
    }

    @Test
    public void testProfileByUserId() throws IOException {
        doReturn("{\"displayName\": \"John\"}").when(confluence).execute(any(GenericRequest.class));

        assertEquals("{\"displayName\": \"John\"}", confluence.profile("user-1"));
    }

    @Test
    public void testTestConnection_Success() throws IOException {
        doReturn("{\"displayName\": \"Jane Doe\", \"email\": \"jane@example.com\"}")
                .when(confluence).execute(any(GenericRequest.class));

        Map<String, Object> result = confluence.testConnection();

        assertEquals(true, result.get("success"));
        assertEquals("Jane Doe", result.get("user"));
        assertEquals("jane@example.com", result.get("email"));
    }

    @Test
    public void testTestConnection_Failure() throws IOException {
        doThrow(new IOException("connection refused")).when(confluence).execute(any(GenericRequest.class));

        Map<String, Object> result = confluence.testConnection();

        assertEquals(false, result.get("success"));
        assertTrue(((String) result.get("message")).contains("connection refused"));
        assertEquals("IOException", result.get("error"));
    }

    // ------------------------------------------------------------------
    // attachments / versions
    // ------------------------------------------------------------------

    @Test
    public void testGetContentAttachments() throws IOException {
        String json = new JSONObject()
                .put("results", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "att1")
                                .put("title", "a.png")
                                .put("_links", new JSONObject()
                                        .put("download", "/download/attachments/123/a.png"))))
                .toString();
        doReturn(json).when(confluence).execute(any(GenericRequest.class));

        List<Attachment> attachments = confluence.getContentAttachments("123");

        assertEquals(1, attachments.size());
        assertEquals("a.png", attachments.get(0).getTitle());
        assertEquals("/download/attachments/123/a.png", attachments.get(0).getDownloadLink());
    }

    @Test
    public void testGetContentVersions() throws IOException {
        doReturn("{\"results\": []}").when(confluence).execute(any(GenericRequest.class));

        ContentResult result = confluence.getContentVersions("123");

        assertNotNull(result);
        assertTrue(result.getContents().isEmpty());
    }

    // ------------------------------------------------------------------
    // findContent / createPage / updatePage
    // ------------------------------------------------------------------

    @Test
    public void testFindContent_EmptyResultsReturnsNull() throws IOException {
        doReturn("{\"results\": []}").when(confluence).execute(any(GenericRequest.class));

        assertNull(confluence.findContent("Missing", "SPACE"));
    }

    @Test
    public void testCreatePage() throws IOException {
        doReturn(buildPageJson("321", "New Page", "<p>body</p>"))
                .when(confluence).post(any(GenericRequest.class));

        Content created = confluence.createPage("New Page", "111", "<p>body</p>", "SPACE");

        assertNotNull(created);
        assertEquals("321", created.getId());
        assertEquals("New Page", created.getTitle());
        verify(confluence, times(1)).post(any(GenericRequest.class));
    }

    @Test
    public void testUpdatePage_SimpleDelegatesToHistoryVariant() throws IOException {
        doReturn("{\"id\": \"123\", \"version\": {\"number\": 2}}")
                .when(confluence).execute(any(GenericRequest.class));
        doReturn(buildPageJson("123", "Updated", "<p>new</p>"))
                .when(confluence).put(any(GenericRequest.class));

        Content updated = confluence.updatePage("123", "Updated", "111", "<p>new</p>", "SPACE");

        assertNotNull(updated);
        assertEquals("Updated", updated.getTitle());
        verify(confluence, times(1)).put(any(GenericRequest.class));
    }

    @Test
    public void testUpdatePage_WithHistoryComment() throws IOException {
        doReturn("{\"id\": \"123\", \"version\": {\"number\": 5}}")
                .when(confluence).execute(any(GenericRequest.class));
        doReturn(buildPageJson("123", "Updated", "<p>new</p>"))
                .when(confluence).put(any(GenericRequest.class));

        Content updated = confluence.updatePage("123", "Updated", "111", "<p>new</p>", "SPACE", "my comment");

        assertEquals("123", updated.getId());
        verify(confluence, times(1)).put(any(GenericRequest.class));
    }

    @Test
    public void testPrepareBodyForConfluence() {
        String result = Confluence.prepareBodyForConfluence("line1<br>line2<br/>line3");
        assertEquals("line1\nline2\nline3", result);
    }

    @Test
    public void testMacroHelpers() {
        String html = Confluence.macroHTML("<b>x</b>");
        assertTrue(html.contains("ac:name=\"html\""));
        assertTrue(html.contains("<![CDATA[<b>x</b>]]>"));

        String cloud = Confluence.macroCloudHTML("<b>x</b>");
        assertTrue(cloud.contains("swc-macro-html-input"));
        assertTrue(cloud.contains("<body><b>x</b></body>"));

        String image = Confluence.macroBase64Image("aGVsbG8=");
        assertTrue(image.contains("data:image/png;base64,aGVsbG8="));
    }

    @Test
    public void testAttachFileToPage_SkipsWhenAttachmentAlreadyExists() throws IOException {
        File tempFile = File.createTempFile("existing", ".png");
        tempFile.deleteOnExit();
        String json = new JSONObject()
                .put("results", new JSONArray()
                        .put(new JSONObject().put("id", "att1").put("title", tempFile.getName().toUpperCase())))
                .toString();
        doReturn(json).when(confluence).execute(any(GenericRequest.class));

        // Must return early (case-insensitive title match) without performing any upload.
        confluence.attachFileToPage("123", tempFile);

        verify(confluence, times(1)).execute(any(GenericRequest.class));
    }

    @Test
    public void testInsertImageInPageBody() throws IOException {
        String pageJson = new JSONObject()
                .put("id", "123")
                .put("title", "Page")
                .put("version", new JSONObject().put("number", 1))
                .put("ancestors", new JSONArray().put(new JSONObject().put("id", "777")))
                .put("body", new JSONObject()
                        .put("storage", new JSONObject()
                                .put("value", "<p>old</p>")
                                .put("representation", "storage")))
                .toString();
        doReturn(pageJson).when(confluence).execute(any(GenericRequest.class));
        doReturn(pageJson).when(confluence).put(any(GenericRequest.class));

        confluence.insertImageInPageBody("SPACE", "123", "img.png");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(confluence, times(1)).put(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("ri:attachment ri:filename=\\\"img.png\\\""));
        assertTrue(body.contains("<p>old<\\/p>"));
    }

    // ------------------------------------------------------------------
    // children
    // ------------------------------------------------------------------

    @Test
    public void testGetChildrenOfContentByName() throws IOException {
        doReturn(buildContentResultJson("123", "Parent", "<p>p</p>"))
                .when(confluence).execute(any(GenericRequest.class));

        List<Content> children = confluence.getChildrenOfContentByName("SPACE", "Parent");

        assertEquals(1, children.size());
        assertEquals("123", children.get(0).getId());
    }

    @Test
    public void testGetChildrenOfContentById_NullFormat() throws IOException {
        doReturn(buildContentResultJson("456", "Child", "<p>c</p>"))
                .when(confluence).execute(any(GenericRequest.class));

        List<Content> children = confluence.getChildrenOfContentById("123");

        assertEquals(1, children.size());
        assertEquals("456", children.get(0).getId());
        assertEquals("<p>c</p>", children.get(0).getStorage().getValue());
    }

    // ------------------------------------------------------------------
    // downloadAttachment / downloadPageAttachments / downloadPages
    // ------------------------------------------------------------------

    @Test
    public void testDownloadAttachment_WithIdAndContainerBuildsRestUrl() throws IOException {
        Attachment attachment = new Attachment(new JSONObject()
                .put("id", "att55")
                .put("title", "photo.png")
                .put("_links", new JSONObject()
                        .put("download", "/download/attachments/999/photo.png")));
        File tempDir = Files.createTempDirectory("dl1").toFile();

        try (MockedStatic<RestClient.Impl> mocked = mockStatic(RestClient.Impl.class)) {
            mocked.when(() -> RestClient.Impl.downloadFile(any(), any(), any()))
                    .thenAnswer(inv -> inv.getArgument(2));

            File result = confluence.downloadAttachment(attachment, tempDir);

            assertNotNull(result);
            assertEquals("photo.png", result.getName());
            mocked.verify(() -> RestClient.Impl.downloadFile(
                    any(),
                    argThat(req -> req.url().equals(
                            "http://example.com/rest/api/content/999/child/attachment/att55/download")),
                    any()));
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadAttachment_FallbackUrlAndExtensionFromMediaType() throws IOException {
        Attachment attachment = new Attachment(new JSONObject()
                .put("title", "report")
                .put("metadata", new JSONObject().put("mediaType", "application/pdf"))
                .put("_links", new JSONObject().put("download", "/files/report")));
        File tempDir = Files.createTempDirectory("dl2").toFile();

        try (MockedStatic<RestClient.Impl> mocked = mockStatic(RestClient.Impl.class)) {
            mocked.when(() -> RestClient.Impl.downloadFile(any(), any(), any()))
                    .thenAnswer(inv -> inv.getArgument(2));

            File result = confluence.downloadAttachment(attachment, tempDir);

            assertNotNull(result);
            assertEquals("report.pdf", result.getName());
            mocked.verify(() -> RestClient.Impl.downloadFile(
                    any(),
                    argThat(req -> req.url().equals("http://example.com/files/report")),
                    any()));
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadAttachment_MediaTypeSubtypeExtraction() throws IOException {
        Attachment attachment = new Attachment(new JSONObject()
                .put("title", "picture")
                .put("metadata", new JSONObject().put("mediaType", "image/x-png"))
                .put("_links", new JSONObject().put("download", "files/picture")));
        File tempDir = Files.createTempDirectory("dl3").toFile();

        try (MockedStatic<RestClient.Impl> mocked = mockStatic(RestClient.Impl.class)) {
            mocked.when(() -> RestClient.Impl.downloadFile(any(), any(), any()))
                    .thenAnswer(inv -> inv.getArgument(2));

            File result = confluence.downloadAttachment(attachment, tempDir);

            assertNotNull(result);
            assertEquals("picture.png", result.getName());
            mocked.verify(() -> RestClient.Impl.downloadFile(
                    any(),
                    argThat(req -> req.url().equals("http://example.com/files/picture")),
                    any()));
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadAttachment_UnknownMediaTypeKeepsName() throws IOException {
        Attachment attachment = new Attachment(new JSONObject()
                .put("title", "blob")
                .put("metadata", new JSONObject().put("mediaType", "application/octet-stream"))
                .put("_links", new JSONObject().put("download", "/f/blob")));
        File tempDir = Files.createTempDirectory("dl4").toFile();

        try (MockedStatic<RestClient.Impl> mocked = mockStatic(RestClient.Impl.class)) {
            mocked.when(() -> RestClient.Impl.downloadFile(any(), any(), any()))
                    .thenAnswer(inv -> inv.getArgument(2));

            File result = confluence.downloadAttachment(attachment, tempDir);

            assertNotNull(result);
            assertEquals("blob", result.getName());
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadPageAttachments_MixedOutcomes() throws IOException {
        File tempDir = Files.createTempDirectory("dlp").toFile();
        File downloaded = new File(tempDir, "ok.png");
        Files.write(downloaded.toPath(), new byte[]{1, 2, 3});

        String attachmentsJson = new JSONObject()
                .put("results", new JSONArray()
                        .put(new JSONObject()
                                .put("id", "att1")
                                .put("title", "ok.png")
                                .put("_links", new JSONObject()
                                        .put("download", "/download/attachments/1/ok.png")))
                        .put(new JSONObject()
                                .put("id", "att2")
                                .put("title", "nolink.png"))
                        .put(new JSONObject()
                                .put("id", "att3")
                                .put("title", "fail.png")
                                .put("_links", new JSONObject()
                                        .put("download", "/download/attachments/1/fail.png"))))
                .toString();
        doReturn(attachmentsJson).when(confluence).execute(any(GenericRequest.class));
        doReturn(downloaded).when(confluence).downloadAttachment(
                argThat(a -> "ok.png".equals(a.getTitle())), any(File.class));
        doThrow(new IOException("disk full")).when(confluence).downloadAttachment(
                argThat(a -> "fail.png".equals(a.getTitle())), any(File.class));

        try {
            List<File> result = confluence.downloadPageAttachments("1", tempDir);

            assertEquals(1, result.size());
            assertEquals(downloaded, result.get(0));
        } finally {
            downloaded.delete();
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadPageAttachments_EmptyAttachments() throws IOException {
        doReturn("{\"results\": []}").when(confluence).execute(any(GenericRequest.class));
        File tempDir = Files.createTempDirectory("dle").toFile();
        try {
            List<File> result = confluence.downloadPageAttachments("1", tempDir);
            assertTrue(result.isEmpty());
        } finally {
            tempDir.delete();
        }
    }

    @Test
    public void testDownloadPages_EmptySeeds() throws IOException {
        File tempDir = Files.createTempDirectory("pages").toFile();
        try {
            String result = confluence.downloadPages(new String[]{}, tempDir.getAbsolutePath(), null, null);
            assertTrue(result.contains("Downloaded 0 Confluence page(s)"));
        } finally {
            tempDir.delete();
        }
    }

    // ------------------------------------------------------------------
    // default-space helpers
    // ------------------------------------------------------------------

    @Test
    public void testFindContentInDefaultSpace() throws IOException {
        Confluence withSpace = Mockito.spy(new Confluence("http://example.com", "auth", null, "DEF"));
        doReturn(buildContentResultJson("77", "T", "<p>x</p>"))
                .when(withSpace).execute(any(GenericRequest.class));

        Content result = withSpace.findContentInDefaultSpace("T", null);

        assertNotNull(result);
        assertEquals("77", result.getId());
    }

    @Test(expected = IllegalStateException.class)
    public void testFindContentInDefaultSpace_NoDefaultSpace() throws IOException {
        confluence.findContentInDefaultSpace("T", null);
    }

    @Test
    public void testFindOrCreate_Found() throws IOException {
        Confluence withSpace = Mockito.spy(new Confluence("http://example.com", "auth", null, "DEF"));
        doReturn(buildContentResultJson("88", "Existing", "<p>x</p>"))
                .when(withSpace).execute(any(GenericRequest.class));

        Content result = withSpace.findOrCreate("Existing", "1", "<p>b</p>");

        assertNotNull(result);
        assertEquals("88", result.getId());
        verify(withSpace, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testFindOrCreate_CreatesWhenMissing() throws IOException {
        Confluence withSpace = Mockito.spy(new Confluence("http://example.com", "auth", null, "DEF"));
        doReturn("{\"results\": []}").when(withSpace).execute(any(GenericRequest.class));
        doReturn(buildPageJson("99", "Created", "<p>b</p>"))
                .when(withSpace).post(any(GenericRequest.class));

        Content result = withSpace.findOrCreate("Created", "1", "<p>b</p>");

        assertNotNull(result);
        assertEquals("99", result.getId());
        verify(withSpace, times(1)).post(any(GenericRequest.class));
    }

    @Test(expected = IllegalStateException.class)
    public void testFindOrCreate_NoDefaultSpace() throws IOException {
        confluence.findOrCreate("T", "1", "b");
    }

    @Test
    public void testContentByTitleInDefaultSpace() throws IOException {
        Confluence withSpace = Mockito.spy(new Confluence("http://example.com", "auth", null, "DEF"));
        doReturn(buildContentResultJson("5", "T", "<p>x</p>"))
                .when(withSpace).execute(any(GenericRequest.class));

        ContentResult result = withSpace.contentByTitleInDefaultSpace("T", null);

        assertEquals(1, result.getContents().size());
    }

    @Test(expected = IllegalStateException.class)
    public void testContentByTitleInDefaultSpace_NoDefaultSpace() throws IOException {
        confluence.contentByTitleInDefaultSpace("T", null);
    }

    // ------------------------------------------------------------------
    // UriToObject / parseUris
    // ------------------------------------------------------------------

    @Test
    public void testParseUris() throws Exception {
        Set<String> uris = confluence.parseUris(
                "See http://example.com/wiki/spaces/SP/pages/123/Page and http://other.com/wiki/x");

        assertEquals(1, uris.size());
        assertTrue(uris.contains("http://example.com/wiki/spaces/SP/pages/123/Page"));
    }

    @Test
    public void testUriToObject_ReturnsCleanedStorageValue() throws Exception {
        Content content = new Content(buildPageJson("1", "T", "<p style=\"color:red\">Hello</p>"));
        doReturn(content).when(confluence).contentByUrl(anyString());

        Object result = confluence.uriToObject("http://example.com/wiki/spaces/SP/pages/1/Page");

        assertNotNull(result);
        assertTrue(result.toString().contains("Hello"));
    }

    // ------------------------------------------------------------------
    // applyFormat branches via contentById
    // ------------------------------------------------------------------

    @Test
    public void testContentById_MarkdownWithExportView() throws IOException {
        String json = new JSONObject()
                .put("id", "123")
                .put("title", "T")
                .put("body", new JSONObject()
                        .put("storage", new JSONObject()
                                .put("value", "<p>Hello</p>")
                                .put("representation", "storage"))
                        .put("export_view", new JSONObject()
                                .put("value", "<p>Hello rendered</p>")))
                .toString();
        doReturn(json).when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.contentById("123", "md");

        assertNotNull(result);
        assertNotNull(result.getStorage().getValue());
        assertEquals("markdown", result.getJSONObject()
                .getJSONObject("body").getJSONObject("storage").getString("representation"));
        assertFalse(result.getJSONObject().getJSONObject("body").has("export_view"));
    }

    @Test
    public void testContentById_MarkdownWithBlankStorageKeepsContent() throws IOException {
        doReturn(buildPageJson("123", "T", "")).when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.contentById("123", "markdown");

        assertNotNull(result);
        assertEquals("storage", result.getJSONObject()
                .getJSONObject("body").getJSONObject("storage").getString("representation"));
    }

    @Test
    public void testContentById_ParseErrorRethrows() throws IOException {
        doReturn("not a json").when(confluence).execute(any(GenericRequest.class));

        try {
            confluence.contentById("123");
            fail("Expected exception");
        } catch (Exception expected) {
            // logged and rethrown
        }
    }

    @Test
    public void testBackwardCompatibleWrappers() throws IOException {
        doReturn(buildContentResultJson("7", "T", "<p>x</p>"))
                .when(confluence).execute(any(GenericRequest.class));

        assertEquals(1, confluence.content("T", "SPACE").getContents().size());
        assertNotNull(confluence.findContent("T", "SPACE"));
        assertEquals(1, confluence.getChildrenOfContentById("7").size());
        assertEquals(1, confluence.getChildrenOfContentByName("SPACE", "T").size());
    }
}
