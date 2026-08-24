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
}
