// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ConfluencePageDownloaderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDownloadPagesWritesInlineComments() throws Exception {
        Confluence confluence = Mockito.spy(new Confluence("https://example.com/wiki", "auth"));

        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn("<p>Page body</p>");

        Content page = mock(Content.class);
        when(page.getId()).thenReturn("123");
        when(page.getTitle()).thenReturn("Test Page");
        when(page.getStorage()).thenReturn(storage);

        doReturn(page).when(confluence).contentByUrl(anyString());
        doReturn("{\"results\":[{\"id\":\"456\",\"resolutionStatus\":\"open\",\"body\":{\"storage\":{\"value\":\"<p>Comment body</p>\"}},\"properties\":{\"inlineOriginalSelection\":\"selected text\"}}]}").when(confluence).getPageInlineComments("123", 100);
        doReturn(Collections.emptyList()).when(confluence).downloadPageAttachments(anyString(), any(File.class));

        ConfluencePageDownloader downloader = new ConfluencePageDownloader(confluence);
        File outputDir = tempFolder.newFolder();
        int written = downloader.downloadPages(
                Collections.singletonList("https://example.com/wiki/spaces/SPACE/pages/123/Test"),
                outputDir, 0, false, true);

        assertEquals(1, written);

        File pageFile = new File(outputDir, "Test_Page/Test_Page.md");
        assertTrue(pageFile.exists());

        File commentsFile = new File(outputDir, "Test_Page/comments.md");
        assertTrue(commentsFile.exists());
        String comments = FileUtils.readFileToString(commentsFile, StandardCharsets.UTF_8);
        assertTrue(comments.contains("Comment 456"));
        assertTrue(comments.contains("selected text"));
        assertTrue(comments.contains("Comment body"));
    }

    @Test
    public void testDownloadPagesSkipsCommentsWhenDisabled() throws Exception {
        Confluence confluence = Mockito.spy(new Confluence("https://example.com/wiki", "auth"));

        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn("<p>Page body</p>");

        Content page = mock(Content.class);
        when(page.getId()).thenReturn("123");
        when(page.getTitle()).thenReturn("Test Page");
        when(page.getStorage()).thenReturn(storage);

        doReturn(page).when(confluence).contentByUrl(anyString());
        doReturn(Collections.emptyList()).when(confluence).downloadPageAttachments(anyString(), any(File.class));

        ConfluencePageDownloader downloader = new ConfluencePageDownloader(confluence);
        File outputDir = tempFolder.newFolder();
        int written = downloader.downloadPages(
                Collections.singletonList("https://example.com/wiki/spaces/SPACE/pages/123/Test"),
                outputDir, 0, false, false);

        assertEquals(1, written);
        assertTrue(new File(outputDir, "Test_Page/Test_Page.md").exists());
        verify(confluence, never()).getPageInlineComments(anyString(), anyInt());
    }

    @Test
    public void internalLinksWithoutSpaceKeyAreResolvedInThePageSpaceFirst() throws Exception {
        Confluence confluence = Mockito.spy(new Confluence("https://example.com/wiki", "auth"));

        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn(
                "<p><ac:link><ri:page ri:content-title=\"Related\" /></ac:link></p>");
        Content page = mock(Content.class);
        when(page.getId()).thenReturn("1");
        when(page.getTitle()).thenReturn("Main");
        when(page.getStorage()).thenReturn(storage);
        when(page.getSpaceKey()).thenReturn("DOCS");

        Storage relatedStorage = mock(Storage.class);
        when(relatedStorage.getValue()).thenReturn("<p>Related body</p>");
        Content related = mock(Content.class);
        when(related.getId()).thenReturn("2");
        when(related.getTitle()).thenReturn("Related");
        when(related.getStorage()).thenReturn(relatedStorage);

        doReturn(page).when(confluence).contentByUrl(anyString());
        doReturn(related).when(confluence).findContent("Related", "DOCS");
        doReturn(Collections.emptyList()).when(confluence).downloadPageAttachments(anyString(), any(File.class));

        File outputDir = tempFolder.newFolder();
        int written = new ConfluencePageDownloader(confluence).downloadPages(
                Collections.singletonList("https://example.com/wiki/spaces/DOCS/pages/1/Main"), outputDir, 1, false);

        assertEquals(2, written);
        assertTrue(new File(outputDir, "Related/Related.md").exists());
        verify(confluence, never()).findContent("Related");
    }

    @Test
    public void internalLinkFallsBackToDefaultLookupWhenNotFoundInPageSpace() throws Exception {
        Confluence confluence = Mockito.spy(new Confluence("https://example.com/wiki", "auth"));

        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn(
                "<p><ac:link><ri:page ri:content-title=\"Related\" /></ac:link></p>");
        Content page = mock(Content.class);
        when(page.getId()).thenReturn("1");
        when(page.getTitle()).thenReturn("Main");
        when(page.getStorage()).thenReturn(storage);
        when(page.getSpaceKey()).thenReturn("DOCS");

        Storage relatedStorage = mock(Storage.class);
        when(relatedStorage.getValue()).thenReturn("<p>Related body</p>");
        Content related = mock(Content.class);
        when(related.getId()).thenReturn("2");
        when(related.getTitle()).thenReturn("Related");
        when(related.getStorage()).thenReturn(relatedStorage);

        doReturn(page).when(confluence).contentByUrl(anyString());
        doReturn(null).when(confluence).findContent("Related", "DOCS");
        doReturn(related).when(confluence).findContent("Related");
        doReturn(Collections.emptyList()).when(confluence).downloadPageAttachments(anyString(), any(File.class));

        int written = new ConfluencePageDownloader(confluence).downloadPages(
                Collections.singletonList("https://example.com/wiki/spaces/DOCS/pages/1/Main"),
                tempFolder.newFolder(), 1, false);

        assertEquals(2, written);
    }

    @Test
    public void downloadedMarkdownContainsInlinedExcerptContent() throws Exception {
        Confluence confluence = Mockito.spy(new Confluence("https://example.com/wiki", "auth"));

        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn(
                "<p>Table from: Source</p><ac:structured-macro ac:name=\"table-excerpt-include\">"
                        + "<ac:parameter ac:name=\"page\"><ac:link><ri:page ri:content-title=\"Source\" /></ac:link></ac:parameter>"
                        + "<ac:parameter ac:name=\"name\">Rules</ac:parameter></ac:structured-macro>");
        Content page = mock(Content.class);
        when(page.getId()).thenReturn("1");
        when(page.getTitle()).thenReturn("Main");
        when(page.getStorage()).thenReturn(storage);
        when(page.getSpaceKey()).thenReturn("DOCS");

        Storage sourceStorage = mock(Storage.class);
        when(sourceStorage.getValue()).thenReturn(
                "<ac:structured-macro ac:name=\"table-excerpt\"><ac:parameter ac:name=\"name\">Rules</ac:parameter>"
                        + "<ac:rich-text-body><table><tbody><tr><th>Key</th></tr><tr><td>included-value</td></tr></tbody></table>"
                        + "</ac:rich-text-body></ac:structured-macro>");
        Content source = mock(Content.class);
        when(source.getId()).thenReturn("2");
        when(source.getSpaceKey()).thenReturn("DOCS");
        when(source.getStorage()).thenReturn(sourceStorage);

        doReturn(page).when(confluence).contentByUrl(anyString());
        doReturn(source).when(confluence).findContent("Source", "DOCS");

        File outputDir = tempFolder.newFolder();
        new ConfluencePageDownloader(confluence).downloadPages(
                Collections.singletonList("https://example.com/wiki/spaces/DOCS/pages/1/Main"), outputDir, 0, false);

        String markdown = FileUtils.readFileToString(new File(outputDir, "Main/Main.md"), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("included-value"));
    }
}
