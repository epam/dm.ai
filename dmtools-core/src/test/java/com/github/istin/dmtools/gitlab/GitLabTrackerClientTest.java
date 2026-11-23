// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.gitlab.model.GitLabTicket;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GitLabTrackerClientTest {

    private GitLabTrackerClient client;

    @Before
    public void setUp() throws IOException {
        client = spy(new GitLabTrackerClient("https://gitlab.example", "token", "group", "project"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCallHookIsUnsupported() {
        client.callHookAndWaitResponse("https://gitlab.example/hook", "request");
    }

    @Test
    public void testPerformTicketUsesCompositeKeyAndProjectPath() throws Exception {
        doReturn("{\"iid\":42,\"title\":\"Broken\",\"state\":\"opened\",\"description\":\"Details\"}")
                .when(client).execute(any(GenericRequest.class));

        GitLabTicket ticket = client.performTicket("group/subgroup/project#42", null);

        assertEquals("group/subgroup/project#42", ticket.getTicketKey());
        assertEquals("Broken", ticket.getTicketTitle());
        assertEquals("opened", ticket.getStatus());

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).execute(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fsubgroup%2Fproject/issues/42",
                captor.getValue().url());
    }

    @Test
    public void testPerformTicketResolvesNumericKeyAgainstDefaultProject() throws Exception {
        doReturn("{\"iid\":7,\"title\":\"Bug\",\"state\":\"opened\"}")
                .when(client).execute(any(GenericRequest.class));

        GitLabTicket ticket = client.performTicket("7", null);

        assertEquals("group/project#7", ticket.getTicketKey());
    }

    @Test
    public void testSearchAndPerformMapsIssues() throws Exception {
        doReturn("[{\"iid\":7,\"title\":\"Bug\",\"state\":\"opened\"}]")
                .when(client).execute(any(GenericRequest.class));

        List<GitLabTicket> tickets = client.searchAndPerform("bug", null);

        assertEquals(1, tickets.size());
        assertEquals("group/project#7", tickets.get(0).getTicketKey());
    }

    @Test
    public void testPostCommentCreatesIssueNote() throws Exception {
        doReturn("{\"id\":1}").when(client).post(any(GenericRequest.class));

        client.postComment("group/project#42", "hello");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues/42/notes",
                captor.getValue().url());
        assertEquals("hello", new JSONObject(captor.getValue().getBody()).getString("body"));
    }

    @Test
    public void testMoveToDoneClosesIssue() throws Exception {
        doReturn("{}").when(client).put(any(GenericRequest.class));

        client.moveToStatus("group/project#42", "Done");

        assertEquals("close", capturedUpdate().getString("state_event"));
    }

    @Test
    public void testMoveToCustomStatusAddsScopedLabel() throws Exception {
        doReturn("{}").when(client).put(any(GenericRequest.class));

        client.moveToStatus("group/project#42", "In Review");

        assertEquals("status::In Review", capturedUpdate().getString("add_labels"));
    }

    @Test
    public void testAssignToUpdatesAssignee() throws Exception {
        doReturn("[{\"id\":123}]").when(client).execute(any(GenericRequest.class));
        doReturn("{}").when(client).put(any(GenericRequest.class));

        client.assignTo("group/project#42", "developer");

        assertEquals(123, capturedUpdate().getJSONArray("assignee_ids").getInt(0));
    }

    @Test
    public void testAddLabelUpdatesIssue() throws Exception {
        doReturn("{}").when(client).put(any(GenericRequest.class));
        GitLabTicket ticket = new GitLabTicket("group/project", "{\"iid\":42,\"labels\":[]}");

        client.addLabelIfNotExists(ticket, "bug");

        assertEquals("bug", capturedUpdate().getString("add_labels"));
    }

    @Test
    public void testRemoveLabelUpdatesIssue() throws Exception {
        doReturn("{}").when(client).put(any(GenericRequest.class));
        GitLabTicket ticket = new GitLabTicket("group/project", "{\"iid\":42,\"labels\":[\"bug\"]}");

        client.deleteLabelInTicket(ticket, "bug");

        assertEquals("bug", capturedUpdate().getString("remove_labels"));
    }

    @Test
    public void testUpdateDescriptionUpdatesIssue() throws Exception {
        doReturn("{}").when(client).put(any(GenericRequest.class));

        client.updateDescription("group/project#42", "Updated");

        assertEquals("Updated", capturedUpdate().getString("description"));
    }

    @Test(expected = IOException.class)
    public void testUpdatePropagatesFailure() throws Exception {
        doThrow(new IOException("GitLab update failed")).when(client).put(any(GenericRequest.class));

        client.updateDescription("group/project#42", "Updated");
    }

    private JSONObject capturedUpdate() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).put(captor.capture());
        verify(client, never()).patch(any(GenericRequest.class));
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues/42",
                captor.getValue().url());
        return new JSONObject(captor.getValue().getBody());
    }

    @Test
    public void testGetTicketBrowseUrl() {
        assertEquals("https://gitlab.example/group/project/-/issues/42",
                client.getTicketBrowseUrl("group/project#42"));
    }
}
