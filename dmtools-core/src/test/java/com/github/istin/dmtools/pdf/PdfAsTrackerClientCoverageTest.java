// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pdf;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.pdf.model.PdfPageAsTicket;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PdfAsTrackerClientCoverageTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PdfAsTrackerClient pdfAsTrackerClient;
    private String basePath;

    @Before
    public void setUp() throws Exception {
        basePath = temporaryFolder.getRoot().getAbsolutePath();
        pdfAsTrackerClient = new PdfAsTrackerClient(basePath);
    }

    private File createTicketFolder(String fileName, String pageNumber) throws IOException {
        File ticketFolder = new File(basePath + "/cache/" + fileName + "/" + pageNumber);
        ticketFolder.mkdirs();
        return ticketFolder;
    }

    @Test
    public void testSimpleGetters() {
        assertEquals("", pdfAsTrackerClient.tag("initiator"));
        assertEquals("query", pdfAsTrackerClient.buildUrlToSearch("query"));
        assertEquals(basePath, pdfAsTrackerClient.getBasePath());
        assertEquals("", pdfAsTrackerClient.getTicketBrowseUrl("doc-1"));
        assertEquals(TrackerClient.TextType.HTML, pdfAsTrackerClient.getTextType());
        assertArrayEquals(new String[0], pdfAsTrackerClient.getDefaultQueryFields());
        assertArrayEquals(new String[0], pdfAsTrackerClient.getExtendedQueryFields());
        pdfAsTrackerClient.setLogEnabled(true);
        pdfAsTrackerClient.setLogEnabled(false);
    }

    @Test
    public void testGetTextFieldsOnly() {
        PdfPageAsTicket ticket = new PdfPageAsTicket();
        ticket.setDescription("description");
        assertEquals("description", pdfAsTrackerClient.getTextFieldsOnly(ticket));

        ticket.setSnapshotDescription("snapshot");
        assertEquals("description\nsnapshot", pdfAsTrackerClient.getTextFieldsOnly(ticket));
    }

    @Test
    public void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.linkIssueWithRelationship("a", "b", "rel"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.updateDescription("a", "desc"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.updateTicket("a", null));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.assignTo("a", "user"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.getChangeLog("a", null));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.attachFileToTicket("a", "name", "type", null));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.createTicket("body"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.createTicketInProject("p", "t", "s", "d", null));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.postCommentIfNotExists("a", "c"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.getComments("a", null));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.postComment("a", "c"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.deleteCommentIfExists("a", "c"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.moveToStatus("a", "s"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.getDefaultStatusField());
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.getTestCases(null, "t"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.setCacheGetRequestsEnabled(true));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.getFixVersions("p"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.isValidImageUrl("u"));
        assertThrows(UnsupportedOperationException.class,
                () -> pdfAsTrackerClient.convertUrlToFile("u"));
    }

    @Test
    public void testPerformTicketWithAllFiles() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "  some   text  ", "UTF-8");
        FileUtils.write(new File(ticketFolder, "snapshot_description.txt"), "snapshot desc", "UTF-8");
        FileUtils.write(new File(ticketFolder, "labels.json"), "[\"l1\", \"l2\"]", "UTF-8");
        FileUtils.write(new File(ticketFolder, "attachment_1.png"), "img", "UTF-8");
        FileUtils.write(new File(ticketFolder, "other.txt"), "other", "UTF-8");

        PdfPageAsTicket ticket = pdfAsTrackerClient.performTicket("doc-1", null);

        assertNotNull(ticket);
        assertEquals("doc-1", ticket.getTicketKey());
        assertEquals("some text", ticket.getDescription());
        assertEquals("some text\nsnapshot desc", ticket.getTicketDescription());
        assertEquals("snapshot desc", ticket.getSnapshotDescription());
        assertNotNull(ticket.getTicketLabels());
        assertEquals(2, ticket.getTicketLabels().length());
        assertEquals(1, ticket.getAttachments().size());
        assertTrue(ticket.getPageSnapshot().getPath().endsWith("page_snapshot.png"));
    }

    @Test
    public void testPerformTicketWithoutOptionalFiles() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");

        PdfPageAsTicket ticket = pdfAsTrackerClient.performTicket("doc-1", null);

        assertNotNull(ticket);
        assertEquals("text", ticket.getTicketDescription());
        assertNull(ticket.getTicketLabels());
        assertNull(ticket.getSnapshotDescription());
        assertTrue(ticket.getAttachments().isEmpty());
    }

    @Test
    public void testPerformTicketMissingFolderReturnsNull() throws IOException {
        PdfPageAsTicket ticket = pdfAsTrackerClient.performTicket("doc-9", null);
        assertNull(ticket);
    }

    @Test
    public void testAddLabelIfNotExistsCreatesFile() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        PdfPageAsTicket ticket = new PdfPageAsTicket();
        ticket.setKey("doc-1");

        pdfAsTrackerClient.addLabelIfNotExists(ticket, "newLabel");

        String content = FileUtils.readFileToString(new File(ticketFolder, "labels.json"), "UTF-8");
        assertEquals("[\"newLabel\"]", content);
    }

    @Test
    public void testAddLabelIfNotExistsAppendsAndSkipsDuplicate() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        File labelsFile = new File(ticketFolder, "labels.json");
        FileUtils.write(labelsFile, "[\"existing\"]", "UTF-8");
        PdfPageAsTicket ticket = new PdfPageAsTicket();
        ticket.setKey("doc-1");

        pdfAsTrackerClient.addLabelIfNotExists(ticket, "added");
        assertEquals("[\"existing\",\"added\"]",
                FileUtils.readFileToString(labelsFile, "UTF-8"));

        // duplicate should not be appended
        pdfAsTrackerClient.addLabelIfNotExists(ticket, "existing");
        assertEquals("[\"existing\",\"added\"]",
                FileUtils.readFileToString(labelsFile, "UTF-8"));
    }

    @Test
    public void testDeleteLabelInTicket() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        File labelsFile = new File(ticketFolder, "labels.json");
        FileUtils.write(labelsFile, "[\"keep\", \"RemoveMe\"]", "UTF-8");
        PdfPageAsTicket ticket = new PdfPageAsTicket();
        ticket.setKey("doc-1");

        // case-insensitive removal
        pdfAsTrackerClient.deleteLabelInTicket(ticket, "removeme");
        assertEquals("[\"keep\"]", FileUtils.readFileToString(labelsFile, "UTF-8"));

        // label not present -> file stays unchanged
        pdfAsTrackerClient.deleteLabelInTicket(ticket, "absent");
        assertEquals("[\"keep\"]", FileUtils.readFileToString(labelsFile, "UTF-8"));
    }

    @Test
    public void testDeleteLabelInTicketWithoutLabelsFile() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        PdfPageAsTicket ticket = new PdfPageAsTicket();
        ticket.setKey("doc-1");

        pdfAsTrackerClient.deleteLabelInTicket(ticket, "any");

        assertFalse(new File(ticketFolder, "labels.json").exists());
    }

    @Test
    public void testUpdatePageSnapshot() throws IOException {
        File ticketFolder = createTicketFolder("doc", "1");
        PdfPageAsTicket ticket = new PdfPageAsTicket();
        ticket.setKey("doc-1");

        pdfAsTrackerClient.updatePageSnapshot(ticket, "snapshot text");

        assertEquals("snapshot text", FileUtils.readFileToString(
                new File(ticketFolder, "snapshot_description.txt"), "UTF-8"));
    }

    @Test
    public void testSearchAndPerformWithoutCacheFolder() throws Exception {
        List<PdfPageAsTicket> tickets = pdfAsTrackerClient.searchAndPerform(null, null);
        assertNotNull(tickets);
        assertTrue(tickets.isEmpty());
    }

    @Test
    public void testSearchAndPerformNullQuery() throws Exception {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");
        // a plain file inside cache folder must be ignored
        FileUtils.write(new File(basePath + "/cache/readme.txt"), "x", "UTF-8");

        List<PdfPageAsTicket> tickets = pdfAsTrackerClient.searchAndPerform(null, null);

        assertEquals(1, tickets.size());
        assertEquals("doc-1", tickets.get(0).getTicketKey());
        assertEquals("text", tickets.get(0).getTicketDescription());
    }

    @Test
    public void testSearchAndPerformWithLabelsQuery() throws Exception {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");
        FileUtils.write(new File(ticketFolder, "labels.json"), "[\"Target\"]", "UTF-8");

        List<PdfPageAsTicket> matching = pdfAsTrackerClient.searchAndPerform("labels=target", null);
        assertEquals(1, matching.size());

        List<PdfPageAsTicket> notMatching = pdfAsTrackerClient.searchAndPerform("labels=absent", null);
        assertTrue(notMatching.isEmpty());
    }

    @Test
    public void testSearchAndPerformWithNonLabelsQuery() throws Exception {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");

        List<PdfPageAsTicket> tickets = pdfAsTrackerClient.searchAndPerform("project=doc", null);
        assertEquals(1, tickets.size());
    }

    @Test
    public void testSearchAndPerformSkipsNonNumericDirs() throws Exception {
        File notesFolder = createTicketFolder("doc", "notes");
        FileUtils.write(new File(notesFolder, "description.txt"), "x", "UTF-8");

        List<PdfPageAsTicket> tickets = pdfAsTrackerClient.searchAndPerform(null, null);
        assertTrue(tickets.isEmpty());
    }

    @Test
    public void testSearchAndPerformSkipsNullTicket() throws Exception {
        // numeric folder exists but is empty -> performTicket returns ticket with null content,
        // and a numeric folder that cannot be read yields null ticket which must be skipped
        createTicketFolder("doc", "1");

        List<PdfPageAsTicket> tickets = pdfAsTrackerClient.searchAndPerform(null, null);
        assertEquals(1, tickets.size());
        assertNull(tickets.get(0).getTicketDescription());
    }

    @Test
    public void testSearchAndPerformStoppingPerformer() throws Exception {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");
        File ticketFolder2 = createTicketFolder("doc2", "1");
        FileUtils.write(new File(ticketFolder2, "description.txt"), "text2", "UTF-8");

        AtomicInteger performed = new AtomicInteger(0);
        pdfAsTrackerClient.searchAndPerform(ticket -> {
            performed.incrementAndGet();
            return true;
        }, null, null);

        assertEquals(1, performed.get());
    }

    @Test
    public void testSearchAndPerformListCollectsAllTickets() throws Exception {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");
        File ticketFolder2 = createTicketFolder("doc2", "1");
        FileUtils.write(new File(ticketFolder2, "description.txt"), "text2", "UTF-8");

        List<PdfPageAsTicket> tickets = pdfAsTrackerClient.searchAndPerform((String) null, null);

        assertEquals(2, tickets.size());
        List<String> keys = new ArrayList<>();
        for (PdfPageAsTicket ticket : tickets) {
            keys.add(ticket.getTicketKey());
        }
        assertTrue(keys.contains("doc-1"));
        assertTrue(keys.contains("doc2-1"));
    }

    @Test
    public void testSearchAndPerformPerformerVariantReturns() throws Exception {
        File ticketFolder = createTicketFolder("doc", "1");
        FileUtils.write(new File(ticketFolder, "description.txt"), "text", "UTF-8");

        List<ITicket> performed = new ArrayList<>();
        pdfAsTrackerClient.searchAndPerform(ticket -> {
            performed.add(ticket);
            return false;
        }, null, null);

        assertEquals(1, performed.size());
    }
}
