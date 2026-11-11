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
        doReturn("{}").when(client).patch(any(GenericRequest.class));

        client.moveToStatus("group/project#42", "Done");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).patch(captor.capture());
        assertEquals("close", new JSONObject(captor.getValue().getBody()).getString("state_event"));
    }

    @Test
    public void testMoveToCustomStatusAddsScopedLabel() throws Exception {
        doReturn("{}").when(client).patch(any(GenericRequest.class));

        client.moveToStatus("group/project#42", "In Review");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).patch(captor.capture());
        assertEquals("status::In Review", new JSONObject(captor.getValue().getBody()).getString("add_labels"));
    }

    @Test
    public void testGetTicketBrowseUrl() {
        assertEquals("https://gitlab.example/group/project/-/issues/42",
                client.getTicketBrowseUrl("group/project#42"));
    }
}
