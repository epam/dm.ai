// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import com.github.istin.dmtools.common.utils.MarkdownToJiraConverter;
import com.github.istin.dmtools.context.ContextOrchestrator;

import java.io.IOException;
import java.util.List;

public class ConfluencePagesContext implements ToText {

    private StringBuffer stringBuffer = new StringBuffer();

    public ConfluencePagesContext(String[] confluencePages, Confluence confluence, boolean transformConfluencePagesToMarkdown) throws Exception {
        this(null, confluencePages, confluence, transformConfluencePagesToMarkdown);
    }

    public ConfluencePagesContext(ContextOrchestrator contextOrchestrator, String[] confluencePages, Confluence confluence, boolean transformConfluencePagesToMarkdown) throws Exception {
        if (confluencePages != null) {
            List<Content> contents = confluence.contentsByUrls(confluencePages);
            stringBuffer.append("Confluence pages\n");
            for (Content content : contents) {
                String pageContent = content.getTitle() + "\n" + content.getStorage().getValue();
                pageContent = transformConfluencePagesToMarkdown ? MarkdownToJiraConverter.convertToJiraMarkdown(HtmlCleaner.cleanOnlyStylesAndSizes(pageContent)) : HtmlCleaner.cleanOnlyStylesAndSizes(pageContent);
                stringBuffer.append(pageContent).append("\n");
                if (contextOrchestrator != null) {
                    contextOrchestrator.processFullContent(content.getViewUrl(confluence.getBasePath()), pageContent, confluence, null, 0);
                }
            }
        }
    }

    @Override
    public String toText() throws IOException {
        return stringBuffer.toString();
    }
}
