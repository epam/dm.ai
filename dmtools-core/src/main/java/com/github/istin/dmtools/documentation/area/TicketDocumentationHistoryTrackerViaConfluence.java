// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation.area;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.TicketLink;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import org.json.JSONArray;

import java.io.IOException;

public class TicketDocumentationHistoryTrackerViaConfluence implements ITicketDocumentationHistoryTracker {

    private BasicConfluence confluence;

    public TicketDocumentationHistoryTrackerViaConfluence(BasicConfluence confluence) {
        this.confluence = confluence;
    }

    @Override
    public boolean isTicketWasAddedToPage(TicketLink ticketLink, String pageName) throws IOException {
        Content content = confluence.findContent(pageName);
        if (content == null) {
            return false;
        }
        Content pageHistoryContent = confluence.findOrCreate(content.getTitle() + " History", content.getId(), "[]");
        return pageHistoryContent.getStorage().getValue().contains(ticketLink.getTicketLink());
    }

    @Override
    public void addTicketToPageHistory(TicketLink ticketLink, String pageName) throws IOException {
        Content content = confluence.findContent(pageName);
        if (content == null) {
            throw new IllegalStateException("something went wrong, page must be created created first");
        }
        Content pageHistoryContent = confluence.findOrCreate(content.getTitle() + " History", content.getId(), "[]");
        String value = HtmlCleaner.cleanAllHtmlTags("", pageHistoryContent.getStorage().getValue());
        JSONArray array = new JSONArray(value.replaceAll("&quot;", "\""));
        array.put(ticketLink.getTicketLink());
        confluence.updatePage(pageHistoryContent, array.toString());
    }
}
