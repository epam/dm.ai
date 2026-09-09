// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GitHubTicket} — the ITicket view of a GitHub issue.
 */
public class GitHubTicketTest {

    private GitHubTicket ticket(String json) {
        return new GitHubTicket("acme", "widgets", json);
    }

    private String baseJson() {
        return "{"
                + "\"number\": 42,"
                + "\"title\": \"Something broken\","
                + "\"body\": \"Steps to reproduce\","
                + "\"state\": \"open\","
                + "\"html_url\": \"https://github.com/acme/widgets/issues/42\","
                + "\"created_at\": \"2024-01-01T10:00:00Z\","
                + "\"updated_at\": \"2024-01-02T10:00:00Z\","
                + "\"labels\": [{\"name\": \"bug\"}, {\"name\": \"priority:high\"}],"
                + "\"user\": {\"login\": \"octocat\"},"
                + "\"assignees\": [{\"login\": \"octocat\"}]"
                + "}";
    }

    @Test
    public void testCompositeKey() {
        GitHubTicket t = ticket(baseJson());
        assertEquals("acme/widgets#42", t.getCompositeKey());
        assertEquals("acme/widgets#42", t.getKey());
        assertEquals("acme/widgets#42", t.getTicketKey());
    }

    @Test
    public void testTitleBodyState() {
        GitHubTicket t = ticket(baseJson());
        assertEquals("Something broken", t.getTicketTitle());
        assertEquals("Steps to reproduce", t.getTicketDescription());
        assertEquals("open", t.getStatus());
        assertEquals("open", t.getStatusModel().getName());
    }

    @Test
    public void testTicketLink() {
        GitHubTicket t = ticket(baseJson());
        assertEquals("https://github.com/acme/widgets/issues/42", t.getTicketLink());
    }

    @Test
    public void testLabels() {
        GitHubTicket t = ticket(baseJson());
        assertEquals(2, t.getTicketLabels().length());
        assertEquals("bug", t.getTicketLabels().getString(0));
    }

    @Test
    public void testPriorityFromLabel() {
        GitHubTicket t = ticket(baseJson());
        assertEquals("High", t.getPriority());
        assertEquals(ITicketPriorityHigh(), t.getPriorityAsEnum());
    }

    private static com.github.istin.dmtools.common.model.ITicket.TicketPriority ITicketPriorityHigh() {
        return com.github.istin.dmtools.common.model.ITicket.TicketPriority.High;
    }

    @Test
    public void testPriorityFromPLabel() {
        GitHubTicket t = ticket("{\"number\":1,\"title\":\"t\",\"state\":\"open\",\"labels\":[{\"name\":\"p1\"}]}");
        assertEquals("High", t.getPriority());
    }

    @Test
    public void testPriorityAbsent() {
        GitHubTicket t = ticket("{\"number\":1,\"title\":\"t\",\"state\":\"open\",\"labels\":[]}");
        assertNull(t.getPriority());
        assertEquals(com.github.istin.dmtools.common.model.ITicket.TicketPriority.NotSet, t.getPriorityAsEnum());
    }

    @Test
    public void testProgress() {
        assertEquals(0.0, ticket(baseJson()).getProgress(), 0.001);
        GitHubTicket closed = ticket("{\"number\":1,\"title\":\"t\",\"state\":\"closed\"}");
        assertEquals(1.0, closed.getProgress(), 0.001);
    }

    @Test
    public void testDates() {
        GitHubTicket t = ticket(baseJson());
        assertNotNull(t.getCreated());
        assertNotNull(t.getUpdatedAsMillis());
    }

    @Test
    public void testIssueTypeAndAttachments() {
        GitHubTicket t = ticket(baseJson());
        assertEquals("Issue", t.getIssueType());
        assertTrue(t.getAttachments().isEmpty());
        assertNull(t.getFields());
    }

    @Test
    public void testCreator() {
        GitHubTicket t = ticket(baseJson());
        assertNotNull(t.getCreator());
        assertEquals("octocat", t.getCreator().getFullName());
    }

    @Test
    public void testToTextContainsKeyAndTitle() {
        GitHubTicket t = ticket(baseJson());
        String text = t.toText();
        assertTrue(text.contains("acme/widgets#42"));
        assertTrue(text.contains("Something broken"));
    }
}
