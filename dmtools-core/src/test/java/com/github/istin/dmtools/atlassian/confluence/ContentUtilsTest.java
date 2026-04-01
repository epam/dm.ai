// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ContentUtilsTest {

    @Mock
    BasicConfluence basicConfluence;

    @Mock
    ContentUtils.UrlToImageFile urlToImageFile;

    @Mock
    Content content;

    @Mock
    Storage storage;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testConvertLinksToImages() throws Exception {
        // Arrange
        String html = "<a href=\"https://example.com/image.png\">Image</a>";
        when(content.getStorage()).thenReturn(storage);
        when(content.getId()).thenReturn("some id");
        when(storage.getValue()).thenReturn(html);
        when(urlToImageFile.isValidImageUrl(anyString())).thenReturn(true);
        when(urlToImageFile.convertUrlToFile(anyString())).thenReturn(new File("image.png"));

        // Act
        String result = ContentUtils.convertLinksToImages(basicConfluence, content, urlToImageFile);

        // Assert
        verify(basicConfluence, times(1)).attachFileToPage(anyString(), any(File.class));
        assertEquals("<a href=\"https://example.com/image.png\">Image</a>\n" +
                "<p><ac:image ac:height=\"250\">\n" +
                "  <ri:attachment ri:filename=\"image.png\" />\n" +
                " </ac:image></p>", result);

        when(storage.getValue()).thenReturn(result);
        result = ContentUtils.convertLinksToImages(basicConfluence, content, urlToImageFile);
        assertEquals("<a href=\"https://example.com/image.png\">Image</a>\n" +
                "<p><ac:image ac:height=\"250\">\n" +
                "  <ri:attachment ri:filename=\"image.png\" />\n" +
                " </ac:image></p>", result);
    }
}