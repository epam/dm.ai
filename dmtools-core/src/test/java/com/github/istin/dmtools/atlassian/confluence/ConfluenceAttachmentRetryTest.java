// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ConfluenceAttachmentRetryTest {

    @Test
    public void rateLimitServerErrorsAndNetworkFailuresAreRetried() {
        assertTrue(Confluence.isTransientDownloadFailure(new IOException("429 Too Many Requests")));
        assertTrue(Confluence.isTransientDownloadFailure(new IOException("503 body")));
        assertTrue(Confluence.isTransientDownloadFailure(new IOException("408 timeout")));
        assertTrue(Confluence.isTransientDownloadFailure(new IOException("unexpected end of stream")));
        assertTrue(Confluence.isTransientDownloadFailure(new IOException((String) null)));
    }

    @Test
    public void clientErrorsAreNotRetried() {
        assertFalse(Confluence.isTransientDownloadFailure(new IOException("401 unauthorized")));
        assertFalse(Confluence.isTransientDownloadFailure(new IOException("404 not found")));
        assertFalse(Confluence.isTransientDownloadFailure(new IOException("403 forbidden")));
    }
}
