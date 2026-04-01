// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class IAttachmentTest {

    @Test
    public void testGetName() {
        IAttachment attachment = mock(IAttachment.class);
        when(attachment.getName()).thenReturn("exampleName");

        String name = attachment.getName();

        assertEquals("exampleName", name);
        verify(attachment).getName();
    }

    @Test
    public void testGetUrl() {
        IAttachment attachment = mock(IAttachment.class);
        when(attachment.getUrl()).thenReturn("http://example.com");

        String url = attachment.getUrl();

        assertEquals("http://example.com", url);
        verify(attachment).getUrl();
    }

    @Test
    public void testGetContentType() {
        IAttachment attachment = mock(IAttachment.class);
        when(attachment.getContentType()).thenReturn("application/pdf");

        String contentType = attachment.getContentType();

        assertEquals("application/pdf", contentType);
        verify(attachment).getContentType();
    }
}