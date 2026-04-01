// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation.area;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.common.model.TicketLink;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TicketDocumentationHistoryTrackerViaConfluenceTest {

    private BasicConfluence confluenceMock;
    private TicketDocumentationHistoryTrackerViaConfluence tracker;
    private TicketLink ticketLinkMock;

    @Before
    public void setUp() {
        confluenceMock = mock(BasicConfluence.class);
        tracker = new TicketDocumentationHistoryTrackerViaConfluence(confluenceMock);
        ticketLinkMock = mock(TicketLink.class);
    }

    @Test(expected = IllegalStateException.class)
    public void testAddTicketToPageHistory_PageNotFound() throws IOException {
        when(confluenceMock.findContent("TestPage")).thenReturn(null);

        tracker.addTicketToPageHistory(ticketLinkMock, "TestPage");
    }

}