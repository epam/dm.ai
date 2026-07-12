// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.index;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.index.mermaid.MermaidIndexIntegration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JiraMermaidIndexIntegration}.
 *
 * <p>The {@link TrackerClient} (or {@link JiraClient} for attachment downloads)
 * is fully mocked; {@code searchAndPerform} is answered by invoking the
 * performer callback with stub tickets, so no network calls are made.</p>
 */
public class JiraMermaidIndexIntegrationCoverageTest {

    private static final String JQL = "project = PROJ";

    /** Stubs searchAndPerform to feed the given tickets to the performer callback. */
    @SuppressWarnings("unchecked")
    private static void stubSearch(TrackerClient<ITicket> client, ITicket... tickets) throws Exception {
        doReturn(new String[0]).when(client).getDefaultQueryFields();
        doAnswer(invocation -> {
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0);
            for (ITicket ticket : tickets) {
                performer.perform(ticket);
            }
            return null;
        }).when(client).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));
    }

    private static ITicket basicTicket(String key) throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.getTicketTitle()).thenReturn("Title " + key);
        when(ticket.getTicketDescription()).thenReturn("Description of " + key);
        when(ticket.getIssueType()).thenReturn("Bug");
        doReturn(Collections.emptyList()).when(ticket).getAttachments();
        when(ticket.getUpdatedAsMillis()).thenReturn(1700000000000L);
        return ticket;
    }

    private static class RecordingProcessor implements MermaidIndexIntegration.ContentProcessor {
        final List<String> pathOrIds = new ArrayList<>();
        final List<String> contentNames = new ArrayList<>();
        final List<String> contents = new ArrayList<>();
        final List<List<String>> metadatas = new ArrayList<>();
        final List<List<File>> attachments = new ArrayList<>();
        final List<Date> lastModifieds = new ArrayList<>();

        @Override
        public void process(String pathOrId, String contentName, String content,
                            List<String> metadata, List<File> attachmentsList, Date lastModified) {
            pathOrIds.add(pathOrId);
            contentNames.add(contentName);
            contents.add(content);
            metadatas.add(metadata);
            attachments.add(attachmentsList);
            lastModifieds.add(lastModified);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullTrackerClient_throws() {
        new JiraMermaidIndexIntegration(null, null, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getContentForIndex_nullIncludePatterns_doesNothing() {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(null, null, processor);

        assertTrue(processor.pathOrIds.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getContentForIndex_emptyIncludePatterns_doesNothing() {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.emptyList(), null, processor);

        assertTrue(processor.pathOrIds.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getContentForIndex_defaultFields_addsDescriptionAndUpdated() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        stubSearch(client, basicTicket("PROJ-1"));
        when(client.getDefaultQueryFields()).thenReturn(new String[]{"summary", "status"});

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        ArgumentCaptor<String[]> fieldsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(client).searchAndPerform(any(JiraClient.Performer.class), eq(JQL), fieldsCaptor.capture());
        List<String> fields = Arrays.asList(fieldsCaptor.getValue());
        assertTrue(fields.contains("summary"));
        assertTrue(fields.contains("status"));
        assertTrue(fields.contains(Fields.DESCRIPTION));
        assertTrue(fields.contains(Fields.UPDATED));

        assertEquals(1, processor.pathOrIds.size());
        assertEquals("PROJ/PROJ-1", processor.pathOrIds.get(0));
        assertEquals("Title PROJ-1", processor.contentNames.get(0));
        assertTrue(processor.contents.get(0).contains("Description of PROJ-1"));
        assertTrue(processor.metadatas.get(0).contains("projectKey:PROJ"));
        assertTrue(processor.metadatas.get(0).contains("ticketKey:PROJ-1"));
        assertTrue(processor.metadatas.get(0).contains("issueType:Bug"));
        assertEquals(new Date(1700000000000L), processor.lastModifieds.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getContentForIndex_customFields_appendsFieldValues() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-2");
        when(ticket.getFieldValueAsString("customfield_1")).thenReturn("custom value");
        when(ticket.getFieldValueAsString("emptyField")).thenReturn("  ");
        when(ticket.getFieldValueAsString("failingField")).thenThrow(new RuntimeException("boom"));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(
                client, new String[]{"customfield_1", "emptyField", "failingField", Fields.DESCRIPTION}, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        ArgumentCaptor<String[]> fieldsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(client).searchAndPerform(any(JiraClient.Performer.class), eq(JQL), fieldsCaptor.capture());
        List<String> fields = Arrays.asList(fieldsCaptor.getValue());
        assertTrue(fields.contains("customfield_1"));
        assertTrue(fields.contains("key"));
        assertTrue(fields.contains(Fields.DESCRIPTION));
        assertTrue(fields.contains(Fields.UPDATED));
        // description was already in customFields, must not be duplicated
        assertEquals(1, countOccurrences(fields, Fields.DESCRIPTION));

        String content = processor.contents.get(0);
        assertTrue(content.contains("--- customfield_1 ---"));
        assertTrue(content.contains("custom value"));
        assertFalse(content.contains("--- emptyField ---"));
    }

    private static int countOccurrences(List<String> list, String value) {
        int count = 0;
        for (String item : list) {
            if (value.equals(item)) {
                count++;
            }
        }
        return count;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_xrayTestStepsAndPreconditions_appended() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-3");

        JSONArray steps = new JSONArray();
        steps.put(new JSONObject()
                .put("action", "Click button")
                .put("data", "some data")
                .put("result", "result ok"));
        steps.put(new JSONObject()
                .put("action", "Type text")
                .put("expectedResult", "text typed"));
        JSONArray preconditions = new JSONArray();
        preconditions.put(new JSONObject()
                .put("jira", new JSONObject().put("key", "PROJ-99"))
                .put("summary", "Pre summary")
                .put("description", "Pre description")
                .put("definition", "Pre definition"));
        JSONObject fieldsJson = new JSONObject()
                .put("xrayTestSteps", steps)
                .put("xrayPreconditions", preconditions);
        when(ticket.getFields()).thenReturn(new Fields(fieldsJson));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        String content = processor.contents.get(0);
        assertTrue(content.contains("--- Test Steps ---"));
        assertTrue(content.contains("Step 1:"));
        assertTrue(content.contains("Action: Click button"));
        assertTrue(content.contains("Data: some data"));
        assertTrue(content.contains("Expected Result: result ok"));
        assertTrue(content.contains("Step 2:"));
        assertTrue(content.contains("Expected Result: text typed"));
        assertTrue(content.contains("--- Preconditions ---"));
        assertTrue(content.contains("Precondition 1 (PROJ-99):"));
        assertTrue(content.contains("Summary: Pre summary"));
        assertTrue(content.contains("Description: Pre description"));
        assertTrue(content.contains("Definition: Pre definition"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_getFieldsThrows_xraySectionSkipped() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-4");
        when(ticket.getFields()).thenThrow(new RuntimeException("no fields"));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertEquals("PROJ/PROJ-4", processor.pathOrIds.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_includeComments_appendsFormattedComments() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-5");

        IUser author = mock(IUser.class);
        when(author.getFullName()).thenReturn("John Doe");
        when(author.getEmailAddress()).thenReturn("john@example.com");
        IComment comment = mock(IComment.class);
        when(comment.getAuthor()).thenReturn(author);
        when(comment.getCreated()).thenReturn(new Date(1700000000000L));
        when(comment.getBody()).thenReturn("First comment body");

        IComment noAuthorComment = mock(IComment.class);
        when(noAuthorComment.getAuthor()).thenReturn(null);
        when(noAuthorComment.getCreated()).thenReturn(null);
        when(noAuthorComment.getBody()).thenReturn("Anonymous comment");

        IComment emptyBodyComment = mock(IComment.class);
        when(emptyBodyComment.getBody()).thenReturn("   ");

        doReturn(Arrays.asList(comment, null, noAuthorComment, emptyBodyComment))
                .when(client).getComments(eq("PROJ-5"), any(ITicket.class));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, true);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        String content = processor.contents.get(0);
        assertTrue(content.contains("--- Comments ---"));
        assertTrue(content.contains("--- Comment by John Doe (john@example.com) on "));
        assertTrue(content.contains("First comment body"));
        assertTrue(content.contains("--- Comment by Unknown ---"));
        assertTrue(content.contains("Anonymous comment"));
        assertFalse(content.contains("--- emptyField ---"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_commentsFetchFails_stillProcessesTicket() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-6");
        when(client.getComments(eq("PROJ-6"), any(ITicket.class))).thenThrow(new IOException("nope"));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, true);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertFalse(processor.contents.get(0).contains("--- Comments ---"));
    }

    @Test
    public void processTicket_attachmentsDownloadedViaJiraClient() throws Exception {
        JiraClient<?> jiraClient = mock(JiraClient.class);
        File downloaded = File.createTempFile("attachment", ".txt");
        downloaded.deleteOnExit();
        doReturn(downloaded).when(jiraClient).convertUrlToFile("http://example.com/file.txt");
        doThrow(new IOException("download failed")).when(jiraClient).convertUrlToFile("http://example.com/broken.txt");

        ITicket ticket = basicTicket("PROJ-7");
        IAttachment good = mock(IAttachment.class);
        when(good.getName()).thenReturn("file.txt");
        when(good.getUrl()).thenReturn("http://example.com/file.txt");
        IAttachment noUrl = mock(IAttachment.class);
        when(noUrl.getName()).thenReturn("no-url.txt");
        when(noUrl.getUrl()).thenReturn(null);
        IAttachment broken = mock(IAttachment.class);
        when(broken.getName()).thenReturn("broken.txt");
        when(broken.getUrl()).thenReturn("http://example.com/broken.txt");
        doReturn(Arrays.asList(good, null, noUrl, broken)).when(ticket).getAttachments();

        stubSearchCast(jiraClient, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(jiraClient, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        List<File> attachments = processor.attachments.get(0);
        assertEquals(1, attachments.size());
        assertEquals(downloaded, attachments.get(0));
    }

    /** Same as stubSearch but for a raw JiraClient mock. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSearchCast(JiraClient client, ITicket... tickets) throws Exception {
        doReturn(new String[0]).when(client).getDefaultQueryFields();
        doAnswer(invocation -> {
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0);
            for (ITicket ticket : tickets) {
                performer.perform(ticket);
            }
            return null;
        }).when(client).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_attachmentsPresentButNotJiraClient_skipsDownload() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-8");
        IAttachment attachment = mock(IAttachment.class);
        when(attachment.getName()).thenReturn("file.txt");
        when(attachment.getUrl()).thenReturn("http://example.com/file.txt");
        doReturn(Collections.singletonList(attachment)).when(ticket).getAttachments();
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertTrue(processor.attachments.get(0).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_getAttachmentsThrows_stillProcesses() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-9");
        when(ticket.getAttachments()).thenThrow(new RuntimeException("attach error"));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertTrue(processor.attachments.get(0).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_titleThrowsIOException_usesKeyAsTitle() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-10");
        when(ticket.getTicketTitle()).thenThrow(new IOException("title error"));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals("PROJ-10", processor.contentNames.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_issueTypeThrowsIOException_omittedFromMetadata() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("PROJ-11");
        when(ticket.getIssueType()).thenThrow(new IOException("type error"));
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        List<String> metadata = processor.metadatas.get(0);
        assertTrue(metadata.contains("ticketKey:PROJ-11"));
        for (String entry : metadata) {
            assertFalse(entry.startsWith("issueType:"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_nullTicketAndNullDescription_handled() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket noDescription = basicTicket("PROJ-12");
        when(noDescription.getTicketDescription()).thenReturn(null);
        stubSearch(client, null, noDescription);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        // null ticket skipped, ticket without description still processed
        assertEquals(1, processor.pathOrIds.size());
        assertEquals("PROJ/PROJ-12", processor.pathOrIds.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_keyFallbackViaGetKey() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn(null);
        when(ticket.getKey()).thenReturn("KEY-9");
        when(ticket.getTicketTitle()).thenReturn("Fallback title");
        when(ticket.getTicketDescription()).thenReturn("desc");
        when(ticket.getIssueType()).thenReturn("Task");
        doReturn(Collections.emptyList()).when(ticket).getAttachments();
        when(ticket.getUpdatedAsMillis()).thenReturn(null);
        Date created = new Date(1600000000000L);
        when(ticket.getCreated()).thenReturn(created);
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertEquals("KEY/KEY-9", processor.pathOrIds.get(0));
        // updated millis null -> falls back to created date
        assertEquals(created, processor.lastModifieds.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_keyFallbackViaJSONModel() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        Ticket ticket = spy(new Ticket(new JSONObject()
                .put("key", "JSON-5")
                .put("fields", new JSONObject())));
        doReturn(null).when(ticket).getTicketKey();
        doReturn(null).when(ticket).getKey();
        doReturn("Bug").when(ticket).getIssueType();
        doReturn(Collections.emptyList()).when(ticket).getAttachments();
        doReturn(null).when(ticket).getUpdatedAsMillis();
        doReturn(null).when(ticket).getCreated();
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertEquals("JSON/JSON-5", processor.pathOrIds.get(0));
        // both updated and created null -> falls back to "now"
        assertNotNull(processor.lastModifieds.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_noKeyAtAll_skipped() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("");
        when(ticket.getKey()).thenReturn(null);
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertTrue(processor.pathOrIds.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processTicket_keyWithoutDash_projectKeyUnknown() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket ticket = basicTicket("NODASH");
        stubSearch(client, ticket);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals("UNKNOWN/NODASH", processor.pathOrIds.get(0));
        assertTrue(processor.metadatas.get(0).contains("projectKey:UNKNOWN"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void performer_ticketThrows_continuesWithNextTicket() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        ITicket failing = mock(ITicket.class);
        // getTicketKey blows up in the error handler too -> "unknown" key path
        when(failing.getTicketKey()).thenThrow(new RuntimeException("key error"));
        when(failing.getKey()).thenThrow(new RuntimeException("key error"));
        when(failing.getTicketDescription()).thenThrow(new RuntimeException("desc error"));
        ITicket good = basicTicket("PROJ-13");
        stubSearch(client, failing, good);

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);
        RecordingProcessor processor = new RecordingProcessor();

        integration.getContentForIndex(Collections.singletonList(JQL), null, processor);

        assertEquals(1, processor.pathOrIds.size());
        assertEquals("PROJ/PROJ-13", processor.pathOrIds.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getContentForIndex_searchFails_wrappedInRuntimeException() throws Exception {
        TrackerClient<ITicket> client = mock(TrackerClient.class);
        when(client.getDefaultQueryFields()).thenReturn(new String[0]);
        doThrow(new IOException("search down")).when(client)
                .searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));

        JiraMermaidIndexIntegration integration = new JiraMermaidIndexIntegration(client, null, false);

        try {
            integration.getContentForIndex(Collections.singletonList(JQL), null, new RecordingProcessor());
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Failed to retrieve content from Jira", e.getMessage());
            assertTrue(e.getCause() instanceof IOException);
        }
    }
}
