// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;

import java.io.IOException;
import java.util.List;

/**
 * Adapter that exposes a {@link Confluence} instance through the
 * {@link MarkdownConfluenceSync.PageOperations} interface.
 */
public class ConfluencePageOperations implements MarkdownConfluenceSync.PageOperations {

    private final Confluence confluence;

    public ConfluencePageOperations(Confluence confluence) {
        this.confluence = confluence;
    }

    @Override
    public Content createPage(String title, String parentId, String body, String space) throws IOException {
        return confluence.createPage(title, parentId, body, space);
    }

    @Override
    public Content updatePage(String contentId, String title, String parentId, String body,
                              String space, String historyComment) throws IOException {
        return confluence.updatePage(contentId, title, parentId, body, space, historyComment);
    }

    @Override
    public List<Content> getChildren(String contentId) throws IOException {
        return confluence.getChildrenOfContentById(contentId, null);
    }

    @Override
    public String deletePage(String contentId) throws IOException {
        return confluence.deletePage(contentId);
    }
}
