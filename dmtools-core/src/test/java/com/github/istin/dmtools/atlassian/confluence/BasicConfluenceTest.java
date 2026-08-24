// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.report.freemarker.GenericReport;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BasicConfluenceTest {

    private BasicConfluence basicConfluence;
    private static final String BASE_PATH = "basePath";
    private static final String LOGIN_PASS_TOKEN = "loginPassToken";
    private static final String DEFAULT_SPACE = "defaultSpace";

    @Before
    public void setUp() throws IOException {
        basicConfluence = Mockito.spy(new BasicConfluence(BASE_PATH, LOGIN_PASS_TOKEN, DEFAULT_SPACE));
    }

    @Test
    public void testGetInstance() throws IOException {
        BasicConfluence instance = BasicConfluence.getInstance();
        assertNotNull(instance);
    }

    @Test
    public void testFindOrCreate() throws IOException {
        String title = "Test Title";
        String parentId = "ParentId";
        String body = "Body Content";

        Content mockContent = mock(Content.class);
        doReturn(null).when(basicConfluence).findContent(title, DEFAULT_SPACE);
        doReturn(mockContent).when(basicConfluence).createPage(title, parentId, body, DEFAULT_SPACE);

        Content content = basicConfluence.findOrCreate(title, parentId, body);
        assertNotNull(content);
    }

    @Test
    public void testUpdatePageWithParameters() throws IOException {
        String contentId = "ContentId";
        String title = "Title";
        String parentId = "ParentId";
        String body = "Body Content";

        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(basicConfluence).updatePage(contentId, title, parentId, body, DEFAULT_SPACE);

        Content updatedContent = basicConfluence.updatePage(contentId, title, parentId, body);
        assertNotNull(updatedContent);
    }

    @Test
    public void testGetDefaultSpace() {
        assertEquals(DEFAULT_SPACE, basicConfluence.getDefaultSpace());
    }

    @Test
    public void testFindContent() throws IOException {
        String title = "Test Title";

        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(basicConfluence).findContent(title, DEFAULT_SPACE);

        Content content = basicConfluence.findContent(title);
        assertNotNull(content);
    }

    @Test
    public void testContent() throws IOException {
        String title = "Test Title";

        ContentResult mockContentResult = mock(ContentResult.class);
        doReturn(mockContentResult).when(basicConfluence).content(title, DEFAULT_SPACE);

        ContentResult contentResult = basicConfluence.content(title);
        assertNotNull(contentResult);
    }

    @Test
    public void testContentAttachments() throws IOException {
        String contentId = "ContentId";

        List<Attachment> mockAttachments = mock(List.class);
        doReturn(mockAttachments).when(basicConfluence).getContentAttachments(contentId);

        List<Attachment> attachments = basicConfluence.contentAttachments(contentId);
        assertNotNull(attachments);
    }



    @Test
    public void testGetChildrenOfContentByName() throws IOException {
        String contentName = "ContentName";

        List<Content> mockContentList = mock(List.class);
        doReturn(mockContentList).when(basicConfluence).getChildrenOfContentByName(DEFAULT_SPACE, contentName);

        List<Content> contentList = basicConfluence.getChildrenOfContentByName(contentName);
        assertNotNull(contentList);
    }

    @Test
    public void testGetPageInlineComments() throws IOException {
        String pageId = "12345";
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            String url = request.url();
            assertTrue(url.contains("/api/v2/pages/" + pageId + "/inline-comments"));
            assertTrue(url.contains("body-format=storage"));
            assertTrue(url.contains("limit=10"));
            return "{\"results\":[{\"id\":\"1\"}]}";
        }).when(basicConfluence).execute(any(GenericRequest.class));

        String result = basicConfluence.getPageInlineComments(pageId, 10);
        assertTrue(result.contains("\"id\":\"1\""));
    }

    @Test
    public void testReplyToInlineComment() throws IOException {
        String pageId = "12345";
        String commentId = "67890";
        String body = "Hello\nWorld";

        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            assertEquals(BASE_PATH + "/api/v2/inline-comments", request.url());
            JSONObject json = new JSONObject(request.getBody());
            assertEquals(commentId, json.getString("parentCommentId"));
            JSONObject bodyJson = json.getJSONObject("body");
            assertEquals("storage", bodyJson.getString("representation"));
            assertEquals("<p>Hello</p>\n<p>World</p>", bodyJson.getString("value"));
            return "{\"id\":\"111\"}";
        }).when(basicConfluence).post(any(GenericRequest.class));

        String result = basicConfluence.replyToInlineComment(pageId, commentId, body);
        assertTrue(result.contains("\"id\":\"111\""));
    }
}