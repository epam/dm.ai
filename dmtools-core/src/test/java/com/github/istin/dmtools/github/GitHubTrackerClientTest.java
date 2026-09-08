// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.github.model.GitHubTicket;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GitHubTrackerClient}. HTTP is mocked via a spy; no
 * external API calls are made.
 */
public class GitHubTrackerClientTest {

    private GitHubTrackerClient client;

    @Before
    public void setUp() throws IOException {
        client = spy(new GitHubTrackerClient("https://api.github.com", "token"));
    }

    @Test
    public void testPerformTicketParsesCompositeKey() throws Exception {
        doReturn("{\"number\":42,\"title\":\"Broken\",\"state\":\"open\",\"html_url\":\"https://github.com/acme/widgets/issues/42\"}")
                .when(client).execute(any(GenericRequest.class));

        GitHubTicket ticket = client.performTicket("acme/widgets#42", null);

        assertEquals("acme/widgets#42", ticket.getCompositeKey());
        assertEquals("Broken", ticket.getTicketTitle());

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).execute(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues/42", captor.getValue().url());
    }

    @Test
    public void testPerformTicketRejectsBadKey() {
        try {
            client.performTicket("not-a-key", null);
            fail("Expected IllegalArgumentException for malformed key");
        } catch (IOException e) {
            fail("Expected IllegalArgumentException, got IOException: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot parse GitHub issue key"));
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSearchAndPerformMapsRepositoryUrl() throws Exception {
        String searchResponse = "{\"items\":["
                + "{\"number\":7,\"title\":\"Bug\",\"state\":\"open\",\"repository_url\":\"https://api.github.com/repos/acme/widgets\"},"
                + "{\"number\":8,\"title\":\"Other\",\"state\":\"closed\",\"repository_url\":\"https://api.github.com/repos/acme/other\"}"
                + "]}";
        doReturn(searchResponse).when(client).execute(any(GenericRequest.class));

        List<GitHubTicket> tickets = client.searchAndPerform("is:issue", null);

        assertEquals(2, tickets.size());
        assertEquals("acme/widgets#7", tickets.get(0).getCompositeKey());
        assertEquals("acme/other#8", tickets.get(1).getCompositeKey());
    }

    @Test
    public void testPostCommentCreatesIssueComment() throws Exception {
        doReturn("{\"id\":1}").when(client).post(any(GenericRequest.class));

        client.postComment("acme/widgets#42", "hello");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues/42/comments", captor.getValue().url());
        assertEquals("hello", new JSONObject(captor.getValue().getBody()).getString("body"));
    }

    @Test
    public void testMoveToStatusDoneCloses() throws Exception {
        doReturn("{\"state\":\"closed\"}").when(client).patch(any(GenericRequest.class));

        client.moveToStatus("acme/widgets#42", "Done");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).patch(captor.capture());
        assertEquals("closed", new JSONObject(captor.getValue().getBody()).getString("state"));
    }

    @Test
    public void testMoveToStatusCustomBecomesLabel() throws Exception {
        doReturn("[]").when(client).post(any(GenericRequest.class));

        client.moveToStatus("acme/widgets#42", "In Review");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());
        assertTrue(captor.getValue().url().endsWith("/issues/42/labels"));
        assertEquals("In Review", new JSONObject(captor.getValue().getBody()).getJSONArray("labels").getString(0));
    }

    @Test
    public void testAssignToPostsAssignees() throws Exception {
        doReturn("{}").when(client).post(any(GenericRequest.class));

        client.assignTo("acme/widgets#42", "octocat");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());
        assertTrue(captor.getValue().url().endsWith("/issues/42/assignees"));
        assertEquals("octocat", new JSONObject(captor.getValue().getBody()).getJSONArray("assignees").getString(0));
    }

    @Test
    public void testUpdateDescriptionPatchesBody() throws Exception {
        doReturn("{}").when(client).patch(any(GenericRequest.class));

        client.updateDescription("acme/widgets#42", "new body");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).patch(captor.capture());
        assertEquals("new body", new JSONObject(captor.getValue().getBody()).getString("body"));
    }

    @Test
    public void testGetTicketBrowseUrl() {
        assertEquals("https://github.com/acme/widgets/issues/42", client.getTicketBrowseUrl("acme/widgets#42"));
    }

    @Test
    public void testUnsupportedOperationsThrow() {
        assertThrows(UnsupportedOperationException.class, () -> client.getChangeLog("acme/widgets#42", null));
        assertThrows(UnsupportedOperationException.class, () -> client.attachFileToTicket("acme/widgets#42", "f.png", "image/png", null));
        assertThrows(UnsupportedOperationException.class, () -> client.linkIssueWithRelationship("a", "b", "blocks"));
    }

    private static <T extends Throwable> T assertThrows(Class<T> type, ThrowingRunnable r) {
        try {
            r.run();
            fail("Expected " + type.getSimpleName());
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + e.getClass().getSimpleName(), e);
        }
        return null;
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
